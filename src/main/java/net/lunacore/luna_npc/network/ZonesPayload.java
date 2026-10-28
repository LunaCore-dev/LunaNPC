package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.NpcZone;

// Server -> client snapshot of every named zone, sent on join and whenever the set changes, so the
// Create/Manage Zone screens and the Spawn tab always render the live zones.
public record ZonesPayload(List<NpcZone> zones) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ZonesPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "zones"));

    public static final StreamCodec<ByteBuf, ZonesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, NpcZone.STREAM_CODEC), ZonesPayload::zones,
            ZonesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
