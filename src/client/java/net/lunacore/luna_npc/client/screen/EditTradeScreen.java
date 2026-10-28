package net.lunacore.luna_npc.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.client.util.ClientRegistries;
import net.lunacore.luna_npc.client.widget.ItemSlotButton;
import net.lunacore.luna_npc.client.widget.RangeSlider;
import net.lunacore.luna_npc.api.NpcItemEnchant;
import net.lunacore.luna_npc.api.NpcStockScope;
import net.lunacore.luna_npc.api.NpcTrade;
import net.lunacore.luna_npc.api.NpcTradeStack;
import net.lunacore.luna_npc.npc.NpcEquipment;
import net.lunacore.luna_npc.npc.NpcValidation;

// One trade offer: up to four input stacks bought for up to four output stacks, an XP reward range,
// and an optional restock-limited stock. Each slot can hold an enchanted stack — clicking a filled
// slot opens the shared slot menu (swap / enchant / clear), reusing the equipment enchant editor so
// a creator can offer or require enchanted items. Works on its own copy and hands the rebuilt
// NpcTrade back to the parent variant editor on close (the apply-on-close contract used across the
// job editors).
public class EditTradeScreen extends LunaScreen {

    private static final int SLOTS = NpcValidation.MAX_TRADE_SLOTS;
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 234;
    private static final int SLOT_PITCH = 30;

    private final LunaScreen parent;
    private final Consumer<NpcTrade> onApply;

    private final String[] inputIds = new String[SLOTS];
    private final int[] inputCounts = new int[SLOTS];
    private final List<List<NpcItemEnchant>> inputEnchants = new ArrayList<>();
    private final String[] outputIds = new String[SLOTS];
    private final int[] outputCounts = new int[SLOTS];
    private final List<List<NpcItemEnchant>> outputEnchants = new ArrayList<>();
    private int xpMin;
    private int xpMax;
    private boolean stockLimited;
    private int stockCount;
    private int restockSeconds;
    private NpcStockScope stockScope;

    private final ItemSlotButton[] inputSlots = new ItemSlotButton[SLOTS];
    private final ItemSlotButton[] outputSlots = new ItemSlotButton[SLOTS];
    private EditBox stockCountField;
    private EditBox restockField;
    private Button scopeButton;

    public EditTradeScreen(LunaScreen parent, NpcTrade trade, Consumer<NpcTrade> onApply) {
        super(Component.translatable("gui.luna_npc.edit_trade.title"));
        this.parent = parent;
        this.onApply = onApply;
        seedSlots(trade.inputs(), inputIds, inputCounts, inputEnchants);
        seedSlots(trade.outputs(), outputIds, outputCounts, outputEnchants);
        xpMin = trade.xpMin();
        xpMax = trade.xpMax();
        stockLimited = trade.stockLimited();
        stockCount = trade.stockCount();
        restockSeconds = trade.restockSeconds();
        stockScope = trade.stockScope();
    }

    private static void seedSlots(List<NpcTradeStack> stacks, String[] ids, int[] counts,
                                  List<List<NpcItemEnchant>> enchants) {
        for (int i = 0; i < SLOTS; i++) {
            if (i < stacks.size()) {
                ids[i] = stacks.get(i).itemId();
                counts[i] = stacks.get(i).count();
                enchants.add(new ArrayList<>(stacks.get(i).enchants()));
            } else {
                ids[i] = "";
                counts[i] = 1;
                enchants.add(new ArrayList<>());
            }
        }
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }

    @Override
    protected void init() {
        int contentX = panelX() + 14;
        int top = panelY();

        buildSlotRow(contentX, top + 28, inputSlots, inputIds, inputCounts, inputEnchants);
        buildSlotRow(contentX, top + 78, outputSlots, outputIds, outputCounts, outputEnchants);
        refreshSlots();

        addRenderableWidget(new RangeSlider(contentX, top + 128, PANEL_WIDTH - 28, 14,
                Component.empty(), 0, NpcValidation.MAX_JOB_XP, xpMin, xpMax, (min, max) -> {
            xpMin = min;
            xpMax = max;
        }));

        addRenderableWidget(Checkbox.builder(
                Component.translatable("gui.luna_npc.edit_trade.out_of_stock"), this.font)
                .pos(contentX, top + 146).selected(stockLimited)
                .tooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_trade.out_of_stock.tooltip")))
                .onValueChange((box, value) -> {
                    stockLimited = value;
                    refreshStockControls();
                }).build());

        stockCountField = numberField(contentX, top + 180, 50, stockCount, 5,
                NpcValidation.MAX_TRADE_STOCK, value -> stockCount = value);
        restockField = numberField(contentX + 58, top + 180, 60, restockSeconds, 7,
                NpcValidation.MAX_RESTOCK_SECONDS, value -> restockSeconds = value);
        scopeButton = addRenderableWidget(Button.builder(scopeLabel(), button -> {
            NpcStockScope[] scopes = NpcStockScope.values();
            stockScope = scopes[(stockScope.ordinal() + 1) % scopes.length];
            scopeButton.setMessage(scopeLabel());
        }).bounds(contentX + 126, top + 180, PANEL_WIDTH - 28 - 126, 18).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 50, top + PANEL_HEIGHT - 26, 100, 20).build());

        refreshStockControls();
    }

    private void buildSlotRow(int x, int y, ItemSlotButton[] slots, String[] ids, int[] counts,
                              List<List<NpcItemEnchant>> enchants) {
        for (int i = 0; i < SLOTS; i++) {
            int slotIndex = i;
            int slotX = x + i * SLOT_PITCH;
            ItemSlotButton slot = new ItemSlotButton(slotX, y, this.title, event -> {
                if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    clearSlot(ids, enchants, slotIndex);
                } else if (ids[slotIndex] == null || ids[slotIndex].isBlank()) {
                    pickItem(ids, counts, enchants, slotIndex);
                } else {
                    openSlotMenu(ids, counts, enchants, slotIndex);
                }
            });
            slots[i] = addRenderableWidget(slot);
            numberField(slotX - 4, y + 20, 26, counts[i], 2, NpcValidation.MAX_TRADE_SLOT_COUNT, value -> {
                counts[slotIndex] = Math.max(1, value);
                applySlot(slots[slotIndex], ids[slotIndex], counts[slotIndex], enchants.get(slotIndex));
            });
        }
    }

    private void openSlotMenu(String[] ids, int[] counts, List<List<NpcItemEnchant>> enchants, int slotIndex) {
        ItemStack preview = resolveEnchanted(ids[slotIndex], counts[slotIndex], enchants.get(slotIndex));
        Minecraft.getInstance().setScreenAndShow(new SlotActionScreen(this, preview,
                Component.translatable("gui.luna_npc.edit_trade.slot"),
                () -> pickItem(ids, counts, enchants, slotIndex),
                () -> enchantSlot(ids, enchants, slotIndex),
                () -> {
                    clearSlot(ids, enchants, slotIndex);
                    Minecraft.getInstance().setScreenAndShow(this);
                }));
    }

    private void pickItem(String[] ids, int[] counts, List<List<NpcItemEnchant>> enchants, int slotIndex) {
        Minecraft.getInstance().setScreenAndShow(new ItemPickerScreen(this, null, itemId -> {
            if (!itemId.equals(ids[slotIndex])) {
                enchants.get(slotIndex).clear();
            }
            ids[slotIndex] = itemId;
            if (!itemId.isEmpty() && counts[slotIndex] < 1) {
                counts[slotIndex] = 1;
            }
        }));
    }

    private void enchantSlot(String[] ids, List<List<NpcItemEnchant>> enchants, int slotIndex) {
        Minecraft.getInstance().setScreenAndShow(new EnchantItemScreen(this, ids[slotIndex],
                enchants.get(slotIndex), updated -> enchants.set(slotIndex, new ArrayList<>(updated))));
    }

    private void clearSlot(String[] ids, List<List<NpcItemEnchant>> enchants, int slotIndex) {
        ids[slotIndex] = "";
        enchants.get(slotIndex).clear();
        refreshSlots();
    }

    private EditBox numberField(int x, int y, int width, int initial, int maxLength, int max,
                                java.util.function.IntConsumer onChange) {
        EditBox field = new EditBox(this.font, x, y, width, 16, Component.empty());
        field.setMaxLength(maxLength);
        field.setValue(String.valueOf(initial));
        field.setResponder(value -> {
            String digits = CreateNpcScreen.digitsOnly(field, value);
            if (digits == null) {
                return;
            }
            onChange.accept(Math.min(digits.isEmpty() ? 0 : Integer.parseInt(digits), max));
        });
        return addRenderableWidget(field);
    }

    private void refreshSlots() {
        for (int i = 0; i < SLOTS; i++) {
            applySlot(inputSlots[i], inputIds[i], inputCounts[i], inputEnchants.get(i));
            applySlot(outputSlots[i], outputIds[i], outputCounts[i], outputEnchants.get(i));
        }
    }

    private static ItemStack resolveEnchanted(String id, int count, List<NpcItemEnchant> enchants) {
        ItemStack stack = NpcEquipment.resolve(id, enchants, ClientRegistries.lookup());
        if (!stack.isEmpty()) {
            stack.setCount(Math.max(1, count));
        }
        return stack;
    }

    private void applySlot(ItemSlotButton slot, String id, int count, List<NpcItemEnchant> enchants) {
        ItemStack stack = resolveEnchanted(id, count, enchants);
        if (!stack.isEmpty()) {
            slot.setStack(stack);
            slot.setTooltip(Tooltip.create(stack.getHoverName()));
        } else {
            slot.setStack(ItemStack.EMPTY);
            slot.setTooltip(null);
        }
    }

    private void refreshStockControls() {
        stockCountField.active = stockLimited;
        stockCountField.visible = stockLimited;
        restockField.active = stockLimited;
        restockField.visible = stockLimited;
        scopeButton.active = stockLimited;
        scopeButton.visible = stockLimited;
    }

    private Component scopeLabel() {
        return Component.translatable("gui.luna_npc.edit_trade.stock_scope." + stockScope.getSerializedName());
    }

    private NpcTrade build() {
        List<NpcTradeStack> inputs = collect(inputIds, inputCounts, inputEnchants);
        List<NpcTradeStack> outputs = collect(outputIds, outputCounts, outputEnchants);
        return new NpcTrade(inputs, outputs, Math.min(xpMin, xpMax), xpMax, stockLimited,
                stockCount, restockSeconds, stockScope);
    }

    private static List<NpcTradeStack> collect(String[] ids, int[] counts, List<List<NpcItemEnchant>> enchants) {
        List<NpcTradeStack> stacks = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (ids[i] != null && !ids[i].isBlank()) {
                stacks.add(new NpcTradeStack(ids[i], Math.max(1, counts[i]), List.copyOf(enchants.get(i))));
            }
        }
        return stacks;
    }

    @Override
    public void onClose() {
        onApply.accept(build());
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int contentX = panelX() + 14;
        int top = panelY();
        graphics.text(this.font, Component.translatable("gui.luna_npc.edit_trade.inputs"),
                contentX, top + 17, SECTION_COLOR);
        graphics.text(this.font, Component.translatable("gui.luna_npc.edit_trade.outputs"),
                contentX, top + 67, SECTION_COLOR);
        graphics.text(this.font, Component.translatable("gui.luna_npc.edit_trade.xp_reward"),
                contentX, top + 116, LABEL_COLOR);
        Component xpValue = Component.literal(xpMin + " - " + xpMax);
        graphics.text(this.font, xpValue, panelX() + PANEL_WIDTH - 14 - this.font.width(xpValue),
                top + 116, LABEL_COLOR);
        if (stockLimited) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.edit_trade.stock_count"),
                    contentX, top + 170, LABEL_COLOR);
            graphics.text(this.font, Component.translatable("gui.luna_npc.edit_trade.restock_time"),
                    contentX + 58, top + 170, LABEL_COLOR);
            graphics.text(this.font, Component.translatable("gui.luna_npc.edit_trade.stock_scope"),
                    contentX + 126, top + 170, LABEL_COLOR);
        }
    }
}
