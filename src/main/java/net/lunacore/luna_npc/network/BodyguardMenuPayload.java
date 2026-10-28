package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.NpcBodyguardMode;
import net.lunacore.luna_npc.api.NpcTradeStack;

// Server -> client: a right-clicked bodyguard's menu. For its hirer (hirer = true) the orders screen with
// the current order and the hire time left (-1 = until it gives up); for anyone else the hire offer with its cost
// and duration (0 = until it gives up).
public record BodyguardMenuPayload(int npcEntityId, String npcName, boolean hirer, List<NpcTradeStack> cost,
                                   int hireSeconds, long remainingSeconds, NpcBodyguardMode mode)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BodyguardMenuPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "bodyguard_menu"));

    public static final StreamCodec<ByteBuf, BodyguardMenuPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BodyguardMenuPayload::npcEntityId,
            ByteBufCodecs.STRING_UTF8, BodyguardMenuPayload::npcName,
            ByteBufCodecs.BOOL, BodyguardMenuPayload::hirer,
            NpcTradeStack.STREAM_CODEC.apply(ByteBufCodecs.list()), BodyguardMenuPayload::cost,
            ByteBufCodecs.VAR_INT, BodyguardMenuPayload::hireSeconds,
            ByteBufCodecs.VAR_LONG, BodyguardMenuPayload::remainingSeconds,
            NpcBodyguardMode.STREAM_CODEC, BodyguardMenuPayload::mode,
            BodyguardMenuPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
