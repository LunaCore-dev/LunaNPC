package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.lunacore.luna_npc.client.widget.LabeledSlider;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.npc.NpcEquipment;
import net.lunacore.luna_npc.api.NpcItemEnchant;

// Edits the enchantments on one equipped item: the item sits on its own panel in the middle, the
// list of enchantments valid for it runs down the left, and the selected enchantment's level
// control lives on the right (like TargetEffectScreen). Only vanilla enchantments the item
// actually supports are offered, but their level may climb past the vanilla maximum on purpose
// (the creator owns balance, per CLAUDE.md) — a gold note flags when it does. Save writes back every
// enchantment with a level of at least one; Cancel and Escape return without saving.
public class EnchantItemScreen extends LunaScreen {

    private static final int TOP = 54;
    private static final int CONTENT_HEIGHT = 160;
    private static final int LIST_WIDTH = 158;
    private static final int CENTER_WIDTH = 76;
    private static final int DETAIL_WIDTH = 150;
    private static final int GAP = 8;
    private static final int ROW_HEIGHT = 18;
    private static final float ITEM_SCALE = 2.5F;
    // Red, so a level pushed past the vanilla maximum reads as a deliberate warning.
    private static final int WARNING_COLOR = 0xFFE33B3B;
    private static final ItemStack ENCHANT_ICON = new ItemStack(Items.ENCHANTED_BOOK);
    private static final SimpleSelectionList.IconRenderer ENCHANT_ICON_RENDERER =
            (graphics, x, yMiddle) -> graphics.item(ENCHANT_ICON, x, yMiddle - 8);

    private final Screen parent;
    private final ItemStack stack;
    private final Consumer<List<NpcItemEnchant>> onSave;
    // Insertion order is the shown order (sorted by name); values are the enchantment holders.
    private final LinkedHashMap<String, Holder<Enchantment>> validById = new LinkedHashMap<>();
    // The live level per enchantment id (0 = not applied).
    private final Map<String, Integer> levels = new HashMap<>();

    private SimpleSelectionList enchantList;
    private LabeledSlider levelSlider;
    private String selectedId = "";

    public EnchantItemScreen(Screen parent, String itemId, List<NpcItemEnchant> current,
                             Consumer<List<NpcItemEnchant>> onSave) {
        super(Component.translatable("gui.luna_npc.enchant.title"));
        this.parent = parent;
        this.onSave = onSave;
        this.stack = NpcEquipment.resolve(itemId);

        Map<String, Integer> seed = new HashMap<>();
        for (NpcItemEnchant enchant : current) {
            seed.put(enchant.enchantId(), enchant.level());
        }
        HolderLookup.RegistryLookup<Enchantment> lookup = enchantmentLookup();
        if (lookup != null && !stack.isEmpty()) {
            List<Holder.Reference<Enchantment>> found = lookup.listElements()
                    .filter(holder -> holder.value().isSupportedItem(stack))
                    .sorted(Comparator.comparing(
                            (Holder.Reference<Enchantment> holder) -> holder.value().description().getString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
            for (Holder.Reference<Enchantment> holder : found) {
                String id = holder.key().identifier().toString();
                validById.put(id, holder);
                levels.put(id, clampLevel(seed.getOrDefault(id, 0)));
            }
        }
        // Land on an already-applied enchantment first, otherwise the first offered one, so the
        // level control on the right is populated on open.
        for (String id : validById.keySet()) {
            if (levels.getOrDefault(id, 0) >= 1) {
                selectedId = id;
                break;
            }
        }
        if (selectedId.isEmpty() && !validById.isEmpty()) {
            selectedId = validById.keySet().iterator().next();
        }
    }

    private static HolderLookup.RegistryLookup<Enchantment> enchantmentLookup() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            return minecraft.getConnection().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        }
        if (minecraft.level != null) {
            return minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        }
        return null;
    }

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(NpcItemEnchant.MAX_LEVEL, level));
    }

    private int listX() {
        return this.width / 2 - (LIST_WIDTH + GAP + CENTER_WIDTH + GAP + DETAIL_WIDTH) / 2;
    }

    private int centerX() {
        return listX() + LIST_WIDTH + GAP;
    }

    private int detailX() {
        return centerX() + CENTER_WIDTH + GAP;
    }

    @Override
    protected void init() {
        enchantList = addRenderableWidget(new SimpleSelectionList(LIST_WIDTH, CONTENT_HEIGHT, TOP, ROW_HEIGHT));
        enchantList.setX(listX());

        int controlX = detailX() + 8;
        levelSlider = addRenderableWidget(new LabeledSlider(controlX, TOP + 38, DETAIL_WIDTH - 16, 20,
                Component.empty(), 0, NpcItemEnchant.MAX_LEVEL, currentLevel(selectedId),
                value -> String.valueOf(Math.round(value)), this::onLevelChanged));

        int bottomY = TOP + CONTENT_HEIGHT + 12;
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.enchant.save"),
                button -> save()).bounds(this.width / 2 - 102, bottomY, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(this.width / 2 + 2, bottomY, 100, 20).build());

        rebuildList();
        refreshDetail();
    }

    private void rebuildList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (Map.Entry<String, Holder<Enchantment>> holder : validById.entrySet()) {
            String id = holder.getKey();
            // The row colour and ✔/• marker read the level live, so changing it never rebuilds the
            // list (which would fight the level slider and reset the scroll).
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(id,
                    holder.getValue().value().description().copy(), this::onEnchantSelected,
                    ENCHANT_ICON_RENDERER, () -> levels.getOrDefault(id, 0) >= 1);
            entries.add(entry);
            if (id.equals(selectedId)) {
                selected = entry;
            }
        }
        enchantList.fill(entries, selected);
    }

    private void onEnchantSelected(String id) {
        selectedId = id;
        refreshDetail();
    }

    private void onLevelChanged(float value) {
        if (validById.containsKey(selectedId)) {
            levels.put(selectedId, clampLevel(Math.round(value)));
        }
    }

    private void refreshDetail() {
        boolean hasSelection = validById.containsKey(selectedId);
        levelSlider.visible = hasSelection;
        if (hasSelection) {
            levelSlider.setValue(currentLevel(selectedId));
        }
    }

    private int currentLevel(String id) {
        return levels.getOrDefault(id, 0);
    }

    // True when any applied enchantment is set past its own vanilla maximum level.
    private boolean anyAboveVanilla() {
        for (Map.Entry<String, Holder<Enchantment>> entry : validById.entrySet()) {
            if (levels.getOrDefault(entry.getKey(), 0) > entry.getValue().value().getMaxLevel()) {
                return true;
            }
        }
        return false;
    }

    private void save() {
        List<NpcItemEnchant> result = new ArrayList<>();
        for (String id : validById.keySet()) {
            int level = levels.getOrDefault(id, 0);
            if (level >= 1) {
                result.add(new NpcItemEnchant(id, level));
            }
        }
        onSave.accept(result);
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        panel(graphics, listX(), TOP, LIST_WIDTH, CONTENT_HEIGHT);
        panel(graphics, centerX(), TOP, CENTER_WIDTH, CONTENT_HEIGHT);
        Component detailTitle = validById.containsKey(selectedId)
                ? validById.get(selectedId).value().description() : Component.empty();
        titledPanel(graphics, detailX(), TOP, DETAIL_WIDTH, CONTENT_HEIGHT, detailTitle);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);

        // A persistent warning under the title so an over-vanilla item reads at a glance, without
        // having to select the offending enchantment.
        if (anyAboveVanilla()) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.enchant.above_vanilla_any"),
                    this.width / 2, TOP - 14, WARNING_COLOR);
        }

        if (!stack.isEmpty()) {
            int cx = centerX() + CENTER_WIDTH / 2;
            int cy = TOP + CONTENT_HEIGHT / 2;
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx, cy);
            graphics.pose().scale(ITEM_SCALE);
            graphics.item(stack, -8, -8);
            graphics.pose().popMatrix();
        }

        if (validById.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.enchant.none"),
                    listX() + LIST_WIDTH / 2, TOP + CONTENT_HEIGHT / 2 - 4, LABEL_COLOR);
            return;
        }
        if (validById.containsKey(selectedId)) {
            int controlX = detailX() + 8;
            graphics.text(this.font, Component.translatable("gui.luna_npc.enchant.level"),
                    controlX, TOP + 26, LABEL_COLOR);
            int level = currentLevel(selectedId);
            int vanillaMax = validById.get(selectedId).value().getMaxLevel();
            if (level > vanillaMax) {
                graphics.text(this.font, Component.translatable("gui.luna_npc.enchant.above_vanilla"),
                        controlX, TOP + 64, WARNING_COLOR);
            }
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
