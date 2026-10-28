package net.lunacore.luna_npc.api;

import java.util.ArrayList;
import java.util.List;
import net.lunacore.luna_npc.api.NpcSpawnSettings;
import net.lunacore.luna_npc.api.NpcZone;
import net.lunacore.luna_npc.api.ZoneShape;
import net.lunacore.luna_npc.npc.NpcValidation;

// The single source of truth for configuration advisories and problems. Each warning's trigger lives
// exactly once, in a condition primitive below; both the editor GUI (which feeds its live edit fields)
// and the developer API (of(NpcDefinition), which feeds the stored settings) call the SAME primitive, so the two
// can never drift. The GUI maps each Id to its localized text; the API and console use the plain-English
// message here. All checks are pure — no world/registry context.
public final class NpcWarnings {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LunaNPC");

    private NpcWarnings() {
    }

    // ---- Condition primitives: the one and only definition of each warning trigger ----

    // Natural spawning + respawn: population can climb without limit as players explore.
    public static boolean spawnLoop(NpcSpawnSettings spawn) {
        return spawn.naturalSpawnEnabled() && spawn.respawnEnabled();
    }

    // Natural spawning on with wild spawns kept persistent: they never despawn, so a natural spawn
    // that would normally thin out as players move on instead accumulates without bound.
    public static boolean spawnPersistentPileup(NpcSpawnSettings spawn) {
        return spawn.naturalSpawnEnabled() && spawn.naturalPersistent();
    }

    // Knockback set negative: hits pull the target toward the NPC instead of away.
    public static boolean negativeKnockback(float knockback) {
        return knockback < 0.0F;
    }

    // A ranged attack with homing off: fast or dodging targets may be missed.
    public static boolean rangedWithoutHoming(boolean rangedEnabled, boolean homing) {
        return rangedEnabled && !homing;
    }

    // Real gear stats on: equipped gear can override or amplify the configured combat numbers.
    public static boolean gearOverridesCombat(boolean gearAffectsStats) {
        return gearAffectsStats;
    }

    // Range switch on but a range it can pick has no attack behind it (a rule or the default selects
    // long while ranged is off, or close while melee is off): the NPC silently falls back to whatever is
    // armed, so the switch can't do what it says.
    public static boolean rangeSwitchMissingAttack(NpcCombatSettings combat) {
        NpcRangeSwitch rangeSwitch = combat.rangeSwitch();
        if (!rangeSwitch.enabled()) {
            return false;
        }
        if (selectsRange(rangeSwitch, NpcAttackRange.LONG) && !combat.ranged().enabled()) {
            return true;
        }
        return selectsRange(rangeSwitch, NpcAttackRange.CLOSE) && !combat.melee().enabled();
    }

    private static boolean selectsRange(NpcRangeSwitch rangeSwitch, NpcAttackRange range) {
        for (NpcRangeRule rule : rangeSwitch.rules()) {
            if (rule.result() == range) {
                return true;
            }
        }
        return false;
    }

    // Ranged fire rate faster than a bow/crossbow draw: the NPC can't finish the pull between shots, so
    // it holds the fully-drawn pose and fires without re-drawing. drawnWeapon is resolved by the caller
    // (it needs the item registry), keeping this trigger pure.
    public static boolean rangedDrawHeld(boolean rangedEnabled, boolean drawnWeapon, float intervalSeconds) {
        return rangedEnabled && drawnWeapon
                && intervalSeconds * 20.0F < net.lunacore.luna_npc.npc.NpcValidation.RANGED_DRAW_TICKS;
    }

    // Advanced combat on with no rules: the NPC has no behaviour and just stands there.
    public static boolean advancedNoRules(NpcCombatSettings combat) {
        return combat.combatMode() == NpcCombatMode.ADVANCED && combat.advanced().rules().isEmpty();
    }

    // An advanced-combat rule's USE_ATTACK names an attack the library doesn't hold — it does nothing.
    public static boolean advancedMissingAttack(NpcCombatSettings combat) {
        if (combat.combatMode() != NpcCombatMode.ADVANCED) {
            return false;
        }
        NpcAdvancedCombat advanced = combat.advanced();
        for (NpcCombatRule rule : advanced.rules()) {
            for (NpcCombatAction action : rule.actions()) {
                if (action.type() == NpcCombatActionType.USE_ATTACK
                        && advanced.attackByName(action.stringArg()) == null) {
                    return true;
                }
            }
        }
        return false;
    }

    // A flat shape (min Y == max Y) with Ignore height off: nothing can spawn in a one-block plane.
    public static boolean zoneFlatNoSpawns(ZoneShape shape, boolean ignoreHeight) {
        return shape.minY() == shape.maxY() && !ignoreHeight;
    }

    // A farming job with natural spawning on: farming is manual-spawn only, so wild spawns of this
    // NPC appear without their job.
    public static boolean farmingOnNaturalSpawn(NpcJobSettings jobs, NpcSpawnSettings spawn) {
        return jobs.jobType() == NpcJobType.FARMING && spawn.naturalSpawnEnabled();
    }

    // A patrol job with natural spawning on: patrol is manual-spawn only, like farming, so wild spawns of
    // this NPC appear without their job instead of all walking off to the same route.
    public static boolean patrolOnNaturalSpawn(NpcJobSettings jobs, NpcSpawnSettings spawn) {
        return jobs.jobType() == NpcJobType.PATROL && spawn.naturalSpawnEnabled();
    }

    // A patrol job with no route to walk: no sub-types, or one that names no route.
    public static boolean patrolWithoutRoute(NpcJobSettings jobs) {
        return jobs.jobType() == NpcJobType.PATROL && (jobs.patrolSubTypes().isEmpty()
                || jobs.patrolSubTypes().stream().anyMatch(subType -> subType.routeId().isBlank()));
    }

    // A bodyguard with neither attack on: it follows, but can never defend anyone.
    public static boolean bodyguardWithoutAttack(NpcJobSettings jobs, boolean meleeEnabled, boolean rangedEnabled) {
        return jobs.jobType() == NpcJobType.BODYGUARD && !meleeEnabled && !rangedEnabled;
    }

    // A bodyguard that picks from its protect targets but has none.
    public static boolean bodyguardProtectsNobody(NpcJobSettings jobs) {
        return jobs.jobType() == NpcJobType.BODYGUARD && jobs.bodyguard().protectsNobody();
    }

    // A bodyguard without teleporting whose range is no wider than its stay or walk distance: it gives
    // its protectee up as soon as they step away, before it ever starts to catch up.
    public static boolean bodyguardRangeTooShort(NpcJobSettings jobs) {
        NpcBodyguardSettings guard = jobs.bodyguard();
        return jobs.jobType() == NpcJobType.BODYGUARD && guard.teleportDistance() == 0
                && guard.range() <= Math.max(guard.stayRadius(), guard.walkWithin());
    }

    // A teleport distance it is already within while just standing guard (its stay radius, or the 3
    // blocks it lands within): it keeps teleporting back and forth.
    public static boolean bodyguardTeleportTooClose(NpcJobSettings jobs) {
        NpcBodyguardSettings guard = jobs.bodyguard();
        return jobs.jobType() == NpcJobType.BODYGUARD && guard.teleportDistance() > 0
                && guard.teleportDistance() <= Math.max(guard.stayRadius(), NpcValidation.BODYGUARD_TELEPORT_LANDING_RADIUS);
    }

    // A retreat distance no wider than the stay radius: it drops every fight the moment it starts.
    public static boolean bodyguardRetreatTooClose(NpcJobSettings jobs) {
        NpcBodyguardSettings guard = jobs.bodyguard();
        return jobs.jobType() == NpcJobType.BODYGUARD && guard.retreat()
                && guard.retreatDistance() <= guard.stayRadius();
    }

    // A chunk loader that spawns naturally: every wild copy keeps its own chunks loaded, so the loaded
    // area (and the server load) grows with the population.
    public static boolean chunkLoaderOnNaturalSpawn(boolean chunkLoader, NpcSpawnSettings spawn) {
        return chunkLoader && spawn.naturalSpawnEnabled();
    }

    // An area rule with natural spawning off: hand-placed and API-spawned NPCs are never area-bound.
    public static boolean areaTetherWithoutNaturalSpawn(NpcSpawnSettings spawn) {
        return (!spawn.leaveArea() || !spawn.leaveAreaInCombat()) && !spawn.naturalSpawnEnabled();
    }

    // A naturally spawned NPC kept inside its area in combat ignores attackers standing outside it.
    public static boolean areaCombatTether(NpcSpawnSettings spawn) {
        return !spawn.leaveAreaInCombat() && spawn.naturalSpawnEnabled();
    }

    // ---- Aggregate checks (API) ----

    // Soft, allowed-but-risky advisories for an NPC (never blocks anything — the creator stays in control).
    public static List<NpcWarning> of(NpcDefinition npc) {
        List<NpcWarning> out = new ArrayList<>();
        if (spawnLoop(npc.spawn())) {
            out.add(new NpcWarning(NpcWarning.Id.SPAWN_UNBOUNDED_POPULATION, NpcWarning.Severity.WARNING,
                    "Natural spawning and respawn are both on — the population can climb without limit as "
                            + "players explore. Cap it with Max nearby or a zone max count."));
        }
        if (spawnPersistentPileup(npc.spawn())) {
            out.add(new NpcWarning(NpcWarning.Id.SPAWN_PERSISTENT_PILEUP, NpcWarning.Severity.WARNING,
                    "Natural spawns are set to persist — they never despawn, so they pile up as players "
                            + "explore. Turn persistence off for wild spawns, or cap it with Max nearby."));
        }
        if (negativeKnockback(npc.movement().knockback())) {
            out.add(new NpcWarning(NpcWarning.Id.NEGATIVE_KNOCKBACK, NpcWarning.Severity.WARNING,
                    "Knockback is negative — hits pull the target toward the NPC instead of away."));
        }
        if (rangedWithoutHoming(npc.combat().ranged().enabled(), npc.combat().projectile().homing())) {
            out.add(new NpcWarning(NpcWarning.Id.RANGED_WITHOUT_HOMING, NpcWarning.Severity.WARNING,
                    "Ranged attack has homing off — fast or dodging targets may be missed."));
        }
        if (gearOverridesCombat(npc.equipment().gearAffectsStats())) {
            out.add(new NpcWarning(NpcWarning.Id.GEAR_OVERRIDES_COMBAT, NpcWarning.Severity.NOTE,
                    "Real gear stats are on — equipped gear can override or amplify the configured combat values."));
        }
        if (rangeSwitchMissingAttack(npc.combat())) {
            out.add(new NpcWarning(NpcWarning.Id.RANGE_SWITCH_MISSING_ATTACK, NpcWarning.Severity.WARNING,
                    "Range switching can pick a range whose attack is off — enable both the melee and ranged "
                            + "attacks it switches between, or it silently falls back to whichever is armed."));
        }
        if (rangedDrawHeld(npc.combat().ranged().enabled(),
                net.lunacore.luna_npc.npc.NpcEquipment.isDrawnWeapon(npc.equipment().rangedWeaponId()),
                npc.combat().ranged().intervalSeconds())) {
            out.add(new NpcWarning(NpcWarning.Id.RANGED_DRAW_HELD, NpcWarning.Severity.NOTE,
                    "Ranged fire rate is faster than the bow/crossbow draw — the NPC holds the fully-drawn "
                            + "pose and fires without re-drawing between shots."));
        }
        if (advancedNoRules(npc.combat())) {
            out.add(new NpcWarning(NpcWarning.Id.ADVANCED_NO_RULES, NpcWarning.Severity.WARNING,
                    "Advanced combat is on but has no rules — this NPC will just stand there. Add rules, or "
                            + "switch back to Simple combat."));
        }
        if (advancedMissingAttack(npc.combat())) {
            out.add(new NpcWarning(NpcWarning.Id.ADVANCED_MISSING_ATTACK, NpcWarning.Severity.WARNING,
                    "An advanced-combat rule uses an attack that isn't in the attack library — that action "
                            + "will do nothing. Add the attack or fix the name."));
        }
        if (areaTetherWithoutNaturalSpawn(npc.spawn())) {
            out.add(new NpcWarning(NpcWarning.Id.AREA_TETHER_WITHOUT_NATURAL_SPAWN, NpcWarning.Severity.WARNING,
                    "Staying in its spawn area is set, but natural spawning is off — only naturally spawned "
                            + "NPCs are tied to an area, so placed or API-spawned ones ignore it."));
        }
        if (areaCombatTether(npc.spawn())) {
            out.add(new NpcWarning(NpcWarning.Id.AREA_COMBAT_TETHER, NpcWarning.Severity.WARNING,
                    "Leaving the area in combat is off — natural spawns of this NPC won't fight anyone outside "
                            + "their spawn area, not even someone attacking them."));
        }
        if (farmingOnNaturalSpawn(npc.jobs(), npc.spawn())) {
            out.add(new NpcWarning(NpcWarning.Id.JOB_FARMING_NATURAL_SPAWN, NpcWarning.Severity.WARNING,
                    "Farming job with natural spawning on — farming is manual-spawn only, so wild "
                            + "spawns of this NPC appear without their job."));
        }
        if (patrolOnNaturalSpawn(npc.jobs(), npc.spawn())) {
            out.add(new NpcWarning(NpcWarning.Id.JOB_PATROL_NATURAL_SPAWN, NpcWarning.Severity.WARNING,
                    "Patrol job with natural spawning on — patrol is manual-spawn only, so wild spawns of "
                            + "this NPC appear without their job."));
        }
        if (patrolWithoutRoute(npc.jobs())) {
            out.add(new NpcWarning(NpcWarning.Id.JOB_PATROL_NO_ROUTE, NpcWarning.Severity.WARNING,
                    "Patrol job without a route — add a patrol variant and pick a route for it, or the NPC "
                            + "just stands still."));
        }
        if (bodyguardWithoutAttack(npc.jobs(), npc.combat().melee().enabled(),
                npc.combat().ranged().enabled())) {
            out.add(new NpcWarning(NpcWarning.Id.JOB_BODYGUARD_NO_ATTACK, NpcWarning.Severity.WARNING,
                    "Bodyguard job with melee and ranged attacks both off — it follows, but can't defend "
                            + "anyone. Turn on an attack in the Combat tab."));
        }
        if (bodyguardProtectsNobody(npc.jobs())) {
            out.add(new NpcWarning(NpcWarning.Id.JOB_BODYGUARD_PROTECTS_NOBODY, NpcWarning.Severity.WARNING,
                    "Bodyguard protects nobody — pick players, alliances or NPCs for it to protect, or "
                            + "switch it to \"Whoever hires it\"."));
        }
        if (bodyguardRangeTooShort(npc.jobs())) {
            out.add(new NpcWarning(NpcWarning.Id.JOB_BODYGUARD_RANGE_TOO_SHORT, NpcWarning.Severity.WARNING,
                    "Bodyguard range is no wider than its stay or walk distance — without teleporting it gives "
                            + "up as soon as its protectee steps away."));
        }
        if (bodyguardTeleportTooClose(npc.jobs())) {
            out.add(new NpcWarning(NpcWarning.Id.JOB_BODYGUARD_TELEPORT_TOO_CLOSE, NpcWarning.Severity.WARNING,
                    "Bodyguard teleport distance is within its stay radius or the 3 blocks it lands within — "
                            + "it keeps teleporting while just standing guard."));
        }
        if (bodyguardRetreatTooClose(npc.jobs())) {
            out.add(new NpcWarning(NpcWarning.Id.JOB_BODYGUARD_RETREAT_TOO_CLOSE, NpcWarning.Severity.WARNING,
                    "Bodyguard retreat distance is no wider than its stay radius — it drops every fight right "
                            + "away."));
        }
        if (chunkLoaderOnNaturalSpawn(npc.traits().chunkLoader(), npc.spawn())) {
            out.add(new NpcWarning(NpcWarning.Id.CHUNK_LOADER_NATURAL_SPAWN, NpcWarning.Severity.WARNING,
                    "Chunk loader with natural spawning on — every wild copy keeps its chunks loaded, so the "
                            + "loaded area and server load grow with the population."));
        }
        return out;
    }

    // Soft advisories for a zone.
    public static List<NpcWarning> of(NpcZone zone) {
        List<NpcWarning> out = new ArrayList<>();
        if (zoneFlatNoSpawns(zone.shape(), zone.ignoreHeight())) {
            out.add(new NpcWarning(NpcWarning.Id.ZONE_FLAT_NO_SPAWNS, NpcWarning.Severity.WARNING,
                    "Flat zone (min Y == max Y) with Ignore height off — nothing can spawn in a one-block plane."));
        }
        return out;
    }

    // Hard problems: settings blocks that fail validation and would be rejected on save. A SEPARATE check
    // from warnings() on purpose — a warning is not an error.
    public static List<NpcWarning> problemsOf(NpcDefinition npc) {
        List<NpcWarning> out = new ArrayList<>();
        invalidBlock(out, "Model", npc.modelSettings().isValid());
        invalidBlock(out, "Drops", npc.dropSettings().isValid());
        invalidBlock(out, "Movement", npc.movement().isValid());
        invalidBlock(out, "Traits", npc.traits().isValid());
        invalidBlock(out, "Combat", npc.combat().isValid());
        invalidBlock(out, "Spawn", npc.spawn().isValid());
        invalidBlock(out, "Jobs", npc.jobs().isValid());
        invalidBlock(out, "Boss bar", npc.bossBar().isValid());
        return out;
    }

    private static void invalidBlock(List<NpcWarning> out, String block, boolean valid) {
        if (!valid) {
            out.add(new NpcWarning(NpcWarning.Id.SETTINGS_INVALID, NpcWarning.Severity.ERROR,
                    block + " settings are invalid and would be rejected — check its values are in range."));
        }
    }

    // Logs each entry to the LunaNPC console (WARN for warnings, ERROR for problems), tagged with the NPC.
    // Used by the print-and-return API calls; call the pure of()/problemsOf() when you don't want output.
    public static void log(NpcDefinition npc, List<NpcWarning> entries) {
        for (NpcWarning entry : entries) {
            if (entry.severity() == NpcWarning.Severity.ERROR) {
                LOGGER.error("NPC '{}' (lunaId {}) [{}] {}", npc.name(), npc.lunaId(), entry.id(), entry.message());
            } else {
                LOGGER.warn("NPC '{}' (lunaId {}) [{}] {}", npc.name(), npc.lunaId(), entry.id(), entry.message());
            }
        }
    }
}
