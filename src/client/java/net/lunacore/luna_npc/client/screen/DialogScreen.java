package net.lunacore.luna_npc.client.screen;

import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.lunacore.luna_npc.network.DialogChoiceLine;
import net.lunacore.luna_npc.network.DialogChoicePayload;
import net.lunacore.luna_npc.network.DialogOpenPayload;

// The conversation an NPC opens on right-click: the NPC's line on a panel, and the answers as flat
// lettered rows (A, B, ... Z, AA, ...) in a fixed five-row box below it — each row outlined so they read
// as separate options, and lit on hover so it's clear they click. The box keeps its size and place
// whatever the node, so nothing jumps; more than five answers scroll with the wheel. Gold answers close
// the talk. A choice round-trips to the server, which pushes the next node back; a node with no choices
// is terminal and shows Close.
public class DialogScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int LINE_HEIGHT = 10;
    private static final int PAD = 6;
    private static final int GAP = 6;
    private static final int TITLE_GAP = 22;
    private static final int MAX_SPEAKER_LINES = 14;
    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_H = 16;
    private static final int ROW_GAP = 2;
    private static final int CHOICES_HEIGHT = VISIBLE_ROWS * ROW_H + (VISIBLE_ROWS - 1) * ROW_GAP + 2 * PAD;
    private static final int SCROLLBAR_W = 4;
    private static final int ROW_HOVER = 0x33FFFFFF;
    private static final int LETTER_COLOR = 0xFF8890A0;

    private final long lunaId;
    private String speakerLine;
    private List<DialogChoiceLine> choices;

    private int panelX;
    private int speakerTop;
    private int speakerHeight;
    private int choicesTop;
    private int scrollOffset;
    private List<FormattedCharSequence> speakerLines = List.of();

    public DialogScreen(DialogOpenPayload payload) {
        super(Component.literal(payload.npcName()));
        this.lunaId = payload.lunaId();
        this.speakerLine = payload.speakerLine();
        this.choices = payload.choices();
    }

    public long lunaId() {
        return lunaId;
    }

    public void apply(DialogOpenPayload payload) {
        this.speakerLine = payload.speakerLine();
        this.choices = payload.choices();
        this.scrollOffset = 0;
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        panelX = this.width / 2 - PANEL_WIDTH / 2;
        speakerLines = this.font.split(Component.literal(speakerLine), PANEL_WIDTH - 2 * PAD);
        int lineCount = Math.min(Math.max(speakerLines.size(), 1), MAX_SPEAKER_LINES);
        speakerHeight = lineCount * LINE_HEIGHT + 2 * PAD;

        choicesTop = (this.height - CHOICES_HEIGHT) / 2 + 8;
        int speakerBottom = choicesTop - GAP;
        speakerTop = Math.max(TITLE_GAP, speakerBottom - speakerHeight);
        speakerHeight = speakerBottom - speakerTop;

        if (choices.isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.dialog.close"),
                            button -> onClose())
                    .bounds(this.width / 2 - 50, choicesTop + (CHOICES_HEIGHT - 20) / 2, 100, 20).build());
        }
    }

    // Bijective base-26 label: A..Z, then AA, AB, ... so answers stay lettered without limit.
    private static String letter(int index) {
        StringBuilder builder = new StringBuilder();
        int value = index + 1;
        while (value > 0) {
            value--;
            builder.insert(0, (char) ('A' + value % 26));
            value /= 26;
        }
        return builder.toString();
    }

    private int maxOffset() {
        return Math.max(0, choices.size() - VISIBLE_ROWS);
    }

    private int rowWidth() {
        return PANEL_WIDTH - 2 * PAD - (choices.size() > VISIBLE_ROWS ? SCROLLBAR_W + 2 : 0);
    }

    private int rowIndexAt(double mouseX, double mouseY) {
        int rowX = panelX + PAD;
        if (mouseX < rowX || mouseX > rowX + rowWidth()) {
            return -1;
        }
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            int rowY = choicesTop + PAD + r * (ROW_H + ROW_GAP);
            if (mouseY >= rowY && mouseY < rowY + ROW_H) {
                int index = scrollOffset + r;
                return index < choices.size() ? index : -1;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = rowIndexAt(event.x(), event.y());
        if (index >= 0) {
            onChoice(choices.get(index));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (choices.size() > VISIBLE_ROWS && mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                && mouseY >= choicesTop && mouseY <= choicesTop + CHOICES_HEIGHT) {
            scrollOffset = Math.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxOffset());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void onChoice(DialogChoiceLine choice) {
        if (choice.enabled()) {
            ClientPlayNetworking.send(new DialogChoicePayload(lunaId, choice.choiceIndex()));
        }
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        panel(graphics, panelX, speakerTop, PANEL_WIDTH, speakerHeight);
        panel(graphics, panelX, choicesTop, PANEL_WIDTH, CHOICES_HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        int shown = Math.min(speakerLines.size(), MAX_SPEAKER_LINES);
        int y = speakerTop + PAD;
        for (int index = 0; index < shown; index++) {
            graphics.text(this.font, speakerLines.get(index), panelX + PAD, y, LABEL_COLOR);
            y += LINE_HEIGHT;
        }
        if (choices.isEmpty()) {
            return;
        }
        extractRows(graphics, mouseX, mouseY);
        if (choices.size() > VISIBLE_ROWS) {
            extractScrollbar(graphics);
        }
    }

    private void extractRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int rowX = panelX + PAD;
        int rowW = rowWidth();
        int hovered = rowIndexAt(mouseX, mouseY);
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            int index = scrollOffset + r;
            if (index >= choices.size()) {
                break;
            }
            DialogChoiceLine choice = choices.get(index);
            int rowY = choicesTop + PAD + r * (ROW_H + ROW_GAP);
            if (index == hovered && choice.enabled()) {
                graphics.fill(rowX + 1, rowY + 1, rowX + rowW - 1, rowY + ROW_H - 1, ROW_HOVER);
            }
            graphics.outline(rowX, rowY, rowW, ROW_H, PANEL_BORDER);
            int textY = rowY + (ROW_H - 8) / 2;
            graphics.text(this.font, Component.literal(letter(index)), rowX + 6, textY, LETTER_COLOR);
            int color = !choice.enabled() ? STAR_EMPTY_COLOR
                    : choice.endsTalk() ? SECTION_COLOR : 0xFFFFFFFF;
            graphics.text(this.font, Component.literal(choice.text()), rowX + 24, textY, color);
        }
    }

    private void extractScrollbar(GuiGraphicsExtractor graphics) {
        int trackX = panelX + PANEL_WIDTH - PAD - SCROLLBAR_W;
        int trackTop = choicesTop + PAD;
        int trackH = CHOICES_HEIGHT - 2 * PAD;
        int thumbH = Math.max(12, trackH * VISIBLE_ROWS / choices.size());
        int thumbY = trackTop + (trackH - thumbH) * scrollOffset / maxOffset();
        graphics.fill(trackX, trackTop, trackX + SCROLLBAR_W, trackTop + trackH, PANEL_BORDER);
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, SECTION_COLOR);
    }
}
