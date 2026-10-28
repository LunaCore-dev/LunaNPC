package net.lunacore.luna_npc.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.api.NpcDefinition;

// The combat sounds. The close/far "attack" sounds play on the NPC the moment it swings or fires;
// the close/far "impact" sounds play where the blow or shot lands. Empty means silent.
public record NpcSoundSettings(String hurtSoundId, String dieSoundId, String ambientSoundId, String aggroSoundId,
                               String combatCloseSoundId, String combatFarSoundId,
                               String closeImpactSoundId, String farImpactSoundId,
                               int ambientIntervalSeconds) {

    public static final Codec<NpcSoundSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("hurt_sound").forGetter(NpcSoundSettings::hurtSoundId),
            Codec.STRING.fieldOf("die_sound").forGetter(NpcSoundSettings::dieSoundId),
            Codec.STRING.fieldOf("ambient_sound").forGetter(NpcSoundSettings::ambientSoundId),
            Codec.STRING.fieldOf("aggro_sound").forGetter(NpcSoundSettings::aggroSoundId),
            Codec.STRING.optionalFieldOf("combat_close_sound", NpcDefaults.COMBAT_CLOSE_SOUND_ID)
                    .forGetter(NpcSoundSettings::combatCloseSoundId),
            Codec.STRING.optionalFieldOf("combat_far_sound", NpcDefaults.COMBAT_FAR_SOUND_ID)
                    .forGetter(NpcSoundSettings::combatFarSoundId),
            Codec.STRING.optionalFieldOf("close_impact_sound", NpcDefaults.CLOSE_IMPACT_SOUND_ID)
                    .forGetter(NpcSoundSettings::closeImpactSoundId),
            Codec.STRING.optionalFieldOf("far_impact_sound", NpcDefaults.FAR_IMPACT_SOUND_ID)
                    .forGetter(NpcSoundSettings::farImpactSoundId),
            Codec.INT.fieldOf("ambient_interval_seconds").forGetter(NpcSoundSettings::ambientIntervalSeconds)
    ).apply(instance, NpcSoundSettings::new));

    public static final StreamCodec<ByteBuf, NpcSoundSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcSoundSettings::hurtSoundId,
            ByteBufCodecs.STRING_UTF8, NpcSoundSettings::dieSoundId,
            ByteBufCodecs.STRING_UTF8, NpcSoundSettings::ambientSoundId,
            ByteBufCodecs.STRING_UTF8, NpcSoundSettings::aggroSoundId,
            ByteBufCodecs.STRING_UTF8, NpcSoundSettings::combatCloseSoundId,
            ByteBufCodecs.STRING_UTF8, NpcSoundSettings::combatFarSoundId,
            ByteBufCodecs.STRING_UTF8, NpcSoundSettings::closeImpactSoundId,
            ByteBufCodecs.STRING_UTF8, NpcSoundSettings::farImpactSoundId,
            ByteBufCodecs.VAR_INT, NpcSoundSettings::ambientIntervalSeconds,
            NpcSoundSettings::new);

    public static NpcSoundSettings of(NpcDefinition npc) {
        return new NpcSoundSettings(npc.hurtSoundId(), npc.dieSoundId(), npc.ambientSoundId(), npc.aggroSoundId(),
                npc.combatCloseSoundId(), npc.combatFarSoundId(), npc.closeImpactSoundId(), npc.farImpactSoundId(),
                npc.ambientIntervalSeconds());
    }

    public static NpcSoundSettings defaults() {
        return new NpcSoundSettings(NpcDefaults.HURT_SOUND_ID, NpcDefaults.DIE_SOUND_ID,
                NpcDefaults.AMBIENT_SOUND_ID, NpcDefaults.AGGRO_SOUND_ID,
                NpcDefaults.COMBAT_CLOSE_SOUND_ID, NpcDefaults.COMBAT_FAR_SOUND_ID,
                NpcDefaults.CLOSE_IMPACT_SOUND_ID, NpcDefaults.FAR_IMPACT_SOUND_ID,
                NpcDefaults.AMBIENT_INTERVAL_SECONDS);
    }
}
