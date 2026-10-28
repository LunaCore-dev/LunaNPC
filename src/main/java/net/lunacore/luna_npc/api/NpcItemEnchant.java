package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// One enchantment applied to an equipped item: the enchantment id and its level. Only vanilla
// enchantments valid for the item are offered, but the level may run up to MAX_LEVEL — past the
// vanilla maximum on purpose (the creator owns balance, per CLAUDE.md).
public record NpcItemEnchant(String enchantId, int level) {

    public static final int MAX_LEVEL = 10;

    public static final Codec<NpcItemEnchant> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(NpcItemEnchant::enchantId),
            Codec.INT.optionalFieldOf("level", 1).forGetter(NpcItemEnchant::level)
    ).apply(instance, NpcItemEnchant::new));

    public static final StreamCodec<ByteBuf, NpcItemEnchant> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcItemEnchant::enchantId,
            ByteBufCodecs.VAR_INT, NpcItemEnchant::level,
            NpcItemEnchant::new);

    public boolean isValid() {
        return enchantId != null && !enchantId.isBlank() && level >= 1 && level <= MAX_LEVEL;
    }

    // Additive convenience copies for scripting/dev use: each returns a new record via the
    // canonical constructor with the named field changed.
    public NpcItemEnchant withEnchantId(String enchantId) {
        return new NpcItemEnchant(enchantId, level);
    }

    public NpcItemEnchant withLevel(int level) {
        return new NpcItemEnchant(enchantId, level);
    }
}
