package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// How a combat condition compares its two value sources. EQUAL/NOT_EQUAL use a small epsilon because both
// sides are floats. New constants must be appended — the stream codec sends the ordinal.
public enum NpcCombatComparator implements StringRepresentable {
    LESS("less"),
    LESS_EQUAL("less_equal"),
    EQUAL("equal"),
    NOT_EQUAL("not_equal"),
    GREATER_EQUAL("greater_equal"),
    GREATER("greater");

    public static final com.mojang.serialization.Codec<NpcCombatComparator> CODEC =
            StringRepresentable.fromEnum(NpcCombatComparator::values);
    public static final StreamCodec<ByteBuf, NpcCombatComparator> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], comparator -> (byte) comparator.ordinal());

    private final String id;

    NpcCombatComparator(String id) {
        this.id = id;
    }

    public boolean test(float a, float b) {
        return switch (this) {
            case LESS -> a < b;
            case LESS_EQUAL -> a <= b;
            case EQUAL -> Math.abs(a - b) < 0.001f;
            case NOT_EQUAL -> Math.abs(a - b) >= 0.001f;
            case GREATER_EQUAL -> a >= b;
            case GREATER -> a > b;
        };
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
