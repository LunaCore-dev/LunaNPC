package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.LunaStick;

// Server -> client: this player's own stick access, sent on join and whenever their grants change. The
// client uses it only to skip opening a screen it can't use (the server still enforces every mutation).
public record PlayerPermissionsPayload(boolean admin, List<LunaStick> allowed) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlayerPermissionsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "player_permissions"));

    public static final StreamCodec<ByteBuf, PlayerPermissionsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PlayerPermissionsPayload::admin,
            ByteBufCodecs.collection(ArrayList::new, LunaStick.STREAM_CODEC), PlayerPermissionsPayload::allowed,
            PlayerPermissionsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
