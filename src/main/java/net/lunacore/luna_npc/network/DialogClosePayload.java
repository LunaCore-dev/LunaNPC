package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Server → client: end the conversation with the NPC identified by lunaId and close the dialog screen.
public record DialogClosePayload(long lunaId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DialogClosePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "dialog_close"));

    public static final StreamCodec<ByteBuf, DialogClosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DialogClosePayload::lunaId,
            DialogClosePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
