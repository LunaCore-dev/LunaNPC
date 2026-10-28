package net.lunacore.luna_npc.client;

import java.util.ArrayList;
import java.util.List;
import net.lunacore.luna_npc.api.NpcRoute;

// Client-side cache of the server's patrol routes, refreshed by RoutesPayload on join and on every
// change. The Route Creator screens, the patrol variant editor and the route renderer read from here.
public final class ClientRoutes {

    private static List<NpcRoute> routes = List.of();

    private ClientRoutes() {
    }

    public static void set(List<NpcRoute> updated) {
        routes = List.copyOf(updated);
    }

    public static List<NpcRoute> all() {
        return routes;
    }

    public static NpcRoute byId(String routeId) {
        for (NpcRoute route : routes) {
            if (route.id().equals(routeId)) {
                return route;
            }
        }
        return null;
    }

    public static String nameOf(String routeId) {
        NpcRoute route = byId(routeId);
        return route == null ? routeId : route.name();
    }

    public static List<NpcRoute> editableCopy() {
        return new ArrayList<>(routes);
    }
}
