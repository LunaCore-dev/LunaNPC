package net.lunacore.luna_npc.permission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.LunaNpcEvents;
import net.lunacore.luna_npc.api.LunaStick;

// Per-stick grant store: which player UUIDs and which alliance ids may use each gated stick. Empty means
// nobody but admins (default-deny). Persisted as SavedData "permissions".
public final class StickPermissionManager extends SavedData {

    private record Grant(List<UUID> players, List<String> alliances) {
        static final Codec<Grant> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(Grant::players),
                Codec.STRING.listOf().optionalFieldOf("alliances", List.of()).forGetter(Grant::alliances)
        ).apply(instance, Grant::new));
    }

    private static final Codec<StickPermissionManager> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(Codec.unboundedMap(LunaStick.CODEC, Grant.CODEC)
                    .optionalFieldOf("grants", Map.of()).forGetter(StickPermissionManager::snapshot))
            .apply(instance, StickPermissionManager::new));

    private static final SavedDataType<StickPermissionManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "permissions"),
            StickPermissionManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<LunaStick, Set<UUID>> playerGrants = new EnumMap<>(LunaStick.class);
    private final Map<LunaStick, Set<String>> allianceGrants = new EnumMap<>(LunaStick.class);

    private StickPermissionManager() {
    }

    private StickPermissionManager(Map<LunaStick, Grant> stored) {
        stored.forEach((stick, grant) -> {
            playerGrants.put(stick, new LinkedHashSet<>(grant.players()));
            allianceGrants.put(stick, new LinkedHashSet<>(grant.alliances()));
        });
    }

    private Map<LunaStick, Grant> snapshot() {
        Map<LunaStick, Grant> out = new EnumMap<>(LunaStick.class);
        for (LunaStick stick : LunaStick.values()) {
            Set<UUID> players = playerGrants.getOrDefault(stick, Set.of());
            Set<String> alliances = allianceGrants.getOrDefault(stick, Set.of());
            if (!players.isEmpty() || !alliances.isEmpty()) {
                out.put(stick, new Grant(List.copyOf(players), List.copyOf(alliances)));
            }
        }
        return out;
    }

    public static StickPermissionManager get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isPlayerGranted(LunaStick stick, UUID player) {
        Set<UUID> set = playerGrants.get(stick);
        return set != null && set.contains(player);
    }

    public boolean isAllianceGranted(LunaStick stick, String allianceId) {
        Set<String> set = allianceGrants.get(stick);
        return set != null && set.contains(allianceId);
    }

    public Set<UUID> grantedPlayers(LunaStick stick) {
        return Set.copyOf(playerGrants.getOrDefault(stick, Set.of()));
    }

    public Set<String> grantedAlliances(LunaStick stick) {
        return Set.copyOf(allianceGrants.getOrDefault(stick, Set.of()));
    }

    public boolean grantPlayer(LunaStick stick, UUID player) {
        if (!playerGrants.computeIfAbsent(stick, ignored -> new LinkedHashSet<>()).add(player)) {
            return false;
        }
        changed(stick);
        return true;
    }

    public boolean revokePlayer(LunaStick stick, UUID player) {
        Set<UUID> set = playerGrants.get(stick);
        if (set == null || !set.remove(player)) {
            return false;
        }
        changed(stick);
        return true;
    }

    public boolean grantAlliance(LunaStick stick, String allianceId) {
        if (!allianceGrants.computeIfAbsent(stick, ignored -> new LinkedHashSet<>()).add(allianceId)) {
            return false;
        }
        changed(stick);
        return true;
    }

    public boolean revokeAlliance(LunaStick stick, String allianceId) {
        Set<String> set = allianceGrants.get(stick);
        if (set == null || !set.remove(allianceId)) {
            return false;
        }
        changed(stick);
        return true;
    }

    private void changed(LunaStick stick) {
        setDirty();
        LunaNpcEvents.firePermissionChanged(stick);
    }
}
