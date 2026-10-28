package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.job.TradeMenuData;

// Server -> client refresh of a trade menu's per-row stock after a trade executes, so the row that
// just sold out dims and shows its restock countdown without reopening the menu.
public record TradeStockPayload(int npcEntityId, List<TradeMenuData.TradeStockView> stock)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TradeStockPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "trade_stock"));

    private static final StreamCodec<ByteBuf, List<TradeMenuData.TradeStockView>> STOCK =
            TradeMenuData.TradeStockView.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, TradeStockPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TradeStockPayload::npcEntityId,
            STOCK, TradeStockPayload::stock,
            TradeStockPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
