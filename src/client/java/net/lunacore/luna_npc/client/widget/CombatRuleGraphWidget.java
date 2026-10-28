package net.lunacore.luna_npc.client.widget;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcCombatRule;
import net.lunacore.luna_npc.api.NpcCombatTriggerType;

// The advanced-combat rule canvas (the CLAUDE.md UI rules): a pannable/zoomable surface of independent rule cards.
// Unlike the dialog graph the rules are a FLAT list with no wiring, so there are no ports or connectors —
// each card just shows its trigger, its condition/action counts and a cooldown/once badge, colour-coded by
// trigger category. A card drags to lay out (persisted into graphX/graphY), empty canvas pans, the wheel
// zooms; only cards intersecting the view are drawn so a boss with dozens of rules stays cheap. The full
// rule is edited in the host screen's dock. Rules carry no id, so selection is tracked by list index.
public class CombatRuleGraphWidget extends AbstractWidget {

    public static final int BOX_WIDTH = 98;
    private static final int HEADER_H = 12;
    private static final int BOX_HEIGHT = 40;
    private static final float MIN_ZOOM = 0.35F;
    private static final float MAX_ZOOM = 2.0F;
    private static final float DEFAULT_ZOOM = 0.9F;
    private static final int GRID_STEP = 48;
    private static final int CLICK_SLOP = 4;

    private static final int CANVAS_BG = 0xFF15151B;
    private static final int GRID_COLOR = 0xFF20202A;
    private static final int BOX_FILL = 0xFF20202A;
    private static final int BOX_BORDER = 0xFF54545F;
    private static final int GOLD = 0xFFE0C060;
    private static final int TEXT_COLOR = 0xFFEDEDF2;
    private static final int MUTED_COLOR = 0xFF9A9AA6;

    private static final int CAT_LIFECYCLE = 0xFF3C4E78;
    private static final int CAT_TIMING = 0xFF2F6E6A;
    private static final int CAT_HEALTH = 0xFF8A4A5E;
    private static final int CAT_DAMAGE = 0xFF8A6A2E;
    private static final int CAT_ADDS = 0xFF3E7A44;
    private static final int CAT_CONTROL = 0xFF5E4A86;

    private final List<NpcCombatRule> rules;
    private final Runnable onSelectionChanged;

    private double viewX;
    private double viewY;
    private float zoom = DEFAULT_ZOOM;
    private int bottomInset;

    private int selectedIndex = -1;
    private int pressedIndex = -1;
    private boolean panning;
    private double grabOffsetX;
    private double grabOffsetY;
    private double dragAccumulated;

    public CombatRuleGraphWidget(int x, int y, int width, int height, List<NpcCombatRule> rules,
                                 Runnable onSelectionChanged) {
        super(x, y, width, height, Component.empty());
        this.rules = rules;
        this.onSelectionChanged = onSelectionChanged;
    }

    public int selectedIndex() {
        return selectedIndex >= 0 && selectedIndex < rules.size() ? selectedIndex : -1;
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

    public void setSelection(int index) {
        this.selectedIndex = index;
    }

    public void deselect() {
        selectedIndex = -1;
        onSelectionChanged.run();
    }

    private int effectiveBottom() {
        return getY() + getHeight() - bottomInset;
    }

    public int newRuleX() {
        return (int) Math.round(viewX + (getWidth() / zoom - BOX_WIDTH) / 2.0);
    }

    public int newRuleY() {
        return (int) Math.round(viewY + ((effectiveBottom() - getY()) / zoom - BOX_HEIGHT) / 2.0);
    }

    public void selectAndFocus(int index) {
        selectedIndex = index;
        if (index >= 0 && index < rules.size()) {
            NpcCombatRule rule = rules.get(index);
            viewX = rule.graphX() + BOX_WIDTH / 2.0 - getWidth() / zoom / 2.0;
            viewY = rule.graphY() + BOX_HEIGHT / 2.0 - (effectiveBottom() - getY()) / zoom / 2.0;
        }
        onSelectionChanged.run();
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

    private boolean onScreen(NpcCombatRule rule) {
        int x = screenX(rule.graphX());
        int y = screenY(rule.graphY());
        int w = Math.round(BOX_WIDTH * zoom);
        int h = Math.round(BOX_HEIGHT * zoom);
        return x + w >= getX() && x <= getX() + getWidth() && y + h >= getY() && y <= effectiveBottom();
    }

    private int ruleAt(double sx, double sy) {
        double wx = worldX(sx);
        double wy = worldY(sy);
        for (int i = rules.size() - 1; i >= 0; i--) {
            NpcCombatRule rule = rules.get(i);
            if (wx >= rule.graphX() && wx <= rule.graphX() + BOX_WIDTH
                    && wy >= rule.graphY() && wy <= rule.graphY() + BOX_HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < effectiveBottom();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        dragAccumulated = 0;
        int hit = ruleAt(event.x(), event.y());
        if (hit >= 0) {
            pressedIndex = hit;
            panning = false;
            NpcCombatRule rule = rules.get(hit);
            grabOffsetX = worldX(event.x()) - rule.graphX();
            grabOffsetY = worldY(event.y()) - rule.graphY();
            if (hit != selectedIndex) {
                selectedIndex = hit;
                onSelectionChanged.run();
            }
        } else {
            pressedIndex = -1;
            panning = true;
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        dragAccumulated += Math.abs(dragX) + Math.abs(dragY);
        if (panning) {
            viewX -= dragX / zoom;
            viewY -= dragY / zoom;
            return;
        }
        if (pressedIndex < 0 || pressedIndex >= rules.size()) {
            return;
        }
        int nx = (int) Math.round(worldX(event.x()) - grabOffsetX);
        int ny = (int) Math.round(worldY(event.y()) - grabOffsetY);
        rules.set(pressedIndex, moved(rules.get(pressedIndex), nx, ny));
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        if (dragAccumulated < CLICK_SLOP && pressedIndex < 0 && panning) {
            deselect();
        }
        pressedIndex = -1;
        panning = false;
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

    private static NpcCombatRule moved(NpcCombatRule rule, int x, int y) {
        return new NpcCombatRule(rule.trigger(), rule.conditions(), rule.actions(),
                rule.cooldownSeconds(), rule.once(), x, y);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        int bottom = effectiveBottom();
        graphics.enableScissor(getX(), getY(), getX() + getWidth(), bottom);
        graphics.fill(getX(), getY(), getX() + getWidth(), bottom, CANVAS_BG);
        extractGrid(graphics, bottom);
        extractCards(graphics);
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

    private void extractCards(GuiGraphicsExtractor graphics) {
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < rules.size(); i++) {
            NpcCombatRule rule = rules.get(i);
            if (!onScreen(rule)) {
                continue;
            }
            int x = screenX(rule.graphX());
            int y = screenY(rule.graphY());
            int w = Math.round(BOX_WIDTH * zoom);
            int h = Math.round(BOX_HEIGHT * zoom);
            boolean selected = i == selectedIndex;
            graphics.fill(x, y, x + w, y + h, BOX_FILL);
            graphics.fill(x + 1, y + 1, x + w - 1, y + Math.max(3, Math.round(HEADER_H * zoom)),
                    categoryColor(rule.trigger().type()));
            graphics.outline(x, y, w, h, selected ? GOLD : BOX_BORDER);
            extractCardText(graphics, font, rule, x, y);
        }
    }

    // The label, counts and badge are laid out once in the card's own unscaled 98x40 space and drawn under a
    // pose scaled by the zoom, so the text always fills the box to the same proportions at any zoom instead
    // of being drawn at a fixed size that overflows when small and looks lost when large.
    private void extractCardText(GuiGraphicsExtractor graphics, Font font, NpcCombatRule rule, int x, int y) {
        org.joml.Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(zoom, zoom);
        String name = Component.translatable("gui.luna_npc.combat.trigger."
                + rule.trigger().type().getSerializedName()).getString();
        graphics.text(font, clip(font, name, BOX_WIDTH - 6), 3, 2, TEXT_COLOR);
        String counts = "C" + rule.conditions().size() + "  A" + rule.actions().size();
        graphics.text(font, Component.literal(counts), 3, HEADER_H + 2, MUTED_COLOR);
        String badge = badge(rule);
        if (!badge.isEmpty()) {
            graphics.text(font, Component.literal(badge), 3, HEADER_H + 13, GOLD);
        }
        pose.popMatrix();
    }

    private static String badge(NpcCombatRule rule) {
        StringBuilder builder = new StringBuilder();
        if (rule.cooldownSeconds() > 0.0F) {
            builder.append("⟳").append(String.format(java.util.Locale.ROOT, "%.1fs", rule.cooldownSeconds()));
        }
        if (rule.once()) {
            if (builder.length() > 0) {
                builder.append("  ");
            }
            builder.append("1×");
        }
        return builder.toString();
    }

    private static int categoryColor(NpcCombatTriggerType type) {
        return switch (type) {
            case ALWAYS, ON_COMBAT_START, ON_TARGET_ACQUIRED, ON_TARGET_LOST -> CAT_LIFECYCLE;
            case INTERVAL, ON_TIMER_ELAPSED, ON_ENRAGE_TIMER -> CAT_TIMING;
            case ON_HP_CROSSES -> CAT_HEALTH;
            case ON_TOOK_DAMAGE, ON_DEALT_DAMAGE, ON_DAMAGE_BURST, ON_INTERRUPTED -> CAT_DAMAGE;
            case ON_KILLED_TARGET, ON_ADD_DIED, ON_ALL_ADDS_DEAD -> CAT_ADDS;
            case ON_SURROUNDED, ON_STATUS_APPLIED, ON_TARGET_ACTION -> CAT_CONTROL;
        };
    }

    private static Component clip(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return Component.literal(text);
        }
        String value = text;
        while (!value.isEmpty() && font.width(value + "…") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return Component.literal(value + "…");
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
    }
}
