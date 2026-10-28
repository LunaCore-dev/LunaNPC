package net.lunacore.luna_npc.client;

import java.util.ArrayList;
import java.util.List;
import net.lunacore.luna_npc.api.NpcZone;

// Client-side cache of the server's named zones, refreshed by ZonesPayload on join and on every
// change. The Create/Manage Zone screens, the Spawn tab, and the border renderer read from here.
public final class ClientZones {

    private static List<NpcZone> zones = List.of();

    private ClientZones() {
    }

    public static void set(List<NpcZone> updated) {
        zones = List.copyOf(updated);
    }

    public static List<NpcZone> all() {
        return zones;
    }

    public static NpcZone byId(String zoneId) {
        for (NpcZone zone : zones) {
            if (zone.id().equals(zoneId)) {
                return zone;
            }
        }
        return null;
    }

    public static String nameOf(String zoneId) {
        NpcZone zone = byId(zoneId);
        return zone == null ? zoneId : zone.name();
    }

    public static List<NpcZone> editableCopy() {
        return new ArrayList<>(zones);
    }
}
