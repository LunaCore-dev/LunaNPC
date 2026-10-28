package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// How a quest completes: TURN_IN requires returning to the NPC (mandatory for collect objectives,
// which are consumed there); AUTO grants the reward in the field the moment the last kill lands.
public enum NpcQuestCompletion implements StringRepresentable {
    TURN_IN("turn_in"),
    AUTO("auto");

    public static final com.mojang.serialization.Codec<NpcQuestCompletion> CODEC =
            StringRepresentable.fromEnum(NpcQuestCompletion::values);
    public static final StreamCodec<ByteBuf, NpcQuestCompletion> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], completion -> (byte) completion.ordinal());

    private final String id;

    NpcQuestCompletion(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
