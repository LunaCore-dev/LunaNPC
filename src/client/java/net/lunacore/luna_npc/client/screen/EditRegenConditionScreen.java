package net.lunacore.luna_npc.client.screen;

import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.widget.LabeledSlider;
import net.lunacore.luna_npc.api.NpcRegenCondition;
import net.lunacore.luna_npc.api.NpcRegenConditionType;
import net.lunacore.luna_npc.npc.NpcValidation;

// One regeneration rule: its gate type and (for value-carrying types) threshold, plus its own interval
// and heal amount. The value slider's range and unit follow the type; OUT_OF_COMBAT hides it.
public class EditRegenConditionScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 168;

    private final LunaScreen parent;
    private final Consumer<NpcRegenCondition> onDone;
    private final int maxHp;
    private NpcRegenConditionType type;
    private int value;
    private float intervalSeconds;
    private int amount;

    private LabeledSlider valueSlider;
    private int contentX;
    private int fieldWidth;

    public EditRegenConditionScreen(LunaScreen parent, NpcRegenCondition current, int maxHp,
                                    Consumer<NpcRegenCondition> onDone) {
        super(Component.translatable("gui.luna_npc.regen.condition.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.maxHp = maxHp;
        this.type = current.type();
        this.value = current.value();
        this.intervalSeconds = current.intervalSeconds();
        this.amount = current.amount();
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

        addRenderableWidget(CycleButton.<NpcRegenConditionType>builder(this::typeLabel, type)
                .withValues(NpcRegenConditionType.values())
                .create(contentX, top + 26, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.regen.condition.type"),
                        (button, selected) -> {
                            type = selected;
                            rebuildValueSlider();
                        }));

        rebuildValueSlider();

        addRenderableWidget(new LabeledSlider(contentX, top + 78, fieldWidth, 20,
                Component.translatable("gui.luna_npc.regen.interval"),
                LabeledSlider.Curve.lowBiased(0, NpcValidation.MAX_REGEN_INTERVAL_SECONDS), intervalSeconds,
                v -> String.format(Locale.ROOT, "%.1fs", Math.round(v * 10) / 10.0),
                v -> intervalSeconds = Math.round(v * 10) / 10.0F));

        addRenderableWidget(new LabeledSlider(contentX, top + 104, fieldWidth, 20,
                Component.translatable("gui.luna_npc.regen.amount"),
                LabeledSlider.Curve.lowBiased(1, NpcValidation.MAX_REGEN_AMOUNT), amount,
                v -> String.valueOf(Math.round(v)), v -> amount = Math.round(v)));

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
        if (type == NpcRegenConditionType.OUT_OF_COMBAT) {
            return;
        }
        int max = type == NpcRegenConditionType.NO_DAMAGE_SECONDS
                ? NpcValidation.MAX_REGEN_NO_DAMAGE_SECONDS : maxHp;
        value = Math.clamp(value, 0, max);
        boolean seconds = type == NpcRegenConditionType.NO_DAMAGE_SECONDS;
        valueSlider = new LabeledSlider(contentX, panelY() + 52, fieldWidth, 20,
                Component.translatable("gui.luna_npc.regen.condition.value"),
                LabeledSlider.Curve.lowBiased(0, max), value,
                v -> seconds ? Math.round(v) + "s" : String.valueOf(Math.round(v)),
                v -> value = Math.round(v));
        addRenderableWidget(valueSlider);
    }

    private Component typeLabel(NpcRegenConditionType type) {
        return Component.translatable("gui.luna_npc.regen.condition.type." + type.getSerializedName());
    }

    private void save() {
        int stored = type == NpcRegenConditionType.OUT_OF_COMBAT ? 0 : value;
        onDone.accept(new NpcRegenCondition(type, stored, intervalSeconds, amount));
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
}
