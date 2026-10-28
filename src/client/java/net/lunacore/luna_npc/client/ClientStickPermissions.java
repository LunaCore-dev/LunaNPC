package net.lunacore.luna_npc.client;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.lunacore.luna_npc.api.LunaStick;

// Client-side cache of this player's own stick access, refreshed by PlayerPermissionsPayload on join and on
// change. Used only to skip opening a screen the player can't use and to show a denial — the server still
// enforces every mutation, so a tampered cache changes nothing.
public final class ClientStickPermissions {

    private static boolean admin;
    private static Set<LunaStick> allowed = EnumSet.noneOf(LunaStick.class);

    private ClientStickPermissions() {
    }

    public static void set(boolean isAdmin, List<LunaStick> allowedSticks) {
        admin = isAdmin;
        allowed = allowedSticks.isEmpty() ? EnumSet.noneOf(LunaStick.class) : EnumSet.copyOf(allowedSticks);
    }

    public static boolean admin() {
        return admin;
    }

    public static boolean canUse(LunaStick stick) {
        return admin || allowed.contains(stick);
    }
}
