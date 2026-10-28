package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// A player the server has seen (from its profile cache), offered in the Manage Permissions screen so an
// admin can grant by picking a name rather than pasting a UUID.
public record KnownPlayer(UUID id, String name) {

    public static final StreamCodec<ByteBuf, KnownPlayer> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, KnownPlayer::id,
            ByteBufCodecs.STRING_UTF8, KnownPlayer::name,
            KnownPlayer::new);
}
