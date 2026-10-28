package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Client -> server: the Manage Permissions stick was used; asks the server (which re-checks OP) to open the
// manager by replying with a PermissionsStatePayload.
public record RequestPermissionsPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestPermissionsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "request_permissions"));

    public static final StreamCodec<ByteBuf, RequestPermissionsPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestPermissionsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
