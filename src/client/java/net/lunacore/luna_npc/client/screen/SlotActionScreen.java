package net.lunacore.luna_npc.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// The small action dialog for one filled item slot: it previews the item and offers to swap it,
// open the enchant editor for it, and (when a clear action is supplied) empty the slot. Shared by
// the Create/Edit NPC equipment slots and the trade editor's input/output slots so both reach the
// enchant editor the same way. Empty slots skip this and open the item picker directly.
public class SlotActionScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 180;
    private static final int BUTTON_WIDTH = 152;
    private static final int BUTTON_HEIGHT = 20;
    private static final int FIRST_BUTTON_Y = 58;
    private static final int BUTTON_PITCH = 24;
    private static final float ITEM_SCALE = 2.5F;

    private final Screen parent;
    private final ItemStack preview;
    private final Component slotName;
    private final Runnable onChoose;
    private final Runnable onEnchant;
    private final Runnable onClear;

    public SlotActionScreen(Screen parent, ItemStack preview, Component slotName,
                            Runnable onChoose, Runnable onEnchant, Runnable onClear) {
        super(slotName);
        this.parent = parent;
        this.preview = preview;
        this.slotName = slotName;
        this.onChoose = onChoose;
        this.onEnchant = onEnchant;
        this.onClear = onClear;
    }

    private int buttonCount() {
        return onClear == null ? 3 : 4;
    }

    private int panelHeight() {
        return FIRST_BUTTON_Y + buttonCount() * BUTTON_PITCH + 12;
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - panelHeight() / 2;
    }

    @Override
    protected void init() {
        int buttonX = this.width / 2 - BUTTON_WIDTH / 2;
        int y = panelY() + FIRST_BUTTON_Y;
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.create_npc.slot_choose"),
                button -> onChoose.run()).bounds(buttonX, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        y += BUTTON_PITCH;
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.create_npc.slot_enchant"),
                button -> onEnchant.run()).bounds(buttonX, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        if (onClear != null) {
            y += BUTTON_PITCH;
            addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.item_picker.clear"),
                    button -> onClear.run()).bounds(buttonX, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        y += BUTTON_PITCH;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(buttonX, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, panelHeight(), slotName);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!preview.isEmpty()) {
            int cx = this.width / 2;
            int cy = panelY() + 36;
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx, cy);
            graphics.pose().scale(ITEM_SCALE);
            graphics.item(preview, -8, -8);
            graphics.pose().popMatrix();
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
