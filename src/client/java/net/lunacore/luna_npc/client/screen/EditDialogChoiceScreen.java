package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.api.NpcDialogChoice;
import net.lunacore.luna_npc.api.NpcDialogCondition;
import net.lunacore.luna_npc.api.NpcDialogConditionType;
import net.lunacore.luna_npc.npc.NpcValidation;

// One answer choice's detail: the button label, the hide-vs-grey behaviour when locked, and its unbounded
// (scrolling) condition list — all conditions must pass for the choice to be offered. The route this
// choice takes is wired on the graph, so its targetNodeId is carried through untouched here.
public class EditDialogChoiceScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 226;
    private static final int LIST_HEIGHT = 84;

    private final LunaScreen parent;
    private final Consumer<NpcDialogChoice> onApply;
    private final String targetNodeId;
    private String text;
    private boolean hideWhenLocked;
    private final List<NpcDialogCondition> conditions;
    private int conditionSelected = -1;

    private SimpleSelectionList conditionList;

    public EditDialogChoiceScreen(LunaScreen parent, NpcDialogChoice choice,
                                  Consumer<NpcDialogChoice> onApply) {
        super(Component.translatable("gui.luna_npc.dialog.choice.title"));
        this.parent = parent;
        this.onApply = onApply;
        this.text = choice.text();
        this.targetNodeId = choice.targetNodeId();
        this.hideWhenLocked = choice.hideWhenLocked();
        this.conditions = new ArrayList<>(choice.conditions());
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
        int innerW = PANEL_WIDTH - 28;
        int top = panelY();

        EditBox textField = addRenderableWidget(new EditBox(this.font, contentX, top + 28, innerW, 16,
                Component.translatable("gui.luna_npc.dialog.choice.text")));
        textField.setMaxLength(NpcValidation.MAX_DIALOG_CHOICE_LENGTH);
        textField.setValue(text);
        textField.setResponder(value -> text = value);

        addRenderableWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, hideWhenLocked)
                .displayOnlyValue().create(contentX + innerW - 70, top + 50, 70, 18,
                        Component.translatable("gui.luna_npc.dialog.choice.hide_when_locked"),
                        (button, value) -> hideWhenLocked = value));

        conditionList = addRenderableWidget(new SimpleSelectionList(innerW, LIST_HEIGHT, top + 88));
        conditionList.setX(contentX);

        int bw = (innerW - 4) / 3;
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.dialog.choice.add_condition"),
                button -> addCondition()).bounds(contentX, top + 176, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.dialog.choice.edit_condition"),
                button -> editCondition()).bounds(contentX + bw + 2, top + 176, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.dialog.choice.remove_condition"),
                button -> removeCondition()).bounds(contentX + 2 * (bw + 2), top + 176, bw, 18).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 50, top + PANEL_HEIGHT - 26, 100, 20).build());

        rebuildConditions();
    }

    private void addCondition() {
        NpcDialogCondition condition = new NpcDialogCondition(NpcDialogConditionType.QUEST_COMPLETED, "", 1, false);
        Minecraft.getInstance().setScreenAndShow(new EditDialogConditionScreen(this, condition, updated -> {
            conditions.add(updated);
            conditionSelected = conditions.size() - 1;
        }));
    }

    private void editCondition() {
        if (conditionSelected < 0) {
            return;
        }
        int index = conditionSelected;
        Minecraft.getInstance().setScreenAndShow(new EditDialogConditionScreen(this, conditions.get(index),
                updated -> conditions.set(index, updated)));
    }

    private void removeCondition() {
        if (conditionSelected < 0) {
            return;
        }
        conditions.remove(conditionSelected);
        conditionSelected = -1;
        rebuildConditions();
    }

    private void rebuildConditions() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (int i = 0; i < conditions.size(); i++) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(String.valueOf(i),
                    conditionSummary(conditions.get(i)), value -> conditionSelected = Integer.parseInt(value));
            entries.add(entry);
            if (i == conditionSelected) {
                selected = entry;
            }
        }
        if (selected == null) {
            conditionSelected = -1;
        }
        conditionList.fill(entries, selected);
    }

    private static Component conditionSummary(NpcDialogCondition condition) {
        Component type = Component.translatable(
                "gui.luna_npc.dialog.condition.type." + condition.type().getSerializedName());
        Component body = condition.negate()
                ? Component.translatable("gui.luna_npc.dialog.condition.negated", type) : type;
        return condition.stringArg().isBlank()
                ? body : body.copy().append(": ").append(Component.literal(condition.stringArg()));
    }

    @Override
    public void onClose() {
        onApply.accept(new NpcDialogChoice(text, targetNodeId, List.copyOf(conditions), hideWhenLocked));
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
        graphics.outline(panelX() + 14, panelY() + 88, PANEL_WIDTH - 28, LIST_HEIGHT, PANEL_BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int contentX = panelX() + 14;
        int top = panelY();
        graphics.text(this.font, Component.translatable("gui.luna_npc.dialog.choice.text"),
                contentX, top + 16, SECTION_COLOR);
        graphics.text(this.font, Component.translatable("gui.luna_npc.dialog.choice.hide_when_locked"),
                contentX, top + 54, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.luna_npc.dialog.choice.conditions", conditions.size()),
                contentX, top + 76, SECTION_COLOR);
    }
}
