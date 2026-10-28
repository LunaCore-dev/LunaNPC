package net.lunacore.luna_npc.client;

import java.util.List;
import net.lunacore.luna_npc.network.NpcSummary;

// The NPC definitions from the last NPC list the server sent (opening the Edit or Spawn stick), so editors
// can offer NPCs by name — the bodyguard's protect picker uses it. Empty until a list has been opened once.
public final class ClientNpcCatalog {

    private static List<NpcSummary> npcs = List.of();

    private ClientNpcCatalog() {
    }

    public static void set(List<NpcSummary> updated) {
        npcs = List.copyOf(updated);
    }

    public static List<NpcSummary> all() {
        return npcs;
    }

    public static String nameOf(long lunaId) {
        for (NpcSummary npc : npcs) {
            if (npc.lunaId() == lunaId) {
                return npc.name();
            }
        }
        return "LID " + lunaId;
    }
}
