package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// The range an NPC fights at: CLOSE drives the melee attack, LONG drives the ranged attack. It is both
// what a range-switch rule selects and the default when no rule matches. New constants must be appended
// — the stream codec sends the ordinal.
public enum NpcAttackRange implements StringRepresentable {
    CLOSE("close"),
    LONG("long");

    public static final com.mojang.serialization.Codec<NpcAttackRange> CODEC =
            StringRepresentable.fromEnum(NpcAttackRange::values);
    public static final StreamCodec<ByteBuf, NpcAttackRange> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], range -> (byte) range.ordinal());

    private final String id;

    NpcAttackRange(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
