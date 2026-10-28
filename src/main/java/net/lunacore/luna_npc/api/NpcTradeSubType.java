package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// One weighted trading variant: an NPC that rolls this sub-type at spawn offers exactly these trades
// (up to 10) for its lifetime. Weights across a job's sub-type list must total exactly 100.
public record NpcTradeSubType(int weightPercent, List<NpcTrade> trades) {

    public NpcTradeSubType {
        trades = List.copyOf(trades);
    }

    private static final StreamCodec<ByteBuf, List<NpcTrade>> TRADE_LIST =
            NpcTrade.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final Codec<NpcTradeSubType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("weight", 0).forGetter(NpcTradeSubType::weightPercent),
            NpcTrade.CODEC.listOf().optionalFieldOf("trades", List.of()).forGetter(NpcTradeSubType::trades)
    ).apply(instance, NpcTradeSubType::new));

    public static final StreamCodec<ByteBuf, NpcTradeSubType> STREAM_CODEC = StreamCodec.of(
            (buffer, subType) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, subType.weightPercent());
                TRADE_LIST.encode(buffer, subType.trades());
            },
            buffer -> new NpcTradeSubType(
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    TRADE_LIST.decode(buffer)));

    public boolean isValid() {
        if (weightPercent < 0 || weightPercent > NpcValidation.JOB_WEIGHT_TOTAL_PERCENT
                || trades.size() > NpcValidation.MAX_TRADES_PER_SUB_TYPE) {
            return false;
        }
        for (NpcTrade trade : trades) {
            if (!trade.isValid()) {
                return false;
            }
        }
        return true;
    }
}
