package net.lunacore.luna_npc.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.lunacore.luna_npc.api.ZoneShape;

// Client-side render + interaction state for the Zone Wand. Holds the shape being placed right now
// (its type and the points clicked so far) and the set of saved zones the player has toggled on for
// border inspection. The world border renderer reads this every frame; it is cleared when placement
// finishes or the tool is dropped.
public final class ClientZoneRender {

    private static boolean placing;
    private static ZoneShape.Type type = ZoneShape.Type.BOX;
    private static final List<BlockPos> points = new ArrayList<>();
    private static final Set<String> inspected = new LinkedHashSet<>();

    private ClientZoneRender() {
    }

    // Begin placing a new shape of the given type; any half-placed shape is discarded.
    public static void startPlacing(ZoneShape.Type shapeType) {
        placing = true;
        type = shapeType;
        points.clear();
    }

    public static boolean isPlacing() {
        return placing;
    }

    public static ZoneShape.Type type() {
        return type;
    }

    public static void addPoint(BlockPos pos) {
        if (points.size() < ZoneShape.MAX_ANCHORS) {
            points.add(pos.immutable());
        }
    }

    public static List<BlockPos> points() {
        return List.copyOf(points);
    }

    public static int pointCount() {
        return points.size();
    }

    public static void finishPlacing() {
        placing = false;
        points.clear();
    }

    public static void toggleInspect(String zoneId) {
        if (!inspected.remove(zoneId)) {
            inspected.add(zoneId);
        }
    }

    public static boolean isInspected(String zoneId) {
        return inspected.contains(zoneId);
    }

    public static Set<String> inspected() {
        return Set.copyOf(inspected);
    }

    public static void clearInspect() {
        inspected.clear();
    }
}
