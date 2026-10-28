package net.lunacore.luna_npc.npc;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.equipment.Equippable;
import net.lunacore.luna_npc.api.NpcDropSettings;
import net.lunacore.luna_npc.api.NpcEquipmentSettings;
import net.lunacore.luna_npc.api.NpcItemEnchant;

public final class NpcEquipment {

    private NpcEquipment() {
    }

    public static ItemStack resolve(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        Identifier parsed = Identifier.tryParse(itemId);
        if (parsed == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(parsed).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    // Same as resolve, but bakes the configured enchantments onto the stack so its combat and armor
    // effects apply for real. Needs registry access (server or client) to look the enchantments up.
    public static ItemStack resolve(String itemId, List<NpcItemEnchant> enchants,
                                    HolderLookup.Provider registries) {
        ItemStack stack = resolve(itemId);
        if (stack.isEmpty() || enchants == null || enchants.isEmpty() || registries == null) {
            return stack;
        }
        HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        for (NpcItemEnchant enchant : enchants) {
            if (!enchant.isValid()) {
                continue;
            }
            Identifier id = Identifier.tryParse(enchant.enchantId());
            if (id == null) {
                continue;
            }
            lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id))
                    .ifPresent(holder -> stack.enchant(holder, enchant.level()));
        }
        return stack;
    }

    public static boolean isKnownOrEmpty(String itemId) {
        return itemId != null && (itemId.isBlank() || !resolve(itemId).isEmpty());
    }

    // Whether the item is a bow or crossbow — the weapons the NPC visibly draws/charges before a shot.
    public static boolean isDrawnWeapon(String itemId) {
        net.minecraft.world.item.Item item = resolve(itemId).getItem();
        return item instanceof net.minecraft.world.item.BowItem
                || item instanceof net.minecraft.world.item.CrossbowItem;
    }

    // Hands accept anything; armor slots only accept items whose equippable component targets that
    // exact slot — the same rule vanilla uses for wearing.
    public static boolean isAllowedInSlot(String itemId, EquipmentSlot slot) {
        if (itemId == null || itemId.isBlank()) {
            return true;
        }
        ItemStack stack = resolve(itemId);
        if (stack.isEmpty()) {
            return false;
        }
        if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
            return true;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == slot;
    }

    public static boolean isValid(NpcEquipmentSettings equipment) {
        return equipment != null
                && isAllowedInSlot(equipment.helmetId(), EquipmentSlot.HEAD)
                && isAllowedInSlot(equipment.chestplateId(), EquipmentSlot.CHEST)
                && isAllowedInSlot(equipment.leggingsId(), EquipmentSlot.LEGS)
                && isAllowedInSlot(equipment.bootsId(), EquipmentSlot.FEET)
                && isKnownOrEmpty(equipment.mainHandId())
                && isKnownOrEmpty(equipment.rangedWeaponId())
                && isKnownOrEmpty(equipment.offHandId())
                && enchantsValid(equipment);
    }

    // Enchantments are level-checked here; which enchantments are offered for which item is a UI
    // restriction (only valid vanilla combinations are shown).
    private static boolean enchantsValid(NpcEquipmentSettings equipment) {
        for (List<NpcItemEnchant> list : equipment.enchantments().values()) {
            for (NpcItemEnchant enchant : list) {
                if (!enchant.isValid()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean areBlocksKnown(java.util.List<String> blockIds) {
        for (String blockId : blockIds) {
            Identifier parsed = Identifier.tryParse(blockId);
            if (parsed == null || BuiltInRegistries.BLOCK.getOptional(parsed).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static boolean areDropItemsKnown(NpcDropSettings dropSettings) {
        for (NpcDropSettings.NpcItemDrop drop : dropSettings.drops()) {
            if (resolve(drop.itemId()).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
