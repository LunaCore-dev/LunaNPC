package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// The geometry a placement spreads its points over. New constants must be appended — the stream codec
// sends the ordinal.
public enum NpcPlacementShape implements StringRepresentable {
    AT_CENTRE("at_centre"),
    CARDINAL_4("cardinal_4"),
    STAR_8("star_8"),
    RING("ring"),
    LINE_TO_TARGET("line_to_target"),
    ARC_TO_TARGET("arc_to_target"),
    RANDOM_IN_AREA("random_in_area"),
    GRID("grid");

    public static final com.mojang.serialization.Codec<NpcPlacementShape> CODEC =
            StringRepresentable.fromEnum(NpcPlacementShape::values);
    public static final StreamCodec<ByteBuf, NpcPlacementShape> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], shape -> (byte) shape.ordinal());

    private final String id;

    NpcPlacementShape(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
