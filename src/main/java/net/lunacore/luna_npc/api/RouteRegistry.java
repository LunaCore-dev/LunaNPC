package net.lunacore.luna_npc.api;

import java.util.List;
import net.minecraft.core.BlockPos;

// Developer access to the named patrol routes — everything the Route Creator stick and its screens do:
// create a route from points, list, rename, edit and delete. A patrol job references routes by id
// (NpcPatrolSubType.routeId), and every NPC walking that route picks up an edit live. Points must follow
// the route rules (see NpcRoute.placementProblem); create/update throw IllegalArgumentException otherwise.
public interface RouteRegistry {

    List<NpcRoute> all();

    NpcRoute byId(String routeId);

    // Creates a route with an id generated from the name. Throws IllegalStateException at the route limit.
    NpcRoute create(String name, String dimension, List<NpcRoutePoint> points, NpcRouteSpeed speed);

    void rename(String routeId, String name);

    void delete(String routeId);

    // Replaces the stored route sharing this one's id. No-op when no route has that id.
    void update(NpcRoute route);

    // ---- Convenience ----

    // A walking route through plain points (no waits, no look direction).
    default NpcRoute createFromPositions(String name, String dimension, List<BlockPos> positions) {
        return create(name, dimension, positions.stream().map(NpcRoutePoint::at).toList(), NpcRouteSpeed.WALK);
    }

    default NpcRoute byName(String name) {
        return all().stream().filter(route -> route.name().equals(name)).findFirst().orElse(null);
    }

    // The route with this name, creating it if none exists — no duplicate on re-run.
    default NpcRoute getOrCreate(String name, String dimension, List<NpcRoutePoint> points, NpcRouteSpeed speed) {
        NpcRoute existing = byName(name);
        return existing != null ? existing : create(name, dimension, points, speed);
    }
}
