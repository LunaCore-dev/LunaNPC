package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// Who a bodyguard picks to protect: the closest of its protect targets (TARGETS) or whoever hires it
// (HIRE). Either way it locks onto one protectee until it gives up. New constants must be appended — the
// stream codec sends ordinals.
public enum NpcBodyguardSource implements StringRepresentable {
    TARGETS("targets"),
    HIRE("hire");

    public static final com.mojang.serialization.Codec<NpcBodyguardSource> CODEC =
            StringRepresentable.fromEnum(NpcBodyguardSource::values);
    public static final StreamCodec<ByteBuf, NpcBodyguardSource> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], value -> (byte) value.ordinal());

    private final String id;

    NpcBodyguardSource(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
