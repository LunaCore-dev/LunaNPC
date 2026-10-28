package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// One priority rule of a range switch: when metric <compare> value holds, the NPC fights at result's
// range. radius is only read for the ENEMIES_IN_RADIUS metric. Rules are evaluated in order and the
// first that passes wins, so a contradictory later rule simply never fires.
public record NpcRangeRule(NpcRangeMetric metric, NpcRangeComparator comparator, float value, int radius,
                           NpcAttackRange result) {

    public static final Codec<NpcRangeRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcRangeMetric.CODEC.fieldOf("metric").forGetter(NpcRangeRule::metric),
            NpcRangeComparator.CODEC.optionalFieldOf("comparator", NpcRangeComparator.AT_MOST)
                    .forGetter(NpcRangeRule::comparator),
            Codec.FLOAT.optionalFieldOf("value", 0.0F).forGetter(NpcRangeRule::value),
            Codec.INT.optionalFieldOf("radius", 8).forGetter(NpcRangeRule::radius),
            NpcAttackRange.CODEC.optionalFieldOf("result", NpcAttackRange.CLOSE).forGetter(NpcRangeRule::result)
    ).apply(instance, NpcRangeRule::new));

    public static final StreamCodec<ByteBuf, NpcRangeRule> STREAM_CODEC = StreamCodec.composite(
            NpcRangeMetric.STREAM_CODEC, NpcRangeRule::metric,
            NpcRangeComparator.STREAM_CODEC, NpcRangeRule::comparator,
            ByteBufCodecs.FLOAT, NpcRangeRule::value,
            ByteBufCodecs.VAR_INT, NpcRangeRule::radius,
            NpcAttackRange.STREAM_CODEC, NpcRangeRule::result,
            NpcRangeRule::new);

    public boolean isValid() {
        if (metric == null || comparator == null || result == null || value < 0.0F) {
            return false;
        }
        return switch (metric) {
            case SELF_HP, OTHERWISE -> true;
            case TARGET_HP_PERCENT -> value <= 100.0F;
            case DISTANCE -> value <= NpcValidation.MAX_RANGE_RULE_DISTANCE;
            case ENEMIES_IN_RADIUS -> value <= NpcValidation.MAX_RANGE_ENEMY_COUNT
                    && radius >= 1 && radius <= NpcValidation.MAX_RANGE_RULE_RADIUS;
        };
    }
}
