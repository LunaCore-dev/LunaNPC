package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// Who shares a trade's limited stock: each player has their own pool, each spawned NPC instance has
// one, or one world-wide pool is shared by everyone at every instance of the definition.
public enum NpcStockScope implements StringRepresentable {
    PER_PLAYER("per_player"),
    PER_NPC("per_npc"),
    PER_WORLD("per_world");

    public static final com.mojang.serialization.Codec<NpcStockScope> CODEC =
            StringRepresentable.fromEnum(NpcStockScope::values);
    public static final StreamCodec<ByteBuf, NpcStockScope> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], scope -> (byte) scope.ordinal());

    private final String id;

    NpcStockScope(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
