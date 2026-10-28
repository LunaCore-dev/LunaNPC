package net.lunacore.luna_npc.combat.advanced;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.api.NpcCombatPlacement;

// Turns a placement (shape + distances + count, anchored to self or the target) into the concrete world
// points an attack, hazard or teleport lands on. All angles are degrees, 0 = +X (east), spread clockwise.
final class PlacementResolver {

    private static final double ABOVE_OFFSET = 3.0;

    private PlacementResolver() {
    }

    static List<Vec3> resolve(NpcCombatPlacement placement, CombatContext ctx, LivingEntity anchorTarget) {
        Vec3 centre = placement.centre() == net.lunacore.luna_npc.api.NpcPlacementCentre.TARGET && anchorTarget != null
                ? anchorTarget.position() : ctx.npc.position();
        LivingEntity toward = anchorTarget != null ? anchorTarget : ctx.primaryTarget;
        double aim = toward != null
                ? Math.atan2(toward.getZ() - centre.z, toward.getX() - centre.x)
                : Math.toRadians(ctx.npc.getYRot() + 90.0F);
        int count = Math.max(1, placement.count());
        double min = placement.minDistance();
        double max = placement.maxDistance();
        double offset = Math.toRadians(placement.angleOffset());
        List<Vec3> raw = new ArrayList<>();
        switch (placement.shape()) {
            case AT_CENTRE -> raw.add(centre);
            case CARDINAL_4 -> ringPoints(raw, centre, max, 4, offset);
            case STAR_8 -> ringPoints(raw, centre, max, 8, offset);
            case RING -> ringPoints(raw, centre, max, count, offset);
            case LINE_TO_TARGET -> linePoints(raw, centre, aim, min, max, count);
            case ARC_TO_TARGET -> arcPoints(raw, centre, aim, max, count, placement.angleOffset());
            case RANDOM_IN_AREA -> randomPoints(raw, ctx, centre, min, max, count);
            case GRID -> gridPoints(raw, centre, max, count);
        }
        List<Vec3> out = new ArrayList<>(raw.size());
        for (Vec3 point : raw) {
            out.add(applyY(point, placement.yMode(), centre.y, ctx));
        }
        return out;
    }

    private static void ringPoints(List<Vec3> out, Vec3 centre, double radius, int count, double offset) {
        for (int i = 0; i < count; i++) {
            double angle = offset + i * (Math.PI * 2.0 / count);
            out.add(radial(centre, angle, radius));
        }
    }

    private static void linePoints(List<Vec3> out, Vec3 centre, double aim, double min, double max, int count) {
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 1.0 : (double) i / (count - 1);
            out.add(radial(centre, aim, Mth.lerp(t, min, max)));
        }
    }

    private static void arcPoints(List<Vec3> out, Vec3 centre, double aim, double radius, int count, int spanDegrees) {
        double span = Math.toRadians(spanDegrees <= 0 ? 90 : spanDegrees);
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.5 : (double) i / (count - 1);
            out.add(radial(centre, aim - span / 2.0 + span * t, radius));
        }
    }

    private static void randomPoints(List<Vec3> out, CombatContext ctx, Vec3 centre, double min, double max, int count) {
        for (int i = 0; i < count; i++) {
            double angle = ctx.engine.random().nextDouble() * Math.PI * 2.0;
            double dist = min + ctx.engine.random().nextDouble() * Math.max(0.0, max - min);
            out.add(radial(centre, angle, dist));
        }
    }

    private static void gridPoints(List<Vec3> out, Vec3 centre, double half, int count) {
        int side = Math.max(1, Mth.ceil(Math.sqrt(count)));
        double step = side <= 1 ? 0.0 : half * 2.0 / (side - 1);
        for (int row = 0; row < side && out.size() < count; row++) {
            for (int col = 0; col < side && out.size() < count; col++) {
                out.add(new Vec3(centre.x - half + col * step, centre.y, centre.z - half + row * step));
            }
        }
    }

    private static Vec3 radial(Vec3 centre, double angle, double radius) {
        return new Vec3(centre.x + Math.cos(angle) * radius, centre.y, centre.z + Math.sin(angle) * radius);
    }

    private static Vec3 applyY(Vec3 point, int yMode, double centreY, CombatContext ctx) {
        return switch (yMode) {
            case 0 -> new Vec3(point.x, ctx.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(point.x), Mth.floor(point.z)), point.z);
            case 2 -> new Vec3(point.x, centreY + ABOVE_OFFSET, point.z);
            default -> new Vec3(point.x, centreY, point.z);
        };
    }
}
