package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.lunacore.luna_npc.client.util.FuzzySearch;
import net.lunacore.luna_npc.npc.NpcValidation;

// Picks one target effect: the enchantment-like effects first (with the enchanted book icon),
// then every registered potion effect with its original transparent icon. Typing filters;
// clicking picks and returns. Effects already present in the category are hidden — one effect
// per category — and each category only offers its own enchants (melee vs ranged).
public class EffectPickerScreen extends LunaScreen {

    private static final int LIST_TOP = 54;
    private static final int LIST_WIDTH = 220;
    private static final net.minecraft.world.item.ItemStack ENCHANT_ICON =
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);

    private final Screen parent;
    private final Set<String> excludedIds;
    private final List<String> allowedEnchants;
    private final Consumer<String> onPicked;

    private EffectEntryList entryList;
    private EditBox searchField;
    private String query = "";

    public EffectPickerScreen(Screen parent, Set<String> excludedIds, List<String> allowedEnchants,
                              Consumer<String> onPicked) {
        super(Component.translatable("gui.luna_npc.effect_picker.title"));
        this.parent = parent;
        this.excludedIds = excludedIds;
        this.allowedEnchants = allowedEnchants;
        this.onPicked = onPicked;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        searchField = addRenderableWidget(new EditBox(this.font, centerX - 110, 28, 220, 20, this.title));
        searchField.setMaxLength(100);
        searchField.setValue(query);
        searchField.setResponder(value -> {
            query = value;
            rebuildList();
        });

        entryList = addRenderableWidget(new EffectEntryList(this.height - LIST_TOP - 40));
        entryList.setX(centerX - LIST_WIDTH / 2);

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX - 50, this.height - 28, 100, 20).build());

        rebuildList();
    }

    private void rebuildList() {
        record Scored(EffectEntry entry, int score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (String enchantId : allowedEnchants) {
            if (excludedIds.contains(enchantId)) {
                continue;
            }
            Component name = Component.translatable("gui.luna_npc.effect." + enchantId);
            int rank = FuzzySearch.score(query, name.getString(), enchantId);
            if (rank >= 0) {
                scored.add(new Scored(new EffectEntry(enchantId, name, null), rank));
            }
        }
        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            String effectId = String.valueOf(BuiltInRegistries.MOB_EFFECT.getKey(effect));
            if (excludedIds.contains(effectId)) {
                continue;
            }
            Component name = effect.getDisplayName().copy();
            int rank = FuzzySearch.score(query, name.getString(), effectId);
            if (rank >= 0) {
                scored.add(new Scored(new EffectEntry(effectId, name,
                        BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect)), rank));
            }
        }
        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
        List<EffectEntry> entries = new ArrayList<>(scored.size());
        for (Scored s : scored) {
            entries.add(s.entry());
        }
        entryList.fill(entries);
    }

    private void pick(String effectId) {
        onPicked.accept(effectId);
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (entryList == null) {
            return;
        }
        int top = searchField.getY() - 5;
        int bottom = entryList.getY() + entryList.getHeight() + 5;
        panel(graphics, entryList.getX() - 5, top, entryList.getWidth() + 10, bottom - top);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        if (entryList.children().isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.npc_list.empty"),
                    this.width / 2, LIST_TOP + 40, LABEL_COLOR);
        }
    }

    // Like SimpleSelectionList but with 18px rows carrying the effect icon.
    private class EffectEntryList extends ObjectSelectionList<EffectEntry> {

        EffectEntryList(int height) {
            super(Minecraft.getInstance(), LIST_WIDTH, height, LIST_TOP, 18);
        }

        void fill(List<EffectEntry> entries) {
            clearEntries();
            entries.forEach(this::addEntry);
            setScrollAmount(0.0);
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 10;
        }
    }

    private class EffectEntry extends ObjectSelectionList.Entry<EffectEntry> {

        private final String effectId;
        private final Component name;
        private final Holder<MobEffect> effect;

        EffectEntry(String effectId, Component name, Holder<MobEffect> effect) {
            this.effectId = effectId;
            this.name = name;
            this.effect = effect;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
                                   float partialTick) {
            if (effect != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect),
                        getContentX() + 1, getContentYMiddle() - 8, 16, 16);
                graphics.text(Minecraft.getInstance().font, name, getContentX() + 22,
                        getContentYMiddle() - 4, 0xFFFFFFFF);
            } else {
                graphics.item(ENCHANT_ICON, getContentX() + 1, getContentYMiddle() - 8);
                graphics.text(Minecraft.getInstance().font, name, getContentX() + 22,
                        getContentYMiddle() - 4, SECTION_COLOR);
            }
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            pick(effectId);
            return true;
        }

        @Override
        public Component getNarration() {
            return name;
        }
    }
}
