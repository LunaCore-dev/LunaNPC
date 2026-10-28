package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// One trade offer: up to 4 input stacks bought for up to 4 output stacks, an XP reward rolled
// uniformly in [xpMin, xpMax], and an optional stock limit — after stockCount uses (counted per
// player, per NPC instance or world-wide, by stockScope) the trade locks until restockSeconds pass.
public record NpcTrade(List<NpcTradeStack> inputs, List<NpcTradeStack> outputs,
                       int xpMin, int xpMax,
                       boolean stockLimited, int stockCount, int restockSeconds,
                       NpcStockScope stockScope) {

    public NpcTrade {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    private static final StreamCodec<ByteBuf, List<NpcTradeStack>> STACK_LIST =
            NpcTradeStack.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final Codec<NpcTrade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcTradeStack.CODEC.listOf().fieldOf("inputs").forGetter(NpcTrade::inputs),
            NpcTradeStack.CODEC.listOf().fieldOf("outputs").forGetter(NpcTrade::outputs),
            Codec.INT.optionalFieldOf("xp_min", 0).forGetter(NpcTrade::xpMin),
            Codec.INT.optionalFieldOf("xp_max", 0).forGetter(NpcTrade::xpMax),
            Codec.BOOL.optionalFieldOf("stock_limited", false).forGetter(NpcTrade::stockLimited),
            Codec.INT.optionalFieldOf("stock_count", 0).forGetter(NpcTrade::stockCount),
            Codec.INT.optionalFieldOf("restock_seconds", 0).forGetter(NpcTrade::restockSeconds),
            NpcStockScope.CODEC.optionalFieldOf("stock_scope", NpcStockScope.PER_PLAYER)
                    .forGetter(NpcTrade::stockScope)
    ).apply(instance, NpcTrade::new));

    public static final StreamCodec<ByteBuf, NpcTrade> STREAM_CODEC = StreamCodec.of(
            (buffer, trade) -> {
                STACK_LIST.encode(buffer, trade.inputs());
                STACK_LIST.encode(buffer, trade.outputs());
                ByteBufCodecs.VAR_INT.encode(buffer, trade.xpMin());
                ByteBufCodecs.VAR_INT.encode(buffer, trade.xpMax());
                ByteBufCodecs.BOOL.encode(buffer, trade.stockLimited());
                ByteBufCodecs.VAR_INT.encode(buffer, trade.stockCount());
                ByteBufCodecs.VAR_INT.encode(buffer, trade.restockSeconds());
                NpcStockScope.STREAM_CODEC.encode(buffer, trade.stockScope());
            },
            buffer -> new NpcTrade(
                    STACK_LIST.decode(buffer),
                    STACK_LIST.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    NpcStockScope.STREAM_CODEC.decode(buffer)));

    public boolean isValid() {
        if (inputs.isEmpty() || inputs.size() > NpcValidation.MAX_TRADE_SLOTS
                || outputs.isEmpty() || outputs.size() > NpcValidation.MAX_TRADE_SLOTS) {
            return false;
        }
        for (NpcTradeStack stack : inputs) {
            if (!stack.isValid()) {
                return false;
            }
        }
        for (NpcTradeStack stack : outputs) {
            if (!stack.isValid()) {
                return false;
            }
        }
        if (xpMin < 0 || xpMax > NpcValidation.MAX_JOB_XP || xpMin > xpMax) {
            return false;
        }
        return stockScope != null
                && stockCount >= 0 && stockCount <= NpcValidation.MAX_TRADE_STOCK
                && restockSeconds >= 0 && restockSeconds <= NpcValidation.MAX_RESTOCK_SECONDS;
    }
}
