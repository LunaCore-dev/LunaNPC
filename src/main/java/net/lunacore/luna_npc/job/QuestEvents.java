package net.lunacore.luna_npc.job;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.LunaNpcChat;
import net.lunacore.luna_npc.api.LunaNpcApi;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcJobType;
import net.lunacore.luna_npc.api.NpcObjectiveType;
import net.lunacore.luna_npc.api.NpcQuest;
import net.lunacore.luna_npc.api.NpcQuestCompletion;
import net.lunacore.luna_npc.api.NpcQuestObjective;
import net.lunacore.luna_npc.api.NpcQuestSubType;
import net.lunacore.luna_npc.api.NpcRegistry;
import net.lunacore.luna_npc.api.NpcTradeStack;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.item.LunaNpcItems;
import net.lunacore.luna_npc.job.QuestProgressManager.ActiveQuest;
import net.lunacore.luna_npc.network.ActiveQuestsPayload;
import net.lunacore.luna_npc.network.ActiveQuestsPayload.ActiveQuestView;
import net.lunacore.luna_npc.network.QuestAcceptPayload;
import net.lunacore.luna_npc.network.QuestAbandonPayload;
import net.lunacore.luna_npc.network.QuestBookRequestPayload;
import net.lunacore.luna_npc.network.QuestOffersPayload;
import net.lunacore.luna_npc.network.QuestOffersPayload.QuestStatusView;
import net.lunacore.luna_npc.network.QuestTurnInPayload;
import net.lunacore.luna_npc.npc.NpcEquipment;

// The quest runtime: it tracks kills toward accepted quests, sweeps out expired ones, and answers the
// accept/turn-in requests. Offer status and reward grants are computed here too, so screen, command
// and kill hook all read one source of truth for what counts as done.
public final class QuestEvents {

    private static final int EXPIRY_SWEEP_INTERVAL_TICKS = 100;
    private static final double INTERACT_RANGE_SQUARED = 64.0;

    private QuestEvents() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getEntity() instanceof ServerPlayer killer) {
                onKill(killer, entity, damageSource);
            }
            if (entity instanceof NpcEntity npc) {
                onNpcDeath(npc);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(QuestEvents::sweepPlayers);
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!player.getItemInHand(hand).is(LunaNpcItems.QUEST_BOOK)) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                sendActiveQuests(serverPlayer);
            }
            return InteractionResult.SUCCESS;
        });
    }

    public static void openQuestOffers(ServerPlayer player, NpcEntity npc) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        QuestProgressManager manager = QuestProgressManager.get(server);
        long now = server.overworld().getGameTime();
        List<NpcQuest> offered = new ArrayList<>();
        List<QuestStatusView> statuses = new ArrayList<>();
        for (NpcQuest quest : availableQuests(npc)) {
            ActiveQuest active = manager.active(player.getUUID(), quest.questId());
            // Locked quests stay hidden until their prerequisites are done, but an already-accepted one
            // is always shown so it can still be turned in.
            if (active == null
                    && !manager.hasAllCompleted(player.getUUID(), quest.prerequisiteQuestIds())) {
                continue;
            }
            offered.add(quest);
            statuses.add(statusFor(player, quest, active, now));
        }
        ServerPlayNetworking.send(player, new QuestOffersPayload(npc.getId(), offered, statuses));
    }

    public static void handleAccept(QuestAcceptPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        NpcEntity npc = resolveNpc(player, payload.npcEntityId());
        if (npc == null) {
            return;
        }
        NpcQuest quest = offeredQuest(npc, payload.questId());
        if (quest == null) {
            return;
        }
        MinecraftServer server = context.server();
        QuestProgressManager manager = QuestProgressManager.get(server);
        if (manager.active(player.getUUID(), quest.questId()) != null
                || !manager.hasAllCompleted(player.getUUID(), quest.prerequisiteQuestIds())) {
            return;
        }
        assignQuest(server, player, npc.lunaId(), quest);
        openQuestOffers(player, npc);
    }

    // Hands an inline quest straight to a player (dialog ASSIGN_QUEST): the shared accept-and-give-book
    // path the offer screen uses, minus the offer/prerequisite gating a direct grant skips.
    public static void assignQuest(MinecraftServer server, ServerPlayer player, long lunaId, NpcQuest quest) {
        if (quest.questId().isBlank()) {
            return;
        }
        QuestProgressManager manager = QuestProgressManager.get(server);
        // Always register the definition and make sure the player has a book (a no-op if they already
        // hold one); only the accept + message are skipped when the quest is already active, so a
        // re-trigger still hands out the book instead of silently doing nothing.
        manager.putAssignedDef(quest);
        giveQuestBook(player);
        if (manager.active(player.getUUID(), quest.questId()) != null) {
            return;
        }
        long now = server.overworld().getGameTime();
        long dueTick = quest.timeLimitSeconds() > 0 ? now + quest.timeLimitSeconds() * 20L : 0L;
        List<Integer> progress = new ArrayList<>(quest.objectives().size());
        for (int index = 0; index < quest.objectives().size(); index++) {
            progress.add(0);
        }
        manager.accept(player.getUUID(), new ActiveQuest(lunaId, quest.questId(), progress, dueTick, now));
        LunaNpcChat.send(player, Component.translatable("message.luna_npc.quests.accepted",
                LunaNpcChat.highlight(quest.name())));
    }

    public static void handleTurnIn(QuestTurnInPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        NpcEntity npc = resolveNpc(player, payload.npcEntityId());
        if (npc == null) {
            return;
        }
        NpcQuest quest = offeredQuest(npc, payload.questId());
        if (quest == null) {
            return;
        }
        MinecraftServer server = context.server();
        QuestProgressManager manager = QuestProgressManager.get(server);
        ActiveQuest active = manager.active(player.getUUID(), quest.questId());
        if (active == null || active.isExpired(server.overworld().getGameTime())
                || !isReady(quest, active, player)) {
            return;
        }
        consumeCollectItems(player.getInventory(), quest);
        completeQuest(player, manager, quest, npc.lunaId());
        openQuestOffers(player, npc);
    }

    // The single reward path: an XP roll plus each configured item stack, dropped at the player when
    // the inventory can't hold it. Called by both the turn-in handler and the auto-complete kill.
    public static void grantReward(ServerPlayer player, NpcQuest quest) {
        int xp = rollXp(player.getRandom(), quest.xpMin(), quest.xpMax());
        if (xp > 0) {
            player.giveExperiencePoints(xp);
        }
        for (NpcTradeStack reward : quest.itemRewards()) {
            ItemStack stack = NpcEquipment.resolve(reward.itemId(), reward.enchants(),
                    player.level().registryAccess());
            if (stack.isEmpty()) {
                continue;
            }
            stack.setCount(reward.count());
            RewardDelivery.give(player, stack);
        }
    }

    // The single completion path: grant the reward, record the quest as completed (for prerequisites),
    // drop its bound book and notify. Turn-in, auto-kill and enter-complete all funnel through here.
    private static void completeQuest(ServerPlayer player, QuestProgressManager manager, NpcQuest quest,
                                      long lunaId) {
        grantReward(player, quest);
        manager.remove(player.getUUID(), quest.questId());
        manager.markCompleted(player.getUUID(), quest.questId());
        playCompleteSound(player, lunaId);
        LunaNpcChat.send(player, Component.translatable("message.luna_npc.quests.completed", LunaNpcChat.highlight(quest.name())));
    }

    private static void playCompleteSound(ServerPlayer player, long lunaId) {
        MinecraftServer server = player.level().getServer();
        String soundId = "";
        if (server != null) {
            NpcDefinition definition = LunaNpcApi.npcs(server).byLunaId(lunaId).orElse(null);
            if (definition != null) {
                soundId = definition.jobs().questCompleteSound();
            }
        }
        SoundEvent sound = NpcEntity.soundOrDefault(soundId, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // Death of NPC L cancels the quests it offered that are flagged cancel-on-death; the rest persist
    // and can still be turned in at any live or respawned copy of L.
    private static void onNpcDeath(NpcEntity npc) {
        MinecraftServer server = npc.level().getServer();
        if (server == null) {
            return;
        }
        long lunaId = npc.lunaId();
        QuestProgressManager manager = QuestProgressManager.get(server);
        NpcRegistry registry = LunaNpcApi.npcs(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (ActiveQuest active : manager.quests(player.getUUID())) {
                if (active.lunaId() != lunaId) {
                    continue;
                }
                NpcQuest definition = resolveQuest(registry, manager, lunaId, active.questId());
                if (definition != null && definition.cancelOnNpcDeath()) {
                    manager.remove(player.getUUID(), active.questId());
                    LunaNpcChat.send(player,
                            Component.translatable("message.luna_npc.quests.cancelled", LunaNpcChat.highlight(definition.name())));
                }
            }
        }
    }

    public static void handleBookRequest(QuestBookRequestPayload payload, ServerPlayNetworking.Context context) {
        sendActiveQuests(context.player());
    }

    // Drops a quest from the player's book on request (the client confirms first). No reward, no
    // completion — it simply leaves the active list, freeing a quest whose NPC is gone or unreachable.
    public static void handleAbandon(QuestAbandonPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        QuestProgressManager manager = QuestProgressManager.get(server);
        ActiveQuest active = manager.active(player.getUUID(), payload.questId());
        if (active == null) {
            return;
        }
        NpcQuest definition = resolveQuest(LunaNpcApi.npcs(server), manager, active.lunaId(), payload.questId());
        manager.remove(player.getUUID(), payload.questId());
        LunaNpcChat.send(player, Component.translatable("message.luna_npc.quests.abandoned",
                LunaNpcChat.highlight(definition != null ? definition.name() : payload.questId())));
        sendActiveQuests(player);
    }

    // Snapshot of the player's active quests for the quest book: each objective's live progress plus a
    // countdown, so the client renders progress and ticks the time-left down locally between refreshes.
    public static void sendActiveQuests(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        QuestProgressManager manager = QuestProgressManager.get(server);
        NpcRegistry registry = LunaNpcApi.npcs(server);
        long now = server.overworld().getGameTime();
        List<ActiveQuestView> views = new ArrayList<>();
        for (ActiveQuest active : manager.quests(player.getUUID())) {
            if (active.isExpired(now)) {
                continue;
            }
            NpcQuest definition = resolveQuest(registry, manager, active.lunaId(), active.questId());
            if (definition == null) {
                continue;
            }
            String title = definition.title().isBlank() ? definition.name() : definition.title();
            boolean timed = active.dueTick() > 0;
            int secondsLeft = timed ? (int) Math.max(0, (active.dueTick() - now) / 20) : 0;
            views.add(new ActiveQuestView(active.questId(), title, definition.description(),
                    definition.objectives(), displayProgress(definition, active, player), timed, secondsLeft));
        }
        ServerPlayNetworking.send(player, new ActiveQuestsPayload(views));
    }

    // One plain, stackable quest book that lists every active quest — not bound to any quest, so a
    // completed quest just leaves the book (it drops out of the active list) while the item stays. Given
    // only when the player has none, so they never accumulate.
    private static void giveQuestBook(ServerPlayer player) {
        if (playerHasQuestBook(player)) {
            return;
        }
        ItemStack book = new ItemStack(LunaNpcItems.QUEST_BOOK);
        player.getInventory().add(book);
        if (!book.isEmpty()) {
            // Full inventory: spawn a visible drop in front of the player instead of losing the book.
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            var level = player.level();
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    level, player.getX() + look.x, player.getEyeY() - 0.2, player.getZ() + look.z, book);
            drop.setDeltaMovement(look.x * 0.3, 0.1, look.z * 0.3);
            drop.setPickUpDelay(20);
            level.addFreshEntity(drop);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.5, 0.4, 0.0);
            LunaNpcChat.send(player, Component.translatable("message.luna_npc.quests.book_dropped"));
        }
    }

    // Gives the player a book if they have active quests but no book (e.g. they dropped it, or a quest
    // was assigned before this behaviour existed). Idempotent — safe to call whenever.
    public static void ensureQuestBook(MinecraftServer server, ServerPlayer player) {
        if (!QuestProgressManager.get(server).quests(player.getUUID()).isEmpty()) {
            giveQuestBook(player);
        }
    }

    private static boolean playerHasQuestBook(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(LunaNpcItems.QUEST_BOOK)) {
                return true;
            }
        }
        return false;
    }

    public static NpcQuest resolveQuest(NpcRegistry registry, QuestProgressManager manager, long lunaId,
                                        String questId) {
        NpcDefinition definition = registry.byLunaId(lunaId).orElse(null);
        if (definition != null) {
            for (NpcQuestSubType subType : definition.jobs().questSubTypes()) {
                for (NpcQuest quest : subType.quests()) {
                    if (quest.questId().equals(questId)) {
                        return quest;
                    }
                }
            }
        }
        // Inline quests handed out by a DIALOG action live in no NPC's list; fall back to the store.
        return manager.assignedDef(questId);
    }

    public static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private static void onKill(ServerPlayer killer, LivingEntity victim, DamageSource source) {
        MinecraftServer server = killer.level().getServer();
        if (server == null) {
            return;
        }
        QuestProgressManager manager = QuestProgressManager.get(server);
        List<ActiveQuest> active = manager.quests(killer.getUUID());
        if (active.isEmpty()) {
            return;
        }
        NpcRegistry registry = LunaNpcApi.npcs(server);
        String victimTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
        boolean victimIsPlayer = victim instanceof ServerPlayer;
        String weaponId = killWeaponId(killer, source);
        long now = server.overworld().getGameTime();
        for (ActiveQuest quest : active) {
            if (quest.isExpired(now)) {
                continue;
            }
            NpcQuest definition = resolveQuest(registry, manager, quest.lunaId(), quest.questId());
            if (definition != null) {
                applyKill(killer, manager, quest, definition, victimTypeId, victimIsPlayer, weaponId);
            }
        }
    }

    private static void applyKill(ServerPlayer killer, QuestProgressManager manager, ActiveQuest quest,
                                  NpcQuest definition, String victimTypeId, boolean victimIsPlayer,
                                  String weaponId) {
        List<NpcQuestObjective> objectives = definition.objectives();
        List<Integer> progress = new ArrayList<>(objectives.size());
        boolean changed = false;
        for (int index = 0; index < objectives.size(); index++) {
            NpcQuestObjective objective = objectives.get(index);
            int current = storedProgress(quest, index);
            if (current < objective.amount() && killMatches(objective, victimTypeId, victimIsPlayer, weaponId)) {
                current++;
                changed = true;
            }
            progress.add(current);
        }
        if (!changed) {
            return;
        }
        ActiveQuest updated = quest.withProgress(progress);
        if (definition.completion() == NpcQuestCompletion.AUTO && objectivesComplete(definition, updated)) {
            completeQuest(killer, manager, definition, quest.lunaId());
        } else {
            manager.replace(killer.getUUID(), updated);
        }
    }

    private static boolean killMatches(NpcQuestObjective objective, String victimTypeId,
                                       boolean victimIsPlayer, String weaponId) {
        return switch (objective.type()) {
            case KILL_MOB -> objective.targetId().equals(victimTypeId)
                    && weaponMatches(objective.requiredWeaponId(), weaponId);
            case KILL_PLAYERS -> victimIsPlayer && weaponMatches(objective.requiredWeaponId(), weaponId);
            default -> false;
        };
    }

    private static void sweepPlayers(MinecraftServer server) {
        if (server.overworld().getGameTime() % EXPIRY_SWEEP_INTERVAL_TICKS != 0) {
            return;
        }
        long now = server.overworld().getGameTime();
        QuestProgressManager manager = QuestProgressManager.get(server);
        NpcRegistry registry = LunaNpcApi.npcs(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            checkEnterObjectives(player, manager, registry, now);
            for (ActiveQuest expired : manager.removeExpired(player.getUUID(), now)) {
                NpcQuest definition = resolveQuest(registry, manager, expired.lunaId(), expired.questId());
                if (definition != null) {
                    LunaNpcChat.send(player,
                            Component.translatable("message.luna_npc.quests.failed", LunaNpcChat.highlight(definition.name())));
                }
            }
        }
    }

    // Location objectives complete the moment the player stands in the target biome, structure or
    // dimension. Checked on the sweep, so simply passing through completes it near the actual moment.
    private static void checkEnterObjectives(ServerPlayer player, QuestProgressManager manager,
                                             NpcRegistry registry, long now) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (ActiveQuest active : manager.quests(player.getUUID())) {
            if (active.isExpired(now)) {
                continue;
            }
            NpcQuest definition = resolveQuest(registry, manager, active.lunaId(), active.questId());
            if (definition != null) {
                applyEnter(player, level, manager, active, definition);
            }
        }
    }

    private static void applyEnter(ServerPlayer player, ServerLevel level, QuestProgressManager manager,
                                   ActiveQuest quest, NpcQuest definition) {
        List<NpcQuestObjective> objectives = definition.objectives();
        List<Integer> progress = new ArrayList<>(objectives.size());
        boolean changed = false;
        for (int index = 0; index < objectives.size(); index++) {
            NpcQuestObjective objective = objectives.get(index);
            int current = storedProgress(quest, index);
            if (objective.type().isEnterObjective() && current < 1 && inEnterTarget(player, level, objective)) {
                current = 1;
                changed = true;
            }
            progress.add(current);
        }
        if (!changed) {
            return;
        }
        ActiveQuest updated = quest.withProgress(progress);
        if (definition.completion() == NpcQuestCompletion.AUTO && objectivesComplete(definition, updated)) {
            completeQuest(player, manager, definition, quest.lunaId());
        } else {
            manager.replace(player.getUUID(), updated);
        }
    }

    private static boolean inEnterTarget(ServerPlayer player, ServerLevel level, NpcQuestObjective objective) {
        String targetId = objective.targetId();
        return switch (objective.type()) {
            case ENTER_BIOME -> {
                Identifier id = Identifier.tryParse(targetId);
                yield id != null && level.getBiome(player.blockPosition()).is(id);
            }
            case ENTER_STRUCTURE -> {
                Identifier id = Identifier.tryParse(targetId);
                yield id != null && level.structureManager()
                        .getStructureWithPieceAt(player.blockPosition(), holder -> holder.is(id)).isValid();
            }
            case ENTER_DIMENSION -> level.dimension().identifier().toString().equals(targetId);
            default -> false;
        };
    }

    private static QuestStatusView statusFor(ServerPlayer player, NpcQuest quest, ActiveQuest active, long now) {
        if (active == null) {
            return new QuestStatusView(quest.questId(), QuestStatusView.OFFERABLE, List.of(), 0);
        }
        List<Integer> progress = displayProgress(quest, active, player);
        byte state = isReady(quest, active, player) ? QuestStatusView.READY : QuestStatusView.ACTIVE;
        int secondsLeft = active.dueTick() > 0 ? (int) Math.max(0, (active.dueTick() - now) / 20) : 0;
        return new QuestStatusView(quest.questId(), state, progress, secondsLeft);
    }

    // A collect objective's "progress" is the live inventory count; a kill objective's is the stored
    // count. Both are capped at the requirement so the screen never shows more than the goal.
    private static List<Integer> displayProgress(NpcQuest quest, ActiveQuest active, ServerPlayer player) {
        List<NpcQuestObjective> objectives = quest.objectives();
        List<Integer> progress = new ArrayList<>(objectives.size());
        for (int index = 0; index < objectives.size(); index++) {
            NpcQuestObjective objective = objectives.get(index);
            int have = objective.type() == NpcObjectiveType.COLLECT_ITEMS
                    ? countItem(player.getInventory(), objective.targetId())
                    : storedProgress(active, index);
            progress.add(Math.min(have, objective.amount()));
        }
        return progress;
    }

    private static boolean isReady(NpcQuest quest, ActiveQuest active, ServerPlayer player) {
        List<NpcQuestObjective> objectives = quest.objectives();
        for (int index = 0; index < objectives.size(); index++) {
            NpcQuestObjective objective = objectives.get(index);
            int have = objective.type() == NpcObjectiveType.COLLECT_ITEMS
                    ? countItem(player.getInventory(), objective.targetId())
                    : storedProgress(active, index);
            if (have < objective.amount()) {
                return false;
            }
        }
        return true;
    }

    private static boolean objectivesComplete(NpcQuest quest, ActiveQuest active) {
        List<NpcQuestObjective> objectives = quest.objectives();
        for (int index = 0; index < objectives.size(); index++) {
            if (storedProgress(active, index) < objectives.get(index).amount()) {
                return false;
            }
        }
        return true;
    }

    private static void consumeCollectItems(Inventory inventory, NpcQuest quest) {
        for (NpcQuestObjective objective : quest.objectives()) {
            if (objective.type() == NpcObjectiveType.COLLECT_ITEMS) {
                removeItems(inventory, objective.targetId(), objective.amount());
            }
        }
    }

    private static NpcEntity resolveNpc(ServerPlayer player, int entityId) {
        if (!(player.level() instanceof ServerLevel level)
                || !(level.getEntity(entityId) instanceof NpcEntity npc)) {
            return null;
        }
        if (!npc.jobActive() || npc.jobSettings().jobType() != NpcJobType.QUESTS
                || npc.isInteractionBlockedFor(player)
                || player.distanceToSqr(npc) > INTERACT_RANGE_SQUARED) {
            return null;
        }
        return npc;
    }

    private static NpcQuest offeredQuest(NpcEntity npc, String questId) {
        for (NpcQuest quest : availableQuests(npc)) {
            if (quest.questId().equals(questId)) {
                return quest;
            }
        }
        return null;
    }

    // The quests this NPC can offer: with offer-all on, every quest across all sub-types; otherwise the
    // single sub-type it rolled at spawn. Prerequisite gating is applied per player at offer time.
    private static List<NpcQuest> availableQuests(NpcEntity npc) {
        if (!npc.jobActive() || npc.jobSettings().jobType() != NpcJobType.QUESTS) {
            return List.of();
        }
        List<NpcQuestSubType> subTypes = npc.jobSettings().questSubTypes();
        if (npc.jobSettings().offerAllQuests()) {
            List<NpcQuest> all = new ArrayList<>();
            for (NpcQuestSubType subType : subTypes) {
                all.addAll(subType.quests());
            }
            return all;
        }
        int index = npc.rolledJobSubType();
        if (index < 0 || index >= subTypes.size()) {
            return List.of();
        }
        return subTypes.get(index).quests();
    }

    private static int storedProgress(ActiveQuest active, int index) {
        return index < active.progress().size() ? active.progress().get(index) : 0;
    }

    private static int rollXp(RandomSource random, int min, int max) {
        if (max <= min) {
            return Math.max(0, min);
        }
        return min + random.nextInt(max - min + 1);
    }

    private static String killWeaponId(ServerPlayer killer, DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        if (weapon == null || weapon.isEmpty()) {
            weapon = killer.getMainHandItem();
        }
        return BuiltInRegistries.ITEM.getKey(weapon.getItem()).toString();
    }

    private static boolean weaponMatches(String requiredWeaponId, String weaponId) {
        return requiredWeaponId == null || requiredWeaponId.isBlank() || requiredWeaponId.equals(weaponId);
    }

    private static int countItem(Inventory inventory, String itemId) {
        Item item = itemOf(itemId);
        if (item == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void removeItems(Inventory inventory, String itemId, int amount) {
        Item item = itemOf(itemId);
        if (item == null) {
            return;
        }
        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                inventory.removeItem(slot, take);
                remaining -= take;
            }
        }
    }

    private static Item itemOf(String itemId) {
        ItemStack stack = NpcEquipment.resolve(itemId);
        return stack.isEmpty() ? null : stack.getItem();
    }
}
