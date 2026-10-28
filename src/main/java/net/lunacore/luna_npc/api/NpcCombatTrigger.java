package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// What fires a combat rule. seconds tunes interval/timer triggers; threshold tunes the crossing triggers
// (e.g. HP fraction); key names the timer, status or flag a trigger listens on.
public record NpcCombatTrigger(NpcCombatTriggerType type, float seconds, float threshold, String key) {

    public static final Codec<NpcCombatTrigger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcCombatTriggerType.CODEC.fieldOf("type").forGetter(NpcCombatTrigger::type),
            Codec.FLOAT.optionalFieldOf("seconds", 0.0F).forGetter(NpcCombatTrigger::seconds),
            Codec.FLOAT.optionalFieldOf("threshold", 0.0F).forGetter(NpcCombatTrigger::threshold),
            Codec.STRING.optionalFieldOf("key", "").forGetter(NpcCombatTrigger::key)
    ).apply(instance, NpcCombatTrigger::new));

    public static final StreamCodec<ByteBuf, NpcCombatTrigger> STREAM_CODEC = StreamCodec.composite(
            NpcCombatTriggerType.STREAM_CODEC, NpcCombatTrigger::type,
            ByteBufCodecs.FLOAT, NpcCombatTrigger::seconds,
            ByteBufCodecs.FLOAT, NpcCombatTrigger::threshold,
            ByteBufCodecs.STRING_UTF8, NpcCombatTrigger::key,
            NpcCombatTrigger::new);

    public static NpcCombatTrigger always() {
        return new NpcCombatTrigger(NpcCombatTriggerType.ALWAYS, 0.0F, 0.0F, "");
    }

    public boolean isValid() {
        return type != null && key != null && seconds >= 0.0F;
    }
}
