package net.lunacore.luna_npc.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.lunacore.luna_npc.api.NpcTrade;
import net.lunacore.luna_npc.api.NpcTradeStack;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.network.TradeStockPayload;
import net.lunacore.luna_npc.npc.NpcEquipment;

// The custom trading container: four free-form payment slots plus the player inventory. Outputs are
// never real slots — the client previews the selected trade's outputs and one server-validated
// button press (id 100) performs the whole swap atomically, which sidesteps the dupe bugs of
// pulling from result slots. Selection (ids 0..9) and execution ride the vanilla container-button
// channel, so no custom C2S payload is needed.
public class TradeMenu extends AbstractContainerMenu {

    public static final int PAYMENT_SLOTS = 4;
    public static final int EXECUTE_BUTTON = 100;

    public static final int IMAGE_WIDTH = 350;
    public static final int IMAGE_HEIGHT = 226;
    public static final int SLOT = 18;
    public static final int PAYMENT_X = 235;
    public static final int PAYMENT_Y = 40;
    public static final int INV_X = 94;
    public static final int INV_Y = 141;
    public static final int HOTBAR_Y = 199;

    private static final double MAX_REACH_SQUARED = 8.0 * 8.0;

    private final NpcEntity entity;
    private final List<NpcTrade> trades;
    private final List<TradeMenuData.TradeStockView> stock;
    private final int npcEntityId;
    private final SimpleContainer payment;
    private int selectedTrade = -1;

    public TradeMenu(int containerId, Inventory inventory, TradeMenuData data) {
        this(containerId, inventory, null, data.trades(), data.stock(), data.npcEntityId());
    }

    public TradeMenu(int containerId, Inventory inventory, NpcEntity entity, List<NpcTrade> trades) {
        this(containerId, inventory, entity, trades, List.of(), entity.getId());
    }

    private TradeMenu(int containerId, Inventory inventory, NpcEntity entity, List<NpcTrade> trades,
                      List<TradeMenuData.TradeStockView> stock, int npcEntityId) {
        super(LunaNpcMenus.TRADE, containerId);
        this.entity = entity;
        this.trades = List.copyOf(trades);
        this.stock = List.copyOf(stock);
        this.npcEntityId = npcEntityId;
        this.payment = new SimpleContainer(PAYMENT_SLOTS);

        for (int index = 0; index < PAYMENT_SLOTS; index++) {
            addSlot(new Slot(payment, index, PAYMENT_X + 1 + index * SLOT, PAYMENT_Y + 1));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + 1 + col * SLOT, INV_Y + 1 + row * SLOT));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_X + 1 + col * SLOT, HOTBAR_Y + 1));
        }
    }

    public List<NpcTrade> trades() {
        return trades;
    }

    public List<TradeMenuData.TradeStockView> stock() {
        return stock;
    }

    public int npcEntityId() {
        return npcEntityId;
    }

    public int selectedTrade() {
        return selectedTrade;
    }

    public void setSelectedTradeClient(int index) {
        selectedTrade = index;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == EXECUTE_BUTTON) {
            return player instanceof ServerPlayer serverPlayer && execute(serverPlayer);
        }
        if (id >= 0 && id < trades.size()) {
            selectedTrade = id;
            return true;
        }
        return false;
    }

    private boolean execute(ServerPlayer player) {
        if (selectedTrade < 0 || selectedTrade >= trades.size() || !stillValid(player)) {
            return false;
        }
        NpcTrade trade = trades.get(selectedTrade);
        MinecraftServer server = player.level().getServer();
        JobStockManager stockManager = JobStockManager.get(server);
        String stockKey = stockKey(trade, player);
        if (trade.stockLimited() && !stockManager.hasStock(server, stockKey, trade.stockCount())) {
            playTradeSound(player, entity.jobSettings().tradeFailSound(), SoundEvents.VILLAGER_NO);
            return false;
        }

        Map<Item, Integer> required = requiredInputs(trade);
        if (required == null || !paymentContains(required)) {
            playTradeSound(player, entity.jobSettings().tradeFailSound(), SoundEvents.VILLAGER_NO);
            return false;
        }
        consume(required);
        giveOutputs(player, trade);
        grantXp(player, trade);
        if (trade.stockLimited()) {
            stockManager.recordUse(server, stockKey, trade.stockCount(), trade.restockSeconds());
        }
        playTradeSound(player, entity.jobSettings().tradeSuccessSound(), SoundEvents.VILLAGER_YES);
        broadcastChanges();
        ServerPlayNetworking.send(player,
                new TradeStockPayload(npcEntityId, buildStockViews(server, entity, player, trades)));
        return true;
    }

    private void playTradeSound(ServerPlayer player, String soundId, SoundEvent fallback) {
        SoundEvent sound = NpcEntity.soundOrDefault(soundId, fallback);
        player.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    private String stockKey(NpcTrade trade, ServerPlayer player) {
        return JobStockManager.key(trade.stockScope(), player.getUUID(), entity.getUUID(), entity.lunaId(),
                entity.rolledJobSubType(), selectedTrade);
    }

    // The 4 payment slots must collectively hold every input stack (counts summed per item id).
    // A null return means an input references an item this server can't resolve — the trade is
    // impossible and rejected.
    private static Map<Item, Integer> requiredInputs(NpcTrade trade) {
        Map<Item, Integer> required = new HashMap<>();
        for (NpcTradeStack input : trade.inputs()) {
            Item item = NpcEquipment.resolve(input.itemId()).getItem();
            if (item == Items.AIR) {
                return null;
            }
            required.merge(item, input.count(), Integer::sum);
        }
        return required;
    }

    private boolean paymentContains(Map<Item, Integer> required) {
        Map<Item, Integer> available = new HashMap<>();
        for (int index = 0; index < PAYMENT_SLOTS; index++) {
            ItemStack stack = payment.getItem(index);
            if (!stack.isEmpty()) {
                available.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void consume(Map<Item, Integer> required) {
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            int remaining = entry.getValue();
            for (int index = 0; index < PAYMENT_SLOTS && remaining > 0; index++) {
                ItemStack stack = payment.getItem(index);
                if (!stack.isEmpty() && stack.getItem() == entry.getKey()) {
                    remaining -= payment.removeItem(index, remaining).getCount();
                }
            }
        }
    }

    private static void giveOutputs(Player player, NpcTrade trade) {
        for (NpcTradeStack output : trade.outputs()) {
            ItemStack stack = NpcEquipment.resolve(output.itemId(), output.enchants(),
                    player.level().registryAccess());
            if (stack.isEmpty()) {
                continue;
            }
            stack.setCount(output.count());
            RewardDelivery.give(player, stack);
        }
    }

    private static void grantXp(Player player, NpcTrade trade) {
        int amount = trade.xpMin();
        if (trade.xpMax() > trade.xpMin()) {
            RandomSource random = player.getRandom();
            amount += random.nextInt(trade.xpMax() - trade.xpMin() + 1);
        }
        if (amount > 0) {
            player.giveExperiencePoints(amount);
        }
    }

    static List<TradeMenuData.TradeStockView> buildStockViews(MinecraftServer server, NpcEntity entity,
                                                              ServerPlayer player, List<NpcTrade> trades) {
        JobStockManager stockManager = JobStockManager.get(server);
        List<TradeMenuData.TradeStockView> views = new ArrayList<>();
        for (int index = 0; index < trades.size(); index++) {
            NpcTrade trade = trades.get(index);
            if (!trade.stockLimited()) {
                views.add(new TradeMenuData.TradeStockView(index, -1, 0));
                continue;
            }
            String key = JobStockManager.key(trade.stockScope(), player.getUUID(), entity.getUUID(),
                    entity.lunaId(), entity.rolledJobSubType(), index);
            views.add(stockManager.view(server, index, key, trade.stockCount()));
        }
        return views;
    }

    @Override
    public boolean stillValid(Player player) {
        if (entity == null) {
            return true;
        }
        if (!entity.isAlive() || player.distanceToSqr(entity) > MAX_REACH_SQUARED) {
            return false;
        }
        entity.holdForInteraction();
        return !(player instanceof ServerPlayer serverPlayer) || !entity.isInteractionBlockedFor(serverPlayer);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < PAYMENT_SLOTS) {
                if (!moveItemStackTo(stack, PAYMENT_SLOTS, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, PAYMENT_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, payment);
    }
}
