package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Client -> server: the player accepts the quest with this id from the interacted NPC. The server
// validates proximity, block state and that the NPC's rolled sub-type actually offers it.
public record QuestAcceptPayload(int npcEntityId, String questId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuestAcceptPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "quest_accept"));

    public static final StreamCodec<ByteBuf, QuestAcceptPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuestAcceptPayload::npcEntityId,
            ByteBufCodecs.STRING_UTF8, QuestAcceptPayload::questId,
            QuestAcceptPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
