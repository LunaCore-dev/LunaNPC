package net.lunacore.luna_npc.spawn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.lunacore.luna_npc.LunaNpcConfig;
import net.lunacore.luna_npc.api.LunaNpcApi;
import net.lunacore.luna_npc.api.NpcAirSpawn;
import net.lunacore.luna_npc.api.NpcWaterSpawn;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcRegistry;
import net.lunacore.luna_npc.entity.LunaNpcEntities;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.api.NpcSpawnSettings;
import net.lunacore.luna_npc.spawn.NpcRespawnManager.Pending;
import net.lunacore.luna_npc.api.NpcZone;
import net.lunacore.luna_npc.api.NpcZoneSpawn;
import net.lunacore.luna_npc.api.SurfaceMode;
import net.lunacore.luna_npc.zone.ZoneManager;

// The custom world spawner: mimics vanilla NaturalSpawner but drives everything off each NPC
// definition's own spawn settings instead of a hardcoded mob list. Natural spawning runs per loaded
// level on a throttle; delayed respawns are drained once per server tick (the respawn queue is
// server-wide, so polling it per level would let the first-ticked dimension steal another's entries).
public final class NpcSpawner {

    // How often a level runs a natural-spawn pass, and how many random columns it probes each pass. The
    // density cap bounds the result, so a generous probe count just fills fresh area (ahead of a moving
    // player) up to the target faster; it idles once the area is full.
    private static final int NATURAL_SPAWN_INTERVAL_TICKS = 20;
    private static final int SPAWN_ATTEMPTS_PER_PASS = 40;

    // Same near-player exclusion vanilla uses (24 blocks) so nothing pops on top of a player.
    private static final double MIN_SPAWN_DISTANCE_SQUARED = 576.0;
    // A spawn within this range of a player, in front of them and in clear line of sight, is
    // suppressed so nobody watches an NPC blink into existence in their face.
    private static final double VISIBLE_RANGE_SQUARED = 96.0 * 96.0;
    // "In front" cone: cos of the angle between the player's look and the direction to the spawn;
    // 0.2 is roughly a 78° half-angle, wide enough to cover what a player would actually notice.
    private static final double FRONT_CONE_DOT = 0.2;
    // Radius the maxNearby cap counts live NPCs within, for biome/structure spawns.
    private static final double MAX_NEARBY_RADIUS = 128.0;
    // A respawn whose level or chunk is not ready is retried after this many ticks.
    private static final int RESPAWN_RETRY_TICKS = 40;
    // The spawn value is a DENSITY target, not a rate: the spawner fills the area around a player up to a
    // live-count cap and then stops, so a parked player never "leaks" more and more NPCs and a moving one
    // finds the area ahead already populated (spawns fill in, and NPCs left >128 blocks behind despawn on
    // their own). The cap for one NPC in one biome is chance * CAP_SCALE * its medium multiplier, counted
    // within CAP_RADIUS of the spot; an explicit max-nearby overrides it. Ground is the reference; the
    // multipliers make air common and water sparse per the creator's feel. All tunable.
    private static final double CAP_SCALE = 0.125;
    private static final double GROUND_CAP_MULT = 2.0;
    private static final double AIR_CAP_MULT = 4.0;
    private static final double WATER_CAP_MULT = 2.0;
    private static final double CAP_RADIUS = 128.0;

    // Diagnostic tally (spawn_debug config): per movement type, [spots resolved, passed gates, spawned].
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SPAWN_DEBUG_LOG_INTERVAL = 150;
    private static final Map<String, long[]> SPAWN_STATS = new HashMap<>();
    private static int spawnDebugPasses;

    private NpcSpawner() {
    }

    public static void register() {
        ServerTickEvents.END_LEVEL_TICK.register(NpcSpawner::tickNaturalSpawns);
        ServerTickEvents.END_SERVER_TICK.register(NpcSpawner::tickRespawns);
    }

    private static void tickNaturalSpawns(ServerLevel level) {
        if (level.getGameTime() % NATURAL_SPAWN_INTERVAL_TICKS != 0 || level.players().isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        List<NpcDefinition> naturalNpcs = collectNaturalNpcs(server);
        if (naturalNpcs.isEmpty()) {
            return;
        }
        runNaturalSpawnPass(level, server, naturalNpcs);
        if (LunaNpcConfig.spawnDebug() && ++spawnDebugPasses >= SPAWN_DEBUG_LOG_INTERVAL) {
            logSpawnStats();
            spawnDebugPasses = 0;
        }
    }

    private static void stat(String medium, int stage) {
        if (LunaNpcConfig.spawnDebug()) {
            SPAWN_STATS.computeIfAbsent(medium, ignored -> new long[3])[stage]++;
        }
    }

    private static void logSpawnStats() {
        for (Map.Entry<String, long[]> entry : SPAWN_STATS.entrySet()) {
            long[] c = entry.getValue();
            LOGGER.info("[spawn-debug] {}: spots-resolved={} passed-gates={} spawned={}",
                    entry.getKey(), c[0], c[1], c[2]);
        }
        SPAWN_STATS.clear();
    }

    private static List<NpcDefinition> collectNaturalNpcs(MinecraftServer server) {
        List<NpcDefinition> natural = new ArrayList<>();
        for (NpcDefinition npc : LunaNpcApi.npcs(server).all()) {
            if (npc.spawn().naturalSpawnEnabled()) {
                natural.add(npc);
            }
        }
        return natural;
    }

    private static void runNaturalSpawnPass(ServerLevel level, MinecraftServer server, List<NpcDefinition> naturalNpcs) {
        RandomSource random = level.getRandom();
        List<ServerPlayer> players = level.players();
        String dimensionId = level.dimension().identifier().toString();
        int span = 2 * NaturalSpawner.SPAWN_DISTANCE_CHUNK + 1;
        // Cached live-count per NPC for this pass, so the cap check counts entities at most once per NPC.
        Map<Long, Integer> nearbyCounts = new HashMap<>();
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS_PER_PASS; attempt++) {
            ServerPlayer anchor = players.get(random.nextInt(players.size()));
            int chunkX = (anchor.getBlockX() >> 4) + random.nextInt(span) - NaturalSpawner.SPAWN_DISTANCE_CHUNK;
            int chunkZ = (anchor.getBlockZ() >> 4) + random.nextInt(span) - NaturalSpawner.SPAWN_DISTANCE_CHUNK;
            if (!level.hasChunk(chunkX, chunkZ)) {
                continue;
            }
            int x = (chunkX << 4) + random.nextInt(16);
            int z = (chunkZ << 4) + random.nextInt(16);
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            int minY = level.getMinY();
            if (surface < minY) {
                continue;
            }
            // One random depth per column, used only to pick which cave floor a caves-eligible ground NPC
            // might land on; the surface, water and air spots are resolved deterministically per column.
            int caveHintY = minY + random.nextInt(surface - minY + 1);
            trySpawnColumn(level, server, naturalNpcs, x, z, surface, caveHintY, dimensionId, random, nearbyCounts);
        }
    }

    private static boolean tooCloseToPlayers(ServerLevel level, BlockPos pos) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= MIN_SPAWN_DISTANCE_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private record SpawnCandidate(NpcDefinition npc, int weight, BlockPos placePos) {
    }

    private static void trySpawnColumn(ServerLevel level, MinecraftServer server, List<NpcDefinition> naturalNpcs,
                                       int x, int z, int surface, int caveHintY, String dimensionId, RandomSource random,
                                       Map<Long, Integer> nearbyCounts) {
        Map<BlockPos, List<SpawnCandidate>> bySpot = new HashMap<>();
        for (NpcDefinition npc : naturalNpcs) {
            String medium = npc.movement().movementType();
            for (BlockPos spot : resolveSpawnSpots(level, npc, x, z, surface, caveHintY)) {
                stat(medium, 0);
                if (tooCloseToPlayers(level, spot) || !passesSpotGates(level, server, npc, spot, dimensionId)) {
                    continue;
                }
                String biomeId = level.getBiome(spot).unwrapKey().map(key -> key.identifier().toString()).orElse("");
                int weight = eligibilityWeight(level, server, npc, spot, biomeId, dimensionId);
                if (weight > 0 && !densityCapReached(level, npc, spot, weight, medium, nearbyCounts)) {
                    stat(medium, 1);
                    bySpot.computeIfAbsent(spot, ignored -> new ArrayList<>()).add(new SpawnCandidate(npc, weight, spot));
                }
            }
        }
        // Each spot spawns at most one NPC — chosen among those eligible there by weight — but surface,
        // cave, water and air are distinct spots, so an NPC allowed in several stacks its chances (the
        // surface + caves total the creator asked for). The density cap, not a per-spot roll, bounds how
        // many end up standing, so the area fills to the target and holds there.
        for (List<SpawnCandidate> atSpot : bySpot.values()) {
            SpawnCandidate chosen = weightedPick(atSpot, random);
            if (chosen == null || visibleToAnyPlayer(level, chosen.placePos())) {
                continue;
            }
            stat(chosen.npc().movement().movementType(), 2);
            spawnNpc(level, chosen.npc(), chosen.placePos(), random.nextFloat() * 360.0F, EntitySpawnReason.NATURAL);
            nearbyCounts.merge(chosen.npc().lunaId(), 1, Integer::sum);
        }
    }

    // True once the area around this spot already holds the target number of this NPC, so spawning stops
    // instead of piling more on. The target is the creator's explicit max-nearby, or (when that is 0) an
    // automatic cap from the spawn value and the medium — the density the "chance" slider really controls.
    private static boolean densityCapReached(ServerLevel level, NpcDefinition npc, BlockPos spot, int weight,
                                             String medium, Map<Long, Integer> nearbyCounts) {
        int explicit = npc.spawn().maxNearby();
        int cap = explicit > 0 ? explicit : autoCap(weight, medium);
        if (cap <= 0) {
            return false;
        }
        long lunaId = npc.lunaId();
        int count = nearbyCounts.computeIfAbsent(lunaId, id -> level.getEntitiesOfClass(NpcEntity.class,
                new AABB(spot).inflate(CAP_RADIUS), entity -> entity.lunaId() == id).size());
        return count >= cap;
    }

    private static int autoCap(int weight, String medium) {
        double mult = switch (medium) {
            case "water" -> WATER_CAP_MULT;
            case "air" -> AIR_CAP_MULT;
            default -> GROUND_CAP_MULT;
        };
        return Math.max(1, (int) Math.round(weight * CAP_SCALE * mult));
    }

    // The gates that don't depend on the competing weights: the light band, the ground surface/cave +
    // build-safety rules, and a clear body-sized space. Shared so every candidate spot is judged the same.
    private static boolean passesSpotGates(ServerLevel level, MinecraftServer server, NpcDefinition npc,
                                           BlockPos spot, String dimensionId) {
        NpcSpawnSettings spawn = npc.spawn();
        int brightness = level.getMaxLocalRawBrightness(spot);
        if (brightness < spawn.minSpawnLight() || brightness > spawn.maxSpawnLight()) {
            return false;
        }
        if ("ground".equals(npc.movement().movementType())
                && !passesPlacementRules(level, spot, applicableSurfaceMode(server, npc, spot, dimensionId))) {
            return false;
        }
        return level.noCollision(LunaNpcEntities.NPC.getSpawnAABB(
                spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5));
    }

    // A spawn a player could watch appear is jarring, so reject any position that is both in front
    // of a nearby player and in their clear line of sight — the NPC then only shows up behind them
    // or hidden by terrain, the way vanilla surface mobs effectively do.
    private static boolean visibleToAnyPlayer(ServerLevel level, BlockPos pos) {
        Vec3 target = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        for (ServerPlayer player : level.players()) {
            Vec3 eye = player.getEyePosition();
            Vec3 toTarget = target.subtract(eye);
            if (toTarget.lengthSqr() > VISIBLE_RANGE_SQUARED) {
                continue;
            }
            if (player.getLookAngle().dot(toTarget.normalize()) < FRONT_CONE_DOT) {
                continue;
            }
            ClipContext ctx = new ClipContext(eye, target, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player);
            if (level.clip(ctx).getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    // The spawn weight for one NPC at one spot: cube weights it fits in (each still under its own
    // max) plus, gated by maxNearby, the structure and biome weights that cover the spot. Zero means
    // this NPC can't spawn here.
    private static int eligibilityWeight(ServerLevel level, MinecraftServer server, NpcDefinition npc,
                                         BlockPos pos, String biomeId, String dimensionId) {
        NpcSpawnSettings spawn = npc.spawn();
        int zoneWeight = zoneWeight(level, server, npc, spawn, pos, dimensionId);
        int openWeight = structureWeight(level, server, spawn, pos) + Math.max(0, spawn.biomeChance(biomeId));
        if (openWeight > 0 && maxNearbyReached(level, npc, pos)) {
            openWeight = 0;
        }
        return zoneWeight + openWeight;
    }

    private static int zoneWeight(ServerLevel level, MinecraftServer server, NpcDefinition npc,
                                  NpcSpawnSettings spawn, BlockPos pos, String dimensionId) {
        if (spawn.zoneSpawns().isEmpty()) {
            return 0;
        }
        ZoneManager cubes = ZoneManager.get(server);
        int total = 0;
        for (NpcZoneSpawn zoneSpawn : spawn.zoneSpawns()) {
            NpcZone cube = cubes.byId(zoneSpawn.zoneId());
            if (cube == null || !cube.contains(dimensionId, pos)) {
                continue;
            }
            if (zoneSpawn.maxCount() > 0
                    && liveAndPendingInZone(level, server, npc.lunaId(), cube) >= zoneSpawn.maxCount()) {
                continue;
            }
            total += Math.max(0, zoneSpawn.chance());
        }
        return total;
    }

    private static int liveAndPendingInZone(ServerLevel level, MinecraftServer server, long lunaId, NpcZone cube) {
        int count = 0;
        for (NpcEntity entity : level.getEntities(LunaNpcEntities.NPC, entity -> entity.lunaId() == lunaId)) {
            if (cube.contains(level.dimension().identifier().toString(), entity.blockPosition())) {
                count++;
            }
        }
        for (Pending pending : NpcRespawnManager.get(server).pending()) {
            if (pending.lunaId() == lunaId && cube.contains(pending.dimension(), pending.pos())) {
                count++;
            }
        }
        return count;
    }

    private static int structureWeight(ServerLevel level, MinecraftServer server,
                                       NpcSpawnSettings spawn, BlockPos pos) {
        if (spawn.structureChances().isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Map.Entry<String, Integer> entry : spawn.structureChances().entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            Structure structure = resolveStructure(server, entry.getKey());
            if (structure != null && level.structureManager().getStructureWithPieceAt(pos.getX(), pos.getY(), pos.getZ(), structure).isValid()) {
                total += entry.getValue();
            }
        }
        return total;
    }

    private static Structure resolveStructure(MinecraftServer server, String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return null;
        }
        return server.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .get(ResourceKey.create(Registries.STRUCTURE, parsed))
                .map(Holder.Reference::value)
                .orElse(null);
    }

    private static boolean maxNearbyReached(ServerLevel level, NpcDefinition npc, BlockPos pos) {
        int max = npc.spawn().maxNearby();
        if (max <= 0) {
            return false;
        }
        long lunaId = npc.lunaId();
        AABB box = new AABB(pos).inflate(MAX_NEARBY_RADIUS);
        return level.getEntitiesOfClass(NpcEntity.class, box, entity -> entity.lunaId() == lunaId).size() >= max;
    }

    // The canonical spawn spot(s) this NPC's movement type offers in this column: one for water (the cell
    // under the fluid surface) and one for air (just above the terrain), and for a ground NPC the surface
    // floor and/or a cave floor near the random depth, whichever its surface mode allows. Resolving one
    // spot per medium per column — instead of sampling a random depth through a contiguous column — is
    // what makes water, air and land spawn at the same per-block rate regardless of depth or sky height.
    private static List<BlockPos> resolveSpawnSpots(ServerLevel level, NpcDefinition npc,
                                                    int x, int z, int surface, int caveHintY) {
        List<BlockPos> spots = new ArrayList<>(2);
        switch (npc.movement().movementType()) {
            case "water" -> addIfPresent(spots,
                    resolveWaterSpot(level, x, z, surface, npc.spawn().waterSpawn(), caveHintY));
            case "air" -> addIfPresent(spots, resolveAirSpot(level, x, z, surface, npc.spawn().airSpawn(), caveHintY));
            default -> {
                SurfaceMode mode = npc.spawn().surfaceMode();
                if (mode != SurfaceMode.CAVES_ONLY) {
                    // The real ground top, ignoring leaves/snow, so a wooded column drops onto the floor
                    // under the canopy instead of onto the leaves (where ON_GROUND placement fails).
                    int groundTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    addIfPresent(spots, resolveGroundSpot(level, x, groundTop, z));
                }
                if (mode != SurfaceMode.SURFACE_ONLY) {
                    BlockPos cave = resolveGroundSpot(level, x, caveHintY, z);
                    if (cave != null && cave.getY() < surface - 1) {
                        spots.add(cave);
                    }
                }
            }
        }
        return spots;
    }

    private static void addIfPresent(List<BlockPos> spots, BlockPos spot) {
        if (spot != null) {
            spots.add(spot);
        }
    }

    // The ground floor reached by dropping from (x, y, z): the terrain surface when y is the surface air
    // cell, or a cave floor when y is inside a cave. Null when the drop lands nowhere an NPC could stand.
    private static BlockPos resolveGroundSpot(ServerLevel level, int x, int y, int z) {
        BlockPos ground = SpawnPlacementTypes.ON_GROUND.adjustSpawnPosition(level, new BlockPos(x, y, z));
        return SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, ground, LunaNpcEntities.NPC) ? ground : null;
    }

    // The air spawn cell: a Y inside both the flyer's world-height window and its height-above-ground
    // window, clamped to the dimension. Null when the two windows don't overlap or the cell isn't open.
    private static BlockPos resolveAirSpot(ServerLevel level, int x, int z, int surface, NpcAirSpawn air, int hintY) {
        int lo = Math.max(Math.max(air.minWorldY(), surface + air.minAboveGround()), level.getMinY());
        int hi = Math.min(Math.min(air.maxWorldY(), surface + air.maxAboveGround()), level.getMaxY());
        if (lo > hi) {
            return null;
        }
        int y = lo + Math.floorMod(hintY, hi - lo + 1);
        BlockPos pos = new BlockPos(x, y, z);
        return SpawnPlacementTypes.NO_RESTRICTIONS.isSpawnPositionOk(level, pos, LunaNpcEntities.NPC) ? pos : null;
    }

    // One spot per column inside the NPC's depth band, so a deep ocean is no denser than a shallow pool
    // but the creator still controls how far down its swimmers sit. Picks a Y within
    // [surface-maxDepth, surface-minDepth], clamped to real fluid (never in the seabed) and guaranteed a
    // fluid cell above so nothing surfaces or pokes out. Null when the column has no swimmable fluid or
    // the band doesn't reach any submerged cell.
    private static BlockPos resolveWaterSpot(ServerLevel level, int x, int z, int surface,
                                             NpcWaterSpawn water, int hintY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, surface, z);
        while (cursor.getY() > level.getMinY()
                && !isSwimFluid(level, cursor, true) && !isSwimFluid(level, cursor, false)) {
            cursor.move(0, -1, 0);
        }
        boolean isWater = isSwimFluid(level, cursor, true);
        if (!isWater && !isSwimFluid(level, cursor, false)) {
            return null;
        }
        int top = cursor.getY();
        int bottom = top;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(x, top - 1, z);
        while (probe.getY() > level.getMinY() && isSwimFluid(level, probe, isWater)) {
            bottom = probe.getY();
            probe.move(0, -1, 0);
        }
        int hi = top - Math.max(1, water.minDepth());
        int lo = Math.max(bottom, top - water.maxDepth());
        if (lo > hi) {
            return null;
        }
        int y = lo + Math.floorMod(hintY, hi - lo + 1);
        BlockPos spot = new BlockPos(x, y, z);
        if (!isSwimFluid(level, spot, isWater) || !isSwimFluid(level, spot.above(), isWater)) {
            return null;
        }
        return (isWater ? SpawnPlacementTypes.IN_WATER : SpawnPlacementTypes.IN_LAVA)
                .isSpawnPositionOk(level, spot, LunaNpcEntities.NPC) ? spot : null;
    }

    private static boolean isSwimFluid(ServerLevel level, BlockPos pos, boolean water) {
        net.minecraft.world.level.material.FluidState fluid = level.getFluidState(pos);
        return water ? fluid.is(net.minecraft.tags.FluidTags.WATER)
                : fluid.is(net.minecraft.tags.FluidTags.LAVA);
    }

    // The surface/cave rule for this NPC at this spot: a zone the spot falls inside wins with its own
    // mode; otherwise the NPC's per-definition master mode (used by biome and structure spawns).
    private static SurfaceMode applicableSurfaceMode(MinecraftServer server, NpcDefinition npc, BlockPos pos, String dimensionId) {
        NpcSpawnSettings spawn = npc.spawn();
        if (!spawn.zoneSpawns().isEmpty()) {
            ZoneManager zones = ZoneManager.get(server);
            for (NpcZoneSpawn zoneSpawn : spawn.zoneSpawns()) {
                NpcZone zone = zones.byId(zoneSpawn.zoneId());
                if (zone != null && zone.contains(dimensionId, pos)) {
                    return zone.surfaceMode();
                }
            }
        }
        return spawn.surfaceMode();
    }

    // Surface/cave gating plus a best-effort build-safety heuristic: a surface spawn needs open sky
    // (so roofed builds are skipped), an underground spawn needs a natural floor (so crafted house
    // floors are skipped).
    private static boolean passesPlacementRules(ServerLevel level, BlockPos pos, SurfaceMode mode) {
        boolean sky = level.canSeeSky(pos);
        return switch (mode) {
            case SURFACE_ONLY -> sky;
            case CAVES_ONLY -> !sky && floorIsNatural(level, pos);
            case ANYWHERE -> sky || floorIsNatural(level, pos);
        };
    }

    // A block an NPC could plausibly stand on in unspoiled terrain: stone, dirt, sand, gravel and the
    // nether/end equivalents. Player builds use planks, bricks, concrete and the like, which fail this,
    // so it keeps most underground spawns out of houses without a full structure scan.
    private static boolean floorIsNatural(ServerLevel level, BlockPos pos) {
        BlockState floor = level.getBlockState(pos.below());
        return floor.is(BlockTags.BASE_STONE_OVERWORLD)
                || floor.is(BlockTags.DIRT)
                || floor.is(BlockTags.SAND)
                || floor.is(Blocks.GRAVEL)
                || floor.is(BlockTags.BASE_STONE_NETHER)
                || floor.is(Blocks.END_STONE)
                || floor.is(Blocks.MOSS_BLOCK);
    }

    private static SpawnCandidate weightedPick(List<SpawnCandidate> candidates, RandomSource random) {
        int total = 0;
        for (SpawnCandidate candidate : candidates) {
            total += candidate.weight();
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (SpawnCandidate candidate : candidates) {
            roll -= candidate.weight();
            if (roll < 0) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static void tickRespawns(MinecraftServer server) {
        NpcRespawnManager respawns = NpcRespawnManager.get(server);
        long now = server.overworld().getGameTime();
        List<Pending> due = respawns.pollDue(now);
        if (due.isEmpty()) {
            return;
        }
        NpcRegistry registry = LunaNpcApi.npcs(server);
        for (Pending pending : due) {
            if (!tryRespawn(server, registry, pending)) {
                respawns.schedule(pending.lunaId(), pending.dimension(), pending.pos(),
                        now + RESPAWN_RETRY_TICKS, pending.suppressed());
            }
        }
    }

    // Spawns one due respawn. Returns false only when the world isn't ready yet (so it gets
    // rescheduled); a deleted NPC returns true so it drops out of the queue for good.
    private static boolean tryRespawn(MinecraftServer server, NpcRegistry registry, Pending pending) {
        ServerLevel level = levelByDimension(server, pending.dimension());
        if (level == null || !level.hasChunk(pending.pos().getX() >> 4, pending.pos().getZ() >> 4)) {
            return false;
        }
        NpcDefinition definition = registry.byLunaId(pending.lunaId()).orElse(null);
        if (definition == null) {
            return true;
        }
        // A respawn is a deliberate "bring this NPC back", so it returns persistent (the placed rule),
        // not despawnable like a fresh wild spawn. A suppressed natural farmer stays jobless.
        NpcEntity respawned = spawnNpc(level, definition, pending.pos(),
                level.getRandom().nextFloat() * 360.0F, EntitySpawnReason.MOB_SUMMONED);
        if (pending.suppressed() && respawned != null) {
            respawned.suppressJob();
        }
        return true;
    }

    private static ServerLevel levelByDimension(MinecraftServer server, String dimensionId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimensionId)) {
                return level;
            }
        }
        return null;
    }

    // Spawns exactly like the manual spawn path, tagging the entity's origin so its own future
    // respawns land back here rather than wherever it happened to die. The reason decides persistence:
    // NATURAL for a wild spawn (despawnable by default), MOB_SUMMONED for a respawn (persistent).
    private static NpcEntity spawnNpc(ServerLevel level, NpcDefinition definition, BlockPos pos, float yaw,
                                      EntitySpawnReason reason) {
        return NpcEntity.spawn(level, definition, reason,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, pos);
    }
}
