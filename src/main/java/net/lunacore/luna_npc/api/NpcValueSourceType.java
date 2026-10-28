package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// Where a combat condition reads a number from at runtime. New constants must be appended — the stream
// codec sends the ordinal.
public enum NpcValueSourceType implements StringRepresentable {
    CONSTANT("constant"),
    SELF_HP("self_hp"),
    SELF_HP_PERCENT("self_hp_percent"),
    TARGET_HP("target_hp"),
    TARGET_HP_PERCENT("target_hp_percent"),
    DISTANCE_TO_TARGET("distance_to_target"),
    HOSTILES_IN_RADIUS("hostiles_in_radius"),
    PLAYERS_IN_RADIUS("players_in_radius"),
    SUMMONS_ALIVE("summons_alive"),
    TIME_IN_COMBAT("time_in_combat"),
    TIME_SINCE_HIT("time_since_hit"),
    FLAG_VALUE("flag_value"),
    COUNTER_VALUE("counter_value"),
    RANDOM("random"),
    PLAYER_COUNT("player_count"),
    MISSING_HP_PERCENT("missing_hp_percent"),
    TARGET_IN_LINE_OF_SIGHT("target_in_line_of_sight");

    public static final com.mojang.serialization.Codec<NpcValueSourceType> CODEC =
            StringRepresentable.fromEnum(NpcValueSourceType::values);
    public static final StreamCodec<ByteBuf, NpcValueSourceType> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], type -> (byte) type.ordinal());

    private final String id;

    NpcValueSourceType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
