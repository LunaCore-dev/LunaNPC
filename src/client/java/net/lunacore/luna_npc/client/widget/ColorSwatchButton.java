package net.lunacore.luna_npc.client.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

// A 16x16 clickable color square; the selected swatch gets the gold outline (see the CLAUDE.md UI rules).
public class ColorSwatchButton extends AbstractWidget {

    public static final int SIZE = 16;

    private static final int SELECTED_BORDER_COLOR = 0xFFFFD700;
    private static final int BORDER_COLOR = 0xFF8B8B8B;

    private final int color;
    private final Runnable onPress;
    private boolean selected;

    public ColorSwatchButton(int x, int y, int color, Component narration, Runnable onPress) {
        super(x, y, SIZE, SIZE, narration);
        this.color = color;
        this.onPress = onPress;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onPress.run();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        graphics.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, color);
        graphics.outline(getX(), getY(), SIZE, SIZE,
                selected ? SELECTED_BORDER_COLOR : active && isHovered() ? 0xFFFFFFFF : BORDER_COLOR);
        if (!active) {
            graphics.fill(getX() + 1, getY() + 1, getX() + SIZE - 1, getY() + SIZE - 1, 0xAA0A0A0A);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
