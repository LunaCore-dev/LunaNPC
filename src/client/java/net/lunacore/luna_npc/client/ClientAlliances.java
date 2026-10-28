package net.lunacore.luna_npc.client;

import java.util.ArrayList;
import java.util.List;
import net.lunacore.luna_npc.api.NpcAlliance;

// Client-side cache of the server's alliances, refreshed by AlliancesPayload on join and on every
// change. The Relationship tab and the Edit Alliances screen read from here so they always show
// the live factions without a round-trip.
public final class ClientAlliances {

    private static List<NpcAlliance> alliances = List.of();

    private ClientAlliances() {
    }

    public static void set(List<NpcAlliance> updated) {
        alliances = List.copyOf(updated);
    }

    public static List<NpcAlliance> all() {
        return alliances;
    }

    public static NpcAlliance byId(String allianceId) {
        for (NpcAlliance alliance : alliances) {
            if (alliance.id().equals(allianceId)) {
                return alliance;
            }
        }
        return null;
    }

    public static String nameOf(String allianceId) {
        NpcAlliance alliance = byId(allianceId);
        return alliance == null ? allianceId : alliance.name();
    }

    // A mutable deep copy for the editor to work on before sending it back.
    public static List<NpcAlliance> editableCopy() {
        List<NpcAlliance> copy = new ArrayList<>();
        for (NpcAlliance alliance : alliances) {
            copy.add(new NpcAlliance(alliance.id(), alliance.name(), alliance.builtin(),
                    new ArrayList<>(alliance.memberMobTypes()), new ArrayList<>(alliance.memberPlayers()),
                    new ArrayList<>(alliance.enemies()), new ArrayList<>(alliance.mates()), alliance.matchAllPlayers(),
                    alliance.friendlyFire()));
        }
        return copy;
    }
}
