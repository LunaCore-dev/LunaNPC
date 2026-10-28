package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// One boolean test the engine evaluates as comparator.test(left, right), optionally inverted by negate.
public record NpcCombatCondition(NpcValueSource left, NpcCombatComparator comparator, NpcValueSource right,
                                 boolean negate) {

    public static final Codec<NpcCombatCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcValueSource.CODEC.fieldOf("left").forGetter(NpcCombatCondition::left),
            NpcCombatComparator.CODEC.optionalFieldOf("comparator", NpcCombatComparator.GREATER_EQUAL)
                    .forGetter(NpcCombatCondition::comparator),
            NpcValueSource.CODEC.optionalFieldOf("right", NpcValueSource.constant(0.0F))
                    .forGetter(NpcCombatCondition::right),
            Codec.BOOL.optionalFieldOf("negate", false).forGetter(NpcCombatCondition::negate)
    ).apply(instance, NpcCombatCondition::new));

    public static final StreamCodec<ByteBuf, NpcCombatCondition> STREAM_CODEC = StreamCodec.composite(
            NpcValueSource.STREAM_CODEC, NpcCombatCondition::left,
            NpcCombatComparator.STREAM_CODEC, NpcCombatCondition::comparator,
            NpcValueSource.STREAM_CODEC, NpcCombatCondition::right,
            ByteBufCodecs.BOOL, NpcCombatCondition::negate,
            NpcCombatCondition::new);

    public boolean isValid() {
        return left != null && left.isValid() && right != null && right.isValid() && comparator != null;
    }
}
