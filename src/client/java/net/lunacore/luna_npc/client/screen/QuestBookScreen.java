package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcQuestObjective;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.job.QuestEvents;
import net.lunacore.luna_npc.network.ActiveQuestsPayload;
import net.lunacore.luna_npc.network.ActiveQuestsPayload.ActiveQuestView;
import net.lunacore.luna_npc.network.QuestAbandonPayload;
import net.lunacore.luna_npc.network.QuestBookRequestPayload;

// The quest book: a live tracker of the player's accepted quests. The server sends a snapshot; each
// objective's progress is refreshed on a short poll while the time-left counts down locally every
// frame from the snapshot, so the countdown stays smooth between server refreshes.
public class QuestBookScreen extends LunaScreen {

    private static final int LIST_TOP = 46;
    private static final int LIST_WIDTH = 130;
    private static final int INFO_WIDTH = 200;
    private static final int PANEL_HEIGHT = 172;
    private static final int GAP = 8;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final int COMPLETE_COLOR = 0xFF66DD66;

    private static final int WARN_COLOR = 0xE33B3B;

    private List<ActiveQuestView> quests;
    private long snapshotMillis;
    private SimpleSelectionList questList;
    private Button abandonButton;
    private String selectedQuestId;
    private boolean confirmingAbandon;
    private int abandonConfirmTicks;
    private int refreshCounter;

    public QuestBookScreen(ActiveQuestsPayload payload) {
        super(Component.translatable("gui.luna_npc.quest_book.title"));
        this.quests = payload.quests();
        this.snapshotMillis = System.currentTimeMillis();
        if (!quests.isEmpty()) {
            selectedQuestId = quests.getFirst().questId();
        }
    }

    private int leftX() {
        return this.width / 2 - (LIST_WIDTH + GAP + INFO_WIDTH) / 2;
    }

    private int infoX() {
        return leftX() + LIST_WIDTH + GAP;
    }

    @Override
    protected void init() {
        int buttonsY = LIST_TOP + PANEL_HEIGHT + 12;
        abandonButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.luna_npc.quest_book.abandon"), button -> onAbandonPressed())
                .bounds(this.width / 2 - 102, buttonsY, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 + 2, buttonsY, 100, 20).build());
        questList = addRenderableWidget(new SimpleSelectionList(LIST_WIDTH, PANEL_HEIGHT, LIST_TOP));
        questList.setX(leftX());
        rebuildList();
        updateAbandonButton();
    }

    private void onAbandonPressed() {
        if (selectedQuestId == null) {
            return;
        }
        if (confirmingAbandon) {
            ClientPlayNetworking.send(new QuestAbandonPayload(selectedQuestId));
            confirmingAbandon = false;
        } else {
            confirmingAbandon = true;
            abandonConfirmTicks = 80;
        }
        updateAbandonButton();
    }

    // Two-click confirm: first press arms it (red "Confirm abandon?"), the second sends. Any change of
    // selection or a fresh snapshot disarms it, so an accidental first click can't drop the wrong quest.
    private void updateAbandonButton() {
        if (abandonButton == null) {
            return;
        }
        abandonButton.active = selectedQuestId != null;
        abandonButton.setMessage(confirmingAbandon
                ? Component.translatable("gui.luna_npc.quest_book.abandon_confirm")
                        .withStyle(style -> style.withColor(WARN_COLOR))
                : Component.translatable("gui.luna_npc.quest_book.abandon"));
    }

    // A fresh snapshot from the server: swap the quest data, re-baseline the local countdown, and keep
    // the same quest selected while it is still active.
    public void apply(ActiveQuestsPayload payload) {
        this.quests = payload.quests();
        this.snapshotMillis = System.currentTimeMillis();
        if (selectedQuestId == null || quests.stream().noneMatch(q -> q.questId().equals(selectedQuestId))) {
            selectedQuestId = quests.isEmpty() ? null : quests.getFirst().questId();
        }
        if (questList != null) {
            rebuildList();
            updateAbandonButton();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (confirmingAbandon && --abandonConfirmTicks <= 0) {
            confirmingAbandon = false;
            updateAbandonButton();
        }
        if (++refreshCounter >= REFRESH_INTERVAL_TICKS) {
            refreshCounter = 0;
            ClientPlayNetworking.send(QuestBookRequestPayload.INSTANCE);
        }
    }

    private void rebuildList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (ActiveQuestView quest : quests) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(quest.questId(),
                    Component.literal(quest.title()), id -> {
                        if (!id.equals(selectedQuestId)) {
                            confirmingAbandon = false;
                        }
                        selectedQuestId = id;
                        updateAbandonButton();
                    }, null, rowColor(quest));
            entries.add(entry);
            if (quest.questId().equals(selectedQuestId)) {
                selected = entry;
            }
        }
        questList.fill(entries, selected);
    }

    private ActiveQuestView selectedQuest() {
        if (selectedQuestId == null) {
            return null;
        }
        for (ActiveQuestView quest : quests) {
            if (quest.questId().equals(selectedQuestId)) {
                return quest;
            }
        }
        return null;
    }

    private int rowColor(ActiveQuestView quest) {
        return allComplete(quest) ? SECTION_COLOR : LABEL_COLOR;
    }

    private boolean allComplete(ActiveQuestView quest) {
        List<NpcQuestObjective> objectives = quest.objectives();
        for (int index = 0; index < objectives.size(); index++) {
            if (progressAt(quest, index) < objectives.get(index).amount()) {
                return false;
            }
        }
        return true;
    }

    private static int progressAt(ActiveQuestView quest, int index) {
        return index < quest.progress().size() ? quest.progress().get(index) : 0;
    }

    private int liveSecondsLeft(ActiveQuestView quest) {
        long elapsed = (System.currentTimeMillis() - snapshotMillis) / 1000L;
        return (int) Math.max(0, quest.secondsLeft() - elapsed);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        panel(graphics, leftX() - 5, LIST_TOP - 5, LIST_WIDTH + 10, PANEL_HEIGHT + 10);
        ActiveQuestView quest = selectedQuest();
        Component title = quest == null
                ? Component.translatable("gui.luna_npc.quest_book.title")
                : Component.literal(quest.title());
        titledPanel(graphics, infoX(), LIST_TOP, INFO_WIDTH, PANEL_HEIGHT, title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        if (quests.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.quest_book.none"),
                    infoX() + INFO_WIDTH / 2, LIST_TOP + PANEL_HEIGHT / 2 - 4, LABEL_COLOR);
            return;
        }
        ActiveQuestView quest = selectedQuest();
        if (quest == null) {
            return;
        }
        int textX = infoX() + 8;
        int maxY = LIST_TOP + PANEL_HEIGHT - 14;
        int y = LIST_TOP + 20;
        if (!quest.description().isBlank()) {
            y = QuestOfferScreen.drawWrapped(graphics, this.font, quest.description(), textX, y,
                    INFO_WIDTH - 16, LABEL_COLOR, 4) + 3;
        }
        List<NpcQuestObjective> objectives = quest.objectives();
        for (int index = 0; index < objectives.size() && y <= maxY; index++) {
            NpcQuestObjective objective = objectives.get(index);
            int done = progressAt(quest, index);
            graphics.text(this.font, QuestOfferScreen.objectiveText(objective, done), textX, y,
                    done >= objective.amount() ? COMPLETE_COLOR : LABEL_COLOR);
            y += 11;
        }
        if (quest.timed() && y <= maxY) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.quest_book.time_left",
                    QuestEvents.formatDuration(liveSecondsLeft(quest))), textX, y, SECTION_COLOR);
        }
    }
}
