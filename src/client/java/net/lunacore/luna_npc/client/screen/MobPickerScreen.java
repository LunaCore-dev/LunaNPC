package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.lunacore.luna_npc.client.util.FuzzySearch;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;

// Searchable entity-type picker (the reuse target for "pick a mob"), following the sound picker
// layout: a panelled list with a fuzzy-matched search field. Living entity types come from the live
// registry, so modded mobs are offered automatically. Clicking a row picks its id and returns.
public class MobPickerScreen extends LunaScreen {

    private static final int LIST_TOP = 54;

    private final Screen parent;
    private final Consumer<String> onSelected;
    private final List<MobOption> options = new ArrayList<>();
    private SimpleSelectionList mobList;
    private String query = "";

    private record MobOption(String id, Component label, String search) { }

    public MobPickerScreen(Screen parent, Consumer<String> onSelected) {
        super(Component.translatable("gui.luna_npc.mob_picker.title"));
        this.parent = parent;
        this.onSelected = onSelected;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            if (type.getCategory() == MobCategory.MISC || "minecraft:player".equals(id)) {
                continue;
            }
            Component label = Component.translatable(type.getDescriptionId());
            options.add(new MobOption(id, label, id + " " + label.getString()));
        }
        options.sort(Comparator.comparing(option -> option.label().getString().toLowerCase(Locale.ROOT)));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        EditBox searchField = addRenderableWidget(new EditBox(this.font, centerX - 110, 28, 220, 20, this.title));
        searchField.setMaxLength(100);
        searchField.setHint(Component.translatable("gui.luna_npc.mob_picker.search"));
        searchField.setResponder(value -> {
            query = value;
            rebuildList();
        });

        mobList = addRenderableWidget(new SimpleSelectionList(220, this.height - LIST_TOP - 40, LIST_TOP));
        mobList.setX(centerX - 110);

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX - 50, this.height - 28, 100, 20).build());

        rebuildList();
    }

    private void rebuildList() {
        record Scored(SimpleSelectionList.Entry entry, int score) { }
        List<Scored> scored = new ArrayList<>();
        for (MobOption option : options) {
            int rank = FuzzySearch.score(query, option.search());
            if (rank < 0) {
                continue;
            }
            scored.add(new Scored(new SimpleSelectionList.Entry(option.id(), option.label(), this::pick), rank));
        }
        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
        List<SimpleSelectionList.Entry> entries = new ArrayList<>(scored.size());
        for (Scored s : scored) {
            entries.add(s.entry());
        }
        mobList.fill(entries, null);
    }

    private void pick(String id) {
        onSelected.accept(id);
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (mobList == null) {
            return;
        }
        int top = 23;
        int bottom = mobList.getY() + mobList.getHeight() + 5;
        panel(graphics, mobList.getX() - 5, top, mobList.getWidth() + 10, bottom - top);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        if (mobList.children().isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.npc_list.empty"),
                    this.width / 2, LIST_TOP + 40, LABEL_COLOR);
        }
    }
}
