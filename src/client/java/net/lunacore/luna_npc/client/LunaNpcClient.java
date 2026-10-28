package net.lunacore.luna_npc.client;

import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.lunacore.luna_npc.client.render.NpcCustomSkins;
import net.lunacore.luna_npc.client.render.NpcRenderer;
import net.lunacore.luna_npc.client.render.ZoneRenderer;
import net.lunacore.luna_npc.client.screen.ConfirmScreen;
import net.lunacore.luna_npc.client.screen.CreateNpcScreen;
import net.lunacore.luna_npc.client.screen.CreateZoneScreen;
import net.lunacore.luna_npc.client.screen.EditAlliancesScreen;
import net.lunacore.luna_npc.client.screen.ManagePermissionsScreen;
import net.lunacore.luna_npc.client.screen.ManageZonesScreen;
import net.lunacore.luna_npc.client.screen.NpcListScreen;
import net.lunacore.luna_npc.client.screen.QuestBookScreen;
import net.lunacore.luna_npc.client.screen.QuestOfferScreen;
import net.lunacore.luna_npc.client.screen.TradeScreen;
import net.lunacore.luna_npc.entity.LunaNpcEntities;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.item.LunaNpcItems;
import net.lunacore.luna_npc.job.LunaNpcMenus;
import net.lunacore.luna_npc.network.ActiveQuestsPayload;
import net.lunacore.luna_npc.network.AlliancesPayload;
import net.lunacore.luna_npc.network.DialogClosePayload;
import net.lunacore.luna_npc.network.DialogOpenPayload;
import net.lunacore.luna_npc.network.NpcListPayload;
import net.lunacore.luna_npc.network.PermissionLogPayload;
import net.lunacore.luna_npc.network.PermissionsStatePayload;
import net.lunacore.luna_npc.network.PlayerPermissionsPayload;
import net.lunacore.luna_npc.network.QuestOffersPayload;
import net.lunacore.luna_npc.network.RemoveNpcPayload;
import net.lunacore.luna_npc.network.RequestNpcListPayload;
import net.lunacore.luna_npc.network.RequestPermissionsPayload;
import net.lunacore.luna_npc.network.StructuresPayload;
import net.lunacore.luna_npc.network.TradeStockPayload;
import net.lunacore.luna_npc.network.ZonesPayload;
import net.lunacore.luna_npc.api.LunaStick;
import net.lunacore.luna_npc.api.ZoneShape;

public class LunaNpcClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NpcCustomSkins.createFolders();
        EntityRenderers.register(LunaNpcEntities.NPC, NpcRenderer::new);
        // Non-arrow projectiles render big and glowing (the fireball look); arrows use the
        // authentic arrow model with tipped coloring.
        EntityRenderers.register(LunaNpcEntities.PROJECTILE, context ->
                new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(context, 3.0F, true));
        EntityRenderers.register(LunaNpcEntities.ARROW,
                net.minecraft.client.renderer.entity.TippableArrowRenderer::new);
        ZoneRenderer.register();
        net.lunacore.luna_npc.client.render.RouteRenderer.register();
        MenuScreens.register(LunaNpcMenus.TRADE, TradeScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(net.lunacore.luna_npc.network.SkinPayload.TYPE,
                (payload, context) -> ClientSkins.put(payload));
        ClientPlayNetworking.registerGlobalReceiver(net.lunacore.luna_npc.network.SkinRemovedPayload.TYPE,
                (payload, context) -> ClientSkins.remove(payload.id()));
        ClientPlayNetworking.registerGlobalReceiver(net.lunacore.luna_npc.network.SkinResultPayload.TYPE,
                (payload, context) -> ClientSkins.onResult(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> client.execute(ClientSkins::clear));
        ClientPlayNetworking.registerGlobalReceiver(NpcListPayload.TYPE, (payload, context) -> {
            ClientNpcCatalog.set(payload.npcs());
            context.client().setScreenAndShow(new NpcListScreen(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(AlliancesPayload.TYPE, (payload, context) ->
                ClientAlliances.set(payload.alliances()));
        ClientPlayNetworking.registerGlobalReceiver(ZonesPayload.TYPE, (payload, context) ->
                ClientZones.set(payload.zones()));
        ClientPlayNetworking.registerGlobalReceiver(net.lunacore.luna_npc.network.RoutesPayload.TYPE,
                (payload, context) -> ClientRoutes.set(payload.routes()));
        ClientPlayNetworking.registerGlobalReceiver(net.lunacore.luna_npc.network.BodyguardMenuPayload.TYPE,
                (payload, context) -> context.client().setScreenAndShow(
                        new net.lunacore.luna_npc.client.screen.BodyguardScreen(payload)));
        ClientPlayNetworking.registerGlobalReceiver(StructuresPayload.TYPE,
                (payload, context) -> ClientStructures.set(payload.structureIds()));
        ClientPlayNetworking.registerGlobalReceiver(TradeStockPayload.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof TradeScreen screen
                    && screen.npcEntityId() == payload.npcEntityId()) {
                screen.applyStock(payload.stock());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(QuestOffersPayload.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof QuestOfferScreen screen
                    && screen.npcEntityId() == payload.npcEntityId()) {
                screen.apply(payload);
            } else {
                context.client().setScreenAndShow(new QuestOfferScreen(payload));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ActiveQuestsPayload.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof QuestBookScreen screen) {
                screen.apply(payload);
            } else {
                context.client().setScreenAndShow(new QuestBookScreen(payload));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(DialogOpenPayload.TYPE, DialogClient::handleOpen);
        ClientPlayNetworking.registerGlobalReceiver(DialogClosePayload.TYPE, DialogClient::handleClose);
        ClientPlayNetworking.registerGlobalReceiver(PlayerPermissionsPayload.TYPE, (payload, context) ->
                ClientStickPermissions.set(payload.admin(), payload.allowed()));
        ClientPlayNetworking.registerGlobalReceiver(PermissionsStatePayload.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof ManagePermissionsScreen screen) {
                screen.applyState(payload);
            } else {
                context.client().setScreenAndShow(new ManagePermissionsScreen(payload));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(PermissionLogPayload.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof ManagePermissionsScreen screen) {
                screen.applyLog(payload);
            }
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!level.isClientSide()) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getItemInHand(hand);
            if (held.is(LunaNpcItems.CREATE_NPC)) {
                if (allowed(player, LunaStick.CREATE_NPC)) {
                    Minecraft.getInstance().setScreenAndShow(new CreateNpcScreen());
                }
                return InteractionResult.SUCCESS;
            }
            if (held.is(LunaNpcItems.EDIT_NPC)) {
                if (allowed(player, LunaStick.EDIT_NPC)) {
                    ClientPlayNetworking.send(new RequestNpcListPayload(false, hitResult.getBlockPos()));
                }
                return InteractionResult.SUCCESS;
            }
            if (held.is(LunaNpcItems.SPAWN_NPC)) {
                if (allowed(player, LunaStick.SPAWN_NPC)) {
                    ClientPlayNetworking.send(new RequestNpcListPayload(true, hitResult.getBlockPos()));
                }
                return InteractionResult.SUCCESS;
            }
            if (held.is(LunaNpcItems.EDIT_ALLIANCES)) {
                if (allowed(player, LunaStick.EDIT_ALLIANCES)) {
                    Minecraft.getInstance().setScreenAndShow(new EditAlliancesScreen());
                }
                return InteractionResult.SUCCESS;
            }
            if (held.is(LunaNpcItems.ZONE_WAND)) {
                return allowed(player, LunaStick.ZONE_WAND)
                        ? handleZoneWand(player, level, hitResult.getBlockPos())
                        : InteractionResult.SUCCESS;
            }
            if (held.is(LunaNpcItems.ROUTE_CREATOR)) {
                return allowed(player, LunaStick.ROUTE_CREATOR)
                        ? RouteCreatorStick.handleClick(player, level, hitResult.getBlockPos())
                        : InteractionResult.SUCCESS;
            }
            if (held.is(LunaNpcItems.MANAGE_PERMISSIONS)) {
                if (ClientStickPermissions.admin()) {
                    ClientPlayNetworking.send(new RequestPermissionsPayload());
                } else {
                    player.sendOverlayMessage(Component.translatable("message.luna_npc.manage_denied"));
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!level.isClientSide() || !player.getItemInHand(hand).is(LunaNpcItems.REMOVE_NPC)
                    || !(entity instanceof NpcEntity npc)) {
                return InteractionResult.PASS;
            }
            if (allowed(player, LunaStick.REMOVE_NPC)) {
                openRemoveConfirm(npc);
            }
            return InteractionResult.SUCCESS;
        });
    }

    // The client-side gate: a non-permitted player gets an action-bar denial instead of the screen. Purely
    // UX — the server re-checks every mutation, so a tampered client gains nothing.
    private static boolean allowed(Player player, LunaStick stick) {
        if (ClientStickPermissions.canUse(stick)) {
            return true;
        }
        player.sendOverlayMessage(Component.translatable("message.luna_npc.stick_denied"));
        return false;
    }

    // The Remove NPC stick: clicking an NPC opens a confirmation showing its name and its live runtime
    // id (the per-instance handle; the luna id is the shared template). Confirming asks the server to
    // permanently wipe that one instance, respawn record and all.
    private static void openRemoveConfirm(NpcEntity npc) {
        List<Component> lines = List.of(
                Component.translatable("gui.luna_npc.remove_npc.line1", npc.getName()),
                Component.translatable("gui.luna_npc.remove_npc.line2", npc.getId()),
                Component.translatable("gui.luna_npc.remove_npc.warning").withStyle(ChatFormatting.RED));
        Minecraft.getInstance().setScreenAndShow(new ConfirmScreen(
                Component.translatable("gui.luna_npc.remove_npc.title"), lines,
                Component.translatable("gui.luna_npc.remove_npc.confirm").withStyle(ChatFormatting.RED),
                () -> ClientPlayNetworking.send(new RemoveNpcPayload(npc.getId()))));
    }

    // The Zone Wand builds a shape by right-clicking points in the world; the border renders live
    // while placing (ClientZoneRender). Fixed-count shapes (box/cylinder/sphere) complete on their
    // second point; a polygon collects points until you sneak-right-click to close it (min 3). With no
    // shape in progress, a click opens Manage Zones, where the "New Zone" buttons start a shape.
    private static InteractionResult handleZoneWand(Player player, Level level, BlockPos clicked) {
        if (!ClientZoneRender.isPlacing()) {
            Minecraft.getInstance().setScreenAndShow(new ManageZonesScreen());
            return InteractionResult.SUCCESS;
        }
        ZoneShape.Type type = ClientZoneRender.type();
        if (player.isShiftKeyDown()) {
            if (type == ZoneShape.Type.POLYGON && ClientZoneRender.pointCount() >= 3) {
                finishZone(level, type);
            } else if (type == ZoneShape.Type.POLYGON) {
                player.sendOverlayMessage(Component.translatable("message.luna_npc.zone_need_points"));
            }
            return InteractionResult.SUCCESS;
        }
        ClientZoneRender.addPoint(clicked);
        if (type != ZoneShape.Type.POLYGON && ClientZoneRender.pointCount() >= 2) {
            finishZone(level, type);
        } else {
            player.sendOverlayMessage(Component.translatable("message.luna_npc.zone_point",
                    ClientZoneRender.pointCount()));
        }
        return InteractionResult.SUCCESS;
    }

    private static void finishZone(Level level, ZoneShape.Type type) {
        ZoneShape shape = buildShape(type, ClientZoneRender.points());
        String dimension = level.dimension().identifier().toString();
        ClientZoneRender.finishPlacing();
        Minecraft.getInstance().setScreenAndShow(new CreateZoneScreen(shape, dimension));
    }

    // Turns the placed points into a shape. Box/cylinder/sphere use two points (corner+corner, or
    // center+rim/surface); the polygon uses them all as its footprint anchors, carrying their Y.
    private static ZoneShape buildShape(ZoneShape.Type type, List<BlockPos> points) {
        return switch (type) {
            case BOX -> new ZoneShape.Box(points.get(0), points.get(1));
            case CYLINDER -> {
                BlockPos center = points.get(0);
                BlockPos rim = points.get(1);
                double dx = rim.getX() - center.getX();
                double dz = rim.getZ() - center.getZ();
                int radius = Math.max(1, (int) Math.round(Math.sqrt(dx * dx + dz * dz)));
                yield new ZoneShape.Cylinder(center, radius,
                        Math.min(center.getY(), rim.getY()), Math.max(center.getY(), rim.getY()));
            }
            case SPHERE -> {
                BlockPos center = points.get(0);
                int radius = Math.max(1, (int) Math.round(Math.sqrt(center.distSqr(points.get(1)))));
                yield new ZoneShape.Sphere(center, radius);
            }
            case POLYGON -> new ZoneShape.Polygon(List.copyOf(points));
        };
    }
}
