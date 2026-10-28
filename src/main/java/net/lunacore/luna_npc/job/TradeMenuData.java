package net.lunacore.luna_npc.job;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.api.NpcTrade;

// Snapshot the client needs to render the trade menu, carried over the Fabric ExtendedMenuType data
// channel as the menu opens. Never trusted server-side: every trade is re-validated on execute.
public record TradeMenuData(int npcEntityId, List<NpcTrade> trades, List<TradeStockView> stock) {

    public TradeMenuData {
        trades = List.copyOf(trades);
        stock = List.copyOf(stock);
    }

    // Display-only. remainingUses < 0 = unlimited; 0 = out of stock, restockSecondsLeft counting down.
    public record TradeStockView(int tradeIndex, int remainingUses, int restockSecondsLeft) {
        public static final StreamCodec<ByteBuf, TradeStockView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TradeStockView::tradeIndex,
                ByteBufCodecs.VAR_INT, TradeStockView::remainingUses,
                ByteBufCodecs.VAR_INT, TradeStockView::restockSecondsLeft,
                TradeStockView::new);

        public boolean unlimited() {
            return remainingUses < 0;
        }

        public boolean inStock() {
            return remainingUses != 0;
        }
    }

    private static final StreamCodec<ByteBuf, List<NpcTrade>> TRADES =
            NpcTrade.STREAM_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<TradeStockView>> STOCK =
            TradeStockView.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, TradeMenuData> STREAM_CODEC = StreamCodec.of(
            (buffer, data) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, data.npcEntityId());
                TRADES.encode(buffer, data.trades());
                STOCK.encode(buffer, data.stock());
            },
            buffer -> new TradeMenuData(
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    TRADES.decode(buffer),
                    STOCK.decode(buffer)));
}
