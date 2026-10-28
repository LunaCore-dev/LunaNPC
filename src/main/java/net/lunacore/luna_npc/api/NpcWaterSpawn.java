package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// How deep below the water (or lava) surface a swimming NPC (movement type "water") is allowed to
// appear when it spawns naturally: a depth band measured in blocks down from the fluid surface
// (minDepth..maxDepth, 0-64). A shallow band keeps reef fish near the top; a deep band confines an
// abyssal creature to the ocean floor. The spawn is clamped to real water so it never lands below the
// seabed. Ignored for ground and air NPCs.
public record NpcWaterSpawn(int minDepth, int maxDepth) {

    public static final int MAX_DEPTH = 64;

    public static final Codec<NpcWaterSpawn> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("min_depth", 1).forGetter(NpcWaterSpawn::minDepth),
            Codec.INT.optionalFieldOf("max_depth", MAX_DEPTH).forGetter(NpcWaterSpawn::maxDepth)
    ).apply(instance, NpcWaterSpawn::new));

    public static final StreamCodec<ByteBuf, NpcWaterSpawn> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcWaterSpawn::minDepth,
            ByteBufCodecs.VAR_INT, NpcWaterSpawn::maxDepth,
            NpcWaterSpawn::new);

    public static NpcWaterSpawn defaults() {
        return new NpcWaterSpawn(1, MAX_DEPTH);
    }

    public NpcWaterSpawn withDepth(int min, int max) {
        return new NpcWaterSpawn(min, max);
    }

    public boolean isValid() {
        return minDepth >= 0 && maxDepth <= MAX_DEPTH && minDepth <= maxDepth;
    }
}
