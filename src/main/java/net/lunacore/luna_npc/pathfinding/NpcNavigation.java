package net.lunacore.luna_npc.pathfinding;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.LunaNpcConfig;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.api.NpcHeightPreference;
import net.lunacore.luna_npc.api.NpcMovementSettings;
import net.lunacore.luna_npc.api.NpcTraitsSettings;
import net.lunacore.luna_npc.npc.NpcValidation;

// Moves an NPC to a target. Ground NPCs follow planned paths through the vanilla locomotion
// inputs (forward input, speed, jump), so friction, collisions and step-up behave exactly like
// vanilla mobs; budget-capped planners reach far targets by chaining partial routes for as long
// as the NPC keeps getting closer overall. Air NPCs fly straight at their flight height above
// the terrain and give way to the target's height on approach unless the preference is fixed;
// water NPCs cruise at their preferred depth and give way to the target's on approach — freely
// when allowed to leave the depth, within the tolerance band when holding it.
public class NpcNavigation {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final double ARRIVE_DISTANCE = 1.5;
    // A big vertical gap (target directly above/below, e.g. a creeper in a cave under the NPC) is
    // not "arrived" even when horizontally on top of it — the NPC must still path down to it.
    private static final double ARRIVE_VERTICAL = 2.0;
    private static final double APPROACH_DISTANCE = 8.0;
    private static final int MAX_FALL = 3;
    private static final int STUCK_TICKS_BEFORE_REPLAN = 40;
    private static final int MAX_REPLANS = 4;
    private static final int MAX_STALE_CHAINS = 3;
    private static final int MAX_STALE_CHAINS_SCRIPTED = 8;
    private static final float TURN_DEGREES_PER_TICK = 45.0F;
    // A floor so even a huge NPC still turns, and the fraction of the gap a swimmer/flyer closes toward
    // its target velocity each tick (blended, not snapped, so a course change eases in). Both are scaled
    // down by body size, so a bigger entity banks around smoothly while a small one stays nimble.
    private static final float MIN_TURN_DEGREES_PER_TICK = 8.0F;
    private static final double STEER_ACCEL = 0.5;
    private static final double MIN_STEER_ACCEL = 0.18;
    private static final int OVERSHOOT_TICKS = 3;
    // Cap on remembered dead-end cells for one trip; clearing on overflow is fine because a new
    // destination resets them anyway.
    private static final int MAX_STUCK_CELLS = 24;
    // Air is passable to a swimmer but heavily taxed, so a water NPC hugs the water and only breaches
    // in the shortest hop needed to reach a target above the surface, never arcing through the sky.
    private static final float OUT_OF_MEDIUM_PENALTY = 6.0F;
    // How far a water NPC may lunge above its water surface — enough to snap at a target at or just
    // over the surface, short enough that it can never fly off across the land after one.
    private static final int WATER_BREACH_DEPTH = 2;
    // How far the 3D planner may nudge the start/goal onto a cell the whole body actually fits in
    // (the NPC hugging a wall, or the target sitting in a block or a step above the water).
    private static final int START_SNAP_RADIUS = 3;
    private static final int GOAL_SNAP_RADIUS = 6;
    // A repeated moveTo toward a target this close to the current one continues the same trip (a chase
    // re-issuing its goal) and keeps what the trip learned; the route is only rebuilt once the goal has
    // moved this far from the one it was planned for.
    private static final double SAME_TRIP_DISTANCE = 8.0;
    private static final double REPLAN_GOAL_SHIFT = 2.0;
    // Off the route: landing this far below the current waypoint (a fall, a knock off a ledge) or this
    // far beside it means the plan no longer matches where the NPC actually is.
    private static final double FELL_BELOW_WAYPOINT = 1.5;
    private static final double OFF_ROUTE_DISTANCE = 4.0;

    private final NpcEntity entity;
    // Cells this trip has proven impassable (kept bumping into them); fed to the planner so a
    // replan detours around instead of retrying the same failed hop. Cleared on a new destination.
    private final Set<Long> stuckCells = new HashSet<>();
    private final NpcDoorController doorController;
    private NpcPath path;
    private PathWorldView routeWorld;
    private Vec3 target;
    private Vec3 plannedTarget;
    // Ground paths are planned on footprint anchor cells; an even-width body stands on the corner between
    // its cells, so its waypoints sit this far (x and z) from the anchor-cell centres.
    private double cellOffset;
    private boolean running;
    private boolean scripted;
    private int stuckTicks;
    private int replans;
    private int staleChains;
    private int inputPauseTicks;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double bestDistanceToTarget;
    private double lastWaypointDistance;
    private int overshootTicks;

    public NpcNavigation(NpcEntity entity) {
        this.entity = entity;
        this.doorController = new NpcDoorController(entity);
    }

    // Starts moving towards the target; true when the NPC has any way forward (for ground NPCs
    // that includes partial paths, which chain automatically). Scripted moves (API, commands)
    // take priority: the wander controller never overrides them.
    public boolean moveTo(Vec3 targetPos, boolean run) {
        return moveTo(targetPos, run, true);
    }

    public boolean moveTo(Vec3 targetPos, boolean run, boolean isScripted) {
        if (target != null && !isVolumeMover() && target.distanceTo(targetPos) <= SAME_TRIP_DISTANCE) {
            target = targetPos;
            running = run;
            scripted = isScripted;
            if (path != null && plannedTarget != null && plannedTarget.distanceTo(targetPos) <= REPLAN_GOAL_SHIFT) {
                return true;
            }
            return plan();
        }
        target = targetPos;
        running = run;
        scripted = isScripted;
        replans = 0;
        staleChains = 0;
        stuckCells.clear();
        bestDistanceToTarget = weightedDistanceToTarget();
        stuckTicks = 0;
        lastX = entity.getX();
        lastY = entity.getY();
        lastZ = entity.getZ();
        if (isVolumeMover()) {
            return planVolume();
        }
        return plan();
    }

    public void stop() {
        path = null;
        target = null;
        plannedTarget = null;
        scripted = false;
        doorController.reset();
        clearInputs();
    }

    public boolean isNavigating() {
        return target != null;
    }

    public boolean isScripted() {
        return scripted;
    }

    public boolean hasFullPath() {
        return path != null && path.reachedGoal();
    }

    // Briefly releases the locomotion inputs so knockback (either direction) is not immediately
    // out-accelerated by walking.
    public void pauseInputs(int ticks) {
        inputPauseTicks = Math.max(inputPauseTicks, ticks);
    }

    // Runs before the entity's physics step so the inputs below are consumed the same tick.
    public void tick() {
        doorController.tickPendingClose();
        if (target == null) {
            return;
        }
        emitDebugParticles();
        if (inputPauseTicks > 0) {
            inputPauseTicks--;
            clearInputs();
            return;
        }
        if (entity.isMovementBlockedByLiquid()) {
            stop();
            return;
        }
        if (isVolumeMover()) {
            tickVolumePath();
        } else if (path == null) {
            // A scripted move can arrive before a plan exists; try to plan, else give up.
            if (!plan()) {
                stop();
            }
        } else {
            tickGroundPath();
        }
    }

    private boolean isFlying() {
        return "air".equals(entity.effectiveMovementType());
    }

    private boolean isWaterMover() {
        return "water".equals(entity.effectiveMovementType());
    }

    // Water and air NPCs both move by free 3D pathfinding through their medium (VolumePlanner)
    // instead of the gravity-bound grid the ground NPCs walk. A water NPC uses it whether or not it
    // is currently submerged, so a plan can carry it up out of the water and drop it back in.
    private boolean isVolumeMover() {
        return isFlying() || isWaterMover();
    }

    // Whole-block body height the planner must fit the NPC through: a stretched 2.5-block NPC
    // needs 3, a small 0.6-block one needs 1. Derived from the live hitbox so model-size and part
    // scales are already baked in.
    private int bodyClearance() {
        return Math.max(1, Mth.ceil(entity.getBbHeight()));
    }

    // Cells to each side of the centre the body also needs clear, from the hitbox width: a
    // player-shaped NPC is 0, a 1.5-2 block wide one is 1, and so on.
    private int bodyFootprintRadius() {
        return Math.max(0, Mth.ceil((entity.getBbWidth() - 1.0F) / 2.0F));
    }

    // After stalling for a while, brand the block we keep bumping into as a wall so the next plan
    // detours around it instead of retrying the same hop forever. Bounded, and forgotten as soon
    // as a new destination is set.
    private void markObstacleAhead(Vec3 waypoint) {
        double dx = waypoint.x - entity.getX();
        double dz = waypoint.z - entity.getZ();
        int sx = dx > 0.4 ? 1 : dx < -0.4 ? -1 : 0;
        int sz = dz > 0.4 ? 1 : dz < -0.4 ? -1 : 0;
        if (sx == 0 && sz == 0) {
            return;
        }
        if (stuckCells.size() >= MAX_STUCK_CELLS) {
            stuckCells.clear();
        }
        BlockPos ahead = anchorCell().offset(sx, 0, sz);
        stuckCells.add(ahead.asLong());
        stuckCells.add(ahead.above().asLong());
    }

    private boolean plan() {
        NpcMovementSettings movement = entity.movementSettings();
        PathfindingType type = PathfindingType.byId(movement.pathfindingType());
        if (type == null || target == null) {
            return false;
        }
        NpcTraitsSettings traits = entity.traitsSettings();
        // Light tolerance is a soft routing cost now (PathWorldView.movementPenalty), not a wall,
        // so no relaxation is needed to path out of a forbidden spot — it always can.
        int maxLight = traits.avoidLight() ? traits.avoidLightLevel() : NpcValidation.MAX_LIGHT_LEVEL;
        int minLight = traits.avoidNight() ? traits.avoidNightLevel() : NpcValidation.MIN_LIGHT_LEVEL;
        PathWorldView world = new PathWorldView(entity.level(), movement.avoidedBlocks(), MAX_FALL,
                maxLight, minLight, traits.avoidWater(), traits.avoidLava(),
                traits.canSwim() && !traits.avoidWater(), bodyClearance(),
                PathWorldView.footprintCells(entity.getBbWidth()), traits.canTraverseDoors());
        world.avoidCells(stuckCells);
        routeWorld = world;
        cellOffset = world.waypointOffset();
        BlockPos targetBlock = BlockPos.containing(target.x - cellOffset, target.y, target.z - cellOffset);
        int goalY = world.snapToGround(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), 2, 8);
        if (goalY == PathWorldView.NO_STEP) {
            return false;
        }
        BlockPos goal = new BlockPos(targetBlock.getX(), goalY, targetBlock.getZ());
        path = type.createPlanner().plan(world, anchorCell(), goal);
        // Every remembered dead end may have walled the NPC in: forget them rather than stand forever.
        if (path == null && !stuckCells.isEmpty()) {
            stuckCells.clear();
            world.avoidCells(stuckCells);
            path = type.createPlanner().plan(world, anchorCell(), goal);
        }
        plannedTarget = target;
        if (LunaNpcConfig.pathfindingDebug()) {
            LOGGER.info("[path] {} {} -> {} : {}", entity.getName().getString(), anchorCell().toShortString(),
                    goal.toShortString(), path == null ? "no path"
                            : (path.reachedGoal() ? "full " : "partial ") + path.waypoints().size() + " waypoints, ends "
                                    + BlockPos.containing(path.waypoints().getLast()).toShortString());
        }
        overshootTicks = 0;
        lastWaypointDistance = Double.MAX_VALUE;
        return path != null;
    }

    // The footprint anchor cell the body occupies (its own block for an odd width).
    private BlockPos anchorCell() {
        return BlockPos.containing(entity.getX() - cellOffset, entity.getY(), entity.getZ() - cellOffset);
    }

    private Vec3 groundWaypoint() {
        return path.currentWaypoint().add(cellOffset, 0.0, cellOffset);
    }

    private void tickGroundPath() {
        while (!path.isDone() && isReached(groundWaypoint())) {
            path.advance();
            lastWaypointDistance = Double.MAX_VALUE;
            overshootTicks = 0;
        }
        if (path.isDone()) {
            onPathFinished();
            return;
        }
        roundCorner();
        Vec3 waypoint = groundWaypoint();
        double dx = waypoint.x - entity.getX();
        double dz = waypoint.z - entity.getZ();
        // The route assumes the NPC is where it planned; a fall or a knock away breaks that. Brand the
        // climb it missed and replan from where it actually stands instead of shoving at a ledge it
        // can no longer reach.
        if (entity.onGround() && (waypoint.y - entity.getY() > FELL_BELOW_WAYPOINT
                || dx * dx + dz * dz > OFF_ROUTE_DISTANCE * OFF_ROUTE_DISTANCE)) {
            if (waypoint.y - entity.getY() > FELL_BELOW_WAYPOINT) {
                markObstacleAhead(waypoint);
            }
            if (++replans > MAX_REPLANS || !plan()) {
                stop();
            }
            return;
        }
        // Engaged with a door on the route: keep shoving forward through (or against) it and face it,
        // without counting this as being stuck — the controller opens or breaks the door and glitches
        // the NPC through, so a door slammed shut on it can never trap it.
        if (doorController.handle(waypoint)) {
            faceTowards(dx, dz);
            entity.setSpeed(walkAttributeSpeed());
            entity.zza = 1.0F;
            entity.xxa = 0.0F;
            stuckTicks = 0;
            lastX = entity.getX();
            lastZ = entity.getZ();
            return;
        }
        // Fast NPCs overshoot the acceptance radius between ticks and would orbit the waypoint
        // forever; a waypoint that keeps getting further away while close is treated as passed.
        double waypointDistance = Math.sqrt(dx * dx + dz * dz);
        if (waypointDistance > lastWaypointDistance + 1.0E-3 && waypointDistance < 2.5) {
            if (++overshootTicks >= OVERSHOOT_TICKS) {
                path.advance();
                lastWaypointDistance = Double.MAX_VALUE;
                overshootTicks = 0;
                return;
            }
        } else {
            overshootTicks = 0;
        }
        lastWaypointDistance = waypointDistance;

        faceTowards(dx, dz);
        entity.setSpeed(walkAttributeSpeed());
        entity.zza = turnThrottle(dx, dz);
        entity.xxa = 0.0F;
        boolean needsRise = waypoint.y - entity.getY() > 0.6
                || entity.horizontalCollision && (entity.isInWater() || isStepAhead(dx, dz));
        if (entity.isInWater()) {
            // Vanilla jumping in water only produces a weak swim-up nudge that never clears a
            // ledge; a real jump impulse lets the NPC hop out of water like a player does.
            entity.setJumping(false);
            if (needsRise && entity.getDeltaMovement().y < 0.1) {
                entity.setDeltaMovement(entity.getDeltaMovement().x, entity.configuredJumpVelocity(),
                        entity.getDeltaMovement().z);
            }
        } else {
            entity.setJumping(entity.onGround() && needsRise);
        }

        trackOverallProgress();
        double movedSquared = Mth.square(entity.getX() - lastX) + Mth.square(entity.getZ() - lastZ);
        lastX = entity.getX();
        lastZ = entity.getZ();
        if (movedSquared < 1.0E-4) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        if (stuckTicks > STUCK_TICKS_BEFORE_REPLAN) {
            stuckTicks = 0;
            markObstacleAhead(waypoint);
            lastWaypointDistance = Double.MAX_VALUE;
            if (++replans > MAX_REPLANS || !plan()) {
                stop();
            }
        }
    }

    // A body can only turn so fast, so the faster it runs the wider it swings; aimed at every grid corner
    // it overshoots the corner and loops back to it. Within its turning distance of the current waypoint
    // it aims at the next one instead, when that is a straight walk away, so corners are rounded rather
    // than orbited. A slow NPC's turning distance is under a block, so its route shape barely changes.
    private void roundCorner() {
        double turnRadius = entity.getDeltaMovement().horizontalDistance() / (turnRate() * Mth.DEG_TO_RAD);
        double lookAhead = Math.max(reachRadius(), 2.0 * turnRadius + 0.3);
        List<Vec3> waypoints = path.waypoints();
        for (int skipped = 0; skipped < 3 && path.currentIndex() + 1 < waypoints.size(); skipped++) {
            Vec3 next = waypoints.get(path.currentIndex() + 1);
            if (horizontalDistanceTo(groundWaypoint()) >= lookAhead
                    || !routeWorld.isLineWalkable(anchorCell(), BlockPos.containing(next))) {
                return;
            }
            path.advance();
            lastWaypointDistance = Double.MAX_VALUE;
            overshootTicks = 0;
        }
    }

    // Overall progress uses the same horizontal+half-vertical metric as the planner heuristic,
    // so climbing counts as progress even when the horizontal distance briefly grows.
    private void trackOverallProgress() {
        double distance = weightedDistanceToTarget();
        if (distance + 0.25 < bestDistanceToTarget) {
            bestDistanceToTarget = distance;
            staleChains = 0;
            replans = 0;
        }
    }

    // A finished (usually partial) path chains into the next plan for as long as the route as a
    // whole keeps improving; a few stale chains are tolerated because repositioning does not
    // always get measurably closer. Scripted moves (commands, API) get extra persistence — the
    // creator explicitly asked for that destination.
    private void onPathFinished() {
        if (horizontalDistanceToTarget() <= ARRIVE_DISTANCE
                && Math.abs(target.y - entity.getY()) <= ARRIVE_VERTICAL) {
            stop();
            return;
        }
        int maxStaleChains = scripted ? MAX_STALE_CHAINS_SCRIPTED : MAX_STALE_CHAINS;
        if (++staleChains > maxStaleChains || !plan()) {
            stop();
        }
    }

    // Builds a fresh 3D route to the target for a swimming or flying NPC and remembers it as the
    // active path. The medium decides what counts as open water/air, and the height preference
    // decides the goal's y: an NPC cruises at its preferred height/depth until it is close, then
    // gives way to the target — a flyer fully unless its height is fixed, a swimmer fully when
    // allowed to leave its depth, within the tolerance band when holding it. The selected
    // pathfinding type drives the 3D planner exactly as it drives the ground one.
    private boolean planVolume() {
        if (target == null) {
            return false;
        }
        NpcMovementSettings movement = entity.movementSettings();
        PathfindingType type = PathfindingType.byId(movement.pathfindingType());
        if (type == null) {
            return false;
        }
        boolean water = isWaterMover();
        NpcHeightPreference preference = entity.effectiveHeightPreference(water);
        VolumeWorldView world = volumeWorldView();
        BlockPos ePos = entity.blockPosition();
        BlockPos startPos = world.snapToOpen(ePos.getX(), ePos.getY(), ePos.getZ(), START_SNAP_RADIUS);
        if (startPos == null) {
            startPos = ePos;
        }
        double goalY = water ? swimGoalY(preference, target) : flightGoalY(preference, target);
        BlockPos goalHint = new BlockPos(Mth.floor(target.x), Mth.floor(goalY), Mth.floor(target.z));
        BlockPos goalPos = world.snapToOpen(goalHint.getX(), goalHint.getY(), goalHint.getZ(),
                GOAL_SNAP_RADIUS);
        if (goalPos == null) {
            return false;
        }
        path = type.createVolumePlanner().plan(world, startPos, goalPos);
        stuckTicks = 0;
        lastX = entity.getX();
        lastY = entity.getY();
        lastZ = entity.getZ();
        return path != null;
    }

    // The depth a water NPC aims for: it cruises at its preferred depth while still closing the
    // horizontal gap, then within combat range leaves that depth to meet the target's — anywhere in
    // the water when it may leave its depth (unfixed), only within the tolerance band around the
    // preferred depth when it holds it (fixed). Never so shallow that its body breaks the surface.
    private double swimGoalY(NpcHeightPreference preference, Vec3 targetPos) {
        if (horizontalDistanceTo(targetPos) > APPROACH_DISTANCE) {
            return preferredSwimY();
        }
        return giveWaySwimY(preference, targetPos.y);
    }

    private double giveWaySwimY(NpcHeightPreference preference, double targetY) {
        double desired = targetY;
        if (preference.fixed()) {
            double preferredY = waterColumnSurface() - entity.effectiveWaterDepth();
            desired = Mth.clamp(targetY, preferredY - preference.range(), preferredY + preference.range());
        }
        return clampToWaterColumn(desired);
    }

    // The depth a water NPC holds while standing its ground against a combat target — the very same
    // give-way rule the path goal uses, so the idle drift can never disagree with where the route
    // just carried it. Without this a swimmer that stops chasing (target within reach) drifts back
    // to its preferred depth and ends up fighting from underneath the target.
    public double combatSwimY(Vec3 combatTargetPos) {
        return giveWaySwimY(entity.effectiveHeightPreference(true), combatTargetPos.y);
    }

    // The altitude a flyer holds while hovering against a combat target — the same give-way rule the
    // flight path goal uses, so the idle hover can never disagree with where the route carried it. The
    // air twin of combatSwimY.
    public double combatFlightY(Vec3 combatTargetPos) {
        return flightGoalY(entity.effectiveHeightPreference(false), combatTargetPos);
    }

    // Whether the target's depth is inside the band a depth-holding swimmer fights in (preferred ±
    // tolerance + margin). Always true when the swimmer may leave its depth — the band is fixed-only.
    public boolean depthWithinHoldBand(double targetY, double margin) {
        NpcHeightPreference preference = entity.effectiveHeightPreference(true);
        if (!preference.fixed()) {
            return true;
        }
        double preferredY = waterColumnSurface() - entity.effectiveWaterDepth();
        return Math.abs(targetY - preferredY) <= preference.range() + margin;
    }

    // The cell a water NPC strikes from: one block out from the target in the nearest cardinal
    // direction whose water fits the whole body, at the target's depth. Null if no side cell fits.
    public Vec3 waterCombatStandoff(LivingEntity prey) {
        double fightY = combatSwimY(prey.position());
        int cellY = Mth.floor(fightY);
        BlockPos preyCell = prey.blockPosition();
        VolumeWorldView world = volumeWorldView();
        Vec3 best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos cell = preyCell.relative(direction);
            if (!world.fits(cell.getX(), cellY, cell.getZ())
                    || !entity.level().getFluidState(new BlockPos(cell.getX(), cellY, cell.getZ()))
                            .is(FluidTags.WATER)) {
                continue;
            }
            Vec3 spot = new Vec3(cell.getX() + 0.5, fightY, cell.getZ() + 0.5);
            double distanceSq = entity.position().distanceToSqr(spot);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = spot;
            }
        }
        return best;
    }

    // A swimmer travels water (unless the creator turned water-avoidance on, an extreme choice);
    // lava is a wall for both so an NPC never routes itself into it, and air is always open but
    // taxed for a swimmer so it only breaches near the target. A flyer's home medium is the air.
    private VolumeWorldView volumeWorldView() {
        NpcMovementSettings movement = entity.movementSettings();
        NpcTraitsSettings traits = entity.traitsSettings();
        boolean water = isWaterMover();
        VolumeWorldView world = new VolumeWorldView(entity.level(), movement.avoidedBlocks(),
                water && !traits.avoidWater(), false, bodyClearance(), bodyFootprintRadius(),
                water ? OUT_OF_MEDIUM_PENALTY : 0.0F, water ? WATER_BREACH_DEPTH : 0);
        world.avoidCells(stuckCells);
        return world;
    }

    // The shallowest a swimmer may float: its whole hitbox height below the surface, so a big model
    // never pokes out — a ~1-block fish sits 1 block under, a 5-block one 5 under. At least one block.
    private double submersionDepth() {
        return Math.max(1.0, entity.getBbHeight());
    }

    // A flyer's cruising altitude: its flight height above the terrain beneath it.
    private double flightCruiseY() {
        int groundY = entity.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                Mth.floor(entity.getX()), Mth.floor(entity.getZ()));
        return groundY + entity.effectiveFlightHeight();
    }

    // Where a flyer wants to float when idle: its cruising altitude above the terrain below it.
    public double preferredFlightY() {
        return flightCruiseY();
    }

    // The height a flyer's route aims for: it cruises at its flight height while still closing the
    // horizontal gap, or whenever it holds a fixed height, then gives way to the target's own height on
    // approach when the height is not fixed. Shared by the path goal and the idle hover so they agree.
    private double flightGoalY(NpcHeightPreference preference, Vec3 goalTarget) {
        if (goalTarget == null || preference.fixed() || horizontalDistanceTo(goalTarget) > APPROACH_DISTANCE) {
            return flightCruiseY();
        }
        return goalTarget.y;
    }

    // Follows the active 3D path by steering the NPC's velocity straight at the current waypoint in
    // all three axes, so it swims/flies around obstacles, up over ledges and out of the water as the
    // route dictates instead of shoving itself into whatever sits between it and the target.
    private void tickVolumePath() {
        if (path == null) {
            if (!planVolume()) {
                stop();
            }
            return;
        }
        while (!path.isDone() && isReached3D(path.currentWaypoint())) {
            path.advance();
        }
        if (path.isDone()) {
            onVolumePathFinished();
            return;
        }
        Vec3 waypoint = path.currentWaypoint();
        double step = blocksPerSecond() / 20.0;
        double dx = waypoint.x - entity.getX();
        double dy = waypoint.y - entity.getY();
        double dz = waypoint.z - entity.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double scale = distance < 1.0E-4 ? 0.0 : Math.min(step, distance) / distance;
        double vy = dy * scale;
        // A wall right ahead (a stale detour, or a gap a hair too tight for the body): rise over it
        // rather than grind against it. If that does not clear it, the stuck timer below forces a
        // real replan that brands the blocking cell so the next route goes around it.
        if (entity.horizontalCollision) {
            vy = step;
        }
        // Ease the velocity toward the waypoint instead of snapping straight to it, so a course change
        // curves through rather than jerking the body around like a robot. Steady straight-line travel
        // still reaches full speed since the target velocity stops changing.
        Vec3 desired = new Vec3(dx * scale, vy, dz * scale);
        Vec3 current = entity.getDeltaMovement();
        entity.setDeltaMovement(current.add(desired.subtract(current).scale(steerAccel())));
        faceTowards(dx, dz);

        trackOverallProgress();
        double movedSquared = Mth.square(entity.getX() - lastX) + Mth.square(entity.getY() - lastY)
                + Mth.square(entity.getZ() - lastZ);
        lastX = entity.getX();
        lastY = entity.getY();
        lastZ = entity.getZ();
        if (movedSquared < 1.0E-4) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        if (stuckTicks > STUCK_TICKS_BEFORE_REPLAN) {
            stuckTicks = 0;
            markVolumeObstacle(waypoint);
            if (++replans > MAX_REPLANS || !planVolume()) {
                stop();
            }
        }
    }

    // A finished volume path either means arrival (stop) or a partial route that got as close as the
    // budget allowed — chain into a fresh plan for as long as the trip as a whole keeps improving,
    // exactly like the ground planner chains its partial walks.
    private void onVolumePathFinished() {
        if (horizontalDistanceToTarget() <= ARRIVE_DISTANCE
                && Math.abs(target.y - entity.getY()) <= ARRIVE_VERTICAL) {
            stop();
            return;
        }
        int maxStaleChains = scripted ? MAX_STALE_CHAINS_SCRIPTED : MAX_STALE_CHAINS;
        if (++staleChains > maxStaleChains || !planVolume()) {
            stop();
        }
    }

    private boolean isReached3D(Vec3 waypoint) {
        double dx = waypoint.x - entity.getX();
        double dy = waypoint.y - entity.getY();
        double dz = waypoint.z - entity.getZ();
        double reach = Mth.clamp(entity.getDeltaMovement().length() * 1.5, 0.6, 1.2);
        return dx * dx + dy * dy + dz * dz < reach * reach;
    }

    // Brands the cell the NPC keeps failing to move into — in 3D, so a ceiling or floor obstacle is
    // remembered as readily as a wall — feeding the next plan so it routes around instead of retrying.
    private void markVolumeObstacle(Vec3 waypoint) {
        int sx = (int) Math.signum(waypoint.x - entity.getX());
        int sy = (int) Math.signum(waypoint.y - entity.getY());
        int sz = (int) Math.signum(waypoint.z - entity.getZ());
        if (sx == 0 && sy == 0 && sz == 0) {
            return;
        }
        if (stuckCells.size() >= MAX_STUCK_CELLS) {
            stuckCells.clear();
        }
        stuckCells.add(entity.blockPosition().offset(sx, sy, sz).asLong());
    }

    // Where a water NPC wants to float when idle: its preferred depth below the surface, but never
    // inside the floor, and never so shallow its body breaks the surface (deeper for a bigger model).
    public double preferredSwimY() {
        return clampToWaterColumn(waterColumnSurface() - entity.effectiveWaterDepth());
    }

    private double clampToWaterColumn(double desired) {
        double floor = waterColumnFloor() + 0.2;
        double ceiling = waterColumnSurface() - submersionDepth();
        // Water too shallow to submerge the whole body: sit as deep as it can (the routing penalty
        // keeps the NPC out of such shallows in the first place).
        return ceiling < floor ? floor : Mth.clamp(desired, floor, ceiling);
    }

    private double waterColumnSurface() {
        BlockPos.MutableBlockPos cursor = entity.blockPosition().mutable();
        while (entity.level().getFluidState(cursor).is(FluidTags.WATER)) {
            cursor.move(0, 1, 0);
        }
        return cursor.getY();
    }

    private double waterColumnFloor() {
        BlockPos.MutableBlockPos cursor = entity.blockPosition().mutable();
        while (entity.level().getFluidState(cursor).is(FluidTags.WATER)) {
            cursor.move(0, -1, 0);
        }
        return cursor.getY() + 1;
    }

    private void faceTowards(double dx, double dz) {
        float desiredYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float yaw = Mth.approachDegrees(entity.getYRot(), desiredYaw, turnRate());
        entity.setYRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);
    }

    // Full forward input while still turning towards a sharp corner swings a fast or slow-turning body
    // wide past the waypoint (it then orbits it) or shoves it into the wall it is turning along, so the
    // input eases off with the heading error: full ahead within 30°, a crawl when facing away.
    private float turnThrottle(double dx, double dz) {
        float desiredYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float error = Mth.degreesDifferenceAbs(entity.getYRot(), desiredYaw);
        if (error <= 30.0F) {
            return 1.0F;
        }
        return Math.max(0.1F, Mth.cos((error - 30.0F) * Mth.DEG_TO_RAD * 1.5F));
    }

    // A one-block step straight ahead (solid at the feet, open above it) is worth a jump. Brushing a
    // wall the route runs along is not: airborne, the NPC loses its ground grip and crawls along the face.
    private boolean isStepAhead(double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3) {
            return false;
        }
        double probe = entity.getBbWidth() * 0.5 + 0.3;
        BlockPos feet = BlockPos.containing(entity.getX() + dx / length * probe, entity.getY() + 0.1,
                entity.getZ() + dz / length * probe);
        Level level = entity.level();
        return !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    // Bigger bodies turn (and steer) more slowly, so they ease around a corner instead of snapping; a
    // small mob stays nimble. bodySize averages width and height so both a long and a tall NPC count.
    private float bodySize() {
        return Math.max(1.0F, (entity.getBbWidth() + entity.getBbHeight()) * 0.5F);
    }

    private float turnRate() {
        return Math.max(MIN_TURN_DEGREES_PER_TICK, TURN_DEGREES_PER_TICK / bodySize());
    }

    private double steerAccel() {
        return Math.max(MIN_STEER_ACCEL, STEER_ACCEL / bodySize());
    }

    private float walkAttributeSpeed() {
        if (running) {
            return NpcMovementSettings.speedAttributeFromBlocksPerSecond(
                    entity.movementSettings().effectiveRunSpeed());
        }
        return (float) entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    private double blocksPerSecond() {
        return running ? entity.movementSettings().effectiveRunSpeed()
                : entity.movementSettings().walkSpeed();
    }

    private boolean isReached(Vec3 waypoint) {
        double dx = waypoint.x - entity.getX();
        double dz = waypoint.z - entity.getZ();
        double reach = reachRadius();
        return dx * dx + dz * dz < reach * reach && Math.abs(waypoint.y - entity.getY()) < 1.2;
    }

    // The acceptance radius grows with the actual per-tick travel so fast NPCs cannot orbit a
    // waypoint they cross between two ticks.
    private double reachRadius() {
        return Mth.clamp(entity.getDeltaMovement().horizontalDistance() * 1.5, 0.5, 1.1);
    }

    private double horizontalDistanceToTarget() {
        return horizontalDistanceTo(target);
    }

    private double horizontalDistanceTo(Vec3 pos) {
        double dx = pos.x - entity.getX();
        double dz = pos.z - entity.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double weightedDistanceToTarget() {
        if (target == null) {
            return Double.MAX_VALUE;
        }
        return horizontalDistanceToTarget() + Math.abs(target.y - entity.getY()) * 0.5;
    }

    private void clearInputs() {
        entity.zza = 0.0F;
        entity.xxa = 0.0F;
        entity.setJumping(false);
    }

    // With pathfinding_debug enabled (config/luna_npc.json, read once at startup) the active
    // task is drawn for nearby players: green = route ahead, end rod = current waypoint, flame =
    // final target (orange for scripted command/API targets, blue soul flame for wander targets).
    private void emitDebugParticles() {
        if (!LunaNpcConfig.pathfindingDebug() || entity.tickCount % 5 != 0
                || !(entity.level() instanceof ServerLevel level)
                || level.getNearestPlayer(entity, 100.0) == null) {
            return;
        }
        if (path != null) {
            List<Vec3> waypoints = path.waypoints();
            for (int index = path.currentIndex(); index < waypoints.size(); index++) {
                Vec3 waypoint = isVolumeMover() ? waypoints.get(index)
                        : waypoints.get(index).add(cellOffset, 0.0, cellOffset);
                level.sendParticles(index == path.currentIndex()
                                ? ParticleTypes.END_ROD : ParticleTypes.HAPPY_VILLAGER,
                        waypoint.x, waypoint.y + 0.3, waypoint.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        level.sendParticles(scripted ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME,
                target.x, target.y + 1.0, target.z, 3, 0.1, 0.3, 0.1, 0.0);
    }
}
