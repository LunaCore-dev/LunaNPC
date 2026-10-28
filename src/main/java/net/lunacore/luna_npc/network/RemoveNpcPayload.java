package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Serverbound: the Remove NPC stick asks the server to permanently wipe the clicked NPC instance,
// identified by its live entity id. The server re-resolves and validates it — the client only drives
// the confirmation UI.
public record RemoveNpcPayload(int entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RemoveNpcPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "remove_npc"));

    public static final StreamCodec<ByteBuf, RemoveNpcPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RemoveNpcPayload::entityId,
            RemoveNpcPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
