package net.lunacore.luna_npc.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.client.widget.ItemSlotButton;
import net.lunacore.luna_npc.api.NpcDialogCondition;
import net.lunacore.luna_npc.api.NpcDialogConditionType;
import net.lunacore.luna_npc.npc.NpcEquipment;
import net.lunacore.luna_npc.npc.NpcValidation;

// One gate on a choice: the condition type, a negate toggle, and the argument its type needs — a quest
// id, an item + count (HAS_ITEM), or a flag key. Switching type swaps which argument control is shown.
public class EditDialogConditionScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 196;

    private final LunaScreen parent;
    private final Consumer<NpcDialogCondition> onApply;
    private NpcDialogConditionType type;
    private boolean negate;
    private String stringArg;
    private int intArg;

    private Button typeButton;
    private EditBox argField;
    private ItemSlotButton itemSlot;
    private EditBox countField;

    public EditDialogConditionScreen(LunaScreen parent, NpcDialogCondition condition,
                                     Consumer<NpcDialogCondition> onApply) {
        super(Component.translatable("gui.luna_npc.dialog.condition.title"));
        this.parent = parent;
        this.onApply = onApply;
        this.type = condition.type();
        this.negate = condition.negate();
        this.stringArg = condition.stringArg();
        this.intArg = Math.max(1, condition.intArg());
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }

    @Override
    protected void init() {
        int contentX = panelX() + 14;
        int innerW = PANEL_WIDTH - 28;
        int top = panelY();

        typeButton = addRenderableWidget(Button.builder(typeLabel(), button -> {
            NpcDialogConditionType[] types = NpcDialogConditionType.values();
            type = types[(type.ordinal() + 1) % types.length];
            typeButton.setMessage(typeLabel());
            refresh();
        }).bounds(contentX, top + 28, innerW, 18).build());

        addRenderableWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, negate)
                .displayOnlyValue().create(contentX + innerW - 70, top + 50, 70, 18,
                        Component.translatable("gui.luna_npc.dialog.condition.negate"),
                        (button, value) -> negate = value));

        argField = addRenderableWidget(new EditBox(this.font, contentX, top + 90, innerW, 16, this.title));
        argField.setMaxLength(200);
        argField.setValue(stringArg);
        argField.setResponder(value -> stringArg = value);

        itemSlot = addRenderableWidget(new ItemSlotButton(contentX, top + 88, this.title, event -> {
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                stringArg = "";
                refreshItemSlot();
            } else {
                Minecraft.getInstance().setScreenAndShow(new ItemPickerScreen(this, null, itemId -> {
                    stringArg = itemId;
                }));
            }
        }));
        countField = addRenderableWidget(new EditBox(this.font, contentX + 24, top + 89, 50, 16, this.title));
        countField.setMaxLength(4);
        countField.setValue(String.valueOf(intArg));
        countField.setResponder(value -> {
            String digits = CreateNpcScreen.digitsOnly(countField, value);
            if (digits != null) {
                intArg = digits.isEmpty() ? 1 : Math.max(1, Integer.parseInt(digits));
            }
        });

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 50, top + PANEL_HEIGHT - 26, 100, 20).build());

        refresh();
    }

    private void refresh() {
        boolean item = type == NpcDialogConditionType.HAS_ITEM;
        boolean flag = type == NpcDialogConditionType.FLAG_SET;
        argField.visible = !item;
        argField.setMaxLength(flag ? NpcValidation.MAX_DIALOG_FLAG_LENGTH : 200);
        itemSlot.visible = item;
        countField.visible = item;
        refreshItemSlot();
    }

    private void refreshItemSlot() {
        ItemStack stack = NpcEquipment.resolve(stringArg);
        if (!stack.isEmpty()) {
            itemSlot.setStack(stack);
            itemSlot.setTooltip(Tooltip.create(stack.getHoverName()));
        } else {
            itemSlot.setStack(ItemStack.EMPTY);
            itemSlot.setTooltip(null);
        }
    }

    private Component typeLabel() {
        return Component.translatable("gui.luna_npc.dialog.condition.type." + type.getSerializedName());
    }

    private Component argLabel() {
        return switch (type) {
            case QUEST_COMPLETED -> Component.translatable("gui.luna_npc.dialog.condition.quest_id");
            case HAS_ITEM -> Component.translatable("gui.luna_npc.dialog.condition.item");
            case FLAG_SET -> Component.translatable("gui.luna_npc.dialog.condition.flag_key");
        };
    }

    @Override
    public void onClose() {
        int count = type == NpcDialogConditionType.HAS_ITEM ? intArg : 1;
        onApply.accept(new NpcDialogCondition(type, stringArg, count, negate));
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int contentX = panelX() + 14;
        int top = panelY();
        graphics.text(this.font, Component.translatable("gui.luna_npc.dialog.condition.type"),
                contentX, top + 16, SECTION_COLOR);
        graphics.text(this.font, Component.translatable("gui.luna_npc.dialog.condition.negate"),
                contentX, top + 54, LABEL_COLOR);
        graphics.text(this.font, argLabel(), contentX, top + 76, LABEL_COLOR);
        if (type == NpcDialogConditionType.HAS_ITEM) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.dialog.condition.count"),
                    contentX + 80, top + 92, LABEL_COLOR);
        }
    }
}
