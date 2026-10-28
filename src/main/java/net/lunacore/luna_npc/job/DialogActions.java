package net.lunacore.luna_npc.job;

import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.api.LunaNpcEvents;
import net.lunacore.luna_npc.api.NpcBodyguardMode;
import net.lunacore.luna_npc.api.NpcDialogAction;
import net.lunacore.luna_npc.api.NpcTradeStack;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.npc.NpcEquipment;

// Runs one dialog node action ("lego brick") on the server, then fires the public API event so a
// developer sees every action — including CUSTOM_EVENT, whose entire effect is that event. JUMP is
// resolved by DialogEvents (auto-routing after a node's actions), so it has no arm here.
public final class DialogActions {

    private DialogActions() {
    }

    public static void execute(MinecraftServer server, NpcEntity npc, ServerPlayer player,
                               NpcDialogAction action) {
        switch (action.type()) {
            case ASSIGN_QUEST -> QuestEvents.assignQuest(server, player, npc.lunaId(), action.quest());
            case OPEN_TRADE -> LunaNpcMenus.openTradeMenu(player, npc, action.intArg());
            case ATTACK_PLAYER -> npc.forceAttack(player);
            case MOVE_TO_ANCHOR -> npc.moveToAnchor(action.anchor(), action.intArg());
            case SET_RELATION -> setRelation(npc, player, action.flag());
            case GIVE_ITEMS -> giveItems(player, action.items());
            case TAKE_ITEMS -> PlayerItems.take(player, action.items());
            case GIVE_XP -> grantExperience(player, action.intArg());
            case RUN_COMMAND -> runCommand(server, npc, player, action);
            case PARTICLE -> spawnParticles(npc, action.stringArg());
            case SET_FLAG -> setFlag(server, npc, player, action.stringArg(), true);
            case CLEAR_FLAG -> setFlag(server, npc, player, action.stringArg(), false);
            case GIVE_BODYGUARD -> BodyguardEvents.giveBodyguard(server, player,
                    NpcDialogAction.parseLunaId(action.stringArg()), action.intArg() > 0 ? action.intArg() : -1);
            case ORDER_BODYGUARDS -> orderBodyguards(player, action.stringArg());
            case JUMP, CUSTOM_EVENT -> {
            }
        }
        LunaNpcEvents.fireDialogAction(npc, player, action);
    }

    private static void orderBodyguards(ServerPlayer player, String order) {
        for (NpcEntity guard : BodyguardEvents.guardsHiredBy(player.getUUID())) {
            switch (order) {
                case "follow" -> guard.bodyguardController().order(NpcBodyguardMode.FOLLOW);
                case "stay" -> guard.bodyguardController().order(NpcBodyguardMode.STAY);
                case "home" -> guard.bodyguardController().order(NpcBodyguardMode.HOME);
                case "dismiss" -> guard.bodyguardController().release(net.lunacore.luna_npc.api.NpcBodyguardRelease.DISMISSED);
                default -> {
                }
            }
        }
    }

    private static void setRelation(NpcEntity npc, ServerPlayer player, boolean hostile) {
        npc.setRelationHostile(player, hostile);
    }

    private static void grantExperience(ServerPlayer player, int amount) {
        if (amount > 0) {
            player.giveExperiencePoints(amount);
        }
    }

    private static void giveItems(ServerPlayer player, List<NpcTradeStack> items) {
        for (NpcTradeStack give : items) {
            ItemStack stack = NpcEquipment.resolve(give.itemId(), give.enchants(),
                    player.level().registryAccess());
            if (stack.isEmpty()) {
                continue;
            }
            stack.setCount(give.count());
            RewardDelivery.give(player, stack);
        }
    }

    // As-player runs with the player's own permissions; otherwise a gamemaster (level-2) console source
    // located at the NPC. Advanced and opt-in — the creator owns the security of whatever command they
    // wire in.
    private static void runCommand(MinecraftServer server, NpcEntity npc, ServerPlayer player,
                                   NpcDialogAction action) {
        String command = action.stringArg();
        if (command == null || command.isBlank()) {
            return;
        }
        CommandSourceStack source = action.flag()
                ? player.createCommandSourceStack()
                : npcCommandSource(server, npc);
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static CommandSourceStack npcCommandSource(MinecraftServer server, NpcEntity npc) {
        CommandSourceStack source = server.createCommandSourceStack()
                .withEntity(npc)
                .withPosition(npc.position())
                .withPermission(LevelBasedPermissionSet.GAMEMASTER)
                .withSuppressedOutput();
        return npc.level() instanceof ServerLevel level ? source.withLevel(level) : source;
    }

    private static void spawnParticles(NpcEntity npc, String particleId) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(parseParticle(particleId), npc.getX(), npc.getY() + 1.0, npc.getZ(),
                12, 0.4, 0.5, 0.4, 0.0);
    }

    private static ParticleOptions parseParticle(String particleId) {
        if (particleId != null && !particleId.isBlank()) {
            Identifier parsed = Identifier.tryParse(particleId);
            if (parsed != null
                    && BuiltInRegistries.PARTICLE_TYPE.getOptional(parsed).orElse(null)
                            instanceof ParticleOptions options) {
                return options;
            }
        }
        return ParticleTypes.HAPPY_VILLAGER;
    }

    private static void setFlag(MinecraftServer server, NpcEntity npc, ServerPlayer player,
                               String flag, boolean set) {
        if (flag == null || flag.isBlank()) {
            return;
        }
        DialogProgressManager manager = DialogProgressManager.get(server);
        DialogProgressManager.PlayerDialogState state = manager.state(player.getUUID(), npc.lunaId());
        if (state != null) {
            manager.put(player.getUUID(), npc.lunaId(), state.withFlag(flag, set));
        }
    }
}
