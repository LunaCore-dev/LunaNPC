package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.widget.ReorderableList;
import net.lunacore.luna_npc.api.NpcAttackRange;
import net.lunacore.luna_npc.api.NpcRangeComparator;
import net.lunacore.luna_npc.api.NpcRangeMetric;
import net.lunacore.luna_npc.api.NpcRangeRule;
import net.lunacore.luna_npc.api.NpcRangeSwitch;
import net.lunacore.luna_npc.npc.NpcValidation;

// Compact popup for the Combat tab's close/long switch. Rules are priority-ordered — the first that
// passes picks the fight range — so the list is drag-reorderable and an OTHERWISE rule (which always
// passes) acts as the "else". Edits a working copy and only commits it through onDone when Done is pressed.
public class EditRangeSwitchScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 240;
    private static final int LIST_TOP = 54;
    private static final int LIST_HEIGHT = NpcValidation.MAX_RANGE_RULES_GUI * ReorderableList.ROW_HEIGHT;
    private static final int PANEL_HEIGHT = LIST_TOP + LIST_HEIGHT + 58;
    private static final int HINT_COLOR = 0xFFAAAAAA;

    private final Screen parent;
    private final Consumer<NpcRangeSwitch> onDone;
    private final int maxHp;

    private boolean enabled;
    private final List<NpcRangeRule> rules;
    private int ruleSelected = -1;

    private ReorderableList ruleList;
    private Button addButton;

    public EditRangeSwitchScreen(Screen parent, NpcRangeSwitch current, int maxHp,
                                 Consumer<NpcRangeSwitch> onDone) {
        super(Component.translatable("gui.luna_npc.rangeswitch.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.maxHp = maxHp;
        this.enabled = current.enabled();
        this.rules = new ArrayList<>(current.rules());
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
        int fieldWidth = PANEL_WIDTH - 24;

        addRenderableWidget(CycleButton.booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, enabled)
                .create(contentX, top + 16, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.rangeswitch.enabled"),
                        (button, value) -> enabled = value));

        ruleList = addRenderableWidget(new ReorderableList(contentX, top + LIST_TOP, fieldWidth, LIST_HEIGHT,
                index -> ruleSelected = index, this::moveRule));

        int bw = (fieldWidth - 4) / 3;
        int buttonsY = top + LIST_TOP + LIST_HEIGHT + 4;
        addButton = Button.builder(Component.translatable("gui.luna_npc.rangeswitch.add_rule"),
                button -> addRule()).bounds(contentX, buttonsY, bw, 18).build();
        addRenderableWidget(addButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.rangeswitch.edit_rule"),
                button -> editRule()).bounds(contentX + bw + 2, buttonsY, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.rangeswitch.remove_rule"),
                button -> removeRule()).bounds(contentX + 2 * (bw + 2), buttonsY, bw, 18).build());

        int buttonWidth = (fieldWidth - 6) / 2;
        int doneY = top + PANEL_HEIGHT - 28;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(contentX, doneY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(contentX + buttonWidth + 6, doneY, buttonWidth, 20).build());

        rebuildRules();
    }

    private void addRule() {
        if (rules.size() >= NpcValidation.MAX_RANGE_RULES_GUI) {
            return;
        }
        NpcRangeRule rule = new NpcRangeRule(NpcRangeMetric.SELF_HP, NpcRangeComparator.AT_MOST, 0.0F, 8,
                NpcAttackRange.LONG);
        Minecraft.getInstance().setScreenAndShow(new EditRangeRuleScreen(this, rule, maxHp, updated -> {
            rules.add(updated);
            ruleSelected = rules.size() - 1;
        }));
    }

    private void editRule() {
        if (ruleSelected < 0) {
            return;
        }
        int index = ruleSelected;
        Minecraft.getInstance().setScreenAndShow(new EditRangeRuleScreen(this, rules.get(index), maxHp,
                updated -> rules.set(index, updated)));
    }

    private void removeRule() {
        if (ruleSelected < 0) {
            return;
        }
        rules.remove(ruleSelected);
        ruleSelected = -1;
        rebuildRules();
    }

    // The list widget reorders its own display rows and reports the move here so the rule list matches.
    private void moveRule(int from, int to) {
        if (from < 0 || from >= rules.size() || to < 0 || to >= rules.size()) {
            return;
        }
        rules.add(to, rules.remove(from));
        ruleSelected = to;
    }

    private void rebuildRules() {
        List<Component> labels = new ArrayList<>();
        for (NpcRangeRule rule : rules) {
            labels.add(ruleSummary(rule));
        }
        ruleList.setRows(labels, ruleSelected);
        addButton.active = rules.size() < NpcValidation.MAX_RANGE_RULES_GUI;
    }

    private static Component ruleSummary(NpcRangeRule rule) {
        Component measure;
        if (rule.metric() == NpcRangeMetric.OTHERWISE) {
            measure = Component.translatable("gui.luna_npc.rangeswitch.summary.otherwise");
        } else {
            Component glyph = Component.translatable(
                    "gui.luna_npc.rangeswitch.summary." + rule.comparator().getSerializedName());
            int amount = Math.round(rule.value());
            measure = switch (rule.metric()) {
                case SELF_HP -> Component.translatable("gui.luna_npc.rangeswitch.summary.self_hp", glyph, amount);
                case TARGET_HP_PERCENT ->
                        Component.translatable("gui.luna_npc.rangeswitch.summary.target_hp_percent", glyph, amount);
                case DISTANCE -> Component.translatable("gui.luna_npc.rangeswitch.summary.distance", glyph, amount);
                case ENEMIES_IN_RADIUS ->
                        Component.translatable("gui.luna_npc.rangeswitch.summary.enemies_in_radius", glyph, amount,
                                rule.radius());
                case OTHERWISE -> Component.empty();
            };
        }
        Component result = Component.translatable(
                "gui.luna_npc.rangeswitch.range." + rule.result().getSerializedName());
        return measure.copy().append(Component.translatable("gui.luna_npc.rangeswitch.summary.arrow", result));
    }

    private void save() {
        onDone.accept(new NpcRangeSwitch(enabled, List.copyOf(rules)));
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
        graphics.outline(panelX() + 12, panelY() + LIST_TOP, PANEL_WIDTH - 24, LIST_HEIGHT, PANEL_BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        Font font = this.font;
        graphics.text(font, Component.translatable("gui.luna_npc.rangeswitch.rules", rules.size()),
                panelX() + 12, panelY() + 42, SECTION_COLOR);
        Component hint = Component.translatable("gui.luna_npc.rangeswitch.reorder_hint");
        graphics.text(font, hint, panelX() + PANEL_WIDTH - 12 - font.width(hint), panelY() + 42, HINT_COLOR);
    }
}
