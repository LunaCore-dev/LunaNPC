package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// Who a TARGETS bodyguard may pick to protect: specific players, members of alliances (an alliance that
// matches all players covers every player; the taxonomy covers vanilla mob types) and LunaNPC
// definitions by lunaId. It locks onto the closest match within its range.
public record NpcProtectTargets(List<NpcPlayerRef> players, List<String> allianceIds, List<Long> npcLunaIds) {

    public NpcProtectTargets {
        players = List.copyOf(players);
        allianceIds = List.copyOf(allianceIds);
        npcLunaIds = List.copyOf(npcLunaIds);
    }

    public static final Codec<NpcProtectTargets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcPlayerRef.CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(NpcProtectTargets::players),
            Codec.STRING.listOf().optionalFieldOf("alliances", List.of()).forGetter(NpcProtectTargets::allianceIds),
            Codec.LONG.listOf().optionalFieldOf("npcs", List.of()).forGetter(NpcProtectTargets::npcLunaIds)
    ).apply(instance, NpcProtectTargets::new));

    public static final StreamCodec<ByteBuf, NpcProtectTargets> STREAM_CODEC = StreamCodec.composite(
            NpcPlayerRef.STREAM_CODEC.apply(ByteBufCodecs.list()), NpcProtectTargets::players,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), NpcProtectTargets::allianceIds,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), NpcProtectTargets::npcLunaIds,
            NpcProtectTargets::new);

    public static NpcProtectTargets none() {
        return new NpcProtectTargets(List.of(), List.of(), List.of());
    }

    public static NpcProtectTargets npcs(Long... lunaIds) {
        return new NpcProtectTargets(List.of(), List.of(), List.of(lunaIds));
    }

    public static NpcProtectTargets alliances(String... allianceIds) {
        return new NpcProtectTargets(List.of(), List.of(allianceIds), List.of());
    }

    public boolean isEmpty() {
        return players.isEmpty() && allianceIds.isEmpty() && npcLunaIds.isEmpty();
    }

    public NpcProtectTargets withPlayers(List<NpcPlayerRef> newPlayers) {
        return new NpcProtectTargets(newPlayers, allianceIds, npcLunaIds);
    }

    public NpcProtectTargets withAlliances(List<String> newAllianceIds) {
        return new NpcProtectTargets(players, newAllianceIds, npcLunaIds);
    }

    public NpcProtectTargets withNpcs(List<Long> newLunaIds) {
        return new NpcProtectTargets(players, allianceIds, newLunaIds);
    }

    public boolean isValid() {
        return players.size() <= NpcValidation.MAX_BODYGUARD_PROTECTED
                && allianceIds.size() <= NpcValidation.MAX_BODYGUARD_PROTECTED
                && npcLunaIds.size() <= NpcValidation.MAX_BODYGUARD_PROTECTED;
    }
}
