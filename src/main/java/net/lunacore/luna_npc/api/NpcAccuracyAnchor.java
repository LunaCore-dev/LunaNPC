package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// One point of the ranged accuracy curve: at this distance the shot is this accurate. The curve
// interpolates linearly between anchors; at most one anchor per block distance.
public record NpcAccuracyAnchor(int distanceBlocks, int percent) {

    public static final Codec<NpcAccuracyAnchor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("distance").forGetter(NpcAccuracyAnchor::distanceBlocks),
            Codec.INT.fieldOf("percent").forGetter(NpcAccuracyAnchor::percent)
    ).apply(instance, NpcAccuracyAnchor::new));

    public static final StreamCodec<ByteBuf, NpcAccuracyAnchor> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcAccuracyAnchor::distanceBlocks,
            ByteBufCodecs.VAR_INT, NpcAccuracyAnchor::percent,
            NpcAccuracyAnchor::new);

    // Developer convenience: each returns a new record with a single field swapped, immutability kept.
    public NpcAccuracyAnchor withDistance(int distanceBlocks) {
        return new NpcAccuracyAnchor(distanceBlocks, percent);
    }

    public NpcAccuracyAnchor withPercent(int percent) {
        return new NpcAccuracyAnchor(distanceBlocks, percent);
    }

    public boolean isValid() {
        return distanceBlocks >= 0 && distanceBlocks <= NpcValidation.MAX_ACCURACY_DISTANCE
                && percent >= 0 && percent <= 100;
    }
}
