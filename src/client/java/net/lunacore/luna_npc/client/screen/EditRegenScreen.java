package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.api.NpcRegenCondition;
import net.lunacore.luna_npc.api.NpcRegenConditionType;
import net.lunacore.luna_npc.api.NpcRegenSettings;
import net.lunacore.luna_npc.npc.NpcValidation;

// Compact popup for the Traits tab's regeneration settings. Each condition is an independent regen rule
// with its own rate. Edits a working copy and only commits it through onDone when Save is pressed.
public class EditRegenScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 214;
    private static final int LIST_HEIGHT = 96;

    private final Screen parent;
    private final Consumer<NpcRegenSettings> onDone;
    private final int maxHp;

    private boolean enabled;
    private final List<NpcRegenCondition> conditions;
    private int conditionSelected = -1;

    private SimpleSelectionList conditionList;
    private Button addButton;

    public EditRegenScreen(Screen parent, NpcRegenSettings current, int maxHp,
                           Consumer<NpcRegenSettings> onDone) {
        super(Component.translatable("gui.luna_npc.regen.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.maxHp = maxHp;
        this.enabled = current.enabled();
        this.conditions = new ArrayList<>(current.conditions());
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
                        Component.translatable("gui.luna_npc.regen.enabled"),
                        (button, value) -> enabled = value));

        conditionList = addRenderableWidget(new SimpleSelectionList(fieldWidth, LIST_HEIGHT, top + 56));
        conditionList.setX(contentX);

        int bw = (fieldWidth - 4) / 3;
        addButton = Button.builder(Component.translatable("gui.luna_npc.regen.add_condition"),
                button -> addCondition()).bounds(contentX, top + 156, bw, 18).build();
        addRenderableWidget(addButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.regen.edit_condition"),
                button -> editCondition()).bounds(contentX + bw + 2, top + 156, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.regen.remove_condition"),
                button -> removeCondition()).bounds(contentX + 2 * (bw + 2), top + 156, bw, 18).build());

        int buttonWidth = (fieldWidth - 6) / 2;
        int buttonY = top + PANEL_HEIGHT - 28;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(contentX, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(contentX + buttonWidth + 6, buttonY, buttonWidth, 20).build());

        rebuildConditions();
    }

    private void addCondition() {
        if (conditions.size() >= NpcValidation.MAX_REGEN_CONDITIONS_GUI) {
            return;
        }
        NpcRegenCondition condition = new NpcRegenCondition(NpcRegenConditionType.OUT_OF_COMBAT, 0, 1.0F, 1);
        Minecraft.getInstance().setScreenAndShow(new EditRegenConditionScreen(this, condition, maxHp, updated -> {
            conditions.add(updated);
            conditionSelected = conditions.size() - 1;
        }));
    }

    private void editCondition() {
        if (conditionSelected < 0) {
            return;
        }
        int index = conditionSelected;
        Minecraft.getInstance().setScreenAndShow(new EditRegenConditionScreen(this, conditions.get(index), maxHp,
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
        addButton.active = conditions.size() < NpcValidation.MAX_REGEN_CONDITIONS_GUI;
    }

    private static Component conditionSummary(NpcRegenCondition condition) {
        Component gate = switch (condition.type()) {
            case OUT_OF_COMBAT -> Component.translatable("gui.luna_npc.regen.condition.summary.out_of_combat");
            case NO_DAMAGE_SECONDS ->
                    Component.translatable("gui.luna_npc.regen.condition.summary.no_damage", condition.value());
            case HP_BELOW -> Component.translatable("gui.luna_npc.regen.condition.summary.hp_below", condition.value());
            case HP_ABOVE -> Component.translatable("gui.luna_npc.regen.condition.summary.hp_above", condition.value());
        };
        String rate = String.format(Locale.ROOT, "  →  +%d / %.1fs",
                condition.amount(), condition.intervalSeconds());
        return gate.copy().append(Component.literal(rate));
    }

    private void save() {
        onDone.accept(new NpcRegenSettings(enabled, List.copyOf(conditions)));
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
        graphics.outline(panelX() + 12, panelY() + 56, PANEL_WIDTH - 24, LIST_HEIGHT, PANEL_BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, Component.translatable("gui.luna_npc.regen.conditions", conditions.size()),
                panelX() + 12, panelY() + 44, SECTION_COLOR);
    }
}
