package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.util.FuzzySearch;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;

// Generic multi-select toggle picker (the reuse target for "choose several ids from a set"): a
// panelled fuzzy-searchable list whose rows toggle a ✔ mark in place. Works on its own copy of the
// selection, so Done applies it and Cancel/Escape discards. The quest editor uses it to pick
// prerequisite quests.
public class MultiSelectPickerScreen extends LunaScreen {

    private static final int LIST_TOP = 54;

    public record Option(String id, Component label, String search) { }

    private final Screen parent;
    private final Component searchHint;
    private final List<Option> options;
    private final Set<String> selected;
    private final Consumer<Set<String>> onDone;
    private SimpleSelectionList list;
    private String query = "";

    public MultiSelectPickerScreen(Screen parent, Component title, Component searchHint,
                                   List<Option> options, Set<String> selected,
                                   Consumer<Set<String>> onDone) {
        super(title);
        this.parent = parent;
        this.searchHint = searchHint;
        this.options = options;
        this.selected = new LinkedHashSet<>(selected);
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        EditBox searchField = addRenderableWidget(new EditBox(this.font, centerX - 110, 28, 220, 20, this.title));
        searchField.setMaxLength(100);
        searchField.setHint(searchHint);
        searchField.setResponder(value -> {
            query = value;
            rebuildList();
        });

        list = addRenderableWidget(new SimpleSelectionList(220, this.height - LIST_TOP - 40, LIST_TOP));
        list.setX(centerX - 110);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            onDone.accept(new LinkedHashSet<>(selected));
            onClose();
        }).bounds(centerX - 102, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX + 2, this.height - 28, 100, 20).build());

        rebuildList();
    }

    private void rebuildList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        for (Option option : options) {
            if (!FuzzySearch.matches(query, option.search())) {
                continue;
            }
            entries.add(new SimpleSelectionList.Entry(option.id(), option.label(),
                    this::toggle, null, () -> selected.contains(option.id())));
        }
        list.fill(entries, null);
    }

    private void toggle(String id) {
        if (!selected.remove(id)) {
            selected.add(id);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (list == null) {
            return;
        }
        int top = 23;
        int bottom = list.getY() + list.getHeight() + 5;
        panel(graphics, list.getX() - 5, top, list.getWidth() + 10, bottom - top);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        if (list.children().isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.quest_prereq.empty"),
                    this.width / 2, LIST_TOP + 40, LABEL_COLOR);
        }
    }
}
