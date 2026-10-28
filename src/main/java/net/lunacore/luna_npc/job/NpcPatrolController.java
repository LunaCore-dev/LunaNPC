package net.lunacore.luna_npc.job;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.api.LunaNpcEvents;
import net.lunacore.luna_npc.api.NpcJobType;
import net.lunacore.luna_npc.api.NpcPatrolSubType;
import net.lunacore.luna_npc.api.NpcRoute;
import net.lunacore.luna_npc.api.NpcRoutePoint;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.route.RouteManager;

// The PATROL job: walks the rolled variant's saved route point to point in its mode (loop, ping-pong or
// random), waits at each point (facing its look direction when it has one) and fires the point-reached
// event. Combat, fear, a menu or a scripted move pause it; afterwards it carries on from the nearest
// point. The route is read live, so a route edit applies at once. Ticked from NpcEntity after farming.
public final class NpcPatrolController {

    private static final String INDEX_KEY = "luna_patrol_index";
    private static final String DIRECTION_KEY = "luna_patrol_direction";
    private static final double ARRIVE_HORIZONTAL_SQ = 1.0;
    private static final double ARRIVE_VERTICAL = 1.5;
    // A failed plan waits this long before retrying; a point that keeps failing is skipped so one
    // blocked point never stalls the whole route.
    private static final int REPLAN_COOLDOWN_TICKS = 40;
    private static final int FAILURES_BEFORE_SKIP = 3;

    private final NpcEntity entity;
    private int pointIndex = -1;
    private int direction = 1;
    // The point the current leg started from; its run flag sets the leg's speed.
    private int legStartIndex = -1;
    private int waitTicks;
    private boolean waiting;
    private boolean resumeAtNearest = true;
    private int replanCooldown;
    private int failedPlans;
    // The route this NPC walked last tick, so an edit that removes points or a swapped route restarts it
    // at the nearest point instead of an index that no longer exists.
    private String walkedRouteId = "";
    private int walkedRouteSize;

    public NpcPatrolController(NpcEntity entity) {
        this.entity = entity;
    }

    // True while the NPC has a route to walk, so the wander controller stays out of the way.
    public boolean isWorking() {
        return activeSubType() != null && activeRoute() != null;
    }

    // True while waiting at a point that sets a look direction, so the idle look-around keeps still.
    public boolean holdsLookDirection() {
        NpcRoute route = activeRoute();
        return waiting && route != null && pointIndex >= 0 && pointIndex < route.size()
                && route.point(pointIndex).look();
    }

    public String routeId() {
        NpcPatrolSubType subType = activeSubType();
        return subType == null ? "" : subType.routeId();
    }

    public int pointIndex() {
        return pointIndex;
    }

    // Sends the NPC to a specific point next (API). Out-of-range indexes are ignored.
    public void setPointIndex(int index) {
        NpcRoute route = activeRoute();
        if (route == null || index < 0 || index >= route.size()) {
            return;
        }
        pointIndex = index;
        legStartIndex = index;
        waiting = false;
        resumeAtNearest = false;
        failedPlans = 0;
        entity.navigation().stop();
    }

    private NpcPatrolSubType activeSubType() {
        if (entity.jobSettings().jobType() != NpcJobType.PATROL || !entity.jobActive()) {
            return null;
        }
        int rolled = entity.rolledJobSubType();
        var subTypes = entity.jobSettings().patrolSubTypes();
        return rolled >= 0 && rolled < subTypes.size() ? subTypes.get(rolled) : null;
    }

    private NpcRoute activeRoute() {
        NpcPatrolSubType subType = activeSubType();
        if (subType == null || !(entity.level() instanceof ServerLevel level)) {
            return null;
        }
        NpcRoute route = RouteManager.get(level.getServer()).byId(subType.routeId());
        if (route == null || route.size() == 0
                || !route.dimension().equals(level.dimension().identifier().toString())) {
            return null;
        }
        return route;
    }

    public void tick() {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        NpcPatrolSubType subType = activeSubType();
        NpcRoute route = activeRoute();
        if (subType == null || route == null) {
            waiting = false;
            return;
        }
        if (entity.combatController().isFighting() || entity.combatController().isFleeing()
                || entity.isHoldingForInteraction()
                || (entity.navigation().isNavigating() && entity.navigation().isScripted())) {
            resumeAtNearest = true;
            waiting = false;
            return;
        }
        if (!route.id().equals(walkedRouteId) || route.size() != walkedRouteSize) {
            walkedRouteId = route.id();
            walkedRouteSize = route.size();
            resumeAtNearest |= pointIndex < 0 || pointIndex >= route.size();
        }
        if (resumeAtNearest) {
            pointIndex = route.nearestPointIndex(entity.getX(), entity.getY(), entity.getZ());
            legStartIndex = pointIndex;
            resumeAtNearest = false;
            waiting = false;
            failedPlans = 0;
        }
        NpcRoutePoint point = route.point(pointIndex);
        if (waiting) {
            tickWaiting(route, subType, point);
            return;
        }
        Vec3 destination = Vec3.atBottomCenterOf(point.pos());
        if (hasArrived(destination)) {
            arrive(route, subType, point);
            return;
        }
        if (entity.navigation().isNavigating()) {
            return;
        }
        if (replanCooldown > 0) {
            replanCooldown--;
            return;
        }
        entity.chunkLoader().keepDestinationLoaded(level, point.pos());
        // Never plan into an unloaded chunk: reading its blocks would load it synchronously on the
        // server thread. The NPC waits at the edge until the chunk is loaded again.
        if (!level.isLoaded(point.pos())) {
            replanCooldown = REPLAN_COOLDOWN_TICKS;
            return;
        }
        int legStart = legStartIndex >= 0 && legStartIndex < route.size() ? legStartIndex : pointIndex;
        if (!entity.navigation().moveTo(destination, subType.runsFrom(route, legStart), false)) {
            replanCooldown = REPLAN_COOLDOWN_TICKS;
            if (++failedPlans >= FAILURES_BEFORE_SKIP) {
                failedPlans = 0;
                advance(route, subType);
            }
        }
    }

    private boolean hasArrived(Vec3 destination) {
        double dx = destination.x - entity.getX();
        double dz = destination.z - entity.getZ();
        return dx * dx + dz * dz <= ARRIVE_HORIZONTAL_SQ && Math.abs(destination.y - entity.getY()) <= ARRIVE_VERTICAL;
    }

    private void arrive(NpcRoute route, NpcPatrolSubType subType, NpcRoutePoint point) {
        entity.navigation().stop();
        failedPlans = 0;
        LunaNpcEvents.firePatrolPointReached(entity, route.id(), pointIndex);
        waitTicks = point.waitSeconds() * 20;
        waiting = true;
        if (waitTicks <= 0) {
            waiting = false;
            advance(route, subType);
        }
    }

    private void tickWaiting(NpcRoute route, NpcPatrolSubType subType, NpcRoutePoint point) {
        if (point.look()) {
            entity.setYRot(point.lookYaw());
            entity.setYHeadRot(point.lookYaw());
            entity.setYBodyRot(point.lookYaw());
        }
        if (--waitTicks <= 0) {
            waiting = false;
            advance(route, subType);
        }
    }

    private void advance(NpcRoute route, NpcPatrolSubType subType) {
        int size = route.size();
        if (size <= 1) {
            return;
        }
        legStartIndex = pointIndex;
        pointIndex = switch (subType.mode()) {
            case LOOP -> (pointIndex + 1) % size;
            case PING_PONG -> {
                if (pointIndex + direction < 0 || pointIndex + direction >= size) {
                    direction = -direction;
                }
                yield pointIndex + direction;
            }
            case RANDOM -> {
                int next = entity.getRandom().nextInt(size - 1);
                yield next >= pointIndex ? next + 1 : next;
            }
        };
    }

    public void save(ValueOutput output) {
        output.putInt(INDEX_KEY, pointIndex);
        output.putInt(DIRECTION_KEY, direction);
    }

    public void load(ValueInput input) {
        pointIndex = input.getIntOr(INDEX_KEY, -1);
        direction = input.getIntOr(DIRECTION_KEY, 1) < 0 ? -1 : 1;
        resumeAtNearest = pointIndex < 0;
    }
}
