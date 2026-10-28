package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// How an NPC moves while an advanced-combat rule is active. New constants must be appended — the stream
// codec sends the ordinal.
public enum NpcNavMode implements StringRepresentable {
    GROUND("ground"),
    WATER("water"),
    FLY("fly");

    public static final com.mojang.serialization.Codec<NpcNavMode> CODEC =
            StringRepresentable.fromEnum(NpcNavMode::values);
    public static final StreamCodec<ByteBuf, NpcNavMode> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], mode -> (byte) mode.ordinal());

    private final String id;

    NpcNavMode(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
