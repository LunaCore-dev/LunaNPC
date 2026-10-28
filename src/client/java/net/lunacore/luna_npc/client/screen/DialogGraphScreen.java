package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.widget.DialogGraphWidget;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.api.NpcDialog;
import net.lunacore.luna_npc.api.NpcDialogAction;
import net.lunacore.luna_npc.api.NpcDialogChoice;
import net.lunacore.luna_npc.api.NpcDialogNode;
import net.lunacore.luna_npc.npc.NpcValidation;

// The visual node-graph editor for a dialog tree. The graph fills the upper canvas; selecting a box
// slides an inline editing dock into the lower strip so the line, sounds, choices and actions are edited
// in place instead of on separate screens (choice targets are wired on the graph itself). Nodes live as a
// mutable working list the widget lays out; the dialog is rebuilt into a fresh NpcDialog on Done.
public class DialogGraphScreen extends LunaScreen {

    private static final int MARGIN = 16;
    private static final int TOOLBAR_Y = 28;
    private static final int GRAPH_TOP = 52;
    private static final int COL_GAP = 12;

    private final Screen parent;
    private final Consumer<NpcDialog> onDone;
    private final String dialogId;
    private final String name;
    private final boolean survivesNpcDeath;
    private final boolean survivesPlayerDeath;
    private final List<NpcDialogNode> nodes;
    private String startNodeId;
    private double viewX;
    private double viewY;
    private float viewZoom = 0.8F;

    private String selectedId;
    private int choiceSelected = -1;
    private int actionSelected = -1;
    private boolean populating;

    private DialogGraphWidget graph;
    private Button deleteButton;
    private Button setStartButton;
    private EditBox speakerField;
    private Button interactSoundButton;
    private Button triggerSoundButton;
    private EditBox choiceTextField;
    private SimpleSelectionList choiceList;
    private SimpleSelectionList actionList;
    private final List<AbstractWidget> dockWidgets = new ArrayList<>();

    private int dockTop;
    private int dockHeight;
    private int colAx;
    private int colBx;
    private int colCx;
    private int colW;
    private int rowTop;

    public DialogGraphScreen(Screen parent, NpcDialog dialog, Consumer<NpcDialog> onDone) {
        super(Component.translatable("gui.luna_npc.dialog.graph.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.dialogId = dialog.dialogId();
        this.name = dialog.name();
        this.survivesNpcDeath = dialog.storySurvivesNpcDeath();
        this.survivesPlayerDeath = dialog.storySurvivesPlayerDeath();
        this.nodes = new ArrayList<>(dialog.nodes());
        this.startNodeId = dialog.startNodeId();
    }

    @Override
    protected void init() {
        dockWidgets.clear();
        int graphBottom = this.height - 34;
        int graphHeight = graphBottom - GRAPH_TOP;
        graph = addRenderableWidget(new DialogGraphWidget(MARGIN, GRAPH_TOP, this.width - 2 * MARGIN,
                graphHeight, nodes, () -> startNodeId, this::syncDock, this::onGraphChanged,
                this::createNodeAt));
        graph.setView(viewX, viewY, viewZoom);
        graph.setSelection(selectedId);

        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.dialog.graph.new_node"),
                button -> addNode()).bounds(MARGIN, TOOLBAR_Y, 90, 20).build());
        setStartButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.luna_npc.dialog.graph.set_start"), button -> setStart())
                .bounds(MARGIN + 94, TOOLBAR_Y, 90, 20).build());

        dockHeight = Math.min(Math.max(104, Math.round(this.height * 0.25F)), Math.max(80, graphHeight - 30));
        dockTop = graphBottom - dockHeight;
        buildDock(graphBottom);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 102, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> cancel())
                .bounds(this.width / 2 + 2, this.height - 28, 100, 20).build());

        syncDock();
    }

    private void buildDock(int graphBottom) {
        int px = MARGIN;
        int pw = this.width - 2 * MARGIN;
        colW = (pw - 20 - 2 * COL_GAP) / 3;
        colAx = px + 10;
        colBx = colAx + colW + COL_GAP;
        colCx = colBx + colW + COL_GAP;
        rowTop = dockTop + 8;
        int btnY = graphBottom - 22;
        int listBottom = btnY - 4;

        speakerField = dock(new EditBox(this.font, colAx, rowTop + 11, colW, 16,
                Component.translatable("gui.luna_npc.dialog.node.speaker_line")));
        speakerField.setMaxLength(NpcValidation.MAX_DIALOG_LINE_LENGTH);
        speakerField.setResponder(this::onSpeakerTyped);
        interactSoundButton = dock(Button.builder(Component.empty(), button -> pickSound(true))
                .bounds(colAx, rowTop + 31, colW, 16).build());
        triggerSoundButton = dock(Button.builder(Component.empty(), button -> pickSound(false))
                .bounds(colAx, rowTop + 50, colW, 16).build());
        deleteButton = dock(Button.builder(Component.translatable("gui.luna_npc.dialog.graph.delete_node"),
                button -> deleteSelected()).bounds(colAx, rowTop + 71, colW, 16).build());

        choiceTextField = dock(new EditBox(this.font, colBx, rowTop + 11, colW, 16,
                Component.translatable("gui.luna_npc.dialog.choice.text")));
        choiceTextField.setMaxLength(NpcValidation.MAX_DIALOG_CHOICE_LENGTH);
        choiceTextField.setResponder(this::onChoiceTextTyped);
        choiceList = dock(new SimpleSelectionList(colW, listBottom - (rowTop + 32), rowTop + 32));
        choiceList.setX(colBx);
        int cbw = (colW - 4) / 3;
        dock(iconButton(colBx, btnY, cbw, "Add", "gui.luna_npc.dialog.node.add_choice", this::addChoice));
        dock(iconButton(colBx + cbw + 2, btnY, cbw, "Cond", "gui.luna_npc.dialog.node.edit_conditions",
                this::openChoiceDetail));
        dock(iconButton(colBx + 2 * (cbw + 2), btnY, cbw, "Del", "gui.luna_npc.dialog.node.remove",
                this::removeChoice));

        actionList = dock(new SimpleSelectionList(colW, listBottom - (rowTop + 11), rowTop + 11));
        actionList.setX(colCx);
        int abw = (colW - 4) / 3;
        dock(iconButton(colCx, btnY, abw, "Add", "gui.luna_npc.dialog.node.add_action", this::addAction));
        dock(iconButton(colCx + abw + 2, btnY, abw, "Edit", "gui.luna_npc.dialog.node.edit", this::editAction));
        dock(iconButton(colCx + 2 * (abw + 2), btnY, abw, "Del", "gui.luna_npc.dialog.node.remove",
                this::removeAction));
    }

    private <T extends AbstractWidget> T dock(T widget) {
        addRenderableWidget(widget);
        dockWidgets.add(widget);
        return widget;
    }

    private Button iconButton(int x, int y, int width, String glyph, String tooltipKey, Runnable action) {
        Button button = Button.builder(Component.literal(glyph), b -> action.run()).bounds(x, y, width, 18).build();
        button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return button;
    }

    private void syncDock() {
        selectedId = graph.selectedNodeId();
        NpcDialogNode node = currentNode();
        boolean has = node != null;
        graph.setBottomInset(has ? dockHeight + 4 : 0);
        for (AbstractWidget widget : dockWidgets) {
            widget.visible = has;
        }
        refreshButtons();
        if (!has) {
            return;
        }
        populating = true;
        speakerField.setValue(node.speakerLine());
        populating = false;
        interactSoundButton.setMessage(soundLabel("gui.luna_npc.dialog.node.interact_sound", node.interactSound()));
        triggerSoundButton.setMessage(soundLabel("gui.luna_npc.dialog.node.trigger_sound", node.triggerSound()));
        rebuildChoices();
        rebuildActions();
    }

    private void onGraphChanged() {
        if (currentNode() != null) {
            rebuildChoices();
        }
        refreshButtons();
    }

    private String createNodeAt(int worldX, int worldY) {
        NpcDialogNode node = new NpcDialogNode(UUID.randomUUID().toString(), "", List.of(), List.of(),
                "", "", worldX, worldY);
        nodes.add(node);
        if (startNodeId.isBlank()) {
            startNodeId = node.nodeId();
        }
        return node.nodeId();
    }

    private void addNode() {
        NpcDialogNode node = new NpcDialogNode(UUID.randomUUID().toString(), "", List.of(), List.of(),
                "", "", graph.newNodeX(), graph.newNodeY());
        nodes.add(node);
        if (startNodeId.isBlank()) {
            startNodeId = node.nodeId();
        }
        choiceSelected = -1;
        actionSelected = -1;
        graph.selectAndFocus(node.nodeId());
    }

    private void deleteSelected() {
        NpcDialogNode selected = currentNode();
        if (selected == null) {
            return;
        }
        String removedId = selected.nodeId();
        nodes.removeIf(node -> node.nodeId().equals(removedId));
        for (int i = 0; i < nodes.size(); i++) {
            NpcDialogNode node = nodes.get(i);
            List<NpcDialogChoice> kept = new ArrayList<>();
            boolean changed = false;
            for (NpcDialogChoice choice : node.choices()) {
                if (choice.targetNodeId().equals(removedId)) {
                    changed = true;
                } else {
                    kept.add(choice);
                }
            }
            if (changed) {
                nodes.set(i, withChoices(node, kept));
            }
        }
        if (startNodeId.equals(removedId)) {
            startNodeId = nodes.isEmpty() ? "" : nodes.get(0).nodeId();
        }
        choiceSelected = -1;
        actionSelected = -1;
        graph.deselect();
    }

    private void setStart() {
        NpcDialogNode selected = currentNode();
        if (selected != null) {
            startNodeId = selected.nodeId();
        }
    }

    private void onSpeakerTyped(String value) {
        if (populating) {
            return;
        }
        NpcDialogNode node = currentNode();
        if (node != null) {
            replaceNode(new NpcDialogNode(node.nodeId(), value, node.choices(), node.actions(),
                    node.interactSound(), node.triggerSound(), node.graphX(), node.graphY()));
        }
    }

    private void pickSound(boolean interact) {
        NpcDialogNode node = currentNode();
        if (node == null) {
            return;
        }
        captureView();
        String current = interact ? node.interactSound() : node.triggerSound();
        Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(this, current, value -> {
            NpcDialogNode live = currentNode();
            if (live == null) {
                return;
            }
            replaceNode(interact
                    ? new NpcDialogNode(live.nodeId(), live.speakerLine(), live.choices(), live.actions(),
                            value, live.triggerSound(), live.graphX(), live.graphY())
                    : new NpcDialogNode(live.nodeId(), live.speakerLine(), live.choices(), live.actions(),
                            live.interactSound(), value, live.graphX(), live.graphY()));
        }));
    }

    private void onChoiceTextTyped(String value) {
        if (populating) {
            return;
        }
        NpcDialogNode node = currentNode();
        if (node == null || choiceSelected < 0 || choiceSelected >= node.choices().size()) {
            return;
        }
        List<NpcDialogChoice> choices = new ArrayList<>(node.choices());
        NpcDialogChoice c = choices.get(choiceSelected);
        choices.set(choiceSelected, new NpcDialogChoice(value, c.targetNodeId(), c.conditions(), c.hideWhenLocked()));
        replaceNode(withChoices(node, choices));
    }

    private void addChoice() {
        NpcDialogNode node = currentNode();
        if (node == null) {
            return;
        }
        List<NpcDialogChoice> choices = new ArrayList<>(node.choices());
        choices.add(new NpcDialogChoice("Choice", "", List.of(), true));
        replaceNode(withChoices(node, choices));
        choiceSelected = choices.size() - 1;
        rebuildChoices();
    }

    private void openChoiceDetail() {
        NpcDialogNode node = currentNode();
        if (node == null || choiceSelected < 0 || choiceSelected >= node.choices().size()) {
            return;
        }
        int index = choiceSelected;
        captureView();
        Minecraft.getInstance().setScreenAndShow(new EditDialogChoiceScreen(this, node.choices().get(index),
                updated -> {
                    NpcDialogNode live = currentNode();
                    if (live == null || index >= live.choices().size()) {
                        return;
                    }
                    List<NpcDialogChoice> choices = new ArrayList<>(live.choices());
                    choices.set(index, updated);
                    replaceNode(withChoices(live, choices));
                }));
    }

    private void removeChoice() {
        NpcDialogNode node = currentNode();
        if (node == null || choiceSelected < 0 || choiceSelected >= node.choices().size()) {
            return;
        }
        List<NpcDialogChoice> choices = new ArrayList<>(node.choices());
        choices.remove(choiceSelected);
        replaceNode(withChoices(node, choices));
        choiceSelected = -1;
        rebuildChoices();
    }

    private void addAction() {
        if (currentNode() == null) {
            return;
        }
        captureView();
        Minecraft.getInstance().setScreenAndShow(new AddDialogActionScreen(this, action -> {
            NpcDialogNode live = currentNode();
            if (live == null) {
                return;
            }
            List<NpcDialogAction> actions = new ArrayList<>(live.actions());
            actions.add(action);
            replaceNode(withActions(live, actions));
            actionSelected = actions.size() - 1;
        }, nodes));
    }

    private void editAction() {
        NpcDialogNode node = currentNode();
        if (node == null || actionSelected < 0 || actionSelected >= node.actions().size()) {
            return;
        }
        int index = actionSelected;
        captureView();
        Minecraft.getInstance().setScreenAndShow(new EditDialogActionScreen(this, node.actions().get(index),
                nodes, updated -> {
                    NpcDialogNode live = currentNode();
                    if (live == null || index >= live.actions().size()) {
                        return;
                    }
                    List<NpcDialogAction> actions = new ArrayList<>(live.actions());
                    actions.set(index, updated);
                    replaceNode(withActions(live, actions));
                }));
    }

    private void removeAction() {
        NpcDialogNode node = currentNode();
        if (node == null || actionSelected < 0 || actionSelected >= node.actions().size()) {
            return;
        }
        List<NpcDialogAction> actions = new ArrayList<>(node.actions());
        actions.remove(actionSelected);
        replaceNode(withActions(node, actions));
        actionSelected = -1;
        rebuildActions();
    }

    private void rebuildChoices() {
        NpcDialogNode node = currentNode();
        List<NpcDialogChoice> choices = node == null ? List.of() : node.choices();
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (int i = 0; i < choices.size(); i++) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(String.valueOf(i),
                    choiceSummary(choices.get(i)), value -> selectChoice(Integer.parseInt(value)));
            entries.add(entry);
            if (i == choiceSelected) {
                selected = entry;
            }
        }
        if (selected == null) {
            choiceSelected = -1;
        }
        choiceList.fill(entries, selected);
        syncChoiceTextField();
    }

    private void selectChoice(int index) {
        choiceSelected = index;
        syncChoiceTextField();
    }

    private void syncChoiceTextField() {
        NpcDialogNode node = currentNode();
        boolean show = node != null && choiceSelected >= 0 && choiceSelected < node.choices().size();
        choiceTextField.visible = node != null && show;
        if (show) {
            populating = true;
            choiceTextField.setValue(node.choices().get(choiceSelected).text());
            populating = false;
        }
    }

    private void rebuildActions() {
        NpcDialogNode node = currentNode();
        List<NpcDialogAction> actions = node == null ? List.of() : node.actions();
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (int i = 0; i < actions.size(); i++) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(String.valueOf(i),
                    EditDialogNodeScreen.actionTypeLabel(actions.get(i).type()),
                    value -> actionSelected = Integer.parseInt(value));
            entries.add(entry);
            if (i == actionSelected) {
                selected = entry;
            }
        }
        if (selected == null) {
            actionSelected = -1;
        }
        actionList.fill(entries, selected);
    }

    private Component choiceSummary(NpcDialogChoice choice) {
        Component target = choice.targetNodeId().isBlank()
                ? Component.translatable("gui.luna_npc.dialog.choice.target_end")
                : NodePickerScreen.nodeLabel(nodes, choice.targetNodeId());
        return Component.literal(choice.text()).copy().append(" → ").append(target);
    }

    private Component soundLabel(String key, String sound) {
        Component base = Component.translatable(key);
        return sound.isBlank() ? base : base.copy().append(": ").append(Component.literal(shortSound(sound)));
    }

    private static String shortSound(String sound) {
        int dot = sound.lastIndexOf('.');
        return dot >= 0 && dot < sound.length() - 1 ? sound.substring(dot + 1) : sound;
    }

    private NpcDialogNode currentNode() {
        if (selectedId == null) {
            return null;
        }
        for (NpcDialogNode node : nodes) {
            if (node.nodeId().equals(selectedId)) {
                return node;
            }
        }
        return null;
    }

    private void replaceNode(NpcDialogNode updated) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).nodeId().equals(updated.nodeId())) {
                nodes.set(i, updated);
                return;
            }
        }
    }

    private static NpcDialogNode withChoices(NpcDialogNode node, List<NpcDialogChoice> choices) {
        return new NpcDialogNode(node.nodeId(), node.speakerLine(), choices, node.actions(),
                node.interactSound(), node.triggerSound(), node.graphX(), node.graphY());
    }

    private static NpcDialogNode withActions(NpcDialogNode node, List<NpcDialogAction> actions) {
        return new NpcDialogNode(node.nodeId(), node.speakerLine(), node.choices(), actions,
                node.interactSound(), node.triggerSound(), node.graphX(), node.graphY());
    }

    private void refreshButtons() {
        boolean hasSelection = graph != null && graph.selectedNode() != null;
        deleteButton.active = hasSelection;
        setStartButton.active = hasSelection;
    }

    private void captureView() {
        viewX = graph.viewX();
        viewY = graph.viewY();
        viewZoom = graph.zoom();
    }

    private void cancel() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void onClose() {
        String id = dialogId.isBlank() ? UUID.randomUUID().toString() : dialogId;
        onDone.accept(new NpcDialog(id, name, startNodeId, List.copyOf(nodes),
                survivesNpcDeath, survivesPlayerDeath));
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (graph == null || graph.selectedNode() == null) {
            return;
        }
        panel(graphics, MARGIN, dockTop, this.width - 2 * MARGIN, dockHeight);
        int btnY = dockTop + dockHeight - 22;
        graphics.outline(colBx, rowTop + 32, colW, btnY - 4 - (rowTop + 32), PANEL_BORDER);
        graphics.outline(colCx, rowTop + 11, colW, btnY - 4 - (rowTop + 11), PANEL_BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        Component count = Component.translatable("gui.luna_npc.dialog.graph.node_count", nodes.size());
        graphics.text(this.font, count, this.width - MARGIN - this.font.width(count), TOOLBAR_Y + 6,
                LABEL_COLOR);
        if (graph == null || graph.selectedNode() == null) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.dialog.graph.hint"),
                    this.width / 2, this.height - 46, LABEL_COLOR);
            return;
        }
        graphics.text(this.font, Component.translatable("gui.luna_npc.dialog.node.speaker_line"),
                colAx, rowTop, SECTION_COLOR);
        NpcDialogNode node = currentNode();
        int choiceCount = node == null ? 0 : node.choices().size();
        int actionCount = node == null ? 0 : node.actions().size();
        graphics.text(this.font, Component.translatable("gui.luna_npc.dialog.node.choices", choiceCount),
                colBx, rowTop, SECTION_COLOR);
        graphics.text(this.font, Component.translatable("gui.luna_npc.dialog.node.actions", actionCount),
                colCx, rowTop, SECTION_COLOR);
    }
}
