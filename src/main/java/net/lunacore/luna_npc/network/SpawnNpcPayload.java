package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

public record SpawnNpcPayload(long lunaId, BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpawnNpcPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "spawn_npc"));

    public static final StreamCodec<ByteBuf, SpawnNpcPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SpawnNpcPayload::lunaId,
            BlockPos.STREAM_CODEC, SpawnNpcPayload::pos,
            SpawnNpcPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
