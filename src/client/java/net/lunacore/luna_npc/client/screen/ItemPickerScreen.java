package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.lunacore.luna_npc.client.util.FuzzySearch;
import net.lunacore.luna_npc.client.widget.ItemSlotButton;

// Creative-tab style item picker: a searchable grid of every registered item, optionally filtered
// to items equippable in one armor slot. Picking calls back with the item id; Clear picks "".
public class ItemPickerScreen extends LunaScreen {

    private static final int GRID_COLUMNS = 9;
    private static final int GRID_ROWS = 6;
    // Layout is vertically centered: search field, panel with the grid, pager, action buttons.
    private int contentTop() {
        int gridHeight = GRID_ROWS * ItemSlotButton.SIZE + 16;
        int total = 20 + 10 + gridHeight + 8 + 20 + 12 + 20;
        return Math.max(24, (this.height - total) / 2);
    }

    private int gridTop() {
        return contentTop() + 20 + 10 + 8;
    }

    private final Screen parent;
    private final EquipmentSlot armorSlotFilter;
    private final Consumer<String> onPicked;
    private final List<ItemStack> allStacks = new ArrayList<>();
    // One lowercase haystack per item: id + display name + tag ids, so searches like "armor" or
    // "sword" match the way the vanilla creative search does via item tags.
    private final Map<ItemStack, String> searchText = new HashMap<>();

    private final ItemSlotButton[] slotButtons = new ItemSlotButton[GRID_COLUMNS * GRID_ROWS];
    private List<ItemStack> filteredStacks = List.of();
    private EditBox searchField;
    private Button previousPageButton;
    private Button nextPageButton;
    private String query = "";
    private int page;
    private int pageCount = 1;

    public ItemPickerScreen(Screen parent, EquipmentSlot armorSlotFilter, Consumer<String> onPicked) {
        this(parent, armorSlotFilter, false, onPicked);
    }

    public ItemPickerScreen(Screen parent, EquipmentSlot armorSlotFilter, boolean blocksOnly,
                            Consumer<String> onPicked) {
        super(Component.translatable("gui.luna_npc.item_picker.title"));
        this.parent = parent;
        this.armorSlotFilter = armorSlotFilter;
        this.onPicked = onPicked;
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (stack.isEmpty() || !matchesArmorFilter(stack)
                    || (blocksOnly && !(item instanceof net.minecraft.world.item.BlockItem))) {
                continue;
            }
            allStacks.add(stack);
            StringBuilder haystack = new StringBuilder()
                    .append(BuiltInRegistries.ITEM.getKey(item)).append(' ')
                    .append(stack.getHoverName().getString());
            item.builtInRegistryHolder().tags()
                    .forEach(tag -> haystack.append(' ').append(tag.location()));
            searchText.put(stack, haystack.toString());
        }
    }

    private boolean matchesArmorFilter(ItemStack stack) {
        if (armorSlotFilter == null) {
            return true;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == armorSlotFilter;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int gridLeft = centerX - GRID_COLUMNS * ItemSlotButton.SIZE / 2;

        searchField = addRenderableWidget(new EditBox(this.font, centerX - 81, contentTop(), 162, 20, this.title));
        searchField.setMaxLength(64);
        searchField.setValue(query);
        searchField.setHint(Component.translatable("gui.luna_npc.item_picker.search"));
        searchField.setResponder(value -> {
            query = value;
            page = 0;
            updateGrid();
        });

        for (int index = 0; index < slotButtons.length; index++) {
            int slotIndex = index;
            int x = gridLeft + (index % GRID_COLUMNS) * ItemSlotButton.SIZE;
            int y = gridTop() + (index / GRID_COLUMNS) * ItemSlotButton.SIZE;
            slotButtons[index] = addRenderableWidget(new ItemSlotButton(x, y, this.title,
                    event -> pickSlot(slotIndex)));
        }

        int pagerY = gridTop() + GRID_ROWS * ItemSlotButton.SIZE + 16;
        previousPageButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            page--;
            updateGrid();
        }).bounds(gridLeft, pagerY, 20, 20).build());
        nextPageButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            page++;
            updateGrid();
        }).bounds(gridLeft + GRID_COLUMNS * ItemSlotButton.SIZE - 20, pagerY, 20, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.item_picker.clear"), button -> {
            onPicked.accept("");
            onClose();
        }).bounds(centerX - 102, pagerY + 32, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX + 2, pagerY + 32, 100, 20).build());

        updateGrid();
    }

    private void updateGrid() {
        if (query.isBlank()) {
            filteredStacks = allStacks;
        } else {
            record Scored(ItemStack stack, int score) {
            }
            filteredStacks = allStacks.stream()
                    .map(stack -> new Scored(stack, FuzzySearch.score(query, searchText.get(stack))))
                    .filter(scored -> scored.score() >= 0)
                    .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                    .map(Scored::stack)
                    .toList();
        }
        int perPage = slotButtons.length;
        pageCount = Math.max(1, (filteredStacks.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pageCount - 1);
        int start = page * perPage;
        for (int index = 0; index < perPage; index++) {
            int stackIndex = start + index;
            boolean hasStack = stackIndex < filteredStacks.size();
            slotButtons[index].visible = hasStack;
            ItemStack stack = hasStack ? filteredStacks.get(stackIndex) : ItemStack.EMPTY;
            slotButtons[index].setStack(stack);
            slotButtons[index].setTooltip(hasStack ? Tooltip.create(stack.getHoverName()) : null);
        }
        previousPageButton.active = page > 0;
        nextPageButton.active = page < pageCount - 1;
        // Page position lives in the pager tooltips instead of a printed label.
        Tooltip pageTooltip = Tooltip.create(
                Component.translatable("gui.luna_npc.npc_list.page", page + 1, pageCount));
        previousPageButton.setTooltip(pageTooltip);
        nextPageButton.setTooltip(pageTooltip);
    }

    private void pickSlot(int slotIndex) {
        int stackIndex = page * slotButtons.length + slotIndex;
        if (stackIndex < filteredStacks.size()) {
            onPicked.accept(BuiltInRegistries.ITEM.getKey(filteredStacks.get(stackIndex).getItem()).toString());
            onClose();
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        int gridLeft = this.width / 2 - GRID_COLUMNS * ItemSlotButton.SIZE / 2;
        // One card behind the search field and the item grid below it.
        int top = contentTop() - 8;
        int bottom = gridTop() + GRID_ROWS * ItemSlotButton.SIZE + 8;
        panel(graphics, gridLeft - 8, top, GRID_COLUMNS * ItemSlotButton.SIZE + 16, bottom - top);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
    }
}
