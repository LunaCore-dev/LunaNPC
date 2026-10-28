package net.lunacore.luna_npc.client.screen;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcCombatComparator;
import net.lunacore.luna_npc.api.NpcCombatCondition;
import net.lunacore.luna_npc.api.NpcValueSource;

// One condition test: two value sources compared by an operator, optionally inverted by negate. Each side
// opens the shared value-source editor, so any live number can be tested against any other (or a constant).
public class CombatConditionScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 168;

    private final LunaScreen parent;
    private final Consumer<NpcCombatCondition> onDone;
    private NpcValueSource left;
    private NpcCombatComparator comparator;
    private NpcValueSource right;
    private boolean negate;

    private Button leftButton;
    private Button rightButton;

    public CombatConditionScreen(LunaScreen parent, NpcCombatCondition current,
                                 Consumer<NpcCombatCondition> onDone) {
        super(Component.translatable("gui.luna_npc.combat.condition.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.left = current.left();
        this.comparator = current.comparator();
        this.right = current.right();
        this.negate = current.negate();
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

        leftButton = addRenderableWidget(Button.builder(sideLabel(left), button -> editSide(true))
                .bounds(contentX, top + 36, fieldWidth, 20).build());

        addRenderableWidget(CycleButton.<NpcCombatComparator>builder(CombatLabels::comparatorText, comparator)
                .withValues(NpcCombatComparator.values())
                .create(contentX, top + 62, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.combat.condition.comparator"),
                        (button, selected) -> comparator = selected));

        rightButton = addRenderableWidget(Button.builder(sideLabel(right), button -> editSide(false))
                .bounds(contentX, top + 96, fieldWidth, 20).build());

        addRenderableWidget(CycleButton.booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, negate)
                .create(contentX, top + 118, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.combat.condition.negate"),
                        (button, value) -> negate = value));

        int buttonWidth = (fieldWidth - 6) / 2;
        int buttonY = top + PANEL_HEIGHT - 26;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL,
                        button -> Minecraft.getInstance().setScreenAndShow(parent))
                .bounds(contentX, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(contentX + buttonWidth + 6, buttonY, buttonWidth, 20).build());
    }

    private Component sideLabel(NpcValueSource source) {
        return CombatLabels.valueSource(source.type()).copy().append(" — ")
                .append(CombatLabels.valueSourceSummary(source));
    }

    private void editSide(boolean isLeft) {
        NpcValueSource current = isLeft ? left : right;
        Minecraft.getInstance().setScreenAndShow(new CombatValueSourceScreen(this, current, updated -> {
            if (isLeft) {
                left = updated;
            } else {
                right = updated;
            }
        }));
    }

    private void save() {
        onDone.accept(new NpcCombatCondition(left, comparator, right, negate));
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
        leftButton.setMessage(sideLabel(left));
        rightButton.setMessage(sideLabel(right));
        graphics.text(this.font, Component.translatable("gui.luna_npc.combat.condition.left"),
                panelX() + 12, panelY() + 26, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.luna_npc.combat.condition.right"),
                panelX() + 12, panelY() + 86, LABEL_COLOR);
    }
}
