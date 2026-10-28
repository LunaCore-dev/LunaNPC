package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// What an NPC does for a living. NONE (the default) means it isn't interactable at all; TRADING,
// QUESTS and DIALOG open a menu on right-click; FARMING works crops autonomously and has no
// player-facing menu; PATROL walks a saved route; BODYGUARD follows and defends (and opens its hire or
// owner menu on right-click). New constants must be appended — the stream codec sends ordinals.
public enum NpcJobType implements StringRepresentable {
    NONE("none"),
    TRADING("trading"),
    QUESTS("quests"),
    FARMING("farming"),
    DIALOG("dialog"),
    PATROL("patrol"),
    BODYGUARD("bodyguard");

    public static final com.mojang.serialization.Codec<NpcJobType> CODEC =
            StringRepresentable.fromEnum(NpcJobType::values);
    public static final StreamCodec<ByteBuf, NpcJobType> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], type -> (byte) type.ordinal());

    private final String id;

    NpcJobType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
