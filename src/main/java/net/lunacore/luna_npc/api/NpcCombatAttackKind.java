package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// How a combat attack delivers its damage. New constants must be appended — the stream codec sends the
// ordinal.
public enum NpcCombatAttackKind implements StringRepresentable {
    MELEE("melee"),
    PROJECTILE("projectile"),
    PATTERN("pattern"),
    AOE("aoe"),
    BEAM("beam");

    public static final com.mojang.serialization.Codec<NpcCombatAttackKind> CODEC =
            StringRepresentable.fromEnum(NpcCombatAttackKind::values);
    public static final StreamCodec<ByteBuf, NpcCombatAttackKind> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], kind -> (byte) kind.ordinal());

    private final String id;

    NpcCombatAttackKind(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
