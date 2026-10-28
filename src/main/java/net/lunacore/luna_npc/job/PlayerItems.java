package net.lunacore.luna_npc.job;

import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.api.NpcTradeStack;
import net.lunacore.luna_npc.npc.NpcEquipment;

// Item payments taken from a player's inventory, matched by item type (the dialog TAKE_ITEMS action and a
// bodyguard's hire cost).
public final class PlayerItems {

    private PlayerItems() {
    }

    public static boolean hasAll(Player player, List<NpcTradeStack> stacks) {
        for (NpcTradeStack wanted : stacks) {
            ItemStack resolved = NpcEquipment.resolve(wanted.itemId());
            if (!resolved.isEmpty() && count(player.getInventory(), resolved.getItem()) < totalOf(stacks, resolved.getItem())) {
                return false;
            }
        }
        return true;
    }

    // Takes as much of each stack as the player has.
    public static void take(Player player, List<NpcTradeStack> stacks) {
        Inventory inventory = player.getInventory();
        for (NpcTradeStack take : stacks) {
            ItemStack resolved = NpcEquipment.resolve(take.itemId());
            if (resolved.isEmpty()) {
                continue;
            }
            int remaining = take.count();
            for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && stack.is(resolved.getItem())) {
                    int amount = Math.min(remaining, stack.getCount());
                    inventory.removeItem(slot, amount);
                    remaining -= amount;
                }
            }
        }
    }

    private static int count(Inventory inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // Two cost slots of the same item add up, so both must be covered.
    private static int totalOf(List<NpcTradeStack> stacks, Item item) {
        int total = 0;
        for (NpcTradeStack stack : stacks) {
            if (NpcEquipment.resolve(stack.itemId()).is(item)) {
                total += stack.count();
            }
        }
        return total;
    }
}
