package net.lunacore.luna_npc.client.render.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// Converts a Bedrock-space CustomModel into a vanilla ModelPart tree plus the measured collision box.
// Bedrock is Y-up with the feet at y=0; vanilla model space is Y-down with the feet at y=24, and the
// LivingEntityRenderer mirrors X, so a point maps as (x,y,z) -> (-x, 24 - y, z) and a bone/cube rotation
// negates its X and Y angles (Recipe A from the research). Winding is made bulletproof by orienting every
// face normal to its known outward axis; only per-face UV rotation can still be off and needs the one
// visual test the plan calls for. SX flips if a whole model comes out mirrored.
public final class CustomModelBaker {

    static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3, UP = 4, DOWN = 5;
    private static final float FEET_Y = 24.0F;
    // Bedrock -> vanilla model space: flip X and Y, keep Z. Real Blockbench/Bedrock models face -Z (a
    // model's face sits on the -Z side, same as vanilla), so Z is NOT flipped. M = diag(-1,-1,1) is a
    // proper rotation (det +1, a 180-deg turn about Z) so winding stays correct; conjugating a rotation by
    // it negates the X and Y angles and keeps Z — see rot().
    private static final float SX = -1.0F;
    private static final float SZ = 1.0F;
    private static final float[][] OUTWARD = {
            {0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private CustomModelBaker() {
    }

    // {width, height, eyeHeight} in blocks, used at import time to fill the server-side hitbox without a
    // full bake.
    public static float[] measure(CustomModel model) {
        return deriveBox(rawBounds(model));
    }

    public static BakedCustomModel bake(CustomModel model) {
        float[] raw = rawBounds(model);
        // Ground the model: its lowest point becomes the feet so an imported model sits on the floor
        // instead of hovering at whatever height it was authored.
        float groundShift = raw[1];
        Map<String, ModelPart> bones = new HashMap<>();
        Map<String, ModelPart> topChildren = new LinkedHashMap<>();
        int index = 0;
        for (CustomModel.Bone bone : model.roots()) {
            topChildren.put("bone$" + index++, buildBone(model, bone, null, groundShift, bones));
        }
        ModelPart root = new ModelPart(List.of(), topChildren);
        float[] box = deriveBox(raw);
        return new BakedCustomModel(root, bones, model.animations(), box[0], box[1], box[2]);
    }

    private static ModelPart buildBone(CustomModel model, CustomModel.Bone bone, float[] parentPivot,
                                       float groundShift, Map<String, ModelPart> bones) {
        float[] pivot = orZero(bone.pivot());
        List<ModelPart.Cube> cubes = new ArrayList<>();
        Map<String, ModelPart> children = new LinkedHashMap<>();
        int cubeIndex = 0;
        for (CustomModel.Cube cube : bone.cubes()) {
            if (isRotated(cube.rotation())) {
                float[] cubePivot = cube.pivot() != null ? cube.pivot() : center(cube);
                ModelPart sub = new ModelPart(List.of(buildCube(model, cube, cubePivot)), Map.of());
                PartPose pose = PartPose.offsetAndRotation(
                        SX * (cubePivot[0] - pivot[0]), -(cubePivot[1] - pivot[1]), SZ * (cubePivot[2] - pivot[2]),
                        (float) -Math.toRadians(cube.rotation()[0]),
                        (float) -Math.toRadians(cube.rotation()[1]),
                        (float) Math.toRadians(cube.rotation()[2]));
                sub.loadPose(pose);
                sub.setInitialPose(pose);
                children.put("cube$" + cubeIndex++, sub);
            } else {
                cubes.add(buildCube(model, cube, pivot));
            }
        }
        // Children are keyed by a unique index, not by bone name: Blockbench models routinely have many
        // unnamed or same-named sibling groups, and a name-keyed map would silently overwrite them, dropping
        // whole branches of the model. The name->part lookup for animation lives in the separate bones map.
        int childIndex = 0;
        for (CustomModel.Bone child : bone.children()) {
            children.put("bone$" + childIndex++, buildBone(model, child, pivot, groundShift, bones));
        }
        ModelPart part = new ModelPart(cubes, children);
        PartPose pose = parentPivot == null
                ? PartPose.offsetAndRotation(SX * pivot[0], FEET_Y - pivot[1] + groundShift, SZ * pivot[2],
                        rot(bone.rotation(), 0), rot(bone.rotation(), 1), rot(bone.rotation(), 2))
                : PartPose.offsetAndRotation(SX * (pivot[0] - parentPivot[0]), -(pivot[1] - parentPivot[1]),
                        SZ * (pivot[2] - parentPivot[2]),
                        rot(bone.rotation(), 0), rot(bone.rotation(), 1), rot(bone.rotation(), 2));
        part.loadPose(pose);
        part.setInitialPose(pose);
        bones.putIfAbsent(bone.name(), part);
        return part;
    }

    private static ModelPart.Cube buildCube(CustomModel model, CustomModel.Cube cube, float[] refPivot) {
        float inf = cube.inflate();
        float[] o = cube.origin();
        float[] s = cube.size();
        float minX = o[0] - inf, minY = o[1] - inf, minZ = o[2] - inf;
        float maxX = o[0] + s[0] + inf, maxY = o[1] + s[1] + inf, maxZ = o[2] + s[2] + inf;

        List<ModelPart.Polygon> polygons = new ArrayList<>();
        float vMinX = Float.MAX_VALUE, vMinY = Float.MAX_VALUE, vMinZ = Float.MAX_VALUE;
        float vMaxX = -Float.MAX_VALUE, vMaxY = -Float.MAX_VALUE, vMaxZ = -Float.MAX_VALUE;
        for (int f = 0; f < 6; f++) {
            CustomModel.Face face = cube.faces()[f];
            if (face == null) {
                continue;
            }
            float[][] corners = faceCorners(f, minX, minY, minZ, maxX, maxY, maxZ);
            float[] uvU = {face.u1(), face.u2(), face.u2(), face.u1()};
            float[] uvV = {face.v1(), face.v1(), face.v2(), face.v2()};
            ModelPart.Vertex[] verts = new ModelPart.Vertex[4];
            for (int i = 0; i < 4; i++) {
                float lx = SX * (corners[i][0] - refPivot[0]);
                float ly = -(corners[i][1] - refPivot[1]);
                float lz = SZ * (corners[i][2] - refPivot[2]);
                verts[i] = new ModelPart.Vertex(lx, ly, lz,
                        uvU[i] / model.textureWidth(), uvV[i] / model.textureHeight());
                vMinX = Math.min(vMinX, lx); vMaxX = Math.max(vMaxX, lx);
                vMinY = Math.min(vMinY, ly); vMaxY = Math.max(vMaxY, ly);
                vMinZ = Math.min(vMinZ, lz); vMaxZ = Math.max(vMaxZ, lz);
            }
            polygons.add(orientedPolygon(verts, f));
        }
        if (polygons.isEmpty()) {
            return new PerFaceCube(0, 0, 0, 0, 0, 0, model.textureWidth(), model.textureHeight(),
                    new ModelPart.Polygon[0]);
        }
        return new PerFaceCube(vMinX, vMinY, vMinZ, vMaxX - vMinX, vMaxY - vMinY, vMaxZ - vMinZ,
                model.textureWidth(), model.textureHeight(), polygons.toArray(new ModelPart.Polygon[0]));
    }

    // Guarantees the normal points along the face's outward axis, reversing the winding if the raw cross
    // product came out inward, so lighting and (optional) culling are correct regardless of corner order.
    private static ModelPart.Polygon orientedPolygon(ModelPart.Vertex[] verts, int face) {
        Vector3f edge1 = new Vector3f(verts[1].x() - verts[0].x(), verts[1].y() - verts[0].y(), verts[1].z() - verts[0].z());
        Vector3f edge2 = new Vector3f(verts[3].x() - verts[0].x(), verts[3].y() - verts[0].y(), verts[3].z() - verts[0].z());
        Vector3f normal = edge1.cross(edge2).normalize();
        Vector3f outward = new Vector3f(SX * OUTWARD[face][0], -OUTWARD[face][1], SZ * OUTWARD[face][2]);
        if (normal.dot(outward) < 0.0F) {
            ModelPart.Vertex tmp = verts[0]; verts[0] = verts[3]; verts[3] = tmp;
            tmp = verts[1]; verts[1] = verts[2]; verts[2] = tmp;
            normal.negate();
        }
        return new ModelPart.Polygon(verts, normal);
    }

    // Bedrock corners for a face as [top-left, top-right, bottom-right, bottom-left] matched to the UV rect.
    private static float[][] faceCorners(int face, float x0, float y0, float z0, float x1, float y1, float z1) {
        return switch (face) {
            case NORTH -> new float[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
            case SOUTH -> new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
            case EAST -> new float[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case WEST -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
            case UP -> new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            default -> new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
        };
    }

    // {minX, minY, minZ, maxX, maxY, maxZ, headPivotY} in Bedrock world space (Y-up).
    private static float[] rawBounds(CustomModel model) {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        float[] headY = {Float.NaN};
        for (CustomModel.Bone bone : model.roots()) {
            accumulate(bone, new Matrix4f(), min, max, headY);
        }
        return new float[]{min[0], min[1], min[2], max[0], max[1], max[2], headY[0]};
    }

    // Collision box in blocks, with the model grounded so its lowest point is the feet.
    private static float[] deriveBox(float[] raw) {
        if (raw[0] > raw[3]) {
            return new float[]{0.6F, 1.8F, 1.6F};
        }
        float radius = Math.max(Math.max(Math.abs(raw[0]), Math.abs(raw[3])),
                Math.max(Math.abs(raw[2]), Math.abs(raw[5])));
        float width = 2.0F * radius / 16.0F;
        float height = (raw[4] - raw[1]) / 16.0F;
        float eye = !Float.isNaN(raw[6]) ? (raw[6] - raw[1]) / 16.0F : height * 0.85F;
        return new float[]{width, height, eye};
    }

    private static void accumulate(CustomModel.Bone bone, Matrix4f parent, float[] min, float[] max, float[] headY) {
        float[] pivot = orZero(bone.pivot());
        Matrix4f world = new Matrix4f(parent)
                .translate(pivot[0], pivot[1], pivot[2])
                .rotateZ((float) Math.toRadians(rotDeg(bone.rotation(), 2)))
                .rotateY((float) Math.toRadians(rotDeg(bone.rotation(), 1)))
                .rotateX((float) Math.toRadians(rotDeg(bone.rotation(), 0)))
                .translate(-pivot[0], -pivot[1], -pivot[2]);
        if (Float.isNaN(headY[0]) && bone.name() != null && bone.name().toLowerCase(java.util.Locale.ROOT).contains("head")) {
            headY[0] = pivot[1];
        }
        for (CustomModel.Cube cube : bone.cubes()) {
            float inf = cube.inflate();
            float[] o = cube.origin();
            float[] s = cube.size();
            Matrix4f cubeWorld = new Matrix4f(world);
            if (isRotated(cube.rotation())) {
                float[] cp = cube.pivot() != null ? cube.pivot() : center(cube);
                cubeWorld.translate(cp[0], cp[1], cp[2])
                        .rotateZ((float) Math.toRadians(cube.rotation()[2]))
                        .rotateY((float) Math.toRadians(cube.rotation()[1]))
                        .rotateX((float) Math.toRadians(cube.rotation()[0]))
                        .translate(-cp[0], -cp[1], -cp[2]);
            }
            for (int cx = 0; cx < 2; cx++) {
                for (int cy = 0; cy < 2; cy++) {
                    for (int cz = 0; cz < 2; cz++) {
                        Vector3f corner = cubeWorld.transformPosition(new Vector3f(
                                cx == 0 ? o[0] - inf : o[0] + s[0] + inf,
                                cy == 0 ? o[1] - inf : o[1] + s[1] + inf,
                                cz == 0 ? o[2] - inf : o[2] + s[2] + inf));
                        min[0] = Math.min(min[0], corner.x); max[0] = Math.max(max[0], corner.x);
                        min[1] = Math.min(min[1], corner.y); max[1] = Math.max(max[1], corner.y);
                        min[2] = Math.min(min[2], corner.z); max[2] = Math.max(max[2], corner.z);
                    }
                }
            }
        }
        for (CustomModel.Bone child : bone.children()) {
            accumulate(child, world, min, max, headY);
        }
    }

    private static boolean isRotated(float[] rotation) {
        return rotation != null && (Math.abs(rotation[0]) > 1.0e-4F
                || Math.abs(rotation[1]) > 1.0e-4F || Math.abs(rotation[2]) > 1.0e-4F);
    }

    private static float[] center(CustomModel.Cube cube) {
        float[] o = cube.origin();
        float[] s = cube.size();
        return new float[]{o[0] + s[0] / 2.0F, o[1] + s[1] / 2.0F, o[2] + s[2] / 2.0F};
    }

    private static float[] orZero(float[] value) {
        return value != null ? value : new float[]{0, 0, 0};
    }

    private static float rot(float[] rotation, int axis) {
        float[] r = orZero(rotation);
        float radians = (float) Math.toRadians(r[axis]);
        return axis == 2 ? radians : -radians;
    }

    private static float rotDeg(float[] rotation, int axis) {
        return orZero(rotation)[axis];
    }
}
