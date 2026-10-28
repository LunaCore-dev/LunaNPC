package net.lunacore.luna_npc.api;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// Slow health regeneration. Each condition is an independent rule carrying its own gate, interval and
// heal amount; the rules fire on their own timers whenever their gate passes. An empty list means no
// regen even while enabled.
public record NpcRegenSettings(boolean enabled, List<NpcRegenCondition> conditions) {

    public NpcRegenSettings {
        conditions = List.copyOf(conditions);
    }

    public static final Codec<NpcRegenSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(NpcRegenSettings::enabled),
            NpcRegenCondition.CODEC.listOf().optionalFieldOf("conditions", List.of())
                    .forGetter(NpcRegenSettings::conditions)
    ).apply(instance, NpcRegenSettings::new));

    public static final StreamCodec<ByteBuf, NpcRegenSettings> STREAM_CODEC = StreamCodec.of(
            (buffer, settings) -> {
                ByteBufCodecs.BOOL.encode(buffer, settings.enabled());
                NpcRegenCondition.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, settings.conditions());
            },
            buffer -> new NpcRegenSettings(
                    ByteBufCodecs.BOOL.decode(buffer),
                    NpcRegenCondition.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer)));

    public static NpcRegenSettings defaults() {
        return new NpcRegenSettings(false, List.of());
    }

    public NpcRegenSettings withEnabled(boolean enabled) {
        return new NpcRegenSettings(enabled, conditions);
    }

    public NpcRegenSettings withConditions(List<NpcRegenCondition> conditions) {
        return new NpcRegenSettings(enabled, conditions);
    }

    public boolean isValid() {
        for (NpcRegenCondition condition : conditions) {
            if (!condition.isValid()) {
                return false;
            }
        }
        return true;
    }
}
