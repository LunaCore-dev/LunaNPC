package net.lunacore.luna_npc.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;

// Client-side placement + inspection state for the Route Creator stick: the points clicked so far for a
// new route (or for one being extended, whose saved points come first), and the saved routes toggled on
// for in-world display. RouteRenderer reads this every frame.
public final class ClientRouteRender {

    private static boolean placing;
    // The route being extended, or null while placing a brand-new route.
    private static String extendingRouteId;
    private static int savedPointCount;
    private static final List<BlockPos> points = new ArrayList<>();
    private static final Set<String> inspected = new LinkedHashSet<>();

    private ClientRouteRender() {
    }

    public static void startNewRoute() {
        placing = true;
        extendingRouteId = null;
        savedPointCount = 0;
        points.clear();
    }

    public static void startExtending(String routeId, List<BlockPos> existing) {
        placing = true;
        extendingRouteId = routeId;
        points.clear();
        points.addAll(existing);
        savedPointCount = existing.size();
    }

    public static boolean isPlacing() {
        return placing;
    }

    public static String extendingRouteId() {
        return extendingRouteId;
    }

    public static void addPoint(BlockPos pos) {
        points.add(pos.immutable());
    }

    public static List<BlockPos> points() {
        return List.copyOf(points);
    }

    public static List<BlockPos> newPoints() {
        return List.copyOf(points.subList(savedPointCount, points.size()));
    }

    public static int pointCount() {
        return points.size();
    }

    public static void finishPlacing() {
        placing = false;
        extendingRouteId = null;
        savedPointCount = 0;
        points.clear();
    }

    public static void toggleInspect(String routeId) {
        if (!inspected.remove(routeId)) {
            inspected.add(routeId);
        }
    }

    public static boolean isInspected(String routeId) {
        return inspected.contains(routeId);
    }

    public static Set<String> inspected() {
        return Set.copyOf(inspected);
    }
}
