package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.AABB;

// Pure 3D geometry for a spawn zone — no id, name, or flags (those live on NpcZone). Four kinds: a
// box between two corners, a vertical cylinder, a sphere, and a prism raised from a footprint polygon
// (up to 250 anchors, concave allowed). contains() tests a block; when ignoreHeight is set the shape
// collapses to its full-height footprint column, so a flat shape and a tall one spawn identically.
public sealed interface ZoneShape {

    int MAX_ANCHORS = 250;

    Type type();

    boolean contains(BlockPos pos, boolean ignoreHeight);

    // The shape's own vertical span (before ignoreHeight); a flat shape has minY == maxY, which is
    // what drives the "this zone can't produce a valid spawn" warning.
    int minY();

    int maxY();

    // Axis-aligned bounds for render culling and surface sampling (max faces are exclusive, +1).
    AABB bounds();

    // Plain-int factories so a scripter never has to build BlockPos objects by hand.
    static ZoneShape box(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Box(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2));
    }

    static ZoneShape cylinder(int centerX, int centerZ, int radius, int bottomY, int topY) {
        return new Cylinder(new BlockPos(centerX, bottomY, centerZ), radius, bottomY, topY);
    }

    static ZoneShape sphere(int centerX, int centerY, int centerZ, int radius) {
        return new Sphere(new BlockPos(centerX, centerY, centerZ), radius);
    }

    static ZoneShape polygon(List<BlockPos> anchors) {
        return new Polygon(List.copyOf(anchors));
    }

    Codec<ZoneShape> CODEC = Type.CODEC.dispatch(ZoneShape::type, Type::mapCodec);

    StreamCodec<ByteBuf, ZoneShape> STREAM_CODEC = StreamCodec.of(
            (buffer, shape) -> {
                buffer.writeByte(shape.type().ordinal());
                switch (shape) {
                    case Box box -> Box.STREAM_CODEC.encode(buffer, box);
                    case Cylinder cylinder -> Cylinder.STREAM_CODEC.encode(buffer, cylinder);
                    case Sphere sphere -> Sphere.STREAM_CODEC.encode(buffer, sphere);
                    case Polygon polygon -> Polygon.STREAM_CODEC.encode(buffer, polygon);
                }
            },
            buffer -> switch (Type.values()[buffer.readByte()]) {
                case BOX -> Box.STREAM_CODEC.decode(buffer);
                case CYLINDER -> Cylinder.STREAM_CODEC.decode(buffer);
                case SPHERE -> Sphere.STREAM_CODEC.decode(buffer);
                case POLYGON -> Polygon.STREAM_CODEC.decode(buffer);
            });

    enum Type implements StringRepresentable {
        BOX("box"),
        CYLINDER("cylinder"),
        SPHERE("sphere"),
        POLYGON("polygon");

        static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        private final String id;

        Type(String id) {
            this.id = id;
        }

        MapCodec<? extends ZoneShape> mapCodec() {
            return switch (this) {
                case BOX -> Box.MAP_CODEC;
                case CYLINDER -> Cylinder.MAP_CODEC;
                case SPHERE -> Sphere.MAP_CODEC;
                case POLYGON -> Polygon.MAP_CODEC;
            };
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    // An axis-aligned box between two opposite corners (either order works). This is what an old
    // Create-Cube cube migrates into.
    record Box(BlockPos corner1, BlockPos corner2) implements ZoneShape {

        static final MapCodec<Box> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                BlockPos.CODEC.fieldOf("corner1").forGetter(Box::corner1),
                BlockPos.CODEC.fieldOf("corner2").forGetter(Box::corner2)
        ).apply(instance, Box::new));

        static final StreamCodec<ByteBuf, Box> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Box::corner1,
                BlockPos.STREAM_CODEC, Box::corner2,
                Box::new);

        @Override
        public Type type() {
            return Type.BOX;
        }

        @Override
        public int minY() {
            return Math.min(corner1.getY(), corner2.getY());
        }

        @Override
        public int maxY() {
            return Math.max(corner1.getY(), corner2.getY());
        }

        @Override
        public boolean contains(BlockPos pos, boolean ignoreHeight) {
            if (pos.getX() < Math.min(corner1.getX(), corner2.getX())
                    || pos.getX() > Math.max(corner1.getX(), corner2.getX())
                    || pos.getZ() < Math.min(corner1.getZ(), corner2.getZ())
                    || pos.getZ() > Math.max(corner1.getZ(), corner2.getZ())) {
                return false;
            }
            return ignoreHeight || (pos.getY() >= minY() && pos.getY() <= maxY());
        }

        @Override
        public AABB bounds() {
            return new AABB(
                    Math.min(corner1.getX(), corner2.getX()), minY(), Math.min(corner1.getZ(), corner2.getZ()),
                    Math.max(corner1.getX(), corner2.getX()) + 1, maxY() + 1, Math.max(corner1.getZ(), corner2.getZ()) + 1);
        }
    }

    // A vertical cylinder: a circle of the given radius around the center's column, capped between
    // bottomY and topY.
    record Cylinder(BlockPos center, int radius, int bottomY, int topY) implements ZoneShape {

        static final MapCodec<Cylinder> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                BlockPos.CODEC.fieldOf("center").forGetter(Cylinder::center),
                Codec.INT.fieldOf("radius").forGetter(Cylinder::radius),
                Codec.INT.fieldOf("bottom_y").forGetter(Cylinder::bottomY),
                Codec.INT.fieldOf("top_y").forGetter(Cylinder::topY)
        ).apply(instance, Cylinder::new));

        static final StreamCodec<ByteBuf, Cylinder> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Cylinder::center,
                ByteBufCodecs.VAR_INT, Cylinder::radius,
                ByteBufCodecs.VAR_INT, Cylinder::bottomY,
                ByteBufCodecs.VAR_INT, Cylinder::topY,
                Cylinder::new);

        @Override
        public Type type() {
            return Type.CYLINDER;
        }

        @Override
        public int minY() {
            return Math.min(bottomY, topY);
        }

        @Override
        public int maxY() {
            return Math.max(bottomY, topY);
        }

        @Override
        public boolean contains(BlockPos pos, boolean ignoreHeight) {
            double dx = pos.getX() + 0.5 - (center.getX() + 0.5);
            double dz = pos.getZ() + 0.5 - (center.getZ() + 0.5);
            if (dx * dx + dz * dz > (double) radius * radius) {
                return false;
            }
            return ignoreHeight || (pos.getY() >= minY() && pos.getY() <= maxY());
        }

        @Override
        public AABB bounds() {
            return new AABB(center.getX() - radius, minY(), center.getZ() - radius,
                    center.getX() + radius + 1, maxY() + 1, center.getZ() + radius + 1);
        }
    }

    // A true 3D ball. With ignoreHeight it degrades to the cylinder of its widest circle.
    record Sphere(BlockPos center, int radius) implements ZoneShape {

        static final MapCodec<Sphere> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                BlockPos.CODEC.fieldOf("center").forGetter(Sphere::center),
                Codec.INT.fieldOf("radius").forGetter(Sphere::radius)
        ).apply(instance, Sphere::new));

        static final StreamCodec<ByteBuf, Sphere> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Sphere::center,
                ByteBufCodecs.VAR_INT, Sphere::radius,
                Sphere::new);

        @Override
        public Type type() {
            return Type.SPHERE;
        }

        @Override
        public int minY() {
            return center.getY() - radius;
        }

        @Override
        public int maxY() {
            return center.getY() + radius;
        }

        @Override
        public boolean contains(BlockPos pos, boolean ignoreHeight) {
            double dx = pos.getX() + 0.5 - (center.getX() + 0.5);
            double dz = pos.getZ() + 0.5 - (center.getZ() + 0.5);
            if (ignoreHeight) {
                return dx * dx + dz * dz <= (double) radius * radius;
            }
            double dy = pos.getY() + 0.5 - (center.getY() + 0.5);
            return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
        }

        @Override
        public AABB bounds() {
            return new AABB(center.getX() - radius, minY(), center.getZ() - radius,
                    center.getX() + radius + 1, maxY() + 1, center.getZ() + radius + 1);
        }
    }

    // A prism raised from a closed footprint polygon (concave allowed). The anchors carry their own
    // Y, so the vertical span runs from the lowest to the highest anchor — walk a ring at y40 and a
    // ring at y80 and the prism spans 40..80. All anchors on one level makes a flat plane.
    record Polygon(List<BlockPos> anchors) implements ZoneShape {

        public Polygon {
            anchors = List.copyOf(anchors);
        }

        static final MapCodec<Polygon> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                BlockPos.CODEC.listOf().fieldOf("anchors").forGetter(Polygon::anchors)
        ).apply(instance, Polygon::new));

        static final StreamCodec<ByteBuf, Polygon> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), Polygon::anchors,
                Polygon::new);

        @Override
        public Type type() {
            return Type.POLYGON;
        }

        @Override
        public int minY() {
            return anchors.stream().mapToInt(BlockPos::getY).min().orElse(0);
        }

        @Override
        public int maxY() {
            return anchors.stream().mapToInt(BlockPos::getY).max().orElse(0);
        }

        @Override
        public boolean contains(BlockPos pos, boolean ignoreHeight) {
            if (anchors.size() < 3 || !footprintContains(pos.getX() + 0.5, pos.getZ() + 0.5)) {
                return false;
            }
            return ignoreHeight || (pos.getY() >= minY() && pos.getY() <= maxY());
        }

        // Ray-casting point-in-polygon over the anchors' XZ projection; handles concave outlines.
        private boolean footprintContains(double x, double z) {
            boolean inside = false;
            int count = anchors.size();
            for (int i = 0, j = count - 1; i < count; j = i++) {
                double xi = anchors.get(i).getX() + 0.5;
                double zi = anchors.get(i).getZ() + 0.5;
                double xj = anchors.get(j).getX() + 0.5;
                double zj = anchors.get(j).getZ() + 0.5;
                if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                    inside = !inside;
                }
            }
            return inside;
        }

        @Override
        public AABB bounds() {
            int minX = anchors.stream().mapToInt(BlockPos::getX).min().orElse(0);
            int maxX = anchors.stream().mapToInt(BlockPos::getX).max().orElse(0);
            int minZ = anchors.stream().mapToInt(BlockPos::getZ).min().orElse(0);
            int maxZ = anchors.stream().mapToInt(BlockPos::getZ).max().orElse(0);
            return new AABB(minX, minY(), minZ, maxX + 1, maxY() + 1, maxZ + 1);
        }
    }
}
