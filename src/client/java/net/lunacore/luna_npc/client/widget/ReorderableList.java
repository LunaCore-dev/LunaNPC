package net.lunacore.luna_npc.client.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

// A compact custom list whose rows the user reorders by mouse: click a row to select it, then press and
// drag it up or down and the list reshuffles live around it. Each row carries a grab handle so it reads
// as draggable. It is sized to show all its rows (the caller caps the count), so there is no scrollbar
// for the drag to fight. Rows are plain labels the owner supplies; selection and reorder are reported
// back through callbacks. The template for any "reorder these items" list (see the CLAUDE.md UI rules).
public class ReorderableList extends AbstractWidget {

    public static final int ROW_HEIGHT = 15;

    private static final int SELECTED_FILL = 0x66E0C060;
    private static final int HOVER_FILL = 0x22FFFFFF;
    private static final int DRAG_FILL = 0x99E0C060;
    private static final int HANDLE_COLOR = 0xFF8A8A96;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    public interface Reorder {
        void moved(int from, int to);
    }

    private final List<Component> rows = new ArrayList<>();
    private final IntConsumer onSelect;
    private final Reorder onReorder;
    private int selected = -1;
    private int dragIndex = -1;
    private boolean dragging;

    public ReorderableList(int x, int y, int width, int height, IntConsumer onSelect, Reorder onReorder) {
        super(x, y, width, height, Component.empty());
        this.onSelect = onSelect;
        this.onReorder = onReorder;
    }

    public void setRows(List<Component> labels, int selected) {
        rows.clear();
        rows.addAll(labels);
        this.selected = selected >= 0 && selected < rows.size() ? selected : -1;
        dragIndex = -1;
        dragging = false;
    }

    public int selectedIndex() {
        return selected;
    }

    private int rowAt(double mouseY) {
        int index = (int) ((mouseY - getY()) / ROW_HEIGHT);
        return mouseY >= getY() && index >= 0 && index < rows.size() ? index : -1;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        selected = rowAt(event.y());
        dragIndex = selected;
        dragging = false;
        onSelect.accept(selected);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        if (dragIndex < 0) {
            return;
        }
        dragging = true;
        int target = Math.clamp((int) ((event.y() - getY()) / ROW_HEIGHT), 0, rows.size() - 1);
        if (target != dragIndex) {
            rows.add(target, rows.remove(dragIndex));
            onReorder.moved(dragIndex, target);
            dragIndex = target;
            selected = target;
            onSelect.accept(target);
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean inX = mouseX >= getX() && mouseX < getX() + getWidth();
        int hovered = inX ? rowAt(mouseY) : -1;
        for (int i = 0; i < rows.size(); i++) {
            int top = getY() + i * ROW_HEIGHT;
            int fill = dragging && i == dragIndex ? DRAG_FILL
                    : i == selected ? SELECTED_FILL : i == hovered ? HOVER_FILL : 0;
            if (fill != 0) {
                graphics.fill(getX(), top, getX() + getWidth(), top + ROW_HEIGHT, fill);
            }
            for (int bar = 0; bar < 3; bar++) {
                graphics.fill(getX() + 4, top + 4 + bar * 3, getX() + 10, top + 5 + bar * 3, HANDLE_COLOR);
            }
            graphics.text(font, clip(font, rows.get(i), getWidth() - 20), getX() + 15, top + 3, TEXT_COLOR);
        }
    }

    private static Component clip(Font font, Component text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String value = text.getString();
        while (!value.isEmpty() && font.width(value + "…") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return Component.literal(value + "…");
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
    }
}
