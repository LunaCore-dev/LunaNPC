package net.lunacore.luna_npc.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.client.widget.ItemSlotButton;
import net.lunacore.luna_npc.api.NpcDialogAction;
import net.lunacore.luna_npc.api.NpcDialogActionType;
import net.lunacore.luna_npc.api.NpcDialogNode;
import net.lunacore.luna_npc.api.NpcQuest;
import net.lunacore.luna_npc.api.NpcTradeStack;
import net.lunacore.luna_npc.npc.NpcEquipment;
import net.lunacore.luna_npc.npc.NpcValidation;

// The parameter editor for one action, laid out for its fixed type (the type is chosen in the add
// picker and never changes here). Only the controls that type needs are built. ASSIGN_QUEST reuses the
// full quest editor; GIVE/TAKE_ITEMS reuse the item-slot grid; the rest are plain fields, pickers and
// toggles. The rebuilt action is handed back to the node editor on close.
public class EditDialogActionScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 212;
    private static final int GRID_COLUMNS = 9;

    private final LunaScreen parent;
    private final Consumer<NpcDialogAction> onApply;
    private final List<NpcDialogNode> allNodes;
    private final NpcDialogActionType type;
    private String stringArg;
    private int intArg;
    private boolean flag;
    private NpcQuest quest;
    private int anchorX;
    private int anchorY;
    private int anchorZ;
    private String anchorDimension;
    private final String[] itemIds = new String[NpcValidation.MAX_DIALOG_ACTION_ITEMS];
    private final int[] itemCounts = new int[NpcValidation.MAX_DIALOG_ACTION_ITEMS];

    private Button questButton;
    private Button particleButton;
    private Button jumpButton;
    private final ItemSlotButton[] itemSlots = new ItemSlotButton[NpcValidation.MAX_DIALOG_ACTION_ITEMS];
    private EditBox itemCountField;
    private int itemSelected = -1;
    private boolean populatingCount;

    public EditDialogActionScreen(LunaScreen parent, NpcDialogAction action, List<NpcDialogNode> allNodes,
                                  Consumer<NpcDialogAction> onApply) {
        super(Component.translatable("gui.luna_npc.dialog.action.edit_title"));
        this.parent = parent;
        this.onApply = onApply;
        this.allNodes = allNodes;
        this.type = action.type();
        this.stringArg = action.stringArg();
        this.intArg = action.intArg();
        this.flag = action.flag();
        this.quest = action.quest();
        this.anchorX = action.anchor().getX();
        this.anchorY = action.anchor().getY();
        this.anchorZ = action.anchor().getZ();
        this.anchorDimension = action.anchorDimension();
        if (type == NpcDialogActionType.ORDER_BODYGUARDS && !NpcDialogAction.BODYGUARD_ORDERS.contains(stringArg)) {
            this.stringArg = NpcDialogAction.BODYGUARD_ORDERS.getFirst();
        }
        seedItems(action.items());
    }

    private void seedItems(List<NpcTradeStack> items) {
        for (int i = 0; i < itemIds.length; i++) {
            if (i < items.size()) {
                itemIds[i] = items.get(i).itemId();
                itemCounts[i] = items.get(i).count();
            } else {
                itemIds[i] = "";
                itemCounts[i] = 1;
            }
        }
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

        switch (type) {
            case ASSIGN_QUEST -> questButton = addRenderableWidget(Button.builder(
                    Component.translatable("gui.luna_npc.dialog.action.assign_quest.edit"), button ->
                    Minecraft.getInstance().setScreenAndShow(new EditQuestScreen(this, quest, List.of(),
                            updated -> quest = updated))).bounds(contentX, top + 40, innerW, 18).build());
            case OPEN_TRADE -> numberField(contentX, top + 40, 60, intArg, 3,
                    NpcValidation.MAX_JOB_SUB_TYPES - 1, value -> intArg = value);
            case GIVE_XP -> numberField(contentX, top + 40, 60, intArg, 4,
                    NpcValidation.MAX_JOB_XP, value -> intArg = value);
            case CUSTOM_EVENT -> textField(contentX, top + 40, innerW, stringArg, 128, value -> stringArg = value);
            case RUN_COMMAND -> {
                textField(contentX, top + 40, innerW, stringArg, NpcValidation.MAX_DIALOG_COMMAND_LENGTH,
                        value -> stringArg = value);
                addRenderableWidget(CycleButton
                        .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, flag)
                        .displayOnlyValue().create(contentX + innerW - 70, top + 66, 70, 18,
                                Component.translatable("gui.luna_npc.dialog.action.as_player"),
                                (button, value) -> flag = value));
            }
            case SET_FLAG, CLEAR_FLAG -> textField(contentX, top + 40, innerW, stringArg,
                    NpcValidation.MAX_DIALOG_FLAG_LENGTH, value -> stringArg = value);
            case SET_RELATION -> addRenderableWidget(CycleButton.booleanBuilder(
                    Component.translatable("gui.luna_npc.dialog.action.relation_hostile"),
                    Component.translatable("gui.luna_npc.dialog.action.relation_friendly"), flag)
                    .displayOnlyValue().create(contentX, top + 40, innerW, 18,
                            Component.translatable("gui.luna_npc.dialog.action.relation"),
                            (button, value) -> flag = value));
            case MOVE_TO_ANCHOR -> buildAnchorFields(contentX, top, innerW);
            case PARTICLE -> particleButton = addRenderableWidget(Button.builder(particleLabel(), button ->
                    Minecraft.getInstance().setScreenAndShow(new ParticlePickerScreen(this, stringArg, value -> {
                        stringArg = value;
                        particleButton.setMessage(particleLabel());
                    }))).bounds(contentX, top + 40, innerW, 18).build());
            case JUMP -> jumpButton = addRenderableWidget(Button.builder(jumpLabel(), button ->
                    Minecraft.getInstance().setScreenAndShow(new NodePickerScreen(this, allNodes, value -> {
                        stringArg = value;
                        jumpButton.setMessage(jumpLabel());
                    }))).bounds(contentX, top + 40, innerW, 18).build());
            case GIVE_ITEMS, TAKE_ITEMS -> buildItemGrid(contentX, top + 40);
            case GIVE_BODYGUARD -> {
                textField(contentX, top + 40, 80, stringArg, 10, value -> stringArg = value.replaceAll("[^0-9]", ""));
                numberField(contentX + 100, top + 40, 80, intArg, 6, NpcValidation.MAX_BODYGUARD_HIRE_SECONDS,
                        value -> intArg = value).setTooltip(Tooltip.create(
                        Component.translatable("gui.luna_npc.dialog.action.give_bodyguard.seconds.tooltip")));
            }
            case ORDER_BODYGUARDS -> addRenderableWidget(CycleButton.<String>builder(order ->
                            Component.translatable("gui.luna_npc.dialog.action.order." + order), stringArg)
                    .withValues(NpcDialogAction.BODYGUARD_ORDERS)
                    .displayOnlyValue().create(contentX, top + 40, innerW, 18,
                            Component.translatable("gui.luna_npc.dialog.action.order"),
                            (button, value) -> stringArg = value));
            case ATTACK_PLAYER -> {
            }
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 50, top + PANEL_HEIGHT - 26, 100, 20).build());
    }

    private void buildAnchorFields(int contentX, int top, int innerW) {
        signedField(contentX, top + 40, 60, anchorX, value -> anchorX = value);
        signedField(contentX + 66, top + 40, 60, anchorY, value -> anchorY = value);
        signedField(contentX + 132, top + 40, 60, anchorZ, value -> anchorZ = value);
        numberField(contentX, top + 70, 60, intArg, 2, NpcValidation.MAX_DIALOG_ANCHOR_RADIUS,
                value -> intArg = value);
        textField(contentX + 66, top + 70, innerW - 66, anchorDimension, 200, value -> anchorDimension = value);
    }

    private void buildItemGrid(int gridLeft, int gridTop) {
        for (int index = 0; index < itemSlots.length; index++) {
            int slotIndex = index;
            int x = gridLeft + (index % GRID_COLUMNS) * ItemSlotButton.SIZE;
            int y = gridTop + (index / GRID_COLUMNS) * ItemSlotButton.SIZE;
            itemSlots[index] = addRenderableWidget(new ItemSlotButton(x, y, this.title, event -> {
                if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    itemIds[slotIndex] = "";
                    if (itemSelected == slotIndex) {
                        itemSelected = -1;
                    }
                    refreshItems();
                } else if (itemIds[slotIndex] != null && !itemIds[slotIndex].isBlank()) {
                    itemSelected = slotIndex;
                    refreshItems();
                } else {
                    Minecraft.getInstance().setScreenAndShow(new ItemPickerScreen(this, null, itemId -> {
                        if (!itemId.isEmpty()) {
                            itemIds[slotIndex] = itemId;
                            itemCounts[slotIndex] = Math.max(1, itemCounts[slotIndex]);
                            itemSelected = slotIndex;
                        }
                    }));
                }
            }));
        }
        itemCountField = addRenderableWidget(new EditBox(this.font,
                gridLeft + GRID_COLUMNS * ItemSlotButton.SIZE + 6, gridTop, 34, 16, this.title));
        itemCountField.setMaxLength(2);
        itemCountField.setResponder(value -> {
            if (populatingCount || itemSelected < 0) {
                return;
            }
            String digits = CreateNpcScreen.digitsOnly(itemCountField, value);
            if (digits == null) {
                return;
            }
            itemCounts[itemSelected] = Math.clamp(digits.isEmpty() ? 1 : Integer.parseInt(digits),
                    1, NpcValidation.MAX_TRADE_SLOT_COUNT);
            refreshItemSlots();
        });
        refreshItems();
    }

    private void refreshItems() {
        refreshItemSlots();
        boolean editable = itemSelected >= 0 && itemIds[itemSelected] != null && !itemIds[itemSelected].isBlank();
        itemCountField.visible = editable;
        itemCountField.active = editable;
        if (editable) {
            populatingCount = true;
            itemCountField.setValue(String.valueOf(itemCounts[itemSelected]));
            populatingCount = false;
        }
    }

    private void refreshItemSlots() {
        for (int i = 0; i < itemSlots.length; i++) {
            ItemStack stack = NpcEquipment.resolve(itemIds[i]);
            if (!stack.isEmpty()) {
                stack.setCount(Math.max(1, itemCounts[i]));
                itemSlots[i].setStack(stack);
                itemSlots[i].setTooltip(Tooltip.create(stack.getHoverName()));
            } else {
                itemSlots[i].setStack(ItemStack.EMPTY);
                itemSlots[i].setTooltip(null);
            }
            itemSlots[i].setSelected(i == itemSelected);
        }
    }

    private EditBox textField(int x, int y, int width, String initial, int maxLength, Consumer<String> onChange) {
        EditBox field = new EditBox(this.font, x, y, width, 16, this.title);
        field.setMaxLength(maxLength);
        field.setValue(initial);
        field.setResponder(onChange);
        return addRenderableWidget(field);
    }

    private EditBox numberField(int x, int y, int width, int initial, int maxLength, int max, IntConsumer onChange) {
        EditBox field = new EditBox(this.font, x, y, width, 16, this.title);
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

    private EditBox signedField(int x, int y, int width, int initial, IntConsumer onChange) {
        EditBox field = new EditBox(this.font, x, y, width, 16, this.title);
        field.setMaxLength(8);
        field.setValue(String.valueOf(initial));
        field.setResponder(value -> {
            String digits = value.replaceAll("[^0-9-]", "").replace("-", "");
            String cleaned = value.startsWith("-") ? "-" + digits : digits;
            if (!cleaned.equals(value)) {
                field.setValue(cleaned);
                return;
            }
            onChange.accept(cleaned.isEmpty() || cleaned.equals("-") ? 0 : Integer.parseInt(cleaned));
        });
        return addRenderableWidget(field);
    }

    private Component particleLabel() {
        Component base = Component.translatable("gui.luna_npc.dialog.action.particle");
        return stringArg.isBlank() ? base : base.copy().append(": ").append(Component.literal(stringArg));
    }

    private Component jumpLabel() {
        Component target = stringArg.isBlank()
                ? Component.translatable("gui.luna_npc.dialog.choice.target_end")
                : NodePickerScreen.nodeLabel(allNodes, stringArg);
        return Component.translatable("gui.luna_npc.dialog.action.jump_target").copy().append(": ").append(target);
    }

    private NpcDialogAction build() {
        List<NpcTradeStack> items = new ArrayList<>();
        if (type == NpcDialogActionType.GIVE_ITEMS || type == NpcDialogActionType.TAKE_ITEMS) {
            for (int i = 0; i < itemIds.length; i++) {
                if (itemIds[i] != null && !itemIds[i].isBlank()) {
                    items.add(new NpcTradeStack(itemIds[i], Math.max(1, itemCounts[i])));
                }
            }
        }
        return new NpcDialogAction(type, stringArg, intArg, items, quest,
                new BlockPos(anchorX, anchorY, anchorZ), anchorDimension, flag);
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
        int contentX = panelX() + 14;
        int top = panelY();
        graphics.text(this.font, EditDialogNodeScreen.actionTypeLabel(type), contentX, top + 16, SECTION_COLOR);
        switch (type) {
            case OPEN_TRADE -> label(graphics, "gui.luna_npc.dialog.action.open_trade.index", contentX, top + 30);
            case GIVE_XP -> label(graphics, "gui.luna_npc.dialog.action.xp", contentX, top + 30);
            case CUSTOM_EVENT -> label(graphics, "gui.luna_npc.dialog.action.custom_event.tag", contentX, top + 30);
            case RUN_COMMAND -> {
                label(graphics, "gui.luna_npc.dialog.action.command", contentX, top + 30);
                label(graphics, "gui.luna_npc.dialog.action.as_player", contentX, top + 70);
            }
            case SET_FLAG, CLEAR_FLAG -> label(graphics, "gui.luna_npc.dialog.action.flag_key", contentX, top + 30);
            case MOVE_TO_ANCHOR -> {
                label(graphics, "gui.luna_npc.dialog.action.anchor_pos", contentX, top + 30);
                label(graphics, "gui.luna_npc.dialog.action.anchor_radius", contentX, top + 60);
                label(graphics, "gui.luna_npc.dialog.action.anchor_dimension", contentX + 66, top + 60);
            }
            case GIVE_ITEMS, TAKE_ITEMS -> label(graphics, "gui.luna_npc.dialog.action.items", contentX, top + 30);
            case GIVE_BODYGUARD -> {
                label(graphics, "gui.luna_npc.dialog.action.give_bodyguard.lid", contentX, top + 30);
                label(graphics, "gui.luna_npc.dialog.action.give_bodyguard.seconds", contentX + 100, top + 30);
                long lunaId = NpcDialogAction.parseLunaId(stringArg);
                graphics.textWithWordWrap(this.font, lunaId >= 0
                                ? Component.translatable("gui.luna_npc.dialog.action.give_bodyguard.hint",
                                        net.lunacore.luna_npc.client.ClientNpcCatalog.nameOf(lunaId))
                                : Component.translatable("gui.luna_npc.dialog.action.give_bodyguard.pick"),
                        contentX, top + 66, PANEL_WIDTH - 28, LABEL_COLOR);
            }
            case ORDER_BODYGUARDS -> graphics.textWithWordWrap(this.font,
                    Component.translatable("gui.luna_npc.dialog.action.order.hint"),
                    contentX, top + 66, PANEL_WIDTH - 28, LABEL_COLOR);
            case ATTACK_PLAYER -> graphics.textWithWordWrap(this.font,
                    Component.translatable("gui.luna_npc.dialog.action.attack_player.hint"),
                    contentX, top + 42, PANEL_WIDTH - 28, LABEL_COLOR);
            default -> {
            }
        }
    }

    private void label(GuiGraphicsExtractor graphics, String key, int x, int y) {
        graphics.text(this.font, Component.translatable(key), x, y, LABEL_COLOR);
    }
}
