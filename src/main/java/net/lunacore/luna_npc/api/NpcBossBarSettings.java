package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcValidation;

// The boss bar shows the NPC name unless a custom name is set ("" means use the NPC name).
public record NpcBossBarSettings(boolean enabled, String color, String customName) {

    public static final Codec<NpcBossBarSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(NpcBossBarSettings::enabled),
            Codec.STRING.optionalFieldOf("color", NpcDefaults.BOSS_BAR_COLOR).forGetter(NpcBossBarSettings::color),
            Codec.STRING.optionalFieldOf("custom_name", "").forGetter(NpcBossBarSettings::customName)
    ).apply(instance, NpcBossBarSettings::new));

    public static final StreamCodec<ByteBuf, NpcBossBarSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, NpcBossBarSettings::enabled,
            ByteBufCodecs.STRING_UTF8, NpcBossBarSettings::color,
            ByteBufCodecs.STRING_UTF8, NpcBossBarSettings::customName,
            NpcBossBarSettings::new);

    public static NpcBossBarSettings defaults() {
        return new NpcBossBarSettings(false, NpcDefaults.BOSS_BAR_COLOR, "");
    }

    // Additive convenience copies: each returns a new record with one field changed
    // via the canonical constructor, preserving immutability.
    public NpcBossBarSettings withEnabled(boolean enabled) {
        return new NpcBossBarSettings(enabled, color, customName);
    }

    public NpcBossBarSettings withColor(String color) {
        return new NpcBossBarSettings(enabled, color, customName);
    }

    public NpcBossBarSettings withCustomName(String customName) {
        return new NpcBossBarSettings(enabled, color, customName);
    }

    public boolean isValid() {
        return NpcValidation.isValidBossBarColor(color)
                && customName != null && customName.length() <= NpcValidation.MAX_BOSS_BAR_NAME_LENGTH;
    }
}
