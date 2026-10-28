package net.lunacore.luna_npc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.client.ClientZoneRender;
import net.lunacore.luna_npc.client.ClientZones;
import net.lunacore.luna_npc.api.NpcZone;
import net.lunacore.luna_npc.api.ZoneShape;

// Draws zone borders in the world as colored line outlines: the shape currently being placed with the
// Zone Wand (cyan preview + a marker at each clicked point) and any saved zones the player has toggled
// on for inspection (green). All state comes from ClientZoneRender / ClientZones; nothing is drawn when
// the wand is idle and no zone is inspected. register() is called once from the client init.
public final class ZoneRenderer {

    // Colors packed as 0xAARRGGBB.
    private static final int PENDING_COLOR = 0xFF22E0FF; // cyan — shape being placed
    private static final int SAVED_COLOR = 0xFF3DFF6E;   // green — inspected saved zones
    private static final int MARKER_COLOR = 0xFFFFE23D;  // amber — clicked placement points

    private static final float LINE_WIDTH = 2.0F;
    private static final double RENDER_RANGE = 64.0;   // only draw shapes within this of the camera
    private static final int CIRCLE_SEGMENTS = 48;     // ring/great-circle resolution
    private static final int VERTICAL_LINES = 8;       // side lines on a cylinder
    private static final double MARKER_HALF = 0.18;    // half-size of a placement-point marker cube

    private ZoneRenderer() {
    }

    public static void register() {
        // COLLECT_SUBMITS fires after vanilla has queued its geometry but before the feature render pass
        // runs, so custom geometry submitted here is actually drawn this frame (later events fire after
        // that pass and would be too late).
        LevelRenderEvents.COLLECT_SUBMITS.register(ZoneRenderer::onCollectSubmits);
    }

    private static void onCollectSubmits(LevelRenderContext context) {
        boolean placing = ClientZoneRender.isPlacing();
        Set<String> inspected = ClientZoneRender.inspected();
        if (!placing && inspected.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        // Subtract the MAIN camera's own interpolated position — the exact origin this frame's view
        // matrix is built from. The camera-render-state copy can lag it by a partial tick, and that
        // few-centimetre delta (invisible far away) is what makes the borders twitch when you stand
        // right next to them while moving.
        Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
        String dimension = mc.level.dimension().identifier().toString();

        // The captured pose stack is a fresh identity stack whose origin is the camera, so every vertex is
        // rendered camera-relative (world coordinate minus camera position). One custom-geometry submit
        // with RenderTypes.lines() carries every segment; color is per-vertex so all shapes share it.
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(),
                (pose, consumer) -> {
                    if (placing) {
                        List<BlockPos> points = ClientZoneRender.points();
                        for (BlockPos point : points) {
                            drawMarker(pose, consumer, point, cameraPos);
                        }
                        ZoneShape preview = buildPreview(ClientZoneRender.type(), points);
                        if (preview != null) {
                            drawShape(pose, consumer, preview, PENDING_COLOR, cameraPos);
                        }
                    }
                    if (!inspected.isEmpty()) {
                        for (NpcZone zone : ClientZones.all()) {
                            if (inspected.contains(zone.id()) && zone.dimension().equals(dimension)) {
                                drawShape(pose, consumer, zone.shape(), SAVED_COLOR, cameraPos);
                            }
                        }
                    }
                });
    }

    // Mirrors the Zone Wand's shape-from-points rules; returns null while there are too few points.
    private static ZoneShape buildPreview(ZoneShape.Type type, List<BlockPos> points) {
        return switch (type) {
            case BOX -> points.size() >= 2 ? new ZoneShape.Box(points.get(0), points.get(1)) : null;
            case CYLINDER -> {
                if (points.size() < 2) {
                    yield null;
                }
                BlockPos center = points.get(0);
                BlockPos edge = points.get(1);
                double dx = edge.getX() - center.getX();
                double dz = edge.getZ() - center.getZ();
                int radius = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                yield new ZoneShape.Cylinder(center, radius,
                        Math.min(center.getY(), edge.getY()), Math.max(center.getY(), edge.getY()));
            }
            case SPHERE -> {
                if (points.size() < 2) {
                    yield null;
                }
                BlockPos center = points.get(0);
                BlockPos edge = points.get(1);
                double dx = edge.getX() - center.getX();
                double dy = edge.getY() - center.getY();
                double dz = edge.getZ() - center.getZ();
                int radius = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
                yield new ZoneShape.Sphere(center, radius);
            }
            case POLYGON -> points.size() >= 3 ? new ZoneShape.Polygon(points) : null;
        };
    }

    private static void drawShape(PoseStack.Pose pose, VertexConsumer consumer, ZoneShape shape,
                                  int color, Vec3 camera) {
        // Cheap whole-shape cull: skip when even the nearest possible point (bounds center pulled in by
        // half the bounds diagonal) is beyond the render range. Segment counts are otherwise bounded, so
        // we never iterate a block volume.
        AABB bounds = shape.bounds();
        double centerX = (bounds.minX + bounds.maxX) * 0.5;
        double centerY = (bounds.minY + bounds.maxY) * 0.5;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
        double halfDiagonal = 0.5 * Math.sqrt(sq(bounds.maxX - bounds.minX)
                + sq(bounds.maxY - bounds.minY) + sq(bounds.maxZ - bounds.minZ));
        if (distance(centerX, centerY, centerZ, camera) > RENDER_RANGE + halfDiagonal) {
            return;
        }

        switch (shape) {
            case ZoneShape.Box box -> drawBox(pose, consumer, box.bounds(), color, camera);
            case ZoneShape.Cylinder cylinder -> drawCylinder(pose, consumer, cylinder, color, camera);
            case ZoneShape.Sphere sphere -> drawSphere(pose, consumer, sphere, color, camera);
            case ZoneShape.Polygon polygon -> drawPolygon(pose, consumer, polygon, color, camera);
        }
    }

    private static void drawMarker(PoseStack.Pose pose, VertexConsumer consumer, BlockPos point, Vec3 camera) {
        double cx = point.getX() + 0.5;
        double cy = point.getY() + 0.5;
        double cz = point.getZ() + 0.5;
        if (distance(cx, cy, cz, camera) > RENDER_RANGE) {
            return;
        }
        drawBox(pose, consumer, new AABB(cx - MARKER_HALF, cy - MARKER_HALF, cz - MARKER_HALF,
                cx + MARKER_HALF, cy + MARKER_HALF, cz + MARKER_HALF), MARKER_COLOR, camera);
    }

    // The 12 edges of a cuboid.
    static void drawBox(PoseStack.Pose pose, VertexConsumer consumer, AABB box, int color, Vec3 camera) {
        double x0 = box.minX;
        double y0 = box.minY;
        double z0 = box.minZ;
        double x1 = box.maxX;
        double y1 = box.maxY;
        double z1 = box.maxZ;
        // Bottom ring.
        line(pose, consumer, x0, y0, z0, x1, y0, z0, color, camera);
        line(pose, consumer, x1, y0, z0, x1, y0, z1, color, camera);
        line(pose, consumer, x1, y0, z1, x0, y0, z1, color, camera);
        line(pose, consumer, x0, y0, z1, x0, y0, z0, color, camera);
        // Top ring.
        line(pose, consumer, x0, y1, z0, x1, y1, z0, color, camera);
        line(pose, consumer, x1, y1, z0, x1, y1, z1, color, camera);
        line(pose, consumer, x1, y1, z1, x0, y1, z1, color, camera);
        line(pose, consumer, x0, y1, z1, x0, y1, z0, color, camera);
        // Verticals.
        line(pose, consumer, x0, y0, z0, x0, y1, z0, color, camera);
        line(pose, consumer, x1, y0, z0, x1, y1, z0, color, camera);
        line(pose, consumer, x1, y0, z1, x1, y1, z1, color, camera);
        line(pose, consumer, x0, y0, z1, x0, y1, z1, color, camera);
    }

    private static void drawCylinder(PoseStack.Pose pose, VertexConsumer consumer, ZoneShape.Cylinder cylinder,
                                     int color, Vec3 camera) {
        double radius = cylinder.radius();
        if (radius <= 0) {
            return;
        }
        double cx = cylinder.center().getX() + 0.5;
        double cz = cylinder.center().getZ() + 0.5;
        double yLow = cylinder.minY();
        double yHigh = cylinder.maxY() + 1;

        double prevX = cx + radius;
        double prevZ = cz;
        for (int i = 1; i <= CIRCLE_SEGMENTS; i++) {
            double angle = 2.0 * Math.PI * i / CIRCLE_SEGMENTS;
            double nextX = cx + radius * Math.cos(angle);
            double nextZ = cz + radius * Math.sin(angle);
            line(pose, consumer, prevX, yLow, prevZ, nextX, yLow, nextZ, color, camera);
            line(pose, consumer, prevX, yHigh, prevZ, nextX, yHigh, nextZ, color, camera);
            prevX = nextX;
            prevZ = nextZ;
        }
        for (int i = 0; i < VERTICAL_LINES; i++) {
            double angle = 2.0 * Math.PI * i / VERTICAL_LINES;
            double px = cx + radius * Math.cos(angle);
            double pz = cz + radius * Math.sin(angle);
            line(pose, consumer, px, yLow, pz, px, yHigh, pz, color, camera);
        }
    }

    // Three axis-aligned great circles through the sphere's center.
    private static void drawSphere(PoseStack.Pose pose, VertexConsumer consumer, ZoneShape.Sphere sphere,
                                   int color, Vec3 camera) {
        double radius = sphere.radius();
        if (radius <= 0) {
            return;
        }
        double cx = sphere.center().getX() + 0.5;
        double cy = sphere.center().getY() + 0.5;
        double cz = sphere.center().getZ() + 0.5;

        double prevCos = radius;
        double prevSin = 0.0;
        for (int i = 1; i <= CIRCLE_SEGMENTS; i++) {
            double angle = 2.0 * Math.PI * i / CIRCLE_SEGMENTS;
            double c = radius * Math.cos(angle);
            double s = radius * Math.sin(angle);
            // XY plane (constant z).
            line(pose, consumer, cx + prevCos, cy + prevSin, cz, cx + c, cy + s, cz, color, camera);
            // XZ plane (constant y).
            line(pose, consumer, cx + prevCos, cy, cz + prevSin, cx + c, cy, cz + s, color, camera);
            // YZ plane (constant x).
            line(pose, consumer, cx, cy + prevCos, cz + prevSin, cx, cy + c, cz + s, color, camera);
            prevCos = c;
            prevSin = s;
        }
    }

    private static void drawPolygon(PoseStack.Pose pose, VertexConsumer consumer, ZoneShape.Polygon polygon,
                                    int color, Vec3 camera) {
        List<BlockPos> anchors = polygon.anchors();
        if (anchors.isEmpty()) {
            return;
        }
        double yLow = polygon.minY();
        double yHigh = polygon.maxY() + 1;
        int count = anchors.size();
        for (int i = 0; i < count; i++) {
            BlockPos a = anchors.get(i);
            BlockPos b = anchors.get((i + 1) % count);
            double ax = a.getX() + 0.5;
            double az = a.getZ() + 0.5;
            double bx = b.getX() + 0.5;
            double bz = b.getZ() + 0.5;
            // Footprint ring at both heights (zero-length closing segment on a single anchor is skipped).
            line(pose, consumer, ax, yLow, az, bx, yLow, bz, color, camera);
            line(pose, consumer, ax, yHigh, az, bx, yHigh, bz, color, camera);
            // Vertical at this anchor.
            line(pose, consumer, ax, yLow, az, ax, yHigh, az, color, camera);
        }
    }

    // Emits one camera-relative line segment. The lines pipeline uses POSITION_COLOR_NORMAL_LINE_WIDTH,
    // so each vertex needs a color, a normal (the segment direction), and a line width.
    static void line(PoseStack.Pose pose, VertexConsumer consumer,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             int color, Vec3 camera) {
        float fx1 = (float) (x1 - camera.x);
        float fy1 = (float) (y1 - camera.y);
        float fz1 = (float) (z1 - camera.z);
        float fx2 = (float) (x2 - camera.x);
        float fy2 = (float) (y2 - camera.y);
        float fz2 = (float) (z2 - camera.z);

        float dx = fx2 - fx1;
        float dy = fy2 - fy1;
        float dz = fz2 - fz1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-5F) {
            return;
        }
        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;

        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;

        consumer.addVertex(pose, fx1, fy1, fz1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz)
                .setLineWidth(LINE_WIDTH);
        consumer.addVertex(pose, fx2, fy2, fz2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz)
                .setLineWidth(LINE_WIDTH);
    }

    static double distance(double x, double y, double z, Vec3 camera) {
        return Math.sqrt(sq(x - camera.x) + sq(y - camera.y) + sq(z - camera.z));
    }

    private static double sq(double value) {
        return value * value;
    }
}
