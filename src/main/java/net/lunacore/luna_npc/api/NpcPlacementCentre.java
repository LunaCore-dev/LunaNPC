package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// Which point a placement pattern is anchored to. New constants must be appended — the stream codec sends
// the ordinal.
public enum NpcPlacementCentre implements StringRepresentable {
    SELF("self"),
    TARGET("target");

    public static final com.mojang.serialization.Codec<NpcPlacementCentre> CODEC =
            StringRepresentable.fromEnum(NpcPlacementCentre::values);
    public static final StreamCodec<ByteBuf, NpcPlacementCentre> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], centre -> (byte) centre.ordinal());

    private final String id;

    NpcPlacementCentre(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
