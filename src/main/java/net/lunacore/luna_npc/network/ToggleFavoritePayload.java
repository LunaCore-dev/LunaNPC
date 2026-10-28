package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

public record ToggleFavoritePayload(long lunaId, boolean favorite) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleFavoritePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "toggle_favorite"));

    public static final StreamCodec<ByteBuf, ToggleFavoritePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ToggleFavoritePayload::lunaId,
            ByteBufCodecs.BOOL, ToggleFavoritePayload::favorite,
            ToggleFavoritePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
