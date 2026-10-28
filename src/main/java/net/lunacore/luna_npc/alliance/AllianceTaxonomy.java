package net.lunacore.luna_npc.alliance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The default vanilla ecosystem: twelve factions covering every living mob, with enemy/mate
// relationships grounded in real vanilla aggression (undead/monsters/illagers hunt players,
// villagers and golems; golems defend villagers and hunt the hostiles; bosses attack nearly
// everything). Creators edit all of this in-game; this is only the starting point. Mobs from
// other mods that aren't listed here fall back to Neutral so nothing is ever unclassified.
public final class AllianceTaxonomy {

    public static final String FALLBACK_ALLIANCE = "neutral";

    public record Builtin(String id, String name, List<String> enemies, List<String> mates) {
    }

    // Order here is the display order in the editor lists.
    public static final List<Builtin> BUILTINS = List.of(
            new Builtin("players", "Players", List.of(), List.of()),
            new Builtin("villagers", "Villagers", List.of(), List.of()),
            new Builtin("animals", "Animals", List.of(), List.of()),
            new Builtin("aquatic", "Aquatic", List.of(), List.of()),
            new Builtin("undead", "Undead", List.of("players", "villagers", "golems"), List.of()),
            new Builtin("creatures", "Monsters", List.of("players", "villagers", "golems"), List.of()),
            new Builtin("illagers", "Illagers", List.of("players", "villagers", "golems"), List.of()),
            new Builtin("nether", "Nether", List.of("players"), List.of()),
            new Builtin("golems", "Golems", List.of("undead", "creatures", "illagers", "nether"),
                    List.of("villagers", "players")),
            new Builtin("bosses", "Bosses", List.of("players", "villagers", "animals", "aquatic",
                    "undead", "creatures", "illagers", "nether", "golems", "neutral", "decoration"), List.of()),
            new Builtin("neutral", "Neutral", List.of(), List.of()),
            new Builtin("decoration", "Decoration", List.of(), List.of()));

    private static final Map<String, String> ENTITY_ALLIANCE = buildEntityMap();

    private AllianceTaxonomy() {
    }

    // The default alliance for a mob type, or null if this taxonomy doesn't cover it (a modded
    // mob), in which case the manager falls back to Neutral.
    public static String allianceFor(String entityId) {
        return ENTITY_ALLIANCE.get(entityId);
    }

    private static Map<String, String> buildEntityMap() {
        Map<String, String> map = new HashMap<>();
        assign(map, "villagers", "villager", "wandering_trader");
        assign(map, "animals", "armadillo", "bee", "camel", "cat", "chicken", "cow", "donkey",
                "fox", "frog", "goat", "horse", "llama", "mooshroom", "mule", "ocelot", "panda",
                "parrot", "pig", "polar_bear", "rabbit", "sheep", "sniffer", "trader_llama", "wolf");
        assign(map, "aquatic", "axolotl", "cod", "dolphin", "glow_squid", "nautilus", "pufferfish",
                "salmon", "squid", "tadpole", "tropical_fish", "turtle");
        assign(map, "undead", "bogged", "camel_husk", "drowned", "giant", "husk", "parched",
                "phantom", "skeleton", "stray", "wither_skeleton", "zoglin", "zombie",
                "zombie_nautilus", "zombie_villager", "zombified_piglin");
        assign(map, "creatures", "breeze", "cave_spider", "creaking", "creeper", "elder_guardian",
                "enderman", "endermite", "guardian", "killer_bunny", "shulker", "silverfish",
                "slime", "spider", "vex");
        assign(map, "illagers", "evoker", "illusioner", "pillager", "ravager", "vindicator", "witch");
        assign(map, "nether", "blaze", "ghast", "hoglin", "magma_cube", "piglin", "piglin_brute",
                "sulfur_cube");
        assign(map, "golems", "copper_golem", "iron_golem", "snow_golem");
        assign(map, "bosses", "ender_dragon", "warden", "wither");
        // Passive undead-typed horses stay neutral so golems don't hunt harmless mounts.
        assign(map, "neutral", "allay", "bat", "happy_ghast", "strider", "skeleton_horse",
                "zombie_horse");
        assign(map, "decoration", "armor_stand", "mannequin");
        return map;
    }

    private static void assign(Map<String, String> map, String allianceId, String... entityIds) {
        for (String entityId : entityIds) {
            map.put("minecraft:" + entityId, allianceId);
        }
    }
}
