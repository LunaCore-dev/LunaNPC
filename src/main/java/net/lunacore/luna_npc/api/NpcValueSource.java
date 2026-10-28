package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// A number the advanced-combat engine resolves at decision time from the NPC, its target or the world.
// value/value2 feed CONSTANT and range-style sources; radius scopes the count sources; key names the
// flag or counter for FLAG_VALUE/COUNTER_VALUE.
public record NpcValueSource(NpcValueSourceType type, float value, float value2, int radius, String key) {

    public static final Codec<NpcValueSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcValueSourceType.CODEC.fieldOf("type").forGetter(NpcValueSource::type),
            Codec.FLOAT.optionalFieldOf("value", 0.0F).forGetter(NpcValueSource::value),
            Codec.FLOAT.optionalFieldOf("value2", 0.0F).forGetter(NpcValueSource::value2),
            Codec.INT.optionalFieldOf("radius", 0).forGetter(NpcValueSource::radius),
            Codec.STRING.optionalFieldOf("key", "").forGetter(NpcValueSource::key)
    ).apply(instance, NpcValueSource::new));

    public static final StreamCodec<ByteBuf, NpcValueSource> STREAM_CODEC = StreamCodec.composite(
            NpcValueSourceType.STREAM_CODEC, NpcValueSource::type,
            ByteBufCodecs.FLOAT, NpcValueSource::value,
            ByteBufCodecs.FLOAT, NpcValueSource::value2,
            ByteBufCodecs.VAR_INT, NpcValueSource::radius,
            ByteBufCodecs.STRING_UTF8, NpcValueSource::key,
            NpcValueSource::new);

    public static NpcValueSource constant(float v) {
        return new NpcValueSource(NpcValueSourceType.CONSTANT, v, 0.0F, 0, "");
    }

    public boolean isValid() {
        return type != null && key != null && radius >= 0;
    }
}
