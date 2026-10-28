package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// A named patrol route in one dimension, built with the Route Creator stick and shared by every NPC whose
// patrol job references its id. The first point is the anchor: every point stays within 300 blocks of it,
// and each point sits 1-50 blocks from the one before (placementProblem is the one place those rules
// live, used by the stick, the editor and the server alike).
public record NpcRoute(String id, String name, String dimension, List<NpcRoutePoint> points, NpcRouteSpeed speed) {

    public NpcRoute {
        points = List.copyOf(points);
    }

    public enum PlacementProblem { NONE, FULL, TOO_CLOSE, TOO_FAR_FROM_PREVIOUS, TOO_FAR_FROM_ANCHOR }

    public static final Codec<NpcRoute> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(NpcRoute::id),
            Codec.STRING.fieldOf("name").forGetter(NpcRoute::name),
            Codec.STRING.fieldOf("dimension").forGetter(NpcRoute::dimension),
            NpcRoutePoint.CODEC.listOf().fieldOf("points").forGetter(NpcRoute::points),
            NpcRouteSpeed.CODEC.optionalFieldOf("speed", NpcRouteSpeed.WALK).forGetter(NpcRoute::speed)
    ).apply(instance, NpcRoute::new));

    public static final StreamCodec<ByteBuf, NpcRoute> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcRoute::id,
            ByteBufCodecs.STRING_UTF8, NpcRoute::name,
            ByteBufCodecs.STRING_UTF8, NpcRoute::dimension,
            NpcRoutePoint.STREAM_CODEC.apply(ByteBufCodecs.list()), NpcRoute::points,
            NpcRouteSpeed.STREAM_CODEC, NpcRoute::speed,
            NpcRoute::new);

    // Whether candidate may be appended after the existing points, under a point cap (the GUI's or the API's).
    public static PlacementProblem placementProblem(List<BlockPos> existing, BlockPos candidate, int maxPoints) {
        if (existing.size() >= maxPoints) {
            return PlacementProblem.FULL;
        }
        if (existing.isEmpty()) {
            return PlacementProblem.NONE;
        }
        double fromPrevious = Math.sqrt(existing.getLast().distSqr(candidate));
        if (fromPrevious < NpcValidation.MIN_ROUTE_POINT_SPACING) {
            return PlacementProblem.TOO_CLOSE;
        }
        if (fromPrevious > NpcValidation.MAX_ROUTE_POINT_SPACING) {
            return PlacementProblem.TOO_FAR_FROM_PREVIOUS;
        }
        if (Math.sqrt(existing.getFirst().distSqr(candidate)) > NpcValidation.MAX_ROUTE_ANCHOR_DISTANCE) {
            return PlacementProblem.TOO_FAR_FROM_ANCHOR;
        }
        return PlacementProblem.NONE;
    }

    public static List<BlockPos> positionsOf(List<NpcRoutePoint> points) {
        return points.stream().map(NpcRoutePoint::pos).toList();
    }

    public NpcRoutePoint point(int index) {
        return points.get(index);
    }

    public int size() {
        return points.size();
    }

    // The leg leaving point index is run when the route says so (always, or that point's own run flag).
    public boolean runsFrom(int index) {
        return switch (speed) {
            case WALK -> false;
            case RUN -> true;
            case PER_POINT -> index >= 0 && index < points.size() && points.get(index).run();
        };
    }

    public int nearestPointIndex(double x, double y, double z) {
        int nearest = 0;
        double best = Double.MAX_VALUE;
        for (int index = 0; index < points.size(); index++) {
            BlockPos pos = points.get(index).pos();
            double dx = pos.getX() + 0.5 - x;
            double dy = pos.getY() - y;
            double dz = pos.getZ() + 0.5 - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < best) {
                best = distance;
                nearest = index;
            }
        }
        return nearest;
    }

    public NpcRoute withName(String newName) {
        return new NpcRoute(id, newName, dimension, points, speed);
    }

    public NpcRoute withPoints(List<NpcRoutePoint> newPoints) {
        return new NpcRoute(id, name, dimension, newPoints, speed);
    }

    public NpcRoute withSpeed(NpcRouteSpeed newSpeed) {
        return new NpcRoute(id, name, dimension, points, newSpeed);
    }

    public NpcRoute withPoint(int index, NpcRoutePoint point) {
        List<NpcRoutePoint> updated = new ArrayList<>(points);
        updated.set(index, point);
        return withPoints(updated);
    }

    // Every point obeys the placement rules relative to the points before it, in order.
    public boolean isValid() {
        if (id == null || id.isBlank() || name == null || dimension == null || speed == null
                || points.size() < NpcValidation.MIN_ROUTE_POINTS || points.size() > NpcValidation.MAX_ROUTE_POINTS) {
            return false;
        }
        List<BlockPos> placed = new ArrayList<>();
        for (NpcRoutePoint point : points) {
            if (!point.isValid()
                    || placementProblem(placed, point.pos(), NpcValidation.MAX_ROUTE_POINTS) != PlacementProblem.NONE) {
                return false;
            }
            placed.add(point.pos());
        }
        return true;
    }
}
