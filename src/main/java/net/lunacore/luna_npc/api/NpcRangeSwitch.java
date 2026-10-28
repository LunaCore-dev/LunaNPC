package net.lunacore.luna_npc.api;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// Creator-controlled close/long switching. When enabled, the NPC picks its attack range each decision by
// walking rules in order and taking the first that passes; a rule whose metric is OTHERWISE always
// passes, so it acts as the "else" fallback (usually placed last). When disabled the NPC keeps its
// built-in distance-based auto-pick. If no rule passes at all, the NPC fights close.
public record NpcRangeSwitch(boolean enabled, List<NpcRangeRule> rules) {

    public NpcRangeSwitch {
        rules = List.copyOf(rules);
    }

    public static final Codec<NpcRangeSwitch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(NpcRangeSwitch::enabled),
            NpcRangeRule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(NpcRangeSwitch::rules)
    ).apply(instance, NpcRangeSwitch::new));

    public static final StreamCodec<ByteBuf, NpcRangeSwitch> STREAM_CODEC = StreamCodec.of(
            (buffer, settings) -> {
                ByteBufCodecs.BOOL.encode(buffer, settings.enabled());
                NpcRangeRule.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, settings.rules());
            },
            buffer -> new NpcRangeSwitch(
                    ByteBufCodecs.BOOL.decode(buffer),
                    NpcRangeRule.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer)));

    public static NpcRangeSwitch defaults() {
        return new NpcRangeSwitch(false, List.of());
    }

    public NpcRangeSwitch withEnabled(boolean enabled) {
        return new NpcRangeSwitch(enabled, rules);
    }

    public NpcRangeSwitch withRules(List<NpcRangeRule> rules) {
        return new NpcRangeSwitch(enabled, rules);
    }

    // Whether any rule reads the world (the only metric that does), so the runtime can skip the entity
    // scan entirely for the common HP/distance-only configuration.
    public boolean scansNearbyEntities() {
        for (NpcRangeRule rule : rules) {
            if (rule.metric() == NpcRangeMetric.ENEMIES_IN_RADIUS) {
                return true;
            }
        }
        return false;
    }

    public boolean isValid() {
        for (NpcRangeRule rule : rules) {
            if (!rule.isValid()) {
                return false;
            }
        }
        return true;
    }
}
