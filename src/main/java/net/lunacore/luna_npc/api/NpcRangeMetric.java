package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// What a range-switch rule measures. SELF_HP is the NPC's own current health (absolute); TARGET_HP_PERCENT
// is the target's health as a percent of its own max (percent because a creator can't know every mob's
// max HP); DISTANCE is the NPC-to-target distance in blocks; ENEMIES_IN_RADIUS counts the NPC's valid
// targets within the rule's radius; OTHERWISE always passes and ignores the comparator/value/radius — an
// "else" placed in the rule list so the fallback range reads as just another (last) rule. Only
// ENEMIES_IN_RADIUS queries the world. New constants must be appended — the stream codec sends the ordinal.
public enum NpcRangeMetric implements StringRepresentable {
    SELF_HP("self_hp"),
    TARGET_HP_PERCENT("target_hp_percent"),
    DISTANCE("distance"),
    ENEMIES_IN_RADIUS("enemies_in_radius"),
    OTHERWISE("otherwise");

    public static final com.mojang.serialization.Codec<NpcRangeMetric> CODEC =
            StringRepresentable.fromEnum(NpcRangeMetric::values);
    public static final StreamCodec<ByteBuf, NpcRangeMetric> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], metric -> (byte) metric.ordinal());

    private final String id;

    NpcRangeMetric(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
