package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Client -> server: the model picker's Download Skin field. arms: 0 = from the profile (classic for a
// URL), 1 = classic, 2 = slim. The server answers with a SkinResultPayload.
public record DownloadSkinPayload(String input, int arms) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DownloadSkinPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "download_skin"));

    public static final StreamCodec<ByteBuf, DownloadSkinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(2048), DownloadSkinPayload::input,
            ByteBufCodecs.VAR_INT, DownloadSkinPayload::arms,
            DownloadSkinPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
