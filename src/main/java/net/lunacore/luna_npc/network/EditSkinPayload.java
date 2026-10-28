package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.NpcSkinArms;

// Client -> server: rename a downloaded skin, change its arms, or delete it (delete wins).
public record EditSkinPayload(String id, String name, NpcSkinArms arms, boolean delete) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EditSkinPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "edit_skin"));

    public static final StreamCodec<ByteBuf, EditSkinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, EditSkinPayload::id,
            ByteBufCodecs.STRING_UTF8, EditSkinPayload::name,
            NpcSkinArms.STREAM_CODEC, EditSkinPayload::arms,
            ByteBufCodecs.BOOL, EditSkinPayload::delete,
            EditSkinPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
