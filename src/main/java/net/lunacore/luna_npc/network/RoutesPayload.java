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

// Server -> client snapshot of every patrol route, sent on join and whenever the set changes, so the
// Route Creator screens, the patrol editor and the in-world route lines always show the live routes.
public record RoutesPayload(List<NpcRoute> routes) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RoutesPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "routes"));

    public static final StreamCodec<ByteBuf, RoutesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, NpcRoute.STREAM_CODEC), RoutesPayload::routes,
            RoutesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
