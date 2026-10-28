package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcValidation;

// The NPC's temperament — how it engages, across three independent reflexes: hunting enemies on
// sight, coming to a mate's defence, and fighting back when hit. Aggressive does all three;
// Defensive skips the hunt (defends and retaliates); Passive only defends itself; Fearful runs
// from any attacker. A mate is never turned on. The flee timer and distance only matter to
// Fearful.
public record NpcReactionSettings(String mode, int fleeSeconds, int fleeDistance) {

    public static final String AGGRESSIVE = "aggressive";
    public static final String DEFENSIVE = "defensive";
    public static final String PASSIVE = "passive";
    public static final String FEARFUL = "fearful";
    public static final java.util.List<String> MODES = java.util.List.of(AGGRESSIVE, DEFENSIVE, PASSIVE, FEARFUL);

    // Normalizes legacy ids (attack/ignore/flee) and anything unknown to a current mode.
    public NpcReactionSettings {
        mode = switch (mode == null ? "" : mode) {
            case AGGRESSIVE, DEFENSIVE, PASSIVE, FEARFUL -> mode;
            case "attack" -> AGGRESSIVE;
            case "ignore" -> PASSIVE;
            case "flee" -> FEARFUL;
            default -> AGGRESSIVE;
        };
    }

    public boolean huntsEnemies() {
        return AGGRESSIVE.equals(mode);
    }

    public boolean defendsMates() {
        return AGGRESSIVE.equals(mode) || DEFENSIVE.equals(mode);
    }

    public boolean fightsBack() {
        return !FEARFUL.equals(mode);
    }

    public boolean flees() {
        return FEARFUL.equals(mode);
    }

    public static final Codec<NpcReactionSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("mode", NpcDefaults.REACTION).forGetter(NpcReactionSettings::mode),
            Codec.INT.optionalFieldOf("flee_seconds", NpcDefaults.FLEE_SECONDS)
                    .forGetter(NpcReactionSettings::fleeSeconds),
            Codec.INT.optionalFieldOf("flee_distance", NpcDefaults.FLEE_DISTANCE)
                    .forGetter(NpcReactionSettings::fleeDistance)
    ).apply(instance, NpcReactionSettings::new));

    public static final StreamCodec<ByteBuf, NpcReactionSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcReactionSettings::mode,
            ByteBufCodecs.VAR_INT, NpcReactionSettings::fleeSeconds,
            ByteBufCodecs.VAR_INT, NpcReactionSettings::fleeDistance,
            NpcReactionSettings::new);

    public static NpcReactionSettings defaults() {
        return new NpcReactionSettings(NpcDefaults.REACTION, NpcDefaults.FLEE_SECONDS, NpcDefaults.FLEE_DISTANCE);
    }

    // Additive convenience copies for scripting/dev use: each returns a new record with a single
    // field changed via the canonical constructor, preserving immutability.
    public NpcReactionSettings withMode(String mode) {
        return new NpcReactionSettings(mode, fleeSeconds, fleeDistance);
    }

    // Type-safe temperament, an alternative to the string constants above.
    public NpcReactionSettings withMode(net.lunacore.luna_npc.api.ReactionMode mode) {
        return withMode(mode.id());
    }

    public NpcReactionSettings withFleeSeconds(int fleeSeconds) {
        return new NpcReactionSettings(mode, fleeSeconds, fleeDistance);
    }

    public NpcReactionSettings withFleeDistance(int fleeDistance) {
        return new NpcReactionSettings(mode, fleeSeconds, fleeDistance);
    }

    public boolean isValid() {
        return MODES.contains(mode)
                && fleeSeconds >= NpcValidation.MIN_FLEE_SECONDS && fleeSeconds <= NpcValidation.MAX_FLEE_SECONDS
                && fleeDistance >= NpcValidation.MIN_FLEE_DISTANCE && fleeDistance <= NpcValidation.MAX_FLEE_DISTANCE;
    }
}
