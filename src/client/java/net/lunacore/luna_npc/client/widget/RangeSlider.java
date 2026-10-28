package net.lunacore.luna_npc.client.widget;

import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

// A slider with two anchors defining a min/max range; dragging grabs the nearest anchor.
public class RangeSlider extends AbstractWidget {

    private static final int HANDLE_WIDTH = 6;
    private static final int TRACK_COLOR = 0xFF2B2B2B;
    private static final int RANGE_COLOR = 0xFF5A8A3C;
    private static final int HANDLE_COLOR = 0xFFD0D0D0;
    private static final int OUTLINE_COLOR = 0xFF000000;

    private final int boundMin;
    private final int boundMax;
    private final BiConsumer<Integer, Integer> onChange;
    private int valueMin;
    private int valueMax;
    private boolean draggingMaxHandle;

    public RangeSlider(int x, int y, int width, int height, Component message,
                       int boundMin, int boundMax, int valueMin, int valueMax,
                       BiConsumer<Integer, Integer> onChange) {
        super(x, y, width, height, message);
        this.boundMin = boundMin;
        this.boundMax = boundMax;
        this.valueMin = valueMin;
        this.valueMax = valueMax;
        this.onChange = onChange;
    }

    public int valueMin() {
        return valueMin;
    }

    public int valueMax() {
        return valueMax;
    }

    private int valueToX(int value) {
        float progress = (float) (value - boundMin) / (boundMax - boundMin);
        return getX() + HANDLE_WIDTH / 2 + Math.round(progress * (getWidth() - HANDLE_WIDTH));
    }

    private int xToValue(double mouseX) {
        float progress = (float) ((mouseX - getX() - HANDLE_WIDTH / 2.0) / (getWidth() - HANDLE_WIDTH));
        int value = boundMin + Math.round(progress * (boundMax - boundMin));
        return Math.clamp(value, boundMin, boundMax);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int clicked = xToValue(event.x());
        draggingMaxHandle = Math.abs(clicked - valueMax) < Math.abs(clicked - valueMin)
                || (clicked > valueMax);
        moveDraggedHandle(clicked);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        moveDraggedHandle(xToValue(event.x()));
    }

    private void moveDraggedHandle(int value) {
        if (draggingMaxHandle) {
            valueMax = Math.max(value, valueMin);
        } else {
            valueMin = Math.min(value, valueMax);
        }
        onChange.accept(valueMin, valueMax);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        int trackTop = getY() + getHeight() / 2 - 2;
        int trackBottom = getY() + getHeight() / 2 + 2;
        graphics.fill(getX(), trackTop, getX() + getWidth(), trackBottom, TRACK_COLOR);
        graphics.fill(valueToX(valueMin), trackTop, valueToX(valueMax), trackBottom, RANGE_COLOR);
        drawHandle(graphics, valueToX(valueMin));
        drawHandle(graphics, valueToX(valueMax));
    }

    private void drawHandle(GuiGraphicsExtractor graphics, int centerX) {
        int left = centerX - HANDLE_WIDTH / 2;
        graphics.fill(left, getY(), left + HANDLE_WIDTH, getY() + getHeight(), HANDLE_COLOR);
        graphics.outline(left, getY(), HANDLE_WIDTH, getHeight(), OUTLINE_COLOR);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
