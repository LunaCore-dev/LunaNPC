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
import net.lunacore.luna_npc.api.NpcCombatTrigger;
import net.lunacore.luna_npc.api.NpcCombatTriggerType;

// Picks what fires a rule and shows only that trigger's parameter: seconds for the interval/enrage timers,
// a threshold for the crossing triggers (HP %, surround count, damage burst) and a key for the ones that
// listen on a named timer or status. Event triggers that need nothing else show just their type.
public class CombatTriggerScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 150;

    private final Screen parent;
    private final Consumer<NpcCombatTrigger> onDone;
    private NpcCombatTriggerType type;
    private float seconds;
    private float threshold;
    private String key;

    private EditBox secondsField;
    private EditBox thresholdField;
    private EditBox keyField;

    public CombatTriggerScreen(Screen parent, NpcCombatTrigger current, Consumer<NpcCombatTrigger> onDone) {
        super(Component.translatable("gui.luna_npc.combat.trigger.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.type = current.type();
        this.seconds = current.seconds();
        this.threshold = current.threshold();
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

        addRenderableWidget(CycleButton.<NpcCombatTriggerType>builder(CombatLabels::trigger, type)
                .withValues(NpcCombatTriggerType.values())
                .create(contentX, top + 26, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.combat.trigger.type"),
                        (button, selected) -> {
                            type = selected;
                            refreshVisibility();
                        }));

        secondsField = numeric(contentX, top + 62, fieldWidth, seconds, v -> seconds = Math.max(0.0F, v));
        thresholdField = numeric(contentX, top + 62, fieldWidth, threshold, v -> threshold = v);
        keyField = addRenderableWidget(new EditBox(this.font, contentX, top + 62, fieldWidth, 20,
                Component.translatable("gui.luna_npc.combat.trigger.key")));
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

    private boolean usesSeconds() {
        return type == NpcCombatTriggerType.INTERVAL || type == NpcCombatTriggerType.ON_ENRAGE_TIMER;
    }

    private boolean usesThreshold() {
        return type == NpcCombatTriggerType.ON_HP_CROSSES || type == NpcCombatTriggerType.ON_SURROUNDED
                || type == NpcCombatTriggerType.ON_DAMAGE_BURST;
    }

    private boolean usesKey() {
        return type == NpcCombatTriggerType.ON_TIMER_ELAPSED || type == NpcCombatTriggerType.ON_STATUS_APPLIED;
    }

    private void refreshVisibility() {
        secondsField.visible = usesSeconds();
        thresholdField.visible = usesThreshold();
        keyField.visible = usesKey();
    }

    private void save() {
        onDone.accept(new NpcCombatTrigger(type, seconds, threshold, key));
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
        int labelY = panelY() + 52;
        if (secondsField.visible) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.combat.trigger.seconds"),
                    labelX, labelY, LABEL_COLOR);
        } else if (thresholdField.visible) {
            graphics.text(this.font, Component.translatable(thresholdLabelKey()), labelX, labelY, LABEL_COLOR);
        } else if (keyField.visible) {
            graphics.text(this.font, Component.translatable(type == NpcCombatTriggerType.ON_STATUS_APPLIED
                    ? "gui.luna_npc.combat.trigger.status" : "gui.luna_npc.combat.trigger.timer"),
                    labelX, labelY, LABEL_COLOR);
        }
    }

    private String thresholdLabelKey() {
        return switch (type) {
            case ON_HP_CROSSES -> "gui.luna_npc.combat.trigger.hp_percent";
            case ON_SURROUNDED -> "gui.luna_npc.combat.trigger.count";
            default -> "gui.luna_npc.combat.trigger.damage";
        };
    }
}
