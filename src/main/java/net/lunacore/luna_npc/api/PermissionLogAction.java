package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// One audit-log action kind. category() groups actions for the log's filter tabs. New constants must be
// appended — the stream codec sends the ordinal.
public enum PermissionLogAction implements StringRepresentable {
    NPC_CREATED("npc_created", "npcs"),
    NPC_EDITED("npc_edited", "npcs"),
    NPC_SPAWNED("npc_spawned", "npcs"),
    NPC_REMOVED("npc_removed", "npcs"),
    ALLIANCES_EDITED("alliances_edited", "alliances"),
    ZONES_EDITED("zones_edited", "zones"),
    PERMISSION_GRANTED("permission_granted", "permissions"),
    PERMISSION_REVOKED("permission_revoked", "permissions"),
    ROUTES_EDITED("routes_edited", "routes"),
    SKIN_DOWNLOADED("skin_downloaded", "npcs"),
    SKIN_EDITED("skin_edited", "npcs"),
    SKIN_DELETED("skin_deleted", "npcs");

    public static final com.mojang.serialization.Codec<PermissionLogAction> CODEC =
            StringRepresentable.fromEnum(PermissionLogAction::values);
    public static final StreamCodec<ByteBuf, PermissionLogAction> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], action -> (byte) action.ordinal());

    private final String id;
    private final String category;

    PermissionLogAction(String id, String category) {
        this.id = id;
        this.category = category;
    }

    public String category() {
        return category;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
