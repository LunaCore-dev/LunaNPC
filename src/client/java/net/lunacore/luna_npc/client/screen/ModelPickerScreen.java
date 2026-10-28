package net.lunacore.luna_npc.client.screen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.lunacore.luna_npc.api.NpcSkin;
import net.lunacore.luna_npc.api.NpcSkinArms;
import net.lunacore.luna_npc.client.ClientSkins;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.network.EditSkinPayload;
import net.lunacore.luna_npc.client.render.CustomModelStore;
import net.lunacore.luna_npc.client.render.NpcCustomSkins;
import net.lunacore.luna_npc.entity.LunaNpcEntities;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcModelCatalog;
import net.lunacore.luna_npc.api.NpcModelSettings;
import net.lunacore.luna_npc.npc.NpcValidation;

// Picks an NPC's model type and skin, with the part-stretch sliders and a live preview. Imports land on
// this PC only; under the player type, Download Skin fetches a skin through the server, which keeps it with
// the world and sends it to everyone, and Skin Settings renames it or changes its arms (both popups).
public class ModelPickerScreen extends LunaScreen {

    private static final int LIST_TOP = 28;
    private static final int TYPE_LIST_WIDTH = 110;
    private static final int MODEL_LIST_WIDTH = 140;
    private static final Component LABEL_PREVIEW = Component.translatable("gui.luna_npc.create_npc.preview");
    // The preview card's bottom gap: the sliders, plus the two skin buttons under it for the Player type.
    private static final int PREVIEW_GAP = 124;
    // The preview's entity box, from the screen centre; its card (6 px wider each side) leaves a 5 px gap to
    // the lists' card and ends flush with the Import buttons below.
    private static final int PREVIEW_LEFT = 92;
    private static final int PREVIEW_RIGHT = 210;
    private static final int PREVIEW_GAP_WITH_SKIN_BUTTONS = 172;

    private final Screen parent;
    private final Consumer<NpcModelSettings> onSelected;

    private SimpleSelectionList typeList;
    private SimpleSelectionList modelList;
    private final List<AbstractSliderButton> partSliders = new ArrayList<>();
    private Component importStatus = Component.empty();

    private String modelType;
    private String modelId;
    private float modelSize;
    private float headScale;
    private float bodyScale;
    private float leftArmScale;
    private float rightArmScale;
    private float leftLegScale;
    private float rightLegScale;
    private float customWidth;
    private float customHeight;
    private float customEyeHeight;
    private Button removeButton;
    private Button skinSettingsButton;
    private boolean confirmingRemove;
    private NpcEntity previewEntity;

    public ModelPickerScreen(Screen parent, NpcModelSettings current, Consumer<NpcModelSettings> onSelected) {
        super(Component.translatable("gui.luna_npc.model_picker.title"));
        this.parent = parent;
        this.onSelected = onSelected;
        this.modelType = current.modelType();
        this.modelId = current.modelId();
        this.modelSize = current.modelSize();
        this.headScale = current.headScale();
        this.bodyScale = current.bodyScale();
        this.leftArmScale = current.leftArmScale();
        this.rightArmScale = current.rightArmScale();
        this.leftLegScale = current.leftLegScale();
        this.rightLegScale = current.rightLegScale();
        this.customWidth = current.customWidth();
        this.customHeight = current.customHeight();
        this.customEyeHeight = current.customEyeHeight();
    }

    private NpcModelSettings currentSettings() {
        return new NpcModelSettings(modelType, modelId, modelSize, headScale, bodyScale,
                leftArmScale, rightArmScale, leftLegScale, rightLegScale, customWidth, customHeight, customEyeHeight);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int listsLeft = centerX - 180;
        int listHeight = this.height - LIST_TOP - 118;

        typeList = addRenderableWidget(new SimpleSelectionList(TYPE_LIST_WIDTH, listHeight, LIST_TOP));
        typeList.setX(listsLeft);
        modelList = addRenderableWidget(new SimpleSelectionList(MODEL_LIST_WIDTH, listHeight, LIST_TOP));
        modelList.setX(listsLeft + TYPE_LIST_WIDTH + 6);

        int slidersTop = this.height - 110;
        partSliders.clear();
        addSlider(centerX - 180, slidersTop, "gui.luna_npc.model_picker.size",
                NpcValidation.MIN_MODEL_SIZE, NpcValidation.MAX_MODEL_SIZE, modelSize, value -> modelSize = value, false);
        addSlider(centerX - 180, slidersTop + 24, "gui.luna_npc.model_picker.head",
                NpcValidation.MIN_LIMB_STRETCH, NpcValidation.MAX_LIMB_STRETCH, headScale, value -> headScale = value, true);
        addSlider(centerX - 180, slidersTop + 48, "gui.luna_npc.model_picker.body",
                NpcValidation.MIN_LIMB_STRETCH, NpcValidation.MAX_LIMB_STRETCH, bodyScale, value -> bodyScale = value, true);
        addSlider(centerX - 180, slidersTop + 72, "gui.luna_npc.model_picker.left_arm",
                NpcValidation.MIN_LIMB_STRETCH, NpcValidation.MAX_LIMB_STRETCH, leftArmScale, value -> leftArmScale = value, true);
        addSlider(centerX - 22, slidersTop, "gui.luna_npc.model_picker.right_arm",
                NpcValidation.MIN_LIMB_STRETCH, NpcValidation.MAX_LIMB_STRETCH, rightArmScale, value -> rightArmScale = value, true);
        addSlider(centerX - 22, slidersTop + 24, "gui.luna_npc.model_picker.left_leg",
                NpcValidation.MIN_LIMB_STRETCH, NpcValidation.MAX_LIMB_STRETCH, leftLegScale, value -> leftLegScale = value, true);
        addSlider(centerX - 22, slidersTop + 48, "gui.luna_npc.model_picker.right_leg",
                NpcValidation.MIN_LIMB_STRETCH, NpcValidation.MAX_LIMB_STRETCH, rightLegScale, value -> rightLegScale = value, true);

        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.model_picker.import_model"),
                        button -> openImportModel())
                .bounds(centerX + 136, slidersTop, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.model_picker.import_texture"),
                        button -> openImportTexture())
                .bounds(centerX + 136, slidersTop + 24, 80, 20).build());
        removeButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.model_picker.remove"),
                        button -> toggleRemove())
                .bounds(centerX + 136, slidersTop + 48, 80, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            onSelected.accept(currentSettings());
            onClose();
        }).bounds(centerX + 136, slidersTop + 72, 80, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX - 22, slidersTop + 72, 150, 20).build());

        if (previewEntity == null && Minecraft.getInstance().level != null) {
            previewEntity = new NpcEntity(LunaNpcEntities.NPC, Minecraft.getInstance().level);
            // Entity ids are only assigned when an entity joins a world; the renderer requires one.
            previewEntity.setId(-1);
        }
        skinSettingsButton = null;
        if (isPlayerType()) {
            int buttonsY = this.height - PREVIEW_GAP_WITH_SKIN_BUTTONS + 10;
            addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.model_picker.download_skin"),
                            button -> Minecraft.getInstance().setScreenAndShow(new DownloadSkinScreen(this, this::selectDownloaded)))
                    .tooltip(Tooltip.create(Component.translatable("gui.luna_npc.model_picker.download_skin.tooltip")))
                    .bounds(centerX + PREVIEW_LEFT - 6, buttonsY, PREVIEW_RIGHT - PREVIEW_LEFT + 12, 20).build());
            skinSettingsButton = addRenderableWidget(Button.builder(
                            Component.translatable("gui.luna_npc.model_picker.skin_settings"), button -> openSkinSettings())
                    .tooltip(Tooltip.create(Component.translatable("gui.luna_npc.model_picker.skin_settings.tooltip")))
                    .bounds(centerX + PREVIEW_LEFT - 6, buttonsY + 24, PREVIEW_RIGHT - PREVIEW_LEFT + 12, 20).build());
        }
        rebuildTypeList();
        rebuildModelList();
        updatePreviewEntity();
    }

    private boolean isPlayerType() {
        return NpcSkin.MODEL_TYPE.equals(modelType);
    }

    // The Download Skin popup's result: wear the new skin (the picker re-inits when the popup closes).
    private void selectDownloaded(String skinId) {
        modelType = NpcSkin.MODEL_TYPE;
        modelId = skinId;
        clearCustomBox();
    }

    private void openSkinSettings() {
        NpcSkin skin = ClientSkins.byId(modelId);
        if (skin != null) {
            Minecraft.getInstance().setScreenAndShow(new EditSkinScreen(this, skin));
        }
    }

    private int previewBottom() {
        return this.height - (isPlayerType() ? PREVIEW_GAP_WITH_SKIN_BUTTONS : PREVIEW_GAP);
    }

    private void addSlider(int x, int y, String labelKey, float min, float max, float initial,
                           Consumer<Float> onChange, boolean partSlider) {
        SettingSlider slider = addRenderableWidget(new SettingSlider(x, y, 150, 20, labelKey, min, max, initial,
                value -> {
                    onChange.accept(value);
                    updatePreviewEntity();
                }));
        if (partSlider) {
            partSliders.add(slider);
        }
    }

    private void rebuildTypeList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (NpcModelCatalog.NpcModelType type : NpcModelCatalog.TYPES) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(type.id(),
                    Component.translatable("gui.luna_npc.model_type." + type.id()), this::selectType);
            entries.add(entry);
            if (type.id().equals(modelType)) {
                selected = entry;
            }
        }
        for (String category : CustomModelStore.listCategories()) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(category,
                    Component.literal(CustomModelStore.displayName(category)), this::selectType);
            entries.add(entry);
            if (category.equals(modelType)) {
                selected = entry;
            }
        }
        typeList.fill(entries, selected);
    }

    private void rebuildModelList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        if (NpcModelCatalog.isCustomCategory(modelType)) {
            for (String variant : CustomModelStore.variantsOf(modelType)) {
                SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(variant,
                        Component.literal(variant), this::selectModel);
                entries.add(entry);
                if (variant.equals(modelId)) {
                    selected = entry;
                }
            }
            modelList.fill(entries, selected);
            updateSliderState();
            updateRemoveState();
            return;
        }
        List<String> catalogIds = NpcModelCatalog.byId(modelType)
                .map(NpcModelCatalog.NpcModelType::modelIds).orElse(List.of());
        for (String id : catalogIds) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(id, Component.translatable("gui.luna_npc.model." + id), this::selectModel);
            entries.add(entry);
            if (id.equals(modelId)) {
                selected = entry;
            }
        }
        // Downloaded skins (the server's, under their renamable name), then imported skin files from
        // LunaNPC/assets/npc_models/<type>/ under their file name.
        if (isPlayerType()) {
            for (NpcSkin skin : ClientSkins.all()) {
                SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(skin.id(),
                        Component.literal(skin.name()), this::selectModel);
                entries.add(entry);
                if (skin.id().equals(modelId)) {
                    selected = entry;
                }
            }
        }
        for (String id : NpcCustomSkins.scan(modelType)) {
            if (catalogIds.contains(id)) {
                continue;
            }
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(id, Component.literal(id), this::selectModel);
            entries.add(entry);
            if (id.equals(modelId)) {
                selected = entry;
            }
        }
        modelList.fill(entries, selected);
        updateSliderState();
        updateRemoveState();
    }

    private void selectType(String typeId) {
        if (typeId.equals(modelType)) {
            return;
        }
        boolean wasPlayer = isPlayerType();
        modelType = typeId;
        if (NpcModelCatalog.isCustomCategory(typeId)) {
            List<String> variants = CustomModelStore.variantsOf(typeId);
            modelId = variants.isEmpty() ? "default" : variants.getFirst();
            CustomModelStore.meta(typeId).ifPresentOrElse(meta -> {
                customWidth = meta.width();
                customHeight = meta.height();
                customEyeHeight = meta.eyeHeight();
            }, this::clearCustomBox);
        } else {
            modelId = NpcModelCatalog.byId(typeId).map(type -> type.modelIds().getFirst()).orElse(modelId);
            clearCustomBox();
        }
        if (wasPlayer != isPlayerType()) {
            rebuildWidgets();
            return;
        }
        rebuildModelList();
        updatePreviewEntity();
    }

    private void clearCustomBox() {
        customWidth = 0.0F;
        customHeight = 0.0F;
        customEyeHeight = 0.0F;
    }

    private void selectModel(String id) {
        modelId = id;
        updateRemoveState();
        updatePreviewEntity();
    }

    private void updateSliderState() {
        boolean stretchable = NpcModelCatalog.isStretchable(modelType);
        for (AbstractSliderButton slider : partSliders) {
            slider.active = stretchable;
        }
    }

    // Imports a texture variant into the selected category — an imported skin for a built-in type, or a
    // new PNG variant for a custom category (the "same structure, new texture" path).
    private void openImportTexture() {
        String targetType = modelType;
        boolean custom = NpcModelCatalog.isCustomCategory(targetType);
        importStatus = Component.empty();
        NpcCustomSkins.openPngFileDialog()
                .thenAcceptAsync(chosen -> {
                    if (chosen == null) {
                        return;
                    }
                    Optional<String> result = custom
                            ? CustomModelStore.importVariant(targetType, Path.of(chosen))
                            : NpcCustomSkins.importFile(targetType, Path.of(chosen));
                    result.ifPresentOrElse(id -> {
                        if (targetType.equals(modelType)) {
                            modelId = id;
                            rebuildModelList();
                            updatePreviewEntity();
                        }
                    }, () -> importStatus = Component.translatable("gui.luna_npc.model_picker.import_failed"));
                }, Minecraft.getInstance());
    }

    // Imports a whole custom model: pick the geometry (.geo.json / .bbmodel), then its texture (optional
    // for a .bbmodel that embeds one), then name the new category. Dialogs open on the client thread; the
    // embedded-texture probe reads the file, so it runs off-thread in between.
    private void openImportModel() {
        importStatus = Component.empty();
        CustomModelStore.openGeometryFileDialog()
                .thenComposeAsync(ModelPickerScreen::pickTextureFor, Minecraft.getInstance())
                .thenAcceptAsync(paths -> {
                    if (paths == null) {
                        return;
                    }
                    String texture = paths[1].isEmpty() ? null : paths[1];
                    Minecraft.getInstance().setScreenAndShow(new NameCategoryScreen(this,
                            defaultCategoryName(paths[0]), name -> finishImport(paths[0], texture, name)));
                }, Minecraft.getInstance());
    }

    // {geometry, texture or ""}, or null when the geometry pick was cancelled.
    private static CompletableFuture<String[]> pickTextureFor(String geometry) {
        if (geometry == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> CustomModelStore.hasEmbeddedTexture(Path.of(geometry)))
                .thenComposeAsync(embedded -> embedded
                        ? CompletableFuture.completedFuture(new String[]{geometry, ""})
                        : NpcCustomSkins.openPngFileDialog().thenApply(texture ->
                                new String[]{geometry, texture == null ? "" : texture}), Minecraft.getInstance());
    }

    private void finishImport(String geometry, String texture, String rawName) {
        CompletableFuture.supplyAsync(() -> {
            StringBuilder failure = new StringBuilder();
            Optional<CustomModelStore.CategoryMeta> meta = CustomModelStore.importModel(Path.of(geometry),
                    texture == null ? null : Path.of(texture), sanitizeCategory(rawName), rawName.trim(), failure);
            return new ImportOutcome(meta.orElse(null), failure.toString());
        }).thenAcceptAsync(outcome -> {
            if (outcome.meta() == null) {
                importStatus = Component.translatable(outcome.failureKey().isEmpty()
                        ? "gui.luna_npc.model_picker.import_failed" : outcome.failureKey());
                return;
            }
            modelType = outcome.meta().category();
            customWidth = outcome.meta().width();
            customHeight = outcome.meta().height();
            customEyeHeight = outcome.meta().eyeHeight();
            List<String> variants = CustomModelStore.variantsOf(modelType);
            modelId = variants.isEmpty() ? "default" : variants.getFirst();
            rebuildTypeList();
            rebuildModelList();
            updatePreviewEntity();
        }, Minecraft.getInstance());
    }

    // Vanilla types and their bundled skins are permanent; only imported content and downloaded skins can be
    // removed (a downloaded one for everyone on the server).
    private boolean isRemovable() {
        return NpcModelCatalog.isCustomCategory(modelType) || NpcCustomSkins.isImportedSkin(modelType, modelId)
                || ClientSkins.byId(modelId) != null && isPlayerType();
    }

    private void updateRemoveState() {
        if (removeButton == null) {
            return;
        }
        confirmingRemove = false;
        removeButton.setMessage(Component.translatable("gui.luna_npc.model_picker.remove"));
        removeButton.active = isRemovable();
        if (skinSettingsButton != null) {
            skinSettingsButton.active = ClientSkins.byId(modelId) != null;
        }
    }

    // First click arms the button, second click removes — a lightweight guard against a misclick wiping
    // an imported model with no confirm screen.
    private void toggleRemove() {
        if (!isRemovable()) {
            return;
        }
        if (!confirmingRemove) {
            confirmingRemove = true;
            removeButton.setMessage(Component.translatable("gui.luna_npc.model_picker.remove_confirm"));
            return;
        }
        performRemove();
    }

    private void performRemove() {
        if (NpcModelCatalog.isCustomCategory(modelType)) {
            boolean lastVariant = CustomModelStore.variantsOf(modelType).size() <= 1;
            CustomModelStore.removeVariant(modelType, modelId);
            if (lastVariant) {
                modelType = NpcDefaults.MODEL_TYPE;
                modelId = NpcDefaults.MODEL_ID;
                clearCustomBox();
            } else {
                List<String> variants = CustomModelStore.variantsOf(modelType);
                modelId = variants.isEmpty() ? "default" : variants.getFirst();
            }
        } else if (isPlayerType() && ClientSkins.byId(modelId) != null) {
            ClientPlayNetworking.send(new EditSkinPayload(modelId, "", NpcSkinArms.CLASSIC, true));
            ClientSkins.remove(modelId);
            modelId = NpcDefaults.MODEL_ID;
            rebuildWidgets();
            return;
        } else {
            NpcCustomSkins.removeSkin(modelType, modelId);
            modelId = NpcModelCatalog.byId(modelType).map(type -> type.modelIds().getFirst()).orElse(modelId);
        }
        rebuildTypeList();
        rebuildModelList();
        updatePreviewEntity();
    }

    private static String defaultCategoryName(String geometryPath) {
        String name = Path.of(geometryPath).getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.indexOf('.');
        return sanitizeCategory(dot < 0 ? name : name.substring(0, dot));
    }

    private static String sanitizeCategory(String raw) {
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
        if (cleaned.length() > NpcValidation.MAX_MODEL_CATEGORY_LENGTH) {
            cleaned = cleaned.substring(0, NpcValidation.MAX_MODEL_CATEGORY_LENGTH);
        }
        return cleaned;
    }

    private record ImportOutcome(CustomModelStore.CategoryMeta meta, String failureKey) {
    }

    private void updatePreviewEntity() {
        if (previewEntity != null) {
            previewEntity.applyModel(currentSettings());
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (typeList == null) {
            return;
        }
        // One card behind the type and model selection lists.
        int listsRight = modelList.getX() + modelList.getWidth();
        panel(graphics, typeList.getX() - 5, typeList.getY() - 5,
                listsRight - typeList.getX() + 10, typeList.getHeight() + 10);
        // Card behind the live model preview; the header sits above the entity box (see the render
        // bounds in extractRenderState) so it never covers the model.
        int centerX = this.width / 2;
        int previewLeft = centerX + PREVIEW_LEFT;
        int previewRight = centerX + PREVIEW_RIGHT;
        int previewBottom = previewBottom();
        titledPanel(graphics, previewLeft - 6, LIST_TOP - 16, previewRight - previewLeft + 12,
                previewBottom - (LIST_TOP - 16) + 6, LABEL_PREVIEW);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        graphics.centeredText(this.font, this.title, centerX, 10, 0xFFFFFFFF);
        graphics.centeredText(this.font, importStatus, centerX, this.height - 12, 0xFFFF5555);
        if (previewEntity != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics,
                    centerX + PREVIEW_LEFT, LIST_TOP, centerX + PREVIEW_RIGHT, previewBottom(), 34, 0.0625F,
                    mouseX, mouseY, previewEntity);
        }
    }

    private class SettingSlider extends AbstractSliderButton {

        private final String labelKey;
        private final float min;
        private final float max;
        private final Consumer<Float> onChange;

        SettingSlider(int x, int y, int width, int height, String labelKey, float min, float max,
                      float initial, Consumer<Float> onChange) {
            super(x, y, width, height, Component.empty(), (initial - min) / (max - min));
            this.labelKey = labelKey;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        private float currentValue() {
            return min + (float) value * (max - min);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(labelKey)
                    .append(Component.literal(String.format(": %.2f", currentValue()))));
        }

        @Override
        protected void applyValue() {
            onChange.accept(currentValue());
        }
    }
}
