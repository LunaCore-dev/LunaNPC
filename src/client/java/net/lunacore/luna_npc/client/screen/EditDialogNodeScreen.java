package net.lunacore.luna_npc.client.screen;

import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcDialogActionType;

// The node editing UI now lives inline in DialogGraphScreen's dock, not on a standalone screen. This
// holder survives only to keep the one shared piece both action editors reference: the localized label
// for an action type, defined once so the add picker and the parameter editor can never disagree.
final class EditDialogNodeScreen {

    private EditDialogNodeScreen() {
    }

    static Component actionTypeLabel(NpcDialogActionType type) {
        return Component.translatable("gui.luna_npc.dialog.action.type." + type.getSerializedName());
    }
}
