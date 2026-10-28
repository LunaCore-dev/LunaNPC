package net.lunacore.luna_npc.job;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// Gives an item to a player; what does not fit lands still at their feet instead of being thrown along
// their gaze, which would put it next to the NPC they are facing.
final class RewardDelivery {

    private RewardDelivery() {
    }

    static void give(Player player, ItemStack stack) {
        player.getInventory().add(stack);
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack,
                0.0, 0.0, 0.0);
        item.setNoPickUpDelay();
        item.setThrower(player);
        player.level().addFreshEntity(item);
    }
}
