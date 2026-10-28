package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcValidation;

public record NpcDropSettings(int xpMin, int xpMax, List<NpcItemDrop> drops) {

    public NpcDropSettings {
        drops = List.copyOf(drops);
    }

    // A configured item drop: the grid slot it sits in (0-35), the item id and the drop chance in
    // percent (0.01-100, rolled independently per drop on death).
    public record NpcItemDrop(int slot, String itemId, float chancePercent) {

        public static final Codec<NpcItemDrop> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(NpcItemDrop::slot),
                Codec.STRING.fieldOf("item").forGetter(NpcItemDrop::itemId),
                Codec.FLOAT.fieldOf("chance_percent").forGetter(NpcItemDrop::chancePercent)
        ).apply(instance, NpcItemDrop::new));

        public static final StreamCodec<ByteBuf, NpcItemDrop> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, NpcItemDrop::slot,
                ByteBufCodecs.STRING_UTF8, NpcItemDrop::itemId,
                ByteBufCodecs.FLOAT, NpcItemDrop::chancePercent,
                NpcItemDrop::new);

        // Additive convenience copies for scripting/dev use: each returns a new record via the
        // canonical constructor with the named field changed.
        public NpcItemDrop withSlot(int slot) {
            return new NpcItemDrop(slot, itemId, chancePercent);
        }

        public NpcItemDrop withItemId(String itemId) {
            return new NpcItemDrop(slot, itemId, chancePercent);
        }

        public NpcItemDrop withChance(float chancePercent) {
            return new NpcItemDrop(slot, itemId, chancePercent);
        }
    }

    public static final Codec<NpcDropSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("xp_min", NpcDefaults.XP_DROP_MIN).forGetter(NpcDropSettings::xpMin),
            Codec.INT.optionalFieldOf("xp_max", NpcDefaults.XP_DROP_MAX).forGetter(NpcDropSettings::xpMax),
            NpcItemDrop.CODEC.listOf().optionalFieldOf("drops", List.of()).forGetter(NpcDropSettings::drops)
    ).apply(instance, NpcDropSettings::new));

    public static final StreamCodec<ByteBuf, NpcDropSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcDropSettings::xpMin,
            ByteBufCodecs.VAR_INT, NpcDropSettings::xpMax,
            ByteBufCodecs.collection(ArrayList::new, NpcItemDrop.STREAM_CODEC), NpcDropSettings::drops,
            NpcDropSettings::new);

    public static NpcDropSettings defaults() {
        return new NpcDropSettings(NpcDefaults.XP_DROP_MIN, NpcDefaults.XP_DROP_MAX, List.of());
    }

    // Additive convenience copies for scripting/dev use: each returns a new record via the
    // canonical constructor, preserving immutability.
    public NpcDropSettings withXp(int xpMin, int xpMax) {
        return new NpcDropSettings(xpMin, xpMax, drops);
    }

    // Adds a drop, replacing any existing drop that occupies the same grid slot.
    public NpcDropSettings withDrop(NpcItemDrop drop) {
        List<NpcItemDrop> copy = new ArrayList<>(drops);
        copy.removeIf(existing -> existing.slot() == drop.slot());
        copy.add(drop);
        return new NpcDropSettings(xpMin, xpMax, copy);
    }

    public NpcDropSettings withDrop(int slot, String itemId, float chancePercent) {
        return withDrop(new NpcItemDrop(slot, itemId, chancePercent));
    }

    // Replaces the whole drops list, defensively copied into a fresh ArrayList.
    public NpcDropSettings withDrops(List<NpcItemDrop> drops) {
        return new NpcDropSettings(xpMin, xpMax, new ArrayList<>(drops));
    }

    public static NpcDropSettings of(int xpMin, int xpMax, NpcItemDrop... drops) {
        return new NpcDropSettings(xpMin, xpMax, new ArrayList<>(List.of(drops)));
    }

    public boolean isValid() {
        if (xpMin < 0 || xpMax > NpcValidation.MAX_XP_DROP || xpMin > xpMax
                || drops.size() > NpcValidation.DROP_GRID_SLOTS) {
            return false;
        }
        for (NpcItemDrop drop : drops) {
            if (drop.slot() < 0 || drop.slot() >= NpcValidation.DROP_GRID_SLOTS
                    || drop.itemId().isBlank()
                    || !NpcValidation.isValidDropChance(drop.chancePercent())) {
                return false;
            }
        }
        return true;
    }
}
