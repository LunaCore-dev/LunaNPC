package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// One independent regeneration rule: a gate plus its own rate. value is the seconds for
// NO_DAMAGE_SECONDS and the HP threshold for HP_BELOW/HP_ABOVE (OUT_OF_COMBAT ignores it);
// intervalSeconds is the time between each heal and amount is the HP restored per heal. The rule fires
// on its own timer whenever its condition passes.
public record NpcRegenCondition(NpcRegenConditionType type, int value, float intervalSeconds, int amount) {

    public static final Codec<NpcRegenCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcRegenConditionType.CODEC.fieldOf("type").forGetter(NpcRegenCondition::type),
            Codec.INT.optionalFieldOf("value", 0).forGetter(NpcRegenCondition::value),
            Codec.FLOAT.optionalFieldOf("interval_seconds", 1.0F).forGetter(NpcRegenCondition::intervalSeconds),
            Codec.INT.optionalFieldOf("amount", 1).forGetter(NpcRegenCondition::amount)
    ).apply(instance, NpcRegenCondition::new));

    public static final StreamCodec<ByteBuf, NpcRegenCondition> STREAM_CODEC = StreamCodec.composite(
            NpcRegenConditionType.STREAM_CODEC, NpcRegenCondition::type,
            ByteBufCodecs.VAR_INT, NpcRegenCondition::value,
            ByteBufCodecs.FLOAT, NpcRegenCondition::intervalSeconds,
            ByteBufCodecs.VAR_INT, NpcRegenCondition::amount,
            NpcRegenCondition::new);

    public boolean isValid() {
        return type != null && value >= 0
                && intervalSeconds >= 0 && intervalSeconds <= NpcValidation.MAX_REGEN_INTERVAL_SECONDS
                && amount >= 1 && amount <= NpcValidation.MAX_REGEN_AMOUNT;
    }
}
