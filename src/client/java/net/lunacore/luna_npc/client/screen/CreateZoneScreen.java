package net.lunacore.luna_npc.client.screen;

import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcWarnings;
import net.lunacore.luna_npc.client.ClientZones;
import net.lunacore.luna_npc.network.UpdateZonesPayload;
import net.lunacore.luna_npc.npc.NpcValidation;
import net.lunacore.luna_npc.api.NpcZone;
import net.lunacore.luna_npc.api.SurfaceMode;
import net.lunacore.luna_npc.api.ZoneShape;

// Naming step for a zone whose geometry was just drawn in-world with the Zone Wand: the shape and
// dimension are already fixed, so this one-card dialog only sets the name, whether height is ignored,
// and where inside the zone NPCs may spawn (surface / anywhere / caves). Nothing is saved until
// Create. Mirrors CreateCubeScreen. Replaces the old cube naming dialog.
public class CreateZoneScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 96;
    private static final int FIELD_WIDTH = 200;
    private static final String DEFAULT_NAME = "Zone";
    private static final int WARNING_COLOR = 0xFFE33B3B;

    private final ZoneShape shape;
    private final String dimension;

    private EditBox nameField;
    private Button surfaceButton;
    private boolean ignoreHeight;
    private SurfaceMode surfaceMode = SurfaceMode.ANYWHERE;

    public CreateZoneScreen(ZoneShape shape, String dimension) {
        super(Component.translatable("gui.luna_npc.create_zone.title"));
        this.shape = shape;
        this.dimension = dimension;
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - 73;
    }

    @Override
    protected void init() {
        int fieldX = this.width / 2 - FIELD_WIDTH / 2;

        nameField = addRenderableWidget(new EditBox(this.font, fieldX, panelY() + 20, FIELD_WIDTH, 20,
                Component.translatable("gui.luna_npc.zone.name")));
        nameField.setMaxLength(NpcValidation.MAX_ZONE_NAME_LENGTH);
        nameField.setHint(Component.literal(DEFAULT_NAME));
        setInitialFocus(nameField);

        // The flat-shape warning re-reads ignoreHeight every frame, so toggling this box is enough.
        addRenderableWidget(Checkbox.builder(Component.translatable("gui.luna_npc.create_zone.ignore_height"), this.font)
                .pos(fieldX, panelY() + 46).selected(ignoreHeight)
                .onValueChange((box, value) -> ignoreHeight = value).build());

        surfaceButton = addRenderableWidget(Button.builder(surfaceModeLabel(surfaceMode), button -> {
            surfaceMode = nextMode(surfaceMode);
            surfaceButton.setMessage(surfaceModeLabel(surfaceMode));
        }).bounds(fieldX, panelY() + 68, FIELD_WIDTH, 20).build());

        int buttonY = panelY() + PANEL_HEIGHT + 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.create_zone.create"), button -> create())
                .bounds(this.width / 2 - 102, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(this.width / 2 + 2, buttonY, 100, 20).build());
    }

    private static SurfaceMode nextMode(SurfaceMode mode) {
        SurfaceMode[] values = SurfaceMode.values();
        return values[(mode.ordinal() + 1) % values.length];
    }

    private static Component surfaceModeLabel(SurfaceMode mode) {
        String key = switch (mode) {
            case ANYWHERE -> "gui.luna_npc.create_zone.surface.anywhere";
            case SURFACE_ONLY -> "gui.luna_npc.create_zone.surface.surface";
            case CAVES_ONLY -> "gui.luna_npc.create_zone.surface.caves";
        };
        return Component.translatable("gui.luna_npc.create_zone.surface_mode")
                .append(": ").append(Component.translatable(key));
    }

    private void create() {
        if (ClientZones.all().size() >= NpcValidation.MAX_ZONES) {
            onClose();
            return;
        }
        String name = nameField.getValue().trim();
        if (name.isEmpty()) {
            name = DEFAULT_NAME;
        }
        NpcZone zone = new NpcZone(freshZoneId(name), name, dimension, shape, ignoreHeight, surfaceMode);
        List<NpcZone> updated = ClientZones.editableCopy();
        updated.add(zone);
        ClientPlayNetworking.send(new UpdateZonesPayload(updated));
        onClose();
    }

    // Lowercase-with-underscores from the name; falls back to "zone" when the name has no usable
    // characters, then appends a number until the id is free of collisions.
    private String freshZoneId(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "zone";
        }
        if (ClientZones.byId(base) == null) {
            return base;
        }
        int n = 2;
        String id = base + "_" + n;
        while (ClientZones.byId(id) != null) {
            id = base + "_" + (++n);
        }
        return id;
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT,
                Component.translatable("gui.luna_npc.create_zone.title"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // A shape with no vertical span can only spawn once it is collapsed to a full-height column.
        if (NpcWarnings.zoneFlatNoSpawns(shape, ignoreHeight)) {
            graphics.textWithWordWrap(this.font, Component.translatable("gui.luna_npc.create_zone.flat_warning"),
                    panelX() + 10, panelY() + PANEL_HEIGHT + 6, PANEL_WIDTH - 20, WARNING_COLOR);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}
