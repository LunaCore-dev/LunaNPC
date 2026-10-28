package net.lunacore.luna_npc.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.lunacore.luna_npc.LunaNpcChat;
import net.lunacore.luna_npc.alliance.NpcAllianceManager;
import net.lunacore.luna_npc.zone.ZoneManager;
import net.lunacore.luna_npc.api.LunaNpcApi;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcRegistry;
import net.lunacore.luna_npc.api.LunaStick;
import net.lunacore.luna_npc.api.NpcAlliance;
import net.lunacore.luna_npc.api.PermissionLogAction;
import net.lunacore.luna_npc.api.PermissionLogEntry;
import net.lunacore.luna_npc.permission.PermissionLogManager;
import net.lunacore.luna_npc.permission.PermissionService;
import net.lunacore.luna_npc.permission.PlayerSkinStore;
import net.lunacore.luna_npc.permission.StickPermissionManager;
import net.lunacore.luna_npc.entity.LunaNpcEntities;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.job.DialogEvents;
import net.lunacore.luna_npc.job.QuestEvents;
import net.lunacore.luna_npc.api.NpcBossBarSettings;
import net.lunacore.luna_npc.api.NpcCombatSettings;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.api.NpcDropSettings;
import net.lunacore.luna_npc.npc.NpcEquipment;
import net.lunacore.luna_npc.api.NpcEquipmentSettings;
import net.lunacore.luna_npc.api.NpcModelSettings;
import net.lunacore.luna_npc.api.NpcMovementSettings;
import net.lunacore.luna_npc.npc.NpcSoundSettings;
import net.lunacore.luna_npc.api.NpcTraitsSettings;
import net.lunacore.luna_npc.npc.NpcValidation;
import net.lunacore.luna_npc.npc.NpcWarningNotifier;

public final class LunaNpcNetworking {

    private static int sentAllianceChanges = -1;

    private static final double MAX_SPAWN_DISTANCE_SQUARED = 32 * 32;
    private static final int PERMISSION_LOG_PAGE = 100;

    private LunaNpcNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(CreateNpcPayload.TYPE, CreateNpcPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestNpcListPayload.TYPE, RequestNpcListPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SpawnNpcPayload.TYPE, SpawnNpcPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateNpcPayload.TYPE, UpdateNpcPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ToggleFavoritePayload.TYPE, ToggleFavoritePayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateAlliancesPayload.TYPE, UpdateAlliancesPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateZonesPayload.TYPE, UpdateZonesPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateRoutesPayload.TYPE, UpdateRoutesPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BodyguardActionPayload.TYPE, BodyguardActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RemoveNpcPayload.TYPE, RemoveNpcPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NpcListPayload.TYPE, NpcListPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AlliancesPayload.TYPE, AlliancesPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ZonesPayload.TYPE, ZonesPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RoutesPayload.TYPE, RoutesPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BodyguardMenuPayload.TYPE, BodyguardMenuPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StructuresPayload.TYPE, StructuresPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TradeStockPayload.TYPE, TradeStockPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QuestOffersPayload.TYPE, QuestOffersPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestAcceptPayload.TYPE, QuestAcceptPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestTurnInPayload.TYPE, QuestTurnInPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestBookRequestPayload.TYPE, QuestBookRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestAbandonPayload.TYPE, QuestAbandonPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ActiveQuestsPayload.TYPE, ActiveQuestsPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DialogOpenPayload.TYPE, DialogOpenPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DialogClosePayload.TYPE, DialogClosePayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DialogChoicePayload.TYPE, DialogChoicePayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestPermissionsPayload.TYPE, RequestPermissionsPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdatePermissionsPayload.TYPE, UpdatePermissionsPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestPermissionLogPayload.TYPE, RequestPermissionLogPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlayerPermissionsPayload.TYPE, PlayerPermissionsPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PermissionsStatePayload.TYPE, PermissionsStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PermissionLogPayload.TYPE, PermissionLogPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DownloadSkinPayload.TYPE, DownloadSkinPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EditSkinPayload.TYPE, EditSkinPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SkinPayload.TYPE, SkinPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SkinRemovedPayload.TYPE, SkinRemovedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SkinResultPayload.TYPE, SkinResultPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CreateNpcPayload.TYPE, LunaNpcNetworking::handleCreateNpc);
        ServerPlayNetworking.registerGlobalReceiver(QuestAcceptPayload.TYPE, QuestEvents::handleAccept);
        ServerPlayNetworking.registerGlobalReceiver(QuestTurnInPayload.TYPE, QuestEvents::handleTurnIn);
        ServerPlayNetworking.registerGlobalReceiver(QuestBookRequestPayload.TYPE, QuestEvents::handleBookRequest);
        ServerPlayNetworking.registerGlobalReceiver(QuestAbandonPayload.TYPE, QuestEvents::handleAbandon);
        ServerPlayNetworking.registerGlobalReceiver(DialogChoicePayload.TYPE, DialogEvents::handleChoice);
        ServerPlayNetworking.registerGlobalReceiver(RequestNpcListPayload.TYPE, LunaNpcNetworking::handleRequestNpcList);
        ServerPlayNetworking.registerGlobalReceiver(SpawnNpcPayload.TYPE, LunaNpcNetworking::handleSpawnNpc);
        ServerPlayNetworking.registerGlobalReceiver(UpdateNpcPayload.TYPE, LunaNpcNetworking::handleUpdateNpc);
        ServerPlayNetworking.registerGlobalReceiver(ToggleFavoritePayload.TYPE, LunaNpcNetworking::handleToggleFavorite);
        ServerPlayNetworking.registerGlobalReceiver(UpdateAlliancesPayload.TYPE, LunaNpcNetworking::handleUpdateAlliances);
        ServerPlayNetworking.registerGlobalReceiver(UpdateZonesPayload.TYPE, LunaNpcNetworking::handleUpdateZones);
        ServerPlayNetworking.registerGlobalReceiver(UpdateRoutesPayload.TYPE, LunaNpcNetworking::handleUpdateRoutes);
        ServerPlayNetworking.registerGlobalReceiver(RemoveNpcPayload.TYPE, LunaNpcNetworking::handleRemoveNpc);
        ServerPlayNetworking.registerGlobalReceiver(RequestPermissionsPayload.TYPE, LunaNpcNetworking::handleRequestPermissions);
        ServerPlayNetworking.registerGlobalReceiver(UpdatePermissionsPayload.TYPE, LunaNpcNetworking::handleUpdatePermissions);
        ServerPlayNetworking.registerGlobalReceiver(RequestPermissionLogPayload.TYPE, LunaNpcNetworking::handleRequestPermissionLog);
        ServerPlayNetworking.registerGlobalReceiver(DownloadSkinPayload.TYPE, LunaNpcNetworking::handleDownloadSkin);
        ServerPlayNetworking.registerGlobalReceiver(EditSkinPayload.TYPE, LunaNpcNetworking::handleEditSkin);

        // Alliance changes reach every client at the end of the tick, whether they came from the editor
        // or the API.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> sentAllianceChanges = -1);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            NpcAllianceManager alliances = NpcAllianceManager.get(server);
            if (alliances.changeCount() != sentAllianceChanges) {
                sentAllianceChanges = alliances.changeCount();
                AlliancesPayload snapshot = new AlliancesPayload(alliances.all());
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, snapshot);
                }
            }
        });

        // Every joining player gets the current alliances, zones, routes and structure ids so their editor
        // screens render live (the structure registry is server-only, so the client needs the list), plus
        // their own stick access so the client can skip opening a screen they can't use.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(new AlliancesPayload(NpcAllianceManager.get(server).all()));
            sender.sendPacket(new ZonesPayload(ZoneManager.get(server).all()));
            sender.sendPacket(new RoutesPayload(net.lunacore.luna_npc.route.RouteManager.get(server).all()));
            net.lunacore.luna_npc.skin.SkinManager.get(server).sendAll(handler.player);
            sender.sendPacket(new StructuresPayload(structureIds(server)));
            sendPlayerPermissions(server, handler.player);
            PlayerSkinStore.get(server).remember(handler.player.getGameProfile());
        });
    }

    private static java.util.List<String> structureIds(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .listElements().map(ref -> ref.key().identifier().toString()).sorted().toList();
    }

    private static void handleUpdateAlliances(UpdateAlliancesPayload payload, ServerPlayNetworking.Context context) {
        MinecraftServer server = context.server();
        ServerPlayer actor = context.player();
        if (!PermissionService.canUse(server, actor, LunaStick.EDIT_ALLIANCES)) {
            PermissionService.denied(actor);
            return;
        }
        NpcAllianceManager.get(server).applyAll(payload.alliances());
        PermissionService.log(server, actor, PermissionLogAction.ALLIANCES_EDITED, "", 0);
    }

    private static void handleUpdateZones(UpdateZonesPayload payload, ServerPlayNetworking.Context context) {
        MinecraftServer server = context.server();
        ServerPlayer actor = context.player();
        if (!PermissionService.canUse(server, actor, LunaStick.ZONE_WAND)) {
            PermissionService.denied(actor);
            return;
        }
        ZoneManager.get(server).applyAll(payload.zones());
        ZonesPayload snapshot = new ZonesPayload(ZoneManager.get(server).all());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, snapshot);
        }
        PermissionService.log(server, actor, PermissionLogAction.ZONES_EDITED, "", 0);
    }

    private static void handleUpdateRoutes(UpdateRoutesPayload payload, ServerPlayNetworking.Context context) {
        MinecraftServer server = context.server();
        ServerPlayer actor = context.player();
        if (!PermissionService.canUse(server, actor, LunaStick.ROUTE_CREATOR)) {
            PermissionService.denied(actor);
            return;
        }
        net.lunacore.luna_npc.route.RouteManager routes = net.lunacore.luna_npc.route.RouteManager.get(server);
        routes.applyAll(payload.routes());
        RoutesPayload snapshot = new RoutesPayload(routes.all());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, snapshot);
        }
        PermissionService.log(server, actor, PermissionLogAction.ROUTES_EDITED, "", 0);
    }

    // The model picker's skin controls belong to the NPC editor, so either editing stick allows them.
    private static boolean mayManageSkins(ServerPlayNetworking.Context context) {
        return PermissionService.canUse(context.server(), context.player(), LunaStick.CREATE_NPC)
                || PermissionService.canUse(context.server(), context.player(), LunaStick.EDIT_NPC);
    }

    private static void handleDownloadSkin(DownloadSkinPayload payload, ServerPlayNetworking.Context context) {
        if (!mayManageSkins(context)) {
            PermissionService.denied(context.player());
            return;
        }
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        net.lunacore.luna_npc.api.NpcSkinArms arms = switch (payload.arms()) {
            case 1 -> net.lunacore.luna_npc.api.NpcSkinArms.CLASSIC;
            case 2 -> net.lunacore.luna_npc.api.NpcSkinArms.SLIM;
            default -> null;
        };
        LunaNpcApi.skins(server).download(payload.input(), null, arms).thenAccept(result -> {
            if (result.succeeded()) {
                PermissionService.log(server, player, PermissionLogAction.SKIN_DOWNLOADED, result.skin().name(), 0);
            }
            ServerPlayNetworking.send(player, new SkinResultPayload(result.succeeded() ? result.skin().id() : "",
                    result.succeeded() ? "" : result.failure().translationKey()));
        });
    }

    private static void handleEditSkin(EditSkinPayload payload, ServerPlayNetworking.Context context) {
        if (!mayManageSkins(context)) {
            PermissionService.denied(context.player());
            return;
        }
        net.lunacore.luna_npc.api.SkinRegistry skins = LunaNpcApi.skins(context.server());
        String name = skins.byId(payload.id()).map(net.lunacore.luna_npc.api.NpcSkin::name).orElse("");
        if (payload.delete()) {
            if (skins.delete(payload.id())) {
                PermissionService.log(context.server(), context.player(), PermissionLogAction.SKIN_DELETED, name, 0);
            }
            return;
        }
        java.util.Optional<net.lunacore.luna_npc.api.NpcSkinFailure> problem = skins.rename(payload.id(), payload.name())
                .or(() -> skins.setArms(payload.id(), payload.arms()));
        if (problem.isEmpty()) {
            PermissionService.log(context.server(), context.player(), PermissionLogAction.SKIN_EDITED, payload.name(), 0);
        }
        ServerPlayNetworking.send(context.player(), new SkinResultPayload(problem.isEmpty() ? payload.id() : "",
                problem.map(net.lunacore.luna_npc.api.NpcSkinFailure::translationKey).orElse("")));
    }

    private static void handleCreateNpc(CreateNpcPayload payload, ServerPlayNetworking.Context context) {
        if (!PermissionService.canUse(context.server(), context.player(), LunaStick.CREATE_NPC)) {
            PermissionService.denied(context.player());
            return;
        }
        NpcRegistry registry = LunaNpcApi.npcs(context.server());
        if (isInvalid(payload.name(), payload.health(), payload.sounds(), payload.model(),
                payload.tagColor(), payload.category(), payload.equipment(), payload.dropSettings(),
                payload.movement(), payload.bossBar(), payload.traits(), payload.combat(), payload.spawn(),
                payload.jobs())) {
            LunaNpcChat.send(context.player(), Component.translatable("message.luna_npc.npc_rejected"));
            return;
        }
        if (isCategoryLimitReached(registry, payload.category())) {
            LunaNpcChat.send(context.player(), Component.translatable("message.luna_npc.category_limit"));
            return;
        }
        if (!payload.editAccess().guiCanEdit()) {
            LunaNpcChat.send(context.player(), Component.translatable("message.luna_npc.npc_rejected"));
            return;
        }
        NpcDefinition npc = registry.create(payload.name(), payload.editAccess());
        net.lunacore.luna_npc.npc.NpcManager.editInGame(npc, () -> applySettings(npc, payload));
        NpcWarningNotifier.notifyNew(context.server(), npc, java.util.Set.of());
        LunaNpcChat.send(context.player(),
                Component.translatable("message.luna_npc.npc_created", LunaNpcChat.highlight(npc.name()), LunaNpcChat.highlight(npc.lunaId())));
        PermissionService.log(context.server(), context.player(), PermissionLogAction.NPC_CREATED, npc.name(), npc.lunaId());
    }

    private static boolean isCategoryLimitReached(NpcRegistry registry, String category) {
        return !category.isEmpty() && !registry.categories().contains(category)
                && registry.categories().size() >= NpcValidation.MAX_CATEGORIES;
    }

    private static boolean isInvalid(String name, int health, NpcSoundSettings sounds, NpcModelSettings model,
                                     String tagColor, String category,
                                     NpcEquipmentSettings equipment, NpcDropSettings dropSettings,
                                     NpcMovementSettings movement, NpcBossBarSettings bossBar,
                                     NpcTraitsSettings traits, NpcCombatSettings combat,
                                     net.lunacore.luna_npc.api.NpcSpawnSettings spawn,
                                     net.lunacore.luna_npc.api.NpcJobSettings jobs) {
        return !NpcValidation.isValidName(name)
                || !NpcValidation.isValidHealth(health)
                || !NpcValidation.isValidAmbientInterval(sounds.ambientIntervalSeconds())
                || !model.isValid()
                || !NpcValidation.isValidTagColor(tagColor)
                || !NpcValidation.isValidCategory(category)
                || !NpcEquipment.isValid(equipment)
                || !dropSettings.isValid()
                || !NpcEquipment.areDropItemsKnown(dropSettings)
                || !movement.isValid()
                || !NpcEquipment.areBlocksKnown(movement.avoidedBlocks())
                || !bossBar.isValid()
                || !traits.isValid()
                || !combat.isValid()
                || !spawn.isValid()
                || !jobs.isValid();
    }

    private static void applySettings(NpcDefinition npc, CreateNpcPayload payload) {
        applySettings(npc, payload.nameTagShown(), payload.health(), payload.sounds(), payload.model(),
                payload.tagColor(), payload.category(), payload.equipment(), payload.dropSettings(),
                payload.movement(), payload.bossBar(), payload.traits(), payload.combat(), payload.allianceIds(),
                payload.spawn(), payload.jobs());
    }

    private static void applySettings(NpcDefinition npc, boolean nameTagShown, int health,
                                      NpcSoundSettings sounds, NpcModelSettings model,
                                      String tagColor, String category,
                                      NpcEquipmentSettings equipment, NpcDropSettings dropSettings,
                                      NpcMovementSettings movement, NpcBossBarSettings bossBar,
                                      NpcTraitsSettings traits, NpcCombatSettings combat, List<String> allianceIds,
                                      net.lunacore.luna_npc.api.NpcSpawnSettings spawn,
                                      net.lunacore.luna_npc.api.NpcJobSettings jobs) {
        npc.setNameTagShown(nameTagShown);
        npc.setTagColor(tagColor);
        npc.setCategory(category);
        npc.setHealth(health);
        npc.setHurtSoundId(orDefault(sounds.hurtSoundId(), NpcDefaults.HURT_SOUND_ID));
        npc.setDieSoundId(orDefault(sounds.dieSoundId(), NpcDefaults.DIE_SOUND_ID));
        npc.setAmbientSoundId(sounds.ambientSoundId());
        npc.setAggroSoundId(sounds.aggroSoundId());
        npc.setCombatCloseSoundId(sounds.combatCloseSoundId());
        npc.setCombatFarSoundId(sounds.combatFarSoundId());
        npc.setCloseImpactSoundId(sounds.closeImpactSoundId());
        npc.setFarImpactSoundId(sounds.farImpactSoundId());
        npc.setAmbientIntervalSeconds(sounds.ambientIntervalSeconds());
        npc.setModelSettings(model);
        npc.setEquipment(equipment);
        npc.setDropSettings(dropSettings);
        npc.setMovement(movement);
        npc.setBossBar(bossBar);
        npc.setTraits(traits);
        npc.setCombat(combat);
        npc.setAllianceIds(allianceIds);
        npc.setSpawn(spawn);
        npc.setJobs(jobs);
    }

    private static void handleRequestNpcList(RequestNpcListPayload payload, ServerPlayNetworking.Context context) {
        LunaStick stick = payload.forSpawn() ? LunaStick.SPAWN_NPC : LunaStick.EDIT_NPC;
        if (!PermissionService.canUse(context.server(), context.player(), stick)) {
            PermissionService.denied(context.player());
            return;
        }
        NpcRegistry registry = LunaNpcApi.npcs(context.server());
        UUID playerId = context.player().getUUID();
        List<NpcSummary> npcs = registry.all().stream()
                .map(npc -> NpcSummary.of(npc, registry.isFavorite(playerId, npc.lunaId())))
                .toList();
        ServerPlayNetworking.send(context.player(),
                new NpcListPayload(payload.forSpawn(), payload.clickedPos(), npcs));
    }

    private static void handleSpawnNpc(SpawnNpcPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!PermissionService.canUse(context.server(), player, LunaStick.SPAWN_NPC)) {
            PermissionService.denied(player);
            return;
        }
        NpcDefinition npc = LunaNpcApi.npcs(context.server()).byLunaId(payload.lunaId()).orElse(null);
        if (npc == null) {
            LunaNpcChat.send(player, Component.translatable("message.luna_npc.npc_missing"));
            return;
        }
        BlockPos pos = payload.pos();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_SPAWN_DISTANCE_SQUARED) {
            return;
        }
        ServerLevel level = player.level();
        // A manually placed NPC remembers where it started (pos.above()), so respawn-at-origin works.
        NpcEntity entity = NpcEntity.spawn(level, npc, EntitySpawnReason.MOB_SUMMONED,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, player.getYRot() + 180.0F, pos.above());
        if (entity == null) {
            return;
        }
        LunaNpcChat.send(player, Component.translatable("message.luna_npc.npc_spawned", LunaNpcChat.highlight(npc.name()), LunaNpcChat.highlight(npc.lunaId())));
        PermissionService.log(context.server(), player, PermissionLogAction.NPC_SPAWNED, npc.name(), npc.lunaId());
    }

    private static void handleUpdateNpc(UpdateNpcPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!PermissionService.canUse(context.server(), player, LunaStick.EDIT_NPC)) {
            PermissionService.denied(player);
            return;
        }
        NpcRegistry registry = LunaNpcApi.npcs(context.server());
        if (isInvalid(payload.name(), payload.health(), payload.sounds(), payload.model(),
                payload.tagColor(), payload.category(), payload.equipment(), payload.dropSettings(),
                payload.movement(), payload.bossBar(), payload.traits(), payload.combat(), payload.spawn(),
                payload.jobs())) {
            LunaNpcChat.send(player, Component.translatable("message.luna_npc.npc_rejected"));
            return;
        }
        if (isCategoryLimitReached(registry, payload.category())) {
            LunaNpcChat.send(player, Component.translatable("message.luna_npc.category_limit"));
            return;
        }
        NpcDefinition npc = registry.byLunaId(payload.lunaId()).orElse(null);
        if (npc == null) {
            LunaNpcChat.send(player, Component.translatable("message.luna_npc.npc_missing"));
            return;
        }
        if (!payload.editAccess().guiCanEdit()) {
            LunaNpcChat.send(player, Component.translatable("message.luna_npc.npc_rejected"));
            return;
        }
        java.util.Set<net.lunacore.luna_npc.api.NpcWarning.Id> warningsBefore = NpcWarningNotifier.idsOf(npc);
        boolean edited = net.lunacore.luna_npc.npc.NpcManager.editInGame(npc, () -> {
            npc.setName(payload.name());
            applySettings(npc, payload.nameTagShown(), payload.health(), payload.sounds(), payload.model(),
                    payload.tagColor(), payload.category(), payload.equipment(), payload.dropSettings(),
                    payload.movement(), payload.bossBar(), payload.traits(), payload.combat(),
                    payload.allianceIds(), payload.spawn(), payload.jobs());
            npc.setEditAccess(payload.editAccess());
        });
        if (!edited) {
            LunaNpcChat.send(player, Component.translatable("message.luna_npc.npc_locked",
                    LunaNpcChat.highlight(npc.name()), LunaNpcChat.highlight(npc.lunaId())));
            return;
        }
        for (ServerLevel level : context.server().getAllLevels()) {
            for (NpcEntity entity : level.getEntities(LunaNpcEntities.NPC, e -> e.lunaId() == npc.lunaId())) {
                entity.applyDefinition(npc);
            }
        }
        NpcWarningNotifier.notifyNew(context.server(), npc, warningsBefore);
        LunaNpcChat.send(player, Component.translatable("message.luna_npc.npc_updated", LunaNpcChat.highlight(npc.name()), LunaNpcChat.highlight(npc.lunaId())));
        PermissionService.log(context.server(), player, PermissionLogAction.NPC_EDITED, npc.name(), npc.lunaId());
    }

    private static void handleRemoveNpc(RemoveNpcPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!PermissionService.canUse(context.server(), player, LunaStick.REMOVE_NPC)) {
            PermissionService.denied(player);
            return;
        }
        if (!(player.level() instanceof ServerLevel level)
                || !(level.getEntity(payload.entityId()) instanceof NpcEntity npc)
                || player.distanceToSqr(npc) > MAX_SPAWN_DISTANCE_SQUARED) {
            return;
        }
        String name = npc.getName().getString();
        long removedLunaId = npc.lunaId();
        npc.wipe();
        LunaNpcChat.send(player, Component.translatable("message.luna_npc.npc_removed",
                LunaNpcChat.highlight(name), LunaNpcChat.highlight(removedLunaId)));
        PermissionService.log(context.server(), player, PermissionLogAction.NPC_REMOVED, name, removedLunaId);
    }

    private static void handleToggleFavorite(ToggleFavoritePayload payload, ServerPlayNetworking.Context context) {
        LunaNpcApi.npcs(context.server())
                .setFavorite(context.player().getUUID(), payload.lunaId(), payload.favorite());
    }

    private static void handleRequestPermissions(RequestPermissionsPayload payload, ServerPlayNetworking.Context context) {
        MinecraftServer server = context.server();
        ServerPlayer player = context.player();
        if (!PermissionService.isAdmin(server, player)) {
            LunaNpcChat.send(player, Component.translatable("message.luna_npc.manage_denied"));
            return;
        }
        ServerPlayNetworking.send(player, buildPermissionsState(server));
    }

    private static void handleUpdatePermissions(UpdatePermissionsPayload payload, ServerPlayNetworking.Context context) {
        MinecraftServer server = context.server();
        ServerPlayer player = context.player();
        if (!PermissionService.isAdmin(server, player)) {
            return;
        }
        StickPermissionManager grants = StickPermissionManager.get(server);
        LunaStick stick = payload.stick();
        boolean changed;
        String targetName;
        if (payload.alliance()) {
            String allianceId = payload.target();
            if (!NpcAllianceManager.get(server).exists(allianceId)) {
                return;
            }
            changed = payload.grant() ? grants.grantAlliance(stick, allianceId)
                    : grants.revokeAlliance(stick, allianceId);
            NpcAlliance alliance = NpcAllianceManager.get(server).byId(allianceId);
            targetName = alliance == null ? allianceId : alliance.name();
        } else {
            UUID uuid = resolvePlayer(server, payload.target());
            if (uuid == null) {
                LunaNpcChat.send(player, Component.translatable("message.luna_npc.player_not_found"));
                return;
            }
            changed = payload.grant() ? grants.grantPlayer(stick, uuid) : grants.revokePlayer(stick, uuid);
            targetName = playerName(server, uuid);
        }
        if (changed) {
            PermissionService.log(server, player, payload.grant()
                            ? PermissionLogAction.PERMISSION_GRANTED : PermissionLogAction.PERMISSION_REVOKED,
                    targetName, stick.ordinal());
            broadcastPlayerPermissions(server);
        }
        ServerPlayNetworking.send(player, buildPermissionsState(server));
    }

    private static void handleRequestPermissionLog(RequestPermissionLogPayload payload, ServerPlayNetworking.Context context) {
        MinecraftServer server = context.server();
        ServerPlayer player = context.player();
        if (!PermissionService.isAdmin(server, player)) {
            return;
        }
        String category = payload.category();
        PermissionLogManager log = PermissionLogManager.get(server);
        List<PermissionLogEntry> window = log.window(payload.offset(), PERMISSION_LOG_PAGE, category);
        PlayerSkinStore skins = PlayerSkinStore.get(server);
        java.util.Map<UUID, net.minecraft.world.item.component.ResolvableProfile> actors =
                new java.util.LinkedHashMap<>();
        for (PermissionLogEntry entry : window) {
            actors.computeIfAbsent(entry.actor(), id -> skins.profileOf(id, entry.actorName()));
        }
        ServerPlayNetworking.send(player, new PermissionLogPayload(payload.offset(), log.size(category), category,
                window, List.copyOf(actors.values())));
    }

    private static PermissionsStatePayload buildPermissionsState(MinecraftServer server) {
        StickPermissionManager grants = StickPermissionManager.get(server);
        List<PermissionGrantDto> stickGrants = new ArrayList<>();
        for (LunaStick stick : LunaStick.values()) {
            stickGrants.add(new PermissionGrantDto(stick,
                    new ArrayList<>(grants.grantedPlayers(stick)),
                    new ArrayList<>(grants.grantedAlliances(stick))));
        }
        List<KnownPlayer> known = new ArrayList<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            known.add(new KnownPlayer(online.getUUID(), online.getGameProfile().name()));
        }
        return new PermissionsStatePayload(!server.usesAuthentication(), stickGrants, known);
    }

    private static void broadcastPlayerPermissions(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendPlayerPermissions(server, player);
        }
    }

    private static void sendPlayerPermissions(MinecraftServer server, ServerPlayer player) {
        ServerPlayNetworking.send(player, new PlayerPermissionsPayload(
                PermissionService.isAdmin(server, player),
                new ArrayList<>(PermissionService.allowedSticks(server, player))));
    }

    // Accepts a UUID or the name of an online player; returns null when a name can't be resolved (an
    // offline player's UUID has to be pasted directly).
    private static UUID resolvePlayer(MinecraftServer server, String target) {
        try {
            return UUID.fromString(target);
        } catch (IllegalArgumentException notAUuid) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(target);
            return online == null ? null : online.getUUID();
        }
    }

    private static String playerName(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        return online == null ? uuid.toString() : online.getGameProfile().name();
    }

    private static String orDefault(String soundId, String defaultSoundId) {
        return soundId == null || soundId.isBlank() ? defaultSoundId : soundId;
    }
}
