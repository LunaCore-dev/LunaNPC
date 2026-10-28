package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.ClientZoneRender;
import net.lunacore.luna_npc.client.ClientZones;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.network.UpdateZonesPayload;
import net.lunacore.luna_npc.npc.NpcValidation;
import net.lunacore.luna_npc.api.NpcZone;
import net.lunacore.luna_npc.api.ZoneShape;

// The zone manager (sneak + Zone Wand): pick a zone on the left, rename / remove / toggle its in-world
// border inspection on the right, and start a fresh placement of any shape from the New Zone grid.
// Rename and delete edits stay on a draft copy and commit only on Save, mirroring ManageCubesScreen;
// Inspect and the New-shape buttons act immediately client-side.
public class ManageZonesScreen extends LunaScreen {

    private static final int TOP = 44;
    private static final int LEFT_WIDTH = 150;
    private static final int RIGHT_WIDTH = 180;
    private static final int GAP = 12;
    // Height reserved inside the left panel for the Remove button beneath the list.
    private static final int BUTTON_STRIP = 24;

    private final List<NpcZone> draft;
    private String selectedId = "";

    private SimpleSelectionList zoneList;
    private EditBox nameField;
    private Button removeButton;
    private Button inspectButton;

    public ManageZonesScreen() {
        super(Component.translatable("gui.luna_npc.zone.manage_title"));
        draft = ClientZones.editableCopy();
        if (!draft.isEmpty()) {
            selectedId = draft.get(0).id();
        }
    }

    private int leftX() {
        return this.width / 2 - (LEFT_WIDTH + GAP + RIGHT_WIDTH) / 2;
    }

    private int rightX() {
        return leftX() + LEFT_WIDTH + GAP;
    }

    private int listBottom() {
        return this.height - 56;
    }

    private int newLabelY() {
        return listBottom() - 66;
    }

    private NpcZone selected() {
        int index = selectedIndex();
        return index < 0 ? null : draft.get(index);
    }

    private int selectedIndex() {
        for (int i = 0; i < draft.size(); i++) {
            if (draft.get(i).id().equals(selectedId)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void init() {
        int left = leftX();
        int right = rightX();

        // The list and its Remove button share one panel: the list stops short of the bottom and the
        // button sits in the reserved strip, so nothing floats below the card.
        int buttonRowY = listBottom() - 20;
        zoneList = addRenderableWidget(new SimpleSelectionList(LEFT_WIDTH, listBottom() - TOP - BUTTON_STRIP, TOP));
        zoneList.setX(left);
        removeButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.zone.delete"),
                button -> removeSelected()).bounds(left, buttonRowY, LEFT_WIDTH, 20).build());

        nameField = addRenderableWidget(new EditBox(this.font, right, TOP, RIGHT_WIDTH, 20,
                Component.translatable("gui.luna_npc.zone.name")));
        nameField.setMaxLength(NpcValidation.MAX_ZONE_NAME_LENGTH);
        nameField.setResponder(value -> {
            int index = selectedIndex();
            if (index >= 0) {
                draft.set(index, draft.get(index).withName(value));
                rebuildList();
            }
        });

        // Toggles the selected zone's in-world border on/off; the label follows the live state.
        inspectButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.zone.inspect"),
                button -> toggleInspectSelected()).bounds(right, TOP + 24, RIGHT_WIDTH, 20).build());

        // New Zone: a 2x2 grid, one button per shape. Each hands off to the in-world placement tool.
        int half = (RIGHT_WIDTH - 4) / 2;
        int row1 = listBottom() - 52;
        int row2 = listBottom() - 30;
        addRenderableWidget(Button.builder(shapeLabel(ZoneShape.Type.BOX), b -> startPlacing(ZoneShape.Type.BOX))
                .bounds(right, row1, half, 20).build());
        addRenderableWidget(Button.builder(shapeLabel(ZoneShape.Type.CYLINDER), b -> startPlacing(ZoneShape.Type.CYLINDER))
                .bounds(right + half + 4, row1, half, 20).build());
        addRenderableWidget(Button.builder(shapeLabel(ZoneShape.Type.SPHERE), b -> startPlacing(ZoneShape.Type.SPHERE))
                .bounds(right, row2, half, 20).build());
        addRenderableWidget(Button.builder(shapeLabel(ZoneShape.Type.POLYGON), b -> startPlacing(ZoneShape.Type.POLYGON))
                .bounds(right + half + 4, row2, half, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.zone.save"), button -> save())
                .bounds(this.width / 2 - 102, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(this.width / 2 + 2, this.height - 28, 100, 20).build());

        rebuild();
    }

    private void startPlacing(ZoneShape.Type type) {
        ClientZoneRender.startPlacing(type);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.luna_npc.zone_placing"));
        }
        onClose();
    }

    private void toggleInspectSelected() {
        NpcZone zone = selected();
        if (zone == null) {
            return;
        }
        ClientZoneRender.toggleInspect(zone.id());
        refreshControls();
    }

    private void removeSelected() {
        int index = selectedIndex();
        if (index < 0) {
            return;
        }
        draft.remove(index);
        selectedId = draft.isEmpty() ? "" : draft.get(Math.min(index, draft.size() - 1)).id();
        rebuild();
    }

    private void save() {
        ClientPlayNetworking.send(new UpdateZonesPayload(draft));
        onClose();
    }

    private void rebuild() {
        rebuildList();
        refreshControls();
    }

    private void rebuildList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selectedEntry = null;
        for (NpcZone zone : draft) {
            // Row shows the name plus a dim shape-type tag so the geometry reads at a glance.
            Component label = Component.literal(zone.name() + "  ")
                    .append(shapeLabel(zone.shape().type()).copy().withStyle(ChatFormatting.GRAY));
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(zone.id(), label, this::onZoneSelected);
            entries.add(entry);
            if (zone.id().equals(selectedId)) {
                selectedEntry = entry;
            }
        }
        zoneList.fill(entries, selectedEntry);
    }

    private void onZoneSelected(String id) {
        selectedId = id;
        refreshControls();
    }

    private void refreshControls() {
        NpcZone zone = selected();
        boolean has = zone != null;
        nameField.visible = has;
        removeButton.active = has;
        inspectButton.visible = has;
        if (has) {
            inspectButton.setMessage(inspectLabel(zone));
            if (!nameField.getValue().equals(zone.name())) {
                nameField.setValue(zone.name());
            }
        }
    }

    private static Component inspectLabel(NpcZone zone) {
        return ClientZoneRender.isInspected(zone.id())
                ? Component.translatable("gui.luna_npc.zone.inspecting")
                : Component.translatable("gui.luna_npc.zone.inspect");
    }

    private static Component shapeLabel(ZoneShape.Type type) {
        return Component.translatable(switch (type) {
            case BOX -> "gui.luna_npc.zone.shape.box";
            case CYLINDER -> "gui.luna_npc.zone.shape.cylinder";
            case SPHERE -> "gui.luna_npc.zone.shape.sphere";
            case POLYGON -> "gui.luna_npc.zone.shape.polygon";
        });
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (zoneList == null) {
            return;
        }
        // Left card holds the list plus its Remove button; the right card holds the edit controls.
        panel(graphics, leftX() - 5, TOP - 5, LEFT_WIDTH + 10, listBottom() - TOP + 10);
        // Thin inset outline directly around the embedded list (DESIGN rule for embedded lists).
        graphics.outline(zoneList.getX() - 1, zoneList.getY() - 1,
                zoneList.getWidth() + 2, zoneList.getHeight() + 2, PANEL_BORDER);
        panel(graphics, rightX() - 6, TOP - 5, RIGHT_WIDTH + 12, listBottom() - TOP + 10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        // The New Zone grid is global, so its heading shows whether or not a zone is selected.
        sectionTitle(graphics, Component.translatable("gui.luna_npc.zone.new_section"), rightX(), newLabelY());
        if (draft.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.zone.empty"),
                    leftX() + LEFT_WIDTH / 2, TOP + 30, LABEL_COLOR);
            return;
        }
        NpcZone zone = selected();
        if (zone != null) {
            graphics.text(this.font, shapeLabel(zone.shape().type()), rightX(), TOP + 50, LABEL_COLOR);
            graphics.text(this.font, Component.literal(zone.dimension()), rightX(), TOP + 64, LABEL_COLOR);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}
