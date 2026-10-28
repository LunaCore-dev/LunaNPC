package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;
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

public record CreateNpcPayload(String name, boolean nameTagShown, int health,
                               NpcSoundSettings sounds, NpcModelSettings model,
                               String tagColor, String category,
                               NpcEquipmentSettings equipment, NpcDropSettings dropSettings,
                               NpcMovementSettings movement, NpcBossBarSettings bossBar,
                               NpcTraitsSettings traits, NpcCombatSettings combat,
                               java.util.List<String> allianceIds, NpcSpawnSettings spawn,
                               NpcJobSettings jobs, NpcEditAccess editAccess)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreateNpcPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "create_npc"));

    private static final StreamCodec<ByteBuf, java.util.List<String>> ALLIANCES =
            ByteBufCodecs.collection(java.util.ArrayList::new, ByteBufCodecs.STRING_UTF8);

    // Manual codec: composite() supports at most 12 fields and this payload has outgrown it.
    public static final StreamCodec<ByteBuf, CreateNpcPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.name());
                ByteBufCodecs.BOOL.encode(buffer, payload.nameTagShown());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.health());
                NpcSoundSettings.STREAM_CODEC.encode(buffer, payload.sounds());
                NpcModelSettings.STREAM_CODEC.encode(buffer, payload.model());
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.tagColor());
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.category());
                NpcEquipmentSettings.STREAM_CODEC.encode(buffer, payload.equipment());
                NpcDropSettings.STREAM_CODEC.encode(buffer, payload.dropSettings());
                NpcMovementSettings.STREAM_CODEC.encode(buffer, payload.movement());
                NpcBossBarSettings.STREAM_CODEC.encode(buffer, payload.bossBar());
                NpcTraitsSettings.STREAM_CODEC.encode(buffer, payload.traits());
                NpcCombatSettings.STREAM_CODEC.encode(buffer, payload.combat());
                ALLIANCES.encode(buffer, payload.allianceIds());
                NpcSpawnSettings.STREAM_CODEC.encode(buffer, payload.spawn());
                NpcJobSettings.STREAM_CODEC.encode(buffer, payload.jobs());
                NpcEditAccess.STREAM_CODEC.encode(buffer, payload.editAccess());
            },
            buffer -> new CreateNpcPayload(
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
                    NpcEditAccess.STREAM_CODEC.decode(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
