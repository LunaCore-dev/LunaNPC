package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Server -> the requesting client: how its download or edit went. skinId is set on success, failureKey
// (a lang key) otherwise.
public record SkinResultPayload(String skinId, String failureKey) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SkinResultPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "skin_result"));

    public static final StreamCodec<ByteBuf, SkinResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SkinResultPayload::skinId,
            ByteBufCodecs.STRING_UTF8, SkinResultPayload::failureKey,
            SkinResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
