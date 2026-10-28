package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;

// Server -> client list of structure ids, sent on join. The structure registry is server-only (not
// synced like biomes), so the Spawn tab's structure picker relies on this instead of the client
// registry — which also keeps modded structures included.
public record StructuresPayload(List<String> structureIds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StructuresPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "structures"));

    public static final StreamCodec<ByteBuf, StructuresPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), StructuresPayload::structureIds,
            StructuresPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
