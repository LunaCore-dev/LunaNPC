package net.lunacore.luna_npc.client.screen;

import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcRoute;
import net.lunacore.luna_npc.api.NpcRoutePoint;
import net.lunacore.luna_npc.api.NpcRouteSpeed;
import net.lunacore.luna_npc.client.ClientRoutes;
import net.lunacore.luna_npc.network.UpdateRoutesPayload;
import net.lunacore.luna_npc.npc.NpcValidation;

// Naming step for a route just clicked out in the world with the Route Creator: the points are fixed, so
// this sets only the name and the speed. Waits and look directions are set later in Edit Points.
public class CreateRouteScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 76;
    private static final int FIELD_WIDTH = 200;
    private static final String DEFAULT_NAME = "Route";

    private final List<BlockPos> points;
    private final String dimension;
    private NpcRouteSpeed speed = NpcRouteSpeed.WALK;

    private EditBox nameField;
    private Button speedButton;

    public CreateRouteScreen(List<BlockPos> points, String dimension) {
        super(Component.translatable("gui.luna_npc.create_route.title"));
        this.points = List.copyOf(points);
        this.dimension = dimension;
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - 60;
    }

    @Override
    protected void init() {
        int fieldX = this.width / 2 - FIELD_WIDTH / 2;
        nameField = addRenderableWidget(new EditBox(this.font, fieldX, panelY() + 20, FIELD_WIDTH, 20,
                Component.translatable("gui.luna_npc.route.name")));
        nameField.setMaxLength(NpcValidation.MAX_ROUTE_NAME_LENGTH);
        nameField.setHint(Component.literal(DEFAULT_NAME));
        setInitialFocus(nameField);
        speedButton = addRenderableWidget(Button.builder(ManageRoutesScreen.speedLabel(speed), button -> {
            NpcRouteSpeed[] speeds = NpcRouteSpeed.values();
            speed = speeds[(speed.ordinal() + 1) % speeds.length];
            speedButton.setMessage(ManageRoutesScreen.speedLabel(speed));
        }).bounds(fieldX, panelY() + 46, FIELD_WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.luna_npc.route.speed.tooltip"))).build());

        int buttonY = panelY() + PANEL_HEIGHT + 10;
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.create_route.create"), button -> create())
                .bounds(this.width / 2 - 102, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(this.width / 2 + 2, buttonY, 100, 20).build());
    }

    private void create() {
        if (ClientRoutes.all().size() >= NpcValidation.MAX_ROUTES) {
            onClose();
            return;
        }
        String name = nameField.getValue().trim();
        if (name.isEmpty()) {
            name = DEFAULT_NAME;
        }
        NpcRoute route = new NpcRoute(freshRouteId(name), name, dimension,
                points.stream().map(NpcRoutePoint::at).toList(), speed);
        List<NpcRoute> updated = ClientRoutes.editableCopy();
        updated.add(route);
        ClientPlayNetworking.send(new UpdateRoutesPayload(updated));
        onClose();
    }

    // Lowercase-with-underscores from the name, numbered until it is free (the zone naming rule).
    private static String freshRouteId(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "route";
        }
        String id = base;
        int suffix = 2;
        while (ClientRoutes.byId(id) != null) {
            id = base + "_" + suffix++;
        }
        return id;
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}
