package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// The order a bodyguard's hirer last gave it: FOLLOW (guard and follow), STAY (hold this spot and defend
// from it) or HOME (walk back to its home and wait there). New constants must be appended — the stream
// codec sends ordinals.
public enum NpcBodyguardMode implements StringRepresentable {
    FOLLOW("follow"),
    STAY("stay"),
    HOME("home");

    public static final com.mojang.serialization.Codec<NpcBodyguardMode> CODEC =
            StringRepresentable.fromEnum(NpcBodyguardMode::values);
    public static final StreamCodec<ByteBuf, NpcBodyguardMode> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], value -> (byte) value.ordinal());

    private final String id;

    NpcBodyguardMode(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
