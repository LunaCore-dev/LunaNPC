package net.lunacore.luna_npc.api;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.api.NpcZoneSpawn;
import net.lunacore.luna_npc.api.SurfaceMode;

// Everything about where and how an NPC comes into the world on its own. Respawn brings a killed
// NPC back after a delay (at its original spawn spot, or where it died). Natural spawning mimics
// vanilla mob spawning but is fully creator-controlled: a weight per biome (global or per-biome),
// per-structure weights, and per-zone weights with a max count; the light band and movement-type
// placement gate where it may appear. Weights work like vanilla spawn weights (chance = weight /
// sum of the competing weights at a spot). All zero / disabled by default — an NPC spawns naturally
// only once the creator opts in. leaveArea lets the NPC wander out of the area it spawned in (the
// zone it appeared in, or a leash around its origin sized to its wander range); leaveAreaInCombat
// separately lets it chase a target out of that area and return afterwards. Both on by default —
// an NPC roams freely like vanilla; disable them to tether it to its spawn area.
// placedPersistent (default on) keeps a hand-placed or API-spawned NPC from ever despawning;
// naturalPersistent (default off) does the same for a world-spawned one — off means natural spawns
// despawn like vanilla mobs so they can't pile up.
public record NpcSpawnSettings(boolean respawnEnabled, int respawnMinSeconds, int respawnMaxSeconds,
                               boolean respawnAtDeath, boolean naturalSpawnEnabled, int globalBiomeChance,
                               Map<String, Integer> biomeChances, Map<String, Integer> structureChances,
                               List<NpcZoneSpawn> zoneSpawns, int maxNearby,
                               int minSpawnLight, int maxSpawnLight,
                               boolean leaveArea, boolean leaveAreaInCombat, SurfaceMode surfaceMode,
                               boolean placedPersistent, boolean naturalPersistent,
                               boolean useGlobalBiome, NpcAirSpawn airSpawn, NpcWaterSpawn waterSpawn) {

    public NpcSpawnSettings {
        biomeChances = Map.copyOf(biomeChances);
        structureChances = Map.copyOf(structureChances);
        zoneSpawns = List.copyOf(zoneSpawns);
    }

    public static final int MAX_RESPAWN_SECONDS = 1000;
    public static final int MAX_LIGHT = 15;

    private static final StreamCodec<ByteBuf, Map<String, Integer>> WEIGHT_MAP =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);
    private static final StreamCodec<ByteBuf, List<NpcZoneSpawn>> ZONE_LIST =
            NpcZoneSpawn.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final Codec<NpcSpawnSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("respawn", false).forGetter(NpcSpawnSettings::respawnEnabled),
            Codec.INT.optionalFieldOf("respawn_min_seconds", 30).forGetter(NpcSpawnSettings::respawnMinSeconds),
            Codec.INT.optionalFieldOf("respawn_max_seconds", 60).forGetter(NpcSpawnSettings::respawnMaxSeconds),
            Codec.BOOL.optionalFieldOf("respawn_at_death", false).forGetter(NpcSpawnSettings::respawnAtDeath),
            Codec.BOOL.optionalFieldOf("natural_spawn", false).forGetter(NpcSpawnSettings::naturalSpawnEnabled),
            Codec.BOOL.optionalFieldOf("use_global_biome", false).forGetter(NpcSpawnSettings::useGlobalBiome),
            Codec.INT.optionalFieldOf("global_biome_weight", 0).forGetter(NpcSpawnSettings::globalBiomeChance),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("biome_weights", Map.of())
                    .forGetter(NpcSpawnSettings::biomeChances),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("structure_weights", Map.of())
                    .forGetter(NpcSpawnSettings::structureChances),
            NpcZoneSpawn.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(NpcSpawnSettings::zoneSpawns),
            Codec.INT.optionalFieldOf("max_nearby", 0).forGetter(NpcSpawnSettings::maxNearby),
            // RecordCodecBuilder groups cap at 16, so the light band, the two leave-area flags and the two
            // persistence flags each share one slot as a flat Pair. They still serialize as their own fields.
            Codec.mapPair(
                    Codec.INT.optionalFieldOf("min_light", 0),
                    Codec.INT.optionalFieldOf("max_light", MAX_LIGHT))
                    .forGetter(s -> Pair.of(s.minSpawnLight(), s.maxSpawnLight())),
            Codec.mapPair(
                    Codec.BOOL.optionalFieldOf("leave_area", true),
                    Codec.BOOL.optionalFieldOf("leave_area_in_combat", true))
                    .forGetter(s -> Pair.of(s.leaveArea(), s.leaveAreaInCombat())),
            SurfaceMode.CODEC.optionalFieldOf("surface_mode", SurfaceMode.ANYWHERE).forGetter(NpcSpawnSettings::surfaceMode),
            Codec.mapPair(
                    Codec.BOOL.optionalFieldOf("placed_persistent", true),
                    Codec.BOOL.optionalFieldOf("natural_persistent", false))
                    .forGetter(s -> Pair.of(s.placedPersistent(), s.naturalPersistent())),
            // air_spawn and water_spawn share a slot as a flat Pair to stay inside the 16-group cap;
            // both still serialize as their own map fields.
            Codec.mapPair(
                    NpcAirSpawn.CODEC.optionalFieldOf("air_spawn", NpcAirSpawn.defaults()),
                    NpcWaterSpawn.CODEC.optionalFieldOf("water_spawn", NpcWaterSpawn.defaults()))
                    .forGetter(s -> Pair.of(s.airSpawn(), s.waterSpawn()))
    ).apply(instance, (respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath, naturalSpawnEnabled,
                       useGlobalBiome, globalBiomeChance, biomeChances, structureChances, zoneSpawns, maxNearby,
                       lightBand, leaveBand, surfaceMode, persistence, mediumSpawns) ->
            new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                    naturalSpawnEnabled, globalBiomeChance, biomeChances, structureChances, zoneSpawns, maxNearby,
                    lightBand.getFirst(), lightBand.getSecond(), leaveBand.getFirst(), leaveBand.getSecond(),
                    surfaceMode, persistence.getFirst(), persistence.getSecond(), useGlobalBiome,
                    mediumSpawns.getFirst(), mediumSpawns.getSecond())));

    // Fifteen fields with two maps and a list exceed StreamCodec.composite, so the network codec is hand-rolled.
    public static final StreamCodec<ByteBuf, NpcSpawnSettings> STREAM_CODEC = StreamCodec.of(
            (buffer, settings) -> {
                ByteBufCodecs.BOOL.encode(buffer, settings.respawnEnabled());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.respawnMinSeconds());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.respawnMaxSeconds());
                ByteBufCodecs.BOOL.encode(buffer, settings.respawnAtDeath());
                ByteBufCodecs.BOOL.encode(buffer, settings.naturalSpawnEnabled());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.globalBiomeChance());
                WEIGHT_MAP.encode(buffer, settings.biomeChances());
                WEIGHT_MAP.encode(buffer, settings.structureChances());
                ZONE_LIST.encode(buffer, settings.zoneSpawns());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.maxNearby());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.minSpawnLight());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.maxSpawnLight());
                ByteBufCodecs.BOOL.encode(buffer, settings.leaveArea());
                ByteBufCodecs.BOOL.encode(buffer, settings.leaveAreaInCombat());
                SurfaceMode.STREAM_CODEC.encode(buffer, settings.surfaceMode());
                ByteBufCodecs.BOOL.encode(buffer, settings.placedPersistent());
                ByteBufCodecs.BOOL.encode(buffer, settings.naturalPersistent());
                ByteBufCodecs.BOOL.encode(buffer, settings.useGlobalBiome());
                NpcAirSpawn.STREAM_CODEC.encode(buffer, settings.airSpawn());
                NpcWaterSpawn.STREAM_CODEC.encode(buffer, settings.waterSpawn());
            },
            buffer -> new NpcSpawnSettings(
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    WEIGHT_MAP.decode(buffer),
                    WEIGHT_MAP.decode(buffer),
                    ZONE_LIST.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    SurfaceMode.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    NpcAirSpawn.STREAM_CODEC.decode(buffer),
                    NpcWaterSpawn.STREAM_CODEC.decode(buffer)));

    public static NpcSpawnSettings defaults() {
        return new NpcSpawnSettings(false, 30, 60, false, false, 0,
                Map.of(), Map.of(), List.of(), 0, 0, MAX_LIGHT, true, true, SurfaceMode.ANYWHERE, true, false,
                false, NpcAirSpawn.defaults(), NpcWaterSpawn.defaults());
    }

    // Additive copy-methods for scripting/API use: each returns a new record via the canonical
    // constructor with one field (or a coherent group) changed, preserving immutability. The methods
    // that opt into a spawn source also flip naturalSpawnEnabled on, so a single call is enough to arm it.

    public NpcSpawnSettings withNaturalSpawn(boolean on) {
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                on, globalBiomeChance, biomeChances, structureChances, zoneSpawns, maxNearby,
                minSpawnLight, maxSpawnLight, leaveArea, leaveAreaInCombat, surfaceMode, placedPersistent, naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    public NpcSpawnSettings withGlobalBiomeChance(int weight) {
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                true, weight, biomeChances, structureChances, zoneSpawns, maxNearby,
                minSpawnLight, maxSpawnLight, leaveArea, leaveAreaInCombat, surfaceMode, placedPersistent, naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    public NpcSpawnSettings withBiomeChance(String biomeId, int weight) {
        Map<String, Integer> weights = new HashMap<>(biomeChances);
        weights.put(biomeId, weight);
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                true, globalBiomeChance, weights, structureChances, zoneSpawns, maxNearby,
                minSpawnLight, maxSpawnLight, leaveArea, leaveAreaInCombat, surfaceMode, placedPersistent, naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    public NpcSpawnSettings withStructureChance(String structureId, int weight) {
        Map<String, Integer> weights = new HashMap<>(structureChances);
        weights.put(structureId, weight);
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                true, globalBiomeChance, biomeChances, weights, zoneSpawns, maxNearby,
                minSpawnLight, maxSpawnLight, leaveArea, leaveAreaInCombat, surfaceMode, placedPersistent, naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    // Replaces any existing spawn for the same zone so a zone is never listed twice.
    public NpcSpawnSettings withZoneSpawn(NpcZoneSpawn spawn) {
        List<NpcZoneSpawn> zones = new ArrayList<>(zoneSpawns);
        zones.removeIf(existing -> existing.zoneId().equals(spawn.zoneId()));
        zones.add(spawn);
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                true, globalBiomeChance, biomeChances, structureChances, zones, maxNearby,
                minSpawnLight, maxSpawnLight, leaveArea, leaveAreaInCombat, surfaceMode, placedPersistent, naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    public NpcSpawnSettings withZoneSpawn(String zoneId, int chance, int maxCount) {
        return withZoneSpawn(new NpcZoneSpawn(zoneId, chance, maxCount));
    }

    public NpcSpawnSettings withRespawn(int minSeconds, int maxSeconds, boolean atDeath) {
        return new NpcSpawnSettings(true, minSeconds, maxSeconds, atDeath,
                naturalSpawnEnabled, globalBiomeChance, biomeChances, structureChances, zoneSpawns, maxNearby,
                minSpawnLight, maxSpawnLight, leaveArea, leaveAreaInCombat, surfaceMode, placedPersistent, naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    public NpcSpawnSettings withMaxNearby(int max) {
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                naturalSpawnEnabled, globalBiomeChance, biomeChances, structureChances, zoneSpawns, max,
                minSpawnLight, maxSpawnLight, leaveArea, leaveAreaInCombat, surfaceMode, placedPersistent, naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    public NpcSpawnSettings withSurfaceMode(SurfaceMode mode) {
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                naturalSpawnEnabled, globalBiomeChance, biomeChances, structureChances, zoneSpawns, maxNearby,
                minSpawnLight, maxSpawnLight, leaveArea, leaveAreaInCombat, mode, placedPersistent, naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    public NpcSpawnSettings withLightBand(int min, int max) {
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                naturalSpawnEnabled, globalBiomeChance, biomeChances, structureChances, zoneSpawns, maxNearby,
                min, max, leaveArea, leaveAreaInCombat, surfaceMode, placedPersistent, naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    public NpcSpawnSettings withLeaveArea(boolean roam, boolean roamInCombat) {
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                naturalSpawnEnabled, globalBiomeChance, biomeChances, structureChances, zoneSpawns, maxNearby,
                minSpawnLight, maxSpawnLight, roam, roamInCombat, surfaceMode, placedPersistent, naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    public NpcSpawnSettings withPlacedPersistent(boolean persistent) {
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                naturalSpawnEnabled, globalBiomeChance, biomeChances, structureChances, zoneSpawns, maxNearby,
                minSpawnLight, maxSpawnLight, leaveArea, leaveAreaInCombat, surfaceMode, persistent,
                naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    public NpcSpawnSettings withNaturalPersistent(boolean persistent) {
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                naturalSpawnEnabled, globalBiomeChance, biomeChances, structureChances, zoneSpawns, maxNearby,
                minSpawnLight, maxSpawnLight, leaveArea, leaveAreaInCombat, surfaceMode, placedPersistent,
                persistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    public NpcSpawnSettings withUseGlobalBiome(boolean useGlobalBiome) {
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                naturalSpawnEnabled, globalBiomeChance, biomeChances, structureChances, zoneSpawns, maxNearby,
                minSpawnLight, maxSpawnLight, leaveArea, leaveAreaInCombat, surfaceMode, placedPersistent,
                naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    public NpcSpawnSettings withAirSpawn(NpcAirSpawn airSpawn) {
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                naturalSpawnEnabled, globalBiomeChance, biomeChances, structureChances, zoneSpawns, maxNearby,
                minSpawnLight, maxSpawnLight, leaveArea, leaveAreaInCombat, surfaceMode, placedPersistent,
                naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    public NpcSpawnSettings withWaterSpawn(NpcWaterSpawn waterSpawn) {
        return new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds, respawnAtDeath,
                naturalSpawnEnabled, globalBiomeChance, biomeChances, structureChances, zoneSpawns, maxNearby,
                minSpawnLight, maxSpawnLight, leaveArea, leaveAreaInCombat, surfaceMode, placedPersistent,
                naturalPersistent, useGlobalBiome, airSpawn, waterSpawn);
    }

    // Static factories: a default settings block already armed for one common spawn source.
    public static NpcSpawnSettings naturalInBiome(String biomeId, int chance) {
        return defaults().withBiomeChance(biomeId, chance);
    }

    public static NpcSpawnSettings inZone(String zoneId, int chance, int maxCount) {
        return defaults().withZoneSpawn(zoneId, chance, maxCount);
    }

    public static NpcSpawnSettings respawning(int minSeconds, int maxSeconds, boolean atDeath) {
        return defaults().withRespawn(minSeconds, maxSeconds, atDeath);
    }

    // The spawn value for a biome. In global mode the one global value covers every biome and every
    // dimension; otherwise it is strictly the biome's own value (0 — no spawn — where none is set). The
    // two modes are an OR, never combined.
    public int biomeChance(String biomeId) {
        return useGlobalBiome ? globalBiomeChance : biomeChances.getOrDefault(biomeId, 0);
    }

    public boolean isValid() {
        if (respawnMinSeconds < 0 || respawnMaxSeconds < respawnMinSeconds
                || respawnMaxSeconds > MAX_RESPAWN_SECONDS || globalBiomeChance < 0 || maxNearby < 0) {
            return false;
        }
        if (minSpawnLight < 0 || maxSpawnLight > MAX_LIGHT || minSpawnLight > maxSpawnLight) {
            return false;
        }
        for (int weight : biomeChances.values()) {
            if (weight < 0) {
                return false;
            }
        }
        for (int weight : structureChances.values()) {
            if (weight < 0) {
                return false;
            }
        }
        for (NpcZoneSpawn zone : zoneSpawns) {
            if (!zone.isValid()) {
                return false;
            }
        }
        return airSpawn.isValid() && waterSpawn.isValid();
    }
}
