package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.lunacore.luna_npc.api.NpcRoute;
import net.lunacore.luna_npc.api.NpcRoutePoint;
import net.lunacore.luna_npc.api.NpcRouteSpeed;
import net.lunacore.luna_npc.client.ClientRouteRender;
import net.lunacore.luna_npc.client.widget.LabeledSlider;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.network.UpdateRoutesPayload;
import net.lunacore.luna_npc.npc.NpcValidation;

// One route's points: the list on the left (reorder with Up/Down, remove), and the selected point's wait
// time, look direction and leg speed on the right. A reorder or removal that breaks a route rule is
// flagged in red and blocks Done until fixed. "Add points in world" saves the route and hands back to
// the Route Creator to click more points onto its end.
public class EditRouteScreen extends LunaScreen {

    private static final int TOP = 44;
    private static final int LEFT_WIDTH = 170;
    private static final int RIGHT_WIDTH = 180;
    private static final int GAP = 12;
    private static final int BUTTON_STRIP = 24;
    private static final int WARNING_COLOR = 0xFFE33B3B;

    private final List<NpcRoute> draft;
    private final int routeIndex;
    private final List<NpcRoutePoint> points;
    private final NpcRouteSpeed speed;
    private int selected = 0;

    private SimpleSelectionList pointList;
    private Button upButton;
    private Button downButton;
    private Button removeButton;
    private LabeledSlider waitSlider;
    private CycleButton<Boolean> lookButton;
    private LabeledSlider yawSlider;
    private Button faceMeButton;
    private CycleButton<Boolean> runButton;
    private Button doneButton;
    private Button addPointsButton;

    public EditRouteScreen(List<NpcRoute> draft, int routeIndex) {
        super(Component.translatable("gui.luna_npc.route.edit_title", draft.get(routeIndex).name()));
        this.draft = draft;
        this.routeIndex = routeIndex;
        this.points = new ArrayList<>(draft.get(routeIndex).points());
        this.speed = draft.get(routeIndex).speed();
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

    @Override
    protected void init() {
        int left = leftX();
        int right = rightX();
        pointList = addRenderableWidget(new SimpleSelectionList(LEFT_WIDTH, listBottom() - TOP - BUTTON_STRIP, TOP));
        pointList.setX(left);
        int third = (LEFT_WIDTH - 4) / 3;
        upButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.route.move_up"),
                button -> move(-1)).bounds(left, listBottom() - 20, third, 20).build());
        downButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.route.move_down"),
                button -> move(1)).bounds(left + third + 2, listBottom() - 20, third, 20).build());
        removeButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.route.remove_point"),
                button -> removePoint()).bounds(left + 2 * (third + 2), listBottom() - 20, third, 20).build());

        waitSlider = addRenderableWidget(new LabeledSlider(right, TOP, RIGHT_WIDTH, 20,
                Component.translatable("gui.luna_npc.route.wait"),
                LabeledSlider.Curve.lowBiased(0, NpcValidation.MAX_ROUTE_WAIT_SECONDS), 0,
                value -> Math.round(value) + "s", value -> updatePoint(point -> point.withWaitSeconds(Math.round(value)))));
        lookButton = addRenderableWidget(CycleButton.booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, false)
                .withTooltip(value -> Tooltip.create(Component.translatable("gui.luna_npc.route.look.tooltip")))
                .create(right, TOP + 24, RIGHT_WIDTH, 20, Component.translatable("gui.luna_npc.route.look"),
                        (button, value) -> updatePoint(point -> point.withLook(value, point.lookYaw()))));
        yawSlider = addRenderableWidget(new LabeledSlider(right, TOP + 48, RIGHT_WIDTH - 44, 20,
                Component.translatable("gui.luna_npc.route.look_yaw"), -180, 180, 0,
                value -> Math.round(value) + "°", value -> updatePoint(point -> point.withLook(point.look(), value))));
        faceMeButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.route.face_me"),
                button -> faceMyDirection()).bounds(right + RIGHT_WIDTH - 42, TOP + 48, 42, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.luna_npc.route.face_me.tooltip"))).build());
        runButton = addRenderableWidget(CycleButton.booleanBuilder(
                        Component.translatable("gui.luna_npc.route.speed.run"),
                        Component.translatable("gui.luna_npc.route.speed.walk"), false)
                .withTooltip(value -> Tooltip.create(Component.translatable(speed == NpcRouteSpeed.PER_POINT
                        ? "gui.luna_npc.route.leg_speed.tooltip" : "gui.luna_npc.route.leg_speed.locked")))
                .create(right, TOP + 72, RIGHT_WIDTH, 20, Component.translatable("gui.luna_npc.route.leg_speed"),
                        (button, value) -> updatePoint(point -> point.withRun(value))));

        addPointsButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.route.add_points"),
                button -> addPointsInWorld()).bounds(right, listBottom() - 20, RIGHT_WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.luna_npc.route.add_points.tooltip"))).build());

        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> done())
                .bounds(this.width / 2 - 102, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> backToManager(draft))
                .bounds(this.width / 2 + 2, this.height - 28, 100, 20).build());
        rebuild();
    }

    private void updatePoint(java.util.function.UnaryOperator<NpcRoutePoint> change) {
        if (selected >= 0 && selected < points.size()) {
            points.set(selected, change.apply(points.get(selected)));
            rebuildList();
            refreshButtons();
        }
    }

    private void faceMyDirection() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            float yaw = Mth.wrapDegrees(player.getYRot());
            updatePoint(point -> point.withLook(true, yaw));
            refreshPointControls();
        }
    }

    private void move(int delta) {
        int target = selected + delta;
        if (selected < 0 || target < 0 || target >= points.size()) {
            return;
        }
        NpcRoutePoint moved = points.remove(selected);
        points.add(target, moved);
        selected = target;
        rebuild();
    }

    private void removePoint() {
        if (selected < 0 || selected >= points.size() || points.size() <= NpcValidation.MIN_ROUTE_POINTS) {
            return;
        }
        points.remove(selected);
        selected = Math.min(selected, points.size() - 1);
        rebuild();
    }

    private NpcRoute edited() {
        return draft.get(routeIndex).withPoints(points);
    }

    // The first point that breaks a route rule against the points before it, or -1 when all are fine.
    private int firstBrokenPoint() {
        List<BlockPos> placed = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            BlockPos pos = points.get(index).pos();
            if (NpcRoute.placementProblem(placed, pos, NpcValidation.MAX_ROUTE_POINTS_GUI) != NpcRoute.PlacementProblem.NONE) {
                return index;
            }
            placed.add(pos);
        }
        return -1;
    }

    private List<NpcRoute> draftWithEdit() {
        List<NpcRoute> updated = new ArrayList<>(draft);
        updated.set(routeIndex, edited());
        return updated;
    }

    private void done() {
        if (firstBrokenPoint() < 0) {
            backToManager(draftWithEdit());
        }
    }

    private void addPointsInWorld() {
        if (firstBrokenPoint() >= 0 || points.size() >= NpcValidation.MAX_ROUTE_POINTS_GUI) {
            return;
        }
        List<NpcRoute> updated = draftWithEdit();
        ClientPlayNetworking.send(new UpdateRoutesPayload(updated));
        ClientRouteRender.startExtending(edited().id(), NpcRoute.positionsOf(points));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.luna_npc.route_extending"));
        }
        Minecraft.getInstance().setScreenAndShow(null);
    }

    private void backToManager(List<NpcRoute> routes) {
        Minecraft.getInstance().setScreenAndShow(new ManageRoutesScreen(routes, draft.get(routeIndex).id()));
    }

    private void rebuild() {
        rebuildList();
        refreshPointControls();
        refreshButtons();
    }

    private void rebuildList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selectedEntry = null;
        int broken = firstBrokenPoint();
        for (int index = 0; index < points.size(); index++) {
            NpcRoutePoint point = points.get(index);
            Component label = Component.literal((index == 0 ? "⚓ " : "#" + (index + 1) + " ") + point.pos().toShortString())
                    .withStyle(index == broken ? ChatFormatting.RED : ChatFormatting.WHITE);
            if (point.waitSeconds() > 0) {
                label = label.copy().append(Component.literal("  " + point.waitSeconds() + "s").withStyle(ChatFormatting.GRAY));
            }
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(String.valueOf(index), label, id -> {
                selected = Integer.parseInt(id);
                refreshPointControls();
                refreshButtons();
            });
            entries.add(entry);
            if (index == selected) {
                selectedEntry = entry;
            }
        }
        pointList.fill(entries, selectedEntry);
    }

    private void refreshPointControls() {
        boolean has = selected >= 0 && selected < points.size();
        waitSlider.visible = has;
        lookButton.visible = has;
        yawSlider.visible = has;
        faceMeButton.visible = has;
        runButton.visible = has;
        if (!has) {
            return;
        }
        NpcRoutePoint point = points.get(selected);
        waitSlider.setValue(point.waitSeconds());
        lookButton.setValue(point.look());
        yawSlider.setValue(point.lookYaw());
        runButton.setValue(point.run());
    }

    private void refreshButtons() {
        boolean has = selected >= 0 && selected < points.size();
        boolean valid = firstBrokenPoint() < 0;
        upButton.active = has && selected > 0;
        downButton.active = has && selected < points.size() - 1;
        removeButton.active = has && points.size() > NpcValidation.MIN_ROUTE_POINTS;
        yawSlider.active = has && points.get(selected).look();
        runButton.active = speed == NpcRouteSpeed.PER_POINT;
        doneButton.active = valid;
        addPointsButton.active = valid && points.size() < NpcValidation.MAX_ROUTE_POINTS_GUI;
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (pointList == null) {
            return;
        }
        panel(graphics, leftX() - 5, TOP - 5, LEFT_WIDTH + 10, listBottom() - TOP + 10);
        graphics.outline(pointList.getX() - 1, pointList.getY() - 1,
                pointList.getWidth() + 2, pointList.getHeight() + 2, PANEL_BORDER);
        panel(graphics, rightX() - 6, TOP - 5, RIGHT_WIDTH + 12, listBottom() - TOP + 10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        graphics.text(this.font, ManageRoutesScreen.speedLabel(speed), rightX(), TOP + 100, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.luna_npc.route.point_count_of", points.size(),
                NpcValidation.MAX_ROUTE_POINTS_GUI), rightX(), TOP + 112, LABEL_COLOR);
        int broken = firstBrokenPoint();
        if (broken >= 0) {
            List<BlockPos> before = NpcRoute.positionsOf(points.subList(0, broken));
            NpcRoute.PlacementProblem problem = NpcRoute.placementProblem(before, points.get(broken).pos(),
                    NpcValidation.MAX_ROUTE_POINTS_GUI);
            graphics.textWithWordWrap(this.font, Component.translatable("gui.luna_npc.route.broken", broken + 1)
                            .append(" ").append(problemText(problem)),
                    rightX(), TOP + 128, RIGHT_WIDTH, WARNING_COLOR);
        }
    }

    public static Component problemText(NpcRoute.PlacementProblem problem) {
        return switch (problem) {
            case NONE -> Component.empty();
            case FULL -> Component.translatable("message.luna_npc.route_full", NpcValidation.MAX_ROUTE_POINTS_GUI);
            case TOO_CLOSE -> Component.translatable("message.luna_npc.route_too_close");
            case TOO_FAR_FROM_PREVIOUS -> Component.translatable("message.luna_npc.route_too_far",
                    (int) NpcValidation.MAX_ROUTE_POINT_SPACING);
            case TOO_FAR_FROM_ANCHOR -> Component.translatable("message.luna_npc.route_too_far_anchor",
                    (int) NpcValidation.MAX_ROUTE_ANCHOR_DISTANCE);
        };
    }

    @Override
    public void onClose() {
        backToManager(draft);
    }
}
