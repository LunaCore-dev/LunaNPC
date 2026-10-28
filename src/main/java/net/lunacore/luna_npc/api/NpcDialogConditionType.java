package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// Gate on a dialog answer choice: the player must have completed a quest, hold an item, or carry a
// story flag. A condition's negate flips it (quest NOT completed, flag NOT set). New constants must be
// appended — the stream codec sends ordinals.
public enum NpcDialogConditionType implements StringRepresentable {
    QUEST_COMPLETED("quest_completed"),
    HAS_ITEM("has_item"),
    FLAG_SET("flag_set");

    public static final com.mojang.serialization.Codec<NpcDialogConditionType> CODEC =
            StringRepresentable.fromEnum(NpcDialogConditionType::values);
    public static final StreamCodec<ByteBuf, NpcDialogConditionType> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], type -> (byte) type.ordinal());

    private final String id;

    NpcDialogConditionType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
