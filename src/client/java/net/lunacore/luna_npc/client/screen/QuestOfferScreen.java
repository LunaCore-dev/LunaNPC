package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.api.NpcQuest;
import net.lunacore.luna_npc.api.NpcQuestObjective;
import net.lunacore.luna_npc.api.NpcTradeStack;
import net.lunacore.luna_npc.client.util.ClientRegistries;
import net.lunacore.luna_npc.client.widget.ItemSlotButton;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.job.QuestEvents;
import net.lunacore.luna_npc.network.QuestAcceptPayload;
import net.lunacore.luna_npc.network.QuestOffersPayload;
import net.lunacore.luna_npc.network.QuestOffersPayload.QuestStatusView;
import net.lunacore.luna_npc.network.QuestTurnInPayload;
import net.lunacore.luna_npc.npc.NpcEquipment;
import net.lunacore.luna_npc.npc.NpcValidation;

// The quest board an NPC opens on right-click: a quest list on the left, a detail card on the right
// with each objective's progress, the reward row and the time limit. Accept arms an offerable quest,
// Complete turns a ready one in; both round-trip to the server, which re-sends this screen refreshed.
public class QuestOfferScreen extends LunaScreen {

    private static final int LIST_TOP = 46;
    private static final int LIST_WIDTH = 130;
    private static final int INFO_WIDTH = 200;
    private static final int PANEL_HEIGHT = 172;
    private static final int GAP = 8;
    private static final int REWARD_COLUMNS = 9;
    private static final int COMPLETE_COLOR = 0xFF66DD66;

    private final int npcEntityId;
    private List<NpcQuest> quests;
    private final Map<String, QuestStatusView> statusByQuestId = new HashMap<>();

    private final ItemSlotButton[] rewardSlots = new ItemSlotButton[NpcValidation.MAX_QUEST_REWARD_SLOTS];
    private SimpleSelectionList questList;
    private Button acceptButton;
    private Button completeButton;
    private int selectedIndex = -1;

    public QuestOfferScreen(QuestOffersPayload payload) {
        super(Component.translatable("gui.luna_npc.quest_offer.title"));
        this.npcEntityId = payload.npcEntityId();
        this.quests = payload.quests();
        for (QuestStatusView status : payload.statuses()) {
            statusByQuestId.put(status.questId(), status);
        }
        if (!quests.isEmpty()) {
            selectedIndex = 0;
        }
    }

    public int npcEntityId() {
        return npcEntityId;
    }

    // A fresh offers snapshot after Accept/Complete: swap the data in place and keep the same quest
    // selected while it is still offered (mirrors QuestBookScreen.apply).
    public void apply(QuestOffersPayload payload) {
        NpcQuest previous = selectedQuest();
        String selectedId = previous == null ? null : previous.questId();
        quests = payload.quests();
        statusByQuestId.clear();
        for (QuestStatusView status : payload.statuses()) {
            statusByQuestId.put(status.questId(), status);
        }
        selectedIndex = indexOfQuest(selectedId);
        if (selectedIndex < 0 && !quests.isEmpty()) {
            selectedIndex = 0;
        }
        if (questList != null) {
            rebuildQuestList();
            updateDetail();
        }
    }

    private int indexOfQuest(String questId) {
        for (int index = 0; index < quests.size(); index++) {
            if (quests.get(index).questId().equals(questId)) {
                return index;
            }
        }
        return -1;
    }

    private int leftX() {
        return this.width / 2 - (LIST_WIDTH + GAP + INFO_WIDTH) / 2;
    }

    private int infoX() {
        return leftX() + LIST_WIDTH + GAP;
    }

    private int rewardGridLeft() {
        return infoX() + (INFO_WIDTH - REWARD_COLUMNS * ItemSlotButton.SIZE) / 2;
    }

    private int rewardGridTop() {
        return LIST_TOP + PANEL_HEIGHT - 3 * ItemSlotButton.SIZE - 8;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int buttonsY = LIST_TOP + PANEL_HEIGHT + 12;

        acceptButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.luna_npc.quest_offer.accept"), button -> sendAccept())
                .bounds(centerX - 152, buttonsY, 100, 20).build());
        completeButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.luna_npc.quest_offer.complete"), button -> sendTurnIn())
                .bounds(centerX - 50, buttonsY, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(centerX + 52, buttonsY, 100, 20).build());

        int gridLeft = rewardGridLeft();
        int gridTop = rewardGridTop();
        for (int index = 0; index < rewardSlots.length; index++) {
            int x = gridLeft + (index % REWARD_COLUMNS) * ItemSlotButton.SIZE;
            int y = gridTop + (index / REWARD_COLUMNS) * ItemSlotButton.SIZE;
            ItemSlotButton slot = new ItemSlotButton(x, y,
                    Component.translatable("gui.luna_npc.quest_offer.rewards"), event -> {
                    });
            slot.visible = false;
            rewardSlots[index] = addRenderableWidget(slot);
        }

        questList = addRenderableWidget(new SimpleSelectionList(LIST_WIDTH, PANEL_HEIGHT, LIST_TOP));
        questList.setX(leftX());
        rebuildQuestList();
        updateDetail();
    }

    private void rebuildQuestList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (int index = 0; index < quests.size(); index++) {
            NpcQuest quest = quests.get(index);
            int position = index;
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(quest.questId(),
                    rowLabel(quest), id -> select(position), null, rowColor(quest));
            entries.add(entry);
            if (index == selectedIndex) {
                selected = entry;
            }
        }
        questList.fill(entries, selected);
    }

    private void select(int index) {
        this.selectedIndex = index;
        updateDetail();
    }

    private NpcQuest selectedQuest() {
        return selectedIndex < 0 || selectedIndex >= quests.size() ? null : quests.get(selectedIndex);
    }

    private QuestStatusView selectedStatus() {
        NpcQuest quest = selectedQuest();
        return quest == null ? null : statusByQuestId.get(quest.questId());
    }

    private void updateDetail() {
        QuestStatusView status = selectedStatus();
        acceptButton.active = status != null && status.state() == QuestStatusView.OFFERABLE;
        completeButton.active = status != null && status.state() == QuestStatusView.READY;

        NpcQuest quest = selectedQuest();
        List<NpcTradeStack> rewards = quest == null ? List.of() : quest.itemRewards();
        for (int index = 0; index < rewardSlots.length; index++) {
            ItemSlotButton slot = rewardSlots[index];
            if (index >= rewards.size()) {
                slot.visible = false;
                continue;
            }
            ItemStack stack = NpcEquipment.resolve(rewards.get(index).itemId(),
                    rewards.get(index).enchants(), ClientRegistries.lookup());
            if (stack.isEmpty()) {
                slot.visible = false;
                continue;
            }
            stack.setCount(rewards.get(index).count());
            slot.setStack(stack);
            slot.setTooltip(Tooltip.create(stack.getHoverName()));
            slot.visible = true;
        }
    }

    private void sendAccept() {
        NpcQuest quest = selectedQuest();
        if (quest != null) {
            ClientPlayNetworking.send(new QuestAcceptPayload(npcEntityId, quest.questId()));
        }
    }

    private void sendTurnIn() {
        NpcQuest quest = selectedQuest();
        if (quest != null) {
            ClientPlayNetworking.send(new QuestTurnInPayload(npcEntityId, quest.questId()));
        }
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        panel(graphics, leftX() - 5, LIST_TOP - 5, LIST_WIDTH + 10, PANEL_HEIGHT + 10);
        NpcQuest quest = selectedQuest();
        Component title = quest == null
                ? Component.translatable("gui.luna_npc.quest_offer.title")
                : Component.literal(displayTitle(quest));
        titledPanel(graphics, infoX(), LIST_TOP, INFO_WIDTH, PANEL_HEIGHT, title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        if (quests.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.quest_offer.none"),
                    infoX() + INFO_WIDTH / 2, LIST_TOP + PANEL_HEIGHT / 2 - 4, LABEL_COLOR);
            return;
        }
        NpcQuest quest = selectedQuest();
        QuestStatusView status = selectedStatus();
        if (quest == null) {
            return;
        }
        int textX = infoX() + 8;
        int maxY = rewardGridTop() - 13;
        int y = LIST_TOP + 20;
        if (!quest.description().isBlank()) {
            y = drawWrapped(graphics, this.font, quest.description(), textX, y, INFO_WIDTH - 16, LABEL_COLOR, 3) + 3;
        }
        List<NpcQuestObjective> objectives = quest.objectives();
        for (int index = 0; index < objectives.size() && y <= maxY; index++) {
            graphics.text(this.font, objectiveLine(objectives.get(index), status, index), textX, y,
                    objectiveComplete(objectives.get(index), status, index) ? COMPLETE_COLOR : LABEL_COLOR);
            y += 11;
        }
        if ((quest.xpMin() > 0 || quest.xpMax() > 0) && y <= maxY) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.quest_offer.reward_xp",
                    xpRange(quest)), textX, y, SECTION_COLOR);
            y += 11;
        }
        if (quest.timeLimitSeconds() > 0 && y <= maxY) {
            graphics.text(this.font, timeLine(quest, status), textX, y, LABEL_COLOR);
        }
        if (!quest.itemRewards().isEmpty()) {
            sectionTitle(graphics, Component.translatable("gui.luna_npc.quest_offer.rewards"),
                    textX, rewardGridTop() - 11);
        }
    }

    private Component objectiveLine(NpcQuestObjective objective, QuestStatusView status, int index) {
        return objectiveText(objective, progressAt(status, index));
    }

    // One readable line per objective, shared with the quest book so both screens render every
    // objective type the same way.
    static Component objectiveText(NpcQuestObjective objective, int done) {
        return switch (objective.type()) {
            case KILL_MOB -> Component.translatable("gui.luna_npc.quest_offer.objective_kill",
                    mobName(objective.targetId()), done, objective.amount());
            case COLLECT_ITEMS -> Component.translatable("gui.luna_npc.quest_offer.objective_collect",
                    itemName(objective.targetId()), done, objective.amount());
            case KILL_PLAYERS -> Component.translatable("gui.luna_npc.quest_offer.objective_kill_players",
                    done, objective.amount());
            case ENTER_BIOME -> Component.translatable("gui.luna_npc.quest_offer.objective_enter_biome",
                    prettyId(objective.targetId()));
            case ENTER_STRUCTURE -> Component.translatable("gui.luna_npc.quest_offer.objective_enter_structure",
                    prettyId(objective.targetId()));
            case ENTER_DIMENSION -> Component.translatable("gui.luna_npc.quest_offer.objective_enter_dimension",
                    prettyId(objective.targetId()));
        };
    }

    private static String displayTitle(NpcQuest quest) {
        return quest.title().isBlank() ? quest.name() : quest.title();
    }

    static String prettyId(String id) {
        Identifier parsed = Identifier.tryParse(id);
        String path = parsed == null ? id : parsed.getPath();
        return path.replace('_', ' ');
    }

    // Wrapped free-form text (quest descriptions), capped at maxLines; returns the y below the block.
    // Shared with the quest book.
    static int drawWrapped(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int width,
                           int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
        int shown = Math.min(lines.size(), maxLines);
        for (int index = 0; index < shown; index++) {
            graphics.text(font, lines.get(index), x, y, color);
            y += 10;
        }
        return y;
    }

    private boolean objectiveComplete(NpcQuestObjective objective, QuestStatusView status, int index) {
        return progressAt(status, index) >= objective.amount();
    }

    private static int progressAt(QuestStatusView status, int index) {
        if (status == null || index >= status.progress().size()) {
            return 0;
        }
        return status.progress().get(index);
    }

    private Component timeLine(NpcQuest quest, QuestStatusView status) {
        if (status != null && status.state() != QuestStatusView.OFFERABLE) {
            return Component.translatable("gui.luna_npc.quest_offer.time_left",
                    QuestEvents.formatDuration(status.secondsLeft()));
        }
        return Component.translatable("gui.luna_npc.quest_offer.time_limit",
                QuestEvents.formatDuration(quest.timeLimitSeconds()));
    }

    private static String xpRange(NpcQuest quest) {
        return quest.xpMin() == quest.xpMax()
                ? String.valueOf(quest.xpMin())
                : quest.xpMin() + "-" + quest.xpMax();
    }

    private Component rowLabel(NpcQuest quest) {
        QuestStatusView status = statusByQuestId.get(quest.questId());
        String prefix = "";
        if (status != null && status.state() == QuestStatusView.READY) {
            prefix = "✔ ";
        } else if (status != null && status.state() == QuestStatusView.ACTIVE) {
            prefix = "▸ ";
        }
        return Component.literal(prefix + quest.name());
    }

    private int rowColor(NpcQuest quest) {
        QuestStatusView status = statusByQuestId.get(quest.questId());
        if (status == null) {
            return LABEL_COLOR;
        }
        if (status.state() == QuestStatusView.READY) {
            return SECTION_COLOR;
        }
        if (status.state() == QuestStatusView.ACTIVE) {
            return 0xFFFFFFFF;
        }
        return LABEL_COLOR;
    }

    private static Component mobName(String entityTypeId) {
        Identifier id = Identifier.tryParse(entityTypeId);
        if (id == null) {
            return Component.literal(entityTypeId);
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .map(EntityType::getDescription)
                .orElse(Component.literal(entityTypeId));
    }

    private static Component itemName(String itemId) {
        ItemStack stack = NpcEquipment.resolve(itemId);
        return stack.isEmpty() ? Component.literal(itemId) : stack.getHoverName();
    }
}
