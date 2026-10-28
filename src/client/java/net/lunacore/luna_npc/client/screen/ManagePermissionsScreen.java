package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.api.LunaStick;
import net.lunacore.luna_npc.item.LunaNpcItems;
import net.lunacore.luna_npc.api.NpcAlliance;
import net.lunacore.luna_npc.client.ClientAlliances;
import net.lunacore.luna_npc.client.widget.PagedLogList;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.network.KnownPlayer;
import net.lunacore.luna_npc.network.PermissionGrantDto;
import net.lunacore.luna_npc.network.PermissionLogPayload;
import net.lunacore.luna_npc.network.PermissionsStatePayload;
import net.lunacore.luna_npc.network.RequestPermissionLogPayload;
import net.lunacore.luna_npc.network.UpdatePermissionsPayload;

// The Manage Permissions stick's screen (OP-only). Two tabs: Sticks grants each gated stick to players or
// alliances (a live-toggle checklist that applies each change immediately); Log views the audit trail
// through the windowed PagedLogList. Grants live in local mirrors that each edit flips optimistically and
// the server's echoed state then confirms.
public class ManagePermissionsScreen extends LunaScreen {

    private static final String GUI = "gui.luna_npc.manage_permissions.";
    private static final int WARNING_COLOR = 0xFFE33B3B;
    private static final int HINT_COLOR = 0xFFC8C8D0;
    private static final int TOP = 54;
    private static final int LEFT_WIDTH = 128;
    private static final int RIGHT_WIDTH = 232;
    private static final int GAP = 12;
    private static final String[] CATEGORIES = {"", "npcs", "alliances", "zones", "routes", "permissions"};
    private static final String[] CATEGORY_KEYS = {"all", "npcs", "alliances", "zones", "routes", "permissions"};

    private enum Tab { STICKS, LOG }

    private enum GrantMode { PLAYERS, ALLIANCES }

    private boolean offlineMode;
    private final Map<LunaStick, Set<UUID>> playerGrants = new EnumMap<>(LunaStick.class);
    private final Map<LunaStick, Set<String>> allianceGrants = new EnumMap<>(LunaStick.class);
    private final List<KnownPlayer> knownPlayers = new ArrayList<>();

    private Tab tab = Tab.STICKS;
    private GrantMode grantMode = GrantMode.PLAYERS;
    private LunaStick selectedStick = LunaStick.CREATE_NPC;
    private String logCategory = "";

    private Button sticksTab;
    private Button logTab;
    private Button playersTab;
    private Button alliancesTab;
    private SimpleSelectionList stickList;
    private SimpleSelectionList grantList;
    private EditBox addPlayerField;
    private Button addPlayerButton;
    private final List<Button> categoryButtons = new ArrayList<>();
    private PagedLogList logList;
    // The player UUIDs the grant list currently shows, so an echoed state only rebuilds it (resetting
    // scroll) when a row was actually added or removed — not on every in-place toggle.
    private final Set<UUID> shownPlayers = new LinkedHashSet<>();

    public ManagePermissionsScreen(PermissionsStatePayload state) {
        super(Component.translatable(GUI + "title"));
        applyState(state);
    }

    public void applyState(PermissionsStatePayload state) {
        offlineMode = state.offlineMode();
        playerGrants.clear();
        allianceGrants.clear();
        for (PermissionGrantDto grant : state.grants()) {
            playerGrants.put(grant.stick(), new LinkedHashSet<>(grant.players()));
            allianceGrants.put(grant.stick(), new LinkedHashSet<>(grant.alliances()));
        }
        knownPlayers.clear();
        knownPlayers.addAll(state.knownPlayers());
        // Alliance toggles and in-place player toggles recolour from the live sets on their own; only a
        // changed player-row set (an add/revoke of an offline player) needs a rebuild.
        if (tab == Tab.STICKS && grantList != null && grantMode == GrantMode.PLAYERS
                && !visiblePlayers().equals(shownPlayers)) {
            rebuildGrantList();
        }
    }

    private Set<UUID> visiblePlayers() {
        Set<UUID> keys = new LinkedHashSet<>();
        for (KnownPlayer known : knownPlayers) {
            keys.add(known.id());
        }
        keys.addAll(grantedPlayers(selectedStick));
        return keys;
    }

    public void applyLog(PermissionLogPayload payload) {
        if (logList != null && payload.category().equals(logCategory)) {
            logList.acceptWindow(payload.offset(), payload.total(), payload.entries(), payload.actors());
        }
    }

    private int totalWidth() {
        return LEFT_WIDTH + GAP + RIGHT_WIDTH;
    }

    private int leftX() {
        return this.width / 2 - totalWidth() / 2;
    }

    private int rightX() {
        return leftX() + LEFT_WIDTH + GAP;
    }

    private int listBottom() {
        return this.height - 64;
    }

    private int grantListTop() {
        return TOP + 44;
    }

    private int logListTop() {
        return TOP + 26;
    }

    @Override
    protected void init() {
        stickList = null;
        grantList = null;
        addPlayerField = null;
        addPlayerButton = null;
        logList = null;
        categoryButtons.clear();

        sticksTab = addRenderableWidget(Button.builder(Component.translatable(GUI + "tab.sticks"),
                button -> switchTab(Tab.STICKS)).bounds(this.width / 2 - 102, 28, 100, 20).build());
        logTab = addRenderableWidget(Button.builder(Component.translatable(GUI + "tab.log"),
                button -> switchTab(Tab.LOG)).bounds(this.width / 2 + 2, 28, 100, 20).build());
        sticksTab.active = tab != Tab.STICKS;
        logTab.active = tab != Tab.LOG;

        if (tab == Tab.STICKS) {
            initSticksTab();
        } else {
            initLogTab();
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private void initSticksTab() {
        int left = leftX();
        int right = rightX();
        stickList = addRenderableWidget(new SimpleSelectionList(LEFT_WIDTH, listBottom() - TOP, TOP, 18));
        stickList.setX(left);

        int tabW = (RIGHT_WIDTH - 4) / 2;
        boolean players = grantMode == GrantMode.PLAYERS;
        playersTab = addRenderableWidget(Button.builder(Component.translatable(GUI + "grant.players"),
                button -> switchGrantMode(GrantMode.PLAYERS)).bounds(right, TOP, tabW, 18).build());
        alliancesTab = addRenderableWidget(Button.builder(Component.translatable(GUI + "grant.alliances"),
                button -> switchGrantMode(GrantMode.ALLIANCES)).bounds(right + tabW + 4, TOP, tabW, 18).build());
        playersTab.active = !players;
        alliancesTab.active = players;

        addPlayerField = addRenderableWidget(new EditBox(this.font, right, TOP + 22, RIGHT_WIDTH - 64, 18,
                Component.translatable(GUI + "grant.add_player_prompt")));
        addPlayerField.setMaxLength(48);
        addPlayerField.setHint(Component.translatable(GUI + "grant.add_player_prompt"));
        addPlayerButton = addRenderableWidget(Button.builder(Component.translatable(GUI + "grant.add_player"),
                button -> addPlayer()).bounds(right + RIGHT_WIDTH - 60, TOP + 22, 60, 18).build());
        addPlayerField.visible = players;
        addPlayerButton.visible = players;

        grantList = addRenderableWidget(new SimpleSelectionList(RIGHT_WIDTH, listBottom() - grantListTop(), grantListTop()));
        grantList.setX(right);

        rebuildStickList();
        rebuildGrantList();
    }

    private void initLogTab() {
        int left = leftX();
        int categoryWidth = (totalWidth() - 16) / CATEGORIES.length;
        for (int i = 0; i < CATEGORIES.length; i++) {
            String category = CATEGORIES[i];
            Button button = addRenderableWidget(Button.builder(
                    Component.translatable(GUI + "log.category." + CATEGORY_KEYS[i]), b -> setCategory(category))
                    .bounds(left + i * (categoryWidth + 4), TOP, categoryWidth, 18).build());
            button.active = !category.equals(logCategory);
            categoryButtons.add(button);
        }
        logList = addRenderableWidget(new PagedLogList(left, logListTop(), totalWidth(),
                listBottom() - logListTop(), this::requestLog));
        logList.reset();
    }

    private void switchTab(Tab newTab) {
        tab = newTab;
        rebuildWidgets();
    }

    private void switchGrantMode(GrantMode mode) {
        grantMode = mode;
        boolean players = mode == GrantMode.PLAYERS;
        playersTab.active = !players;
        alliancesTab.active = players;
        addPlayerField.visible = players;
        addPlayerButton.visible = players;
        rebuildGrantList();
    }

    private void setCategory(String category) {
        logCategory = category;
        for (int i = 0; i < categoryButtons.size(); i++) {
            categoryButtons.get(i).active = !CATEGORIES[i].equals(category);
        }
        logList.reset();
    }

    private void rebuildStickList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (LunaStick stick : LunaStick.values()) {
            ItemStack iconStack = new ItemStack(itemFor(stick));
            SimpleSelectionList.IconRenderer icon = (graphics, x, yMiddle) ->
                    graphics.item(iconStack, x, yMiddle - 8);
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(stick.getSerializedName(),
                    Component.translatable("item.luna_npc." + stick.getSerializedName()),
                    id -> selectStick(stick), icon, 0xFFFFFFFF);
            entries.add(entry);
            if (stick == selectedStick) {
                selected = entry;
            }
        }
        stickList.fill(entries, selected);
    }

    private static Item itemFor(LunaStick stick) {
        return switch (stick) {
            case CREATE_NPC -> LunaNpcItems.CREATE_NPC;
            case EDIT_NPC -> LunaNpcItems.EDIT_NPC;
            case SPAWN_NPC -> LunaNpcItems.SPAWN_NPC;
            case REMOVE_NPC -> LunaNpcItems.REMOVE_NPC;
            case EDIT_ALLIANCES -> LunaNpcItems.EDIT_ALLIANCES;
            case ZONE_WAND -> LunaNpcItems.ZONE_WAND;
            case ROUTE_CREATOR -> LunaNpcItems.ROUTE_CREATOR;
        };
    }

    private void selectStick(LunaStick stick) {
        selectedStick = stick;
        rebuildGrantList();
    }

    private void rebuildGrantList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        if (grantMode == GrantMode.PLAYERS) {
            Map<UUID, String> shown = new LinkedHashMap<>();
            for (KnownPlayer known : knownPlayers) {
                shown.putIfAbsent(known.id(), known.name());
            }
            for (UUID granted : grantedPlayers(selectedStick)) {
                shown.putIfAbsent(granted, granted.toString());
            }
            shownPlayers.clear();
            shownPlayers.addAll(shown.keySet());
            for (Map.Entry<UUID, String> row : shown.entrySet()) {
                UUID uuid = row.getKey();
                entries.add(new SimpleSelectionList.Entry(uuid.toString(), Component.literal(row.getValue()),
                        id -> togglePlayer(uuid), null, () -> grantedPlayers(selectedStick).contains(uuid)));
            }
        } else {
            for (NpcAlliance alliance : ClientAlliances.all()) {
                String id = alliance.id();
                entries.add(new SimpleSelectionList.Entry(id, Component.literal(alliance.name()),
                        ignored -> toggleAlliance(id), null, () -> grantedAlliances(selectedStick).contains(id)));
            }
        }
        grantList.fill(entries, null);
    }

    private Set<UUID> grantedPlayers(LunaStick stick) {
        return playerGrants.getOrDefault(stick, Set.of());
    }

    private Set<String> grantedAlliances(LunaStick stick) {
        return allianceGrants.getOrDefault(stick, Set.of());
    }

    private void togglePlayer(UUID uuid) {
        Set<UUID> set = playerGrants.computeIfAbsent(selectedStick, ignored -> new LinkedHashSet<>());
        boolean granted = set.contains(uuid);
        ClientPlayNetworking.send(new UpdatePermissionsPayload(selectedStick, false, !granted, uuid.toString()));
        if (granted) {
            set.remove(uuid);
        } else {
            set.add(uuid);
        }
    }

    private void toggleAlliance(String allianceId) {
        Set<String> set = allianceGrants.computeIfAbsent(selectedStick, ignored -> new LinkedHashSet<>());
        boolean granted = set.contains(allianceId);
        ClientPlayNetworking.send(new UpdatePermissionsPayload(selectedStick, true, !granted, allianceId));
        if (granted) {
            set.remove(allianceId);
        } else {
            set.add(allianceId);
        }
    }

    private void addPlayer() {
        String value = addPlayerField.getValue().trim();
        if (value.isEmpty()) {
            return;
        }
        ClientPlayNetworking.send(new UpdatePermissionsPayload(selectedStick, false, true, value));
        addPlayerField.setValue("");
    }

    private void requestLog(int offset) {
        ClientPlayNetworking.send(new RequestPermissionLogPayload(offset, logCategory));
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (tab == Tab.STICKS) {
            panel(graphics, leftX() - 5, TOP - 5, LEFT_WIDTH + 10, listBottom() - TOP + 10);
            panel(graphics, rightX() - 6, TOP - 5, RIGHT_WIDTH + 12, listBottom() - TOP + 10);
            graphics.outline(leftX(), TOP, LEFT_WIDTH, listBottom() - TOP, PANEL_BORDER);
            graphics.outline(rightX(), grantListTop(), RIGHT_WIDTH, listBottom() - grantListTop(), PANEL_BORDER);
        } else {
            panel(graphics, leftX() - 5, TOP - 5, totalWidth() + 10, listBottom() - TOP + 10);
            graphics.outline(leftX(), logListTop(), totalWidth(), listBottom() - logListTop(), PANEL_BORDER);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        if (tab == Tab.STICKS) {
            if (grantMode == GrantMode.ALLIANCES) {
                graphics.textWithWordWrap(this.font, Component.translatable(GUI + "grant.alliances_hint"),
                        rightX() + 2, TOP + 24, RIGHT_WIDTH - 4, HINT_COLOR);
            } else if (offlineMode) {
                graphics.textWithWordWrap(this.font, Component.literal("⚠ ")
                        .append(Component.translatable(GUI + "offline_warning")),
                        leftX(), listBottom() + 4, totalWidth(), WARNING_COLOR);
            }
        }
        graphics.centeredText(this.font, Component.translatable(GUI + "grant.admin_note"),
                this.width / 2, this.height - 40, HINT_COLOR);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}
