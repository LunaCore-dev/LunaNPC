package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcRoute;
import net.lunacore.luna_npc.api.NpcRouteSpeed;
import net.lunacore.luna_npc.client.ClientRouteRender;
import net.lunacore.luna_npc.client.ClientRoutes;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.network.UpdateRoutesPayload;
import net.lunacore.luna_npc.npc.NpcValidation;

// The route manager (Route Creator stick): pick a route on the left; rename it, switch its speed, toggle
// its in-world display, edit its points or delete it on the right; start a new one with New Route.
// Edits stay on a draft and commit on Save, like Manage Zones. Inspect and New Route act at once.
public class ManageRoutesScreen extends LunaScreen {

    private static final int TOP = 44;
    private static final int LEFT_WIDTH = 150;
    private static final int RIGHT_WIDTH = 180;
    private static final int GAP = 12;
    private static final int BUTTON_STRIP = 24;

    private final List<NpcRoute> draft;
    private String selectedId = "";

    private SimpleSelectionList routeList;
    private EditBox nameField;
    private Button removeButton;
    private Button inspectButton;
    private Button speedButton;
    private Button editPointsButton;

    public ManageRoutesScreen() {
        this(ClientRoutes.editableCopy(), "");
    }

    // Reopened by the point editor with the draft it was working on, so unsaved edits survive the trip.
    ManageRoutesScreen(List<NpcRoute> draft, String selectedId) {
        super(Component.translatable("gui.luna_npc.route.manage_title"));
        this.draft = new ArrayList<>(draft);
        this.selectedId = selectedId;
        if (selectedIndex() < 0 && !this.draft.isEmpty()) {
            this.selectedId = this.draft.getFirst().id();
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

    private NpcRoute selected() {
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

        routeList = addRenderableWidget(new SimpleSelectionList(LEFT_WIDTH, listBottom() - TOP - BUTTON_STRIP, TOP));
        routeList.setX(left);
        removeButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.route.delete"),
                button -> removeSelected()).bounds(left, listBottom() - 20, LEFT_WIDTH, 20).build());

        nameField = addRenderableWidget(new EditBox(this.font, right, TOP, RIGHT_WIDTH, 20,
                Component.translatable("gui.luna_npc.route.name")));
        nameField.setMaxLength(NpcValidation.MAX_ROUTE_NAME_LENGTH);
        nameField.setResponder(value -> {
            int index = selectedIndex();
            if (index >= 0) {
                draft.set(index, draft.get(index).withName(value));
                rebuildList();
            }
        });

        speedButton = addRenderableWidget(Button.builder(Component.empty(), button -> cycleSpeed())
                .bounds(right, TOP + 24, RIGHT_WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.luna_npc.route.speed.tooltip"))).build());
        editPointsButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.route.edit_points"),
                button -> editPoints()).bounds(right, TOP + 48, RIGHT_WIDTH, 20).build());
        inspectButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.route.inspect"),
                button -> toggleInspectSelected()).bounds(right, TOP + 72, RIGHT_WIDTH, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.route.new"), button -> startNewRoute())
                .bounds(right, listBottom() - 20, RIGHT_WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.luna_npc.route.new.tooltip"))).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.route.save"), button -> save())
                .bounds(this.width / 2 - 102, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(this.width / 2 + 2, this.height - 28, 100, 20).build());

        rebuildList();
        refreshControls();
    }

    private void startNewRoute() {
        if (draft.size() >= NpcValidation.MAX_ROUTES) {
            return;
        }
        ClientRouteRender.startNewRoute();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.luna_npc.route_placing"));
        }
        onClose();
    }

    private void cycleSpeed() {
        int index = selectedIndex();
        if (index < 0) {
            return;
        }
        NpcRouteSpeed[] speeds = NpcRouteSpeed.values();
        NpcRoute route = draft.get(index);
        draft.set(index, route.withSpeed(speeds[(route.speed().ordinal() + 1) % speeds.length]));
        refreshControls();
    }

    private void editPoints() {
        int index = selectedIndex();
        if (index < 0) {
            return;
        }
        Minecraft.getInstance().setScreenAndShow(new EditRouteScreen(draft, index));
    }

    private void toggleInspectSelected() {
        NpcRoute route = selected();
        if (route != null) {
            ClientRouteRender.toggleInspect(route.id());
            refreshControls();
        }
    }

    private void removeSelected() {
        int index = selectedIndex();
        if (index < 0) {
            return;
        }
        draft.remove(index);
        selectedId = draft.isEmpty() ? "" : draft.get(Math.min(index, draft.size() - 1)).id();
        rebuildList();
        refreshControls();
    }

    private void save() {
        ClientPlayNetworking.send(new UpdateRoutesPayload(draft));
        onClose();
    }

    private void rebuildList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selectedEntry = null;
        for (NpcRoute route : draft) {
            Component label = Component.literal(route.name() + "  ").append(Component.translatable(
                    "gui.luna_npc.route.point_count", route.size()).withStyle(ChatFormatting.GRAY));
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(route.id(), label, id -> {
                selectedId = id;
                refreshControls();
            });
            entries.add(entry);
            if (route.id().equals(selectedId)) {
                selectedEntry = entry;
            }
        }
        routeList.fill(entries, selectedEntry);
    }

    private void refreshControls() {
        NpcRoute route = selected();
        boolean has = route != null;
        nameField.visible = has;
        speedButton.visible = has;
        editPointsButton.visible = has;
        inspectButton.visible = has;
        removeButton.active = has;
        if (!has) {
            return;
        }
        speedButton.setMessage(speedLabel(route.speed()));
        inspectButton.setMessage(Component.translatable(ClientRouteRender.isInspected(route.id())
                ? "gui.luna_npc.route.inspecting" : "gui.luna_npc.route.inspect"));
        if (!nameField.getValue().equals(route.name())) {
            nameField.setValue(route.name());
        }
    }

    public static Component speedLabel(NpcRouteSpeed speed) {
        return Component.translatable("gui.luna_npc.route.speed").append(": ")
                .append(Component.translatable("gui.luna_npc.route.speed." + speed.getSerializedName()));
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (routeList == null) {
            return;
        }
        panel(graphics, leftX() - 5, TOP - 5, LEFT_WIDTH + 10, listBottom() - TOP + 10);
        graphics.outline(routeList.getX() - 1, routeList.getY() - 1,
                routeList.getWidth() + 2, routeList.getHeight() + 2, PANEL_BORDER);
        panel(graphics, rightX() - 6, TOP - 5, RIGHT_WIDTH + 12, listBottom() - TOP + 10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        if (draft.isEmpty()) {
            graphics.textWithWordWrap(this.font, Component.translatable("gui.luna_npc.route.empty"),
                    leftX() + 6, TOP + 30, LEFT_WIDTH - 12, LABEL_COLOR);
            return;
        }
        NpcRoute route = selected();
        if (route != null) {
            int y = TOP + 100;
            graphics.text(this.font, Component.literal(route.dimension()), rightX(), y, LABEL_COLOR);
            graphics.text(this.font, Component.translatable("gui.luna_npc.route.length",
                    Math.round(routeLength(route))), rightX(), y + 12, LABEL_COLOR);
            graphics.text(this.font, Component.translatable("gui.luna_npc.route.anchor",
                    route.point(0).pos().toShortString()), rightX(), y + 24, LABEL_COLOR);
        }
    }

    private static double routeLength(NpcRoute route) {
        double length = 0.0;
        for (int index = 1; index < route.size(); index++) {
            length += Math.sqrt(route.point(index - 1).pos().distSqr(route.point(index).pos()));
        }
        return length;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}
