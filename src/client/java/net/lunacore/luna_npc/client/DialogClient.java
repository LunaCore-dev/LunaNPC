package net.lunacore.luna_npc.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.lunacore.luna_npc.client.screen.DialogScreen;
import net.lunacore.luna_npc.network.DialogClosePayload;
import net.lunacore.luna_npc.network.DialogOpenPayload;

// Client receivers for the dialog payloads, registered by LunaNpcClient. A node arriving for the NPC
// already on screen refreshes it in place; otherwise it opens a new DialogScreen.
public final class DialogClient {

    private DialogClient() {
    }

    public static void handleOpen(DialogOpenPayload payload, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            if (context.client().gui.screen() instanceof DialogScreen screen && screen.lunaId() == payload.lunaId()) {
                screen.apply(payload);
            } else {
                context.client().setScreenAndShow(new DialogScreen(payload));
            }
        });
    }

    public static void handleClose(DialogClosePayload payload, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            if (context.client().gui.screen() instanceof DialogScreen screen && screen.lunaId() == payload.lunaId()) {
                context.client().setScreenAndShow(null);
            }
        });
    }
}
