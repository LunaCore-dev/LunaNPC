package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcBossBarSettings;
import net.lunacore.luna_npc.api.NpcCombatSettings;
import net.lunacore.luna_npc.api.NpcDropSettings;
import net.lunacore.luna_npc.api.NpcEquipmentSettings;
import net.lunacore.luna_npc.api.NpcJobSettings;
import net.lunacore.luna_npc.api.NpcEditAccess;
import net.lunacore.luna_npc.api.NpcModelSettings;
import net.lunacore.luna_npc.api.NpcMovementSettings;
import net.lunacore.luna_npc.npc.NpcSoundSettings;
import net.lunacore.luna_npc.api.NpcSpawnSettings;
import net.lunacore.luna_npc.api.NpcTraitsSettings;

public record NpcSummary(long lunaId, String name, boolean nameTagShown, int health,
                         NpcSoundSettings sounds, NpcModelSettings model,
                         String tagColor, String category,
                         NpcEquipmentSettings equipment, NpcDropSettings dropSettings,
                         NpcMovementSettings movement, NpcBossBarSettings bossBar,
                         NpcTraitsSettings traits, NpcCombatSettings combat,
                         java.util.List<String> allianceIds,
                         NpcSpawnSettings spawn, NpcJobSettings jobs, NpcEditAccess editAccess,
                         boolean favorite) {

    private static final StreamCodec<ByteBuf, java.util.List<String>> ALLIANCES =
            ByteBufCodecs.collection(java.util.ArrayList::new, ByteBufCodecs.STRING_UTF8);

    // Manual codec: composite() supports at most 12 fields and the summary has outgrown it.
    public static final StreamCodec<ByteBuf, NpcSummary> STREAM_CODEC = StreamCodec.of(
            (buffer, summary) -> {
                ByteBufCodecs.VAR_LONG.encode(buffer, summary.lunaId());
                ByteBufCodecs.STRING_UTF8.encode(buffer, summary.name());
                ByteBufCodecs.BOOL.encode(buffer, summary.nameTagShown());
                ByteBufCodecs.VAR_INT.encode(buffer, summary.health());
                NpcSoundSettings.STREAM_CODEC.encode(buffer, summary.sounds());
                NpcModelSettings.STREAM_CODEC.encode(buffer, summary.model());
                ByteBufCodecs.STRING_UTF8.encode(buffer, summary.tagColor());
                ByteBufCodecs.STRING_UTF8.encode(buffer, summary.category());
                NpcEquipmentSettings.STREAM_CODEC.encode(buffer, summary.equipment());
                NpcDropSettings.STREAM_CODEC.encode(buffer, summary.dropSettings());
                NpcMovementSettings.STREAM_CODEC.encode(buffer, summary.movement());
                NpcBossBarSettings.STREAM_CODEC.encode(buffer, summary.bossBar());
                NpcTraitsSettings.STREAM_CODEC.encode(buffer, summary.traits());
                NpcCombatSettings.STREAM_CODEC.encode(buffer, summary.combat());
                ALLIANCES.encode(buffer, summary.allianceIds());
                NpcSpawnSettings.STREAM_CODEC.encode(buffer, summary.spawn());
                NpcJobSettings.STREAM_CODEC.encode(buffer, summary.jobs());
                NpcEditAccess.STREAM_CODEC.encode(buffer, summary.editAccess());
                ByteBufCodecs.BOOL.encode(buffer, summary.favorite());
            },
            buffer -> new NpcSummary(
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    NpcSoundSettings.STREAM_CODEC.decode(buffer),
                    NpcModelSettings.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    NpcEquipmentSettings.STREAM_CODEC.decode(buffer),
                    NpcDropSettings.STREAM_CODEC.decode(buffer),
                    NpcMovementSettings.STREAM_CODEC.decode(buffer),
                    NpcBossBarSettings.STREAM_CODEC.decode(buffer),
                    NpcTraitsSettings.STREAM_CODEC.decode(buffer),
                    NpcCombatSettings.STREAM_CODEC.decode(buffer),
                    ALLIANCES.decode(buffer),
                    NpcSpawnSettings.STREAM_CODEC.decode(buffer),
                    NpcJobSettings.STREAM_CODEC.decode(buffer),
                    NpcEditAccess.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer)));

    public static NpcSummary of(NpcDefinition npc, boolean favorite) {
        return new NpcSummary(npc.lunaId(), npc.name(), npc.isNameTagShown(), npc.health(),
                NpcSoundSettings.of(npc), npc.modelSettings(), npc.tagColor(), npc.category(),
                npc.equipment(), npc.dropSettings(), npc.movement(), npc.bossBar(), npc.traits(),
                npc.combat(), npc.allianceIds(), npc.spawn(), npc.jobs(), npc.editAccess(), favorite);
    }

    public NpcSummary withFavorite(boolean newFavorite) {
        return new NpcSummary(lunaId, name, nameTagShown, health, sounds, model, tagColor, category,
                equipment, dropSettings, movement, bossBar, traits, combat, allianceIds, spawn, jobs,
                editAccess, newFavorite);
    }
}
