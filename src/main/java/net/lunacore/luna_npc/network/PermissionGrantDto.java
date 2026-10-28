package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.api.LunaStick;

// One stick's grant set for the Manage Permissions screen: the player UUIDs and the alliance ids allowed to
// use it. Alliance display names come from the client's already-synced alliance list.
public record PermissionGrantDto(LunaStick stick, List<UUID> players, List<String> alliances) {

    public static final StreamCodec<ByteBuf, PermissionGrantDto> STREAM_CODEC = StreamCodec.composite(
            LunaStick.STREAM_CODEC, PermissionGrantDto::stick,
            ByteBufCodecs.collection(ArrayList::new, UUIDUtil.STREAM_CODEC), PermissionGrantDto::players,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), PermissionGrantDto::alliances,
            PermissionGrantDto::new);
}
