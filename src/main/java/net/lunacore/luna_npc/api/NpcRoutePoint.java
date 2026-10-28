package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// One stop on a patrol route. pos is the block the NPC stands in (one above the clicked ground). The NPC
// waits waitSeconds on arrival, facing lookYaw (degrees, Minecraft yaw) when look is on. run is the speed
// of the leg that leaves this point, used only when the route's speed is PER_POINT.
public record NpcRoutePoint(BlockPos pos, int waitSeconds, boolean look, float lookYaw, boolean run) {

    public static final Codec<NpcRoutePoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(NpcRoutePoint::pos),
            Codec.INT.optionalFieldOf("wait_seconds", 0).forGetter(NpcRoutePoint::waitSeconds),
            Codec.BOOL.optionalFieldOf("look", false).forGetter(NpcRoutePoint::look),
            Codec.FLOAT.optionalFieldOf("look_yaw", 0.0F).forGetter(NpcRoutePoint::lookYaw),
            Codec.BOOL.optionalFieldOf("run", false).forGetter(NpcRoutePoint::run)
    ).apply(instance, NpcRoutePoint::new));

    public static final StreamCodec<ByteBuf, NpcRoutePoint> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NpcRoutePoint::pos,
            ByteBufCodecs.VAR_INT, NpcRoutePoint::waitSeconds,
            ByteBufCodecs.BOOL, NpcRoutePoint::look,
            ByteBufCodecs.FLOAT, NpcRoutePoint::lookYaw,
            ByteBufCodecs.BOOL, NpcRoutePoint::run,
            NpcRoutePoint::new);

    public static NpcRoutePoint at(BlockPos pos) {
        return new NpcRoutePoint(pos.immutable(), 0, false, 0.0F, false);
    }

    public NpcRoutePoint withPos(BlockPos newPos) {
        return new NpcRoutePoint(newPos.immutable(), waitSeconds, look, lookYaw, run);
    }

    public NpcRoutePoint withWaitSeconds(int seconds) {
        return new NpcRoutePoint(pos, seconds, look, lookYaw, run);
    }

    public NpcRoutePoint withLook(boolean enabled, float yaw) {
        return new NpcRoutePoint(pos, waitSeconds, enabled, yaw, run);
    }

    public NpcRoutePoint withRun(boolean running) {
        return new NpcRoutePoint(pos, waitSeconds, look, lookYaw, running);
    }

    public boolean isValid() {
        return pos != null && waitSeconds >= 0 && waitSeconds <= NpcValidation.MAX_ROUTE_WAIT_SECONDS
                && Float.isFinite(lookYaw);
    }
}
