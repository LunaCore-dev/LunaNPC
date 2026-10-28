package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// How fast an NPC walks a patrol route: always its walk speed, always its run speed, or per point (each
// point's run flag sets the speed of the leg that leaves it). New constants must be appended — the stream
// codec sends ordinals.
public enum NpcRouteSpeed implements StringRepresentable {
    WALK("walk"),
    RUN("run"),
    PER_POINT("per_point");

    public static final com.mojang.serialization.Codec<NpcRouteSpeed> CODEC =
            StringRepresentable.fromEnum(NpcRouteSpeed::values);
    public static final StreamCodec<ByteBuf, NpcRouteSpeed> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], value -> (byte) value.ordinal());

    private final String id;

    NpcRouteSpeed(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
