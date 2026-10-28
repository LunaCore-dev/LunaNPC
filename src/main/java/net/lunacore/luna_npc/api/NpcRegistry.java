package net.lunacore.luna_npc.api;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface NpcRegistry {

    // Who may edit the new NPC is required: see NpcEditAccess. API NPCs are usually API_ONLY.
    NpcDefinition create(NpcEditAccess access);

    NpcDefinition create(String name, NpcEditAccess access);

    Optional<NpcDefinition> byLunaId(long lunaId);

    // Every NPC definition. The element type is NpcDefinition, so callers never need to cast.
    Collection<NpcDefinition> all();

    Set<String> categories();

    boolean isFavorite(UUID playerId, long lunaId);

    void setFavorite(UUID playerId, long lunaId, boolean favorite);

    Set<Long> favoritesOf(UUID playerId);

    // Removes the definition with this lunaId; true if one was removed. Entities already spawned from
    // it keep their current behaviour until they despawn. Throws IllegalStateException on a GUI_ONLY NPC.
    boolean remove(long lunaId);

    // ---- Lookup / upsert convenience over the calls above ----

    // The first definition with this exact name, if any. A SHARED or GUI_ONLY NPC can be renamed in-game,
    // so look those up by the LID saved at creation instead; API_ONLY names cannot change.
    default Optional<NpcDefinition> findByName(String name) {
        return all().stream().filter(npc -> npc.name().equals(name)).findFirst();
    }

    default boolean exists(String name) {
        return findByName(name).isPresent();
    }

    // The definition with this name, creating it with this access if none exists — the idempotent
    // "define once" call. An existing NPC keeps its access.
    default NpcDefinition getOrCreate(String name, NpcEditAccess access) {
        return findByName(name).orElseGet(() -> create(name, access));
    }

    // Removes the first definition with this name; true if one was removed (same lock as remove(lunaId)).
    default boolean remove(String name) {
        return findByName(name).map(npc -> remove(npc.lunaId())).orElse(false);
    }
}
