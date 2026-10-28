package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.widget.LabeledSlider;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.api.NpcStockScope;
import net.lunacore.luna_npc.api.NpcTrade;
import net.lunacore.luna_npc.api.NpcTradeStack;
import net.lunacore.luna_npc.api.NpcTradeSubType;
import net.lunacore.luna_npc.npc.NpcEquipment;
import net.lunacore.luna_npc.npc.NpcValidation;

// One weighted trading variant: its probability weight plus the list of trades it offers. The nested
// EditTradeScreen edits a single trade; this screen owns the trade list and hands the rebuilt variant
// back to the Jobs tab on close.
public class EditTradeSubTypeScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 236;
    private static final int LIST_TOP_OFFSET = 54;
    private static final int LIST_HEIGHT = 112;

    private final LunaScreen parent;
    private final Consumer<NpcTradeSubType> onApply;
    private final List<NpcTrade> trades;
    private int weight;
    private int selectedIndex = -1;

    private SimpleSelectionList tradeList;
    private Button addButton;
    private Button removeButton;
    private Button duplicateButton;
    private Button editButton;

    public EditTradeSubTypeScreen(LunaScreen parent, NpcTradeSubType subType,
                                  Consumer<NpcTradeSubType> onApply) {
        super(Component.translatable("gui.luna_npc.edit_trade_subtype.title"));
        this.parent = parent;
        this.onApply = onApply;
        this.weight = subType.weightPercent();
        this.trades = new ArrayList<>(subType.trades());
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }

    @Override
    protected void init() {
        int contentX = panelX() + 12;
        int top = panelY();

        LabeledSlider weightSlider = new LabeledSlider(contentX, top + 16, PANEL_WIDTH - 24, 20,
                Component.translatable("gui.luna_npc.edit_subtype.weight"), 0,
                NpcValidation.JOB_WEIGHT_TOTAL_PERCENT, weight,
                value -> Math.round(value) + "%", value -> weight = Math.round(value));
        weightSlider.setTooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_subtype.weight.tooltip")));
        addRenderableWidget(weightSlider);

        tradeList = addRenderableWidget(new SimpleSelectionList(PANEL_WIDTH - 24, LIST_HEIGHT, top + LIST_TOP_OFFSET));
        tradeList.setX(contentX);

        int buttonWidth = (PANEL_WIDTH - 24 - 9) / 4;
        int buttonY = top + LIST_TOP_OFFSET + LIST_HEIGHT + 8;
        addButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.luna_npc.edit_trade.add_trade"), button -> addTrade())
                .bounds(contentX, buttonY, buttonWidth, 20).build());
        removeButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.luna_npc.create_npc.remove_sub_type"), button -> removeSelected())
                .bounds(contentX + buttonWidth + 3, buttonY, buttonWidth, 20).build());
        duplicateButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.luna_npc.create_npc.duplicate_sub_type"), button -> duplicateSelected())
                .bounds(contentX + 2 * (buttonWidth + 3), buttonY, buttonWidth, 20).build());
        editButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.luna_npc.create_npc.edit_sub_type"), button -> editSelected())
                .bounds(contentX + 3 * (buttonWidth + 3), buttonY, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 50, top + PANEL_HEIGHT - 28, 100, 20).build());

        rebuildList();
    }

    private void rebuildList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (int i = 0; i < trades.size(); i++) {
            String id = String.valueOf(i);
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(id, summarize(trades.get(i)),
                    value -> selectedIndex = Integer.parseInt(value));
            entries.add(entry);
            if (i == selectedIndex) {
                selected = entry;
            }
        }
        if (selected == null) {
            selectedIndex = -1;
        }
        tradeList.fill(entries, selected);
        refreshButtons();
    }

    private void refreshButtons() {
        boolean hasSelection = selectedIndex >= 0;
        boolean roomForMore = trades.size() < NpcValidation.MAX_TRADES_PER_SUB_TYPE;
        addButton.active = roomForMore;
        removeButton.active = hasSelection;
        duplicateButton.active = hasSelection && roomForMore;
        editButton.active = hasSelection;
    }

    private static NpcTrade emptyTrade() {
        return new NpcTrade(List.of(), List.of(), 0, 0, false, 0, 0, NpcStockScope.PER_PLAYER);
    }

    private void addTrade() {
        if (trades.size() >= NpcValidation.MAX_TRADES_PER_SUB_TYPE) {
            return;
        }
        Minecraft.getInstance().setScreenAndShow(new EditTradeScreen(this, emptyTrade(), trade -> {
            trades.add(trade);
            selectedIndex = trades.size() - 1;
        }));
    }

    private void editSelected() {
        if (selectedIndex < 0) {
            return;
        }
        int index = selectedIndex;
        Minecraft.getInstance().setScreenAndShow(new EditTradeScreen(this, trades.get(index),
                trade -> trades.set(index, trade)));
    }

    private void duplicateSelected() {
        if (selectedIndex < 0 || trades.size() >= NpcValidation.MAX_TRADES_PER_SUB_TYPE) {
            return;
        }
        trades.add(selectedIndex + 1, trades.get(selectedIndex));
        selectedIndex++;
        rebuildList();
    }

    private void removeSelected() {
        if (selectedIndex < 0) {
            return;
        }
        trades.remove(selectedIndex);
        selectedIndex = -1;
        rebuildList();
    }

    // "2x Diamond -> 1x Emerald", first slot each side (a trailing + marks extra slots).
    private static Component summarize(NpcTrade trade) {
        return side(trade.inputs()).copy().append(" -> ").append(side(trade.outputs()));
    }

    private static Component side(List<NpcTradeStack> stacks) {
        if (stacks.isEmpty()) {
            return Component.literal("?");
        }
        NpcTradeStack first = stacks.get(0);
        Component label = Component.literal(first.count() + "x ")
                .append(NpcEquipment.resolve(first.itemId()).getHoverName());
        return stacks.size() > 1 ? label.copy().append(" +") : label;
    }

    @Override
    public void onClose() {
        onApply.accept(new NpcTradeSubType(weight, trades));
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
        graphics.outline(panelX() + 12, panelY() + LIST_TOP_OFFSET, PANEL_WIDTH - 24, LIST_HEIGHT, PANEL_BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, Component.translatable("gui.luna_npc.edit_trade_subtype.trades", trades.size()),
                panelX() + 12, panelY() + 42, SECTION_COLOR);
    }
}
