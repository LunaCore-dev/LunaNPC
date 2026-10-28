package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// A single gate on health regeneration; all of an NPC's conditions must pass for regen to advance. New
// constants must be appended — the stream codec sends ordinals.
public enum NpcRegenConditionType implements StringRepresentable {
    OUT_OF_COMBAT("out_of_combat"),
    NO_DAMAGE_SECONDS("no_damage_seconds"),
    HP_BELOW("hp_below"),
    HP_ABOVE("hp_above");

    public static final com.mojang.serialization.Codec<NpcRegenConditionType> CODEC =
            StringRepresentable.fromEnum(NpcRegenConditionType::values);
    public static final StreamCodec<ByteBuf, NpcRegenConditionType> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], type -> (byte) type.ordinal());

    private final String id;

    NpcRegenConditionType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
