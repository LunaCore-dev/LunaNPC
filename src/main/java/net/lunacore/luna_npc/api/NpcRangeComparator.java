package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// How a range-switch rule compares its metric to its threshold. New constants must be appended — the
// stream codec sends the ordinal.
public enum NpcRangeComparator implements StringRepresentable {
    AT_MOST("at_most"),
    AT_LEAST("at_least");

    public static final com.mojang.serialization.Codec<NpcRangeComparator> CODEC =
            StringRepresentable.fromEnum(NpcRangeComparator::values);
    public static final StreamCodec<ByteBuf, NpcRangeComparator> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], comparator -> (byte) comparator.ordinal());

    private final String id;

    NpcRangeComparator(String id) {
        this.id = id;
    }

    public boolean test(float metric, float threshold) {
        return this == AT_MOST ? metric <= threshold : metric >= threshold;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
