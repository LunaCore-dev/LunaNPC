package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// The arm width a downloaded player skin is drawn with: CLASSIC (Steve, 4 px) or SLIM (Alex, 3 px).
// New constants must be appended — the stream codec sends ordinals.
public enum NpcSkinArms implements StringRepresentable {
    CLASSIC("classic"),
    SLIM("slim");

    public static final com.mojang.serialization.Codec<NpcSkinArms> CODEC =
            StringRepresentable.fromEnum(NpcSkinArms::values);
    public static final StreamCodec<ByteBuf, NpcSkinArms> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], value -> (byte) value.ordinal());

    private final String id;

    NpcSkinArms(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
