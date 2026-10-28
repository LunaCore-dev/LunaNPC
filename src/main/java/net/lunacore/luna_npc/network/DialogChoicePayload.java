package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Client → server: the player picked choiceIndex on the NPC identified by lunaId. The server
// re-validates the choice is currently offered before routing to the next node.
public record DialogChoicePayload(long lunaId, int choiceIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DialogChoicePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "dialog_choice"));

    public static final StreamCodec<ByteBuf, DialogChoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DialogChoicePayload::lunaId,
            ByteBufCodecs.VAR_INT, DialogChoicePayload::choiceIndex,
            DialogChoicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
