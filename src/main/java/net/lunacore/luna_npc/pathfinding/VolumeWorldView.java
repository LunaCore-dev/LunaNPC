package net.lunacore.luna_npc.pathfinding;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

// Walkability queries for free 3D movement through a fluid volume (swimming) or the air (flight),
// where y is a real search axis instead of gravity-bound like the ground PathWorldView. A cell is
// open only when it holds NO blocking collision at all — a full water/lava source, flowing fluid or
// plain air — so a waterlogged slab, stairs or any partial block is a wall, never a passage (that is
// the "only a FULL fluid block counts as free space, not a half one" rule). The whole body box must
// fit, so a big NPC never threads a gap it couldn't actually swim through.
public class VolumeWorldView {

    private final Level level;
    private final Set<Block> avoidedBlocks = new HashSet<>();
    // The fluids this mover travels. A water NPC swims water; a lava-dweller swims lava; a flyer
    // swims neither (it lives in the air). Air is open to every volume mover — that is what lets a
    // water NPC breach the surface to reach a target standing above the water.
    private final boolean swimWater;
    private final boolean swimLava;
    // Body size in whole cells: clearance is the height, footprintRadius the cells to each side of
    // the centre. The whole box must be open for the mover to occupy a cell.
    private final int clearance;
    private final int footprintRadius;
    // How far above its fluid a swimmer may enter open air. Air within this many cells straight above
    // the water counts as a breach cell it can lunge into; anything higher, or air over land, is a
    // wall — so a water NPC snaps at a target at the surface without ever flying off across the land.
    // Ignored for a flyer, whose home medium is the air itself.
    private final int maxBreachDepth;
    // Cost added for a cell OUTSIDE the mover's home medium — air for a swimmer. Keeps a water NPC
    // inside the water, breaching in short hops right at the target instead of arcing through the
    // sky. Zero for a flyer, whose home medium is the air itself.
    private final float outOfMediumPenalty;
    private Set<Long> avoidedCells = Set.of();
    private final Map<Long, Boolean> openCache = new HashMap<>();

    public VolumeWorldView(Level level, List<String> avoidedBlockIds, boolean swimWater,
                           boolean swimLava, int clearance, int footprintRadius,
                           float outOfMediumPenalty, int maxBreachDepth) {
        this.level = level;
        this.swimWater = swimWater;
        this.swimLava = swimLava;
        this.clearance = Math.max(1, clearance);
        this.footprintRadius = Math.max(0, footprintRadius);
        this.outOfMediumPenalty = outOfMediumPenalty;
        this.maxBreachDepth = Math.max(0, maxBreachDepth);
        for (String blockId : avoidedBlockIds) {
            Identifier parsed = Identifier.tryParse(blockId);
            if (parsed != null) {
                BuiltInRegistries.BLOCK.getOptional(parsed).ifPresent(avoidedBlocks::add);
            }
        }
    }

    // The navigator hands over the cells it kept bumping into so the next plan treats them as walls
    // and finds a genuinely different 3D route. Empty for a normal, unobstructed plan.
    public void avoidCells(Set<Long> cells) {
        this.avoidedCells = cells;
    }

    // A single cell is open when nothing blocks it: not a remembered dead end, not an avoided block,
    // and no collision shape at all. A full fluid and plain air both have an empty collision shape;
    // a slab, stair, waterlogged fence or any solid block does not, so those stay walls. A fluid the
    // mover does not travel (lava for a water NPC) is also a wall even though it has no collision.
    public boolean isOpen(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        Boolean cached = openCache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean open = computeOpen(x, y, z);
        openCache.put(key, open);
        return open;
    }

    private boolean computeOpen(int x, int y, int z) {
        if (avoidedCells.contains(BlockPos.asLong(x, y, z))) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (avoidedBlocks.contains(state.getBlock())) {
            return false;
        }
        // Any collision at all makes the cell a wall: this is what keeps slabs, stairs and every
        // partial/solid block out while letting full fluids and air (both collision-free) through.
        if (!state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        FluidState fluid = state.getFluidState();
        if (fluid.is(FluidTags.WATER)) {
            return swimWater;
        }
        if (fluid.is(FluidTags.LAVA)) {
            return swimLava;
        }
        // Air, or a non-colliding block like seagrass out of water. A flyer's home medium IS the air,
        // so it is always open; a fluid swimmer may only enter air as a shallow breach right above its
        // fluid, which is what keeps a water NPC from flying off across the land after a target on it.
        if (!swimWater && !swimLava) {
            return true;
        }
        return fluidWithinBelow(x, y, z);
    }

    // Scans straight down from just under the cell for the swimmer's fluid, giving up at the first
    // solid block: an air cell is a breach cell only when open fluid sits within maxBreachDepth
    // beneath it — true just above the water surface, false everywhere over land.
    private boolean fluidWithinBelow(int x, int y, int z) {
        if (maxBreachDepth <= 0) {
            return false;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y, z);
        for (int depth = 1; depth <= maxBreachDepth; depth++) {
            cursor.setY(y - depth);
            BlockState state = level.getBlockState(cursor);
            FluidState fluid = state.getFluidState();
            if ((swimWater && fluid.is(FluidTags.WATER)) || (swimLava && fluid.is(FluidTags.LAVA))) {
                return true;
            }
            if (!state.getCollisionShape(level, cursor).isEmpty()) {
                return false;
            }
        }
        return false;
    }

    // The whole body box (width x height x width) fits: every cell from the feet up through the
    // clearance height and out to the footprint radius is open. The 3D analogue of the ground view's
    // footprint+headroom check, so a big NPC never routes through a pocket too small to hold it.
    public boolean fits(int x, int y, int z) {
        for (int dy = 0; dy < clearance; dy++) {
            for (int dx = -footprintRadius; dx <= footprintRadius; dx++) {
                for (int dz = -footprintRadius; dz <= footprintRadius; dz++) {
                    if (!isOpen(x + dx, y + dy, z + dz)) {
                        return false;
                    }
                }
            }
        }
        // Water too shallow to cover the whole body plus one buffer cell is simply not part of a
        // swimmer's navigable volume — routes plan around it instead of into it, so the NPC never
        // wanders into the shallows and floats half out of the water. (snapToOpen still pulls a mover
        // that starts in shallow water back out to a deep cell, so this never strands it.)
        return !(swimWater || swimLava) || submergedWithBuffer(x, y, z);
    }

    // The feet cell up through the head plus one buffer cell above are all the mover's fluid: the water
    // here is at least (height + 1) blocks deep.
    private boolean submergedWithBuffer(int x, int y, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y, z);
        for (int dy = 0; dy <= clearance; dy++) {
            cursor.setY(y + dy);
            FluidState fluid = level.getFluidState(cursor);
            if (!((swimWater && fluid.is(FluidTags.WATER)) || (swimLava && fluid.is(FluidTags.LAVA)))) {
                return false;
            }
        }
        return true;
    }

    // Extra cost for a spot where the swimmer's body isn't fully in its fluid — air above the surface,
    // or water too shallow to cover it. Taxing these steers the cheapest route to water deep enough to
    // stay submerged, so a big NPC does not swim into the shallows and break the surface.
    public float movementPenalty(int x, int y, int z) {
        if (outOfMediumPenalty <= 0.0F) {
            return 0.0F;
        }
        return bodyInFluid(x, y, z) ? 0.0F : outOfMediumPenalty;
    }

    private boolean bodyInFluid(int x, int y, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y, z);
        for (int dy = 0; dy < clearance; dy++) {
            cursor.setY(y + dy);
            FluidState fluid = level.getFluidState(cursor);
            if (!((swimWater && fluid.is(FluidTags.WATER)) || (swimLava && fluid.is(FluidTags.LAVA)))) {
                return false;
            }
        }
        return true;
    }

    // Nearest cell to the hint whose whole body box fits, spiralling outward over Chebyshev shells.
    // Snaps the start (the NPC may straddle a boundary or hug a wall) and the goal (the target may
    // stand in a solid block or a step above the water) onto a genuinely occupiable cell. Null when
    // nothing within range fits.
    public BlockPos snapToOpen(int x, int y, int z, int radius) {
        if (fits(x, y, z)) {
            return new BlockPos(x, y, z);
        }
        for (int r = 1; r <= radius; r++) {
            for (int i = 0; i <= 2 * r; i++) {
                // y offsets nearest the hint first (0, +1, -1, +2, ...): scanning -r upward instead
                // returned the DEEPEST fitting cell of the shell, silently sinking every snapped
                // goal below the depth the caller actually asked for.
                int dy = i % 2 == 1 ? (i + 1) / 2 : -(i / 2);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        // Only the shell at the current radius — inner shells were already tried.
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) {
                            continue;
                        }
                        if (fits(x + dx, y + dy, z + dz)) {
                            return new BlockPos(x + dx, y + dy, z + dz);
                        }
                    }
                }
            }
        }
        return null;
    }

    // A clear straight line between two cells: the body box fits at every sampled step. Lets the
    // planner string-pull its 6-connected route into smooth diagonal swims.
    public boolean isLineClear(BlockPos from, BlockPos to) {
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(from.distSqr(to)) * 2.0));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = Mth.floor(Mth.lerp(t, from.getX(), to.getX()) + 0.5);
            int y = Mth.floor(Mth.lerp(t, from.getY(), to.getY()) + 0.5);
            int z = Mth.floor(Mth.lerp(t, from.getZ(), to.getZ()) + 0.5);
            if (!fits(x, y, z)) {
                return false;
            }
        }
        return true;
    }
}
