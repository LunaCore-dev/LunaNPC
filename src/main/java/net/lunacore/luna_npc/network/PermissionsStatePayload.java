package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Server -> client: the full state the Manage Permissions screen renders — every gated stick's grants, the
// known-players list for the picker, and whether this is an offline-mode server (which shows the UUID
// warning). Sent only to an admin who opened the manager.
public record PermissionsStatePayload(boolean offlineMode, List<PermissionGrantDto> grants,
                                      List<KnownPlayer> knownPlayers) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PermissionsStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "permissions_state"));

    public static final StreamCodec<ByteBuf, PermissionsStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PermissionsStatePayload::offlineMode,
            ByteBufCodecs.collection(ArrayList::new, PermissionGrantDto.STREAM_CODEC), PermissionsStatePayload::grants,
            ByteBufCodecs.collection(ArrayList::new, KnownPlayer.STREAM_CODEC), PermissionsStatePayload::knownPlayers,
            PermissionsStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
