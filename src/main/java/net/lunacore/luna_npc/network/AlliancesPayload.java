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

// Server -> client snapshot of every alliance, sent on join and whenever the set changes, so the
// Relationship tab and the Edit Alliances screen always render the live factions.
public record AlliancesPayload(List<NpcAlliance> alliances) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AlliancesPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "alliances"));

    public static final StreamCodec<ByteBuf, AlliancesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, NpcAlliance.STREAM_CODEC), AlliancesPayload::alliances,
            AlliancesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
