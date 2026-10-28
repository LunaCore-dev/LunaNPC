package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Client -> server: fetch a newest-first window of the audit log as the admin scrolls. `offset` counts back
// from the newest matching entry; `category` filters by action group (blank = all). OP re-checked.
public record RequestPermissionLogPayload(int offset, String category) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestPermissionLogPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "request_permission_log"));

    public static final StreamCodec<ByteBuf, RequestPermissionLogPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RequestPermissionLogPayload::offset,
            ByteBufCodecs.STRING_UTF8, RequestPermissionLogPayload::category,
            RequestPermissionLogPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
