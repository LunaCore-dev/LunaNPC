package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// One weighted patrol variant: which saved route (by id) the NPC walks, in which order (mode), and whether
// it keeps the route's own walk/run speeds or overrides them. Weights across the patrol sub-types must
// total exactly 100, so spawned copies of one NPC can be spread over several routes.
public record NpcPatrolSubType(int weightPercent, String routeId, NpcPatrolMode mode, NpcPatrolSpeed speed) {

    public static final Codec<NpcPatrolSubType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("weight", 0).forGetter(NpcPatrolSubType::weightPercent),
            Codec.STRING.optionalFieldOf("route", "").forGetter(NpcPatrolSubType::routeId),
            NpcPatrolMode.CODEC.optionalFieldOf("mode", NpcPatrolMode.LOOP).forGetter(NpcPatrolSubType::mode),
            NpcPatrolSpeed.CODEC.optionalFieldOf("speed", NpcPatrolSpeed.ROUTE).forGetter(NpcPatrolSubType::speed)
    ).apply(instance, NpcPatrolSubType::new));

    public static final StreamCodec<ByteBuf, NpcPatrolSubType> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcPatrolSubType::weightPercent,
            ByteBufCodecs.STRING_UTF8, NpcPatrolSubType::routeId,
            NpcPatrolMode.STREAM_CODEC, NpcPatrolSubType::mode,
            NpcPatrolSpeed.STREAM_CODEC, NpcPatrolSubType::speed,
            NpcPatrolSubType::new);

    public static NpcPatrolSubType of(String routeId) {
        return new NpcPatrolSubType(100, routeId, NpcPatrolMode.LOOP, NpcPatrolSpeed.ROUTE);
    }

    // The leg leaving point index is run: the override when there is one, otherwise the route's setting.
    public boolean runsFrom(NpcRoute route, int index) {
        return switch (speed) {
            case WALK -> false;
            case RUN -> true;
            case ROUTE -> route.runsFrom(index);
        };
    }

    public boolean isValid() {
        return weightPercent >= 0 && weightPercent <= 100 && routeId != null && mode != null && speed != null;
    }
}
