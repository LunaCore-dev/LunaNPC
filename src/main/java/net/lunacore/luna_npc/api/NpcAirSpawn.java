package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// Where a flying NPC (movement type "air") is allowed to appear when it spawns naturally. Two bands
// that both apply: an absolute world-height window (minWorldY..maxWorldY, clamped to the dimension's
// real height) and a height-above-the-terrain window (minAboveGround..maxAboveGround, 0-64 blocks).
// The spawn Y is picked inside the overlap of the two, so a creator can say "high in the sky" with the
// world window, "just over the treetops" with the above-ground window, or combine them. Ignored for
// ground and water NPCs.
public record NpcAirSpawn(int minWorldY, int maxWorldY, int minAboveGround, int maxAboveGround) {

    public static final int MAX_ABOVE_GROUND = 64;
    public static final int WORLD_Y_FLOOR = -64;
    public static final int WORLD_Y_CEIL = 320;

    public static final Codec<NpcAirSpawn> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("min_world_y", WORLD_Y_FLOOR).forGetter(NpcAirSpawn::minWorldY),
            Codec.INT.optionalFieldOf("max_world_y", WORLD_Y_CEIL).forGetter(NpcAirSpawn::maxWorldY),
            Codec.INT.optionalFieldOf("min_above_ground", 1).forGetter(NpcAirSpawn::minAboveGround),
            Codec.INT.optionalFieldOf("max_above_ground", 16).forGetter(NpcAirSpawn::maxAboveGround)
    ).apply(instance, NpcAirSpawn::new));

    public static final StreamCodec<ByteBuf, NpcAirSpawn> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcAirSpawn::minWorldY,
            ByteBufCodecs.VAR_INT, NpcAirSpawn::maxWorldY,
            ByteBufCodecs.VAR_INT, NpcAirSpawn::minAboveGround,
            ByteBufCodecs.VAR_INT, NpcAirSpawn::maxAboveGround,
            NpcAirSpawn::new);

    public static NpcAirSpawn defaults() {
        return new NpcAirSpawn(WORLD_Y_FLOOR, WORLD_Y_CEIL, 1, 16);
    }

    public NpcAirSpawn withWorldRange(int minY, int maxY) {
        return new NpcAirSpawn(minY, maxY, minAboveGround, maxAboveGround);
    }

    public NpcAirSpawn withAboveGround(int min, int max) {
        return new NpcAirSpawn(minWorldY, maxWorldY, min, max);
    }

    public boolean isValid() {
        return minWorldY <= maxWorldY
                && minAboveGround >= 0 && maxAboveGround <= MAX_ABOVE_GROUND
                && minAboveGround <= maxAboveGround;
    }
}
