package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// One weighted quest variant: an NPC that rolls this sub-type at spawn offers exactly these quests
// for its lifetime. Quest weights are relative (normalized at roll) — unlike trades/farms they do
// not have to total 100.
public record NpcQuestSubType(int weightPercent, List<NpcQuest> quests) {

    public NpcQuestSubType {
        quests = List.copyOf(quests);
    }

    private static final StreamCodec<ByteBuf, List<NpcQuest>> QUEST_LIST =
            NpcQuest.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final Codec<NpcQuestSubType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("weight", 0).forGetter(NpcQuestSubType::weightPercent),
            NpcQuest.CODEC.listOf().optionalFieldOf("quests", List.of()).forGetter(NpcQuestSubType::quests)
    ).apply(instance, NpcQuestSubType::new));

    public static final StreamCodec<ByteBuf, NpcQuestSubType> STREAM_CODEC = StreamCodec.of(
            (buffer, subType) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, subType.weightPercent());
                QUEST_LIST.encode(buffer, subType.quests());
            },
            buffer -> new NpcQuestSubType(
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    QUEST_LIST.decode(buffer)));

    public boolean isValid() {
        if (weightPercent < 0) {
            return false;
        }
        for (NpcQuest quest : quests) {
            if (!quest.isValid()) {
                return false;
            }
        }
        return true;
    }
}
