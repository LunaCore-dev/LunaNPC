package net.lunacore.luna_npc;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.lunacore.luna_npc.alliance.NpcAllianceManager;
import net.lunacore.luna_npc.job.DialogProgressManager;
import net.lunacore.luna_npc.job.JobStockManager;
import net.lunacore.luna_npc.job.QuestProgressManager;
import net.lunacore.luna_npc.npc.NpcManager;
import net.lunacore.luna_npc.permission.PermissionLogManager;
import net.lunacore.luna_npc.permission.StickPermissionManager;
import net.lunacore.luna_npc.spawn.NpcRespawnManager;
import net.lunacore.luna_npc.zone.ZoneManager;

// Flushes LunaNPC's saved data (NPCs, zones, alliances, respawns) to disk shortly after any change,
// rather than waiting on the vanilla world autosave. Every mutation — whether made through the
// in-game menus or the developer API — routes through the same managers and only marks them dirty;
// the actual disk write otherwise happens on the next world save. That autosave can be minutes
// apart, stretches further under server lag, and is reconfigurable (or disabled) by the server
// admin, so leaning on it risks losing recent edits to a crash. This ticker guarantees a write
// within FLUSH_INTERVAL_TICKS of a change instead. Ticks with nothing dirty do no work.
public final class LunaNpcPersistence {

    // 200 ticks = 10 seconds at full speed. Kept well under 300 ticks so the worst-case delay stays
    // under five minutes of wall-clock even on a server crawling at a single tick per second.
    private static final int FLUSH_INTERVAL_TICKS = 200;

    private static int ticksUntilFlush = FLUSH_INTERVAL_TICKS;

    private LunaNpcPersistence() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LunaNpcPersistence::tick);
    }

    private static void tick(MinecraftServer server) {
        if (--ticksUntilFlush > 0) {
            return;
        }
        ticksUntilFlush = FLUSH_INTERVAL_TICKS;
        if (hasUnsavedChanges(server)) {
            // Serializes every dirty entry — all of ours together — on this (the server) thread, then
            // writes to disk asynchronously, so the flush never stalls the tick. This also clears the
            // dirty flags, leaving the next interval free until something changes again.
            server.getDataStorage().scheduleSave();
        }
    }

    private static boolean hasUnsavedChanges(MinecraftServer server) {
        return NpcManager.get(server).isDirty()
                || ZoneManager.get(server).isDirty()
                || net.lunacore.luna_npc.route.RouteManager.get(server).isDirty()
                || NpcAllianceManager.get(server).isDirty()
                || NpcRespawnManager.get(server).isDirty()
                || JobStockManager.get(server).isDirty()
                || QuestProgressManager.get(server).isDirty()
                || DialogProgressManager.get(server).isDirty()
                || StickPermissionManager.get(server).isDirty()
                || PermissionLogManager.get(server).isDirty();
    }
}
