package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// One answer button as sent to the client: choiceIndex points back at the node's original choice list
// (some choices are filtered out server-side, so the index is not the display position), text is the
// label, enabled is false for a locked-but-shown choice (greyed rather than hidden), and endsTalk marks
// a choice that closes the conversation so the screen can flag it.
public record DialogChoiceLine(int choiceIndex, String text, boolean enabled, boolean endsTalk) {

    public static final StreamCodec<ByteBuf, DialogChoiceLine> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DialogChoiceLine::choiceIndex,
            ByteBufCodecs.STRING_UTF8, DialogChoiceLine::text,
            ByteBufCodecs.BOOL, DialogChoiceLine::enabled,
            ByteBufCodecs.BOOL, DialogChoiceLine::endsTalk,
            DialogChoiceLine::new);
}
