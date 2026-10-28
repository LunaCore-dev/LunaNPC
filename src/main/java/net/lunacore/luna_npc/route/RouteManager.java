package net.lunacore.luna_npc.route;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.NpcRoute;
import net.lunacore.luna_npc.api.NpcRoutePoint;
import net.lunacore.luna_npc.api.NpcRouteSpeed;
import net.lunacore.luna_npc.api.RouteRegistry;
import net.lunacore.luna_npc.npc.NpcValidation;

// The world's named patrol routes, created with the Route Creator stick. A global registry persisted to
// routes.dat and synced to clients; patrol jobs reference routes by id. Mirrors ZoneManager.
public final class RouteManager extends SavedData implements RouteRegistry {

    private static final Codec<RouteManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcRoute.CODEC.listOf().optionalFieldOf("routes", List.of())
                    .forGetter(manager -> List.copyOf(manager.routes.values()))
    ).apply(instance, RouteManager::new));

    private static final SavedDataType<RouteManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "routes"),
            RouteManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<String, NpcRoute> routes = new LinkedHashMap<>();

    private RouteManager() {
    }

    private RouteManager(List<NpcRoute> stored) {
        for (NpcRoute route : stored) {
            routes.put(route.id(), route);
        }
    }

    public static RouteManager get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    @Override
    public List<NpcRoute> all() {
        return List.copyOf(routes.values());
    }

    @Override
    public NpcRoute byId(String routeId) {
        return routes.get(routeId);
    }

    // Replaces the whole set from a client edit: keeps valid, uniquely-id'd routes within the GUI point
    // cap, and sanitises names.
    public void applyAll(List<NpcRoute> incoming) {
        Map<String, NpcRoute> next = new LinkedHashMap<>();
        for (NpcRoute route : incoming) {
            if (next.containsKey(route.id()) || next.size() >= NpcValidation.MAX_ROUTES
                    || route.size() > NpcValidation.MAX_ROUTE_POINTS_GUI || !route.isValid()) {
                continue;
            }
            next.put(route.id(), route.withName(sanitizeName(route.name())));
        }
        routes.clear();
        routes.putAll(next);
        setDirty();
    }

    private static String sanitizeName(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.length() > NpcValidation.MAX_ROUTE_NAME_LENGTH) {
            trimmed = trimmed.substring(0, NpcValidation.MAX_ROUTE_NAME_LENGTH);
        }
        return trimmed.isEmpty() ? "Route" : trimmed;
    }

    @Override
    public NpcRoute create(String name, String dimension, List<NpcRoutePoint> points, NpcRouteSpeed speed) {
        if (routes.size() >= NpcValidation.MAX_ROUTES) {
            throw new IllegalStateException("Route limit of " + NpcValidation.MAX_ROUTES + " reached");
        }
        NpcRoute route = new NpcRoute(freshId(name), sanitizeName(name), dimension, points, speed);
        requireValid(route);
        routes.put(route.id(), route);
        setDirty();
        return route;
    }

    @Override
    public void rename(String routeId, String name) {
        NpcRoute route = routes.get(routeId);
        if (route != null) {
            routes.put(routeId, route.withName(sanitizeName(name)));
            setDirty();
        }
    }

    @Override
    public void delete(String routeId) {
        if (routes.remove(routeId) != null) {
            setDirty();
        }
    }

    @Override
    public void update(NpcRoute route) {
        if (route != null && routes.containsKey(route.id())) {
            requireValid(route);
            routes.put(route.id(), route.withName(sanitizeName(route.name())));
            setDirty();
        }
    }

    private static void requireValid(NpcRoute route) {
        if (!route.isValid()) {
            throw new IllegalArgumentException("Route '" + route.name() + "' breaks the route rules: "
                    + NpcValidation.MIN_ROUTE_POINTS + "-" + NpcValidation.MAX_ROUTE_POINTS + " points, each "
                    + NpcValidation.MIN_ROUTE_POINT_SPACING + "-" + NpcValidation.MAX_ROUTE_POINT_SPACING
                    + " blocks from the previous and within " + NpcValidation.MAX_ROUTE_ANCHOR_DISTANCE
                    + " of the first");
        }
    }

    // A unique id derived from the name (lowercased alphanumerics), suffixed until free.
    private String freshId(String name) {
        String base = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (base.isEmpty()) {
            base = "route";
        }
        String id = base;
        int suffix = 2;
        while (routes.containsKey(id)) {
            id = base + suffix++;
        }
        return id;
    }
}
