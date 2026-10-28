package net.lunacore.luna_npc.client.widget;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.ResolvableProfile;
import net.lunacore.luna_npc.api.LunaStick;
import net.lunacore.luna_npc.api.PermissionLogEntry;

// The audit-log viewer: a custom scrolling list over the whole 10 000-entry log without ever holding it in
// memory. It shows only the rows in view, fetches the 100-entry window each visible row falls in through a
// callback, and keeps a few recently-seen windows cached so scrolling back is instant (see the CLAUDE.md UI rules). Rows
// are compact: the actor's face, their name in bold with their UUID under it, and the action text beside them.
public class PagedLogList extends AbstractWidget {

    public interface WindowRequester {
        void request(int offset);
    }

    private static final int ROW_HEIGHT = 22;
    private static final int PAGE = 100;
    private static final int MAX_CACHED_WINDOWS = 6;
    private static final int NAME_COLUMN = 152;
    private static final int FACE_SIZE = 16;
    private static final int TEXT_LEFT = FACE_SIZE + 8;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int UUID_COLOR = 0xFF8A8A96;
    private static final int ACTION_COLOR = 0xFFD8D8E0;
    private static final int TIME_COLOR = 0xFF7A7A86;
    private static final int HINT_COLOR = 0xFFC8C8D0;
    private static final int ROW_STRIPE = 0x11FFFFFF;
    private static final int SCROLL_TRACK = 0x33000000;
    private static final int SCROLL_THUMB = 0xFF5A5A68;

    private final WindowRequester requester;
    // Access-ordered so the eldest untouched window is evicted first.
    private final Map<Integer, List<PermissionLogEntry>> windows =
            new LinkedHashMap<>(16, 0.75F, true);
    private final java.util.Set<Integer> pending = new java.util.HashSet<>();
    private final Map<java.util.UUID, ResolvableProfile> faces = new java.util.HashMap<>();
    private int total;
    private double scrollRow;

    public PagedLogList(int x, int y, int width, int height, WindowRequester requester) {
        super(x, y, width, height, Component.empty());
        this.requester = requester;
    }

    public void reset() {
        windows.clear();
        pending.clear();
        total = 0;
        scrollRow = 0;
        pending.add(0);
        requester.request(0);
    }

    public void acceptWindow(int offset, int total, List<PermissionLogEntry> entries,
                             List<ResolvableProfile> actors) {
        this.total = total;
        for (ResolvableProfile actor : actors) {
            faces.put(actor.partialProfile().id(), actor);
        }
        pending.remove(offset);
        windows.put(offset, entries);
        while (windows.size() > MAX_CACHED_WINDOWS) {
            windows.remove(windows.keySet().iterator().next());
        }
    }

    private int visibleRows() {
        return Math.max(1, getHeight() / ROW_HEIGHT);
    }

    private PermissionLogEntry entryAt(int index) {
        int windowOffset = index / PAGE * PAGE;
        List<PermissionLogEntry> window = windows.get(windowOffset);
        if (window != null) {
            int local = index - windowOffset;
            return local < window.size() ? window.get(local) : null;
        }
        if (pending.add(windowOffset)) {
            requester.request(windowOffset);
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scrollRow = Mth.clamp(scrollRow - scrollY, 0, Math.max(0, total - visibleRows()));
        return true;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        Font font = Minecraft.getInstance().font;
        graphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        if (total == 0) {
            graphics.centeredText(font, Component.translatable("gui.luna_npc.manage_permissions.log.empty"),
                    getX() + getWidth() / 2, getY() + getHeight() / 2 - 4, HINT_COLOR);
        } else {
            renderRows(graphics, font);
        }
        graphics.disableScissor();
        renderScrollbar(graphics);
    }

    private void renderRows(GuiGraphicsExtractor graphics, Font font) {
        int first = (int) Math.floor(scrollRow);
        // Whole-row rendering: every visible row sits fully inside the list, so a partial row can never
        // poke past the bottom edge while scrolling.
        for (int row = 0; row < visibleRows(); row++) {
            int index = first + row;
            if (index >= total) {
                break;
            }
            int top = getY() + row * ROW_HEIGHT;
            if ((index & 1) == 1) {
                graphics.fill(getX(), top, getX() + getWidth() - 4, top + ROW_HEIGHT, ROW_STRIPE);
            }
            PermissionLogEntry entry = entryAt(index);
            if (entry == null) {
                graphics.text(font, Component.literal("…"), getX() + 4, top + 7, TIME_COLOR);
                continue;
            }
            ResolvableProfile face = faces.get(entry.actor());
            if (face != null) {
                PlayerFaceExtractor.extractRenderState(graphics, face, getX() + 4, top + 3, FACE_SIZE);
            }
            String name = clip(font, entry.actorName().isEmpty() ? entry.actor().toString() : entry.actorName(),
                    NAME_COLUMN - TEXT_LEFT - 2);
            graphics.text(font, Component.literal(name).withStyle(ChatFormatting.BOLD), getX() + TEXT_LEFT, top + 3,
                    NAME_COLOR);
            graphics.text(font, clip(font, entry.actor().toString(), NAME_COLUMN - TEXT_LEFT - 2), getX() + TEXT_LEFT,
                    top + 12, UUID_COLOR);
            int actionX = getX() + NAME_COLUMN;
            int actionWidth = getX() + getWidth() - 6 - actionX;
            graphics.text(font, clipComponent(font, actionText(entry), actionWidth), actionX, top + 3, ACTION_COLOR);
            graphics.text(font, TIME_FORMAT.format(Instant.ofEpochMilli(entry.timestampMillis())),
                    actionX, top + 12, TIME_COLOR);
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics) {
        int visible = visibleRows();
        if (total <= visible) {
            return;
        }
        int trackX = getX() + getWidth() - 3;
        graphics.fill(trackX, getY(), trackX + 2, getY() + getHeight(), SCROLL_TRACK);
        int thumbHeight = Math.max(12, getHeight() * visible / total);
        int travel = getHeight() - thumbHeight;
        int thumbY = getY() + (int) (travel * (scrollRow / (total - visible)));
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, SCROLL_THUMB);
    }

    private static Component actionText(PermissionLogEntry entry) {
        String base = "gui.luna_npc.manage_permissions.log." + entry.action().getSerializedName();
        return switch (entry.action()) {
            case NPC_CREATED, NPC_EDITED, NPC_SPAWNED, NPC_REMOVED ->
                    Component.translatable(base, entry.targetName(), entry.targetId());
            case ALLIANCES_EDITED, ZONES_EDITED, ROUTES_EDITED -> Component.translatable(base);
            case SKIN_DOWNLOADED, SKIN_EDITED, SKIN_DELETED -> Component.translatable(base, entry.targetName());
            case PERMISSION_GRANTED, PERMISSION_REVOKED ->
                    Component.translatable(base, stickName(entry.targetId()), entry.targetName());
        };
    }

    private static Component stickName(long ordinal) {
        LunaStick[] sticks = LunaStick.values();
        if (ordinal < 0 || ordinal >= sticks.length) {
            return Component.literal("?");
        }
        return Component.translatable("item.luna_npc." + sticks[(int) ordinal].getSerializedName());
    }

    private static Component clipComponent(Font font, Component text, int maxWidth) {
        return Component.literal(clip(font, text.getString(), maxWidth));
    }

    private static String clip(Font font, String value, int maxWidth) {
        if (maxWidth <= 0 || font.width(value) <= maxWidth) {
            return value;
        }
        while (!value.isEmpty() && font.width(value + "…") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "…";
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
    }
}
