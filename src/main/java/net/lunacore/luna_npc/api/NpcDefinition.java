package net.lunacore.luna_npc.api;

import java.util.List;
import net.lunacore.luna_npc.api.NpcBossBarSettings;
import net.lunacore.luna_npc.api.SurfaceMode;
import net.lunacore.luna_npc.api.NpcCombatSettings;
import net.lunacore.luna_npc.api.NpcDropSettings;
import net.lunacore.luna_npc.api.NpcEquipmentSettings;
import net.lunacore.luna_npc.api.NpcModelSettings;
import net.lunacore.luna_npc.api.NpcMovementSettings;
import net.lunacore.luna_npc.api.NpcSpawnSettings;
import net.lunacore.luna_npc.api.NpcTraitsSettings;

public interface NpcDefinition {

    long lunaId();

    // Who may change this definition. Every setter below throws IllegalStateException on a GUI_ONLY NPC;
    // changing the access itself follows the same rule.
    NpcEditAccess editAccess();

    void setEditAccess(NpcEditAccess access);

    String name();

    void setName(String name);

    boolean isNameTagShown();

    void setNameTagShown(boolean shown);

    String tagColor();

    void setTagColor(String tagColor);

    String category();

    void setCategory(String category);

    int health();

    void setHealth(int health);

    String hurtSoundId();

    void setHurtSoundId(String soundId);

    String dieSoundId();

    void setDieSoundId(String soundId);

    String ambientSoundId();

    void setAmbientSoundId(String soundId);

    String aggroSoundId();

    void setAggroSoundId(String soundId);

    // Plays on the NPC whenever it lands a close-range attack; empty means silent.
    String combatCloseSoundId();

    void setCombatCloseSoundId(String soundId);

    // Plays on the NPC whenever it fires a long-range shot; empty means silent.
    String combatFarSoundId();

    void setCombatFarSoundId(String soundId);

    // Plays where a close-range blow lands; empty means silent.
    String closeImpactSoundId();

    void setCloseImpactSoundId(String soundId);

    // Plays where a long-range shot lands; empty means silent.
    String farImpactSoundId();

    void setFarImpactSoundId(String soundId);

    int ambientIntervalSeconds();

    void setAmbientIntervalSeconds(int seconds);

    NpcModelSettings modelSettings();

    void setModelSettings(NpcModelSettings settings);

    NpcEquipmentSettings equipment();

    void setEquipment(NpcEquipmentSettings equipment);

    NpcDropSettings dropSettings();

    void setDropSettings(NpcDropSettings dropSettings);

    NpcMovementSettings movement();

    void setMovement(NpcMovementSettings movement);

    NpcBossBarSettings bossBar();

    void setBossBar(NpcBossBarSettings bossBar);

    NpcTraitsSettings traits();

    void setTraits(NpcTraitsSettings traits);

    NpcCombatSettings combat();

    void setCombat(NpcCombatSettings combat);

    NpcSpawnSettings spawn();

    void setSpawn(NpcSpawnSettings spawn);

    NpcJobSettings jobs();

    void setJobs(NpcJobSettings jobs);

    // The factions this NPC belongs to; who it hunts (enemies) and protects (mates) is the union across
    // all of them. An NPC may be in several at once; the list is never empty (falls back to the default
    // faction). See LunaNpcApi.alliances(server) to define factions and their relationships.
    List<String> allianceIds();

    void setAllianceIds(List<String> allianceIds);

    // Single-alliance convenience: the primary (first) home faction, and setting membership to just one.
    default String allianceId() {
        List<String> ids = allianceIds();
        return ids.isEmpty() ? net.lunacore.luna_npc.npc.NpcDefaults.ALLIANCE : ids.get(0);
    }

    default void setAllianceId(String allianceId) {
        setAllianceIds(allianceId == null || allianceId.isBlank() ? List.of() : List.of(allianceId));
    }

    // Add/remove a single faction membership without replacing the whole set.
    default void addAlliance(String allianceId) {
        if (allianceId == null || allianceId.isBlank() || allianceIds().contains(allianceId)) {
            return;
        }
        java.util.List<String> next = new java.util.ArrayList<>(allianceIds());
        next.add(allianceId);
        setAllianceIds(next);
    }

    default void removeAlliance(String allianceId) {
        if (!allianceIds().contains(allianceId)) {
            return;
        }
        java.util.List<String> next = new java.util.ArrayList<>(allianceIds());
        next.remove(allianceId);
        setAllianceIds(next);
    }

    default boolean isInAlliance(String allianceId) {
        return allianceIds().contains(allianceId);
    }

    // ---- Warnings & problems (see NpcWarning) — two separate checks: a warning is not an error ----

    // Soft, allowed-but-risky advisories about this NPC's configuration (spawn loop, negative knockback,
    // ranged-without-homing, gear-overrides-combat). Pure — no console output. Empty when all is well.
    // The Id on each entry is stable, so code can detect a specific one and self-correct with a helper.
    default List<NpcWarning> warnings() {
        return NpcWarnings.of(this);
    }

    // Like warnings() but also prints each to the server console, then returns them — the inspect-and-react
    // call. Prefer warnings() in tight loops to avoid console spam.
    default List<NpcWarning> logWarnings() {
        List<NpcWarning> list = warnings();
        NpcWarnings.log(this, list);
        return list;
    }

    // Hard problems: settings that would be rejected on save (ERROR severity). A separate check from
    // warnings(). Empty means the configuration is structurally valid.
    default List<NpcWarning> problems() {
        return NpcWarnings.problemsOf(this);
    }

    // Like problems() but also prints each to the server console, then returns them.
    default List<NpcWarning> logProblems() {
        List<NpcWarning> list = problems();
        NpcWarnings.log(this, list);
        return list;
    }

    // ---- Scripting convenience verbs: one call over the get -> withX -> set pattern ----

    // Make this NPC natural-spawn in a biome with the given chance (also arms natural spawning).
    default void spawnNaturallyIn(String biomeId, int chance) {
        setSpawn(spawn().withBiomeChance(biomeId, chance));
    }

    // Make this NPC natural-spawn inside a named zone with a chance and max alive count (0 = unlimited).
    default void spawnInZone(String zoneId, int chance, int maxCount) {
        setSpawn(spawn().withZoneSpawn(zoneId, chance, maxCount));
    }

    // Bring this NPC back after death, after a random delay in the given range; atDeath = reappear where
    // it died instead of at its spawn origin.
    default void enableRespawn(int minSeconds, int maxSeconds, boolean atDeath) {
        setSpawn(spawn().withRespawn(minSeconds, maxSeconds, atDeath));
    }

    // Cap how many of this NPC may be alive near players from biome/structure spawns (0 = unlimited).
    // The one-call fix for the SPAWN_UNBOUNDED_POPULATION warning.
    default void setMaxNearby(int max) {
        setSpawn(spawn().withMaxNearby(max));
    }

    // Where this NPC's biome/structure spawns may appear (Anywhere / Surface only / Caves only).
    default void setSurfaceMode(SurfaceMode mode) {
        setSpawn(spawn().withSurfaceMode(mode));
    }

    // Put a melee weapon in the main hand and enable a melee attack for the given damage.
    default void giveMeleeWeapon(String itemId, float damage) {
        setEquipment(equipment().withMainHand(itemId));
        setCombat(combat().withMelee(combat().melee().withEnabled(true).withDamage(damage)));
    }

    // Put a ranged weapon in hand, fire the given projectile item, and enable a ranged attack for the
    // given damage.
    default void giveRangedWeapon(String weaponItemId, String projectileItemId, float damage) {
        setEquipment(equipment().withRangedWeapon(weaponItemId));
        setCombat(combat()
                .withRanged(combat().ranged().withEnabled(true).withDamage(damage))
                .withProjectile(combat().projectile().withItem(projectileItemId)));
    }

    // The close/long range-switch rules, reached through combat(); sugar so callers needn't rebuild the
    // whole combat block to read or replace just the switch.
    default NpcRangeSwitch rangeSwitch() {
        return combat().rangeSwitch();
    }

    default void setRangeSwitch(NpcRangeSwitch rangeSwitch) {
        setCombat(combat().withRangeSwitch(rangeSwitch));
    }

    // The either/or combat system this NPC runs (SIMPLE melee/ranged vs the ADVANCED node graph).
    default NpcCombatMode combatMode() {
        return combat().combatMode();
    }

    default void setCombatMode(NpcCombatMode mode) {
        setCombat(combat().withCombatMode(mode));
    }

    // The advanced-combat behaviour; setting it also flips the NPC into ADVANCED mode.
    default NpcAdvancedCombat advancedCombat() {
        return combat().advanced();
    }

    default void setAdvancedCombat(NpcAdvancedCombat advanced) {
        setCombat(combat().withAdvanced(advanced).withCombatMode(NpcCombatMode.ADVANCED));
    }

    // Give this NPC the trading job with the given weighted sub-type variants (weights must total 100).
    default void giveTradingJob(NpcTradeSubType... subTypes) {
        NpcJobSettings jobs = jobs().withJobType(NpcJobType.TRADING);
        for (NpcTradeSubType subType : subTypes) {
            jobs = jobs.withTradeSubType(subType);
        }
        setJobs(jobs);
    }

    // Give this NPC the quest job with the given weighted sub-type variants (weights must total 100).
    default void giveQuestJob(NpcQuestSubType... subTypes) {
        NpcJobSettings jobs = jobs().withJobType(NpcJobType.QUESTS);
        for (NpcQuestSubType subType : subTypes) {
            jobs = jobs.withQuestSubType(subType);
        }
        setJobs(jobs);
    }

    // Give this NPC the farming job (manual spawns only — natural spawns come up jobless).
    default void giveFarmingJob(NpcFarmSubType subType) {
        setJobs(jobs().withFarmSubType(subType));
    }

    // Give this NPC the patrol job: weighted variants, each walking a saved route (weights must total 100).
    // Manual spawns only — natural spawns come up jobless.
    default void givePatrolJob(NpcPatrolSubType... subTypes) {
        NpcJobSettings jobs = jobs().withJobType(NpcJobType.PATROL);
        for (NpcPatrolSubType subType : subTypes) {
            jobs = jobs.withPatrolSubType(subType);
        }
        setJobs(jobs);
    }

    default void giveBodyguardJob(NpcBodyguardSettings settings) {
        setJobs(jobs().withBodyguard(settings));
    }

    // The Chunk Loader trait: keeps the chunks around each live copy loaded while it lives.
    default void setChunkLoader(boolean chunkLoader) {
        setTraits(traits().withChunkLoader(chunkLoader));
    }

    // Lock the NPC's job menu after a hit: allSeconds for everyone, attackerSeconds only for the
    // player who landed the hit (0 disables the respective block).
    default void setInteractionBlockOnHit(int allSeconds, int attackerSeconds) {
        setJobs(jobs().withBlockOnAnyHit(allSeconds).withBlockAttacker(attackerSeconds));
    }

    // Add an item drop at the first free drop slot with the given chance percent (0.01-100).
    default void addDrop(String itemId, float chancePercent) {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (NpcDropSettings.NpcItemDrop drop : dropSettings().drops()) {
            used.add(drop.slot());
        }
        int slot = 0;
        while (slot < 36 && used.contains(slot)) {
            slot++;
        }
        setDropSettings(dropSettings().withDrop(slot, itemId, chancePercent));
    }

    // ---- Typed overloads: pass registry objects (Items.IRON_SWORD) instead of ids for compile-time
    // safety and IDE completion. The string forms above stay for dynamic mods and scripting. ----

    default void giveMeleeWeapon(net.minecraft.world.item.Item weapon, float damage) {
        giveMeleeWeapon(idOf(weapon), damage);
    }

    default void giveRangedWeapon(net.minecraft.world.item.Item weapon,
                                  net.minecraft.world.item.Item projectile, float damage) {
        giveRangedWeapon(idOf(weapon), idOf(projectile), damage);
    }

    default void addDrop(net.minecraft.world.item.Item item, float chancePercent) {
        addDrop(idOf(item), chancePercent);
    }

    private static String idOf(net.minecraft.world.item.Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
