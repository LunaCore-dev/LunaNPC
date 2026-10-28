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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.api.NpcRoute;
import net.lunacore.luna_npc.client.ClientRouteRender;
import net.lunacore.luna_npc.client.ClientRoutes;
import net.lunacore.luna_npc.item.LunaNpcItems;
import net.lunacore.luna_npc.npc.NpcValidation;

// Draws patrol routes in the world: the route being placed with the Route Creator (cyan legs, plus a
// preview leg to the block under the crosshair — green when that point would be accepted, red when it
// breaks a route rule) and every saved route toggled on for inspection (purple). Each point is a small
// marker cube with a post so it reads from afar; the anchor (first point) is a larger gold cube.
public final class RouteRenderer {

    private static final int PLACING_COLOR = 0xFF22E0FF;
    private static final int SAVED_COLOR = 0xFFB46CFF;
    private static final int POINT_COLOR = 0xFFFFE23D;
    private static final int ANCHOR_COLOR = 0xFFFFA020;
    private static final int VALID_PREVIEW_COLOR = 0xFF3DFF6E;
    private static final int INVALID_PREVIEW_COLOR = 0xFFE33B3B;
    private static final double RENDER_RANGE = 96.0;
    private static final double POINT_HALF = 0.18;
    private static final double ANCHOR_HALF = 0.3;
    private static final double POST_HEIGHT = 1.6;
    // Legs float a little above the ground they run along so they never hide inside it.
    private static final double LINE_LIFT = 0.15;

    private RouteRenderer() {
    }

    public static void register() {
        // Same submit phase as the zone borders (see ZoneRenderer.register).
        LevelRenderEvents.COLLECT_SUBMITS.register(RouteRenderer::onCollectSubmits);
    }

    private static void onCollectSubmits(LevelRenderContext context) {
        boolean placing = ClientRouteRender.isPlacing();
        Set<String> inspected = ClientRouteRender.inspected();
        Minecraft mc = Minecraft.getInstance();
        if ((!placing && inspected.isEmpty()) || mc.level == null || mc.player == null) {
            return;
        }
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        String dimension = mc.level.dimension().identifier().toString();
        BlockPos preview = placing && mc.player.getMainHandItem().is(LunaNpcItems.ROUTE_CREATOR)
                && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
                ? ((BlockHitResult) mc.hitResult).getBlockPos().above() : null;

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(),
                (pose, consumer) -> {
                    for (NpcRoute route : ClientRoutes.all()) {
                        if (inspected.contains(route.id()) && route.dimension().equals(dimension)
                                && !route.id().equals(ClientRouteRender.extendingRouteId())) {
                            drawRoute(pose, consumer, NpcRoute.positionsOf(route.points()), SAVED_COLOR, camera);
                        }
                    }
                    if (placing) {
                        List<BlockPos> points = ClientRouteRender.points();
                        drawRoute(pose, consumer, points, PLACING_COLOR, camera);
                        if (preview != null && !points.isEmpty()) {
                            boolean valid = NpcRoute.placementProblem(points, preview,
                                    NpcValidation.MAX_ROUTE_POINTS_GUI) == NpcRoute.PlacementProblem.NONE;
                            drawLeg(pose, consumer, points.getLast(), preview,
                                    valid ? VALID_PREVIEW_COLOR : INVALID_PREVIEW_COLOR, camera);
                        }
                    }
                });
    }

    private static void drawRoute(PoseStack.Pose pose, VertexConsumer consumer, List<BlockPos> points,
                                  int legColor, Vec3 camera) {
        for (int index = 0; index < points.size(); index++) {
            drawPoint(pose, consumer, points.get(index), index == 0, camera);
            if (index > 0) {
                drawLeg(pose, consumer, points.get(index - 1), points.get(index), legColor, camera);
            }
        }
    }

    private static void drawLeg(PoseStack.Pose pose, VertexConsumer consumer, BlockPos from, BlockPos to,
                                int color, Vec3 camera) {
        double fromX = from.getX() + 0.5;
        double fromY = from.getY() + LINE_LIFT;
        double fromZ = from.getZ() + 0.5;
        double toX = to.getX() + 0.5;
        double toY = to.getY() + LINE_LIFT;
        double toZ = to.getZ() + 0.5;
        if (ZoneRenderer.distance(fromX, fromY, fromZ, camera) > RENDER_RANGE
                && ZoneRenderer.distance(toX, toY, toZ, camera) > RENDER_RANGE) {
            return;
        }
        ZoneRenderer.line(pose, consumer, fromX, fromY, fromZ, toX, toY, toZ, color, camera);
    }

    private static void drawPoint(PoseStack.Pose pose, VertexConsumer consumer, BlockPos point, boolean anchor,
                                  Vec3 camera) {
        double x = point.getX() + 0.5;
        double y = point.getY();
        double z = point.getZ() + 0.5;
        if (ZoneRenderer.distance(x, y, z, camera) > RENDER_RANGE) {
            return;
        }
        double half = anchor ? ANCHOR_HALF : POINT_HALF;
        int color = anchor ? ANCHOR_COLOR : POINT_COLOR;
        ZoneRenderer.line(pose, consumer, x, y, z, x, y + POST_HEIGHT, z, color, camera);
        ZoneRenderer.drawBox(pose, consumer, new AABB(x - half, y + POST_HEIGHT - half, z - half,
                x + half, y + POST_HEIGHT + half, z + half), color, camera);
    }
}
