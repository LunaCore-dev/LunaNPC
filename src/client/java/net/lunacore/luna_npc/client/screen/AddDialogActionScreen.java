package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.api.NpcDialogAction;
import net.lunacore.luna_npc.api.NpcDialogActionType;
import net.lunacore.luna_npc.api.NpcDialogNode;
import net.lunacore.luna_npc.api.NpcQuest;
import net.lunacore.luna_npc.api.NpcQuestCompletion;

// Picks one of the 14 action types, then drops straight into its parameter editor. The finished action
// is handed to the node editor's add callback; the type picker never returns to the back-stack.
public class AddDialogActionScreen extends LunaScreen {

    private static final int LIST_TOP = 54;

    private final LunaScreen parent;
    private final Consumer<NpcDialogAction> onAdd;
    private final List<NpcDialogNode> allNodes;

    private SimpleSelectionList typeList;

    public AddDialogActionScreen(LunaScreen parent, Consumer<NpcDialogAction> onAdd,
                                 List<NpcDialogNode> allNodes) {
        super(Component.translatable("gui.luna_npc.dialog.action.add_title"));
        this.parent = parent;
        this.onAdd = onAdd;
        this.allNodes = allNodes;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        typeList = addRenderableWidget(new SimpleSelectionList(220, this.height - LIST_TOP - 40, LIST_TOP));
        typeList.setX(centerX - 110);

        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        for (NpcDialogActionType type : NpcDialogActionType.values()) {
            entries.add(new SimpleSelectionList.Entry(type.getSerializedName(),
                    EditDialogNodeScreen.actionTypeLabel(type), id -> pick(type)));
        }
        typeList.fill(entries, null);

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX - 50, this.height - 28, 100, 20).build());
    }

    private void pick(NpcDialogActionType type) {
        Minecraft.getInstance().setScreenAndShow(
                new EditDialogActionScreen(parent, defaultAction(type), allNodes, onAdd));
    }

    static NpcDialogAction defaultAction(NpcDialogActionType type) {
        return new NpcDialogAction(type, "", 0, List.of(), blankQuest(), BlockPos.ZERO, "", false);
    }

    private static NpcQuest blankQuest() {
        return NpcQuest.create("Quest", List.of(), NpcQuestCompletion.TURN_IN, 0, 0, 0, List.of());
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
        int top = LIST_TOP - 5;
        int bottom = typeList.getY() + typeList.getHeight() + 5;
        panel(graphics, typeList.getX() - 5, top, typeList.getWidth() + 10, bottom - top);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
    }
}
