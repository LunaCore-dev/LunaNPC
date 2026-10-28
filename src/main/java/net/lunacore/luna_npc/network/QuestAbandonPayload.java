package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Client → server: the player chose to drop the quest with this id from their book. The server removes
// it from their active quests (no reward, no completion) and pushes a fresh book snapshot back.
public record QuestAbandonPayload(String questId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuestAbandonPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "quest_abandon"));

    public static final StreamCodec<ByteBuf, QuestAbandonPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, QuestAbandonPayload::questId,
            QuestAbandonPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
