package net.lunacore.luna_npc.client.render.model;

import java.util.List;
import java.util.Map;

// Format-agnostic in-memory model, held in raw Bedrock space (pixels, Y-up, feet at y=0, absolute bone
// pivots) exactly as the .geo.json / .bbmodel author laid it out. Both parsers normalise into this and
// CustomModelBaker does the single Bedrock->vanilla conversion, so the coordinate math lives in one place.
public record CustomModel(int textureWidth, int textureHeight, List<Bone> roots,
                          Map<String, Animation> animations, byte[] embeddedTexturePng) {

    public record Bone(String name, float[] pivot, float[] rotation, List<Cube> cubes, List<Bone> children) {
    }

    // origin is the box's minimum corner; a null face is simply not drawn. uv is texture pixels.
    public record Cube(float[] origin, float[] size, float inflate, boolean mirror,
                       float[] rotation, float[] pivot, Face[] faces) {
    }

    public record Face(float u1, float v1, float u2, float v2) {
    }

    public record Animation(double length, boolean loop, Map<String, BoneChannels> bones) {
    }

    public record BoneChannels(List<Key> position, List<Key> rotation, List<Key> scale) {
    }

    public record Key(double time, float x, float y, float z) {
    }

    public boolean hasBone(String name) {
        return findBone(roots, name) != null;
    }

    private static Bone findBone(List<Bone> bones, String name) {
        for (Bone bone : bones) {
            if (name.equals(bone.name())) {
                return bone;
            }
            Bone nested = findBone(bone.children(), name);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }
}
