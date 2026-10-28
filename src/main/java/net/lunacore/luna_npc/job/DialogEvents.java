package net.lunacore.luna_npc.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.lunacore.luna_npc.api.NpcDialog;
import net.lunacore.luna_npc.api.NpcDialogAction;
import net.lunacore.luna_npc.api.NpcDialogActionType;
import net.lunacore.luna_npc.api.NpcDialogChoice;
import net.lunacore.luna_npc.api.NpcDialogCondition;
import net.lunacore.luna_npc.api.NpcDialogNode;
import net.lunacore.luna_npc.api.NpcJobType;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.job.DialogProgressManager.PlayerDialogState;
import net.lunacore.luna_npc.network.DialogChoiceLine;
import net.lunacore.luna_npc.network.DialogChoicePayload;
import net.lunacore.luna_npc.network.DialogClosePayload;
import net.lunacore.luna_npc.network.DialogOpenPayload;
import net.lunacore.luna_npc.npc.NpcEquipment;

// The dialog runtime: opens a conversation, enters nodes (fire actions, auto-route JUMPs, offer the
// choices whose conditions pass for this player) and validates the player's picks. Per-player progress
// lives in DialogProgressManager; conditions are always re-checked server-side, never trusted from the
// client.
public final class DialogEvents {

    private static final double INTERACT_RANGE = 8.0;
    private static final double INTERACT_RANGE_SQUARED = 64.0;
    // A story that JUMPs through itself without ever stopping for an answer is a creator mistake, not a
    // reason to hang the server thread: cap how many nodes one entry may auto-chain through.
    private static final int MAX_JUMP_HOPS = 32;

    private DialogEvents() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                MinecraftServer server = player.level().getServer();
                if (server != null) {
                    DialogProgressManager.get(server).onPlayerDeath(player.getUUID());
                }
            }
        });
    }

    public static void open(ServerPlayer player, NpcEntity npc) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        NpcDialog dialog = npc.jobSettings().dialog();
        if (!dialog.hasStart()) {
            return;
        }
        registerAssignableQuests(server, dialog);
        QuestEvents.ensureQuestBook(server, player);
        npc.beginDialog(player.getUUID());
        DialogProgressManager manager = DialogProgressManager.get(server);
        PlayerDialogState state = manager.state(player.getUUID(), npc.lunaId());
        if (state == null) {
            state = new PlayerDialogState(dialog.startNodeId(), Set.of(),
                    dialog.storySurvivesNpcDeath(), dialog.storySurvivesPlayerDeath());
            manager.put(player.getUUID(), npc.lunaId(), state);
        }
        enterNode(server, npc, player, dialog, state.currentNodeId());
    }

    // Registers every quest the dialog can hand out, so the quest book and kill-tracking can resolve an
    // inline quest by id whenever the player opens the dialog — not only at the instant it was assigned.
    private static void registerAssignableQuests(MinecraftServer server, NpcDialog dialog) {
        QuestProgressManager manager = QuestProgressManager.get(server);
        for (NpcDialogNode node : dialog.nodes()) {
            for (NpcDialogAction action : node.actions()) {
                if (action.type() == NpcDialogActionType.ASSIGN_QUEST
                        && !action.quest().questId().isBlank()) {
                    manager.putAssignedDef(action.quest());
                }
            }
        }
    }

    public static void handleChoice(DialogChoicePayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        NpcEntity npc = resolveNpc(player, payload.lunaId());
        if (npc == null) {
            return;
        }
        NpcDialog dialog = npc.jobSettings().dialog();
        PlayerDialogState state = DialogProgressManager.get(server).state(player.getUUID(), npc.lunaId());
        if (state == null) {
            return;
        }
        NpcDialogNode node = dialog.nodeById(state.currentNodeId());
        if (node == null) {
            return;
        }
        int index = payload.choiceIndex();
        if (index < 0 || index >= node.choices().size()) {
            return;
        }
        NpcDialogChoice choice = node.choices().get(index);
        if (!choicePasses(server, npc, player, choice)) {
            return;
        }
        if (choice.targetNodeId().isBlank()) {
            npc.endDialog(player.getUUID());
            ServerPlayNetworking.send(player, new DialogClosePayload(npc.lunaId()));
            return;
        }
        enterNode(server, npc, player, dialog, choice.targetNodeId());
    }

    private static void enterNode(MinecraftServer server, NpcEntity npc, ServerPlayer player,
                                  NpcDialog dialog, String nodeId) {
        NpcDialogNode node = dialog.nodeById(nodeId);
        if (node == null) {
            node = dialog.nodeById(dialog.startNodeId());
        }
        int hops = 0;
        while (node != null) {
            playNodeSound(npc, node.interactSound());
            String jumpTarget = runActions(server, npc, player, node);
            playNodeSound(npc, node.triggerSound());
            saveNode(server, npc, player, node.nodeId());
            NpcDialogNode next = jumpTarget == null || jumpTarget.isBlank()
                    ? null : dialog.nodeById(jumpTarget);
            if (next == null || ++hops >= MAX_JUMP_HOPS) {
                break;
            }
            node = next;
        }
        if (node != null) {
            sendNode(server, npc, player, node);
        }
    }

    private static String runActions(MinecraftServer server, NpcEntity npc, ServerPlayer player,
                                     NpcDialogNode node) {
        String jumpTarget = null;
        for (NpcDialogAction action : node.actions()) {
            DialogActions.execute(server, npc, player, action);
            if (jumpTarget == null && action.type() == NpcDialogActionType.JUMP) {
                jumpTarget = action.stringArg();
            }
        }
        return jumpTarget;
    }

    private static void saveNode(MinecraftServer server, NpcEntity npc, ServerPlayer player,
                                 String nodeId) {
        DialogProgressManager manager = DialogProgressManager.get(server);
        PlayerDialogState state = manager.state(player.getUUID(), npc.lunaId());
        if (state != null && !state.currentNodeId().equals(nodeId)) {
            manager.put(player.getUUID(), npc.lunaId(), state.withNode(nodeId));
        }
    }

    private static void sendNode(MinecraftServer server, NpcEntity npc, ServerPlayer player,
                                 NpcDialogNode node) {
        List<DialogChoiceLine> lines = new ArrayList<>();
        List<NpcDialogChoice> choices = node.choices();
        for (int index = 0; index < choices.size(); index++) {
            NpcDialogChoice choice = choices.get(index);
            boolean endsTalk = choice.targetNodeId().isBlank();
            if (choicePasses(server, npc, player, choice)) {
                lines.add(new DialogChoiceLine(index, choice.text(), true, endsTalk));
            } else if (!choice.hideWhenLocked()) {
                lines.add(new DialogChoiceLine(index, choice.text(), false, endsTalk));
            }
        }
        ServerPlayNetworking.send(player, new DialogOpenPayload(npc.lunaId(),
                npc.getDisplayName().getString(), node.speakerLine(), lines));
    }

    private static boolean choicePasses(MinecraftServer server, NpcEntity npc, ServerPlayer player,
                                        NpcDialogChoice choice) {
        for (NpcDialogCondition condition : choice.conditions()) {
            if (!conditionPasses(server, npc, player, condition)) {
                return false;
            }
        }
        return true;
    }

    private static boolean conditionPasses(MinecraftServer server, NpcEntity npc, ServerPlayer player,
                                           NpcDialogCondition condition) {
        boolean met = switch (condition.type()) {
            case QUEST_COMPLETED ->
                    QuestProgressManager.get(server).hasCompleted(player.getUUID(), condition.stringArg());
            case HAS_ITEM -> countItem(player, condition.stringArg()) >= Math.max(1, condition.intArg());
            case FLAG_SET -> hasFlag(server, npc, player, condition.stringArg());
        };
        return condition.negate() != met;
    }

    private static boolean hasFlag(MinecraftServer server, NpcEntity npc, ServerPlayer player,
                                   String flag) {
        PlayerDialogState state = DialogProgressManager.get(server).state(player.getUUID(), npc.lunaId());
        return state != null && state.flags().contains(flag);
    }

    private static int countItem(ServerPlayer player, String itemId) {
        ItemStack resolved = NpcEquipment.resolve(itemId);
        if (resolved.isEmpty()) {
            return 0;
        }
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(resolved.getItem())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static NpcEntity resolveNpc(ServerPlayer player, long lunaId) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        AABB box = player.getBoundingBox().inflate(INTERACT_RANGE);
        for (NpcEntity npc : level.getEntitiesOfClass(NpcEntity.class, box, other -> other.lunaId() == lunaId)) {
            if (npc.jobActive() && npc.jobSettings().jobType() == NpcJobType.DIALOG
                    && !npc.isInteractionBlockedFor(player)
                    && player.distanceToSqr(npc) <= INTERACT_RANGE_SQUARED) {
                return npc;
            }
        }
        return null;
    }

    private static void playNodeSound(NpcEntity npc, String soundId) {
        if (soundId == null || soundId.isBlank()) {
            return;
        }
        Identifier parsed = Identifier.tryParse(soundId);
        if (parsed == null) {
            return;
        }
        BuiltInRegistries.SOUND_EVENT.getOptional(parsed).ifPresent(sound ->
                npc.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                        sound, SoundSource.NEUTRAL, 1.0F, 1.0F));
    }
}
