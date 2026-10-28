package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcValidation;

// How an NPC roams on its own. Stroll rests for a random minSeconds..maxSeconds pause, walks to
// a random point within the radius, then rests again. Restless walks all the time: the next
// random point starts the moment the previous walk ends (the interval is unused). Static stands
// still. Ground prefers stroll, water and air prefer restless — that only picks the default,
// never limits the choice. minGoalDistance keeps wander goals at least that many blocks away
// (0 = no minimum); it can never exceed the radius.
public record NpcWanderSettings(String behavior, int minSeconds, int maxSeconds, int radius,
                                int minGoalDistance) {

    public static final String STATIC = "static";
    public static final String STROLL = "stroll";
    public static final String RESTLESS = "restless";

    public static final Codec<NpcWanderSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("behavior", STROLL).forGetter(NpcWanderSettings::behavior),
            Codec.INT.optionalFieldOf("min_seconds", NpcDefaults.WANDER_MIN_SECONDS)
                    .forGetter(NpcWanderSettings::minSeconds),
            Codec.INT.optionalFieldOf("max_seconds", NpcDefaults.WANDER_MAX_SECONDS)
                    .forGetter(NpcWanderSettings::maxSeconds),
            Codec.INT.optionalFieldOf("radius", NpcDefaults.WANDER_RADIUS)
                    .forGetter(NpcWanderSettings::radius),
            Codec.INT.optionalFieldOf("min_goal_distance", NpcDefaults.WANDER_MIN_GOAL_DISTANCE)
                    .forGetter(NpcWanderSettings::minGoalDistance)
    ).apply(instance, NpcWanderSettings::new));

    public static final StreamCodec<ByteBuf, NpcWanderSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcWanderSettings::behavior,
            ByteBufCodecs.VAR_INT, NpcWanderSettings::minSeconds,
            ByteBufCodecs.VAR_INT, NpcWanderSettings::maxSeconds,
            ByteBufCodecs.VAR_INT, NpcWanderSettings::radius,
            ByteBufCodecs.VAR_INT, NpcWanderSettings::minGoalDistance,
            NpcWanderSettings::new);

    public static NpcWanderSettings defaults() {
        return new NpcWanderSettings(STROLL, NpcDefaults.WANDER_MIN_SECONDS, NpcDefaults.WANDER_MAX_SECONDS,
                NpcDefaults.WANDER_RADIUS, NpcDefaults.WANDER_MIN_GOAL_DISTANCE);
    }

    public static String recommendedBehavior(String movementType) {
        return "ground".equals(movementType) ? STROLL : RESTLESS;
    }

    // Additive convenience copies for scripting/dev use: each returns a new record with a single
    // field changed via the canonical constructor, preserving immutability.
    public NpcWanderSettings withBehavior(String behavior) {
        return new NpcWanderSettings(behavior, minSeconds, maxSeconds, radius, minGoalDistance);
    }

    public NpcWanderSettings withMinSeconds(int minSeconds) {
        return new NpcWanderSettings(behavior, minSeconds, maxSeconds, radius, minGoalDistance);
    }

    public NpcWanderSettings withMaxSeconds(int maxSeconds) {
        return new NpcWanderSettings(behavior, minSeconds, maxSeconds, radius, minGoalDistance);
    }

    public NpcWanderSettings withRadius(int radius) {
        return new NpcWanderSettings(behavior, minSeconds, maxSeconds, radius, minGoalDistance);
    }

    public NpcWanderSettings withMinGoalDistance(int minGoalDistance) {
        return new NpcWanderSettings(behavior, minSeconds, maxSeconds, radius, minGoalDistance);
    }

    public boolean isValid() {
        return NpcValidation.WANDER_BEHAVIORS.contains(behavior)
                && minSeconds >= NpcValidation.MIN_WANDER_INTERVAL_SECONDS
                && maxSeconds >= minSeconds
                && maxSeconds <= NpcValidation.MAX_WANDER_INTERVAL_SECONDS
                && radius >= NpcValidation.MIN_WANDER_RADIUS
                && radius <= NpcValidation.MAX_WANDER_RADIUS
                && minGoalDistance >= 0
                && minGoalDistance <= radius;
    }
}
