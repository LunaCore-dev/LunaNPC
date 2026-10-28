package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// The kind of quest objective: kill a mob type, bring items to the NPC at turn-in, kill players
// (targetId empty = any player), or enter a biome/structure/dimension named by targetId (these
// complete on first entry, so amount is always 1). New constants must be appended — the stream
// codec sends ordinals.
public enum NpcObjectiveType implements StringRepresentable {
    KILL_MOB("kill_mob"),
    COLLECT_ITEMS("collect_items"),
    KILL_PLAYERS("kill_players"),
    ENTER_BIOME("enter_biome"),
    ENTER_STRUCTURE("enter_structure"),
    ENTER_DIMENSION("enter_dimension");

    public static final com.mojang.serialization.Codec<NpcObjectiveType> CODEC =
            StringRepresentable.fromEnum(NpcObjectiveType::values);
    public static final StreamCodec<ByteBuf, NpcObjectiveType> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], type -> (byte) type.ordinal());

    private final String id;

    NpcObjectiveType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public boolean isEnterObjective() {
        return this == ENTER_BIOME || this == ENTER_STRUCTURE || this == ENTER_DIMENSION;
    }
}
