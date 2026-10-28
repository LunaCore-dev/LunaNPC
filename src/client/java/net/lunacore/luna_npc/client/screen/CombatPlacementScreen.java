package net.lunacore.luna_npc.client.screen;

import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.widget.LabeledSlider;
import net.lunacore.luna_npc.api.NpcCombatPlacement;
import net.lunacore.luna_npc.api.NpcPlacementCentre;
import net.lunacore.luna_npc.api.NpcPlacementShape;
import net.lunacore.luna_npc.npc.NpcValidation;

// Where a placing action or attack drops its points: a shape spread around a centre, between a min and max
// distance, with a point count, an angle offset and a vertical anchor. Distances and count carry the model's
// placement caps; everything here maps one field to one control.
public class CombatPlacementScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 218;

    private final Screen parent;
    private final Consumer<NpcCombatPlacement> onDone;
    private NpcPlacementCentre centre;
    private NpcPlacementShape shape;
    private float minDistance;
    private float maxDistance;
    private int count;
    private int angleOffset;
    private int yMode;

    private LabeledSlider minSlider;
    private LabeledSlider maxSlider;

    public CombatPlacementScreen(Screen parent, NpcCombatPlacement current,
                                 Consumer<NpcCombatPlacement> onDone) {
        super(Component.translatable("gui.luna_npc.combat.placement.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.centre = current.centre();
        this.shape = current.shape();
        this.minDistance = current.minDistance();
        this.maxDistance = current.maxDistance();
        this.count = current.count();
        this.angleOffset = current.angleOffset();
        this.yMode = current.yMode();
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }

    @Override
    protected void init() {
        int contentX = panelX() + 12;
        int fieldWidth = PANEL_WIDTH - 24;
        int top = panelY();

        addRenderableWidget(CycleButton.<NpcPlacementCentre>builder(CombatLabels::centre, centre)
                .withValues(NpcPlacementCentre.values())
                .create(contentX, top + 24, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.combat.placement.centre"),
                        (button, selected) -> centre = selected));
        addRenderableWidget(CycleButton.<NpcPlacementShape>builder(CombatLabels::shape, shape)
                .withValues(NpcPlacementShape.values())
                .create(contentX, top + 46, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.combat.placement.shape"),
                        (button, selected) -> shape = selected));

        minSlider = addRenderableWidget(new LabeledSlider(contentX, top + 72, fieldWidth, 20,
                Component.translatable("gui.luna_npc.combat.placement.min"),
                LabeledSlider.Curve.lowBiased(0, NpcValidation.MAX_PLACEMENT_DISTANCE), minDistance,
                v -> String.format(Locale.ROOT, "%.1f", v), v -> {
                    minDistance = v;
                    if (maxDistance < minDistance) {
                        maxDistance = minDistance;
                        maxSlider.setValue(maxDistance);
                    }
                }));
        maxSlider = addRenderableWidget(new LabeledSlider(contentX, top + 94, fieldWidth, 20,
                Component.translatable("gui.luna_npc.combat.placement.max"),
                LabeledSlider.Curve.lowBiased(0, NpcValidation.MAX_PLACEMENT_DISTANCE), maxDistance,
                v -> String.format(Locale.ROOT, "%.1f", v), v -> {
                    maxDistance = v;
                    if (minDistance > maxDistance) {
                        minDistance = maxDistance;
                        minSlider.setValue(minDistance);
                    }
                }));
        addRenderableWidget(new LabeledSlider(contentX, top + 116, fieldWidth, 20,
                Component.translatable("gui.luna_npc.combat.placement.count"),
                LabeledSlider.Curve.linear(1, NpcValidation.MAX_PLACEMENT_POINTS), count,
                v -> String.valueOf(Math.round(v)), v -> count = Math.round(v)));
        addRenderableWidget(new LabeledSlider(contentX, top + 138, fieldWidth, 20,
                Component.translatable("gui.luna_npc.combat.placement.angle"),
                LabeledSlider.Curve.linear(0, 360), angleOffset,
                v -> Math.round(v) + "°", v -> angleOffset = Math.round(v)));
        addRenderableWidget(CycleButton.<Integer>builder(CombatLabels::yMode, yMode)
                .withValues(0, 1, 2)
                .create(contentX, top + 160, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.combat.placement.y_mode"),
                        (button, selected) -> yMode = selected));

        int buttonWidth = (fieldWidth - 6) / 2;
        int buttonY = top + PANEL_HEIGHT - 26;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(contentX, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(contentX + buttonWidth + 6, buttonY, buttonWidth, 20).build());
    }

    private void save() {
        onDone.accept(new NpcCombatPlacement(centre, shape, minDistance, maxDistance, count, angleOffset, yMode));
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
    }
}
