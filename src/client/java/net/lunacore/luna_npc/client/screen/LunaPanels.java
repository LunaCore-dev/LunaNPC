package net.lunacore.luna_npc.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

// The LunaScreen panel surfaces as static helpers, so screens that cannot extend LunaScreen (a
// container screen must extend AbstractContainerScreen) still draw the identical cards. LunaScreen
// itself delegates here, keeping one definition of the look (the container-screen rule in CLAUDE.md).
public final class LunaPanels {

    public static final int SURFACE = 0xFF1E1E26;
    public static final int HEADER = 0xFF2A2A34;
    public static final int BORDER = 0xFF5A5A68;
    public static final int SECTION = 0xFFE0C060;

    private LunaPanels() {
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, SURFACE);
        graphics.outline(x, y, width, height, BORDER);
    }

    public static void titledPanel(GuiGraphicsExtractor graphics, Font font, int x, int y, int width,
                                   int height, Component title) {
        panel(graphics, x, y, width, height);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 14, HEADER);
        graphics.text(font, title, x + 6, y + 4, SECTION);
    }

    public static void sectionTitle(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y) {
        graphics.text(font, text, x, y, SECTION);
    }
}
