package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// What a bodyguard does when it gives up its protectee (see NpcBodyguardRelease): stay where it is, walk
// back home, or despawn. A guard that stayed or got home looks for (or waits for) its next protectee.
// New constants must be appended — the stream codec sends ordinals.
public enum NpcGiveUpAction implements StringRepresentable {
    STAY("stay"),
    GO_HOME("go_home"),
    DESPAWN("despawn");

    public static final com.mojang.serialization.Codec<NpcGiveUpAction> CODEC =
            StringRepresentable.fromEnum(NpcGiveUpAction::values);
    public static final StreamCodec<ByteBuf, NpcGiveUpAction> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], value -> (byte) value.ordinal());

    private final String id;

    NpcGiveUpAction(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
