package net.lunacore.luna_npc.client.widget;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// An 18x18 item slot; left click and right click are both passed to the handler (right click is
// used for clearing). The overlay text replaces the stack count corner text (used for drop chance).
public class ItemSlotButton extends AbstractWidget {

    public static final int SIZE = 18;

    private static final int BACKGROUND_COLOR = 0xFF373737;
    private static final int BORDER_COLOR = 0xFF8B8B8B;
    private static final int SELECTED_BORDER_COLOR = 0xFFFFD700;

    private final Consumer<MouseButtonEvent> onPress;
    private ItemStack stack = ItemStack.EMPTY;
    private String overlayText;
    private boolean selected;

    public ItemSlotButton(int x, int y, Component narration, Consumer<MouseButtonEvent> onPress) {
        super(x, y, SIZE, SIZE, narration);
        this.onPress = onPress;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
    }

    public void setOverlayText(String overlayText) {
        this.overlayText = overlayText;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
        return buttonInfo.button() == InputConstants.MOUSE_BUTTON_LEFT || buttonInfo.button() == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onPress.accept(event);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        graphics.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, BACKGROUND_COLOR);
        graphics.outline(getX(), getY(), SIZE, SIZE,
                selected ? SELECTED_BORDER_COLOR : active && isHovered() ? 0xFFFFFFFF : BORDER_COLOR);
        if (!stack.isEmpty()) {
            graphics.item(stack, getX() + 1, getY() + 1);
            graphics.itemDecorations(Minecraft.getInstance().font, stack, getX() + 1, getY() + 1, overlayText);
        }
        if (!active) {
            graphics.fill(getX() + 1, getY() + 1, getX() + SIZE - 1, getY() + SIZE - 1, 0xAA0A0A0A);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
