package net.lunacore.luna_npc.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// Shared look for all LunaNPC screens, described in the CLAUDE.md UI rules: the screen background stays the
// vanilla translucent gradient (the world shines through), while content sits on opaque panel
// cards that provide the contrast. Simple list screens use no panels at all.
public abstract class LunaScreen extends Screen {

    protected static final int PANEL_SURFACE = 0xFF1E1E26;
    protected static final int PANEL_HEADER = 0xFF2A2A34;
    protected static final int PANEL_BORDER = 0xFF5A5A68;
    protected static final int TITLE_COLOR = 0xFFFFFFFF;
    protected static final int LABEL_COLOR = 0xFFC8C8D0;
    protected static final int SECTION_COLOR = 0xFFE0C060;
    // Unfilled slots of a star rating: always visible, never colored.
    protected static final int STAR_EMPTY_COLOR = 0xFF4A4A55;

    protected LunaScreen(Component title) {
        super(title);
    }

    // Panels must be part of the background phase: widgets are extracted right after the
    // background, so anything drawn later in extractRenderState paints over them.
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        extractPanels(graphics);
    }

    protected void extractPanels(GuiGraphicsExtractor graphics) {
    }

    protected void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        LunaPanels.panel(graphics, x, y, width, height);
    }

    protected void titledPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                               Component title) {
        LunaPanels.titledPanel(graphics, this.font, x, y, width, height, title);
    }

    protected void sectionTitle(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        LunaPanels.sectionTitle(graphics, this.font, text, x, y);
    }

    // A settings-grid label that never overflows its control: drawn on one line (centred in the
    // 22px row) when it fits maxWidth, otherwise wrapped to two lines that still fit the row.
    // maxWidth is the gap between the label and its control (controlX - labelX - 4).
    protected void columnLabel(GuiGraphicsExtractor graphics, Component label, int x, int rowTop, int maxWidth) {
        if (this.font.width(label) <= maxWidth) {
            graphics.text(this.font, label, x, rowTop + 6, LABEL_COLOR);
        } else {
            graphics.textWithWordWrap(this.font, label, x, rowTop + 1, maxWidth, LABEL_COLOR);
        }
    }

    protected void screenTitle(GuiGraphicsExtractor graphics) {
        graphics.centeredText(this.font, this.title, this.width / 2, 12, TITLE_COLOR);
    }
}
