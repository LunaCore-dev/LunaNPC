package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.NpcAlliance;

// Client -> server: the full edited alliance set from the Edit Alliances screen. The server
// validates it, replaces its state, then broadcasts the fresh snapshot to everyone.
public record UpdateAlliancesPayload(List<NpcAlliance> alliances) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateAlliancesPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "update_alliances"));

    public static final StreamCodec<ByteBuf, UpdateAlliancesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, NpcAlliance.STREAM_CODEC), UpdateAlliancesPayload::alliances,
            UpdateAlliancesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
