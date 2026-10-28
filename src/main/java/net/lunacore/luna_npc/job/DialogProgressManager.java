package net.lunacore.luna_npc.job;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.lunacore.luna_npc.LunaNpc;

// Per-player dialog progress, keyed on (player, NPC lunaId): the node the player is currently at plus
// the story flags they've earned with that NPC. The NPC's two death toggles are snapshotted into the
// state when the conversation starts, so onNpcDeath/onPlayerDeath decide what to wipe without needing
// the live NPC or its definition. Every mutation marks the store dirty for the crash-safety flush.
public final class DialogProgressManager extends SavedData {

    private static final Codec<UUID> UUID_STRING_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<Long> LONG_STRING_CODEC = Codec.STRING.xmap(Long::parseLong, String::valueOf);

    public record PlayerDialogState(String currentNodeId, Set<String> flags,
                                    boolean survivesNpcDeath, boolean survivesPlayerDeath) {

        public PlayerDialogState {
            flags = Set.copyOf(flags);
        }

        public static final Codec<PlayerDialogState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("node", "").forGetter(PlayerDialogState::currentNodeId),
                Codec.STRING.listOf().optionalFieldOf("flags", List.of())
                        .forGetter(state -> List.copyOf(state.flags())),
                Codec.BOOL.optionalFieldOf("survive_npc_death", false)
                        .forGetter(PlayerDialogState::survivesNpcDeath),
                Codec.BOOL.optionalFieldOf("survive_player_death", true)
                        .forGetter(PlayerDialogState::survivesPlayerDeath)
        ).apply(instance, (node, flags, npcDeath, playerDeath) ->
                new PlayerDialogState(node, new HashSet<>(flags), npcDeath, playerDeath)));

        public PlayerDialogState withNode(String node) {
            return new PlayerDialogState(node, flags, survivesNpcDeath, survivesPlayerDeath);
        }

        public PlayerDialogState withFlag(String flag, boolean set) {
            Set<String> next = new HashSet<>(flags);
            if (set) {
                next.add(flag);
            } else {
                next.remove(flag);
            }
            return new PlayerDialogState(currentNodeId, next, survivesNpcDeath, survivesPlayerDeath);
        }
    }

    private static final Codec<Map<Long, PlayerDialogState>> BY_NPC =
            Codec.unboundedMap(LONG_STRING_CODEC, PlayerDialogState.CODEC);

    private static final Codec<DialogProgressManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(UUID_STRING_CODEC, BY_NPC)
                    .optionalFieldOf("progress", Map.of()).forGetter(DialogProgressManager::copyOut)
    ).apply(instance, DialogProgressManager::new));

    private static final SavedDataType<DialogProgressManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "dialog_progress"),
            DialogProgressManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Map<Long, PlayerDialogState>> byPlayer = new HashMap<>();

    private DialogProgressManager() {
    }

    private DialogProgressManager(Map<UUID, Map<Long, PlayerDialogState>> stored) {
        stored.forEach((player, states) -> byPlayer.put(player, new HashMap<>(states)));
    }

    private Map<UUID, Map<Long, PlayerDialogState>> copyOut() {
        Map<UUID, Map<Long, PlayerDialogState>> copy = new HashMap<>();
        byPlayer.forEach((player, states) -> {
            if (!states.isEmpty()) {
                copy.put(player, Map.copyOf(states));
            }
        });
        return copy;
    }

    public static DialogProgressManager get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public PlayerDialogState state(UUID player, long lunaId) {
        Map<Long, PlayerDialogState> states = byPlayer.get(player);
        return states == null ? null : states.get(lunaId);
    }

    public void put(UUID player, long lunaId, PlayerDialogState state) {
        byPlayer.computeIfAbsent(player, ignored -> new HashMap<>()).put(lunaId, state);
        setDirty();
    }

    public void reset(UUID player, long lunaId) {
        Map<Long, PlayerDialogState> states = byPlayer.get(player);
        if (states != null && states.remove(lunaId) != null) {
            if (states.isEmpty()) {
                byPlayer.remove(player);
            }
            setDirty();
        }
    }

    public void onNpcDeath(long lunaId) {
        boolean changed = false;
        var iterator = byPlayer.values().iterator();
        while (iterator.hasNext()) {
            Map<Long, PlayerDialogState> states = iterator.next();
            PlayerDialogState state = states.get(lunaId);
            if (state != null && !state.survivesNpcDeath()) {
                states.remove(lunaId);
                changed = true;
            }
            if (states.isEmpty()) {
                iterator.remove();
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public void onPlayerDeath(UUID player) {
        Map<Long, PlayerDialogState> states = byPlayer.get(player);
        if (states == null) {
            return;
        }
        if (states.values().removeIf(state -> !state.survivesPlayerDeath())) {
            if (states.isEmpty()) {
                byPlayer.remove(player);
            }
            setDirty();
        }
    }
}
