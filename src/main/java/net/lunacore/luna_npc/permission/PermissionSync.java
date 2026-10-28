package net.lunacore.luna_npc.permission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.lunacore.luna_npc.api.LunaStick;
import net.lunacore.luna_npc.network.PlayerPermissionsPayload;

// Keeps each player's client-side stick access fresh when it changes for a reason a grant edit doesn't
// cover — above all /op and /deop, which vanilla never routes through our grant path. A grant edit still
// pushes immediately; this once-a-second sweep is the safety net that also catches op-level changes, so a
// de-opped admin's client stops believing it is still an admin (and a freshly-opped player gains access)
// within a second, without needing to reconnect.
public final class PermissionSync {

    private static final int INTERVAL_TICKS = 20;

    private record Snapshot(boolean admin, Set<LunaStick> allowed) {
    }

    private static final Map<UUID, Snapshot> lastSent = new HashMap<>();
    private static int ticksUntilSweep = INTERVAL_TICKS;

    private PermissionSync() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PermissionSync::sweep);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                lastSent.remove(handler.player.getUUID()));
    }

    private static void sweep(MinecraftServer server) {
        if (--ticksUntilSweep > 0) {
            return;
        }
        ticksUntilSweep = INTERVAL_TICKS;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean admin = PermissionService.isAdmin(server, player);
            Set<LunaStick> allowed = PermissionService.allowedSticks(server, player);
            Snapshot previous = lastSent.get(player.getUUID());
            if (previous == null || previous.admin() != admin || !previous.allowed().equals(allowed)) {
                ServerPlayNetworking.send(player, new PlayerPermissionsPayload(admin, new ArrayList<>(allowed)));
                lastSent.put(player.getUUID(), new Snapshot(admin, allowed));
            }
        }
    }
}
