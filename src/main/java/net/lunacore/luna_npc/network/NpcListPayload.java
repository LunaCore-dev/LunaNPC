package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

public record NpcListPayload(boolean forSpawn, BlockPos clickedPos, List<NpcSummary> npcs)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NpcListPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "npc_list"));

    public static final StreamCodec<ByteBuf, NpcListPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, NpcListPayload::forSpawn,
            BlockPos.STREAM_CODEC, NpcListPayload::clickedPos,
            ByteBufCodecs.collection(ArrayList::new, NpcSummary.STREAM_CODEC), NpcListPayload::npcs,
            NpcListPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
