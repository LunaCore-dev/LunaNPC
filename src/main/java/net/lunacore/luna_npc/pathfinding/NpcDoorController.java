package net.lunacore.luna_npc.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.entity.NpcEntity;

// Walks an NPC physically through a wooden door on its route. PathWorldView has already made the door
// a passable cell, so the plan runs straight through the doorway; this drives the crossing so the NPC
// never freezes at a door. An open-capable NPC throws the door open and HOLDS it open while shoving
// forward through the gap — so a player slamming the door shut cannot trap it, it just glitches through
// the moment the leaf clears. A break-capable NPC punches a closed door down like a zombie, in five
// steps over ~15s, then walks through. The interaction survives the constant re-paths of a combat
// chase (navigation only resets it on a full stop) and is dropped if the door leaves the route.
final class NpcDoorController {

    // Delay before an opened door swings shut behind the NPC, and how far clear it must be first so it
    // never closes on itself (forced anyway a couple of seconds later if the NPC lingers in the doorway).
    private static final int CLOSE_DELAY = 20;
    private static final double CLOSE_CLEAR_DISTANCE_SQ = 1.0;
    private static final int CLOSE_FORCE_TICKS = -40;
    // Door mode only triggers when the NPC is squared up in front of the doorway: within this far in
    // front along the door's facing axis, and no further than this to the side of the doorway centre. A
    // door a block off to the side never triggers it. Re-checked every tick, not only on entry, so an
    // NPC knocked or nudged off the doorway drops out of door mode instead of breaking a door it no
    // longer faces.
    private static final double ENGAGE_ALONG = 1.5;
    private static final double LATERAL_TOLERANCE = 0.6;
    // The crossing stays active — holding the NPC centred and pushing — until its body centre is this
    // far past the door centre, i.e. its whole body has cleared the door block (half-body 0.3 past the
    // far face at 0.5). Ending sooner leaves its trailing half in the block where the leaf catches it.
    // A crossing that somehow can't finish is abandoned after a ceiling so it can never hang the NPC.
    private static final double CROSS_THRESHOLD = 0.8;
    private static final int MAX_CROSSING_TICKS = 60;
    // Extra forward shove while crossing an open door, on top of the walk input, so the NPC slips
    // through even if a griefer flickers the door.
    private static final double PUSH = 0.06;
    // How hard the NPC steers back onto the doorway centreline, and the cap on that sideways speed, so
    // it threads the gap the open leaf still leaves instead of catching on it off-centre.
    private static final double CENTER_GAIN = 0.35;
    private static final double CENTER_MAX = 0.1;
    // Zombie-style break: total ticks to smash a door (~15s), split into five 20% steps that each play
    // one knock at half volume, plus a faster swing cadence so the NPC visibly keeps punching between.
    private static final int BREAK_TICKS = 300;
    private static final int BREAK_STEPS = 5;
    private static final int BREAK_STEP_TICKS = BREAK_TICKS / BREAK_STEPS;
    private static final int SWING_INTERVAL = 12;
    private static final float BREAK_VOLUME = 0.5F;

    private enum Stage { NONE, CROSSING, BREAK }

    private final NpcEntity entity;
    private Stage stage = Stage.NONE;
    private BlockPos doorPos;
    private double fwdX;
    private double fwdZ;
    private int crossingTicks;
    private int breakTicks;
    private int breakStage = -1;
    private BlockPos closeDoor;
    private int closeTimer;
    private boolean closeArmed;

    NpcDoorController(NpcEntity entity) {
        this.entity = entity;
    }

    // Shuts a door the NPC opened, once it has had its second and stepped clear. Ticked every server
    // tick — even after the NPC arrives and stops navigating — so a door is never left hanging open.
    void tickPendingClose() {
        if (!closeArmed) {
            return;
        }
        closeTimer--;
        if (closeTimer > 0) {
            return;
        }
        double dx = closeDoor.getX() + 0.5 - entity.getX();
        double dz = closeDoor.getZ() + 0.5 - entity.getZ();
        if (dx * dx + dz * dz < CLOSE_CLEAR_DISTANCE_SQ && closeTimer > CLOSE_FORCE_TICKS) {
            return;
        }
        setOpen(closeDoor, false);
        closeArmed = false;
        closeDoor = null;
    }

    // Advances the door interaction for this ground tick. Returns true while the NPC is engaged with a
    // door — navigation then keeps shoving it forward toward the waypoint and suppresses its stuck
    // replan, so pushing against a door it is breaking (or through one held open) is never mistaken for
    // being stuck. waypoint is the cell the NPC is currently steering toward.
    boolean handle(Vec3 waypoint) {
        if (!entity.traitsSettings().canTraverseDoors()) {
            reset();
            return false;
        }
        if (stage == Stage.NONE) {
            BlockPos door = doorAhead(waypoint);
            if (door == null) {
                return false;
            }
            begin(door, waypoint);
        }
        return switch (stage) {
            case CROSSING -> tickCrossing();
            case BREAK -> tickBreak();
            case NONE -> false;
        };
    }

    private void begin(BlockPos door, Vec3 waypoint) {
        doorPos = door;
        double dx = waypoint.x - entity.getX();
        double dz = waypoint.z - entity.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        fwdX = len < 1.0E-4 ? 0.0 : dx / len;
        fwdZ = len < 1.0E-4 ? 0.0 : dz / len;
        // A closed door only a break-capable NPC can't open gets smashed; anything else (a door we can
        // open, or one already standing open) is crossed — centred through so the leaf never snags it.
        boolean closed = !entity.level().getBlockState(door).getValue(DoorBlock.OPEN);
        if (closed && !entity.traitsSettings().canOpenDoors()) {
            stage = Stage.BREAK;
            breakTicks = 0;
            breakStage = -1;
        } else {
            stage = Stage.CROSSING;
            crossingTicks = 0;
        }
    }

    private boolean tickCrossing() {
        BlockState state = entity.level().getBlockState(doorPos);
        boolean stillDoor = PathWorldView.isOperableDoor(state);
        boolean operate = entity.traitsSettings().canOpenDoors();
        if (stillDoor && operate) {
            setOpen(doorPos, true);
        }
        if (crossed() || !stillDoor) {
            if (stillDoor && operate) {
                armClose(doorPos);
            }
            stage = Stage.NONE;
            doorPos = null;
            return false;
        }
        if (!squaredUp(doorPos) || ++crossingTicks > MAX_CROSSING_TICKS) {
            reset();
            return false;
        }
        // Even open, the leaf still fills a sliver of the doorway, so steer the NPC back onto the
        // centreline (perpendicular to the door's facing) as it shoves forward — off-centre it would
        // otherwise catch on the leaf and stall. The sideways nudge clears it toward the open side.
        Direction facing = state.getValue(DoorBlock.FACING);
        double wx = facing.getStepZ();
        double wz = -facing.getStepX();
        double lateral = (entity.getX() - (doorPos.getX() + 0.5)) * wx + (entity.getZ() - (doorPos.getZ() + 0.5)) * wz;
        double latVel = Math.max(-CENTER_MAX, Math.min(CENTER_MAX, -lateral * CENTER_GAIN));
        Vec3 dm = entity.getDeltaMovement();
        entity.setDeltaMovement(dm.x + fwdX * PUSH + wx * latVel, dm.y, dm.z + fwdZ * PUSH + wz * latVel);
        return true;
    }

    // The NPC's body centre has passed the door centre along its approach direction (about half its
    // body beyond the leaf) — from here it is committed and just keeps shoving forward.
    private boolean crossed() {
        double rx = entity.getX() - (doorPos.getX() + 0.5);
        double rz = entity.getZ() - (doorPos.getZ() + 0.5);
        return rx * fwdX + rz * fwdZ > CROSS_THRESHOLD;
    }

    private boolean tickBreak() {
        Level level = entity.level();
        BlockState state = level.getBlockState(doorPos);
        if (!PathWorldView.isOperableDoor(state) || state.getValue(DoorBlock.OPEN) || !squaredUp(doorPos)) {
            reset();
            return false;
        }
        if (breakTicks % SWING_INTERVAL == 0) {
            entity.swing(InteractionHand.MAIN_HAND, entity.getMainHandItem().getAttackAnimation(), false);
        }
        if (breakTicks % BREAK_STEP_TICKS == 0 && breakTicks < BREAK_TICKS) {
            level.playSound(null, doorPos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE,
                    BREAK_VOLUME, 0.95F + entity.getRandom().nextFloat() * 0.1F);
        }
        breakTicks++;
        int shown = Math.min(9, breakTicks * 10 / BREAK_TICKS);
        if (shown != breakStage) {
            breakStage = shown;
            level.destroyBlockProgress(entity.getId(), doorPos, shown);
        }
        if (breakTicks >= BREAK_TICKS) {
            breakDown(doorPos);
            return false;
        }
        return true;
    }

    private void breakDown(BlockPos pos) {
        Level level = entity.level();
        level.destroyBlock(pos, false);
        BlockPos upper = pos.above();
        if (PathWorldView.isOperableDoor(level.getBlockState(upper))) {
            level.destroyBlock(upper, false);
        }
        level.destroyBlockProgress(entity.getId(), pos, -1);
        stage = Stage.NONE;
        doorPos = null;
        breakStage = -1;
    }

    private void armClose(BlockPos pos) {
        closeDoor = pos;
        closeTimer = CLOSE_DELAY;
        closeArmed = true;
    }

    private void setOpen(BlockPos pos, boolean open) {
        BlockState state = entity.level().getBlockState(pos);
        if (PathWorldView.isOperableDoor(state) && state.getValue(DoorBlock.OPEN) != open) {
            ((DoorBlock) state.getBlock()).setOpen(entity, entity.level(), state, pos, open);
        }
    }

    // Cancels an in-progress crossing or break (navigation stopped, or the door left the route),
    // clearing a half-broken door's crack overlay. A pending close stays armed so an opened door shuts.
    void reset() {
        if (stage == Stage.BREAK && doorPos != null) {
            entity.level().destroyBlockProgress(entity.getId(), doorPos, -1);
        }
        stage = Stage.NONE;
        doorPos = null;
        breakStage = -1;
    }

    // The closed operable door the NPC is walking into, or null: checks the cell it is stepping toward
    // and its own cell, snapping an upper half down to the lower one the door state machine acts on.
    private BlockPos doorAhead(Vec3 waypoint) {
        double dx = waypoint.x - entity.getX();
        double dz = waypoint.z - entity.getZ();
        int sx = dx > 0.4 ? 1 : dx < -0.4 ? -1 : 0;
        int sz = dz > 0.4 ? 1 : dz < -0.4 ? -1 : 0;
        BlockPos feet = entity.blockPosition();
        BlockPos door = operableDoorAt(feet.offset(sx, 0, sz));
        if (door == null) {
            door = operableDoorAt(feet);
        }
        if (door == null || !squaredUp(door)) {
            return null;
        }
        return door;
    }

    // The NPC is squared up in front of the door: aligned with the doorway centre (small sideways
    // offset from the door's facing axis) and close in front of it. This keeps it from breaking or
    // crossing a door it is only standing beside, a block off to one side.
    private boolean squaredUp(BlockPos door) {
        BlockState state = entity.level().getBlockState(door);
        if (!PathWorldView.isOperableDoor(state)) {
            return false;
        }
        Direction facing = state.getValue(DoorBlock.FACING);
        double rx = entity.getX() - (door.getX() + 0.5);
        double rz = entity.getZ() - (door.getZ() + 0.5);
        double along = Math.abs(rx * facing.getStepX() + rz * facing.getStepZ());
        double lateral = Math.abs(rx * facing.getStepZ() - rz * facing.getStepX());
        return along <= ENGAGE_ALONG && lateral <= LATERAL_TOLERANCE;
    }

    // Any wooden door the NPC can handle at this cell, open or closed — an open one still has its leaf
    // filling a sliver of the gap, so the NPC engages it too and threads through centred rather than
    // snagging on it. Snaps an upper half down to the lower one the state machine acts on.
    private BlockPos operableDoorAt(BlockPos pos) {
        BlockState state = entity.level().getBlockState(pos);
        if (!PathWorldView.isOperableDoor(state)) {
            return null;
        }
        return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos.immutable();
    }
}
