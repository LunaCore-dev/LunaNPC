package net.lunacore.luna_npc.job;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import net.lunacore.luna_npc.api.NpcQuest;

// Per-player quest progress. Each accepted quest is keyed on (lunaId, questId): the questId is a
// generated UUID, so editing or reordering a definition's quest list never corrupts progress. dueTick
// is game time (0 = no limit), so a countdown stays accurate through chunk unload, restart and offline
// catch-up. Every mutation marks the store dirty for the crash-safety flush.
public final class QuestProgressManager extends SavedData {

    private static final Codec<UUID> UUID_STRING_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public record ActiveQuest(long lunaId, String questId, List<Integer> progress,
                              long dueTick, long acceptedTick) {

        public ActiveQuest {
            progress = List.copyOf(progress);
        }

        public static final Codec<ActiveQuest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("luna_id").forGetter(ActiveQuest::lunaId),
                Codec.STRING.fieldOf("quest_id").forGetter(ActiveQuest::questId),
                Codec.INT.listOf().optionalFieldOf("progress", List.of()).forGetter(ActiveQuest::progress),
                Codec.LONG.optionalFieldOf("due_tick", 0L).forGetter(ActiveQuest::dueTick),
                Codec.LONG.optionalFieldOf("accepted_tick", 0L).forGetter(ActiveQuest::acceptedTick)
        ).apply(instance, ActiveQuest::new));

        public boolean isExpired(long now) {
            return dueTick > 0 && dueTick <= now;
        }

        public ActiveQuest withProgress(List<Integer> newProgress) {
            return new ActiveQuest(lunaId, questId, newProgress, dueTick, acceptedTick);
        }
    }

    private static final Codec<QuestProgressManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(UUID_STRING_CODEC, ActiveQuest.CODEC.listOf())
                    .optionalFieldOf("active", Map.of()).forGetter(QuestProgressManager::copyOut),
            Codec.unboundedMap(UUID_STRING_CODEC, Codec.STRING.listOf())
                    .optionalFieldOf("completed", Map.of()).forGetter(QuestProgressManager::copyOutCompleted),
            Codec.unboundedMap(Codec.STRING, NpcQuest.CODEC)
                    .optionalFieldOf("assigned", Map.of()).forGetter(QuestProgressManager::copyOutAssigned)
    ).apply(instance, QuestProgressManager::new));

    private static final SavedDataType<QuestProgressManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "quest_progress"),
            QuestProgressManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, List<ActiveQuest>> byPlayer = new HashMap<>();
    private final Map<UUID, Set<String>> completedByPlayer = new HashMap<>();
    // Definitions of quests handed out inline (e.g. by a DIALOG action), keyed by questId, so the book,
    // kill-tracking and completion can resolve a quest that lives in no NPC's quest list.
    private final Map<String, NpcQuest> assignedDefs = new HashMap<>();

    private QuestProgressManager() {
    }

    private QuestProgressManager(Map<UUID, List<ActiveQuest>> stored,
                                 Map<UUID, List<String>> storedCompleted,
                                 Map<String, NpcQuest> storedAssigned) {
        stored.forEach((player, quests) -> byPlayer.put(player, new ArrayList<>(quests)));
        storedCompleted.forEach((player, questIds) -> completedByPlayer.put(player, new HashSet<>(questIds)));
        assignedDefs.putAll(storedAssigned);
    }

    private Map<String, NpcQuest> copyOutAssigned() {
        return Map.copyOf(assignedDefs);
    }

    public void putAssignedDef(NpcQuest quest) {
        if (!quest.questId().isBlank()) {
            assignedDefs.put(quest.questId(), quest);
            setDirty();
        }
    }

    public NpcQuest assignedDef(String questId) {
        return assignedDefs.get(questId);
    }

    private Map<UUID, List<ActiveQuest>> copyOut() {
        Map<UUID, List<ActiveQuest>> copy = new HashMap<>();
        byPlayer.forEach((player, quests) -> {
            if (!quests.isEmpty()) {
                copy.put(player, List.copyOf(quests));
            }
        });
        return copy;
    }

    private Map<UUID, List<String>> copyOutCompleted() {
        Map<UUID, List<String>> copy = new HashMap<>();
        completedByPlayer.forEach((player, questIds) -> {
            if (!questIds.isEmpty()) {
                copy.put(player, List.copyOf(questIds));
            }
        });
        return copy;
    }

    public static QuestProgressManager get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<ActiveQuest> quests(UUID player) {
        List<ActiveQuest> quests = byPlayer.get(player);
        return quests == null ? List.of() : List.copyOf(quests);
    }

    public ActiveQuest active(UUID player, String questId) {
        List<ActiveQuest> quests = byPlayer.get(player);
        if (quests == null) {
            return null;
        }
        for (ActiveQuest quest : quests) {
            if (quest.questId().equals(questId)) {
                return quest;
            }
        }
        return null;
    }

    public void accept(UUID player, ActiveQuest quest) {
        byPlayer.computeIfAbsent(player, ignored -> new ArrayList<>()).add(quest);
        setDirty();
    }

    public void replace(UUID player, ActiveQuest updated) {
        List<ActiveQuest> quests = byPlayer.get(player);
        if (quests == null) {
            return;
        }
        for (int index = 0; index < quests.size(); index++) {
            if (quests.get(index).questId().equals(updated.questId())) {
                quests.set(index, updated);
                setDirty();
                return;
            }
        }
    }

    public void remove(UUID player, String questId) {
        List<ActiveQuest> quests = byPlayer.get(player);
        if (quests != null && quests.removeIf(quest -> quest.questId().equals(questId))) {
            if (quests.isEmpty()) {
                byPlayer.remove(player);
            }
            setDirty();
        }
    }

    public void markCompleted(UUID player, String questId) {
        if (completedByPlayer.computeIfAbsent(player, ignored -> new HashSet<>()).add(questId)) {
            setDirty();
        }
    }

    public boolean hasCompleted(UUID player, String questId) {
        Set<String> completed = completedByPlayer.get(player);
        return completed != null && completed.contains(questId);
    }

    public boolean hasAllCompleted(UUID player, Collection<String> questIds) {
        for (String questId : questIds) {
            if (!hasCompleted(player, questId)) {
                return false;
            }
        }
        return true;
    }

    public List<ActiveQuest> removeExpired(UUID player, long now) {
        List<ActiveQuest> quests = byPlayer.get(player);
        if (quests == null || quests.isEmpty()) {
            return List.of();
        }
        List<ActiveQuest> expired = new ArrayList<>();
        Iterator<ActiveQuest> iterator = quests.iterator();
        while (iterator.hasNext()) {
            ActiveQuest quest = iterator.next();
            if (quest.isExpired(now)) {
                expired.add(quest);
                iterator.remove();
            }
        }
        if (!expired.isEmpty()) {
            if (quests.isEmpty()) {
                byPlayer.remove(player);
            }
            setDirty();
        }
        return expired;
    }
}
