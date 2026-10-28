package net.lunacore.luna_npc.job;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.lunacore.luna_npc.LunaNpcChat;
import net.lunacore.luna_npc.api.LunaNpcApi;
import net.lunacore.luna_npc.api.NpcBodyguardHire;
import net.lunacore.luna_npc.api.NpcBodyguardMode;
import net.lunacore.luna_npc.api.NpcBodyguardRelease;
import net.lunacore.luna_npc.api.NpcBodyguardSource;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcJobType;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.network.BodyguardActionPayload;
import net.lunacore.luna_npc.network.BodyguardMenuPayload;

// Server side of the bodyguard job: the live set of working bodyguards, the damage hook that turns a hit
// on (or by) a protectee into a fight, the right-click menu (hire offer or the hirer's orders) and its
// buttons, and the helpers the dialog actions and the command share.
public final class BodyguardEvents {

    private static final double MENU_REACH_SQ = 8.0 * 8.0;
    private static final double GIVE_SPAWN_OFFSET = 1.5;
    // Bodyguards register themselves each tick while working and drop out when they stop or unload; a
    // weak set so an entity that vanished without notice never lingers.
    private static final Set<NpcEntity> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private BodyguardEvents() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) -> {
            if (ACTIVE.isEmpty() || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
                return;
            }
            for (NpcEntity guard : List.copyOf(ACTIVE)) {
                if (guard.isRemoved() || guard.level() != victim.level() || guard == victim) {
                    continue;
                }
                guard.bodyguardController().onProtectedHurt(victim, attacker);
                guard.bodyguardController().onProtectedAttacked(attacker, victim);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(BodyguardActionPayload.TYPE, BodyguardEvents::handleAction);
    }

    static void track(NpcEntity guard) {
        ACTIVE.add(guard);
    }

    public static void untrack(NpcEntity guard) {
        ACTIVE.remove(guard);
    }

    // The right-click: the hirer gets the orders menu; a HIRE guard that is free shows its offer and one
    // that guards someone says who. Returns false when the click should fall through.
    public static boolean openMenu(ServerPlayer player, NpcEntity guard) {
        NpcBodyguardController controller = guard.bodyguardController();
        NpcBodyguardHire hire = guard.jobSettings().bodyguard().hire();
        String name = guard.getName().getString();
        if (player.getUUID().equals(controller.hirer())) {
            ServerPlayNetworking.send(player, new BodyguardMenuPayload(guard.getId(), name, true, List.of(),
                    hire.hireSeconds(), controller.remainingHireSeconds(), controller.mode()));
            return true;
        }
        if (guard.jobSettings().bodyguard().source() != NpcBodyguardSource.HIRE) {
            return false;
        }
        if (controller.isLocked()) {
            LunaNpcChat.send(player, Component.translatable("message.luna_npc.bodyguard_taken",
                    LunaNpcChat.highlight(name), LunaNpcChat.highlight(controller.protecteeName())));
            return true;
        }
        ServerPlayNetworking.send(player, new BodyguardMenuPayload(guard.getId(), name, false, hire.cost(),
                hire.hireSeconds(), -1L, controller.mode()));
        return true;
    }

    private static void handleAction(BodyguardActionPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        Entity found = player.level().getEntity(payload.npcEntityId());
        if (!(found instanceof NpcEntity guard) || guard.jobSettings().jobType() != NpcJobType.BODYGUARD
                || player.distanceToSqr(guard) > MENU_REACH_SQ) {
            return;
        }
        NpcBodyguardController controller = guard.bodyguardController();
        if (payload.action() == BodyguardActionPayload.Action.HIRE) {
            hire(player, guard);
            return;
        }
        if (!player.getUUID().equals(controller.hirer())) {
            return;
        }
        switch (payload.action()) {
            case FOLLOW -> controller.order(NpcBodyguardMode.FOLLOW);
            case STAY -> controller.order(NpcBodyguardMode.STAY);
            case HOME -> controller.order(NpcBodyguardMode.HOME);
            case DISMISS -> controller.release(NpcBodyguardRelease.DISMISSED);
            case HIRE -> {
            }
        }
    }

    private static void hire(ServerPlayer player, NpcEntity guard) {
        NpcBodyguardHire hire = guard.jobSettings().bodyguard().hire();
        if (guard.jobSettings().bodyguard().source() != NpcBodyguardSource.HIRE
                || guard.bodyguardController().isLocked()) {
            return;
        }
        if (!PlayerItems.hasAll(player, hire.cost())) {
            LunaNpcChat.send(player, Component.translatable("message.luna_npc.bodyguard_cant_afford"));
            return;
        }
        PlayerItems.take(player, hire.cost());
        guard.bodyguardController().hire(player.getUUID(), player.getGameProfile().name(), -1);
        LunaNpcChat.send(player, Component.translatable("message.luna_npc.bodyguard_hired",
                LunaNpcChat.highlight(guard.getName().getString())));
    }

    // Every working bodyguard hired by this player, in any loaded dimension.
    public static List<NpcEntity> guardsHiredBy(UUID player) {
        List<NpcEntity> hired = new ArrayList<>();
        for (NpcEntity guard : ACTIVE) {
            if (!guard.isRemoved() && player.equals(guard.bodyguardController().hirer())) {
                hired.add(guard);
            }
        }
        return hired;
    }

    // The dialog GIVE_BODYGUARD action: spawns the NPC beside the player and, when it is a bodyguard,
    // hires it for the player (seconds < 0 = the job's hire time). Returns the spawned NPC or null.
    public static NpcEntity giveBodyguard(MinecraftServer server, ServerPlayer player, long lunaId, int seconds) {
        NpcDefinition definition = LunaNpcApi.npcs(server).byLunaId(lunaId).orElse(null);
        if (definition == null || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        double angle = Math.toRadians(player.getYRot() + 90.0F);
        double x = player.getX() + Math.cos(angle) * GIVE_SPAWN_OFFSET;
        double z = player.getZ() + Math.sin(angle) * GIVE_SPAWN_OFFSET;
        NpcEntity guard = NpcEntity.spawn(level, definition, EntitySpawnReason.MOB_SUMMONED,
                x, player.getY(), z, player.getYRot(), BlockPos.containing(x, player.getY(), z));
        if (guard != null && definition.jobs().jobType() == NpcJobType.BODYGUARD) {
            guard.bodyguardController().hire(player.getUUID(), player.getGameProfile().name(), seconds);
        }
        return guard;
    }
}
