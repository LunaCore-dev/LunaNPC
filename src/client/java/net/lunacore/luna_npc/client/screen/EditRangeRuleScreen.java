package net.lunacore.luna_npc.client.screen;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.widget.LabeledSlider;
import net.lunacore.luna_npc.api.NpcAttackRange;
import net.lunacore.luna_npc.api.NpcRangeComparator;
import net.lunacore.luna_npc.api.NpcRangeMetric;
import net.lunacore.luna_npc.api.NpcRangeRule;
import net.lunacore.luna_npc.npc.NpcValidation;

// One range-switch rule: its metric, comparator and threshold, plus the range it selects. The value
// slider's range and unit follow the metric; radius only shows for ENEMIES_IN_RADIUS, the one metric
// that reads the world.
public class EditRangeRuleScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 194;

    private final LunaScreen parent;
    private final Consumer<NpcRangeRule> onDone;
    private final int maxHp;
    private NpcRangeMetric metric;
    private NpcRangeComparator comparator;
    private float value;
    private int radius;
    private NpcAttackRange result;

    private CycleButton<NpcRangeComparator> comparatorButton;
    private LabeledSlider valueSlider;
    private LabeledSlider radiusSlider;
    private int contentX;
    private int fieldWidth;

    public EditRangeRuleScreen(LunaScreen parent, NpcRangeRule current, int maxHp,
                               Consumer<NpcRangeRule> onDone) {
        super(Component.translatable("gui.luna_npc.rangeswitch.rule.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.maxHp = maxHp;
        this.metric = current.metric();
        this.comparator = current.comparator();
        this.value = current.value();
        this.radius = current.radius();
        this.result = current.result();
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }

    @Override
    protected void init() {
        contentX = panelX() + 12;
        fieldWidth = PANEL_WIDTH - 24;
        int top = panelY();

        addRenderableWidget(CycleButton.<NpcRangeMetric>builder(this::metricLabel, metric)
                .withValues(NpcRangeMetric.values())
                .create(contentX, top + 26, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.rangeswitch.rule.metric"),
                        (button, selected) -> {
                            metric = selected;
                            rebuildValueSlider();
                            rebuildRadiusSlider();
                            refreshComparatorVisibility();
                        }));

        comparatorButton = addRenderableWidget(CycleButton.<NpcRangeComparator>builder(this::comparatorLabel, comparator)
                .withValues(NpcRangeComparator.values())
                .create(contentX, top + 52, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.rangeswitch.rule.comparator"),
                        (button, selected) -> comparator = selected));

        rebuildValueSlider();
        rebuildRadiusSlider();
        refreshComparatorVisibility();

        addRenderableWidget(CycleButton.<NpcAttackRange>builder(this::rangeLabel, result)
                .withValues(NpcAttackRange.values())
                .create(contentX, top + 130, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.rangeswitch.rule.result"),
                        (button, selected) -> result = selected));

        int buttonWidth = (fieldWidth - 6) / 2;
        int buttonY = top + PANEL_HEIGHT - 28;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL,
                        button -> Minecraft.getInstance().setScreenAndShow(parent))
                .bounds(contentX, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(contentX + buttonWidth + 6, buttonY, buttonWidth, 20).build());
    }

    private void rebuildValueSlider() {
        if (valueSlider != null) {
            removeWidget(valueSlider);
            valueSlider = null;
        }
        if (metric == NpcRangeMetric.OTHERWISE) {
            return;
        }
        float max = switch (metric) {
            case SELF_HP -> maxHp;
            case TARGET_HP_PERCENT -> 100.0F;
            case DISTANCE -> NpcValidation.MAX_RANGE_RULE_DISTANCE;
            case ENEMIES_IN_RADIUS -> NpcValidation.MAX_RANGE_ENEMY_COUNT;
            case OTHERWISE -> 0.0F;
        };
        value = Math.clamp(value, 0.0F, max);
        boolean percent = metric == NpcRangeMetric.TARGET_HP_PERCENT;
        valueSlider = new LabeledSlider(contentX, panelY() + 78, fieldWidth, 20,
                Component.translatable("gui.luna_npc.rangeswitch.rule.value"),
                LabeledSlider.Curve.lowBiased(0, max), value,
                v -> percent ? Math.round(v) + "%" : String.valueOf(Math.round(v)),
                v -> value = Math.round(v));
        addRenderableWidget(valueSlider);
    }

    private void rebuildRadiusSlider() {
        if (radiusSlider != null) {
            removeWidget(radiusSlider);
            radiusSlider = null;
        }
        if (metric != NpcRangeMetric.ENEMIES_IN_RADIUS) {
            return;
        }
        radius = Math.clamp(radius, 1, NpcValidation.MAX_RANGE_RULE_RADIUS);
        radiusSlider = new LabeledSlider(contentX, panelY() + 104, fieldWidth, 20,
                Component.translatable("gui.luna_npc.rangeswitch.rule.radius"),
                LabeledSlider.Curve.lowBiased(1, NpcValidation.MAX_RANGE_RULE_RADIUS), radius,
                v -> String.valueOf(Math.round(v)), v -> radius = Math.round(v));
        addRenderableWidget(radiusSlider);
    }

    private Component metricLabel(NpcRangeMetric metric) {
        return Component.translatable("gui.luna_npc.rangeswitch.metric." + metric.getSerializedName());
    }

    private Component comparatorLabel(NpcRangeComparator comparator) {
        return Component.translatable("gui.luna_npc.rangeswitch.comparator." + comparator.getSerializedName());
    }

    private Component rangeLabel(NpcAttackRange range) {
        return Component.translatable("gui.luna_npc.rangeswitch.range." + range.getSerializedName());
    }

    // OTHERWISE always passes, so its comparator and threshold are meaningless — hide them.
    private void refreshComparatorVisibility() {
        if (comparatorButton != null) {
            comparatorButton.visible = metric != NpcRangeMetric.OTHERWISE;
        }
    }

    private void save() {
        onDone.accept(new NpcRangeRule(metric, comparator, value, radius, result));
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (metric == NpcRangeMetric.OTHERWISE) {
            graphics.textWithWordWrap(this.font,
                    Component.translatable("gui.luna_npc.rangeswitch.rule.otherwise_hint"),
                    panelX() + 12, panelY() + 80, PANEL_WIDTH - 24, 0xFFAAAAAA);
        }
    }
}
