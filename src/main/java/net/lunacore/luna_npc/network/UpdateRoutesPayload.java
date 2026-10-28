package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.NpcRoute;

// Client -> server: the full edited set of patrol routes (add / rename / edit / delete), committed from
// the Route Creator screens.
public record UpdateRoutesPayload(List<NpcRoute> routes) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateRoutesPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "update_routes"));

    public static final StreamCodec<ByteBuf, UpdateRoutesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, NpcRoute.STREAM_CODEC), UpdateRoutesPayload::routes,
            UpdateRoutesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
