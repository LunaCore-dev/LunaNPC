package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// One quest an NPC can offer: bundled objectives, an optional time limit (0 = none, in seconds), a
// completion mode, and an XP roll in [xpMin, xpMax] plus up to 27 item reward stacks. questId is a
// generated UUID string — per-player progress keys on it, so reordering or editing the quest list
// never corrupts progress. Build quests through create(...) so the id can't be forgotten. title and
// description are optional display text (name stays the short editor label); prerequisiteQuestIds
// gate the offer on quests the player already completed; cancelOnNpcDeath drops the active quest
// when the offering NPC dies.
public record NpcQuest(String questId, String name, String title, String description,
                       List<NpcQuestObjective> objectives,
                       NpcQuestCompletion completion, int timeLimitSeconds,
                       int xpMin, int xpMax, List<NpcTradeStack> itemRewards,
                       boolean cancelOnNpcDeath, List<String> prerequisiteQuestIds) {

    public NpcQuest {
        objectives = List.copyOf(objectives);
        itemRewards = List.copyOf(itemRewards);
        prerequisiteQuestIds = List.copyOf(prerequisiteQuestIds);
    }

    private static final StreamCodec<ByteBuf, List<NpcQuestObjective>> OBJECTIVE_LIST =
            NpcQuestObjective.STREAM_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<NpcTradeStack>> REWARD_LIST =
            NpcTradeStack.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final Codec<NpcQuest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("quest_id").forGetter(NpcQuest::questId),
            Codec.STRING.fieldOf("name").forGetter(NpcQuest::name),
            Codec.STRING.optionalFieldOf("title", "").forGetter(NpcQuest::title),
            Codec.STRING.optionalFieldOf("description", "").forGetter(NpcQuest::description),
            NpcQuestObjective.CODEC.listOf().optionalFieldOf("objectives", List.of())
                    .forGetter(NpcQuest::objectives),
            NpcQuestCompletion.CODEC.optionalFieldOf("completion", NpcQuestCompletion.TURN_IN)
                    .forGetter(NpcQuest::completion),
            Codec.INT.optionalFieldOf("time_limit_seconds", 0).forGetter(NpcQuest::timeLimitSeconds),
            Codec.INT.optionalFieldOf("xp_min", 0).forGetter(NpcQuest::xpMin),
            Codec.INT.optionalFieldOf("xp_max", 0).forGetter(NpcQuest::xpMax),
            NpcTradeStack.CODEC.listOf().optionalFieldOf("item_rewards", List.of())
                    .forGetter(NpcQuest::itemRewards),
            Codec.BOOL.optionalFieldOf("cancel_on_npc_death", false)
                    .forGetter(NpcQuest::cancelOnNpcDeath),
            Codec.STRING.listOf().optionalFieldOf("prerequisites", List.of())
                    .forGetter(NpcQuest::prerequisiteQuestIds)
    ).apply(instance, NpcQuest::new));

    private static final StreamCodec<ByteBuf, List<String>> PREREQUISITE_LIST =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, NpcQuest> STREAM_CODEC = StreamCodec.of(
            (buffer, quest) -> {
                ByteBufCodecs.STRING_UTF8.encode(buffer, quest.questId());
                ByteBufCodecs.STRING_UTF8.encode(buffer, quest.name());
                ByteBufCodecs.STRING_UTF8.encode(buffer, quest.title());
                ByteBufCodecs.STRING_UTF8.encode(buffer, quest.description());
                OBJECTIVE_LIST.encode(buffer, quest.objectives());
                NpcQuestCompletion.STREAM_CODEC.encode(buffer, quest.completion());
                ByteBufCodecs.VAR_INT.encode(buffer, quest.timeLimitSeconds());
                ByteBufCodecs.VAR_INT.encode(buffer, quest.xpMin());
                ByteBufCodecs.VAR_INT.encode(buffer, quest.xpMax());
                REWARD_LIST.encode(buffer, quest.itemRewards());
                ByteBufCodecs.BOOL.encode(buffer, quest.cancelOnNpcDeath());
                PREREQUISITE_LIST.encode(buffer, quest.prerequisiteQuestIds());
            },
            buffer -> new NpcQuest(
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    OBJECTIVE_LIST.decode(buffer),
                    NpcQuestCompletion.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    REWARD_LIST.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    PREREQUISITE_LIST.decode(buffer)));

    public static NpcQuest create(String name, String title, String description,
                                  List<NpcQuestObjective> objectives,
                                  NpcQuestCompletion completion, int timeLimitSeconds,
                                  int xpMin, int xpMax, List<NpcTradeStack> itemRewards,
                                  boolean cancelOnNpcDeath, List<String> prerequisiteQuestIds) {
        return new NpcQuest(UUID.randomUUID().toString(), name, title, description, objectives,
                completion, timeLimitSeconds, xpMin, xpMax, itemRewards, cancelOnNpcDeath,
                prerequisiteQuestIds);
    }

    public static NpcQuest create(String name, List<NpcQuestObjective> objectives,
                                  NpcQuestCompletion completion, int timeLimitSeconds,
                                  int xpMin, int xpMax, List<NpcTradeStack> itemRewards) {
        return create(name, "", "", objectives, completion, timeLimitSeconds,
                xpMin, xpMax, itemRewards, false, List.of());
    }

    // Collect objectives are consumed at the NPC, so a quest containing one is inherently turn-in.
    // The single shared trigger for isValid() and the editor's disabled AUTO option.
    public boolean hasCollectObjective() {
        for (NpcQuestObjective objective : objectives) {
            if (objective.type() == NpcObjectiveType.COLLECT_ITEMS) {
                return true;
            }
        }
        return false;
    }

    public boolean isValid() {
        if (questId == null || questId.isBlank() || name == null || completion == null
                || title == null || description == null) {
            return false;
        }
        for (String prerequisiteId : prerequisiteQuestIds) {
            if (prerequisiteId == null || prerequisiteId.isBlank()) {
                return false;
            }
        }
        for (NpcQuestObjective objective : objectives) {
            if (!objective.isValid()) {
                return false;
            }
        }
        if (completion == NpcQuestCompletion.AUTO && hasCollectObjective()) {
            return false;
        }
        if (timeLimitSeconds < 0 || timeLimitSeconds > NpcValidation.MAX_QUEST_TIME_LIMIT_SECONDS) {
            return false;
        }
        if (xpMin < 0 || xpMax > NpcValidation.MAX_JOB_XP || xpMin > xpMax) {
            return false;
        }
        if (itemRewards.size() > NpcValidation.MAX_QUEST_REWARD_SLOTS) {
            return false;
        }
        for (NpcTradeStack reward : itemRewards) {
            if (!reward.isValid()) {
                return false;
            }
        }
        return true;
    }
}
