package net.lunacore.luna_npc.api;

// A structured advisory or problem about a configuration. The stable Id lets code detect and react to
// a specific one (the lego idea: the Id that flags a problem points at the helper that fixes it), so a
// script can do `if (warnings contains SPAWN_UNBOUNDED_POPULATION) npc.setMaxNearby(...)`. Soft
// warnings (allowed-but-risky, from NpcDefinition.warnings()) and hard problems (config that would be rejected,
// from NpcDefinition.problems()) share this shape but are returned by separate checks — a warning is not an error.
public record NpcWarning(Id id, Severity severity, String message) {

    public enum Severity {
        // Purely informational (e.g. "gear now drives combat values").
        NOTE,
        // Allowed, but risky or surprising — the creator/coder should know.
        WARNING,
        // A hard problem: this configuration is invalid and would be rejected.
        ERROR
    }

    public enum Id {
        // ---- Soft NPC warnings (NpcDefinition.warnings()) ----
        // Natural spawning + respawn both on: population can climb without limit as players explore.
        SPAWN_UNBOUNDED_POPULATION,
        // Natural spawning on with wild spawns kept persistent: they never despawn, so they pile up.
        SPAWN_PERSISTENT_PILEUP,
        // Knockback set negative: hits pull the target toward the NPC instead of away.
        NEGATIVE_KNOCKBACK,
        // Ranged attack with homing off: fast/dodging targets may be missed.
        RANGED_WITHOUT_HOMING,
        // Real gear stats on: equipped gear can override or amplify the configured combat numbers.
        GEAR_OVERRIDES_COMBAT,
        // Farming job + natural spawning: farming is manual-spawn only, wild spawns come up jobless.
        JOB_FARMING_NATURAL_SPAWN,
        // Range switching can select a close/long range whose attack is disabled: it silently falls back.
        RANGE_SWITCH_MISSING_ATTACK,
        // Ranged fire rate is faster than the bow/crossbow draw, so the NPC holds the drawn pose.
        RANGED_DRAW_HELD,
        // Advanced combat is on but has no rules, so the NPC does nothing in a fight.
        ADVANCED_NO_RULES,
        // An advanced-combat rule uses an attack name that isn't in the attack library.
        ADVANCED_MISSING_ATTACK,
        // An area tether (leave area / leave area in combat off) without natural spawning: only natural
        // spawns are ever area-bound, so hand-placed and API-spawned NPCs ignore it.
        AREA_TETHER_WITHOUT_NATURAL_SPAWN,
        // A natural spawn that may not leave its area in combat: it never fights anyone standing outside
        // that area, not even someone attacking it.
        AREA_COMBAT_TETHER,
        // Patrol job + natural spawning: patrol is manual-spawn only, wild spawns come up jobless.
        JOB_PATROL_NATURAL_SPAWN,
        // Patrol job without a route to walk.
        JOB_PATROL_NO_ROUTE,
        // Bodyguard job with melee and ranged both off: it can follow but never defend.
        JOB_BODYGUARD_NO_ATTACK,
        // Bodyguard that picks from its protect targets but has none.
        JOB_BODYGUARD_PROTECTS_NOBODY,
        // Bodyguard without teleporting whose range is within its stay/walk distance: gives up at once.
        JOB_BODYGUARD_RANGE_TOO_SHORT,
        // Bodyguard teleport distance within its stay radius or landing radius: teleports nonstop.
        JOB_BODYGUARD_TELEPORT_TOO_CLOSE,
        // Bodyguard retreat distance within its stay radius: drops every fight at once.
        JOB_BODYGUARD_RETREAT_TOO_CLOSE,
        // Chunk loader on a natural spawn: every wild copy loads its own chunks.
        CHUNK_LOADER_NATURAL_SPAWN,

        // ---- Soft zone warning (NpcZone.warnings()) ----
        // A flat shape (min Y == max Y) with Ignore height off: nothing can spawn in a 1-block plane.
        ZONE_FLAT_NO_SPAWNS,

        // ---- Hard problems (NpcDefinition.problems()) ----
        // One of the NPC's settings blocks fails validation and would be rejected on save.
        SETTINGS_INVALID
    }
}
