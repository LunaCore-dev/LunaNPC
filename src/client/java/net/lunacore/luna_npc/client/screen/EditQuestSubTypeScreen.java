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
import net.lunacore.luna_npc.api.NpcQuest;
import net.lunacore.luna_npc.api.NpcQuestCompletion;
import net.lunacore.luna_npc.api.NpcQuestSubType;
import net.lunacore.luna_npc.npc.NpcValidation;

// One weighted quest variant: its probability weight plus the quests it offers. The nested
// EditQuestScreen edits a single quest; this screen owns the quest list and hands the rebuilt variant
// back to the Jobs tab on close. Duplicating a quest re-generates its id so per-player progress never
// collides between the copies.
public class EditQuestSubTypeScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 236;
    private static final int LIST_TOP_OFFSET = 54;
    private static final int LIST_HEIGHT = 112;

    private final LunaScreen parent;
    private final Consumer<NpcQuestSubType> onApply;
    private final List<NpcQuest> quests;
    private int weight;
    private int selectedIndex = -1;

    private SimpleSelectionList questList;
    private Button removeButton;
    private Button duplicateButton;
    private Button editButton;

    public EditQuestSubTypeScreen(LunaScreen parent, NpcQuestSubType subType,
                                  Consumer<NpcQuestSubType> onApply) {
        super(Component.translatable("gui.luna_npc.edit_quest_subtype.title"));
        this.parent = parent;
        this.onApply = onApply;
        this.weight = subType.weightPercent();
        this.quests = new ArrayList<>(subType.quests());
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
                value -> String.valueOf(Math.round(value)), value -> weight = Math.round(value));
        weightSlider.setTooltip(Tooltip.create(
                Component.translatable("gui.luna_npc.edit_subtype.weight_relative.tooltip")));
        addRenderableWidget(weightSlider);

        questList = addRenderableWidget(new SimpleSelectionList(PANEL_WIDTH - 24, LIST_HEIGHT, top + LIST_TOP_OFFSET));
        questList.setX(contentX);

        int buttonWidth = (PANEL_WIDTH - 24 - 9) / 4;
        int buttonY = top + LIST_TOP_OFFSET + LIST_HEIGHT + 8;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.luna_npc.edit_quest_subtype.add_quest"), button -> addQuest())
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
        for (int i = 0; i < quests.size(); i++) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(String.valueOf(i),
                    summarize(quests.get(i)), value -> selectedIndex = Integer.parseInt(value));
            entries.add(entry);
            if (i == selectedIndex) {
                selected = entry;
            }
        }
        if (selected == null) {
            selectedIndex = -1;
        }
        questList.fill(entries, selected);
        refreshButtons();
    }

    private void refreshButtons() {
        boolean hasSelection = selectedIndex >= 0;
        removeButton.active = hasSelection;
        duplicateButton.active = hasSelection;
        editButton.active = hasSelection;
    }

    private static NpcQuest emptyQuest() {
        return NpcQuest.create("Quest", List.of(), NpcQuestCompletion.TURN_IN, 0, 0, 0, List.of());
    }

    private void addQuest() {
        Minecraft.getInstance().setScreenAndShow(new EditQuestScreen(this, emptyQuest(),
                new ArrayList<>(quests), quest -> {
                    quests.add(quest);
                    selectedIndex = quests.size() - 1;
                }));
    }

    private void editSelected() {
        if (selectedIndex < 0) {
            return;
        }
        int index = selectedIndex;
        Minecraft.getInstance().setScreenAndShow(new EditQuestScreen(this, quests.get(index),
                new ArrayList<>(quests), quest -> quests.set(index, quest)));
    }

    private void duplicateSelected() {
        if (selectedIndex < 0) {
            return;
        }
        NpcQuest source = quests.get(selectedIndex);
        quests.add(selectedIndex + 1, NpcQuest.create(source.name(), source.title(),
                source.description(), source.objectives(), source.completion(),
                source.timeLimitSeconds(), source.xpMin(), source.xpMax(), source.itemRewards(),
                source.cancelOnNpcDeath(), source.prerequisiteQuestIds()));
        selectedIndex++;
        rebuildList();
    }

    private void removeSelected() {
        if (selectedIndex < 0) {
            return;
        }
        quests.remove(selectedIndex);
        selectedIndex = -1;
        rebuildList();
    }

    private static Component summarize(NpcQuest quest) {
        Component name = quest.name().isBlank()
                ? Component.translatable("gui.luna_npc.edit_quest.title") : Component.literal(quest.name());
        return name.copy().append(" (" + quest.objectives().size() + ")");
    }

    @Override
    public void onClose() {
        onApply.accept(new NpcQuestSubType(weight, quests));
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
        graphics.text(this.font, Component.translatable("gui.luna_npc.edit_quest_subtype.quests", quests.size()),
                panelX() + 12, panelY() + 42, SECTION_COLOR);
    }
}
