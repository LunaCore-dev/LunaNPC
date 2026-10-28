package net.lunacore.luna_npc.api;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

// A live NPC in the world, as opposed to an NpcDefinition template. This is what LunaNpcApi.spawn(),
// the LunaNpcEvents hooks and LunaNpcApi.requestAttack hand you. It exposes only safe operations —
// the backing entity is internal and never handed out directly, so an addon can't reach into engine
// state it shouldn't. Look the definition up via LunaNpcApi.npcs(server).byLunaId(lunaId()).
public interface NpcInstance {

    // The definition id this NPC was spawned from (stable across the world's lifetime).
    long lunaId();

    // The display name shown above the NPC, or null if hidden.
    Component getCustomName();

    // This entity's world UUID (unique per spawned instance, unlike lunaId).
    UUID getUUID();

    boolean isAlive();

    double getX();

    double getY();

    double getZ();

    float getHealth();

    float getMaxHealth();

    void setHealth(float health);

    // The factions this live NPC belongs to.
    List<String> allianceIds();

    // The job sub-type variant this instance rolled at spawn (-1 = no job or suppressed).
    int rolledJobSubType();

    // True when this instance spawned naturally and dropped its farming job (manual-spawn only).
    boolean jobSuppressed();

    // The NPC's current combat target, or null when it isn't fighting.
    LivingEntity combatTarget();

    // The range the NPC is currently fighting at (which weapon it is showing). CLOSE while idle.
    NpcAttackRange activeAttackRange();

    // Immutable snapshot of the farming buffer (copies). Empty for non-farmers or an empty buffer.
    List<ItemStack> farmedItems();

    // ---- Patrol job ----

    // The route this PATROL NPC walks ("" when it isn't patrolling) and the point it is heading to or
    // waiting at (-1 before it has picked one).
    String patrolRouteId();

    int patrolPointIndex();

    // Sends the patrol to this point next; ignored when out of range.
    void setPatrolPointIndex(int pointIndex);

    // ---- Bodyguard job ----

    // The entity this bodyguard is locked onto, or null (none yet, or a dead player it waits for).
    LivingEntity bodyguardProtectee();

    // The player who hired it (and may order it around), or null when it isn't hired.
    UUID bodyguardHirer();

    // Hires it for the player for seconds (0 = until it gives up, negative = the job's hire time), in
    // either source mode, replacing any current protectee. Ignored when the NPC's job isn't BODYGUARD.
    void hireBodyguard(UUID playerId, String playerName, int seconds);

    // Makes it give up its protectee (DISMISSED); it then runs its give-up action.
    void releaseBodyguard();

    NpcBodyguardMode bodyguardMode();

    // Orders a hired bodyguard; ignored while it isn't hired.
    void setBodyguardMode(NpcBodyguardMode mode);

    // Seconds of hire left; -1 when not hired or hired until it gives up.
    long bodyguardRemainingSeconds();

    // Move the NPC to a position in its current world.
    void teleport(double x, double y, double z);

    // Remove the NPC from the world (no drops, no death event).
    void discard();
}
