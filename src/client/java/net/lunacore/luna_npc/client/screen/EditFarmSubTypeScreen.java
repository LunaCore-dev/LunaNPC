package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.lunacore.luna_npc.client.util.FuzzySearch;
import net.lunacore.luna_npc.job.NpcFarmController;
import net.lunacore.luna_npc.client.widget.LabeledSlider;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.api.NpcFarmSubType;
import net.lunacore.luna_npc.npc.NpcValidation;

// One weighted farming variant: its weight, the work radius, the deposit-chest search radius, and the
// crop whitelist. Farmable blocks (crops, nether wart, stem fruit) are read from the live block registry,
// modded ones included; an empty whitelist means "every crop". Hands the rebuilt variant back to the
// Jobs tab on close.
public class EditFarmSubTypeScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 276;
    private static final int CROP_TITLE_OFFSET = 130;
    private static final int SEARCH_OFFSET = 142;
    private static final int LIST_TOP_OFFSET = 162;
    private static final int LIST_HEIGHT = 76;

    private final LunaScreen parent;
    private final Consumer<NpcFarmSubType> onApply;
    private final Set<String> selectedCrops = new LinkedHashSet<>();
    private final List<CropOption> cropOptions = new ArrayList<>();
    private int weight;
    private int radius;
    private int chestRadius;
    private int capacity;
    private int idleDepositSeconds;
    private String query = "";

    private SimpleSelectionList cropList;

    private record CropOption(String id, Component label, String search) { }

    public EditFarmSubTypeScreen(LunaScreen parent, NpcFarmSubType subType,
                                 Consumer<NpcFarmSubType> onApply) {
        super(Component.translatable("gui.luna_npc.edit_farm.title"));
        this.parent = parent;
        this.onApply = onApply;
        this.weight = subType.weightPercent();
        this.radius = subType.radius();
        this.chestRadius = subType.chestRadius();
        this.capacity = subType.capacity();
        this.idleDepositSeconds = subType.idleDepositSeconds();
        this.selectedCrops.addAll(subType.cropBlockIds());
        for (Block block : BuiltInRegistries.BLOCK) {
            if (NpcFarmController.isFarmableBlock(block)) {
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                cropOptions.add(new CropOption(id, block.getName(), id + " " + block.getName().getString()));
            }
        }
        cropOptions.sort(Comparator.comparing(option -> option.label().getString().toLowerCase(Locale.ROOT)));
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }

    @Override
    protected void init() {
        int contentX = panelX() + 12;
        int top = panelY();
        int sliderWidth = PANEL_WIDTH - 24;

        LabeledSlider weightSlider = new LabeledSlider(contentX, top + 16, sliderWidth, 18,
                Component.translatable("gui.luna_npc.edit_subtype.weight"), 0,
                NpcValidation.JOB_WEIGHT_TOTAL_PERCENT, weight,
                value -> Math.round(value) + "%", value -> weight = Math.round(value));
        weightSlider.setTooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_subtype.weight.tooltip")));
        addRenderableWidget(weightSlider);

        LabeledSlider radiusSlider = new LabeledSlider(contentX, top + 40, sliderWidth, 16,
                Component.translatable("gui.luna_npc.edit_farm.radius"),
                NpcValidation.MIN_WANDER_RADIUS, NpcValidation.MAX_WANDER_RADIUS, radius,
                value -> String.valueOf(Math.round(value)), value -> radius = Math.round(value));
        radiusSlider.setTooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_farm.radius.tooltip")));
        addRenderableWidget(radiusSlider);

        LabeledSlider chestSlider = new LabeledSlider(contentX, top + 62, sliderWidth, 16,
                Component.translatable("gui.luna_npc.edit_farm.chest_radius"),
                1, NpcValidation.MAX_FARM_CHEST_RADIUS, chestRadius,
                value -> String.valueOf(Math.round(value)), value -> chestRadius = Math.round(value));
        chestSlider.setTooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_farm.chest_radius.tooltip")));
        addRenderableWidget(chestSlider);

        LabeledSlider capacitySlider = new LabeledSlider(contentX, top + 84, sliderWidth, 16,
                Component.translatable("gui.luna_npc.edit_farm.capacity"),
                0, NpcValidation.MAX_FARM_CAPACITY, capacity,
                value -> String.valueOf(Math.round(value)), value -> capacity = Math.round(value));
        capacitySlider.setTooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_farm.capacity.tooltip")));
        addRenderableWidget(capacitySlider);

        LabeledSlider idleSlider = new LabeledSlider(contentX, top + 106, sliderWidth, 16,
                Component.translatable("gui.luna_npc.edit_farm.idle_deposit"),
                0, NpcValidation.MAX_FARM_IDLE_SECONDS, idleDepositSeconds,
                value -> Math.round(value) + "s", value -> idleDepositSeconds = Math.round(value));
        idleSlider.setTooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_farm.idle_deposit.tooltip")));
        addRenderableWidget(idleSlider);

        EditBox searchField = new EditBox(this.font, contentX, top + SEARCH_OFFSET, sliderWidth, 16,
                Component.translatable("gui.luna_npc.item_picker.search"));
        searchField.setMaxLength(100);
        searchField.setHint(Component.translatable("gui.luna_npc.item_picker.search"));
        searchField.setResponder(value -> {
            query = value;
            rebuildList();
        });
        addRenderableWidget(searchField);

        cropList = addRenderableWidget(new SimpleSelectionList(sliderWidth, LIST_HEIGHT, top + LIST_TOP_OFFSET));
        cropList.setX(contentX);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 50, top + PANEL_HEIGHT - 28, 100, 20).build());

        rebuildList();
    }

    private void rebuildList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        for (CropOption option : cropOptions) {
            if (!FuzzySearch.matches(query, option.search())) {
                continue;
            }
            entries.add(new SimpleSelectionList.Entry(option.id(), option.label(),
                    this::toggleCrop, null, () -> selectedCrops.contains(option.id())));
        }
        cropList.fill(entries, null);
    }

    private void toggleCrop(String id) {
        if (!selectedCrops.remove(id)) {
            selectedCrops.add(id);
        }
    }

    @Override
    public void onClose() {
        onApply.accept(new NpcFarmSubType(weight, radius, chestRadius, capacity, idleDepositSeconds,
                new ArrayList<>(selectedCrops)));
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
        graphics.outline(panelX() + 12, panelY() + LIST_TOP_OFFSET, PANEL_WIDTH - 24, LIST_HEIGHT, PANEL_BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int contentX = panelX() + 12;
        int top = panelY();
        graphics.text(this.font, Component.translatable("gui.luna_npc.edit_farm.crops"),
                contentX, top + CROP_TITLE_OFFSET, SECTION_COLOR);
        if (selectedCrops.isEmpty()) {
            Component all = Component.translatable("gui.luna_npc.edit_farm.all_crops");
            graphics.text(this.font, all, panelX() + PANEL_WIDTH - 12 - this.font.width(all),
                    top + CROP_TITLE_OFFSET, LABEL_COLOR);
        }
    }
}
