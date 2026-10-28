package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

public record RequestNpcListPayload(boolean forSpawn, BlockPos clickedPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestNpcListPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "request_npc_list"));

    public static final StreamCodec<ByteBuf, RequestNpcListPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RequestNpcListPayload::forSpawn,
            BlockPos.STREAM_CODEC, RequestNpcListPayload::clickedPos,
            RequestNpcListPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
