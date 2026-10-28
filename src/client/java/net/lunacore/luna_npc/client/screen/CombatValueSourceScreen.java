package net.lunacore.luna_npc.client.screen;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcValueSource;
import net.lunacore.luna_npc.api.NpcValueSourceType;

// Edits one NpcValueSource — the number a condition side or an action amount reads at runtime. Only the
// fields the picked type actually uses are shown: value (CONSTANT / RANDOM low), value2 (RANDOM high),
// radius (the in-radius counts) and key (a flag or counter name); the live game sources need nothing more.
public class CombatValueSourceScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 172;

    private final Screen parent;
    private final Consumer<NpcValueSource> onDone;
    private NpcValueSourceType type;
    private float value;
    private float value2;
    private int radius;
    private String key;

    private EditBox valueField;
    private EditBox value2Field;
    private EditBox radiusField;
    private EditBox keyField;

    public CombatValueSourceScreen(Screen parent, NpcValueSource current, Consumer<NpcValueSource> onDone) {
        super(Component.translatable("gui.luna_npc.combat.value.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.type = current.type();
        this.value = current.value();
        this.value2 = current.value2();
        this.radius = current.radius();
        this.key = current.key();
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
        int fieldWidth = PANEL_WIDTH - 24;
        int top = panelY();

        addRenderableWidget(CycleButton.<NpcValueSourceType>builder(CombatLabels::valueSource, type)
                .withValues(NpcValueSourceType.values())
                .create(contentX, top + 26, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.combat.value.type"),
                        (button, selected) -> {
                            type = selected;
                            refreshVisibility();
                        }));

        valueField = numeric(contentX, top + 62, fieldWidth, value, v -> value = v);
        value2Field = numeric(contentX, top + 98, fieldWidth, value2, v -> value2 = v);
        radiusField = numeric(contentX, top + 62, fieldWidth, radius, v -> radius = Math.max(0, Math.round(v)));
        keyField = addRenderableWidget(new EditBox(this.font, contentX, top + 62, fieldWidth, 20,
                Component.translatable("gui.luna_npc.combat.value.key")));
        keyField.setValue(key);
        keyField.setResponder(text -> key = text);

        int buttonWidth = (fieldWidth - 6) / 2;
        int buttonY = top + PANEL_HEIGHT - 28;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(contentX, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(contentX + buttonWidth + 6, buttonY, buttonWidth, 20).build());

        refreshVisibility();
    }

    private EditBox numeric(int x, int y, int width, float initial, Consumer<Float> onValue) {
        EditBox field = addRenderableWidget(new EditBox(this.font, x, y, width, 20, Component.empty()));
        field.setValue(CombatText.number(initial));
        field.setResponder(text -> CombatText.parse(text).ifPresent(onValue));
        return field;
    }

    private void refreshVisibility() {
        boolean showValue = type == NpcValueSourceType.CONSTANT || type == NpcValueSourceType.RANDOM;
        boolean showValue2 = type == NpcValueSourceType.RANDOM;
        boolean showRadius = type == NpcValueSourceType.HOSTILES_IN_RADIUS
                || type == NpcValueSourceType.PLAYERS_IN_RADIUS;
        boolean showKey = type == NpcValueSourceType.FLAG_VALUE || type == NpcValueSourceType.COUNTER_VALUE;
        valueField.visible = showValue;
        value2Field.visible = showValue2;
        radiusField.visible = showRadius;
        keyField.visible = showKey;
    }

    private void save() {
        onDone.accept(new NpcValueSource(type, value, value2, radius, key));
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
        int labelX = panelX() + 12;
        if (valueField.visible) {
            graphics.text(this.font, Component.translatable(type == NpcValueSourceType.RANDOM
                    ? "gui.luna_npc.combat.value.low" : "gui.luna_npc.combat.value.value"),
                    labelX, panelY() + 52, LABEL_COLOR);
        }
        if (value2Field.visible) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.combat.value.high"),
                    labelX, panelY() + 88, LABEL_COLOR);
        }
        if (radiusField.visible) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.combat.value.radius"),
                    labelX, panelY() + 52, LABEL_COLOR);
        }
        if (keyField.visible) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.combat.value.key"),
                    labelX, panelY() + 52, LABEL_COLOR);
        }
    }
}
