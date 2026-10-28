package net.lunacore.luna_npc.pathfinding;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.lunacore.luna_npc.npc.NpcValidation;

// Walkability queries for ground pathfinding, sized for a roughly player-shaped NPC (one block
// wide, two blocks tall). Low obstacles like snow layers and carpets are walked over, not
// around — snowy mountain surfaces would otherwise fall apart into unreachable islands. Avoided
// blocks poison both the floor the NPC would stand on and the space it would walk through.
public class PathWorldView {

    public static final int NO_STEP = Integer.MIN_VALUE;

    // Collision up to half a block (snow layers, carpets) is stepped over; collision of a
    // near-full block (farmland, dirt paths) carries an NPC even though its top face is not
    // technically sturdy.
    private static final double LOW_OBSTACLE_HEIGHT = 0.5;
    private static final double FULL_FLOOR_HEIGHT = 0.9;

    private final Level level;
    private final Set<Block> avoidedBlocks = new HashSet<>();
    private final int maxFall;
    private final int maxLightLevel;
    private final int minLightLevel;
    private final boolean avoidWater;
    private final boolean avoidLava;
    private final boolean swimAcrossWater;
    // When set, a closed wooden door the NPC can operate counts as a passable cell: the runtime
    // door controller opens (or breaks) it, so all four ground planners route through the doorway.
    private final boolean passThroughDoors;
    // How many blocks of vertical air the NPC's body needs (ceil of its hitbox height): a
    // player-shaped NPC needs 2, a stretched 2.5-block one needs 3, a 0.6-block one needs 1. The
    // planner refuses any cell that can't fit the whole body, so a big NPC never routes through a
    // gap it would suffocate in.
    private final int clearance;
    // The body's width in whole cells (ceil of the hitbox width, like vanilla's pathfinder): a
    // player-shaped NPC is 1, a 1.46-wide one 2, a 2.5-wide one 3. A cell is the footprint's anchor:
    // odd footprints centre on it, even ones extend toward +x/+z and centre on its far corner (see
    // waypointOffset), so a 2-wide body fits a 2-wide gap instead of needing three.
    private final int footprintLow;
    private final int footprintHigh;
    // Cells the navigator has learned are dead ends this trip (an obstacle it kept failing to
    // clear). Treated as solid walls so a replan routes around them instead of retrying the same
    // jump forever. Reset for every new destination.
    private Set<Long> avoidedCells = Set.of();
    // One view lives for exactly one plan, and a plan probes the same cells thousands of times —
    // caching the answers is safe and buys a large speedup.
    private final Map<Long, Boolean> passableCache = new HashMap<>();
    private final Map<Long, Boolean> standableCache = new HashMap<>();

    public PathWorldView(Level level, List<String> avoidedBlockIds, int maxFall) {
        this(level, avoidedBlockIds, maxFall, NpcValidation.MAX_LIGHT_LEVEL, NpcValidation.MIN_LIGHT_LEVEL,
                false, true, true, 2, 1, false);
    }

    // The light bounds carry the avoid-light/avoid-darkness traits: cells brighter than
    // maxLightLevel or darker than minLightLevel are not walkable for this NPC. avoidWater and
    // avoidLava exclude those fluids the same way (lava on by default; off is an intentionally
    // extreme creator choice). swimAcrossWater lets a swimming-capable land NPC treat the water
    // surface as walkable ground, crossing on top like a player. clearance is the body height in
    // whole blocks (see the field).
    public PathWorldView(Level level, List<String> avoidedBlockIds, int maxFall,
                         int maxLightLevel, int minLightLevel, boolean avoidWater, boolean avoidLava,
                         boolean swimAcrossWater, int clearance, int footprintCells, boolean passThroughDoors) {
        this.level = level;
        this.maxFall = maxFall;
        this.maxLightLevel = maxLightLevel;
        this.minLightLevel = minLightLevel;
        this.avoidWater = avoidWater;
        this.avoidLava = avoidLava;
        this.swimAcrossWater = swimAcrossWater;
        this.passThroughDoors = passThroughDoors;
        this.clearance = Math.max(1, clearance);
        int cells = Math.max(1, footprintCells);
        this.footprintLow = -(cells - 1) / 2;
        this.footprintHigh = footprintLow + cells - 1;
        for (String blockId : avoidedBlockIds) {
            Identifier parsed = Identifier.tryParse(blockId);
            if (parsed != null) {
                BuiltInRegistries.BLOCK.getOptional(parsed).ifPresent(avoidedBlocks::add);
            }
        }
    }

    public static int footprintCells(float width) {
        return Math.max(1, Mth.ceil(width - 1.0E-3F));
    }

    // How far the body's centre sits from its anchor cell's centre, on x and z: 0 for an odd footprint,
    // half a block for an even one (it centres on the corner between its cells).
    public double waypointOffset() {
        return (footprintHigh - footprintLow + 1) % 2 == 0 ? 0.5 : 0.0;
    }

    // The navigator hands over the cells it got stuck against so the next plan treats them as
    // walls and finds a genuinely different route. Empty for a normal, unobstructed plan.
    public void avoidCells(Set<Long> cells) {
        this.avoidedCells = cells;
    }

    public boolean isPassable(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        Boolean cached = passableCache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean passable = computePassable(x, y, z);
        passableCache.put(key, passable);
        return passable;
    }

    private boolean computePassable(int x, int y, int z) {
        if (avoidedCells.contains(BlockPos.asLong(x, y, z))) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if ((avoidLava && state.getFluidState().is(FluidTags.LAVA))
                || (avoidWater && state.getFluidState().is(FluidTags.WATER))
                || avoidedBlocks.contains(state.getBlock())) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty() || shape.max(Direction.Axis.Y) <= LOW_OBSTACLE_HEIGHT) {
            return true;
        }
        return passThroughDoors && isOperableDoor(state);
    }

    // A wooden door the NPC can open by hand (iron doors need redstone, so they stay walls). Modded
    // doors are picked up automatically through their block-set type. This is the single definition of
    // "a door the door controller handles", shared by planning here and the runtime controller.
    static boolean isOperableDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand();
    }

    boolean isOperableDoorAt(int x, int y, int z) {
        return isOperableDoor(level.getBlockState(new BlockPos(x, y, z)));
    }

    // A standable cell has room for feet and head and something to stand on. Light tolerance is
    // NOT a standability rule — it is a soft routing preference (see movementPenalty), so an NPC
    // can still walk (or flee) through forbidden light when there is no compliant path.
    public boolean isStandable(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        Boolean cached = standableCache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean standable = computeStandable(x, y, z);
        standableCache.put(key, standable);
        return standable;
    }

    private boolean computeStandable(int x, int y, int z) {
        return hasFootprintHeadroom(x, y, z) && hasFootprintFloor(x, y, z);
    }

    // The whole footprint (the entity's width in cells around its anchor) needs vertical
    // clearance: a one-wide NPC only checks its own column, a wider one its neighbours too, so a fat
    // NPC never routes through a gap it couldn't squeeze into.
    private boolean hasFootprintHeadroom(int x, int y, int z) {
        for (int dx = footprintLow; dx <= footprintHigh; dx++) {
            for (int dz = footprintLow; dz <= footprintHigh; dz++) {
                if (!hasHeadroom(x + dx, y, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    // Support is footprint-wide but only needs ONE column: Minecraft rests a mob on the highest
    // block under any part of its footprint, so a wide NPC is held up the moment a single column
    // beneath it has a floor — it does not need ground under its centre. That is what lets a big NPC
    // climb onto a one-block ledge its hitbox edge merely touches (centre four blocks away, body
    // hanging over the lower ground around it) instead of treating that ledge as an impassable wall.
    // headroom above still checks the WHOLE footprint, so this never lets a body squeeze somewhere it
    // doesn't fit. A one-wide NPC has a single column, so this stays a plain centre floor for normal
    // player-shaped NPCs.
    private boolean hasFootprintFloor(int x, int y, int z) {
        for (int dx = footprintLow; dx <= footprintHigh; dx++) {
            for (int dz = footprintLow; dz <= footprintHigh; dz++) {
                if (hasFloor(x + dx, y, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    // The body fits when every block from the feet up through the clearance height is passable.
    private boolean hasHeadroom(int x, int y, int z) {
        for (int dy = 0; dy < clearance; dy++) {
            if (!isPassable(x, y + dy, z)) {
                return false;
            }
        }
        return true;
    }

    // True when the cell's light is inside the NPC's tolerated window; used by the routing
    // penalty and by callers that want to find genuinely compliant spots.
    public boolean isLightTolerated(int x, int y, int z) {
        if (maxLightLevel >= NpcValidation.MAX_LIGHT_LEVEL && minLightLevel <= NpcValidation.MIN_LIGHT_LEVEL) {
            return true;
        }
        int brightness = level.getMaxLocalRawBrightness(new BlockPos(x, y, z));
        return brightness <= maxLightLevel && brightness >= minLightLevel;
    }

    private boolean hasFloor(int x, int y, int z) {
        // A low obstacle in the feet cell (snow layer, carpet) is its own floor; isPassable has
        // already ruled out anything taller.
        BlockPos feet = new BlockPos(x, y, z);
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
            return true;
        }
        BlockPos below = new BlockPos(x, y - 1, z);
        // Swimmers walk the water surface: the first cell above the water column counts as
        // ground (only the surface — never cells inside the water body).
        if (swimAcrossWater && level.getFluidState(below).is(FluidTags.WATER)
                && level.getFluidState(feet).isEmpty()) {
            return true;
        }
        BlockState state = level.getBlockState(below);
        if (avoidedBlocks.contains(state.getBlock())) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, below);
        if (shape.isEmpty()) {
            return false;
        }
        return state.isFaceSturdy(level, below, Direction.UP)
                || shape.max(Direction.Axis.Y) >= FULL_FLOOR_HEIGHT;
    }

    // Land entities prefer solid ground: cells that mean wading or surface-swimming cost extra,
    // so water is only crossed when it is genuinely the better (or only) route. Forbidden light
    // adds a heavy cost the same way — strongly avoided, never impossible.
    private static final float LIGHT_PENALTY = 8.0F;

    public float movementPenalty(int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos below = new BlockPos(x, y - 1, z);
        float penalty = level.getFluidState(feet).is(FluidTags.WATER)
                || level.getFluidState(below).is(FluidTags.WATER) ? 4.0F : 0.0F;
        if (!isLightTolerated(x, y, z)) {
            penalty += LIGHT_PENALTY;
        }
        return penalty;
    }

    // Destination y when walking one cell from (x, y, z) by (dx, dz): flat first, then a
    // one-block step up (needs headroom above the source), then a drop of up to maxFall.
    // NO_STEP means the move is impossible.
    public int stepTarget(int x, int y, int z, int dx, int dz) {
        int nx = x + dx;
        int nz = z + dz;
        if (isStandable(nx, y, nz)) {
            return y;
        }
        // Stepping up one block: the source column needs one more block of air above the body (at
        // y + clearance) to rise through, and the destination must fit the whole body.
        if (isPassable(x, y + clearance, z) && isStandable(nx, y + 1, nz)) {
            return y + 1;
        }
        if (hasHeadroom(nx, y, nz)) {
            for (int drop = 1; drop <= maxFall; drop++) {
                if (isStandable(nx, y - drop, nz)) {
                    return y - drop;
                }
            }
        }
        return NO_STEP;
    }

    // Nearest standable y to the hint, searching down first because targets usually sit on the
    // ground. NO_STEP when nothing standable is in range.
    public int snapToGround(int x, int yHint, int z, int up, int down) {
        for (int dy = 0; dy <= down; dy++) {
            if (isStandable(x, yHint - dy, z)) {
                return yHint - dy;
            }
        }
        for (int dy = 1; dy <= up; dy++) {
            if (isStandable(x, yHint + dy, z)) {
                return yHint + dy;
            }
        }
        return NO_STEP;
    }

    // Straight walkable line over flat ground, sampled at the four corners of the NPC's
    // footprint; the any-angle planner uses this to cut grid corners.
    public boolean isLineWalkable(BlockPos from, BlockPos to) {
        if (from.getY() != to.getY()) {
            return false;
        }
        double fromX = from.getX() + 0.5;
        double fromZ = from.getZ() + 0.5;
        double dx = to.getX() + 0.5 - fromX;
        double dz = to.getZ() + 0.5 - fromZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        int samples = Math.max(1, Mth.ceil(length / 0.25));
        int y = from.getY();
        for (int step = 0; step <= samples; step++) {
            double px = fromX + dx * step / samples;
            double pz = fromZ + dz * step / samples;
            // A door is passable but must stay an explicit grid waypoint: smoothing a diagonal line
            // across the doorway would send the NPC into the frame, so the any-angle shortcut stops
            // at doors and the NPC approaches them head-on.
            if (passThroughDoors && isOperableDoorAt(Mth.floor(px), y, Mth.floor(pz))) {
                return false;
            }
            if (!isFootprintStandable(px, y, pz)) {
                return false;
            }
        }
        return true;
    }

    private static final double HALF_WIDTH = 0.3;

    private boolean isFootprintStandable(double x, int y, double z) {
        return isStandable(Mth.floor(x - HALF_WIDTH), y, Mth.floor(z - HALF_WIDTH))
                && isStandable(Mth.floor(x + HALF_WIDTH), y, Mth.floor(z - HALF_WIDTH))
                && isStandable(Mth.floor(x - HALF_WIDTH), y, Mth.floor(z + HALF_WIDTH))
                && isStandable(Mth.floor(x + HALF_WIDTH), y, Mth.floor(z + HALF_WIDTH));
    }
}
