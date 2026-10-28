package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// A specific player, matched by UUID; the name is only the label shown in menus (it may be out of date
// after a rename, which never breaks the match).
public record NpcPlayerRef(UUID id, String name) {

    public static final Codec<NpcPlayerRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(NpcPlayerRef::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(NpcPlayerRef::name)
    ).apply(instance, NpcPlayerRef::new));

    public static final StreamCodec<ByteBuf, NpcPlayerRef> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, NpcPlayerRef::id,
            ByteBufCodecs.STRING_UTF8, NpcPlayerRef::name,
            NpcPlayerRef::new);
}
