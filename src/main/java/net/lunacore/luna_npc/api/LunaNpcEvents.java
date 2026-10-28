package net.lunacore.luna_npc.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.lunacore.luna_npc.entity.NpcEntity;

// Server-side hooks for reacting to NPC lifecycle and combat. Register listeners once (e.g. on mod
// init); they fire on the server thread. Listeners are global — filter inside them by npc.lunaId()
// or npc.name() if you only care about specific NPCs.
public final class LunaNpcEvents {

    @FunctionalInterface
    public interface SpawnListener {
        void onSpawn(NpcInstance npc);
    }

    @FunctionalInterface
    public interface DeathListener {
        void onDeath(NpcInstance npc, DamageSource cause);
    }

    @FunctionalInterface
    public interface TargetListener {
        void onTargetChanged(NpcInstance npc, LivingEntity previous, LivingEntity current);
    }

    @FunctionalInterface
    public interface AllianceListener {
        void onAllianceChanged(String allianceId);
    }

    @FunctionalInterface
    public interface DialogActionListener {
        void onDialogAction(NpcInstance npc, ServerPlayer player, NpcDialogAction action);
    }

    @FunctionalInterface
    public interface PermissionListener {
        void onPermissionChanged(LunaStick stick);
    }

    @FunctionalInterface
    public interface CombatEventListener {
        void onCombatEvent(NpcInstance npc, String tag, LivingEntity target);
    }

    @FunctionalInterface
    public interface PatrolPointListener {
        void onPatrolPointReached(NpcInstance npc, String routeId, int pointIndex);
    }

    // hired is true for a hire (by a player, dialog, command or API), false for a protect target it picked.
    @FunctionalInterface
    public interface BodyguardLockListener {
        void onBodyguardLocked(NpcInstance npc, LivingEntity protectee, boolean hired);
    }

    @FunctionalInterface
    public interface BodyguardReleaseListener {
        void onBodyguardReleased(NpcInstance npc, java.util.UUID protecteeId, NpcBodyguardRelease reason);
    }

    private static final List<SpawnListener> SPAWN = new CopyOnWriteArrayList<>();
    private static final List<DeathListener> DEATH = new CopyOnWriteArrayList<>();
    private static final List<TargetListener> TARGET = new CopyOnWriteArrayList<>();
    private static final List<AllianceListener> ALLIANCE = new CopyOnWriteArrayList<>();
    private static final List<DialogActionListener> DIALOG_ACTION = new CopyOnWriteArrayList<>();
    private static final List<CombatEventListener> COMBAT_EVENT = new CopyOnWriteArrayList<>();
    private static final List<PermissionListener> PERMISSION = new CopyOnWriteArrayList<>();
    private static final List<PatrolPointListener> PATROL_POINT = new CopyOnWriteArrayList<>();
    private static final List<BodyguardLockListener> BODYGUARD_LOCKED = new CopyOnWriteArrayList<>();
    private static final List<BodyguardReleaseListener> BODYGUARD_RELEASED = new CopyOnWriteArrayList<>();

    private LunaNpcEvents() {
    }

    // A LunaNPC entity entered the world (natural spawn, zone spawn, Spawn stick or LunaNpcApi.spawn).
    public static void onNpcSpawn(SpawnListener listener) {
        SPAWN.add(listener);
    }

    // A LunaNPC entity died; cause is the killing damage source.
    public static void onNpcDeath(DeathListener listener) {
        DEATH.add(listener);
    }

    // A LunaNPC entity picked up or dropped a combat target (current is null when it stopped fighting).
    public static void onNpcTargetChanged(TargetListener listener) {
        TARGET.add(listener);
    }

    // A faction's membership or relationships changed (created, edited or deleted).
    public static void onAllianceChanged(AllianceListener listener) {
        ALLIANCE.add(listener);
    }

    // A DIALOG NPC ran an action from a conversation node for a player — the hook that lets a mod act
    // on any built-in action or a CUSTOM_EVENT tag, keeping the dialog job at full API/in-game parity.
    public static void onDialogAction(DialogActionListener listener) {
        DIALOG_ACTION.add(listener);
    }

    // An advanced-combat rule ran a CUSTOM_EVENT action, carrying its tag and the current target — the
    // scripting seam that lets a mod bolt custom behaviour onto a boss without touching its rules.
    public static void onCombatEvent(CombatEventListener listener) {
        COMBAT_EVENT.add(listener);
    }

    // A stick's grant set changed (a player or alliance was granted or revoked use of that stick).
    public static void onPermissionChanged(PermissionListener listener) {
        PERMISSION.add(listener);
    }

    // A PATROL NPC arrived at a point of its route (before it starts waiting there).
    public static void onPatrolPointReached(PatrolPointListener listener) {
        PATROL_POINT.add(listener);
    }

    // A bodyguard locked onto a new protectee: a protect target it picked, or a hire.
    public static void onBodyguardLocked(BodyguardLockListener listener) {
        BODYGUARD_LOCKED.add(listener);
    }

    // A bodyguard let go of its protectee; it now runs its give-up action (except for REPLACED).
    public static void onBodyguardReleased(BodyguardReleaseListener listener) {
        BODYGUARD_RELEASED.add(listener);
    }

    // ---- Fired by LunaNPC internally; not meant to be called by consumers. ----

    public static void firePatrolPointReached(NpcEntity npc, String routeId, int pointIndex) {
        for (PatrolPointListener listener : PATROL_POINT) {
            listener.onPatrolPointReached(npc, routeId, pointIndex);
        }
    }

    public static void fireBodyguardLocked(NpcEntity npc, LivingEntity protectee, boolean hired) {
        for (BodyguardLockListener listener : BODYGUARD_LOCKED) {
            listener.onBodyguardLocked(npc, protectee, hired);
        }
    }

    public static void fireBodyguardReleased(NpcEntity npc, java.util.UUID protecteeId, NpcBodyguardRelease reason) {
        for (BodyguardReleaseListener listener : BODYGUARD_RELEASED) {
            listener.onBodyguardReleased(npc, protecteeId, reason);
        }
    }

    public static void fireSpawn(NpcEntity npc) {
        for (SpawnListener listener : SPAWN) {
            listener.onSpawn(npc);
        }
    }

    public static void fireDeath(NpcEntity npc, DamageSource cause) {
        for (DeathListener listener : DEATH) {
            listener.onDeath(npc, cause);
        }
    }

    public static void fireTargetChanged(NpcEntity npc, LivingEntity previous, LivingEntity current) {
        if (previous == current) {
            return;
        }
        for (TargetListener listener : TARGET) {
            listener.onTargetChanged(npc, previous, current);
        }
    }

    public static void fireAllianceChanged(String allianceId) {
        for (AllianceListener listener : ALLIANCE) {
            listener.onAllianceChanged(allianceId);
        }
    }

    public static void fireDialogAction(NpcInstance npc, ServerPlayer player, NpcDialogAction action) {
        for (DialogActionListener listener : DIALOG_ACTION) {
            listener.onDialogAction(npc, player, action);
        }
    }

    public static void fireCombatEvent(NpcEntity npc, String tag, LivingEntity target) {
        for (CombatEventListener listener : COMBAT_EVENT) {
            listener.onCombatEvent(npc, tag, target);
        }
    }

    public static void firePermissionChanged(LunaStick stick) {
        for (PermissionListener listener : PERMISSION) {
            listener.onPermissionChanged(stick);
        }
    }
}
