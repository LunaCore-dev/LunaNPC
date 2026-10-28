package net.lunacore.luna_npc;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

// Every non-command chat line the mod prints is branded: a purple [LunaNPC] prefix, a plain white
// body, and orange for the values worth the player's eye (names, ids). Command feedback is exempt.
public final class LunaNpcChat {

    private static final int PREFIX_COLOR = 0xB57EDC;
    private static final int HIGHLIGHT_COLOR = 0xFFA23A;

    private LunaNpcChat() {
    }

    public static void send(ServerPlayer player, Component body) {
        player.sendSystemMessage(prefixed(body));
    }

    public static MutableComponent prefixed(Component body) {
        return Component.empty()
                .append(Component.literal("[LunaNPC] ").withStyle(style -> style.withColor(PREFIX_COLOR).withBold(true)))
                .append(body);
    }

    public static Component highlight(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(style -> style.withColor(HIGHLIGHT_COLOR));
    }
}
