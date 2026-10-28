package net.lunacore.luna_npc.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.client.widget.ItemSlotButton;
import net.lunacore.luna_npc.client.widget.RangeSlider;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.api.NpcObjectiveType;
import net.lunacore.luna_npc.api.NpcQuest;
import net.lunacore.luna_npc.api.NpcQuestCompletion;
import net.lunacore.luna_npc.api.NpcQuestObjective;
import net.lunacore.luna_npc.api.NpcTradeStack;
import net.lunacore.luna_npc.npc.NpcEquipment;
import net.lunacore.luna_npc.npc.NpcValidation;

// One quest: a short editor name plus optional display title/description, its bundled objectives
// (kill mobs/players, collect items, enter a biome/structure/dimension), a completion mode, an
// optional time limit, an XP range, up to 27 item rewards, a cancel-on-NPC-death toggle, and the
// prerequisite quests that gate the offer (chosen from the other quests in the same variant). AUTO
// completion is refused when a collect objective exists (consumed at turn-in), matching
// NpcQuest.isValid. Hands the rebuilt quest back on close; the quest id is preserved so per-player
// progress keyed on it survives an edit.
public class EditQuestScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 390;
    private static final int PANEL_HEIGHT = 302;
    private static final int OBJ_LIST_HEIGHT = 86;
    private static final int REWARD_COLUMNS = 9;
    private static final int REWARD_ROWS = 3;
    private static final int[] UNIT_FACTORS = {1, 60, 3600};
    private static final String[] UNIT_KEYS = {"seconds", "minutes", "hours"};

    private final LunaScreen parent;
    private final Consumer<NpcQuest> onApply;
    private final String questId;
    private final List<NpcQuest> siblingQuests;
    private String name;
    private String questTitle;
    private String questDescription;
    private boolean cancelOnNpcDeath;
    private final Set<String> prerequisiteQuestIds = new LinkedHashSet<>();
    private final List<NpcQuestObjective> objectives;
    private NpcQuestCompletion completion;
    private int xpMin;
    private int xpMax;
    private final String[] rewardIds = new String[NpcValidation.MAX_QUEST_REWARD_SLOTS];
    private final int[] rewardCounts = new int[NpcValidation.MAX_QUEST_REWARD_SLOTS];
    private int timeValue;
    private int timeUnit;
    private NpcObjectiveType addType = NpcObjectiveType.KILL_MOB;
    private int objectiveSelected = -1;
    private int rewardSelected = -1;
    // Guards suppress the field responder while code re-sets a field value (setValue fires it).
    private boolean populatingReward;
    private boolean populatingAmount;

    private SimpleSelectionList objectiveList;
    private Button objectiveTypeButton;
    private Button removeObjectiveButton;
    private Button targetButton;
    private EditBox amountField;
    private Button weaponButton;
    private Button completionButton;
    private CycleButton<Boolean> cancelDeathButton;
    private Button prerequisitesButton;
    private EditBox timeField;
    private Button unitButton;
    private final ItemSlotButton[] rewardSlots = new ItemSlotButton[REWARD_COLUMNS * REWARD_ROWS];
    private EditBox rewardCountField;

    public EditQuestScreen(LunaScreen parent, NpcQuest quest, List<NpcQuest> siblingQuests,
                           Consumer<NpcQuest> onApply) {
        super(Component.translatable("gui.luna_npc.edit_quest.title"));
        this.parent = parent;
        this.onApply = onApply;
        this.siblingQuests = siblingQuests;
        this.questId = quest.questId();
        this.name = quest.name();
        this.questTitle = quest.title();
        this.questDescription = quest.description();
        this.cancelOnNpcDeath = quest.cancelOnNpcDeath();
        this.prerequisiteQuestIds.addAll(quest.prerequisiteQuestIds());
        this.objectives = new ArrayList<>(quest.objectives());
        this.completion = quest.completion();
        this.xpMin = quest.xpMin();
        this.xpMax = quest.xpMax();
        for (int i = 0; i < rewardIds.length; i++) {
            if (i < quest.itemRewards().size()) {
                rewardIds[i] = quest.itemRewards().get(i).itemId();
                rewardCounts[i] = quest.itemRewards().get(i).count();
            } else {
                rewardIds[i] = "";
                rewardCounts[i] = 1;
            }
        }
        seedTimeUnit(quest.timeLimitSeconds());
    }

    private void seedTimeUnit(int seconds) {
        if (seconds > 0 && seconds % 3600 == 0) {
            timeUnit = 2;
        } else if (seconds > 0 && seconds % 60 == 0) {
            timeUnit = 1;
        } else {
            timeUnit = 0;
        }
        timeValue = seconds / UNIT_FACTORS[timeUnit];
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }

    @Override
    protected void init() {
        int left = panelX() + 14;
        int rLabel = panelX() + 200;
        int rField = panelX() + 272;
        int rFull = panelX() + PANEL_WIDTH - 18;
        int top = panelY();

        buildRightColumn(rLabel, rField, rFull, top);
        buildLeftColumn(left, top);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 50, top + PANEL_HEIGHT - 26, 100, 20).build());

        rebuildObjectives();
        refreshRewards();
    }

    private void buildLeftColumn(int left, int top) {
        objectiveList = addRenderableWidget(new SimpleSelectionList(172, OBJ_LIST_HEIGHT, top + 30));
        objectiveList.setX(left);

        objectiveTypeButton = addRenderableWidget(Button.builder(objectiveTypeLabel(), button -> {
            NpcObjectiveType[] types = NpcObjectiveType.values();
            addType = types[(addType.ordinal() + 1) % types.length];
            objectiveTypeButton.setMessage(objectiveTypeLabel());
        }).bounds(left, top + 120, 104, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.edit_quest.add"),
                button -> addObjective()).bounds(left + 108, top + 120, 64, 18).build());

        removeObjectiveButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.luna_npc.create_npc.remove_sub_type"), button -> removeObjective())
                .bounds(left, top + 142, 172, 18).build());
        targetButton = addRenderableWidget(Button.builder(Component.empty(), button -> changeTarget())
                .bounds(left, top + 164, 172, 18).build());
        amountField = new EditBox(this.font, left + 50, top + 186, 44, 16, Component.empty());
        amountField.setMaxLength(6);
        amountField.setResponder(this::onAmountTyped);
        addRenderableWidget(amountField);
        weaponButton = addRenderableWidget(Button.builder(Component.empty(), button -> changeWeapon())
                .bounds(left + 100, top + 186, 72, 18).build());
    }

    private void buildRightColumn(int rLabel, int rField, int rFull, int top) {
        int fieldW = rFull - rField;

        EditBox nameField = new EditBox(this.font, rField, top + 16, fieldW, 16, Component.empty());
        nameField.setMaxLength(48);
        nameField.setValue(name);
        nameField.setResponder(value -> name = value);
        addRenderableWidget(nameField);

        EditBox titleField = new EditBox(this.font, rField, top + 38, fieldW, 16, Component.empty());
        titleField.setMaxLength(64);
        titleField.setValue(questTitle);
        titleField.setResponder(value -> questTitle = value);
        addRenderableWidget(titleField);

        EditBox descriptionField = new EditBox(this.font, rField, top + 60, fieldW, 16, Component.empty());
        descriptionField.setMaxLength(256);
        descriptionField.setValue(questDescription);
        descriptionField.setResponder(value -> questDescription = value);
        addRenderableWidget(descriptionField);

        completionButton = addRenderableWidget(Button.builder(completionLabel(), button -> {
            completion = completion == NpcQuestCompletion.TURN_IN
                    ? NpcQuestCompletion.AUTO : NpcQuestCompletion.TURN_IN;
            completionButton.setMessage(completionLabel());
        }).bounds(rField, top + 82, fieldW, 18).build());

        cancelDeathButton = addRenderableWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, cancelOnNpcDeath)
                .displayOnlyValue().withTooltip(value -> Tooltip.create(
                        Component.translatable("gui.luna_npc.edit_quest.cancel_on_death.tooltip")))
                .create(rField, top + 104, fieldW, 18,
                        Component.translatable("gui.luna_npc.edit_quest.cancel_on_death"),
                        (button, value) -> cancelOnNpcDeath = value));

        timeField = new EditBox(this.font, rField, top + 126, 40, 16, Component.empty());
        timeField.setMaxLength(6);
        timeField.setValue(String.valueOf(timeValue));
        timeField.setResponder(value -> {
            String digits = CreateNpcScreen.digitsOnly(timeField, value);
            if (digits == null) {
                return;
            }
            timeValue = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            clampTime();
        });
        addRenderableWidget(timeField);
        unitButton = addRenderableWidget(Button.builder(unitLabel(), button -> {
            timeUnit = (timeUnit + 1) % UNIT_FACTORS.length;
            unitButton.setMessage(unitLabel());
            clampTime();
        }).bounds(rField + 44, top + 126, rFull - (rField + 44), 18).build());

        addRenderableWidget(new RangeSlider(rLabel, top + 160, rFull - rLabel, 14, Component.empty(),
                0, NpcValidation.MAX_JOB_XP, xpMin, xpMax, (min, max) -> {
            xpMin = min;
            xpMax = max;
        }));

        prerequisitesButton = addRenderableWidget(Button.builder(prerequisitesLabel(),
                button -> openPrerequisites()).bounds(rLabel, top + 178, rFull - rLabel, 18).build());

        buildRewardGrid(rLabel, top + 214);
        rewardCountField = new EditBox(this.font, rFull - 34, top + 198, 34, 16, Component.empty());
        rewardCountField.setMaxLength(2);
        rewardCountField.setResponder(value -> {
            if (populatingReward) {
                return;
            }
            String digits = CreateNpcScreen.digitsOnly(rewardCountField, value);
            if (digits == null || rewardSelected < 0) {
                return;
            }
            int parsed = digits.isEmpty() ? 1 : Integer.parseInt(digits);
            rewardCounts[rewardSelected] = Math.clamp(parsed, 1, NpcValidation.MAX_TRADE_SLOT_COUNT);
            refreshRewardSlots();
        });
        addRenderableWidget(rewardCountField);
    }

    private void buildRewardGrid(int gridLeft, int gridTop) {
        for (int index = 0; index < rewardSlots.length; index++) {
            int slotIndex = index;
            int x = gridLeft + (index % REWARD_COLUMNS) * ItemSlotButton.SIZE;
            int y = gridTop + (index / REWARD_COLUMNS) * ItemSlotButton.SIZE;
            rewardSlots[index] = addRenderableWidget(new ItemSlotButton(x, y, this.title, event -> {
                if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    rewardIds[slotIndex] = "";
                    if (rewardSelected == slotIndex) {
                        rewardSelected = -1;
                    }
                    refreshRewards();
                } else if (rewardIds[slotIndex] != null && !rewardIds[slotIndex].isBlank()) {
                    rewardSelected = slotIndex;
                    refreshRewards();
                } else {
                    Minecraft.getInstance().setScreenAndShow(new ItemPickerScreen(this, null, itemId -> {
                        if (!itemId.isEmpty()) {
                            rewardIds[slotIndex] = itemId;
                            rewardCounts[slotIndex] = Math.max(1, rewardCounts[slotIndex]);
                            rewardSelected = slotIndex;
                        }
                    }));
                }
            }));
        }
    }

    // The picker (if any) for a freshly added objective of the chosen type; KILL_PLAYERS has no
    // target (any player) and is added straight away.
    private void addObjective() {
        Consumer<String> onPicked = targetId -> {
            if (targetId != null && !targetId.isEmpty()) {
                placeObjective(new NpcQuestObjective(addType, targetId, 1, ""));
            }
        };
        switch (addType) {
            case KILL_MOB -> Minecraft.getInstance().setScreenAndShow(new MobPickerScreen(this, onPicked));
            case COLLECT_ITEMS -> Minecraft.getInstance().setScreenAndShow(
                    new ItemPickerScreen(this, null, onPicked));
            case KILL_PLAYERS -> placeObjective(new NpcQuestObjective(addType, "", 1, ""));
            case ENTER_BIOME -> Minecraft.getInstance().setScreenAndShow(
                    RegistryPickerScreen.biomes(this, onPicked));
            case ENTER_STRUCTURE -> Minecraft.getInstance().setScreenAndShow(
                    RegistryPickerScreen.structures(this, onPicked));
            case ENTER_DIMENSION -> Minecraft.getInstance().setScreenAndShow(
                    RegistryPickerScreen.dimensions(this, onPicked));
        }
    }

    private void placeObjective(NpcQuestObjective objective) {
        objectives.add(objective);
        objectiveSelected = objectives.size() - 1;
        if (objective.type() == NpcObjectiveType.COLLECT_ITEMS) {
            completion = NpcQuestCompletion.TURN_IN;
        }
        rebuildObjectives();
    }

    private void removeObjective() {
        if (objectiveSelected < 0) {
            return;
        }
        objectives.remove(objectiveSelected);
        objectiveSelected = -1;
        rebuildObjectives();
    }

    private void changeTarget() {
        if (objectiveSelected < 0) {
            return;
        }
        NpcQuestObjective objective = objectives.get(objectiveSelected);
        Consumer<String> onPicked = targetId -> {
            if (targetId != null && !targetId.isEmpty()) {
                replaceSelected(new NpcQuestObjective(objective.type(), targetId, objective.amount(),
                        objective.requiredWeaponId()));
            }
        };
        switch (objective.type()) {
            case KILL_MOB -> Minecraft.getInstance().setScreenAndShow(new MobPickerScreen(this, onPicked));
            case COLLECT_ITEMS -> Minecraft.getInstance().setScreenAndShow(
                    new ItemPickerScreen(this, null, onPicked));
            case ENTER_BIOME -> Minecraft.getInstance().setScreenAndShow(
                    RegistryPickerScreen.biomes(this, onPicked));
            case ENTER_STRUCTURE -> Minecraft.getInstance().setScreenAndShow(
                    RegistryPickerScreen.structures(this, onPicked));
            case ENTER_DIMENSION -> Minecraft.getInstance().setScreenAndShow(
                    RegistryPickerScreen.dimensions(this, onPicked));
            case KILL_PLAYERS -> { }
        }
    }

    private void changeWeapon() {
        if (objectiveSelected < 0) {
            return;
        }
        NpcQuestObjective objective = objectives.get(objectiveSelected);
        Minecraft.getInstance().setScreenAndShow(new ItemPickerScreen(this, null, weaponId ->
                replaceSelected(new NpcQuestObjective(objective.type(), objective.targetId(),
                        objective.amount(), weaponId == null ? "" : weaponId))));
    }

    private void openPrerequisites() {
        List<MultiSelectPickerScreen.Option> options = new ArrayList<>();
        for (NpcQuest quest : siblingQuests) {
            if (quest.questId().equals(questId)) {
                continue;
            }
            Component label = quest.name().isBlank()
                    ? Component.translatable("gui.luna_npc.edit_quest.unnamed") : Component.literal(quest.name());
            options.add(new MultiSelectPickerScreen.Option(quest.questId(), label,
                    quest.questId() + " " + label.getString()));
        }
        Minecraft.getInstance().setScreenAndShow(new MultiSelectPickerScreen(this,
                Component.translatable("gui.luna_npc.quest_prereq.title"),
                Component.translatable("gui.luna_npc.quest_prereq.search"), options,
                prerequisiteQuestIds, chosen -> {
                    prerequisiteQuestIds.clear();
                    prerequisiteQuestIds.addAll(chosen);
                    prerequisitesButton.setMessage(prerequisitesLabel());
                }));
    }

    private void onAmountTyped(String value) {
        if (populatingAmount) {
            return;
        }
        String digits = CreateNpcScreen.digitsOnly(amountField, value);
        if (digits == null || objectiveSelected < 0) {
            return;
        }
        NpcQuestObjective objective = objectives.get(objectiveSelected);
        if (objective.type().isEnterObjective()) {
            return;
        }
        int amount = digits.isEmpty() ? 1 : Math.max(1, Integer.parseInt(digits));
        objectives.set(objectiveSelected, new NpcQuestObjective(objective.type(), objective.targetId(),
                amount, objective.requiredWeaponId()));
    }

    // Replaces the selected objective and refreshes the row labels without stealing focus from a field.
    private void replaceSelected(NpcQuestObjective objective) {
        objectives.set(objectiveSelected, objective);
        rebuildObjectives();
    }

    private void rebuildObjectives() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (int i = 0; i < objectives.size(); i++) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(String.valueOf(i),
                    objectiveSummary(objectives.get(i)), value -> {
                        objectiveSelected = Integer.parseInt(value);
                        refreshObjectiveDetail();
                    });
            entries.add(entry);
            if (i == objectiveSelected) {
                selected = entry;
            }
        }
        if (selected == null) {
            objectiveSelected = -1;
        }
        objectiveList.fill(entries, selected);
        refreshObjectiveDetail();
    }

    private void refreshObjectiveDetail() {
        boolean hasSelection = objectiveSelected >= 0;
        NpcObjectiveType type = hasSelection ? objectives.get(objectiveSelected).type() : null;
        boolean kill = type == NpcObjectiveType.KILL_MOB;
        boolean hasTarget = hasSelection && type != NpcObjectiveType.KILL_PLAYERS;
        removeObjectiveButton.active = hasSelection;
        targetButton.visible = hasTarget;
        amountField.visible = hasSelection;
        amountField.active = hasSelection && !type.isEnterObjective();
        weaponButton.visible = kill;
        if (hasSelection) {
            NpcQuestObjective objective = objectives.get(objectiveSelected);
            if (hasTarget) {
                targetButton.setMessage(Component.translatable("gui.luna_npc.edit_quest.target")
                        .copy().append(": ").append(targetName(objective)));
            }
            populatingAmount = true;
            amountField.setValue(String.valueOf(objective.amount()));
            populatingAmount = false;
            weaponButton.setMessage(Component.translatable("gui.luna_npc.edit_quest.required_weapon"));
            weaponButton.setTooltip(Tooltip.create(objective.requiredWeaponId().isEmpty()
                    ? Component.translatable("gui.luna_npc.edit_quest.required_weapon.tooltip")
                    : NpcEquipment.resolve(objective.requiredWeaponId()).getHoverName()));
        }
        refreshCompletion();
    }

    private void refreshRewards() {
        refreshRewardSlots();
        boolean editable = rewardSelected >= 0 && rewardIds[rewardSelected] != null
                && !rewardIds[rewardSelected].isBlank();
        rewardCountField.visible = editable;
        rewardCountField.active = editable;
        if (editable) {
            populatingReward = true;
            rewardCountField.setValue(String.valueOf(rewardCounts[rewardSelected]));
            populatingReward = false;
        }
    }

    private void refreshRewardSlots() {
        for (int i = 0; i < rewardSlots.length; i++) {
            ItemStack stack = NpcEquipment.resolve(rewardIds[i]);
            if (!stack.isEmpty()) {
                stack.setCount(Math.max(1, rewardCounts[i]));
                rewardSlots[i].setStack(stack);
                rewardSlots[i].setTooltip(Tooltip.create(stack.getHoverName()));
            } else {
                rewardSlots[i].setStack(ItemStack.EMPTY);
                rewardSlots[i].setTooltip(null);
            }
            rewardSlots[i].setSelected(i == rewardSelected);
        }
    }

    private void refreshCompletion() {
        boolean lockedTurnIn = hasCollectObjective();
        if (lockedTurnIn) {
            completion = NpcQuestCompletion.TURN_IN;
        }
        completionButton.active = !lockedTurnIn;
        completionButton.setMessage(completionLabel());
        completionButton.setTooltip(lockedTurnIn
                ? Tooltip.create(Component.translatable("gui.luna_npc.edit_quest.completion.auto.disabled_tooltip"))
                : null);
    }

    private boolean hasCollectObjective() {
        for (NpcQuestObjective objective : objectives) {
            if (objective.type() == NpcObjectiveType.COLLECT_ITEMS) {
                return true;
            }
        }
        return false;
    }

    private void clampTime() {
        long seconds = (long) timeValue * UNIT_FACTORS[timeUnit];
        if (seconds > NpcValidation.MAX_QUEST_TIME_LIMIT_SECONDS) {
            timeValue = NpcValidation.MAX_QUEST_TIME_LIMIT_SECONDS / UNIT_FACTORS[timeUnit];
            timeField.setValue(String.valueOf(timeValue));
        }
    }

    private Component completionLabel() {
        return Component.translatable("gui.luna_npc.edit_quest.completion."
                + completion.getSerializedName());
    }

    private Component unitLabel() {
        return Component.translatable("gui.luna_npc.edit_quest.time_limit." + UNIT_KEYS[timeUnit]);
    }

    private Component objectiveTypeLabel() {
        return Component.translatable("gui.luna_npc.edit_quest.objective_type." + addType.getSerializedName());
    }

    private Component prerequisitesLabel() {
        return Component.translatable("gui.luna_npc.edit_quest.prerequisites", prerequisiteQuestIds.size());
    }

    private static Component objectiveSummary(NpcQuestObjective objective) {
        return switch (objective.type()) {
            case KILL_MOB -> Component.translatable("gui.luna_npc.edit_quest.summary_kill",
                    objective.amount(), targetName(objective));
            case COLLECT_ITEMS -> Component.translatable("gui.luna_npc.edit_quest.summary_collect",
                    objective.amount(), targetName(objective));
            case KILL_PLAYERS -> Component.translatable("gui.luna_npc.edit_quest.summary_kill_players",
                    objective.amount());
            case ENTER_BIOME, ENTER_STRUCTURE, ENTER_DIMENSION ->
                    Component.translatable("gui.luna_npc.quest_offer.objective_enter", targetName(objective));
        };
    }

    private static Component targetName(NpcQuestObjective objective) {
        return switch (objective.type()) {
            case KILL_MOB -> {
                Identifier id = Identifier.tryParse(objective.targetId());
                yield id == null ? Component.literal(objective.targetId())
                        : BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                                .map(type -> (Component) Component.translatable(type.getDescriptionId()))
                                .orElse(Component.literal(objective.targetId()));
            }
            case COLLECT_ITEMS -> NpcEquipment.resolve(objective.targetId()).getHoverName();
            case ENTER_BIOME -> {
                Identifier id = Identifier.tryParse(objective.targetId());
                yield id == null ? Component.literal(objective.targetId())
                        : Component.translatable("biome." + id.getNamespace() + "." + id.getPath());
            }
            case ENTER_STRUCTURE, ENTER_DIMENSION -> {
                Identifier id = Identifier.tryParse(objective.targetId());
                yield Component.literal(CreateNpcScreen.prettify(id == null ? objective.targetId() : id.getPath()));
            }
            case KILL_PLAYERS -> Component.translatable("gui.luna_npc.edit_quest.any_player");
        };
    }

    private NpcQuest build() {
        List<NpcTradeStack> rewards = new ArrayList<>();
        for (int i = 0; i < rewardIds.length; i++) {
            if (rewardIds[i] != null && !rewardIds[i].isBlank()) {
                rewards.add(new NpcTradeStack(rewardIds[i], Math.max(1, rewardCounts[i])));
            }
        }
        int timeLimit = (int) Math.min((long) timeValue * UNIT_FACTORS[timeUnit],
                NpcValidation.MAX_QUEST_TIME_LIMIT_SECONDS);
        NpcQuestCompletion mode = hasCollectObjective() ? NpcQuestCompletion.TURN_IN : completion;
        return new NpcQuest(questId, name, questTitle, questDescription, objectives, mode, timeLimit,
                Math.min(xpMin, xpMax), xpMax, rewards, cancelOnNpcDeath,
                new ArrayList<>(prerequisiteQuestIds));
    }

    @Override
    public void onClose() {
        onApply.accept(build());
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
        graphics.outline(panelX() + 14, panelY() + 30, 172, OBJ_LIST_HEIGHT, PANEL_BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = panelX() + 14;
        int rLabel = panelX() + 200;
        int rFull = panelX() + PANEL_WIDTH - 18;
        int top = panelY();
        graphics.text(this.font, Component.translatable("gui.luna_npc.edit_quest.objectives"),
                left, top + 18, SECTION_COLOR);
        columnLabel(graphics, Component.translatable("gui.luna_npc.edit_quest.name"), rLabel, top + 14, 68);
        columnLabel(graphics, Component.translatable("gui.luna_npc.edit_quest.display_title"), rLabel, top + 36, 68);
        columnLabel(graphics, Component.translatable("gui.luna_npc.edit_quest.description"), rLabel, top + 58, 68);
        columnLabel(graphics, Component.translatable("gui.luna_npc.edit_quest.completion"), rLabel, top + 80, 68);
        columnLabel(graphics, Component.translatable("gui.luna_npc.edit_quest.cancel_on_death"), rLabel, top + 102, 68);
        columnLabel(graphics, Component.translatable("gui.luna_npc.edit_quest.time_limit"), rLabel, top + 124, 68);
        Component xpLabel = Component.translatable("gui.luna_npc.edit_quest.xp_reward")
                .copy().append(": " + xpMin + " - " + xpMax);
        graphics.text(this.font, xpLabel, rLabel, top + 150, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.luna_npc.edit_quest.rewards"),
                rLabel, top + 202, SECTION_COLOR);
        if (rewardCountField.visible) {
            Component count = Component.translatable("gui.luna_npc.edit_quest.reward_count");
            graphics.text(this.font, count, rFull - 34 - 4 - this.font.width(count), top + 202, LABEL_COLOR);
        }
        if (amountField.visible) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.edit_quest.objective_amount"),
                    left, top + 190, LABEL_COLOR);
        }
        if (objectiveSelected >= 0
                && objectives.get(objectiveSelected).type() == NpcObjectiveType.KILL_PLAYERS) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.edit_quest.any_player"),
                    left, top + 168, LABEL_COLOR);
        }
    }
}
