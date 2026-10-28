package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// Which combat system drives an NPC: the simple attack list or the advanced trigger/action program. New
// constants must be appended — the stream codec sends the ordinal.
public enum NpcCombatMode implements StringRepresentable {
    SIMPLE("simple"),
    ADVANCED("advanced");

    public static final com.mojang.serialization.Codec<NpcCombatMode> CODEC =
            StringRepresentable.fromEnum(NpcCombatMode::values);
    public static final StreamCodec<ByteBuf, NpcCombatMode> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], mode -> (byte) mode.ordinal());

    private final String id;

    NpcCombatMode(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
