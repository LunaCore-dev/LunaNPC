package net.lunacore.luna_npc.api;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.lunacore.luna_npc.alliance.NpcAllianceManager;
import net.lunacore.luna_npc.entity.LunaNpcEntities;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.npc.NpcManager;
import net.lunacore.luna_npc.api.NpcZone;
import net.lunacore.luna_npc.zone.ZoneManager;

public final class LunaNpcApi {

    // Mirrors the mod's own version (gradle mod_version / fabric.mod.json), read from the loaded mod at
    // startup, so the API version can never drift from the mod version. Check it before relying on newer
    // methods: if (LunaNpcApi.apiVersion().startsWith("0.")) { ... }.
    public static final String API_VERSION = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer("luna_npc")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("0.2.0");

    private LunaNpcApi() {
    }

    public static String apiVersion() {
        return API_VERSION;
    }

    // The API must be called on the server thread. If you're on an async task (a database callback, a
    // web request, a timer), hand the work back through here so you never touch world state off-thread.
    public static void runOnServerThread(MinecraftServer server, Runnable action) {
        server.execute(action);
    }

    // Asks an NPC to attack a target right now — a runtime override of its normal targeting (event
    // scripts, boss phases, etc.). The NPC engages if it has an attack enabled; the result says why
    // not otherwise. Call on the server thread.
    public static AttackIntentResult requestAttack(NpcInstance source,
                                                   net.minecraft.world.entity.LivingEntity target) {
        if (source == null || !source.isAlive()) {
            return AttackIntentResult.SOURCE_REMOVED;
        }
        if (target == null || target.isRemoved() || target.isDeadOrDying()) {
            return AttackIntentResult.TARGET_REMOVED;
        }
        if (target == source) {
            return AttackIntentResult.INVALID_TARGET;
        }
        return source instanceof NpcEntity npc && npc.forceAttack(target)
                ? AttackIntentResult.ACCEPTED : AttackIntentResult.UNSUPPORTED;
    }

    public static NpcRegistry npcs(MinecraftServer server) {
        return NpcManager.get(server);
    }

    public static AllianceRegistry alliances(MinecraftServer server) {
        return NpcAllianceManager.get(server);
    }

    public static ZoneRegistry zones(MinecraftServer server) {
        return ZoneManager.get(server);
    }

    // The named patrol routes the Route Creator stick builds; a PATROL job references them by id.
    public static RouteRegistry routes(MinecraftServer server) {
        return net.lunacore.luna_npc.route.RouteManager.get(server);
    }

    // Player skins downloaded by name or URL and kept with the world; an NPC wears one through its model.
    public static SkinRegistry skins(MinecraftServer server) {
        return net.lunacore.luna_npc.skin.SkinManager.get(server);
    }

    public static StickPermissions permissions(MinecraftServer server) {
        return new net.lunacore.luna_npc.permission.PermissionsAccess(server);
    }

    // Spawns a live NPC of the given definition into the world at pos (the Spawn NPC stick's action),
    // returning the placed entity, or null if it couldn't be created. The NPC records pos as its spawn
    // origin so respawn-at-origin works. Get or build the definition via npcs(server).
    public static NpcInstance spawn(ServerLevel level, NpcDefinition definition, BlockPos pos) {
        return NpcEntity.spawn(level, definition, EntitySpawnReason.MOB_SUMMONED,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, pos);
    }

    // Scatters up to `count` copies of the definition at valid positions inside the zone (reusing
    // spawn()), returning the ones actually placed. Best-effort sampling: it prefers the surface when
    // that sits inside the zone, otherwise a point in the zone's own Y range, and skips spots that would
    // suffocate the NPC. Returns fewer than count if the zone is too tight to fit them.
    public static List<NpcInstance> spawnCopiesInZone(ServerLevel level, NpcDefinition definition, NpcZone zone, int count) {
        List<NpcInstance> spawned = new ArrayList<>();
        String dimension = level.dimension().identifier().toString();
        if (count <= 0 || !zone.dimension().equals(dimension)) {
            return spawned;
        }
        AABB bounds = zone.shape().bounds();
        RandomSource random = level.getRandom();
        int maxAttempts = count * 50;
        for (int attempt = 0; attempt < maxAttempts && spawned.size() < count; attempt++) {
            int x = Mth.floor(Mth.lerp(random.nextDouble(), bounds.minX, bounds.maxX));
            int z = Mth.floor(Mth.lerp(random.nextDouble(), bounds.minZ, bounds.maxZ));
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = zone.contains(dimension, new BlockPos(x, surface, z)) ? surface
                    : Mth.floor(Mth.lerp(random.nextDouble(), bounds.minY, bounds.maxY));
            BlockPos pos = new BlockPos(x, y, z);
            if (!zone.contains(dimension, pos)
                    || !level.noCollision(LunaNpcEntities.NPC.getSpawnAABB(x + 0.5, y, z + 0.5))) {
                continue;
            }
            NpcInstance entity = spawn(level, definition, pos);
            if (entity != null) {
                spawned.add(entity);
            }
        }
        return spawned;
    }
}
