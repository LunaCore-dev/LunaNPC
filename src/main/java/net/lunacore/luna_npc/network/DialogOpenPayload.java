package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Server → client: the node to show. lunaId identifies the NPC being talked to (the client echoes it
// back with a choice), npcName titles the screen, speakerLine is the NPC's text, and choices are the
// answer buttons already filtered for this player. An empty choice list is a terminal node — the
// client shows a single Close.
public record DialogOpenPayload(long lunaId, String npcName, String speakerLine,
                                List<DialogChoiceLine> choices) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DialogOpenPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "dialog_open"));

    private static final StreamCodec<ByteBuf, List<DialogChoiceLine>> CHOICES =
            DialogChoiceLine.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, DialogOpenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DialogOpenPayload::lunaId,
            ByteBufCodecs.STRING_UTF8, DialogOpenPayload::npcName,
            ByteBufCodecs.STRING_UTF8, DialogOpenPayload::speakerLine,
            CHOICES, DialogOpenPayload::choices,
            DialogOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
