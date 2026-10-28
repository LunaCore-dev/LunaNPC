package net.lunacore.luna_npc.client.widget;

import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcAccuracyAnchor;
import net.lunacore.luna_npc.api.NpcProjectileSettings;
import net.lunacore.luna_npc.npc.NpcValidation;

// The inline accuracy curve editor: a slider track on top holds one handle per anchor, the
// graph under it plots the resulting accuracy curve over distance. Clicking the graph creates
// an anchor at that distance/percent, clicking a handle or curve point selects it, dragging
// moves it (track handles slide the distance, graph points move freely), and the screen removes
// the selected anchor through removeSelected(). Operates directly on the combat screen's list.
// It lives inside a scrolling panel, so it clips its drawing (and ignores clicks) to the panel
// window the screen hands it, and greys out fully when dynamic accuracy is switched off.
public class AccuracyCurveWidget extends AbstractWidget {

    private static final int TRACK_HEIGHT = 10;
    private static final int TRACK_GAP = 2;
    private static final int HANDLE_WIDTH = 6;
    private static final int POINT_HALF = 2;
    private static final int PICK_RADIUS = 5;
    private static final int MAX_DISTANCE = NpcValidation.MAX_ACCURACY_DISTANCE;

    private static final int TRACK_COLOR = 0xFF2B2B2B;
    private static final int GRAPH_BACKGROUND = 0xFF15151B;
    private static final int GRAPH_BORDER = 0xFF5A5A68;
    private static final int GRID_COLOR = 0xFF2A2A34;
    private static final int CURVE_COLOR = 0xFFE0C060;
    private static final int POINT_OUTLINE = 0xFF000000;
    private static final int SELECTED_OUTLINE = 0xFFFFFFFF;
    // The disabled palette: the same shapes drawn in muted greys so the graph reads as inert.
    private static final int DISABLED_CURVE = 0xFF6A6A72;
    private static final int DISABLED_BORDER = 0xFF3A3A42;

    private final List<NpcAccuracyAnchor> anchors;
    private final Runnable onChanged;
    private int selectedDistance = -1;
    private boolean draggingFromTrack;
    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;

    public AccuracyCurveWidget(int x, int y, int width, int height,
                               List<NpcAccuracyAnchor> anchors, Runnable onChanged) {
        super(x, y, width, height, Component.empty());
        this.anchors = anchors;
        this.onChanged = onChanged;
    }

    // The vertical window (screen coordinates) the widget may draw in and accept clicks in; the
    // rest is scrolled under the panel edge.
    public void setClipBounds(int top, int bottom) {
        this.clipTop = top;
        this.clipBottom = bottom;
    }

    public NpcAccuracyAnchor selectedAnchor() {
        for (NpcAccuracyAnchor anchor : anchors) {
            if (anchor.distanceBlocks() == selectedDistance) {
                return anchor;
            }
        }
        return null;
    }

    public void removeSelected() {
        anchors.removeIf(anchor -> anchor.distanceBlocks() == selectedDistance);
        selectedDistance = -1;
        onChanged.run();
    }

    private int plotLeft() {
        return getX() + HANDLE_WIDTH / 2;
    }

    private int plotWidth() {
        return getWidth() - HANDLE_WIDTH;
    }

    private int graphTop() {
        return getY() + TRACK_HEIGHT + TRACK_GAP;
    }

    private int graphHeight() {
        return getHeight() - TRACK_HEIGHT - TRACK_GAP;
    }

    private int distanceToX(float distance) {
        return plotLeft() + Math.round(distance / MAX_DISTANCE * plotWidth());
    }

    private int percentToY(float percent) {
        return graphTop() + Math.round((100.0F - percent) / 100.0F * (graphHeight() - 1));
    }

    private int xToDistance(double mouseX) {
        return Math.clamp(Math.round((float) (mouseX - plotLeft()) / plotWidth() * MAX_DISTANCE),
                0, MAX_DISTANCE);
    }

    private int yToPercent(double mouseY) {
        return Math.clamp(100 - Math.round((float) (mouseY - graphTop())
                / (graphHeight() - 1) * 100.0F), 0, 100);
    }

    // Clicks landing on the scrolled-away part of the widget belong to whatever is under the
    // panel edge, not to the graph.
    private boolean withinClip(double mouseY) {
        return mouseY >= clipTop && mouseY <= clipBottom;
    }

    // The widget rect can extend past the panel while scrolled; only the clipped-in part is
    // really the graph, so anything outside it (grayed-out included) is transparent to the mouse
    // and passes through to the controls under the panel edge.
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return active && visible && withinClip(mouseY) && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !withinClip(event.y())) {
            return false;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !withinClip(event.y())) {
            return;
        }
        NpcAccuracyAnchor picked = pickAnchor(event.x(), event.y());
        if (picked != null) {
            selectedDistance = picked.distanceBlocks();
            draggingFromTrack = event.y() < graphTop();
            onChanged.run();
            return;
        }
        if (event.y() < graphTop()) {
            return;
        }
        int distance = xToDistance(event.x());
        // A click on an occupied distance selects that anchor instead of stacking a second one.
        if (anchorAtDistance(distance) == null) {
            anchors.add(new NpcAccuracyAnchor(distance, yToPercent(event.y())));
            anchors.sort(Comparator.comparingInt(NpcAccuracyAnchor::distanceBlocks));
        }
        selectedDistance = distance;
        draggingFromTrack = false;
        onChanged.run();
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        if (!active) {
            return;
        }
        NpcAccuracyAnchor selected = selectedAnchor();
        if (selected == null) {
            return;
        }
        int distance = xToDistance(event.x());
        // Sliding onto a distance another anchor owns is blocked, never a silent replacement.
        NpcAccuracyAnchor occupant = anchorAtDistance(distance);
        if (occupant != null && occupant != selected) {
            distance = selected.distanceBlocks();
        }
        int percent = draggingFromTrack ? selected.percent()
                : Math.clamp(yToPercent(Math.clamp(event.y(), clipTop, clipBottom)), 0, 100);
        anchors.remove(selected);
        anchors.add(new NpcAccuracyAnchor(distance, percent));
        anchors.sort(Comparator.comparingInt(NpcAccuracyAnchor::distanceBlocks));
        selectedDistance = distance;
        onChanged.run();
    }

    private NpcAccuracyAnchor anchorAtDistance(int distance) {
        for (NpcAccuracyAnchor anchor : anchors) {
            if (anchor.distanceBlocks() == distance) {
                return anchor;
            }
        }
        return null;
    }

    // The nearest anchor within picking range of the mouse: track hits compare x only, graph
    // hits compare against the anchor's curve point.
    private NpcAccuracyAnchor pickAnchor(double mouseX, double mouseY) {
        NpcAccuracyAnchor best = null;
        double bestDistance = PICK_RADIUS + 1;
        for (NpcAccuracyAnchor anchor : anchors) {
            int anchorX = distanceToX(anchor.distanceBlocks());
            double distance = mouseY < graphTop()
                    ? Math.abs(mouseX - anchorX)
                    : Math.max(Math.abs(mouseX - anchorX), Math.abs(mouseY - percentToY(anchor.percent())));
            if (distance <= PICK_RADIUS && distance < bestDistance) {
                best = anchor;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        graphics.enableScissor(getX(), Math.max(clipTop, getY()),
                getX() + getWidth(), Math.min(clipBottom, getY() + getHeight()));
        extractTrack(graphics);
        extractGraph(graphics);
        graphics.disableScissor();
    }

    private void extractTrack(GuiGraphicsExtractor graphics) {
        int curveColor = active ? CURVE_COLOR : DISABLED_CURVE;
        int trackTop = getY() + TRACK_HEIGHT / 2 - 2;
        graphics.fill(getX(), trackTop, getX() + getWidth(), trackTop + 4, TRACK_COLOR);
        for (NpcAccuracyAnchor anchor : anchors) {
            int left = distanceToX(anchor.distanceBlocks()) - HANDLE_WIDTH / 2;
            graphics.fill(left, getY(), left + HANDLE_WIDTH, getY() + TRACK_HEIGHT, curveColor);
            graphics.outline(left, getY(), HANDLE_WIDTH, TRACK_HEIGHT,
                    active && anchor.distanceBlocks() == selectedDistance ? SELECTED_OUTLINE : POINT_OUTLINE);
        }
    }

    private void extractGraph(GuiGraphicsExtractor graphics) {
        int curveColor = active ? CURVE_COLOR : DISABLED_CURVE;
        int borderColor = active ? GRAPH_BORDER : DISABLED_BORDER;
        int top = graphTop();
        int bottom = top + graphHeight();
        graphics.fill(getX(), top, getX() + getWidth(), bottom, GRAPH_BACKGROUND);
        graphics.outline(getX(), top, getWidth(), graphHeight(), borderColor);
        for (int percent = 25; percent <= 75; percent += 25) {
            int y = percentToY(percent);
            graphics.fill(getX() + 1, y, getX() + getWidth() - 1, y + 1, GRID_COLOR);
        }
        // The curve is drawn column by column, each column connected to the previous one so
        // steep segments stay a continuous line instead of scattered dots.
        int previousY = percentToY(NpcProjectileSettings.curveAccuracyAt(anchors, 0.0F));
        for (int column = 0; column <= plotWidth(); column++) {
            float distance = (float) column / plotWidth() * MAX_DISTANCE;
            int y = percentToY(NpcProjectileSettings.curveAccuracyAt(anchors, distance));
            graphics.fill(plotLeft() + column, Math.min(y, previousY),
                    plotLeft() + column + 1, Math.max(y, previousY) + 1, curveColor);
            previousY = y;
        }
        for (NpcAccuracyAnchor anchor : anchors) {
            int x = distanceToX(anchor.distanceBlocks());
            int y = percentToY(anchor.percent());
            boolean selected = active && anchor.distanceBlocks() == selectedDistance;
            int half = selected ? POINT_HALF + 1 : POINT_HALF;
            graphics.fill(x - half, y - half, x + half + 1, y + half + 1, curveColor);
            graphics.outline(x - half, y - half, 2 * half + 1, 2 * half + 1,
                    selected ? SELECTED_OUTLINE : POINT_OUTLINE);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
        // Placing and dragging anchors clicks constantly; the editor stays quiet.
    }
}
