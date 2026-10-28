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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.client.ClientStructures;
import net.lunacore.luna_npc.client.util.FuzzySearch;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;

// Generic single-select picker over a list of registry ids (the reuse target for the quest
// enter-objective targets), following the sound/mob picker layout: a panelled fuzzy-matched list.
// The three factories read from the live client data — biomes from the synced registry, structures
// from the ClientStructures cache (that registry isn't synced), dimensions from the joined level
// keys — so modded content is offered automatically. Clicking a row picks its id and returns.
public class RegistryPickerScreen extends LunaScreen {

    private static final int LIST_TOP = 54;

    public record Option(String id, Component label, String search) { }

    private final Screen parent;
    private final Component searchHint;
    private final List<Option> options;
    private final Consumer<String> onSelected;
    private SimpleSelectionList list;
    private String query = "";

    public RegistryPickerScreen(Screen parent, Component title, Component searchHint,
                                List<Option> options, Consumer<String> onSelected) {
        super(title);
        this.parent = parent;
        this.searchHint = searchHint;
        this.options = options;
        this.onSelected = onSelected;
    }

    public static RegistryPickerScreen biomes(Screen parent, Consumer<String> onSelected) {
        List<Option> options = new ArrayList<>();
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.registryAccess().lookup(Registries.BIOME).ifPresent(registry ->
                    registry.listElements().forEach(ref -> {
                        Identifier id = ref.key().identifier();
                        Component label = Component.translatable(
                                "biome." + id.getNamespace() + "." + id.getPath());
                        options.add(new Option(id.toString(), label, id + " " + label.getString()));
                    }));
        }
        sortByLabel(options);
        return new RegistryPickerScreen(parent, Component.translatable("gui.luna_npc.biome_picker.title"),
                Component.translatable("gui.luna_npc.biome_picker.search"), options, onSelected);
    }

    public static RegistryPickerScreen structures(Screen parent, Consumer<String> onSelected) {
        List<Option> options = new ArrayList<>();
        for (String id : ClientStructures.all()) {
            Identifier parsed = Identifier.tryParse(id);
            String path = parsed == null ? id : parsed.getPath();
            options.add(new Option(id, Component.literal(CreateNpcScreen.prettify(path)), id));
        }
        sortByLabel(options);
        return new RegistryPickerScreen(parent, Component.translatable("gui.luna_npc.structure_picker.title"),
                Component.translatable("gui.luna_npc.structure_picker.search"), options, onSelected);
    }

    public static RegistryPickerScreen dimensions(Screen parent, Consumer<String> onSelected) {
        List<Option> options = new ArrayList<>();
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.levels().forEach(key -> {
                Identifier id = key.identifier();
                options.add(new Option(id.toString(), Component.literal(CreateNpcScreen.prettify(id.getPath())),
                        id.toString()));
            });
        }
        sortByLabel(options);
        return new RegistryPickerScreen(parent, Component.translatable("gui.luna_npc.dimension_picker.title"),
                Component.translatable("gui.luna_npc.dimension_picker.search"), options, onSelected);
    }

    private static void sortByLabel(List<Option> options) {
        options.sort(Comparator.comparing(option -> option.label().getString().toLowerCase(Locale.ROOT)));
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

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX - 50, this.height - 28, 100, 20).build());

        rebuildList();
    }

    private void rebuildList() {
        record Scored(SimpleSelectionList.Entry entry, int score) { }
        List<Scored> scored = new ArrayList<>();
        for (Option option : options) {
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
        list.fill(entries, null);
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
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.npc_list.empty"),
                    this.width / 2, LIST_TOP + 40, LABEL_COLOR);
        }
    }
}
