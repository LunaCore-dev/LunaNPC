package net.lunacore.luna_npc.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.api.NpcAlliance;
import net.lunacore.luna_npc.api.NpcBodyguardHire;
import net.lunacore.luna_npc.api.NpcBodyguardSettings;
import net.lunacore.luna_npc.api.NpcBodyguardSource;
import net.lunacore.luna_npc.api.NpcGiveUpAction;
import net.lunacore.luna_npc.api.NpcJobSettings;
import net.lunacore.luna_npc.api.NpcPlayerRef;
import net.lunacore.luna_npc.api.NpcProtectTargets;
import net.lunacore.luna_npc.api.NpcTradeStack;
import net.lunacore.luna_npc.api.NpcWarnings;
import net.lunacore.luna_npc.client.ClientAlliances;
import net.lunacore.luna_npc.client.ClientNpcCatalog;
import net.lunacore.luna_npc.client.util.ClientRegistries;
import net.lunacore.luna_npc.client.widget.ItemSlotButton;
import net.lunacore.luna_npc.client.widget.LabeledSlider;
import net.lunacore.luna_npc.network.NpcSummary;
import net.lunacore.luna_npc.npc.NpcEquipment;
import net.lunacore.luna_npc.npc.NpcValidation;

// The BODYGUARD job's settings on three pages. Protect: the one "Guards" switch — chosen targets (players,
// alliances, NPCs) or whoever hires it (price and hire time) — and the range, so the two ways can never
// look active at once. Follow: stay and walk distances, teleporting, what happens after the protectee's
// death and when it gives up. Defend: how long it fights, assisting, retreating and whether the ones it
// guards can hurt it. The setup warnings
// show in red above Done, which hands the settings back.
public class EditBodyguardScreen extends LunaScreen {

    private enum Page { PROTECT, FOLLOW, DEFEND }

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 300;
    private static final int LABEL_X = 12;
    private static final int CONTROL_X = 150;
    private static final int ROW = 22;
    private static final int PAGES_Y = 18;
    private static final int CONTENT_Y = 46;
    private static final int COST_SLOTS = NpcValidation.MAX_TRADE_SLOTS;
    private static final int SLOT_PITCH = 30;
    private static final int WARNING_COLOR = 0xFFE33B3B;

    private final LunaScreen parent;
    private final Consumer<NpcBodyguardSettings> onApply;
    private Page page = Page.PROTECT;

    private NpcBodyguardSource source;
    private final Map<UUID, String> players = new LinkedHashMap<>();
    private final Set<String> alliances = new LinkedHashSet<>();
    private final Set<Long> npcs = new LinkedHashSet<>();
    private int range;
    private int stayRadius;
    private int walkWithin;
    private int teleportDistance;
    private int defenseSeconds;
    private boolean assist;
    private boolean retreat;
    private int retreatDistance;
    private boolean friendlyFire;
    private final String[] costIds = new String[COST_SLOTS];
    private final int[] costCounts = new int[COST_SLOTS];
    private int hireSeconds;
    private boolean keepAfterDeath;
    private NpcGiveUpAction giveUp;

    private final ItemSlotButton[] costSlots = new ItemSlotButton[COST_SLOTS];

    public EditBodyguardScreen(LunaScreen parent, NpcBodyguardSettings settings, Consumer<NpcBodyguardSettings> onApply) {
        super(Component.translatable("gui.luna_npc.edit_bodyguard.title"));
        this.parent = parent;
        this.onApply = onApply;
        source = settings.source();
        NpcProtectTargets protect = settings.protect();
        protect.players().forEach(ref -> players.put(ref.id(), ref.name()));
        alliances.addAll(protect.allianceIds());
        npcs.addAll(protect.npcLunaIds());
        range = settings.range();
        stayRadius = settings.stayRadius();
        walkWithin = settings.walkWithin();
        teleportDistance = settings.teleportDistance();
        defenseSeconds = settings.defenseSeconds();
        assist = settings.assistAttacks();
        retreat = settings.retreat();
        retreatDistance = settings.retreatDistance();
        friendlyFire = settings.friendlyFire();
        NpcBodyguardHire hire = settings.hire();
        for (int index = 0; index < COST_SLOTS; index++) {
            boolean filled = index < hire.cost().size();
            costIds[index] = filled ? hire.cost().get(index).itemId() : "";
            costCounts[index] = filled ? hire.cost().get(index).count() : 1;
        }
        hireSeconds = hire.hireSeconds();
        keepAfterDeath = settings.keepAfterDeath();
        giveUp = settings.giveUp();
    }

    // "2 players, 1 NPC", or "Nobody yet" — shared with the Jobs tab summary card.
    public static Component protectSummary(NpcProtectTargets protect) {
        if (protect.isEmpty()) {
            return Component.translatable("gui.luna_npc.edit_bodyguard.protect_nobody");
        }
        List<String> parts = new ArrayList<>();
        addCount(parts, protect.players().size(), "player");
        addCount(parts, protect.allianceIds().size(), "alliance");
        addCount(parts, protect.npcLunaIds().size(), "npc");
        return Component.literal(String.join(", ", parts));
    }

    private static void addCount(List<String> parts, int count, String key) {
        if (count > 0) {
            parts.add(Component.translatable("gui.luna_npc.edit_bodyguard.count." + key + (count == 1 ? "" : "s"),
                    count).getString());
        }
    }

    // The red setup warnings that concern these settings alone (the attack one lives in the Combat tab).
    public static List<Component> warnings(NpcBodyguardSettings settings) {
        NpcJobSettings jobs = NpcJobSettings.bodyguard(settings);
        List<Component> out = new ArrayList<>();
        if (NpcWarnings.bodyguardProtectsNobody(jobs)) {
            out.add(Component.translatable("gui.luna_npc.create_npc.bodyguard_nobody_warning"));
        }
        if (NpcWarnings.bodyguardRangeTooShort(jobs)) {
            out.add(Component.translatable("gui.luna_npc.create_npc.bodyguard_range_warning"));
        }
        if (NpcWarnings.bodyguardTeleportTooClose(jobs)) {
            out.add(Component.translatable("gui.luna_npc.create_npc.bodyguard_teleport_warning"));
        }
        if (NpcWarnings.bodyguardRetreatTooClose(jobs)) {
            out.add(Component.translatable("gui.luna_npc.create_npc.bodyguard_retreat_warning"));
        }
        return out;
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }

    private int rowY(int row) {
        return panelY() + CONTENT_Y + row * ROW;
    }

    private int controlWidth() {
        return PANEL_WIDTH - CONTROL_X - 12;
    }

    @Override
    protected void init() {
        int pageWidth = (PANEL_WIDTH - 24 - 8) / 3;
        int x = panelX() + 12;
        for (Page candidate : Page.values()) {
            Button tab = addRenderableWidget(Button.builder(Component.translatable(
                    "gui.luna_npc.edit_bodyguard.page." + candidate.name().toLowerCase(java.util.Locale.ROOT)), button -> {
                page = candidate;
                rebuildWidgets();
            }).bounds(x + candidate.ordinal() * (pageWidth + 4), panelY() + PAGES_Y, pageWidth, 20).build());
            tab.active = candidate != page;
        }
        switch (page) {
            case PROTECT -> buildProtectPage();
            case FOLLOW -> buildFollowPage();
            case DEFEND -> buildDefendPage();
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 50, panelY() + PANEL_HEIGHT - 26, 100, 20).build());
    }

    private void buildProtectPage() {
        int x = panelX() + CONTROL_X;
        addRenderableWidget(CycleButton.<NpcBodyguardSource>builder(value -> Component.translatable(
                        "gui.luna_npc.edit_bodyguard.source." + value.getSerializedName()), source)
                .withValues(NpcBodyguardSource.values())
                .displayOnlyValue()
                .withTooltip(value -> Tooltip.create(Component.translatable("gui.luna_npc.edit_bodyguard.source.tooltip")))
                .create(x, rowY(0), controlWidth(), 20, Component.empty(), (button, value) -> {
                    source = value;
                    rebuildWidgets();
                }));
        slider(x, rowY(1), controlWidth(), 1, NpcValidation.MAX_BODYGUARD_RANGE, range, " blocks",
                value -> range = value, "gui.luna_npc.edit_bodyguard.range.tooltip");
        if (source == NpcBodyguardSource.TARGETS) {
            buildTargetRows(x);
        } else {
            buildHireRows(x);
        }
    }

    private void buildTargetRows(int x) {
        addRenderableWidget(Button.builder(pickLabel(players.size()), button -> pickPlayers())
                .bounds(x, rowY(2), controlWidth(), 20).build());
        addRenderableWidget(Button.builder(pickLabel(alliances.size()), button -> pickAlliances())
                .bounds(x, rowY(3), controlWidth(), 20)
                .tooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_bodyguard.alliances.tooltip"))).build());
        addRenderableWidget(Button.builder(pickLabel(npcs.size()), button -> pickNpcs())
                .bounds(x, rowY(4), controlWidth(), 20)
                .tooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_bodyguard.npcs.tooltip"))).build());
        EditBox lidField = addRenderableWidget(new EditBox(this.font, x, rowY(5), controlWidth() - 44, 20,
                Component.translatable("gui.luna_npc.edit_bodyguard.add_lid")));
        lidField.setMaxLength(10);
        lidField.setHint(Component.translatable("gui.luna_npc.edit_bodyguard.add_lid"));
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            String digits = lidField.getValue().replaceAll("[^0-9]", "");
            if (!digits.isEmpty() && npcs.size() < NpcValidation.MAX_BODYGUARD_PROTECTED) {
                npcs.add(Long.parseLong(digits));
                rebuildWidgets();
            }
        }).bounds(x + controlWidth() - 40, rowY(5), 40, 20).build());
    }

    private void buildHireRows(int x) {
        for (int index = 0; index < COST_SLOTS; index++) {
            int slotIndex = index;
            int slotX = x + index * SLOT_PITCH;
            costSlots[index] = addRenderableWidget(new ItemSlotButton(slotX, rowY(2), this.title, event -> {
                if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    costIds[slotIndex] = "";
                    refreshCostSlots();
                } else {
                    Minecraft.getInstance().setScreenAndShow(new ItemPickerScreen(this, null, itemId -> {
                        costIds[slotIndex] = itemId;
                        costCounts[slotIndex] = Math.max(1, costCounts[slotIndex]);
                    }));
                }
            }));
            numberField(slotX - 4, rowY(2) + 20, 26, costCounts[index], 2, NpcValidation.MAX_TRADE_SLOT_COUNT, value -> {
                costCounts[slotIndex] = Math.max(1, value);
                refreshCostSlots();
            });
        }
        refreshCostSlots();
        numberField(x, rowY(4), controlWidth(), hireSeconds, 6, NpcValidation.MAX_BODYGUARD_HIRE_SECONDS,
                value -> hireSeconds = value)
                .setTooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_bodyguard.hire_seconds.tooltip")));
    }

    private void buildFollowPage() {
        int x = panelX() + CONTROL_X;
        int width = controlWidth();
        slider(x, rowY(0), width, 1, NpcValidation.MAX_BODYGUARD_STAY_RADIUS, stayRadius, " blocks",
                value -> stayRadius = value, "gui.luna_npc.edit_bodyguard.stay_radius.tooltip");
        slider(x, rowY(1), width, 1, NpcValidation.MAX_BODYGUARD_WALK_WITHIN, walkWithin, " blocks",
                value -> walkWithin = value, "gui.luna_npc.edit_bodyguard.walk_within.tooltip");
        LabeledSlider teleport = addRenderableWidget(new LabeledSlider(x, rowY(2), width, 20, Component.empty(),
                0, NpcValidation.MAX_BODYGUARD_TELEPORT_DISTANCE, teleportDistance,
                value -> Math.round(value) == 0 ? Component.translatable("gui.luna_npc.edit_bodyguard.off").getString()
                        : Math.round(value) + " blocks",
                value -> teleportDistance = Math.round(value)));
        teleport.setTooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_bodyguard.teleport.tooltip")));
        addRenderableWidget(CycleButton.booleanBuilder(
                        Component.translatable("gui.luna_npc.edit_bodyguard.keep_after_death.keep"),
                        Component.translatable("gui.luna_npc.edit_bodyguard.keep_after_death.give_up"), keepAfterDeath)
                .displayOnlyValue()
                .withTooltip(value -> Tooltip.create(Component.translatable("gui.luna_npc.edit_bodyguard.keep_after_death.tooltip")))
                .create(x, rowY(3), width, 20, Component.empty(), (button, value) -> keepAfterDeath = value));
        addRenderableWidget(CycleButton.<NpcGiveUpAction>builder(value -> Component.translatable(
                        "gui.luna_npc.edit_bodyguard.give_up." + value.getSerializedName()), giveUp)
                .withValues(NpcGiveUpAction.values())
                .displayOnlyValue()
                .withTooltip(value -> Tooltip.create(Component.translatable("gui.luna_npc.edit_bodyguard.give_up.tooltip")))
                .create(x, rowY(4), width, 20, Component.empty(), (button, value) -> giveUp = value));
    }

    private void buildDefendPage() {
        int x = panelX() + CONTROL_X;
        int width = controlWidth();
        LabeledSlider defense = addRenderableWidget(new LabeledSlider(x, rowY(0), width, 20, Component.empty(),
                0, NpcValidation.MAX_BODYGUARD_DEFENSE_SECONDS, defenseSeconds,
                value -> Math.round(value) == 0
                        ? Component.translatable("gui.luna_npc.edit_bodyguard.until_dead").getString()
                        : Math.round(value) + "s",
                value -> defenseSeconds = Math.round(value)));
        defense.setTooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_bodyguard.defense.tooltip")));
        addRenderableWidget(toggle(x, rowY(1), assist, "gui.luna_npc.edit_bodyguard.assist.tooltip",
                value -> assist = value));
        addRenderableWidget(toggle(x, rowY(2), retreat, "gui.luna_npc.edit_bodyguard.retreat.tooltip", value -> {
            retreat = value;
            rebuildWidgets();
        }));
        LabeledSlider retreatSlider = slider(x, rowY(3), width, 1, NpcValidation.MAX_BODYGUARD_RETREAT_DISTANCE,
                retreatDistance, " blocks", value -> retreatDistance = value,
                "gui.luna_npc.edit_bodyguard.retreat_distance.tooltip");
        retreatSlider.active = retreat;
        addRenderableWidget(toggle(x, rowY(4), friendlyFire, "gui.luna_npc.edit_bodyguard.friendly_fire.tooltip",
                value -> friendlyFire = value));
    }

    private CycleButton<Boolean> toggle(int x, int y, boolean value, String tooltipKey, Consumer<Boolean> onChange) {
        return CycleButton.booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, value)
                .displayOnlyValue()
                .withTooltip(current -> Tooltip.create(Component.translatable(tooltipKey)))
                .create(x, y, controlWidth(), 20, Component.empty(), (button, changed) -> onChange.accept(changed));
    }

    private LabeledSlider slider(int x, int y, int width, int min, int max, int value, String unit,
                                 IntConsumer onChange, String tooltipKey) {
        LabeledSlider slider = addRenderableWidget(new LabeledSlider(x, y, width, 20, Component.empty(), min, max,
                value, current -> Math.round(current) + unit, current -> onChange.accept(Math.round(current))));
        slider.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return slider;
    }

    private EditBox numberField(int x, int y, int width, int initial, int maxLength, int max, IntConsumer onChange) {
        EditBox field = new EditBox(this.font, x, y, width, width < 40 ? 16 : 20, Component.empty());
        field.setMaxLength(maxLength);
        field.setValue(String.valueOf(initial));
        field.setResponder(value -> {
            String digits = CreateNpcScreen.digitsOnly(field, value);
            if (digits != null) {
                onChange.accept(Math.min(digits.isEmpty() ? 0 : Integer.parseInt(digits), max));
            }
        });
        return addRenderableWidget(field);
    }

    private void refreshCostSlots() {
        for (int index = 0; index < COST_SLOTS; index++) {
            if (costSlots[index] == null) {
                continue;
            }
            ItemStack stack = NpcEquipment.resolve(costIds[index], List.of(), ClientRegistries.lookup());
            if (!stack.isEmpty()) {
                stack.setCount(costCounts[index]);
                costSlots[index].setStack(stack);
                costSlots[index].setTooltip(Tooltip.create(stack.getHoverName()));
            } else {
                costSlots[index].setStack(ItemStack.EMPTY);
                costSlots[index].setTooltip(null);
            }
        }
    }

    private static Component pickLabel(int count) {
        return Component.translatable("gui.luna_npc.edit_bodyguard.pick", count);
    }

    private void pickPlayers() {
        Map<UUID, String> known = new LinkedHashMap<>(players);
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                known.putIfAbsent(info.getProfile().id(), info.getProfile().name());
            }
        }
        List<MultiSelectPickerScreen.Option> options = new ArrayList<>();
        known.forEach((id, name) -> options.add(new MultiSelectPickerScreen.Option(id.toString(),
                Component.literal(name.isEmpty() ? id.toString() : name), name + " " + id)));
        Set<String> selected = new LinkedHashSet<>();
        players.keySet().forEach(id -> selected.add(id.toString()));
        Minecraft.getInstance().setScreenAndShow(new MultiSelectPickerScreen(this,
                Component.translatable("gui.luna_npc.edit_bodyguard.players"),
                Component.translatable("gui.luna_npc.item_picker.search"), options, selected, picked -> {
                    players.clear();
                    for (String id : picked) {
                        if (players.size() < NpcValidation.MAX_BODYGUARD_PROTECTED) {
                            UUID uuid = UUID.fromString(id);
                            players.put(uuid, known.getOrDefault(uuid, ""));
                        }
                    }
                }));
    }

    private void pickAlliances() {
        List<MultiSelectPickerScreen.Option> options = new ArrayList<>();
        for (NpcAlliance alliance : ClientAlliances.all()) {
            options.add(new MultiSelectPickerScreen.Option(alliance.id(), Component.literal(alliance.name()),
                    alliance.name() + " " + alliance.id()));
        }
        Minecraft.getInstance().setScreenAndShow(new MultiSelectPickerScreen(this,
                Component.translatable("gui.luna_npc.edit_bodyguard.alliances"),
                Component.translatable("gui.luna_npc.item_picker.search"), options, alliances, picked -> {
                    alliances.clear();
                    picked.stream().limit(NpcValidation.MAX_BODYGUARD_PROTECTED).forEach(alliances::add);
                }));
    }

    private void pickNpcs() {
        Map<Long, String> known = new LinkedHashMap<>();
        npcs.forEach(lunaId -> known.put(lunaId, ClientNpcCatalog.nameOf(lunaId)));
        for (NpcSummary npc : ClientNpcCatalog.all()) {
            known.putIfAbsent(npc.lunaId(), npc.name());
        }
        List<MultiSelectPickerScreen.Option> options = new ArrayList<>();
        known.forEach((lunaId, name) -> options.add(new MultiSelectPickerScreen.Option(String.valueOf(lunaId),
                Component.literal(name + "  (LID " + lunaId + ")"), name + " " + lunaId)));
        Set<String> selected = new LinkedHashSet<>();
        npcs.forEach(lunaId -> selected.add(String.valueOf(lunaId)));
        Minecraft.getInstance().setScreenAndShow(new MultiSelectPickerScreen(this,
                Component.translatable("gui.luna_npc.edit_bodyguard.npcs"),
                Component.translatable("gui.luna_npc.item_picker.search"), options, selected, picked -> {
                    npcs.clear();
                    picked.stream().limit(NpcValidation.MAX_BODYGUARD_PROTECTED).map(Long::parseLong).forEach(npcs::add);
                }));
    }

    private NpcBodyguardSettings build() {
        List<NpcPlayerRef> playerRefs = new ArrayList<>();
        players.forEach((id, name) -> playerRefs.add(new NpcPlayerRef(id, name)));
        List<NpcTradeStack> cost = new ArrayList<>();
        for (int index = 0; index < COST_SLOTS; index++) {
            if (costIds[index] != null && !costIds[index].isBlank()) {
                cost.add(new NpcTradeStack(costIds[index], Math.max(1, costCounts[index])));
            }
        }
        return new NpcBodyguardSettings(source,
                new NpcProtectTargets(playerRefs, List.copyOf(alliances), List.copyOf(npcs)),
                new NpcBodyguardHire(cost, hireSeconds), range, stayRadius, walkWithin, teleportDistance,
                defenseSeconds, assist, retreat, retreatDistance, friendlyFire, keepAfterDeath, giveUp);
    }

    @Override
    public void onClose() {
        onApply.accept(build());
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int x = panelX() + LABEL_X;
        int maxWidth = CONTROL_X - LABEL_X - 4;
        boolean targets = source == NpcBodyguardSource.TARGETS;
        String[] labels = switch (page) {
            case PROTECT -> targets
                    ? new String[] {"source", "range", "players", "alliances", "npcs", "add_lid"}
                    : new String[] {"source", "range", "cost", "", "hire_seconds"};
            case FOLLOW -> new String[] {"stay_radius", "walk_within", "teleport", "keep_after_death", "give_up"};
            case DEFEND -> new String[] {"defense", "assist", "retreat", "retreat_distance", "friendly_fire"};
        };
        for (int row = 0; row < labels.length; row++) {
            if (!labels[row].isEmpty()) {
                columnLabel(graphics, Component.translatable("gui.luna_npc.edit_bodyguard." + labels[row]),
                        x, rowY(row), maxWidth);
            }
        }
        int textWidth = PANEL_WIDTH - 24;
        if (page == Page.PROTECT) {
            Component hint = Component.translatable("gui.luna_npc.edit_bodyguard.source."
                    + source.getSerializedName() + ".hint");
            int hintY = rowY(labels.length) + 4;
            if (targets) {
                graphics.text(this.font, protectSummary(build().protect()), x, hintY, TITLE_COLOR);
                hintY += 14;
            }
            graphics.textWithWordWrap(this.font, hint, x, hintY, textWidth, LABEL_COLOR);
        }
        int bottom = panelY() + PANEL_HEIGHT - 32;
        for (Component warning : warnings(build())) {
            int y = bottom - this.font.wordWrapHeight(warning, textWidth);
            graphics.textWithWordWrap(this.font, warning, x, y, textWidth, WARNING_COLOR);
            bottom = y - 4;
        }
    }
}
