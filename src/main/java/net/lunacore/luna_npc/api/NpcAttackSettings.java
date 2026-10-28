package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcValidation;

// One attack category (close range / long range): raw damage, reach, seconds between attacks,
// the effects the hit puts on the target and what the impact does to the world around it. Each
// effect may appear only once per category.
public record NpcAttackSettings(boolean enabled, float damage, float range, float intervalSeconds,
                                List<NpcAttackEffect> effects, NpcImpactSettings impact) {

    public NpcAttackSettings {
        effects = List.copyOf(effects);
    }

    public static final Codec<NpcAttackSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(NpcAttackSettings::enabled),
            Codec.FLOAT.optionalFieldOf("damage", NpcDefaults.ATTACK_DAMAGE).forGetter(NpcAttackSettings::damage),
            Codec.FLOAT.optionalFieldOf("range", NpcDefaults.MELEE_RANGE).forGetter(NpcAttackSettings::range),
            Codec.FLOAT.optionalFieldOf("interval_seconds", NpcDefaults.ATTACK_INTERVAL_SECONDS)
                    .forGetter(NpcAttackSettings::intervalSeconds),
            NpcAttackEffect.CODEC.listOf().optionalFieldOf("effects", List.of())
                    .forGetter(NpcAttackSettings::effects),
            NpcImpactSettings.CODEC.optionalFieldOf("impact", NpcImpactSettings.defaults())
                    .forGetter(NpcAttackSettings::impact)
    ).apply(instance, NpcAttackSettings::new));

    public static final StreamCodec<ByteBuf, NpcAttackSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, NpcAttackSettings::enabled,
            ByteBufCodecs.FLOAT, NpcAttackSettings::damage,
            ByteBufCodecs.FLOAT, NpcAttackSettings::range,
            ByteBufCodecs.FLOAT, NpcAttackSettings::intervalSeconds,
            ByteBufCodecs.collection(ArrayList::new, NpcAttackEffect.STREAM_CODEC),
            NpcAttackSettings::effects,
            NpcImpactSettings.STREAM_CODEC, NpcAttackSettings::impact,
            NpcAttackSettings::new);

    public static NpcAttackSettings defaultMelee() {
        return new NpcAttackSettings(true, NpcDefaults.ATTACK_DAMAGE, NpcDefaults.MELEE_RANGE,
                NpcDefaults.ATTACK_INTERVAL_SECONDS, List.of(), NpcImpactSettings.defaults());
    }

    public static NpcAttackSettings defaultRanged() {
        return new NpcAttackSettings(false, NpcDefaults.ATTACK_DAMAGE, NpcDefaults.RANGED_RANGE,
                NpcDefaults.ATTACK_INTERVAL_SECONDS, List.of(), NpcImpactSettings.defaults());
    }

    // Developer convenience: each returns a new record with a single field swapped, immutability kept.
    public NpcAttackSettings withEnabled(boolean enabled) {
        return new NpcAttackSettings(enabled, damage, range, intervalSeconds, effects, impact);
    }

    public NpcAttackSettings withDamage(float damage) {
        return new NpcAttackSettings(enabled, damage, range, intervalSeconds, effects, impact);
    }

    public NpcAttackSettings withRange(float range) {
        return new NpcAttackSettings(enabled, damage, range, intervalSeconds, effects, impact);
    }

    public NpcAttackSettings withInterval(float seconds) {
        return new NpcAttackSettings(enabled, damage, range, seconds, effects, impact);
    }

    // Adds the effect, first dropping any existing one with the same id (each effect is unique per category).
    public NpcAttackSettings withEffect(NpcAttackEffect effect) {
        List<NpcAttackEffect> updated = new ArrayList<>(effects);
        updated.removeIf(existing -> existing.effectId().equals(effect.effectId()));
        updated.add(effect);
        return new NpcAttackSettings(enabled, damage, range, intervalSeconds, updated, impact);
    }

    public NpcAttackSettings withEffects(List<NpcAttackEffect> effects) {
        return new NpcAttackSettings(enabled, damage, range, intervalSeconds,
                new ArrayList<>(effects), impact);
    }

    public NpcAttackSettings withImpact(NpcImpactSettings impact) {
        return new NpcAttackSettings(enabled, damage, range, intervalSeconds, effects, impact);
    }

    // An enabled close-range attack at the given damage and reach; other fields keep the melee defaults.
    public static NpcAttackSettings melee(float damage, float range) {
        return defaultMelee().withEnabled(true).withDamage(damage).withRange(range);
    }

    // An enabled long-range attack at the given damage and reach; other fields keep the ranged defaults.
    public static NpcAttackSettings ranged(float damage, float range) {
        return defaultRanged().withEnabled(true).withDamage(damage).withRange(range);
    }

    public int enchantLevel(String enchantId) {
        for (NpcAttackEffect effect : effects) {
            if (effect.isEnchantLike() && effect.effectId().equals(enchantId)) {
                return effect.amplifier();
            }
        }
        return 0;
    }

    public boolean isValid() {
        if (damage < NpcValidation.MIN_ATTACK_DAMAGE || damage > NpcValidation.MAX_ATTACK_DAMAGE
                || range < 0.0F || range > NpcValidation.MAX_ATTACK_RANGE
                || intervalSeconds < NpcValidation.MIN_ATTACK_INTERVAL_SECONDS
                || intervalSeconds > NpcValidation.MAX_ATTACK_INTERVAL_SECONDS
                || effects.size() > NpcValidation.MAX_ATTACK_EFFECTS
                || !impact.isValid()) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (NpcAttackEffect effect : effects) {
            if (!effect.isValid() || !seen.add(effect.effectId())) {
                return false;
            }
        }
        return true;
    }
}
