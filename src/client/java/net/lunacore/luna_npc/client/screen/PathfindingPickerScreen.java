package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.pathfinding.PathfindingType;

// Pathfinding algorithm picker: the algorithm list on the left, a live info card on the right
// with the two star ratings (path quality, compute cost) and a short description. All five star
// slots are always drawn; the rating fills them in gold, the rest stay uncolored. The card
// follows the list selection in real time.
public class PathfindingPickerScreen extends LunaScreen {

    private static final int LIST_TOP = 54;
    private static final int LIST_WIDTH = 130;
    private static final int INFO_WIDTH = 178;
    private static final int INFO_HEIGHT = 132;
    private static final int GAP = 8;

    private final Screen parent;
    private final Consumer<String> onSelected;
    private String selectedId;

    private SimpleSelectionList typeList;

    public PathfindingPickerScreen(Screen parent, String currentId, Consumer<String> onSelected) {
        super(Component.translatable("gui.luna_npc.pathfinding_picker.title"));
        this.parent = parent;
        this.onSelected = onSelected;
        this.selectedId = PathfindingType.byId(currentId) == null
                ? PathfindingType.VANILLA.id() : currentId;
    }

    private int leftX() {
        return this.width / 2 - (LIST_WIDTH + GAP + INFO_WIDTH) / 2;
    }

    private int infoX() {
        return leftX() + LIST_WIDTH + GAP;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        typeList = addRenderableWidget(new SimpleSelectionList(LIST_WIDTH, INFO_HEIGHT, LIST_TOP));
        typeList.setX(leftX());
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (PathfindingType type : PathfindingType.values()) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(type.id(),
                    typeName(type.id()), id -> selectedId = id);
            entries.add(entry);
            if (type.id().equals(selectedId)) {
                selected = entry;
            }
        }
        typeList.fill(entries, selected);

        int buttonsY = LIST_TOP + INFO_HEIGHT + 12;
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.pathfinding_picker.select"),
                button -> {
                    onSelected.accept(selectedId);
                    onClose();
                }).bounds(centerX - 102, buttonsY, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX + 2, buttonsY, 100, 20).build());
    }

    private static Component typeName(String id) {
        return Component.translatable("gui.luna_npc.pathfinding." + id);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        panel(graphics, leftX() - 5, LIST_TOP - 5, LIST_WIDTH + 10, INFO_HEIGHT + 10);
        titledPanel(graphics, infoX(), LIST_TOP, INFO_WIDTH, INFO_HEIGHT, typeName(selectedId));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        PathfindingType type = PathfindingType.byId(selectedId);
        if (type == null) {
            return;
        }
        int x = infoX() + 8;
        starRow(graphics, Component.translatable("gui.luna_npc.pathfinding_picker.quality"),
                type.qualityStars(), x, LIST_TOP + 22);
        starRow(graphics, Component.translatable("gui.luna_npc.pathfinding_picker.cost"),
                type.costStars(), x, LIST_TOP + 38);
        graphics.textWithWordWrap(this.font,
                Component.translatable("gui.luna_npc.pathfinding.desc." + selectedId),
                x, LIST_TOP + 58, INFO_WIDTH - 16, LABEL_COLOR);
    }

    private void starRow(GuiGraphicsExtractor graphics, Component label, int filled, int x, int y) {
        graphics.text(this.font, label, x, y, LABEL_COLOR);
        stars(graphics, this.font, filled, x + 92, y);
    }

    // The shared star-rating look: all five slots always visible, the rating in gold, the rest
    // kept as uncolored dark stars.
    static void stars(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                      int filled, int x, int y) {
        for (int slot = 0; slot < PathfindingType.MAX_STARS; slot++) {
            graphics.text(font, "★", x + slot * 10, y,
                    slot < filled ? SECTION_COLOR : STAR_EMPTY_COLOR);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
