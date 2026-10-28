package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.lunacore.luna_npc.client.widget.LabeledSlider;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.api.NpcAttackEffect;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcValidation;

// The Target Effect page of one attack category: everything the attack puts on its target.
// Operates directly on the combat screen's effect list, so Back simply returns with the changes
// in place. Potion effects have a duration and an amplifier; the enchantment-like effects
// (sharpness, knockback, flame, ...) only have their level.
public class TargetEffectScreen extends LunaScreen {

    private static final int LIST_TOP = 54;
    private static final int LIST_WIDTH = 190;
    private static final int INFO_WIDTH = 140;
    private static final int INFO_HEIGHT = 150;
    private static final int GAP = 8;
    private static final int ROW_HEIGHT = 18;
    private static final ItemStack ENCHANT_ICON = new ItemStack(Items.ENCHANTED_BOOK);

    private final Screen parent;
    private final List<NpcAttackEffect> effects;
    private final List<String> allowedEnchants;

    private SimpleSelectionList effectList;
    private EditBox durationField;
    private LabeledSlider amplifierSlider;
    private Button removeButton;
    private String selectedId = "";

    public TargetEffectScreen(Screen parent, Component categoryName, List<NpcAttackEffect> effects,
                              List<String> allowedEnchants) {
        super(Component.translatable("gui.luna_npc.target_effects.title").copy()
                .append(" - ").append(categoryName));
        this.parent = parent;
        this.effects = effects;
        this.allowedEnchants = allowedEnchants;
    }

    private int leftX() {
        return this.width / 2 - (LIST_WIDTH + GAP + INFO_WIDTH) / 2;
    }

    private int infoX() {
        return leftX() + LIST_WIDTH + GAP;
    }

    @Override
    protected void init() {
        effectList = addRenderableWidget(new SimpleSelectionList(LIST_WIDTH, INFO_HEIGHT, LIST_TOP, ROW_HEIGHT));
        effectList.setX(leftX());

        int controlX = infoX() + 8;
        durationField = addRenderableWidget(new EditBox(this.font, controlX, LIST_TOP + 32,
                INFO_WIDTH - 16, 18, Component.translatable("gui.luna_npc.target_effects.duration")));
        durationField.setMaxLength(7);
        durationField.setResponder(this::onDurationTyped);

        amplifierSlider = addRenderableWidget(new LabeledSlider(controlX, LIST_TOP + 72,
                INFO_WIDTH - 16, 18, Component.empty(), 0, NpcValidation.MAX_EFFECT_AMPLIFIER, 0,
                value -> String.valueOf(Math.round(value)), value -> updateSelected(effect ->
                new NpcAttackEffect(effect.effectId(), effect.durationSeconds(), Math.round(value)))));

        removeButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.luna_npc.target_effects.remove"), button -> {
                    effects.removeIf(effect -> effect.effectId().equals(selectedId));
                    selectedId = "";
                    rebuildList();
                }).bounds(controlX, LIST_TOP + 98, INFO_WIDTH - 16, 20).build());

        int bottomY = LIST_TOP + INFO_HEIGHT + 12;
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.target_effects.add"),
                button -> {
                    Set<String> existing = new HashSet<>();
                    effects.forEach(effect -> existing.add(effect.effectId()));
                    Minecraft.getInstance().setScreenAndShow(new EffectPickerScreen(this, existing,
                            allowedEnchants, effectId -> effects.add(new NpcAttackEffect(effectId,
                                    NpcDefaults.EFFECT_DURATION_SECONDS, 0))));
                }).bounds(this.width / 2 - 102, bottomY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.target_effects.back"),
                button -> onClose()).bounds(this.width / 2 + 2, bottomY, 100, 20).build());

        rebuildList();
    }

    private void rebuildList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (NpcAttackEffect effect : effects) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(effect.effectId(),
                    entryLabel(effect), this::onEffectSelected, iconFor(effect),
                    effect.isEnchantLike() ? SECTION_COLOR : 0xFFFFFFFF);
            entries.add(entry);
            if (effect.effectId().equals(selectedId)) {
                selected = entry;
            }
        }
        effectList.fill(entries, selected);
        if (selected == null) {
            selectedId = "";
        }
        refreshControls();
    }

    // The list icon mirrors the picker: the enchanted book for enchant-like effects, the potion
    // effect's own sprite for potions (null when the effect is unknown, so the row is text-only).
    private static SimpleSelectionList.IconRenderer iconFor(NpcAttackEffect effect) {
        if (effect.isEnchantLike()) {
            return (graphics, x, yMiddle) -> graphics.item(ENCHANT_ICON, x, yMiddle - 8);
        }
        Holder<MobEffect> holder = effectHolder(effect.effectId());
        if (holder == null) {
            return null;
        }
        return (graphics, x, yMiddle) -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                Hud.getMobEffectSprite(holder), x, yMiddle - 8, 16, 16);
    }

    private static Holder<MobEffect> effectHolder(String effectId) {
        Identifier parsed = Identifier.tryParse(effectId);
        if (parsed == null) {
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.getOptional(parsed)
                .map(BuiltInRegistries.MOB_EFFECT::wrapAsHolder)
                .orElse(null);
    }

    public static Component effectName(String effectId) {
        if (!effectId.contains(":")) {
            return Component.translatable("gui.luna_npc.effect." + effectId);
        }
        Identifier parsed = Identifier.tryParse(effectId);
        if (parsed != null) {
            var effect = BuiltInRegistries.MOB_EFFECT.getOptional(parsed).orElse(null);
            if (effect != null) {
                return effect.getDisplayName().copy();
            }
        }
        return Component.literal(effectId);
    }

    // "Wither 2 - 5.0s" for potions, "Knockback 3" for enchantment-likes.
    public static Component entryLabel(NpcAttackEffect effect) {
        var label = effectName(effect.effectId()).copy().append(" " + effect.amplifier());
        if (!effect.isEnchantLike()) {
            label.append(String.format(Locale.ROOT, " - %.1fs", effect.durationSeconds()));
        }
        return label;
    }

    private void onEffectSelected(String effectId) {
        selectedId = effectId;
        refreshControls();
        NpcAttackEffect selected = selectedEffect();
        if (selected != null) {
            durationField.setValue(String.format(Locale.ROOT, "%.1f", selected.durationSeconds()));
            amplifierSlider.setValue(selected.amplifier());
        }
    }

    private NpcAttackEffect selectedEffect() {
        for (NpcAttackEffect effect : effects) {
            if (effect.effectId().equals(selectedId)) {
                return effect;
            }
        }
        return null;
    }

    private void refreshControls() {
        NpcAttackEffect selected = selectedEffect();
        boolean hasSelection = selected != null;
        durationField.visible = hasSelection && !selected.isEnchantLike();
        durationField.setEditable(durationField.visible);
        amplifierSlider.visible = hasSelection;
        removeButton.visible = hasSelection;
    }

    private void onDurationTyped(String text) {
        String sanitized = text.replaceAll("[^0-9.]", "");
        if (!sanitized.equals(text)) {
            durationField.setValue(sanitized);
            return;
        }
        try {
            float seconds = Float.parseFloat(sanitized);
            if (seconds >= 0.0F && seconds <= NpcValidation.MAX_EFFECT_DURATION_SECONDS) {
                updateSelected(effect ->
                        new NpcAttackEffect(effect.effectId(), seconds, effect.amplifier()));
            }
        } catch (NumberFormatException incomplete) {
            // Mid-typing states like "." simply do not commit yet.
        }
    }

    // The list entry label refreshes on the next rebuild (add/remove/reopen); rebuilding here
    // would steal the focus from the control being used.
    private void updateSelected(java.util.function.UnaryOperator<NpcAttackEffect> change) {
        for (int index = 0; index < effects.size(); index++) {
            if (effects.get(index).effectId().equals(selectedId)) {
                effects.set(index, change.apply(effects.get(index)));
                return;
            }
        }
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        // The effect list needs its own opaque panel: SimpleSelectionList suppresses its background and
        // relies on one behind it (the CLAUDE.md UI rules), so without this the list read straight through to the world.
        panel(graphics, leftX() - 5, LIST_TOP - 5, LIST_WIDTH + 10, INFO_HEIGHT + 10);
        graphics.outline(leftX(), LIST_TOP, LIST_WIDTH, INFO_HEIGHT, PANEL_BORDER);
        titledPanel(graphics, infoX(), LIST_TOP, INFO_WIDTH, INFO_HEIGHT,
                selectedId.isEmpty() ? Component.empty() : effectName(selectedId));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        if (effects.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.target_effects.empty"),
                    leftX() + LIST_WIDTH / 2, LIST_TOP + 60, LABEL_COLOR);
        }
        NpcAttackEffect selected = selectedEffect();
        if (selected != null) {
            if (!selected.isEnchantLike()) {
                graphics.text(this.font, Component.translatable("gui.luna_npc.target_effects.duration"),
                        infoX() + 8, LIST_TOP + 22, LABEL_COLOR);
            }
            graphics.text(this.font, Component.translatable("gui.luna_npc.target_effects.amplifier"),
                    infoX() + 8, LIST_TOP + 62, LABEL_COLOR);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
