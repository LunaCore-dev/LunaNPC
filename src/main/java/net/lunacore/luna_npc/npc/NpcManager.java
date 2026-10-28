package net.lunacore.luna_npc.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcEditAccess;
import net.lunacore.luna_npc.api.NpcRegistry;

public final class NpcManager extends SavedData implements NpcRegistry {

    private static final Codec<UUID> UUID_STRING_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<NpcManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("next_luna_id").forGetter(manager -> manager.nextLunaId),
            NpcEntry.CODEC.listOf().fieldOf("npcs").forGetter(manager -> List.copyOf(manager.npcsByLunaId.values())),
            Codec.unboundedMap(UUID_STRING_CODEC, Codec.LONG.listOf())
                    .optionalFieldOf("favorites", Map.of()).forGetter(NpcManager::copyFavorites)
    ).apply(instance, NpcManager::new));

    // Vanilla crashes on a null DataFixTypes when reading saved data from disk, so we must borrow a
    // vanilla type; its data fixers only touch their own schema keys and never our custom ones.
    private static final SavedDataType<NpcManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "npcs"),
            NpcManager::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<Long, NpcEntry> npcsByLunaId = new LinkedHashMap<>();
    private final Map<UUID, Set<Long>> favoritesByPlayer = new HashMap<>();
    private long nextLunaId;

    private NpcManager() {
        this.nextLunaId = 1;
    }

    private NpcManager(long nextLunaId, List<NpcEntry> npcs, Map<UUID, List<Long>> favorites) {
        this.nextLunaId = nextLunaId;
        for (NpcEntry npc : npcs) {
            npc.attachTo(this);
            this.npcsByLunaId.put(npc.lunaId(), npc);
        }
        favorites.forEach((playerId, lunaIds) -> favoritesByPlayer.put(playerId, new HashSet<>(lunaIds)));
    }

    private Map<UUID, List<Long>> copyFavorites() {
        Map<UUID, List<Long>> copy = new HashMap<>();
        favoritesByPlayer.forEach((playerId, lunaIds) -> copy.put(playerId, List.copyOf(lunaIds)));
        return copy;
    }

    public static NpcManager get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    @Override
    public NpcDefinition create(NpcEditAccess access) {
        return create(NpcValidation.generatePlaceholderName(), access);
    }

    @Override
    public NpcDefinition create(String name, NpcEditAccess access) {
        if (!NpcValidation.isValidName(name)) {
            throw new IllegalArgumentException("Invalid NPC name: " + name);
        }
        if (access == null) {
            throw new IllegalArgumentException("NPC edit access is required");
        }
        NpcEntry npc = new NpcEntry(nextLunaId++, name);
        npc.initAccess(access);
        npc.attachTo(this);
        npcsByLunaId.put(npc.lunaId(), npc);
        setDirty();
        return npc;
    }

    @Override
    public Optional<NpcDefinition> byLunaId(long lunaId) {
        return Optional.ofNullable(npcsByLunaId.get(lunaId));
    }

    @Override
    public Collection<NpcDefinition> all() {
        return List.copyOf(npcsByLunaId.values());
    }

    @Override
    public boolean remove(long lunaId) {
        NpcEntry npc = npcsByLunaId.get(lunaId);
        if (npc == null) {
            return false;
        }
        npc.checkEditable();
        npcsByLunaId.remove(lunaId);
        favoritesByPlayer.values().forEach(favorites -> favorites.remove(lunaId));
        setDirty();
        return true;
    }

    // Runs an in-game editor change: allowed on GUI_ONLY and SHARED NPCs, refused on API_ONLY.
    public static boolean editInGame(NpcDefinition npc, Runnable edit) {
        if (!(npc instanceof NpcEntry entry) || !entry.editAccess().guiCanEdit()) {
            return false;
        }
        entry.inGameEdit = true;
        try {
            edit.run();
        } finally {
            entry.inGameEdit = false;
        }
        return true;
    }

    @Override
    public Set<String> categories() {
        Set<String> categories = new TreeSet<>();
        for (NpcEntry npc : npcsByLunaId.values()) {
            if (!npc.category().isEmpty()) {
                categories.add(npc.category());
            }
        }
        return Collections.unmodifiableSet(categories);
    }

    boolean canUseCategory(String category) {
        Set<String> existing = categories();
        return existing.contains(category) || existing.size() < NpcValidation.MAX_CATEGORIES;
    }

    @Override
    public boolean isFavorite(UUID playerId, long lunaId) {
        Set<Long> favorites = favoritesByPlayer.get(playerId);
        return favorites != null && favorites.contains(lunaId);
    }

    @Override
    public void setFavorite(UUID playerId, long lunaId, boolean favorite) {
        if (favorite) {
            favoritesByPlayer.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(lunaId);
        } else {
            Set<Long> favorites = favoritesByPlayer.get(playerId);
            if (favorites == null || !favorites.remove(lunaId)) {
                return;
            }
            if (favorites.isEmpty()) {
                favoritesByPlayer.remove(playerId);
            }
        }
        setDirty();
    }

    @Override
    public Set<Long> favoritesOf(UUID playerId) {
        Set<Long> favorites = favoritesByPlayer.get(playerId);
        return favorites == null ? Set.of() : Collections.unmodifiableSet(favorites);
    }
}
