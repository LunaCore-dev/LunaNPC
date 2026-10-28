package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// Items are stored as plain item ids ("" = empty slot); unknown ids resolve to an empty slot.
// The ranged weapon is the second primary weapon (bow, crossbow, throwables) used by the
// long-range attack. gearAffectsStats decides whether the gear actually does anything: off
// (default) means purely cosmetic, on means armor, weapons and their enchantments behave
// exactly like on a player — which can override or amplify the Combat tab values. enchantments
// maps a slot key (helmet/chestplate/leggings/boots/main_hand/ranged_weapon/off_hand) to the
// enchantments applied to that slot's item.
public record NpcEquipmentSettings(String helmetId, String chestplateId, String leggingsId, String bootsId,
                                   String mainHandId, String rangedWeaponId, String offHandId,
                                   boolean gearAffectsStats,
                                   Map<String, List<NpcItemEnchant>> enchantments) {

    public NpcEquipmentSettings {
        Map<String, List<NpcItemEnchant>> copy = new java.util.LinkedHashMap<>();
        enchantments.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        enchantments = Map.copyOf(copy);
    }

    public static final String SLOT_HELMET = "helmet";
    public static final String SLOT_CHESTPLATE = "chestplate";
    public static final String SLOT_LEGGINGS = "leggings";
    public static final String SLOT_BOOTS = "boots";
    public static final String SLOT_MAIN_HAND = "main_hand";
    public static final String SLOT_RANGED_WEAPON = "ranged_weapon";
    public static final String SLOT_OFF_HAND = "off_hand";

    private static final StreamCodec<ByteBuf, List<NpcItemEnchant>> ENCHANT_LIST =
            NpcItemEnchant.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final Codec<NpcEquipmentSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("helmet", "").forGetter(NpcEquipmentSettings::helmetId),
            Codec.STRING.optionalFieldOf("chestplate", "").forGetter(NpcEquipmentSettings::chestplateId),
            Codec.STRING.optionalFieldOf("leggings", "").forGetter(NpcEquipmentSettings::leggingsId),
            Codec.STRING.optionalFieldOf("boots", "").forGetter(NpcEquipmentSettings::bootsId),
            Codec.STRING.optionalFieldOf("main_hand", "").forGetter(NpcEquipmentSettings::mainHandId),
            Codec.STRING.optionalFieldOf("ranged_weapon", "").forGetter(NpcEquipmentSettings::rangedWeaponId),
            Codec.STRING.optionalFieldOf("off_hand", "").forGetter(NpcEquipmentSettings::offHandId),
            Codec.BOOL.optionalFieldOf("gear_affects_stats", false)
                    .forGetter(NpcEquipmentSettings::gearAffectsStats),
            Codec.unboundedMap(Codec.STRING, NpcItemEnchant.CODEC.listOf())
                    .optionalFieldOf("enchantments", Map.of()).forGetter(NpcEquipmentSettings::enchantments)
    ).apply(instance, NpcEquipmentSettings::new));

    public static final StreamCodec<ByteBuf, NpcEquipmentSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcEquipmentSettings::helmetId,
            ByteBufCodecs.STRING_UTF8, NpcEquipmentSettings::chestplateId,
            ByteBufCodecs.STRING_UTF8, NpcEquipmentSettings::leggingsId,
            ByteBufCodecs.STRING_UTF8, NpcEquipmentSettings::bootsId,
            ByteBufCodecs.STRING_UTF8, NpcEquipmentSettings::mainHandId,
            ByteBufCodecs.STRING_UTF8, NpcEquipmentSettings::rangedWeaponId,
            ByteBufCodecs.STRING_UTF8, NpcEquipmentSettings::offHandId,
            ByteBufCodecs.BOOL, NpcEquipmentSettings::gearAffectsStats,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ENCHANT_LIST),
            NpcEquipmentSettings::enchantments,
            NpcEquipmentSettings::new);

    // The enchantments applied to a given slot's item, or an empty list.
    public List<NpcItemEnchant> enchantsFor(String slotKey) {
        return enchantments.getOrDefault(slotKey, List.of());
    }

    public static NpcEquipmentSettings empty() {
        return new NpcEquipmentSettings("", "", "", "", "", "", "", false, Map.of());
    }

    // Additive convenience copies for scripting/dev use: each returns a new record via the
    // canonical constructor with the named fields changed; the enchantments map is preserved.
    public NpcEquipmentSettings withMainHand(String mainHandId) {
        return new NpcEquipmentSettings(helmetId, chestplateId, leggingsId, bootsId, mainHandId,
                rangedWeaponId, offHandId, gearAffectsStats, enchantments);
    }

    public NpcEquipmentSettings withRangedWeapon(String rangedWeaponId) {
        return new NpcEquipmentSettings(helmetId, chestplateId, leggingsId, bootsId, mainHandId,
                rangedWeaponId, offHandId, gearAffectsStats, enchantments);
    }

    public NpcEquipmentSettings withOffHand(String offHandId) {
        return new NpcEquipmentSettings(helmetId, chestplateId, leggingsId, bootsId, mainHandId,
                rangedWeaponId, offHandId, gearAffectsStats, enchantments);
    }

    public NpcEquipmentSettings withArmor(String helmetId, String chestplateId, String leggingsId, String bootsId) {
        return new NpcEquipmentSettings(helmetId, chestplateId, leggingsId, bootsId, mainHandId,
                rangedWeaponId, offHandId, gearAffectsStats, enchantments);
    }

    public NpcEquipmentSettings withGearAffectsStats(boolean gearAffectsStats) {
        return new NpcEquipmentSettings(helmetId, chestplateId, leggingsId, bootsId, mainHandId,
                rangedWeaponId, offHandId, gearAffectsStats, enchantments);
    }

    public NpcEquipmentSettings withHelmet(String helmetId) {
        return new NpcEquipmentSettings(helmetId, chestplateId, leggingsId, bootsId, mainHandId,
                rangedWeaponId, offHandId, gearAffectsStats, enchantments);
    }

    public NpcEquipmentSettings withChestplate(String chestplateId) {
        return new NpcEquipmentSettings(helmetId, chestplateId, leggingsId, bootsId, mainHandId,
                rangedWeaponId, offHandId, gearAffectsStats, enchantments);
    }

    public NpcEquipmentSettings withLeggings(String leggingsId) {
        return new NpcEquipmentSettings(helmetId, chestplateId, leggingsId, bootsId, mainHandId,
                rangedWeaponId, offHandId, gearAffectsStats, enchantments);
    }

    public NpcEquipmentSettings withBoots(String bootsId) {
        return new NpcEquipmentSettings(helmetId, chestplateId, leggingsId, bootsId, mainHandId,
                rangedWeaponId, offHandId, gearAffectsStats, enchantments);
    }

    // Replaces the whole enchantments map, defensively copied into a fresh HashMap.
    public NpcEquipmentSettings withEnchantments(Map<String, List<NpcItemEnchant>> enchantments) {
        return new NpcEquipmentSettings(helmetId, chestplateId, leggingsId, bootsId, mainHandId,
                rangedWeaponId, offHandId, gearAffectsStats, new HashMap<>(enchantments));
    }

    // Copies the enchantments map and sets one slot's enchantments (use the SLOT_* keys for slotKey).
    public NpcEquipmentSettings withSlotEnchants(String slotKey, List<NpcItemEnchant> enchants) {
        Map<String, List<NpcItemEnchant>> copy = new HashMap<>(enchantments);
        copy.put(slotKey, new ArrayList<>(enchants));
        return new NpcEquipmentSettings(helmetId, chestplateId, leggingsId, bootsId, mainHandId,
                rangedWeaponId, offHandId, gearAffectsStats, copy);
    }

    // ---- Typed overloads: pass Items instead of ids. Use "" (or the string forms) to clear a slot. ----

    public NpcEquipmentSettings withHelmet(net.minecraft.world.item.Item item) {
        return withHelmet(id(item));
    }

    public NpcEquipmentSettings withChestplate(net.minecraft.world.item.Item item) {
        return withChestplate(id(item));
    }

    public NpcEquipmentSettings withLeggings(net.minecraft.world.item.Item item) {
        return withLeggings(id(item));
    }

    public NpcEquipmentSettings withBoots(net.minecraft.world.item.Item item) {
        return withBoots(id(item));
    }

    public NpcEquipmentSettings withMainHand(net.minecraft.world.item.Item item) {
        return withMainHand(id(item));
    }

    public NpcEquipmentSettings withRangedWeapon(net.minecraft.world.item.Item item) {
        return withRangedWeapon(id(item));
    }

    public NpcEquipmentSettings withOffHand(net.minecraft.world.item.Item item) {
        return withOffHand(id(item));
    }

    private static String id(net.minecraft.world.item.Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
