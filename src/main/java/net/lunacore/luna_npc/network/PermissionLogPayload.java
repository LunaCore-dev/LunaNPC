package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.PermissionLogEntry;

// Server -> client: one requested window of the audit log. `total` is the full count for this category so
// the client knows when it has reached the end; `category` and `offset` echo the request so a stale window
// for an old filter can be discarded. `actors` carries the stored skin of each actor in the window.
public record PermissionLogPayload(int offset, int total, String category, List<PermissionLogEntry> entries,
                                   List<ResolvableProfile> actors) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PermissionLogPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "permission_log"));

    public static final StreamCodec<ByteBuf, PermissionLogPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PermissionLogPayload::offset,
            ByteBufCodecs.VAR_INT, PermissionLogPayload::total,
            ByteBufCodecs.STRING_UTF8, PermissionLogPayload::category,
            ByteBufCodecs.collection(ArrayList::new, PermissionLogEntry.STREAM_CODEC), PermissionLogPayload::entries,
            ByteBufCodecs.collection(ArrayList::new, ResolvableProfile.STREAM_CODEC), PermissionLogPayload::actors,
            PermissionLogPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
