package net.lunacore.luna_npc.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.ZoneRegistry;
import net.lunacore.luna_npc.npc.NpcValidation;
import net.lunacore.luna_npc.api.NpcZone;
import net.lunacore.luna_npc.api.SurfaceMode;
import net.lunacore.luna_npc.api.ZoneShape;

// The world's named zones, created with the Zone Wand. A global registry persisted to zones.dat and
// synced to clients, referenced by NPCs as spawn areas. On first load it imports any regions from the
// legacy cubes.dat (the old Create Cube stick) as Box zones, so nothing placed before the redesign is
// lost. Mirrors the old CubeManager.
public final class ZoneManager extends SavedData implements ZoneRegistry {

    private static final Codec<ZoneManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcZone.CODEC.listOf().optionalFieldOf("zones", List.of())
                    .forGetter(manager -> List.copyOf(manager.zones.values())),
            Codec.BOOL.optionalFieldOf("migrated", false).forGetter(manager -> manager.migrated)
    ).apply(instance, ZoneManager::new));

    private static final SavedDataType<ZoneManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "zones"),
            ZoneManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<String, NpcZone> zones = new LinkedHashMap<>();
    private boolean migrated;

    private ZoneManager() {
    }

    private ZoneManager(List<NpcZone> stored, boolean migrated) {
        for (NpcZone zone : stored) {
            zones.put(zone.id(), zone);
        }
        this.migrated = migrated;
    }

    public static ZoneManager get(MinecraftServer server) {
        ZoneManager manager = server.getDataStorage().computeIfAbsent(TYPE);
        if (!manager.migrated) {
            manager.importLegacyCubes(server);
        }
        return manager;
    }

    // One-time import of the pre-redesign cubes.dat into zones (each cube becomes a Box zone). The
    // migrated flag then keeps deleted cubes from ever coming back.
    private void importLegacyCubes(MinecraftServer server) {
        LegacyCubes legacy = server.getDataStorage().computeIfAbsent(LegacyCubes.TYPE);
        for (LegacyCube cube : legacy.cubes) {
            if (!cube.id().isBlank() && !zones.containsKey(cube.id())) {
                zones.put(cube.id(), cube.toZone());
            }
        }
        migrated = true;
        setDirty();
    }

    @Override
    public List<NpcZone> all() {
        return List.copyOf(zones.values());
    }

    @Override
    public NpcZone byId(String id) {
        return zones.get(id);
    }

    // Replaces the whole set from a client edit: keeps valid, uniquely-id'd zones and sanitises names.
    public void applyAll(List<NpcZone> incoming) {
        Map<String, NpcZone> next = new LinkedHashMap<>();
        for (NpcZone zone : incoming) {
            if (zone.id().isBlank() || next.containsKey(zone.id()) || next.size() >= NpcValidation.MAX_ZONES) {
                continue;
            }
            next.put(zone.id(), zone.withName(sanitizeName(zone.name())));
        }
        zones.clear();
        zones.putAll(next);
        migrated = true;
        setDirty();
    }

    private static String sanitizeName(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.length() > NpcValidation.MAX_ZONE_NAME_LENGTH) {
            trimmed = trimmed.substring(0, NpcValidation.MAX_ZONE_NAME_LENGTH);
        }
        return trimmed.isEmpty() ? "Zone" : trimmed;
    }

    // ---- Developer API (ZoneRegistry); mirrors the Zone Wand + Manage Zones screen ----

    @Override
    public NpcZone create(String name, String dimension, ZoneShape shape,
                          boolean ignoreHeight, SurfaceMode surfaceMode) {
        if (zones.size() >= NpcValidation.MAX_ZONES) {
            throw new IllegalStateException("Zone limit of " + NpcValidation.MAX_ZONES + " reached");
        }
        String id = freshId(name);
        NpcZone zone = new NpcZone(id, sanitizeName(name), dimension, shape, ignoreHeight, surfaceMode);
        zones.put(id, zone);
        migrated = true;
        setDirty();
        return zone;
    }

    @Override
    public void rename(String zoneId, String name) {
        NpcZone zone = zones.get(zoneId);
        if (zone != null) {
            zones.put(zoneId, zone.withName(sanitizeName(name)));
            setDirty();
        }
    }

    @Override
    public void delete(String zoneId) {
        if (zones.remove(zoneId) != null) {
            setDirty();
        }
    }

    @Override
    public void update(NpcZone zone) {
        if (zone != null && zones.containsKey(zone.id())) {
            zones.put(zone.id(), zone.withName(sanitizeName(zone.name())));
            setDirty();
        }
    }

    // A unique id derived from the name (lowercased alphanumerics), suffixed until free.
    private String freshId(String name) {
        String base = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (base.isEmpty()) {
            base = "zone";
        }
        String id = base;
        int suffix = 2;
        while (zones.containsKey(id)) {
            id = base + suffix++;
        }
        return id;
    }

    // ---- Legacy cubes.dat migration support ----

    private record LegacyCube(String id, String name, String dimension, BlockPos corner1, BlockPos corner2) {
        static final Codec<LegacyCube> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(LegacyCube::id),
                Codec.STRING.fieldOf("name").forGetter(LegacyCube::name),
                Codec.STRING.fieldOf("dimension").forGetter(LegacyCube::dimension),
                BlockPos.CODEC.fieldOf("corner1").forGetter(LegacyCube::corner1),
                BlockPos.CODEC.fieldOf("corner2").forGetter(LegacyCube::corner2)
        ).apply(instance, LegacyCube::new));

        NpcZone toZone() {
            return new NpcZone(id, name, dimension, new ZoneShape.Box(corner1, corner2), false, SurfaceMode.ANYWHERE);
        }
    }

    private static final class LegacyCubes extends SavedData {
        static final Codec<LegacyCubes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LegacyCube.CODEC.listOf().optionalFieldOf("cubes", List.of()).forGetter(store -> store.cubes)
        ).apply(instance, LegacyCubes::new));

        static final SavedDataType<LegacyCubes> TYPE = new SavedDataType<>(
                Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "cubes"),
                () -> new LegacyCubes(List.of()), CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

        final List<LegacyCube> cubes;

        LegacyCubes(List<LegacyCube> cubes) {
            this.cubes = new ArrayList<>(cubes);
        }
    }
}
