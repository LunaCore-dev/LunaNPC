package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcValidation;

// One effect an attack applies to its target. Namespaced ids ("minecraft:wither") are potion
// effects with a duration; plain ids ("knockback", "flame") are the enchantment-like effects,
// which ignore the duration and use the amplifier as their level — deliberately allowed up to
// 10, far beyond the vanilla enchantment caps.
public record NpcAttackEffect(String effectId, float durationSeconds, int amplifier) {

    public static final Codec<NpcAttackEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("effect").forGetter(NpcAttackEffect::effectId),
            Codec.FLOAT.optionalFieldOf("duration_seconds", NpcDefaults.EFFECT_DURATION_SECONDS)
                    .forGetter(NpcAttackEffect::durationSeconds),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(NpcAttackEffect::amplifier)
    ).apply(instance, NpcAttackEffect::new));

    public static final StreamCodec<ByteBuf, NpcAttackEffect> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcAttackEffect::effectId,
            ByteBufCodecs.FLOAT, NpcAttackEffect::durationSeconds,
            ByteBufCodecs.VAR_INT, NpcAttackEffect::amplifier,
            NpcAttackEffect::new);

    // Developer convenience: each returns a new record with a single field swapped, immutability kept.
    public NpcAttackEffect withEffectId(String effectId) {
        return new NpcAttackEffect(effectId, durationSeconds, amplifier);
    }

    public NpcAttackEffect withDuration(float durationSeconds) {
        return new NpcAttackEffect(effectId, durationSeconds, amplifier);
    }

    public NpcAttackEffect withAmplifier(int amplifier) {
        return new NpcAttackEffect(effectId, durationSeconds, amplifier);
    }

    public boolean isEnchantLike() {
        return !effectId.contains(":");
    }

    public boolean isValid() {
        if (durationSeconds < 0.0F || durationSeconds > NpcValidation.MAX_EFFECT_DURATION_SECONDS
                || amplifier < 0 || amplifier > NpcValidation.MAX_EFFECT_AMPLIFIER) {
            return false;
        }
        if (isEnchantLike()) {
            return NpcValidation.ENCHANT_EFFECTS.contains(effectId);
        }
        Identifier parsed = Identifier.tryParse(effectId);
        return parsed != null && BuiltInRegistries.MOB_EFFECT.getOptional(parsed).isPresent();
    }
}
