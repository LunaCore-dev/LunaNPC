package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.api.NpcTrade;
import net.lunacore.luna_npc.api.NpcTradeStack;
import net.lunacore.luna_npc.client.util.ClientRegistries;
import net.lunacore.luna_npc.job.TradeMenu;
import net.lunacore.luna_npc.job.TradeMenuData;
import net.lunacore.luna_npc.npc.NpcEquipment;

// The trade menu screen. A container screen can't extend LunaScreen, so its surfaces are drawn
// through LunaPanels to match the rest of the mod (the CLAUDE.md UI rules). Left: a scrollable list of the NPC's
// trades (input icons -> output icons, dimmed with a restock tooltip when out of stock). Right: the
// four payment slots, an output preview and the Trade button. Selecting a row and pressing Trade
// both ride the vanilla container-button channel; the server validates and performs the swap.
public class TradeScreen extends AbstractContainerScreen<TradeMenu> {

    private static final int LIST_X = 6;
    private static final int LIST_Y = 16;
    private static final int LIST_W = 210;
    private static final int LIST_H = 110;
    private static final int ROW_X = 9;
    private static final int ROWS_Y = 34;
    private static final int ROW_W = 204;
    private static final int ROW_H = 18;
    private static final int VISIBLE_ROWS = 5;
    private static final int RIGHT_X = 220;
    private static final int RIGHT_Y = 16;
    private static final int RIGHT_W = 124;
    private static final int RIGHT_H = 110;
    private static final int PREVIEW_X = 235;
    private static final int PREVIEW_Y = 74;
    private static final int TRADE_BUTTON_X = 235;
    private static final int TRADE_BUTTON_Y = 98;
    private static final int TRADE_BUTTON_W = 100;
    private static final int TRADE_BUTTON_H = 18;
    private static final int INV_PANEL_X = 86;
    private static final int INV_PANEL_Y = 128;
    private static final int INV_PANEL_W = 178;
    private static final int INV_PANEL_H = 94;
    // The player inventory keeps the ordinary Minecraft look (beige panel, recessed slots, dark
    // title) so it never reads as a restyled inventory; only the trade UI above wears the Luna skin.
    private static final int VANILLA_PANEL = 0xFFC6C6C6;
    private static final int VANILLA_PANEL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int VANILLA_PANEL_SHADOW = 0xFF555555;
    private static final int SLOT_INNER = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;
    private static final int INV_TITLE_COLOR = 0xFF404040;

    private final List<NpcTrade> trades;
    private List<TradeMenuData.TradeStockView> stock;
    private final Row[] rows = new Row[VISIBLE_ROWS];
    private Button tradeButton;
    private int scroll;
    private int restockTicks;

    public TradeScreen(TradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TradeMenu.IMAGE_WIDTH, TradeMenu.IMAGE_HEIGHT);
        this.trades = menu.trades();
        this.stock = new ArrayList<>(menu.stock());
    }

    public int npcEntityId() {
        return menu.npcEntityId();
    }

    public void applyStock(List<TradeMenuData.TradeStockView> updated) {
        this.stock = new ArrayList<>(updated);
        refreshRows();
    }

    @Override
    protected void init() {
        super.init();
        for (int index = 0; index < VISIBLE_ROWS; index++) {
            rows[index] = addRenderableWidget(new Row(leftPos + ROW_X, topPos + ROWS_Y + index * ROW_H,
                    ROW_W, ROW_H - 1, this.font, this::selectTrade));
        }
        tradeButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.luna_npc.trade.trade_button"), button -> executeTrade())
                .bounds(leftPos + TRADE_BUTTON_X, topPos + TRADE_BUTTON_Y, TRADE_BUTTON_W, TRADE_BUTTON_H)
                .build());
        refreshRows();
    }

    private void selectTrade(int tradeIndex) {
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, tradeIndex);
        menu.setSelectedTradeClient(tradeIndex);
        refreshRows();
    }

    private void executeTrade() {
        if (menu.selectedTrade() >= 0) {
            Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, TradeMenu.EXECUTE_BUTTON);
        }
    }

    private void refreshRows() {
        int selected = menu.selectedTrade();
        for (int index = 0; index < VISIBLE_ROWS; index++) {
            int tradeIndex = scroll + index;
            if (tradeIndex < trades.size()) {
                rows[index].bind(tradeIndex, trades.get(tradeIndex), viewFor(tradeIndex), tradeIndex == selected);
            } else {
                rows[index].visible = false;
            }
        }
        boolean canTrade = selected >= 0 && selected < trades.size();
        if (canTrade) {
            TradeMenuData.TradeStockView view = viewFor(selected);
            canTrade = view.unlimited() || view.inStock();
        }
        if (tradeButton != null) {
            tradeButton.active = canTrade;
        }
    }

    private TradeMenuData.TradeStockView viewFor(int tradeIndex) {
        for (TradeMenuData.TradeStockView view : stock) {
            if (view.tradeIndex() == tradeIndex) {
                return view;
            }
        }
        return new TradeMenuData.TradeStockView(tradeIndex, -1, 0);
    }

    // Client-side restock countdown: once per second, tick down each sold-out row's timer and flip it
    // back in stock when it elapses. The server's next TradeStockPayload replaces these estimates.
    @Override
    protected void containerTick() {
        super.containerTick();
        if (++restockTicks < 20) {
            return;
        }
        restockTicks = 0;
        boolean changed = false;
        List<TradeMenuData.TradeStockView> next = new ArrayList<>(stock.size());
        for (TradeMenuData.TradeStockView view : stock) {
            if (view.remainingUses() == 0 && view.restockSecondsLeft() > 0) {
                int left = view.restockSecondsLeft() - 1;
                next.add(new TradeMenuData.TradeStockView(view.tradeIndex(),
                        left <= 0 ? 1 : 0, Math.max(0, left)));
                changed = true;
            } else {
                next.add(view);
            }
        }
        if (changed) {
            stock = next;
            refreshRows();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int max = Math.max(0, trades.size() - VISIBLE_ROWS);
        if (max > 0 && mouseX >= leftPos + LIST_X && mouseX <= leftPos + LIST_X + LIST_W
                && mouseY >= topPos + LIST_Y && mouseY <= topPos + LIST_Y + LIST_H) {
            scroll = Math.clamp(scroll - (int) Math.signum(deltaY), 0, max);
            refreshRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        LunaPanels.titledPanel(graphics, font, leftPos + LIST_X, topPos + LIST_Y, LIST_W, LIST_H, this.title);
        LunaPanels.panel(graphics, leftPos + RIGHT_X, topPos + RIGHT_Y, RIGHT_W, RIGHT_H);
        LunaPanels.sectionTitle(graphics, font, Component.translatable("gui.luna_npc.trade.payment"),
                leftPos + RIGHT_X + 6, topPos + RIGHT_Y + 6);
        LunaPanels.sectionTitle(graphics, font, Component.translatable("gui.luna_npc.trade.preview"),
                leftPos + PREVIEW_X, topPos + PREVIEW_Y - 10);
        vanillaPanel(graphics, leftPos + INV_PANEL_X, topPos + INV_PANEL_Y, INV_PANEL_W, INV_PANEL_H);
        drawSlotFrames(graphics);
    }

    private static void vanillaPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, VANILLA_PANEL);
        graphics.fill(x, y, x + width, y + 1, VANILLA_PANEL_HIGHLIGHT);
        graphics.fill(x, y, x + 1, y + height, VANILLA_PANEL_HIGHLIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, VANILLA_PANEL_SHADOW);
        graphics.fill(x + width - 1, y, x + width, y + height, VANILLA_PANEL_SHADOW);
    }

    private void drawSlotFrames(GuiGraphicsExtractor graphics) {
        for (int index = 0; index < TradeMenu.PAYMENT_SLOTS; index++) {
            slotFrame(graphics, leftPos + TradeMenu.PAYMENT_X + index * TradeMenu.SLOT, topPos + TradeMenu.PAYMENT_Y);
            slotFrame(graphics, leftPos + PREVIEW_X + index * TradeMenu.SLOT, topPos + PREVIEW_Y);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotFrame(graphics, leftPos + TradeMenu.INV_X + col * TradeMenu.SLOT,
                        topPos + TradeMenu.INV_Y + row * TradeMenu.SLOT);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotFrame(graphics, leftPos + TradeMenu.INV_X + col * TradeMenu.SLOT, topPos + TradeMenu.HOTBAR_Y);
        }
    }

    // The recessed vanilla slot: a dark top-left lip, a light bottom-right lip and a mid-grey well,
    // matching the ordinary container slot exactly.
    private static void slotFrame(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + TradeMenu.SLOT, y + TradeMenu.SLOT, SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + TradeMenu.SLOT, y + TradeMenu.SLOT, SLOT_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + TradeMenu.SLOT - 1, y + TradeMenu.SLOT - 1, SLOT_INNER);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, this.playerInventoryTitle, TradeMenu.INV_X, INV_PANEL_Y + 3, INV_TITLE_COLOR);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int selected = menu.selectedTrade();
        if (selected < 0 || selected >= trades.size()) {
            return;
        }
        List<NpcTradeStack> outputs = trades.get(selected).outputs();
        for (int index = 0; index < outputs.size() && index < TradeMenu.PAYMENT_SLOTS; index++) {
            drawStack(graphics, font, outputs.get(index),
                    leftPos + PREVIEW_X + 1 + index * TradeMenu.SLOT, topPos + PREVIEW_Y + 1);
        }
    }

    private static void drawStack(GuiGraphicsExtractor graphics, Font font, NpcTradeStack stack, int x, int y) {
        ItemStack rendered = NpcEquipment.resolve(stack.itemId(), stack.enchants(), ClientRegistries.lookup());
        if (rendered.isEmpty()) {
            return;
        }
        rendered.setCount(stack.count());
        graphics.item(rendered, x, y);
        graphics.itemDecorations(font, rendered, x, y, stack.count() > 1 ? String.valueOf(stack.count()) : null);
    }

    private static final class Row extends AbstractWidget {

        private static final int ARROW_COLOR = 0xFFC8C8D0;
        private static final int SELECTED_COLOR = 0xFFE0C060;

        private final Font font;
        private final IntConsumer onSelect;
        private int tradeIndex = -1;
        private NpcTrade trade;
        private TradeMenuData.TradeStockView view;
        private boolean selected;

        Row(int x, int y, int width, int height, Font font, IntConsumer onSelect) {
            super(x, y, width, height, Component.empty());
            this.font = font;
            this.onSelect = onSelect;
            this.visible = false;
        }

        void bind(int tradeIndex, NpcTrade trade, TradeMenuData.TradeStockView view, boolean selected) {
            this.tradeIndex = tradeIndex;
            this.trade = trade;
            this.view = view;
            this.selected = selected;
            this.visible = true;
            if (!view.unlimited() && !view.inStock()) {
                setTooltip(Tooltip.create(view.restockSecondsLeft() > 0
                        ? Component.translatable("gui.luna_npc.trade.restock_in", view.restockSecondsLeft())
                        : Component.translatable("gui.luna_npc.trade.out_of_stock")));
            } else {
                setTooltip(null);
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (tradeIndex >= 0) {
                onSelect.accept(tradeIndex);
            }
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                float partialTick) {
            if (trade == null) {
                return;
            }
            int x = getX();
            int y = getY();
            graphics.fill(x, y, x + width, y + height,
                    selected ? 0x40E0C060 : isHovered() ? 0x30FFFFFF : 0x22000000);
            graphics.outline(x, y, width, height, selected ? SELECTED_COLOR : LunaPanels.BORDER);
            int iconY = y + (height - 16) / 2;
            int iconX = x + 3;
            for (NpcTradeStack input : trade.inputs()) {
                drawStack(graphics, font, input, iconX, iconY);
                iconX += 16;
            }
            graphics.text(font, "→", iconX + 1, iconY + 4, ARROW_COLOR);
            iconX += 11;
            for (NpcTradeStack output : trade.outputs()) {
                drawStack(graphics, font, output, iconX, iconY);
                iconX += 16;
            }
            if (!view.unlimited() && !view.inStock()) {
                graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x99101015);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
