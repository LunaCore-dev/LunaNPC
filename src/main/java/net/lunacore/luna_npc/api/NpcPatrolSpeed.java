package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// Where a patrol NPC takes its speed from: the route's own walk/run setting, or an override that ignores
// it and always walks or always runs. New constants must be appended — the stream codec sends ordinals.
public enum NpcPatrolSpeed implements StringRepresentable {
    ROUTE("route"),
    WALK("walk"),
    RUN("run");

    public static final com.mojang.serialization.Codec<NpcPatrolSpeed> CODEC =
            StringRepresentable.fromEnum(NpcPatrolSpeed::values);
    public static final StreamCodec<ByteBuf, NpcPatrolSpeed> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], value -> (byte) value.ordinal());

    private final String id;

    NpcPatrolSpeed(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
