package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.LunaStick;

// Client -> server: one grant edit from the Manage Permissions screen. `alliance` picks which grant set;
// `grant` toggles on/off. `target` is an alliance id, or (for a player) a UUID or a username the server
// resolves. The server re-checks OP before applying.
public record UpdatePermissionsPayload(LunaStick stick, boolean alliance, boolean grant,
                                       String target) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdatePermissionsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "update_permissions"));

    public static final StreamCodec<ByteBuf, UpdatePermissionsPayload> STREAM_CODEC = StreamCodec.composite(
            LunaStick.STREAM_CODEC, UpdatePermissionsPayload::stick,
            ByteBufCodecs.BOOL, UpdatePermissionsPayload::alliance,
            ByteBufCodecs.BOOL, UpdatePermissionsPayload::grant,
            ByteBufCodecs.STRING_UTF8, UpdatePermissionsPayload::target,
            UpdatePermissionsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
