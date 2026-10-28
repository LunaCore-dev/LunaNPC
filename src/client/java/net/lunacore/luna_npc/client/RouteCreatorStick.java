package net.lunacore.luna_npc.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.lunacore.luna_npc.api.NpcRoute;
import net.lunacore.luna_npc.api.NpcRoutePoint;
import net.lunacore.luna_npc.client.screen.CreateRouteScreen;
import net.lunacore.luna_npc.client.screen.EditRouteScreen;
import net.lunacore.luna_npc.client.screen.ManageRoutesScreen;
import net.lunacore.luna_npc.network.UpdateRoutesPayload;
import net.lunacore.luna_npc.npc.NpcValidation;

// The Route Creator stick. Idle, a right-click opens Manage Routes. While placing, each right-click on the
// ground adds a point on top of the clicked block — rejected with the reason when it breaks a route rule —
// and a sneak-right-click finishes: a new route goes on to its naming screen, an extended one saves.
public final class RouteCreatorStick {

    private RouteCreatorStick() {
    }

    public static InteractionResult handleClick(Player player, Level level, BlockPos clicked) {
        if (!ClientRouteRender.isPlacing()) {
            Minecraft.getInstance().setScreenAndShow(new ManageRoutesScreen());
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            finish(player, level);
            return InteractionResult.SUCCESS;
        }
        BlockPos point = clicked.above();
        NpcRoute.PlacementProblem problem = NpcRoute.placementProblem(ClientRouteRender.points(), point,
                NpcValidation.MAX_ROUTE_POINTS_GUI);
        if (problem != NpcRoute.PlacementProblem.NONE) {
            player.sendOverlayMessage(EditRouteScreen.problemText(problem));
            return InteractionResult.SUCCESS;
        }
        ClientRouteRender.addPoint(point);
        player.sendOverlayMessage(Component.translatable("message.luna_npc.route_point",
                ClientRouteRender.pointCount(), NpcValidation.MAX_ROUTE_POINTS_GUI));
        return InteractionResult.SUCCESS;
    }

    private static void finish(Player player, Level level) {
        String extending = ClientRouteRender.extendingRouteId();
        if (extending != null) {
            saveExtension(player, extending);
            return;
        }
        List<BlockPos> points = ClientRouteRender.points();
        ClientRouteRender.finishPlacing();
        if (points.size() < NpcValidation.MIN_ROUTE_POINTS) {
            player.sendOverlayMessage(Component.translatable("message.luna_npc.route_cancelled"));
            return;
        }
        Minecraft.getInstance().setScreenAndShow(new CreateRouteScreen(points,
                level.dimension().identifier().toString()));
    }

    private static void saveExtension(Player player, String routeId) {
        List<BlockPos> added = ClientRouteRender.newPoints();
        ClientRouteRender.finishPlacing();
        NpcRoute route = ClientRoutes.byId(routeId);
        if (route == null || added.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.luna_npc.route_cancelled"));
            return;
        }
        List<NpcRoutePoint> points = new ArrayList<>(route.points());
        added.forEach(pos -> points.add(NpcRoutePoint.at(pos)));
        List<NpcRoute> updated = ClientRoutes.editableCopy();
        updated.replaceAll(existing -> existing.id().equals(routeId) ? route.withPoints(points) : existing);
        ClientPlayNetworking.send(new UpdateRoutesPayload(updated));
        player.sendOverlayMessage(Component.translatable("message.luna_npc.route_extended", added.size(), route.name()));
    }
}
