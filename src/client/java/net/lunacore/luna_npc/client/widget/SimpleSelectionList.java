package net.lunacore.luna_npc.client.widget;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

// Compact scrollable single-column list used across LunaNPC screens (see the CLAUDE.md UI rules); selecting an
// entry runs its callback with the entry id. Entries may carry an optional leading icon (potion
// sprite, item, ...) drawn by the caller.
public class SimpleSelectionList extends ObjectSelectionList<SimpleSelectionList.Entry> {

    public static final int ROW_HEIGHT = 14;
    private static final int ICON_TEXT_INSET = 20;

    public SimpleSelectionList(int width, int height, int y) {
        this(width, height, y, ROW_HEIGHT);
    }

    public SimpleSelectionList(int width, int height, int y, int rowHeight) {
        super(Minecraft.getInstance(), width, height, y, rowHeight);
    }

    public void fill(List<Entry> entries, Entry selected) {
        clearEntries();
        int textWidth = getRowWidth() - 4;
        for (Entry entry : entries) {
            entry.maxTextWidth = textWidth;
            addEntry(entry);
        }
        super.setSelected(selected);
        if (selected != null) {
            centerScrollOn(selected);
        }
    }

    private static net.minecraft.network.chat.Component clip(net.minecraft.network.chat.Component text,
                                                             int maxWidth) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
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
    public void setSelected(Entry entry) {
        super.setSelected(entry);
        if (entry != null) {
            entry.onSelect.accept(entry.id);
        }
    }

    // Leaves a margin on the right so row text clears the scrollbar instead of running under it.
    @Override
    public int getRowWidth() {
        return getWidth() - 16;
    }

    // Keep the scrollbar inside the list (and the panel behind it) rather than on its outer edge.
    @Override
    protected int scrollBarX() {
        return getX() + getWidth() - 8;
    }

    // Every LunaNPC list sits on an opaque panel the screen draws behind it, so the list's own
    // dark background would only double-darken it and make the shade differ between screens.
    // Suppress it (and the top/bottom separator fades) for one uniform panel colour everywhere.
    @Override
    protected void extractListBackground(GuiGraphicsExtractor graphics) {
    }

    @Override
    protected void extractListSeparators(GuiGraphicsExtractor graphics) {
    }

    // Draws a small leading icon at the row's left edge; y is the row's vertical middle.
    public interface IconRenderer {
        void render(GuiGraphicsExtractor graphics, int x, int yMiddle);
    }

    // A ✔/• toggle row that reads its on/off state live, so clicking recolours it without
    // rebuilding the whole list (which would reset the scroll position).
    private static final int TOGGLE_ON_COLOR = 0xFFE0C060;
    private static final int TOGGLE_OFF_COLOR = 0xFFFFFFFF;

    public static class Entry extends ObjectSelectionList.Entry<Entry> {

        public final String id;
        private final Component label;
        private final Consumer<String> onSelect;
        private final IconRenderer icon;
        private final int textColor;
        private final BooleanSupplier toggleState;
        private int maxTextWidth;

        public Entry(String id, Component label, Consumer<String> onSelect) {
            this(id, label, onSelect, null, 0xFFFFFFFF);
        }

        public Entry(String id, Component label, Consumer<String> onSelect, IconRenderer icon, int textColor) {
            this(id, label, onSelect, icon, textColor, null);
        }

        // A live-toggle entry: onSelect flips the state, the marker and colour follow it.
        public Entry(String id, Component label, Consumer<String> onSelect, IconRenderer icon,
                     BooleanSupplier toggleState) {
            this(id, label, onSelect, icon, 0xFFFFFFFF, toggleState);
        }

        private Entry(String id, Component label, Consumer<String> onSelect, IconRenderer icon,
                      int textColor, BooleanSupplier toggleState) {
            this.id = id;
            this.label = label;
            this.onSelect = onSelect;
            this.icon = icon;
            this.textColor = textColor;
            this.toggleState = toggleState;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
                                   float partialTick) {
            int textX = getContentX() + 2;
            if (icon != null) {
                icon.render(graphics, getContentX() + 1, getContentYMiddle());
                textX = getContentX() + ICON_TEXT_INSET;
            }
            Component shown = label;
            int color = textColor;
            if (toggleState != null) {
                boolean on = toggleState.getAsBoolean();
                color = on ? TOGGLE_ON_COLOR : TOGGLE_OFF_COLOR;
                shown = Component.literal(on ? "✔ " : "• ").append(label);
            }
            int available = maxTextWidth - (textX - getContentX());
            graphics.text(Minecraft.getInstance().font, clip(shown, available), textX,
                    getContentYMiddle() - 4, color);
        }

        @Override
        public Component getNarration() {
            return label;
        }
    }
}
