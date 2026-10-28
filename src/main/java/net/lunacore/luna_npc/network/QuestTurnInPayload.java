package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Client -> server: the player turns the quest with this id in at the interacted NPC. The server
// re-validates readiness (kills done, collect items present), consumes collect items and grants rewards.
public record QuestTurnInPayload(int npcEntityId, String questId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuestTurnInPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "quest_turn_in"));

    public static final StreamCodec<ByteBuf, QuestTurnInPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuestTurnInPayload::npcEntityId,
            ByteBufCodecs.STRING_UTF8, QuestTurnInPayload::questId,
            QuestTurnInPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
