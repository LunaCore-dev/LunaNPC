package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.lunacore.luna_npc.api.NpcAlliance;
import net.lunacore.luna_npc.client.ClientAlliances;
import net.lunacore.luna_npc.client.util.FuzzySearch;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.network.UpdateAlliancesPayload;
import net.lunacore.luna_npc.npc.NpcValidation;

// The global faction manager (Edit Alliances stick). One page: pick an alliance on the left; on
// the right rename it (custom only) and switch between its Members, Attacks and Protects tabs,
// clicking entries to toggle them (they recolour gold). Built-in factions are the fixed vanilla
// ecosystem and are view-only. To keep multiplayer sane, players aren't listed inline — an
// "Individual players" row drills into them. Nothing commits until Save.
public class EditAlliancesScreen extends LunaScreen {

    private static final int WARNING_COLOR = 0xFFE33B3B;
    // MISC-category entities that are really mobs and should still be offered.
    private static final List<String> MISC_MOBS = List.of(
            "minecraft:iron_golem", "minecraft:snow_golem", "minecraft:copper_golem",
            "minecraft:armor_stand", "minecraft:villager", "minecraft:wandering_trader");

    private enum Mode { MEMBERS, ATTACKS, PROTECTS }

    public static final class Draft {
        public final String id;
        public String name;
        public final boolean builtin;
        public final List<String> memberMobTypes;
        public final List<String> memberPlayers;
        public final List<String> enemies;
        public final List<String> mates;
        public boolean matchAllPlayers;
        // Alliance-wide friendly-fire rule: NpcAlliance.FF_PER_NPC / FF_ALLOW / FF_BLOCK.
        public String friendlyFire = NpcAlliance.FF_PER_NPC;

        Draft(NpcAlliance alliance) {
            this(alliance.id(), alliance.name(), alliance.builtin(),
                    new ArrayList<>(alliance.memberMobTypes()), new ArrayList<>(alliance.memberPlayers()),
                    new ArrayList<>(alliance.enemies()), new ArrayList<>(alliance.mates()));
            this.matchAllPlayers = alliance.matchAllPlayers();
            this.friendlyFire = alliance.friendlyFire();
        }

        Draft(String id, String name, boolean builtin, List<String> memberMobTypes,
              List<String> memberPlayers, List<String> enemies, List<String> mates) {
            this.id = id;
            this.name = name;
            this.builtin = builtin;
            this.memberMobTypes = memberMobTypes;
            this.memberPlayers = memberPlayers;
            this.enemies = enemies;
            this.mates = mates;
        }

        NpcAlliance toAlliance() {
            return new NpcAlliance(id, name, builtin, memberMobTypes, memberPlayers, enemies, mates,
                    matchAllPlayers, friendlyFire);
        }

        boolean hasConflict() {
            if (enemies.contains(id)) {
                return true;
            }
            for (String enemy : enemies) {
                if (mates.contains(enemy)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record MemberRow(String key, Component label, String search, boolean player) {
    }

    private static final int TOP = 44;
    private static final int LEFT_WIDTH = 118;
    private static final int RIGHT_WIDTH = 210;
    private static final int GAP = 12;
    // Height reserved inside the left panel for the Add/Remove button row beneath the list.
    private static final int BUTTON_STRIP = 24;

    private final List<Draft> drafts = new ArrayList<>();
    private final List<MemberRow> mobRows = new ArrayList<>();
    private final List<MemberRow> playerRows = new ArrayList<>();
    private String selectedId = "";
    private Mode mode = Mode.MEMBERS;
    private boolean playerSublist;
    private String query = "";

    private SimpleSelectionList allianceList;
    private SimpleSelectionList itemList;
    private EditBox nameField;
    private EditBox searchField;
    private Button membersTab;
    private Button attacksTab;
    private Button protectsTab;
    private Button deleteButton;
    private Button friendlyFireButton;

    public EditAlliancesScreen() {
        super(Component.translatable("gui.luna_npc.alliances.title"));
        for (NpcAlliance alliance : ClientAlliances.all()) {
            drafts.add(new Draft(alliance));
        }
        if (!drafts.isEmpty()) {
            selectedId = drafts.get(0).id;
        }
        collectRows();
    }

    private void collectRows() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            minecraft.getConnection().getOnlinePlayers().stream()
                    .sorted(java.util.Comparator.comparing(info -> info.getProfile().name().toLowerCase(Locale.ROOT)))
                    .forEach(info -> playerRows.add(new MemberRow(info.getProfile().id().toString(),
                            Component.literal(info.getProfile().name()),
                            info.getProfile().name().toLowerCase(Locale.ROOT), true)));
        }
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            if ("minecraft:player".equals(id)) {
                continue;
            }
            if (type.getCategory() != MobCategory.MISC || MISC_MOBS.contains(id)) {
                // Search both the id and the display name so "iron golem" finds minecraft:iron_golem.
                Component label = Component.translatable(type.getDescriptionId());
                mobRows.add(new MemberRow(id, label, id + " " + label.getString(), false));
            }
        }
        mobRows.sort(java.util.Comparator.comparing(row -> row.label().getString().toLowerCase(Locale.ROOT)));
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

    private int itemListTop() {
        return TOP + 92;
    }

    private Draft selected() {
        for (Draft draft : drafts) {
            if (draft.id.equals(selectedId)) {
                return draft;
            }
        }
        return null;
    }

    @Override
    protected void init() {
        int left = leftX();
        int right = rightX();
        int halfLeft = (LEFT_WIDTH - 4) / 2;

        // The list and its Add/Remove buttons share one panel: the list stops short of the bottom
        // and the buttons sit in the reserved strip, so nothing floats below the card.
        int buttonRowY = listBottom() - 20;
        allianceList = addRenderableWidget(new SimpleSelectionList(LEFT_WIDTH, listBottom() - TOP - BUTTON_STRIP, TOP));
        allianceList.setX(left);
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.alliances.new"), button -> {
            if (drafts.size() < NpcValidation.MAX_ALLIANCES) {
                String id = freshCustomId();
                drafts.add(new Draft(id, "Alliance", false, new ArrayList<>(), new ArrayList<>(),
                        new ArrayList<>(), new ArrayList<>()));
                selectedId = id;
                rebuild();
            }
        }).bounds(left, buttonRowY, halfLeft, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.alliances.delete"),
                button -> deleteSelected()).bounds(left + halfLeft + 4, buttonRowY, halfLeft, 20).build());

        nameField = addRenderableWidget(new EditBox(this.font, right, TOP, RIGHT_WIDTH, 20,
                Component.translatable("gui.luna_npc.alliances.name")));
        nameField.setMaxLength(NpcValidation.MAX_ALLIANCE_NAME_LENGTH);
        nameField.setResponder(value -> {
            Draft draft = selected();
            if (draft != null && !draft.builtin) {
                draft.name = value;
                rebuildAllianceList();
            }
        });

        int tabW = (RIGHT_WIDTH - 8) / 3;
        membersTab = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.alliances.tab.members"),
                button -> switchMode(Mode.MEMBERS)).bounds(right, TOP + 24, tabW, 20).build());
        attacksTab = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.alliances.tab.attacks"),
                button -> switchMode(Mode.ATTACKS)).bounds(right + tabW + 4, TOP + 24, tabW, 20).build());
        protectsTab = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.alliances.tab.protects"),
                button -> switchMode(Mode.PROTECTS)).bounds(right + 2 * (tabW + 4), TOP + 24, tabW, 20).build());

        // Alliance-wide friendly-fire rule for this faction's own members (cycles Per NPC / on / off).
        friendlyFireButton = addRenderableWidget(Button.builder(Component.empty(),
                button -> cycleFriendlyFire()).bounds(right, TOP + 48, RIGHT_WIDTH, 18).build());

        searchField = addRenderableWidget(new EditBox(this.font, right, TOP + 70, RIGHT_WIDTH, 18,
                Component.translatable("gui.luna_npc.item_picker.search")));
        searchField.setMaxLength(100);
        searchField.setHint(Component.translatable("gui.luna_npc.item_picker.search"));
        searchField.setValue(query);
        searchField.setResponder(value -> {
            query = value;
            rebuildItemList();
        });

        itemList = addRenderableWidget(new SimpleSelectionList(RIGHT_WIDTH, listBottom() - itemListTop(), itemListTop()));
        itemList.setX(right);

        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.alliances.save"), button -> save())
                .bounds(this.width / 2 - 102, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(this.width / 2 + 2, this.height - 28, 100, 20).build());

        rebuild();
    }

    private String freshCustomId() {
        int n = 1;
        String id = "custom" + n;
        while (containsDraft(id)) {
            id = "custom" + (++n);
        }
        return id;
    }

    private boolean containsDraft(String id) {
        for (Draft draft : drafts) {
            if (draft.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void deleteSelected() {
        Draft draft = selected();
        if (draft == null || draft.builtin) {
            return;
        }
        drafts.remove(draft);
        for (Draft other : drafts) {
            other.enemies.remove(draft.id);
            other.mates.remove(draft.id);
        }
        selectedId = drafts.isEmpty() ? "" : drafts.get(0).id;
        rebuild();
    }

    private void switchMode(Mode newMode) {
        mode = newMode;
        playerSublist = false;
        rebuildItemList();
        refreshControls();
    }

    private void cycleFriendlyFire() {
        Draft draft = selected();
        if (draft == null || draft.builtin) {
            return;
        }
        draft.friendlyFire = switch (draft.friendlyFire) {
            case NpcAlliance.FF_PER_NPC -> NpcAlliance.FF_ALLOW;
            case NpcAlliance.FF_ALLOW -> NpcAlliance.FF_BLOCK;
            default -> NpcAlliance.FF_PER_NPC;
        };
        refreshControls();
    }

    private static Component friendlyFireLabel(String value) {
        String key = switch (value) {
            case NpcAlliance.FF_ALLOW -> "gui.luna_npc.alliances.ff.allow";
            case NpcAlliance.FF_BLOCK -> "gui.luna_npc.alliances.ff.block";
            default -> "gui.luna_npc.alliances.ff.per_npc";
        };
        return Component.translatable("gui.luna_npc.alliances.friendly_fire")
                .append(": ").append(Component.translatable(key));
    }

    private void save() {
        List<NpcAlliance> out = new ArrayList<>();
        for (Draft draft : drafts) {
            out.add(draft.toAlliance());
        }
        ClientPlayNetworking.send(new UpdateAlliancesPayload(out));
        onClose();
    }

    private void rebuild() {
        rebuildAllianceList();
        rebuildItemList();
        refreshControls();
    }

    private void rebuildAllianceList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selectedEntry = null;
        for (Draft draft : drafts) {
            boolean conflict = draft.hasConflict();
            Component label = conflict ? Component.literal("⚠ " + draft.name) : Component.literal(draft.name);
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(draft.id, label,
                    this::onAllianceSelected, null, conflict ? WARNING_COLOR : 0xFFFFFFFF);
            entries.add(entry);
            if (draft.id.equals(selectedId)) {
                selectedEntry = entry;
            }
        }
        allianceList.fill(entries, selectedEntry);
    }

    private void rebuildItemList() {
        Draft draft = selected();
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        if (draft != null) {
            boolean editable = !draft.builtin;
            if (mode == Mode.MEMBERS) {
                buildMemberEntries(draft, editable, entries);
            } else {
                List<String> onList = mode == Mode.ATTACKS ? draft.enemies : draft.mates;
                List<String> otherList = mode == Mode.ATTACKS ? draft.mates : draft.enemies;
                for (Draft other : drafts) {
                    if (other.id.equals(draft.id) || !FuzzySearch.matches(query, other.name)) {
                        continue;
                    }
                    entries.add(new SimpleSelectionList.Entry(other.id, Component.literal(other.name),
                            id -> { if (editable) { toggleRelation(other.id, onList, otherList); } },
                            null, () -> onList.contains(other.id)));
                }
            }
        }
        itemList.fill(entries, null);
    }

    private void buildMemberEntries(Draft draft, boolean editable,
                                    List<SimpleSelectionList.Entry> entries) {
        if (playerSublist) {
            entries.add(new SimpleSelectionList.Entry("__back", Component.translatable("gui.luna_npc.alliances.back"),
                    id -> { playerSublist = false; rebuildItemList(); }));
            // One toggle to include every player at once, above the individual list.
            entries.add(new SimpleSelectionList.Entry("__all_players",
                    Component.translatable("gui.luna_npc.alliances.all_players"),
                    id -> { if (editable) { draft.matchAllPlayers = !draft.matchAllPlayers; } },
                    null, () -> draft.matchAllPlayers));
            for (MemberRow row : playerRows) {
                if (!FuzzySearch.matches(query, row.search())) {
                    continue;
                }
                entries.add(new SimpleSelectionList.Entry(row.key(), row.label(),
                        id -> { if (editable) { toggleMember(row); } }, null,
                        () -> draft.memberPlayers.contains(row.key())));
            }
            return;
        }
        // A single row drills into the player list instead of listing thousands inline.
        entries.add(new SimpleSelectionList.Entry("__players",
                Component.translatable("gui.luna_npc.alliances.individual_players", draft.memberPlayers.size()),
                id -> { playerSublist = true; rebuildItemList(); }));
        for (MemberRow row : mobRows) {
            if (!FuzzySearch.matches(query, row.search())) {
                continue;
            }
            entries.add(new SimpleSelectionList.Entry(row.key(), row.label(),
                    id -> { if (editable) { toggleMember(row); } }, null,
                    () -> draft.memberMobTypes.contains(row.key())));
        }
    }

    private void toggleMember(MemberRow row) {
        Draft draft = selected();
        if (draft == null || draft.builtin) {
            return;
        }
        List<String> members = row.player() ? draft.memberPlayers : draft.memberMobTypes;
        if (members.remove(row.key())) {
            return;
        }
        // Exclusive membership: hand it to this alliance only.
        for (Draft other : drafts) {
            if (other != draft) {
                (row.player() ? other.memberPlayers : other.memberMobTypes).remove(row.key());
            }
        }
        members.add(row.key());
    }

    private void toggleRelation(String id, List<String> onList, List<String> otherList) {
        if (!onList.remove(id)) {
            onList.add(id);
            otherList.remove(id);
        }
    }

    private void onAllianceSelected(String id) {
        selectedId = id;
        playerSublist = false;
        rebuildItemList();
        refreshControls();
    }

    private void refreshControls() {
        Draft draft = selected();
        boolean has = draft != null;
        nameField.visible = has;
        searchField.visible = has;
        membersTab.visible = has;
        attacksTab.visible = has;
        protectsTab.visible = has;
        friendlyFireButton.visible = has;
        friendlyFireButton.active = has && !draft.builtin;
        if (has) {
            friendlyFireButton.setMessage(friendlyFireLabel(draft.friendlyFire));
        }
        deleteButton.active = has && !draft.builtin;
        membersTab.active = mode != Mode.MEMBERS;
        attacksTab.active = mode != Mode.ATTACKS;
        protectsTab.active = mode != Mode.PROTECTS;
        if (has) {
            nameField.setEditable(!draft.builtin);
            if (!nameField.getValue().equals(draft.name)) {
                nameField.setValue(draft.name);
            }
        }
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        panel(graphics, leftX() - 5, TOP - 5, LEFT_WIDTH + 10, listBottom() - TOP + 10);
        panel(graphics, rightX() - 6, TOP - 5, RIGHT_WIDTH + 12, listBottom() - TOP + 10);
        // Each embedded list gets a thin inset border of its own (the CLAUDE.md UI rules).
        graphics.outline(leftX(), TOP, LEFT_WIDTH, listBottom() - TOP - BUTTON_STRIP, PANEL_BORDER);
        graphics.outline(rightX(), itemListTop(), RIGHT_WIDTH, listBottom() - itemListTop(), PANEL_BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        Draft draft = selected();
        if (draft != null && draft.builtin) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.alliances.locked"),
                    rightX() + 2, listBottom() + 8, LABEL_COLOR);
        } else if (draft != null && draft.hasConflict()) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.alliances.conflict"),
                    rightX() + 2, listBottom() + 8, WARNING_COLOR);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}
