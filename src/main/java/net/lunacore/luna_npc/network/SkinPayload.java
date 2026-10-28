package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.NpcSkinArms;

// Server -> client: one downloaded skin with its PNG, sent per skin on join and whenever it is added or
// edited, so every player renders it.
public record SkinPayload(String id, String name, NpcSkinArms arms, byte[] png) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SkinPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "skin"));

    public static final StreamCodec<ByteBuf, SkinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SkinPayload::id,
            ByteBufCodecs.STRING_UTF8, SkinPayload::name,
            NpcSkinArms.STREAM_CODEC, SkinPayload::arms,
            ByteBufCodecs.byteArray(256 * 1024), SkinPayload::png,
            SkinPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
