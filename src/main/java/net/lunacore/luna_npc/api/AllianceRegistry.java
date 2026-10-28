package net.lunacore.luna_npc.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.lunacore.luna_npc.api.NpcAlliance;

// Developer access to the faction system. Everything the in-game Edit Alliances screen can do is
// here too: create/rename/delete alliances, edit their membership (mob types and specific
// players) and their enemy/mate relationships. Built-in alliances cannot be deleted but are
// otherwise fully editable, so a mod can, for example, drop a player into an alliance and have
// every NPC react to it immediately.
public interface AllianceRegistry {

    List<NpcAlliance> all();

    NpcAlliance byId(String allianceId);

    // Creates a new (non-built-in) alliance with a generated id and the given display name.
    NpcAlliance create(String name);

    void rename(String allianceId, String name);

    // Deletes a non-built-in alliance and scrubs it from every relationship list.
    void delete(String allianceId);

    void setMemberMobTypes(String allianceId, List<String> mobTypeIds);

    void setMemberPlayers(String allianceId, List<UUID> playerIds);

    void setEnemies(String allianceId, List<String> enemyAllianceIds);

    void setMates(String allianceId, List<String> mateAllianceIds);

    // Makes every player a member (on top of the listed ones), like the editor's "All players" toggle.
    void setMatchAllPlayers(String allianceId, boolean matchAll);

    // The alliance-wide friendly-fire rule: NpcAlliance.FF_PER_NPC, FF_ALLOW or FF_BLOCK.
    void setFriendlyFire(String allianceId, String rule);

    // Convenience: put a player into exactly one alliance (removing it from the others).
    void assignPlayer(UUID playerId, String allianceId);

    // The alliance a mob type / player currently belongs to (never null for a living target).
    String allianceOfMobType(String mobTypeId);

    String allianceOfPlayer(UUID playerId);

    // ---- Lookup / upsert + incremental relationship edits (safe for several mods to share) ----

    // The alliance with this display name, creating a new one if none exists.
    default NpcAlliance getOrCreate(String name) {
        return all().stream().filter(alliance -> alliance.name().equals(name)).findFirst()
                .orElseGet(() -> create(name));
    }

    // Add/remove a single relationship without replacing the whole list — so one mod's setEnemies
    // does not wipe another mod's entries. No-ops if the alliance is unknown or already in that state.
    default void addEnemy(String allianceId, String enemyId) {
        NpcAlliance alliance = byId(allianceId);
        if (alliance == null || alliance.enemies().contains(enemyId)) {
            return;
        }
        List<String> next = new ArrayList<>(alliance.enemies());
        next.add(enemyId);
        setEnemies(allianceId, next);
    }

    default void removeEnemy(String allianceId, String enemyId) {
        NpcAlliance alliance = byId(allianceId);
        if (alliance == null || !alliance.enemies().contains(enemyId)) {
            return;
        }
        List<String> next = new ArrayList<>(alliance.enemies());
        next.remove(enemyId);
        setEnemies(allianceId, next);
    }

    default void addMate(String allianceId, String mateId) {
        NpcAlliance alliance = byId(allianceId);
        if (alliance == null || alliance.mates().contains(mateId)) {
            return;
        }
        List<String> next = new ArrayList<>(alliance.mates());
        next.add(mateId);
        setMates(allianceId, next);
    }

    default void removeMate(String allianceId, String mateId) {
        NpcAlliance alliance = byId(allianceId);
        if (alliance == null || !alliance.mates().contains(mateId)) {
            return;
        }
        List<String> next = new ArrayList<>(alliance.mates());
        next.remove(mateId);
        setMates(allianceId, next);
    }
}
