package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.widget.CombatRuleGraphWidget;
import net.lunacore.luna_npc.client.widget.LabeledSlider;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.api.NpcAdvancedCombat;
import net.lunacore.luna_npc.api.NpcCombatAction;
import net.lunacore.luna_npc.api.NpcCombatActionType;
import net.lunacore.luna_npc.api.NpcCombatAttack;
import net.lunacore.luna_npc.api.NpcCombatComparator;
import net.lunacore.luna_npc.api.NpcCombatCondition;
import net.lunacore.luna_npc.api.NpcCombatPlacement;
import net.lunacore.luna_npc.api.NpcCombatRule;
import net.lunacore.luna_npc.api.NpcCombatTrigger;
import net.lunacore.luna_npc.api.NpcTargetSelector;
import net.lunacore.luna_npc.api.NpcValueSource;
import net.lunacore.luna_npc.api.NpcValueSourceType;
import net.lunacore.luna_npc.npc.NpcValidation;

// The advanced-combat rule graph: rule cards fill the upper canvas and selecting one slides an inline dock
// into the lower strip to edit its trigger, conditions, actions and cooldown/once in place. Rules are a flat
// list the widget lays out; the whole set is rebuilt into a fresh NpcAdvancedCombat on Done. Mirrors
// DialogGraphScreen — the same canvas + dock split — minus the wiring, since rules never connect.
public class CombatGraphScreen extends LunaScreen {

    private static final int MARGIN = 16;
    private static final int TOOLBAR_Y = 28;
    private static final int GRAPH_TOP = 52;
    private static final int COL_GAP = 12;

    private final Screen parent;
    private final Consumer<NpcAdvancedCombat> onDone;
    private float engagementRadius;
    private final List<NpcCombatAttack> attackLibrary;
    private final List<NpcCombatRule> rules;

    private double viewX;
    private double viewY;
    private float viewZoom = 0.9F;
    private int selectedIndex = -1;
    private int conditionSelected = -1;
    private int actionSelected = -1;
    private boolean populating;

    private CombatRuleGraphWidget graph;
    private Button deleteButton;
    private Button triggerButton;
    private EditBox cooldownField;
    private Button onceButton;
    private SimpleSelectionList conditionList;
    private SimpleSelectionList actionList;
    private final List<AbstractWidget> dockWidgets = new ArrayList<>();

    private int dockTop;
    private int dockHeight;
    private int colAx;
    private int colBx;
    private int colCx;
    private int colW;
    private int rowTop;

    public CombatGraphScreen(Screen parent, NpcAdvancedCombat advanced, Consumer<NpcAdvancedCombat> onDone) {
        super(Component.translatable("gui.luna_npc.combat.graph.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.engagementRadius = advanced.engagementRadius();
        this.attackLibrary = new ArrayList<>(advanced.attackLibrary());
        this.rules = new ArrayList<>(advanced.rules());
    }

    @Override
    protected void init() {
        dockWidgets.clear();
        int graphBottom = this.height - 34;
        int graphHeight = graphBottom - GRAPH_TOP;
        graph = addRenderableWidget(new CombatRuleGraphWidget(MARGIN, GRAPH_TOP, this.width - 2 * MARGIN,
                graphHeight, rules, this::syncDock));
        graph.setView(viewX, viewY, viewZoom);
        graph.setSelection(selectedIndex);

        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.combat.graph.new_rule"),
                button -> addRule()).bounds(MARGIN, TOOLBAR_Y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.combat.graph.library"),
                button -> openLibrary()).bounds(MARGIN + 94, TOOLBAR_Y, 110, 20).build());
        addRenderableWidget(new LabeledSlider(MARGIN + 208, TOOLBAR_Y, 160, 20,
                Component.translatable("gui.luna_npc.combat.graph.engagement"),
                LabeledSlider.Curve.lowBiased(1, NpcValidation.MAX_ENGAGEMENT_RADIUS), engagementRadius,
                v -> String.format(Locale.ROOT, "%.0f", v), v -> engagementRadius = Math.max(1.0F, v)));

        dockHeight = Math.min(Math.max(104, Math.round(this.height * 0.25F)), Math.max(80, graphHeight - 30));
        dockTop = graphBottom - dockHeight;
        buildDock(graphBottom);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 102, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> cancel())
                .bounds(this.width / 2 + 2, this.height - 28, 100, 20).build());

        syncDock();
    }

    private void buildDock(int graphBottom) {
        int px = MARGIN;
        int pw = this.width - 2 * MARGIN;
        colW = (pw - 20 - 2 * COL_GAP) / 3;
        colAx = px + 10;
        colBx = colAx + colW + COL_GAP;
        colCx = colBx + colW + COL_GAP;
        rowTop = dockTop + 8;
        int btnY = graphBottom - 22;
        int listBottom = btnY - 4;

        triggerButton = dock(Button.builder(Component.empty(), button -> editTrigger())
                .bounds(colAx, rowTop + 11, colW, 16).build());
        cooldownField = dock(new EditBox(this.font, colAx, rowTop + 31, colW, 16,
                Component.translatable("gui.luna_npc.combat.rule.cooldown")));
        cooldownField.setHint(Component.translatable("gui.luna_npc.combat.rule.cooldown"));
        cooldownField.setResponder(this::onCooldownTyped);
        onceButton = dock(Button.builder(Component.empty(), button -> toggleOnce())
                .bounds(colAx, rowTop + 50, colW, 16).build());
        deleteButton = dock(Button.builder(Component.translatable("gui.luna_npc.combat.graph.delete_rule"),
                button -> deleteSelected()).bounds(colAx, rowTop + 71, colW, 16).build());

        conditionList = dock(new SimpleSelectionList(colW, listBottom - (rowTop + 11), rowTop + 11));
        conditionList.setX(colBx);
        int cbw = (colW - 4) / 3;
        dock(iconButton(colBx, btnY, cbw, "Add", "gui.luna_npc.combat.rule.add_condition", this::addCondition));
        dock(iconButton(colBx + cbw + 2, btnY, cbw, "Edit", "gui.luna_npc.combat.rule.edit_condition",
                this::editCondition));
        dock(iconButton(colBx + 2 * (cbw + 2), btnY, cbw, "Del", "gui.luna_npc.combat.rule.remove",
                this::removeCondition));

        actionList = dock(new SimpleSelectionList(colW, listBottom - (rowTop + 11), rowTop + 11));
        actionList.setX(colCx);
        int abw = (colW - 4) / 3;
        dock(iconButton(colCx, btnY, abw, "Add", "gui.luna_npc.combat.rule.add_action", this::addAction));
        dock(iconButton(colCx + abw + 2, btnY, abw, "Edit", "gui.luna_npc.combat.rule.edit_action",
                this::editAction));
        dock(iconButton(colCx + 2 * (abw + 2), btnY, abw, "Del", "gui.luna_npc.combat.rule.remove",
                this::removeAction));
    }

    private <T extends AbstractWidget> T dock(T widget) {
        addRenderableWidget(widget);
        dockWidgets.add(widget);
        return widget;
    }

    private Button iconButton(int x, int y, int width, String glyph, String tooltipKey, Runnable action) {
        Button button = Button.builder(Component.literal(glyph), b -> action.run()).bounds(x, y, width, 18).build();
        button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return button;
    }

    private void syncDock() {
        selectedIndex = graph.selectedIndex();
        NpcCombatRule rule = currentRule();
        boolean has = rule != null;
        graph.setBottomInset(has ? dockHeight + 4 : 0);
        for (AbstractWidget widget : dockWidgets) {
            widget.visible = has;
        }
        deleteButton.active = has;
        if (!has) {
            return;
        }
        triggerButton.setMessage(Component.translatable("gui.luna_npc.combat.rule.trigger")
                .copy().append(": ").append(CombatLabels.trigger(rule.trigger().type())));
        onceButton.setMessage(Component.translatable("gui.luna_npc.combat.rule.once").copy()
                .append(": ").append(rule.once() ? CommonComponents.GUI_YES : CommonComponents.GUI_NO));
        populating = true;
        cooldownField.setValue(CombatText.number(rule.cooldownSeconds()));
        populating = false;
        rebuildConditions();
        rebuildActions();
    }

    private void addRule() {
        if (rules.size() >= NpcValidation.MAX_COMBAT_RULES_GUI) {
            return;
        }
        rules.add(new NpcCombatRule(NpcCombatTrigger.always(), List.of(), List.of(), 0.0F, false,
                graph.newRuleX(), graph.newRuleY()));
        conditionSelected = -1;
        actionSelected = -1;
        graph.selectAndFocus(rules.size() - 1);
    }

    private void deleteSelected() {
        if (selectedIndex < 0 || selectedIndex >= rules.size()) {
            return;
        }
        rules.remove(selectedIndex);
        conditionSelected = -1;
        actionSelected = -1;
        graph.deselect();
    }

    private void openLibrary() {
        captureView();
        Minecraft.getInstance().setScreenAndShow(new AttackLibraryScreen(this, attackLibrary, updated -> {
            attackLibrary.clear();
            attackLibrary.addAll(updated);
        }));
    }

    private void editTrigger() {
        NpcCombatRule rule = currentRule();
        if (rule == null) {
            return;
        }
        captureView();
        Minecraft.getInstance().setScreenAndShow(new CombatTriggerScreen(this, rule.trigger(), updated -> {
            NpcCombatRule live = currentRule();
            if (live != null) {
                replaceRule(new NpcCombatRule(updated, live.conditions(), live.actions(),
                        live.cooldownSeconds(), live.once(), live.graphX(), live.graphY()));
            }
        }));
    }

    private void onCooldownTyped(String text) {
        if (populating) {
            return;
        }
        NpcCombatRule rule = currentRule();
        if (rule == null) {
            return;
        }
        CombatText.parse(text).ifPresent(value -> replaceRule(new NpcCombatRule(rule.trigger(),
                rule.conditions(), rule.actions(), Math.max(0.0F, value), rule.once(), rule.graphX(),
                rule.graphY())));
    }

    private void toggleOnce() {
        NpcCombatRule rule = currentRule();
        if (rule == null) {
            return;
        }
        replaceRule(new NpcCombatRule(rule.trigger(), rule.conditions(), rule.actions(),
                rule.cooldownSeconds(), !rule.once(), rule.graphX(), rule.graphY()));
        onceButton.setMessage(Component.translatable("gui.luna_npc.combat.rule.once").copy().append(": ")
                .append(!rule.once() ? CommonComponents.GUI_YES : CommonComponents.GUI_NO));
    }

    private void addCondition() {
        NpcCombatRule rule = currentRule();
        if (rule == null || rule.conditions().size() >= NpcValidation.MAX_COMBAT_CONDITIONS_GUI) {
            return;
        }
        captureView();
        NpcCombatCondition fresh = new NpcCombatCondition(
                new NpcValueSource(NpcValueSourceType.SELF_HP_PERCENT, 0.0F, 0.0F, 0, ""),
                NpcCombatComparator.LESS_EQUAL, NpcValueSource.constant(50.0F), false);
        Minecraft.getInstance().setScreenAndShow(new CombatConditionScreen(this, fresh, updated -> {
            NpcCombatRule live = currentRule();
            if (live == null) {
                return;
            }
            List<NpcCombatCondition> conditions = new ArrayList<>(live.conditions());
            conditions.add(updated);
            replaceRule(withConditions(live, conditions));
            conditionSelected = conditions.size() - 1;
        }));
    }

    private void editCondition() {
        NpcCombatRule rule = currentRule();
        if (rule == null || conditionSelected < 0 || conditionSelected >= rule.conditions().size()) {
            return;
        }
        int index = conditionSelected;
        captureView();
        Minecraft.getInstance().setScreenAndShow(new CombatConditionScreen(this,
                rule.conditions().get(index), updated -> {
                    NpcCombatRule live = currentRule();
                    if (live == null || index >= live.conditions().size()) {
                        return;
                    }
                    List<NpcCombatCondition> conditions = new ArrayList<>(live.conditions());
                    conditions.set(index, updated);
                    replaceRule(withConditions(live, conditions));
                }));
    }

    private void removeCondition() {
        NpcCombatRule rule = currentRule();
        if (rule == null || conditionSelected < 0 || conditionSelected >= rule.conditions().size()) {
            return;
        }
        List<NpcCombatCondition> conditions = new ArrayList<>(rule.conditions());
        conditions.remove(conditionSelected);
        replaceRule(withConditions(rule, conditions));
        conditionSelected = -1;
        rebuildConditions();
    }

    private void addAction() {
        NpcCombatRule rule = currentRule();
        if (rule == null || rule.actions().size() >= NpcValidation.MAX_COMBAT_ACTIONS_GUI) {
            return;
        }
        captureView();
        NpcCombatAction fresh = new NpcCombatAction(NpcCombatActionType.USE_ATTACK, NpcTargetSelector.PRIMARY,
                NpcCombatPlacement.atTarget(), NpcValueSource.constant(0.0F), 0, 0, "", List.of(), false);
        Minecraft.getInstance().setScreenAndShow(new CombatActionScreen(this, fresh, attackNames(), updated -> {
            NpcCombatRule live = currentRule();
            if (live == null) {
                return;
            }
            List<NpcCombatAction> actions = new ArrayList<>(live.actions());
            actions.add(updated);
            replaceRule(withActions(live, actions));
            actionSelected = actions.size() - 1;
        }));
    }

    private void editAction() {
        NpcCombatRule rule = currentRule();
        if (rule == null || actionSelected < 0 || actionSelected >= rule.actions().size()) {
            return;
        }
        int index = actionSelected;
        captureView();
        Minecraft.getInstance().setScreenAndShow(new CombatActionScreen(this, rule.actions().get(index),
                attackNames(), updated -> {
                    NpcCombatRule live = currentRule();
                    if (live == null || index >= live.actions().size()) {
                        return;
                    }
                    List<NpcCombatAction> actions = new ArrayList<>(live.actions());
                    actions.set(index, updated);
                    replaceRule(withActions(live, actions));
                }));
    }

    private void removeAction() {
        NpcCombatRule rule = currentRule();
        if (rule == null || actionSelected < 0 || actionSelected >= rule.actions().size()) {
            return;
        }
        List<NpcCombatAction> actions = new ArrayList<>(rule.actions());
        actions.remove(actionSelected);
        replaceRule(withActions(rule, actions));
        actionSelected = -1;
        rebuildActions();
    }

    private List<String> attackNames() {
        List<String> names = new ArrayList<>();
        for (NpcCombatAttack attack : attackLibrary) {
            names.add(attack.name());
        }
        return names;
    }

    private void rebuildConditions() {
        NpcCombatRule rule = currentRule();
        List<NpcCombatCondition> conditions = rule == null ? List.of() : rule.conditions();
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry chosen = null;
        for (int i = 0; i < conditions.size(); i++) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(String.valueOf(i),
                    CombatLabels.conditionSummary(conditions.get(i)),
                    value -> conditionSelected = Integer.parseInt(value));
            entries.add(entry);
            if (i == conditionSelected) {
                chosen = entry;
            }
        }
        if (chosen == null) {
            conditionSelected = -1;
        }
        conditionList.fill(entries, chosen);
    }

    private void rebuildActions() {
        NpcCombatRule rule = currentRule();
        List<NpcCombatAction> actions = rule == null ? List.of() : rule.actions();
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry chosen = null;
        for (int i = 0; i < actions.size(); i++) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(String.valueOf(i),
                    CombatLabels.actionSummary(actions.get(i)),
                    value -> actionSelected = Integer.parseInt(value));
            entries.add(entry);
            if (i == actionSelected) {
                chosen = entry;
            }
        }
        if (chosen == null) {
            actionSelected = -1;
        }
        actionList.fill(entries, chosen);
    }

    private NpcCombatRule currentRule() {
        return selectedIndex >= 0 && selectedIndex < rules.size() ? rules.get(selectedIndex) : null;
    }

    private void replaceRule(NpcCombatRule updated) {
        if (selectedIndex >= 0 && selectedIndex < rules.size()) {
            rules.set(selectedIndex, updated);
        }
    }

    private static NpcCombatRule withConditions(NpcCombatRule rule, List<NpcCombatCondition> conditions) {
        return new NpcCombatRule(rule.trigger(), conditions, rule.actions(), rule.cooldownSeconds(),
                rule.once(), rule.graphX(), rule.graphY());
    }

    private static NpcCombatRule withActions(NpcCombatRule rule, List<NpcCombatAction> actions) {
        return new NpcCombatRule(rule.trigger(), rule.conditions(), actions, rule.cooldownSeconds(),
                rule.once(), rule.graphX(), rule.graphY());
    }

    private void captureView() {
        viewX = graph.viewX();
        viewY = graph.viewY();
        viewZoom = graph.zoom();
    }

    private void cancel() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void onClose() {
        onDone.accept(new NpcAdvancedCombat(true, engagementRadius, List.copyOf(attackLibrary),
                List.copyOf(rules)));
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (currentRule() == null) {
            return;
        }
        panel(graphics, MARGIN, dockTop, this.width - 2 * MARGIN, dockHeight);
        int btnY = dockTop + dockHeight - 22;
        graphics.outline(colBx, rowTop + 11, colW, btnY - 4 - (rowTop + 11), PANEL_BORDER);
        graphics.outline(colCx, rowTop + 11, colW, btnY - 4 - (rowTop + 11), PANEL_BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        Component count = Component.translatable("gui.luna_npc.combat.graph.rule_count", rules.size());
        graphics.text(this.font, count, this.width - MARGIN - this.font.width(count), TOOLBAR_Y + 6, LABEL_COLOR);
        if (currentRule() == null) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.combat.graph.hint"),
                    this.width / 2, this.height - 46, LABEL_COLOR);
            return;
        }
        NpcCombatRule rule = currentRule();
        graphics.text(this.font, Component.translatable("gui.luna_npc.combat.rule.section"),
                colAx, rowTop, SECTION_COLOR);
        graphics.text(this.font, Component.translatable("gui.luna_npc.combat.rule.conditions",
                rule.conditions().size()), colBx, rowTop, SECTION_COLOR);
        graphics.text(this.font, Component.translatable("gui.luna_npc.combat.rule.actions",
                rule.actions().size()), colCx, rowTop, SECTION_COLOR);
    }
}
