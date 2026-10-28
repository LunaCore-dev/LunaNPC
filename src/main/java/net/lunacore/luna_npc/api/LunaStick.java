package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// The admin/builder sticks whose in-game use is gated by StickPermissions. The ids match the item ids
// (item.luna_npc.<id> is the display name). The Manage Permissions stick is intentionally absent: it is
// OP-only and never itself grantable. New constants must be appended — the stream codec sends the ordinal.
public enum LunaStick implements StringRepresentable {
    CREATE_NPC("create_npc"),
    EDIT_NPC("edit_npc"),
    SPAWN_NPC("spawn_npc"),
    REMOVE_NPC("remove_npc"),
    EDIT_ALLIANCES("edit_alliances"),
    ZONE_WAND("zone_wand"),
    ROUTE_CREATOR("route_creator");

    public static final com.mojang.serialization.Codec<LunaStick> CODEC =
            StringRepresentable.fromEnum(LunaStick::values);
    public static final StreamCodec<ByteBuf, LunaStick> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], stick -> (byte) stick.ordinal());

    private final String id;

    LunaStick(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
