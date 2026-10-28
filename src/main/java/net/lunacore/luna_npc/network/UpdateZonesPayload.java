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

// Client -> server: the full edited set of named zones (add / rename / delete), committed from the
// Create Zone screen or the Manage Zones screen.
public record UpdateZonesPayload(List<NpcZone> zones) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateZonesPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "update_zones"));

    public static final StreamCodec<ByteBuf, UpdateZonesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, NpcZone.STREAM_CODEC), UpdateZonesPayload::zones,
            UpdateZonesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
