package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
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
import net.lunacore.luna_npc.api.NpcDialogNode;

// Picks the node a choice or JUMP routes to. The list is every node in the dialog plus an "End" row
// (a blank target that closes the conversation); clicking returns the chosen id straight away.
public class NodePickerScreen extends LunaScreen {

    private static final int LIST_TOP = 54;
    private static final int SNIPPET_LENGTH = 28;

    private final Screen parent;
    private final List<NpcDialogNode> nodes;
    private final Consumer<String> onSelected;

    private SimpleSelectionList nodeList;
    private EditBox searchField;

    public NodePickerScreen(Screen parent, List<NpcDialogNode> nodes, Consumer<String> onSelected) {
        super(Component.translatable("gui.luna_npc.dialog.node_picker.title"));
        this.parent = parent;
        this.nodes = nodes;
        this.onSelected = onSelected;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        searchField = addRenderableWidget(new EditBox(this.font, centerX - 110, 28, 220, 20, this.title));
        searchField.setHint(Component.translatable("gui.luna_npc.dialog.node_picker.search"));
        searchField.setResponder(value -> rebuildList());

        nodeList = addRenderableWidget(new SimpleSelectionList(220, this.height - LIST_TOP - 40, LIST_TOP));
        nodeList.setX(centerX - 110);

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX - 50, this.height - 28, 100, 20).build());

        rebuildList();
    }

    private void rebuildList() {
        String query = searchField.getValue();
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        entries.add(new SimpleSelectionList.Entry("",
                Component.translatable("gui.luna_npc.dialog.node_picker.end"), this::finish));
        record Scored(SimpleSelectionList.Entry entry, int score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Component label = indexedLabel(i, nodes.get(i));
            int rank = FuzzySearch.score(query, label.getString());
            if (rank < 0) {
                continue;
            }
            scored.add(new Scored(new SimpleSelectionList.Entry(nodes.get(i).nodeId(), label, this::finish), rank));
        }
        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
        for (Scored s : scored) {
            entries.add(s.entry());
        }
        nodeList.fill(entries, null);
    }

    private void finish(String nodeId) {
        onSelected.accept(nodeId);
        onClose();
    }

    private static Component indexedLabel(int index, NpcDialogNode node) {
        return Component.literal((index + 1) + ": " + snippet(node));
    }

    static Component nodeLabel(List<NpcDialogNode> nodes, String nodeId) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).nodeId().equals(nodeId)) {
                return indexedLabel(i, nodes.get(i));
            }
        }
        return Component.translatable("gui.luna_npc.dialog.node_picker.unknown");
    }

    private static String snippet(NpcDialogNode node) {
        if (node.speakerLine().isBlank()) {
            return Component.translatable("gui.luna_npc.dialog.graph.empty_node").getString();
        }
        String line = node.speakerLine();
        return line.length() <= SNIPPET_LENGTH ? line : line.substring(0, SNIPPET_LENGTH) + "…";
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (nodeList == null) {
            return;
        }
        int top = searchField.getY() - 5;
        int bottom = nodeList.getY() + nodeList.getHeight() + 5;
        panel(graphics, nodeList.getX() - 5, top, nodeList.getWidth() + 10, bottom - top);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
    }
}
