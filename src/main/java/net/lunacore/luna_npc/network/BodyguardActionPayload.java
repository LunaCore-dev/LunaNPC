package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Client -> server: a button on a bodyguard's menu — hire it, or (hirer only) give it an order or dismiss it.
// The server re-checks the distance, the hirer and the price.
public record BodyguardActionPayload(int npcEntityId, Action action) implements CustomPacketPayload {

    // New constants must be appended — the codec sends ordinals.
    public enum Action { HIRE, FOLLOW, STAY, HOME, DISMISS }

    public static final CustomPacketPayload.Type<BodyguardActionPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "bodyguard_action"));

    public static final StreamCodec<ByteBuf, BodyguardActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BodyguardActionPayload::npcEntityId,
            ByteBufCodecs.BYTE.map(b -> Action.values()[b], action -> (byte) action.ordinal()),
            BodyguardActionPayload::action,
            BodyguardActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
