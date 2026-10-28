package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcValidation;

// How strictly a vertical preference (flight height above ground, swim depth below surface) is
// held. Flight: unfixed gives way to the target's height in combat, fixed holds the exact height
// robotically. Swim depth: unfixed leaves the preferred depth freely in combat, fixed holds it —
// the tolerance range then becomes the depth band a target must be within to be fought at all.
public record NpcHeightPreference(boolean fixed, int range) {

    public static final Codec<NpcHeightPreference> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("fixed", false).forGetter(NpcHeightPreference::fixed),
            Codec.INT.optionalFieldOf("range", NpcDefaults.HEIGHT_RANGE).forGetter(NpcHeightPreference::range)
    ).apply(instance, NpcHeightPreference::new));

    public static final StreamCodec<ByteBuf, NpcHeightPreference> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, NpcHeightPreference::fixed,
            ByteBufCodecs.VAR_INT, NpcHeightPreference::range,
            NpcHeightPreference::new);

    public static NpcHeightPreference defaults() {
        return new NpcHeightPreference(false, NpcDefaults.HEIGHT_RANGE);
    }

    // Additive convenience copies: each returns a new record with a single field changed via the
    // canonical constructor, preserving immutability.
    public NpcHeightPreference withFixed(boolean fixed) {
        return new NpcHeightPreference(fixed, range);
    }

    public NpcHeightPreference withRange(int range) {
        return new NpcHeightPreference(fixed, range);
    }

    public boolean isValid() {
        return range >= NpcValidation.MIN_HEIGHT_RANGE && range <= NpcValidation.MAX_HEIGHT_RANGE;
    }
}
