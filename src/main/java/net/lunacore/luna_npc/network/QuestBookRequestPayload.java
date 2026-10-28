package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Client -> server: the open quest book asks for a fresh snapshot of the sender's active quests, so
// its progress lines stay live while the countdown ticks locally. Player identity is implicit.
public record QuestBookRequestPayload() implements CustomPacketPayload {

    public static final QuestBookRequestPayload INSTANCE = new QuestBookRequestPayload();

    public static final CustomPacketPayload.Type<QuestBookRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "quest_book_request"));

    public static final StreamCodec<ByteBuf, QuestBookRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
