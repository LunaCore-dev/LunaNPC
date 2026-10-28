package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// A small centred yes/no dialog on one opaque panel (the CLAUDE.md UI rules): a title strip, one or more centred
// message lines, and a confirm/cancel button pair. Reusable for any destructive action — the caller
// supplies the wording, the confirm label and what to run on confirm; cancelling just closes back to
// the game. Long lines wrap to the panel width so text never spills outside the card, and each line
// keeps its own styling, so a caller can tint its warning line red.
public class ConfirmScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 280;
    private static final int TEXT_INSET = 14;

    private final List<Component> lines;
    private final Component confirmLabel;
    private final Runnable onConfirm;
    private final List<FormattedCharSequence> wrapped = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelHeight;

    public ConfirmScreen(Component title, List<Component> lines, Component confirmLabel, Runnable onConfirm) {
        super(title);
        this.lines = List.copyOf(lines);
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        wrapped.clear();
        for (Component line : lines) {
            wrapped.addAll(this.font.split(line, PANEL_WIDTH - 2 * TEXT_INSET));
        }
        panelHeight = 62 + wrapped.size() * 12;
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - panelHeight) / 2;
        int buttonsY = panelY + panelHeight - 30;
        int buttonWidth = (PANEL_WIDTH - 30) / 2;
        addRenderableWidget(Button.builder(confirmLabel, button -> {
            onConfirm.run();
            onClose();
        }).bounds(panelX + 10, buttonsY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(panelX + PANEL_WIDTH - 10 - buttonWidth, buttonsY, buttonWidth, 20).build());
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight, this.title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centreX = panelX + PANEL_WIDTH / 2;
        int y = panelY + 24;
        for (FormattedCharSequence line : wrapped) {
            graphics.text(this.font, line, centreX - this.font.width(line) / 2, y, LABEL_COLOR);
            y += 12;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}
