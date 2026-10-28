package net.lunacore.luna_npc.api;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// The whole advanced-combat brain: a flat list of rules the engine evaluates each tick (HP is just a
// condition, so "phases" are emergent), a named attack library the actions reference, and the engagement
// radius — the combat-awareness bubble that scopes every count and target. Runs only when the NPC's
// combatMode is ADVANCED.
public record NpcAdvancedCombat(boolean enabled, float engagementRadius, List<NpcCombatAttack> attackLibrary,
                                List<NpcCombatRule> rules) {

    public NpcAdvancedCombat {
        attackLibrary = List.copyOf(attackLibrary);
        rules = List.copyOf(rules);
    }

    public static final Codec<NpcAdvancedCombat> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(NpcAdvancedCombat::enabled),
            Codec.FLOAT.optionalFieldOf("engagement_radius", 24.0F).forGetter(NpcAdvancedCombat::engagementRadius),
            NpcCombatAttack.CODEC.listOf().optionalFieldOf("attacks", List.of())
                    .forGetter(NpcAdvancedCombat::attackLibrary),
            NpcCombatRule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(NpcAdvancedCombat::rules)
    ).apply(instance, NpcAdvancedCombat::new));

    public static final StreamCodec<ByteBuf, NpcAdvancedCombat> STREAM_CODEC = StreamCodec.of(
            (buffer, advanced) -> {
                ByteBufCodecs.BOOL.encode(buffer, advanced.enabled());
                ByteBufCodecs.FLOAT.encode(buffer, advanced.engagementRadius());
                NpcCombatAttack.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, advanced.attackLibrary());
                NpcCombatRule.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, advanced.rules());
            },
            buffer -> new NpcAdvancedCombat(
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    NpcCombatAttack.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    NpcCombatRule.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer)));

    public static NpcAdvancedCombat defaults() {
        return new NpcAdvancedCombat(false, 24.0F, List.of(), List.of());
    }

    public NpcAdvancedCombat withEnabled(boolean enabled) {
        return new NpcAdvancedCombat(enabled, engagementRadius, attackLibrary, rules);
    }

    public NpcAdvancedCombat withEngagementRadius(float engagementRadius) {
        return new NpcAdvancedCombat(enabled, engagementRadius, attackLibrary, rules);
    }

    public NpcAdvancedCombat withRules(List<NpcCombatRule> rules) {
        return new NpcAdvancedCombat(enabled, engagementRadius, attackLibrary, rules);
    }

    public NpcAdvancedCombat withAttackLibrary(List<NpcCombatAttack> attackLibrary) {
        return new NpcAdvancedCombat(enabled, engagementRadius, attackLibrary, rules);
    }

    // The attack a USE_ATTACK action names, or null when the library has no such entry (a soft warning).
    public NpcCombatAttack attackByName(String name) {
        for (NpcCombatAttack attack : attackLibrary) {
            if (attack.name().equals(name)) {
                return attack;
            }
        }
        return null;
    }

    public boolean isValid() {
        if (engagementRadius < 1.0F || engagementRadius > NpcValidation.MAX_ENGAGEMENT_RADIUS) {
            return false;
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (NpcCombatAttack attack : attackLibrary) {
            if (!attack.isValid() || !names.add(attack.name())) {
                return false;
            }
        }
        for (NpcCombatRule rule : rules) {
            if (!rule.isValid()) {
                return false;
            }
        }
        return true;
    }
}
