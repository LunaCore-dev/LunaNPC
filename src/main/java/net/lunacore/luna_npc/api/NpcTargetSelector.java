package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// Which entities an action or attack applies to. New constants must be appended — the stream codec sends
// the ordinal.
public enum NpcTargetSelector implements StringRepresentable {
    PRIMARY("primary"),
    ALL_IN_RANGE("all_in_range"),
    EACH("each"),
    NEAREST("nearest"),
    FARTHEST("farthest"),
    LOWEST_HP("lowest_hp"),
    HIGHEST_DAMAGE("highest_damage"),
    RANDOM("random"),
    ALL_PLAYERS("all_players"),
    SELF("self");

    public static final com.mojang.serialization.Codec<NpcTargetSelector> CODEC =
            StringRepresentable.fromEnum(NpcTargetSelector::values);
    public static final StreamCodec<ByteBuf, NpcTargetSelector> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], selector -> (byte) selector.ordinal());

    private final String id;

    NpcTargetSelector(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
