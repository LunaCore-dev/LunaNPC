package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// The order a patrol NPC visits its route's points: LOOP runs last-to-first and starts over, PING_PONG
// turns around at each end, RANDOM picks any other point next. New constants must be appended — the
// stream codec sends ordinals.
public enum NpcPatrolMode implements StringRepresentable {
    LOOP("loop"),
    PING_PONG("ping_pong"),
    RANDOM("random");

    public static final com.mojang.serialization.Codec<NpcPatrolMode> CODEC =
            StringRepresentable.fromEnum(NpcPatrolMode::values);
    public static final StreamCodec<ByteBuf, NpcPatrolMode> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], value -> (byte) value.ordinal());

    private final String id;

    NpcPatrolMode(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
