package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// Where inside a zone (or, as the per-NPC master, inside a biome/structure) an NPC may appear:
// anywhere in the vertical range, only at the exposed surface (open sky above), or only underground
// (caves — sky blocked). The build-safety heuristic still applies on top of whichever is chosen.
public enum SurfaceMode implements StringRepresentable {
    ANYWHERE("anywhere"),
    SURFACE_ONLY("surface"),
    CAVES_ONLY("caves");

    public static final com.mojang.serialization.Codec<SurfaceMode> CODEC =
            StringRepresentable.fromEnum(SurfaceMode::values);
    public static final StreamCodec<ByteBuf, SurfaceMode> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], mode -> (byte) mode.ordinal());

    private final String id;

    SurfaceMode(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
