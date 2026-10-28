package net.lunacore.luna_npc.client.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcDialogChoice;
import net.lunacore.luna_npc.api.NpcDialogNode;

// The dialog node-graph canvas (the CLAUDE.md UI rules), a compact DaVinci-Resolve-style editor: each node is a
// small box showing its title, a choice-count and an action-count, with one input port on the left and
// one output port per choice on the right (plus a "+" port that spawns a choice). Press-and-hold an
// output port and release on another box to wire that choice's target — routing is fully visual. Boxes
// drag to lay out, empty canvas pans, the wheel zooms. Only nodes intersecting the view are drawn, so a
// hundred-node tree stays cheap. The full text of the selected node lives in the host screen's dock.
public class DialogGraphWidget extends AbstractWidget {

    public static final int BOX_WIDTH = 84;
    private static final int HEADER_H = 12;
    private static final int ROW_H = 8;
    private static final int BOTTOM_PAD = 4;
    private static final float MIN_ZOOM = 0.35F;
    private static final float MAX_ZOOM = 2.0F;
    private static final float DEFAULT_ZOOM = 0.85F;
    private static final int GRID_STEP = 48;
    private static final int CLICK_SLOP = 4;
    private static final double PORT_HIT = 6.0;
    private static final String DEFAULT_CHOICE_TEXT = "Choice";

    private static final int CANVAS_BG = 0xFF15151B;
    private static final int GRID_COLOR = 0xFF20202A;
    private static final int BOX_FILL = 0xFF20202A;
    private static final int TERMINAL_FILL = 0xFF191922;
    private static final int BOX_HEADER = 0xFF2E2E3A;
    private static final int BOX_BORDER = 0xFF54545F;
    private static final int START_WASH = 0x30E0C060;
    private static final int LINE_COLOR = 0xFF6A6A72;
    private static final int GOLD = 0xFFE0C060;
    private static final int PORT_FILL = 0xFF7A7A86;
    private static final int PORT_MUTED = 0xFF3A3A42;
    private static final int TEXT_COLOR = 0xFFEDEDF2;
    private static final int MUTED_COLOR = 0xFF9A9AA6;

    public interface NodeCreator {
        String create(int worldX, int worldY);
    }

    private record OutPort(String nodeId, int choiceIndex) {
    }

    private final List<NpcDialogNode> nodes;
    private final Supplier<String> startNodeId;
    private final Runnable onSelectionChanged;
    private final Runnable onGraphChanged;
    private final NodeCreator nodeCreator;

    private double viewX;
    private double viewY;
    private float zoom = DEFAULT_ZOOM;
    private int bottomInset;

    private String selectedId;
    private String pressedId;
    private boolean panning;
    private double grabOffsetX;
    private double grabOffsetY;
    private double dragAccumulated;

    private boolean wiring;
    private String wireNodeId;
    private int wireChoice;

    // Rebuilt once per frame so connector target lookups are O(1) instead of scanning the node list.
    private final java.util.HashMap<String, NpcDialogNode> frameIndex = new java.util.HashMap<>();

    public DialogGraphWidget(int x, int y, int width, int height, List<NpcDialogNode> nodes,
                             Supplier<String> startNodeId, Runnable onSelectionChanged,
                             Runnable onGraphChanged, NodeCreator nodeCreator) {
        super(x, y, width, height, Component.empty());
        this.nodes = nodes;
        this.startNodeId = startNodeId;
        this.onSelectionChanged = onSelectionChanged;
        this.onGraphChanged = onGraphChanged;
        this.nodeCreator = nodeCreator;
    }

    public NpcDialogNode selectedNode() {
        return findNode(selectedId);
    }

    public String selectedNodeId() {
        return selectedId;
    }

    public double viewX() {
        return viewX;
    }

    public double viewY() {
        return viewY;
    }

    public float zoom() {
        return zoom;
    }

    public void setView(double x, double y, float z) {
        this.viewX = x;
        this.viewY = y;
        this.zoom = z;
    }

    public void setBottomInset(int inset) {
        this.bottomInset = Math.max(0, inset);
    }

    public void setSelection(String nodeId) {
        this.selectedId = nodeId;
    }

    public void deselect() {
        selectedId = null;
        onSelectionChanged.run();
    }

    private int effectiveBottom() {
        return getY() + getHeight() - bottomInset;
    }

    public int newNodeX() {
        return (int) Math.round(viewX + (getWidth() / zoom - BOX_WIDTH) / 2.0);
    }

    public int newNodeY() {
        return (int) Math.round(viewY + ((effectiveBottom() - getY()) / zoom - HEADER_H) / 2.0);
    }

    public void selectAndFocus(String nodeId) {
        selectedId = nodeId;
        NpcDialogNode node = findNode(nodeId);
        if (node != null) {
            viewX = node.graphX() + BOX_WIDTH / 2.0 - getWidth() / zoom / 2.0;
            viewY = node.graphY() + boxHeight(node) / 2.0 - (effectiveBottom() - getY()) / zoom / 2.0;
        }
        onSelectionChanged.run();
    }

    private NpcDialogNode findNode(String id) {
        if (id == null) {
            return null;
        }
        for (NpcDialogNode node : nodes) {
            if (node.nodeId().equals(id)) {
                return node;
            }
        }
        return null;
    }

    private int indexOf(String id) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).nodeId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static int boxHeight(NpcDialogNode node) {
        return HEADER_H + (node.choices().size() + 1) * ROW_H + BOTTOM_PAD;
    }

    private static double outPortWorldY(NpcDialogNode node, int index) {
        return node.graphY() + HEADER_H + index * ROW_H + ROW_H / 2.0;
    }

    private int screenX(double worldX) {
        return getX() + (int) Math.round((worldX - viewX) * zoom);
    }

    private int screenY(double worldY) {
        return getY() + (int) Math.round((worldY - viewY) * zoom);
    }

    private double worldX(double sx) {
        return viewX + (sx - getX()) / zoom;
    }

    private double worldY(double sy) {
        return viewY + (sy - getY()) / zoom;
    }

    // A node whose scaled box overlaps the visible canvas; everything else is skipped when drawing.
    private boolean onScreen(NpcDialogNode node) {
        int x = screenX(node.graphX());
        int y = screenY(node.graphY());
        int w = Math.round(BOX_WIDTH * zoom);
        int h = Math.round(boxHeight(node) * zoom);
        return x + w >= getX() && x <= getX() + getWidth() && y + h >= getY() && y <= effectiveBottom();
    }

    private NpcDialogNode nodeAt(double sx, double sy) {
        double wx = worldX(sx);
        double wy = worldY(sy);
        for (int i = nodes.size() - 1; i >= 0; i--) {
            NpcDialogNode node = nodes.get(i);
            if (wx >= node.graphX() && wx <= node.graphX() + BOX_WIDTH
                    && wy >= node.graphY() && wy <= node.graphY() + boxHeight(node)) {
                return node;
            }
        }
        return null;
    }

    private OutPort outPortAt(double sx, double sy) {
        double wx = worldX(sx);
        double wy = worldY(sy);
        for (int i = nodes.size() - 1; i >= 0; i--) {
            NpcDialogNode node = nodes.get(i);
            double px = node.graphX() + BOX_WIDTH;
            if (Math.abs(wx - px) > PORT_HIT) {
                continue;
            }
            for (int c = 0; c < node.choices().size(); c++) {
                if (Math.abs(wy - outPortWorldY(node, c)) <= PORT_HIT) {
                    return new OutPort(node.nodeId(), c);
                }
            }
            if (Math.abs(wy - outPortWorldY(node, node.choices().size())) <= PORT_HIT) {
                return new OutPort(node.nodeId(), -1);
            }
        }
        return null;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < effectiveBottom();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        dragAccumulated = 0;
        OutPort port = outPortAt(event.x(), event.y());
        if (port != null) {
            wiring = true;
            wireNodeId = port.nodeId();
            wireChoice = port.choiceIndex();
            pressedId = null;
            panning = false;
            return;
        }
        NpcDialogNode hit = nodeAt(event.x(), event.y());
        if (hit != null) {
            pressedId = hit.nodeId();
            panning = false;
            grabOffsetX = worldX(event.x()) - hit.graphX();
            grabOffsetY = worldY(event.y()) - hit.graphY();
            if (!hit.nodeId().equals(selectedId)) {
                selectedId = hit.nodeId();
                onSelectionChanged.run();
            }
        } else {
            pressedId = null;
            panning = true;
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        dragAccumulated += Math.abs(dragX) + Math.abs(dragY);
        if (wiring) {
            return;
        }
        if (panning) {
            viewX -= dragX / zoom;
            viewY -= dragY / zoom;
            return;
        }
        int index = indexOf(pressedId);
        if (index < 0) {
            return;
        }
        NpcDialogNode node = nodes.get(index);
        int nx = (int) Math.round(worldX(event.x()) - grabOffsetX);
        int ny = (int) Math.round(worldY(event.y()) - grabOffsetY);
        nodes.set(index, moved(node, nx, ny));
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        if (wiring) {
            resolveWire(event.x(), event.y());
            wiring = false;
            wireNodeId = null;
            return;
        }
        if (dragAccumulated < CLICK_SLOP && pressedId == null && panning) {
            deselect();
        }
        pressedId = null;
        panning = false;
    }

    private void resolveWire(double sx, double sy) {
        NpcDialogNode target = nodeAt(sx, sy);
        String targetId;
        boolean createdNew = false;
        if (target != null) {
            targetId = target.nodeId();
        } else {
            int wx = (int) Math.round(worldX(sx));
            int wy = (int) Math.round(worldY(sy) - HEADER_H / 2.0);
            targetId = nodeCreator.create(wx, wy);
            createdNew = true;
        }
        if (targetId == null || targetId.isBlank()) {
            return;
        }
        applyWire(wireNodeId, wireChoice, targetId);
        onGraphChanged.run();
        if (createdNew) {
            selectedId = targetId;
            onSelectionChanged.run();
        }
    }

    private void applyWire(String nodeId, int choiceIndex, String targetId) {
        int index = indexOf(nodeId);
        if (index < 0) {
            return;
        }
        NpcDialogNode node = nodes.get(index);
        List<NpcDialogChoice> choices = new ArrayList<>(node.choices());
        if (choiceIndex < 0) {
            choices.add(new NpcDialogChoice(DEFAULT_CHOICE_TEXT, targetId, List.of(), true));
        } else if (choiceIndex < choices.size()) {
            NpcDialogChoice c = choices.get(choiceIndex);
            choices.set(choiceIndex, new NpcDialogChoice(c.text(), targetId, c.conditions(), c.hideWhenLocked()));
        }
        nodes.set(index, withChoices(node, choices));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        double anchorWorldX = worldX(mouseX);
        double anchorWorldY = worldY(mouseY);
        zoom = (float) Math.clamp(zoom * (scrollY > 0 ? 1.1 : 1 / 1.1), MIN_ZOOM, MAX_ZOOM);
        viewX = anchorWorldX - (mouseX - getX()) / zoom;
        viewY = anchorWorldY - (mouseY - getY()) / zoom;
        return true;
    }

    private static NpcDialogNode moved(NpcDialogNode node, int x, int y) {
        return new NpcDialogNode(node.nodeId(), node.speakerLine(), node.choices(), node.actions(),
                node.interactSound(), node.triggerSound(), x, y);
    }

    private static NpcDialogNode withChoices(NpcDialogNode node, List<NpcDialogChoice> choices) {
        return new NpcDialogNode(node.nodeId(), node.speakerLine(), choices, node.actions(),
                node.interactSound(), node.triggerSound(), node.graphX(), node.graphY());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        int bottom = effectiveBottom();
        frameIndex.clear();
        for (NpcDialogNode node : nodes) {
            frameIndex.put(node.nodeId(), node);
        }
        graphics.enableScissor(getX(), getY(), getX() + getWidth(), bottom);
        graphics.fill(getX(), getY(), getX() + getWidth(), bottom, CANVAS_BG);
        extractGrid(graphics, bottom);
        extractConnectors(graphics);
        extractBoxes(graphics);
        extractPorts(graphics, mouseX, mouseY);
        if (wiring) {
            extractLiveWire(graphics, mouseX, mouseY);
        }
        graphics.disableScissor();
        graphics.outline(getX(), getY(), getWidth(), bottom - getY(), BOX_BORDER);
    }

    private void extractGrid(GuiGraphicsExtractor graphics, int bottom) {
        int step = Math.round(GRID_STEP * zoom);
        if (step < 8) {
            return;
        }
        int firstX = getX() - Math.floorMod(screenX(0), step);
        for (int x = firstX; x <= getX() + getWidth(); x += step) {
            graphics.fill(x, getY(), x + 1, bottom, GRID_COLOR);
        }
        int firstY = getY() - Math.floorMod(screenY(0), step);
        for (int y = firstY; y <= bottom; y += step) {
            graphics.fill(getX(), y, getX() + getWidth(), y + 1, GRID_COLOR);
        }
    }

    private void extractConnectors(GuiGraphicsExtractor graphics) {
        for (NpcDialogNode node : nodes) {
            boolean sourceVisible = onScreen(node);
            int outX = screenX(node.graphX() + BOX_WIDTH);
            int color = node.nodeId().equals(selectedId) ? GOLD : LINE_COLOR;
            for (int c = 0; c < node.choices().size(); c++) {
                NpcDialogChoice choice = node.choices().get(c);
                if (choice.targetNodeId().isBlank()) {
                    if (sourceVisible) {
                        int outY = screenY(outPortWorldY(node, c));
                        hLine(graphics, outX, outX + Math.round(12 * zoom), outY, PORT_MUTED);
                    }
                    continue;
                }
                NpcDialogNode target = frameIndex.get(choice.targetNodeId());
                if (target == null || (!sourceVisible && !onScreen(target))) {
                    continue;
                }
                int outY = screenY(outPortWorldY(node, c));
                int inX = screenX(target.graphX());
                int inY = screenY(target.graphY() + boxHeight(target) / 2.0);
                bezier(graphics, outX, outY, inX, inY, color);
                arrowInto(graphics, inX, inY, color);
            }
        }
    }

    // A smooth cubic with horizontal tangents. Each span between samples is drawn as ONE rotated quad
    // (not a per-pixel fill), and the sample count scales with the on-screen length, so a dense graph of
    // connectors costs a few dozen quads each instead of hundreds of per-pixel fills.
    private void bezier(GuiGraphicsExtractor graphics, int x0, int y0, int x3, int y3, int color) {
        int pull = Math.max(18, Math.abs(x3 - x0) / 2);
        double x1 = x0 + pull;
        double x2 = x3 - pull;
        int steps = Math.clamp((Math.abs(x3 - x0) + Math.abs(y3 - y0)) / 8, 8, 40);
        int thickness = zoom >= 0.9F ? 2 : 1;
        double px = x0;
        double py = y0;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double mt = 1 - t;
            double bx = mt * mt * mt * x0 + 3 * mt * mt * t * x1 + 3 * mt * t * t * x2 + t * t * t * x3;
            double by = mt * mt * mt * y0 + 3 * mt * mt * t * y0 + 3 * mt * t * t * y3 + t * t * t * y3;
            segment(graphics, px, py, bx, by, thickness, color);
            px = bx;
            py = by;
        }
    }

    // One line span as a single rotated quad through the pose stack — a constant number of fills
    // regardless of length, versus the old per-pixel line which cost one fill per covered pixel.
    private static void segment(GuiGraphicsExtractor graphics, double x0, double y0, double x1, double y1,
                                int thickness, int color) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5) {
            return;
        }
        org.joml.Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((float) x0, (float) y0);
        pose.rotate((float) Math.atan2(dy, dx));
        graphics.fill(0, 0, (int) Math.round(len) + 1, thickness, color);
        pose.popMatrix();
    }

    private static void arrowInto(GuiGraphicsExtractor graphics, int x, int y, int color) {
        segment(graphics, x - 4, y - 3, x, y, 1, color);
        segment(graphics, x - 4, y + 3, x, y, 1, color);
    }

    private void extractLiveWire(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        NpcDialogNode node = findNode(wireNodeId);
        if (node == null) {
            return;
        }
        int outX = screenX(node.graphX() + BOX_WIDTH);
        int outY = screenY(outPortWorldY(node, wireChoice < 0 ? node.choices().size() : wireChoice));
        bezier(graphics, outX, outY, mouseX, mouseY, GOLD);
    }

    private void extractBoxes(GuiGraphicsExtractor graphics) {
        Font font = font();
        String start = startNodeId.get();
        for (NpcDialogNode node : nodes) {
            if (!onScreen(node)) {
                continue;
            }
            int x = screenX(node.graphX());
            int y = screenY(node.graphY());
            int w = Math.round(BOX_WIDTH * zoom);
            int h = Math.round(boxHeight(node) * zoom);
            boolean selected = node.nodeId().equals(selectedId);
            boolean isStart = node.nodeId().equals(start);
            boolean terminal = node.choices().isEmpty();
            graphics.fill(x, y, x + w, y + h, terminal ? TERMINAL_FILL : BOX_FILL);
            graphics.fill(x + 1, y + 1, x + w - 1, y + Math.max(3, Math.round(HEADER_H * zoom)), BOX_HEADER);
            if (isStart) {
                graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, START_WASH);
            }
            graphics.outline(x, y, w, h, selected ? GOLD : BOX_BORDER);
            extractBoxText(graphics, font, node, x, y, isStart, terminal);
        }
    }

    // The header glyphs are laid out in the box's unscaled coordinates and drawn under a pose scaled by the
    // zoom, so they track the box exactly at every zoom instead of being drawn at a fixed size that spills
    // out when the box is small and vanishes when it is smaller still.
    private void extractBoxText(GuiGraphicsExtractor graphics, Font font, NpcDialogNode node, int x, int y,
                                boolean isStart, boolean terminal) {
        org.joml.Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(zoom, zoom);
        if (isStart) {
            graphics.text(font, Component.literal("★"), 3, 3, GOLD);
        }
        String main = terminal ? "■" : "→" + node.choices().size();
        graphics.text(font, Component.literal(main), BOX_WIDTH / 2 - font.width(main) / 2, 3,
                terminal ? MUTED_COLOR : TEXT_COLOR);
        if (!node.actions().isEmpty()) {
            String acts = "*" + node.actions().size();
            graphics.text(font, Component.literal(acts), BOX_WIDTH - 3 - font.width(acts), 3, GOLD);
        }
        pose.popMatrix();
    }

    private void extractPorts(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int half = Math.max(2, Math.round(2.4F * zoom));
        NpcDialogNode dropTarget = wiring ? nodeAt(mouseX, mouseY) : null;
        for (NpcDialogNode node : nodes) {
            if (!onScreen(node)) {
                continue;
            }
            int inX = screenX(node.graphX());
            int inY = screenY(node.graphY() + boxHeight(node) / 2.0);
            boolean inputLit = dropTarget != null && dropTarget.nodeId().equals(node.nodeId());
            port(graphics, inX, inY, half, inputLit ? GOLD : PORT_FILL);
            int outX = screenX(node.graphX() + BOX_WIDTH);
            for (int c = 0; c < node.choices().size(); c++) {
                int outY = screenY(outPortWorldY(node, c));
                boolean hover = portHover(mouseX, mouseY, node, c);
                boolean blank = node.choices().get(c).targetNodeId().isBlank();
                port(graphics, outX, outY, half, hover ? GOLD : (blank ? PORT_MUTED : PORT_FILL));
            }
            int addY = screenY(outPortWorldY(node, node.choices().size()));
            boolean addHover = portHover(mouseX, mouseY, node, node.choices().size());
            plusPort(graphics, outX, addY, half, addHover ? GOLD : PORT_MUTED);
        }
    }

    private boolean portHover(int mouseX, int mouseY, NpcDialogNode node, int index) {
        return !wiring && Math.abs(worldX(mouseX) - (node.graphX() + BOX_WIDTH)) <= PORT_HIT
                && Math.abs(worldY(mouseY) - outPortWorldY(node, index)) <= PORT_HIT;
    }

    private static void port(GuiGraphicsExtractor graphics, int cx, int cy, int half, int color) {
        graphics.fill(cx - half, cy - half, cx + half + 1, cy + half + 1, color);
        graphics.outline(cx - half, cy - half, 2 * half + 1, 2 * half + 1, CANVAS_BG);
    }

    private static void plusPort(GuiGraphicsExtractor graphics, int cx, int cy, int half, int color) {
        port(graphics, cx, cy, half, color);
        graphics.fill(cx - half + 1, cy, cx + half, cy + 1, CANVAS_BG);
        graphics.fill(cx, cy - half + 1, cx + 1, cy + half, CANVAS_BG);
    }

    private static void hLine(GuiGraphicsExtractor graphics, int xa, int xb, int y, int color) {
        graphics.fill(Math.min(xa, xb), y, Math.max(xa, xb) + 1, y + 1, color);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
    }
}
