package net.lunacore.luna_npc.api;

import java.util.List;
import net.lunacore.luna_npc.api.NpcZone;
import net.lunacore.luna_npc.api.SurfaceMode;
import net.lunacore.luna_npc.api.ZoneShape;

// Developer access to the named spawn zones — the regions the in-game Zone Wand builds. Everything the
// wand and the Manage Zones screen can do is here too: create a zone of any shape (box, cylinder,
// sphere, polygon), list them, rename and delete. Build a ZoneShape (e.g. new ZoneShape.Box(cornerA,
// cornerB), new ZoneShape.Cylinder(center, radius, bottomY, topY), or new ZoneShape.Polygon(anchors))
// and hand it to create(); an NPC then references zones by id in its spawn settings
// (NpcSpawnSettings.zoneSpawns, i.e. the "Zones" spawn method), and the world spawner uses them live.
public interface ZoneRegistry {

    List<NpcZone> all();

    NpcZone byId(String zoneId);

    // Creates a zone with an id generated from the name. shape is the geometry; ignoreHeight collapses
    // it to a full-height column; surfaceMode gates surface vs cave spawning — exactly the fields the
    // Zone Wand's create screen sets. Throws IllegalStateException if the zone limit is reached.
    NpcZone create(String name, String dimension, ZoneShape shape, boolean ignoreHeight, SurfaceMode surfaceMode);

    void rename(String zoneId, String name);

    void delete(String zoneId);

    // Replaces the stored zone sharing this one's id (its shape, flags and name). An API-only
    // convenience — in-game you delete and re-make. No-op when no zone has that id.
    void update(NpcZone zone);

    // ---- Coords-in-one-call convenience creators (Anywhere surface mode, height respected) ----

    default NpcZone createBox(String name, String dimension, int x1, int y1, int z1, int x2, int y2, int z2) {
        return create(name, dimension, ZoneShape.box(x1, y1, z1, x2, y2, z2), false, SurfaceMode.ANYWHERE);
    }

    default NpcZone createCylinder(String name, String dimension,
                                   int centerX, int centerZ, int radius, int bottomY, int topY) {
        return create(name, dimension, ZoneShape.cylinder(centerX, centerZ, radius, bottomY, topY),
                false, SurfaceMode.ANYWHERE);
    }

    default NpcZone createSphere(String name, String dimension, int centerX, int centerY, int centerZ, int radius) {
        return create(name, dimension, ZoneShape.sphere(centerX, centerY, centerZ, radius), false, SurfaceMode.ANYWHERE);
    }

    // ---- Lookup / upsert convenience ----

    // The first zone with this display name, or null.
    default NpcZone byName(String name) {
        return all().stream().filter(zone -> zone.name().equals(name)).findFirst().orElse(null);
    }

    // The box zone with this name, creating it if none exists — no duplicate on re-run.
    default NpcZone getOrCreateBox(String name, String dimension, int x1, int y1, int z1, int x2, int y2, int z2) {
        NpcZone existing = byName(name);
        return existing != null ? existing : createBox(name, dimension, x1, y1, z1, x2, y2, z2);
    }
}
