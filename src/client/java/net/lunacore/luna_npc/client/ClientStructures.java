package net.lunacore.luna_npc.client;

import java.util.List;

// Client-side cache of the server's structure ids (the structure registry isn't synced to clients),
// refreshed by StructuresPayload on join. The Spawn tab's structure picker reads from here.
public final class ClientStructures {

    private static List<String> structureIds = List.of();

    private ClientStructures() {
    }

    public static void set(List<String> ids) {
        structureIds = List.copyOf(ids);
    }

    public static List<String> all() {
        return structureIds;
    }
}
