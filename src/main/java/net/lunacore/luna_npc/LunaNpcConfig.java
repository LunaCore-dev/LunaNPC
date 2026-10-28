package net.lunacore.luna_npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

// File-only options, deliberately without any in-game toggle: config/luna_npc.json is read once
// during startup and never re-read while the game runs.
public final class LunaNpcConfig {

    private static boolean pathfindingDebug;
    private static boolean spawnDebug;
    private static boolean combatDebug;

    private LunaNpcConfig() {
    }

    // Renders active pathfinding tasks with particles near players (debug aid).
    public static boolean pathfindingDebug() {
        return pathfindingDebug;
    }

    // Logs a periodic per-medium natural-spawn tally (spots resolved / passed gates / spawned) so a
    // ground-vs-water density mismatch can be traced to the exact stage it happens.
    public static boolean spawnDebug() {
        return spawnDebug;
    }

    // Logs every hit an NPC takes — who struck it, the faction relation, its temperament, and whether
    // it engages — so "it won't fight back" can be traced to the exact decision that dropped the fight.
    public static boolean combatDebug() {
        return combatDebug;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(LunaNpc.MOD_ID + ".json");
        try {
            if (!Files.exists(file)) {
                JsonObject defaults = new JsonObject();
                defaults.addProperty("pathfinding_debug", false);
                defaults.addProperty("spawn_debug", false);
                defaults.addProperty("combat_debug", false);
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(defaults));
                return;
            }
            JsonObject json = new Gson().fromJson(Files.readString(file), JsonObject.class);
            pathfindingDebug = json != null && json.has("pathfinding_debug")
                    && json.get("pathfinding_debug").getAsBoolean();
            spawnDebug = json != null && json.has("spawn_debug")
                    && json.get("spawn_debug").getAsBoolean();
            combatDebug = json != null && json.has("combat_debug")
                    && json.get("combat_debug").getAsBoolean();
        } catch (IOException | RuntimeException brokenConfig) {
            pathfindingDebug = false;
            spawnDebug = false;
            combatDebug = false;
        }
    }
}
