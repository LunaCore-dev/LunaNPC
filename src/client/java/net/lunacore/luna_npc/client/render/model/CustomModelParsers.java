package net.lunacore.luna_npc.client.render.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Two front-ends onto the shared CustomModel: Bedrock geometry (.geo.json, the plan's target format) and
// Blockbench projects (.bbmodel, the format most mcmodels.net downloads ship in, which also embeds its
// texture). Both are normalised into raw Bedrock space so CustomModelBaker does the coordinate math once.
public final class CustomModelParsers {

    private CustomModelParsers() {
    }

    public static boolean rejectedFormatVersion(JsonObject geo) {
        String version = geo.has("format_version") ? geo.get("format_version").getAsString() : "";
        return version.startsWith("1.12");
    }

    // ---- Bedrock .geo.json ----

    public static CustomModel parseGeo(JsonObject geo) {
        JsonArray geometries = geo.getAsJsonArray("minecraft:geometry");
        JsonObject entry = geometries.get(0).getAsJsonObject();
        JsonObject description = entry.getAsJsonObject("description");
        int texW = intOr(description, "texture_width", 16);
        int texH = intOr(description, "texture_height", 16);

        Map<String, RawBone> byName = new LinkedHashMap<>();
        for (JsonElement element : entry.getAsJsonArray("bones")) {
            JsonObject boneJson = element.getAsJsonObject();
            RawBone bone = new RawBone();
            bone.name = boneJson.get("name").getAsString();
            bone.parent = boneJson.has("parent") ? boneJson.get("parent").getAsString() : null;
            bone.pivot = arr3OrZero(boneJson, "pivot");
            bone.rotation = arr3OrZero(boneJson, "rotation");
            if (boneJson.has("cubes")) {
                for (JsonElement cubeElement : boneJson.getAsJsonArray("cubes")) {
                    bone.cubes.add(parseGeoCube(cubeElement.getAsJsonObject()));
                }
            }
            byName.put(bone.name, bone);
        }
        return new CustomModel(texW, texH, buildTree(byName), Map.of(), null);
    }

    private static CustomModel.Cube parseGeoCube(JsonObject cubeJson) {
        float[] origin = arr3OrZero(cubeJson, "origin");
        float[] size = arr3OrZero(cubeJson, "size");
        for (int i = 0; i < 3; i++) {
            if (size[i] < 0.0F) {
                origin[i] += size[i];
                size[i] = -size[i];
            }
        }
        float inflate = cubeJson.has("inflate") ? cubeJson.get("inflate").getAsFloat() : 0.0F;
        boolean mirror = cubeJson.has("mirror") && cubeJson.get("mirror").getAsBoolean();
        float[] rotation = cubeJson.has("rotation") ? arr3(cubeJson.getAsJsonArray("rotation")) : null;
        float[] pivot = cubeJson.has("pivot") ? arr3(cubeJson.getAsJsonArray("pivot")) : null;

        CustomModel.Face[] faces = new CustomModel.Face[6];
        JsonElement uv = cubeJson.get("uv");
        if (uv != null && uv.isJsonArray()) {
            boxUvFaces(faces, uv.getAsJsonArray().get(0).getAsFloat(), uv.getAsJsonArray().get(1).getAsFloat(),
                    size[0], size[1], size[2]);
        } else if (uv != null && uv.isJsonObject()) {
            for (Map.Entry<String, JsonElement> face : uv.getAsJsonObject().entrySet()) {
                int index = faceIndex(face.getKey());
                if (index < 0) {
                    continue;
                }
                JsonObject faceJson = face.getValue().getAsJsonObject();
                float[] fuv = arr2(faceJson.getAsJsonArray("uv"));
                float[] fsize = faceJson.has("uv_size") ? arr2(faceJson.getAsJsonArray("uv_size"))
                        : defaultFaceSize(index, size);
                faces[index] = new CustomModel.Face(fuv[0], fuv[1], fuv[0] + fsize[0], fuv[1] + fsize[1]);
            }
        }
        return new CustomModel.Cube(origin, size, inflate, mirror, rotation, pivot, faces);
    }

    public static Map<String, CustomModel.Animation> parseGeoAnimations(JsonObject json) {
        Map<String, CustomModel.Animation> animations = new LinkedHashMap<>();
        if (!json.has("animations")) {
            return animations;
        }
        for (Map.Entry<String, JsonElement> clip : json.getAsJsonObject("animations").entrySet()) {
            JsonObject clipJson = clip.getValue().getAsJsonObject();
            double length = clipJson.has("animation_length") ? clipJson.get("animation_length").getAsDouble() : 0.0;
            boolean loop = clipJson.has("loop") && clipJson.get("loop").isJsonPrimitive()
                    && clipJson.get("loop").getAsJsonPrimitive().isBoolean() && clipJson.get("loop").getAsBoolean();
            Map<String, CustomModel.BoneChannels> bones = new LinkedHashMap<>();
            if (clipJson.has("bones")) {
                for (Map.Entry<String, JsonElement> bone : clipJson.getAsJsonObject("bones").entrySet()) {
                    JsonObject boneJson = bone.getValue().getAsJsonObject();
                    bones.put(bone.getKey(), new CustomModel.BoneChannels(
                            parseChannel(boneJson.get("position")),
                            parseChannel(boneJson.get("rotation")),
                            parseChannel(boneJson.get("scale"))));
                    length = Math.max(length, channelEnd(bones.get(bone.getKey())));
                }
            }
            animations.put(clip.getKey(), new CustomModel.Animation(length, loop, bones));
        }
        return animations;
    }

    // A .animation.json channel is either a bare [x,y,z] (one key at t=0) or a time-keyed object.
    private static List<CustomModel.Key> parseChannel(JsonElement channel) {
        List<CustomModel.Key> keys = new ArrayList<>();
        if (channel == null) {
            return keys;
        }
        if (channel.isJsonArray()) {
            float[] value = arr3(channel.getAsJsonArray());
            keys.add(new CustomModel.Key(0.0, value[0], value[1], value[2]));
        } else if (channel.isJsonObject()) {
            for (Map.Entry<String, JsonElement> frame : channel.getAsJsonObject().entrySet()) {
                double time = parseDouble(frame.getKey());
                JsonElement value = frame.getValue();
                if (value.isJsonObject() && value.getAsJsonObject().has("post")) {
                    value = value.getAsJsonObject().get("post");
                }
                float[] xyz = value.isJsonArray() ? arr3(value.getAsJsonArray()) : new float[]{0, 0, 0};
                keys.add(new CustomModel.Key(time, xyz[0], xyz[1], xyz[2]));
            }
            keys.sort((a, b) -> Double.compare(a.time(), b.time()));
        }
        return keys;
    }

    // ---- Blockbench .bbmodel ----

    public static CustomModel parseBbmodel(JsonObject project) {
        JsonObject resolution = project.has("resolution") ? project.getAsJsonObject("resolution") : new JsonObject();
        // Per-face UVs are in the texture's own UV space (uv_width/uv_height), which can differ from the
        // legacy project resolution — using the resolution then scales every UV wrong.
        int texW = intOr(resolution, "width", 16);
        int texH = intOr(resolution, "height", 16);
        if (project.has("textures") && !project.getAsJsonArray("textures").isEmpty()) {
            JsonObject firstTexture = project.getAsJsonArray("textures").get(0).getAsJsonObject();
            texW = intOr(firstTexture, "uv_width", texW);
            texH = intOr(firstTexture, "uv_height", texH);
        }

        Map<String, JsonObject> elements = new LinkedHashMap<>();
        for (JsonElement element : project.getAsJsonArray("elements")) {
            JsonObject cube = element.getAsJsonObject();
            if (cube.has("uuid")) {
                elements.put(cube.get("uuid").getAsString(), cube);
            }
        }
        Map<String, String> groupNames = new LinkedHashMap<>();
        List<CustomModel.Bone> roots = new ArrayList<>();
        for (JsonElement node : project.getAsJsonArray("outliner")) {
            if (node.isJsonObject() && !isHelperNode(node.getAsJsonObject())) {
                roots.add(parseBbGroup(node.getAsJsonObject(), elements, groupNames));
            }
        }
        Map<String, CustomModel.Animation> animations = parseBbAnimations(project, groupNames);
        return new CustomModel(texW, texH, roots, animations, decodeTexture(project));
    }

    private static CustomModel.Bone parseBbGroup(JsonObject group, Map<String, JsonObject> elements,
                                                 Map<String, String> groupNames) {
        // Fall back to the group's uuid when it has no name: many Blockbench models leave groups unnamed,
        // and animation targets bones by name, so an unnamed bone still needs a unique, resolvable id.
        String uuid = optString(group, "uuid", null);
        String name = optString(group, "name", "");
        if (name.isBlank()) {
            name = uuid != null ? uuid : "bone";
        }
        if (uuid != null) {
            groupNames.put(uuid, name);
        }
        // Some exports (often ModelEngine ones) strip group pivots entirely. Without a pivot, animation
        // would rotate a bone's cubes about the world origin and fling them off, so fall back to the centre
        // of the group's cubes. Static rendering is pivot-independent, so this only affects animation.
        float[] pivot = group.has("origin") ? arr3(group.getAsJsonArray("origin")) : groupCenter(group, elements);
        float[] rotation = arr3OrZero(group, "rotation");
        List<CustomModel.Cube> cubes = new ArrayList<>();
        List<CustomModel.Bone> children = new ArrayList<>();
        if (group.has("children")) {
            for (JsonElement child : group.getAsJsonArray("children")) {
                if (child.isJsonObject()) {
                    if (!isHelperNode(child.getAsJsonObject())) {
                        children.add(parseBbGroup(child.getAsJsonObject(), elements, groupNames));
                    }
                } else {
                    JsonObject element = elements.get(child.getAsString());
                    if (element != null && "cube".equals(optString(element, "type", "cube"))
                            && !isHelperNode(element)) {
                        cubes.add(parseBbCube(element));
                    }
                }
            }
        }
        return new CustomModel.Bone(name, pivot, rotation, cubes, children);
    }

    // mcmodels.net models (packaged for ModelEngine/MythicMobs) carry an invisible collision-marker box the
    // plugin never renders: it is either hidden (Blockbench "visibility": false) or lives on a bone named
    // for the hitbox. Rendering it as solid geometry would swallow the whole model, so drop such nodes from
    // both the mesh and the auto-measured collision box.
    private static boolean isHelperNode(JsonObject node) {
        if (node.has("visibility") && node.get("visibility").isJsonPrimitive()
                && node.get("visibility").getAsJsonPrimitive().isBoolean() && !node.get("visibility").getAsBoolean()) {
            return true;
        }
        return isHelperName(optString(node, "name", ""));
    }

    private static boolean isHelperName(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        return switch (n) {
            case "hitbox", "hit_box", "hibox", "hurtbox", "hurt_box",
                 "bounding_box", "boundingbox", "collision", "collision_box", "collisionbox", "col_box" -> true;
            default -> false;
        };
    }

    private static float[] groupCenter(JsonObject group, Map<String, JsonObject> elements) {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        collectCubeBounds(group, elements, min, max);
        if (min[0] > max[0]) {
            return new float[]{0, 0, 0};
        }
        return new float[]{(min[0] + max[0]) / 2.0F, (min[1] + max[1]) / 2.0F, (min[2] + max[2]) / 2.0F};
    }

    private static void collectCubeBounds(JsonObject group, Map<String, JsonObject> elements, float[] min, float[] max) {
        if (!group.has("children")) {
            return;
        }
        for (JsonElement child : group.getAsJsonArray("children")) {
            if (child.isJsonObject()) {
                collectCubeBounds(child.getAsJsonObject(), elements, min, max);
            } else {
                JsonObject element = elements.get(child.getAsString());
                if (element != null && element.has("from") && element.has("to")) {
                    float[] from = arr3(element.getAsJsonArray("from"));
                    float[] to = arr3(element.getAsJsonArray("to"));
                    for (int i = 0; i < 3; i++) {
                        min[i] = Math.min(min[i], Math.min(from[i], to[i]));
                        max[i] = Math.max(max[i], Math.max(from[i], to[i]));
                    }
                }
            }
        }
    }

    private static CustomModel.Cube parseBbCube(JsonObject element) {
        float[] a = arr3(element.getAsJsonArray("from"));
        float[] b = arr3(element.getAsJsonArray("to"));
        // Blockbench lets an element's "to" sit below its "from" on an axis; the box is min..max of the two.
        // Taking to-from verbatim yields a negative size and an inside-out cube, so normalise to the true
        // min corner and a non-negative size.
        float[] from = {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
        float[] size = {Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]), Math.abs(b[2] - a[2])};
        float inflate = element.has("inflate") ? element.get("inflate").getAsFloat() : 0.0F;
        float[] rotation = element.has("rotation") ? arr3(element.getAsJsonArray("rotation")) : null;
        float[] pivot = element.has("origin") ? arr3(element.getAsJsonArray("origin")) : null;

        CustomModel.Face[] faces = new CustomModel.Face[6];
        if (element.has("faces")) {
            for (Map.Entry<String, JsonElement> face : element.getAsJsonObject("faces").entrySet()) {
                int index = faceIndex(face.getKey());
                JsonObject faceJson = face.getValue().getAsJsonObject();
                if (index < 0 || !faceJson.has("uv")) {
                    continue;
                }
                float[] rect = arr(faceJson.getAsJsonArray("uv"), 4);
                faces[index] = new CustomModel.Face(rect[0], rect[1], rect[2], rect[3]);
            }
        }
        return new CustomModel.Cube(from, size, inflate, false, rotation, pivot, faces);
    }

    private static Map<String, CustomModel.Animation> parseBbAnimations(JsonObject project,
                                                                        Map<String, String> groupNames) {
        Map<String, CustomModel.Animation> animations = new LinkedHashMap<>();
        if (!project.has("animations")) {
            return animations;
        }
        for (JsonElement element : project.getAsJsonArray("animations")) {
            JsonObject clip = element.getAsJsonObject();
            String name = optString(clip, "name", "animation");
            double length = clip.has("length") ? clip.get("length").getAsDouble() : 0.0;
            boolean loop = "loop".equals(optString(clip, "loop", "once"));
            Map<String, CustomModel.BoneChannels> bones = new LinkedHashMap<>();
            if (clip.has("animators")) {
                for (Map.Entry<String, JsonElement> animator : clip.getAsJsonObject("animators").entrySet()) {
                    String boneName = groupNames.getOrDefault(animator.getKey(),
                            optString(animator.getValue().getAsJsonObject(), "name", animator.getKey()));
                    List<CustomModel.Key> position = new ArrayList<>();
                    List<CustomModel.Key> rotation = new ArrayList<>();
                    List<CustomModel.Key> scale = new ArrayList<>();
                    JsonObject animatorJson = animator.getValue().getAsJsonObject();
                    if (animatorJson.has("keyframes")) {
                        for (JsonElement keyElement : animatorJson.getAsJsonArray("keyframes")) {
                            JsonObject key = keyElement.getAsJsonObject();
                            String channel = optString(key, "channel", "");
                            double time = key.has("time") ? key.get("time").getAsDouble() : 0.0;
                            float[] value = bbKeyValue(key);
                            CustomModel.Key parsed = new CustomModel.Key(time, value[0], value[1], value[2]);
                            switch (channel) {
                                case "position" -> position.add(parsed);
                                case "rotation" -> rotation.add(parsed);
                                case "scale" -> scale.add(parsed);
                                default -> { }
                            }
                        }
                    }
                    sortByTime(position);
                    sortByTime(rotation);
                    sortByTime(scale);
                    bones.put(boneName, new CustomModel.BoneChannels(position, rotation, scale));
                }
            }
            for (CustomModel.BoneChannels channels : bones.values()) {
                length = Math.max(length, channelEnd(channels));
            }
            animations.put(name, new CustomModel.Animation(length, loop, bones));
        }
        return animations;
    }

    private static float[] bbKeyValue(JsonObject key) {
        if (key.has("data_points") && key.getAsJsonArray("data_points").size() > 0) {
            JsonObject point = key.getAsJsonArray("data_points").get(0).getAsJsonObject();
            return new float[]{floatField(point, "x"), floatField(point, "y"), floatField(point, "z")};
        }
        return new float[]{0, 0, 0};
    }

    private static byte[] decodeTexture(JsonObject project) {
        if (!project.has("textures") || project.getAsJsonArray("textures").isEmpty()) {
            return null;
        }
        JsonObject texture = project.getAsJsonArray("textures").get(0).getAsJsonObject();
        if (!texture.has("source")) {
            return null;
        }
        String source = texture.get("source").getAsString();
        int comma = source.indexOf(',');
        if (source.startsWith("data:") && comma > 0) {
            return Base64.getDecoder().decode(source.substring(comma + 1));
        }
        return null;
    }

    // ---- shared helpers ----

    private static List<CustomModel.Bone> buildTree(Map<String, RawBone> byName) {
        List<CustomModel.Bone> roots = new ArrayList<>();
        for (RawBone bone : byName.values()) {
            if (bone.parent == null || !byName.containsKey(bone.parent)) {
                roots.add(toBone(bone, byName));
            }
        }
        return roots;
    }

    private static CustomModel.Bone toBone(RawBone bone, Map<String, RawBone> byName) {
        List<CustomModel.Bone> children = new ArrayList<>();
        for (RawBone candidate : byName.values()) {
            if (bone.name.equals(candidate.parent)) {
                children.add(toBone(candidate, byName));
            }
        }
        return new CustomModel.Bone(bone.name, bone.pivot, bone.rotation, bone.cubes, children);
    }

    private static void boxUvFaces(CustomModel.Face[] faces, float u, float v, float w, float h, float d) {
        faces[CustomModelBaker.DOWN] = new CustomModel.Face(u + d, v, u + d + w, v + d);
        faces[CustomModelBaker.UP] = new CustomModel.Face(u + d + w, v + d, u + d + 2 * w, v);
        faces[CustomModelBaker.WEST] = new CustomModel.Face(u, v + d, u + d, v + d + h);
        faces[CustomModelBaker.NORTH] = new CustomModel.Face(u + d, v + d, u + d + w, v + d + h);
        faces[CustomModelBaker.EAST] = new CustomModel.Face(u + d + w, v + d, u + 2 * d + w, v + d + h);
        faces[CustomModelBaker.SOUTH] = new CustomModel.Face(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h);
    }

    private static float[] defaultFaceSize(int index, float[] size) {
        return switch (index) {
            case CustomModelBaker.NORTH, CustomModelBaker.SOUTH -> new float[]{size[0], size[1]};
            case CustomModelBaker.EAST, CustomModelBaker.WEST -> new float[]{size[2], size[1]};
            default -> new float[]{size[0], size[2]};
        };
    }

    private static int faceIndex(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "north" -> CustomModelBaker.NORTH;
            case "east" -> CustomModelBaker.EAST;
            case "south" -> CustomModelBaker.SOUTH;
            case "west" -> CustomModelBaker.WEST;
            case "up" -> CustomModelBaker.UP;
            case "down" -> CustomModelBaker.DOWN;
            default -> -1;
        };
    }

    private static double channelEnd(CustomModel.BoneChannels channels) {
        double end = 0.0;
        end = Math.max(end, lastTime(channels.position()));
        end = Math.max(end, lastTime(channels.rotation()));
        end = Math.max(end, lastTime(channels.scale()));
        return end;
    }

    private static double lastTime(List<CustomModel.Key> keys) {
        return keys.isEmpty() ? 0.0 : keys.getLast().time();
    }

    private static void sortByTime(List<CustomModel.Key> keys) {
        keys.sort((a, b) -> Double.compare(a.time(), b.time()));
    }

    private static float floatField(JsonObject object, String key) {
        if (!object.has(key)) {
            return 0.0F;
        }
        try {
            return object.get(key).getAsFloat();
        } catch (NumberFormatException ignored) {
            return 0.0F;
        }
    }

    private static float[] arr(JsonArray array, int count) {
        float[] out = new float[count];
        for (int i = 0; i < count && i < array.size(); i++) {
            out[i] = array.get(i).getAsFloat();
        }
        return out;
    }

    private static float[] arr2(JsonArray array) {
        return arr(array, 2);
    }

    private static float[] arr3(JsonArray array) {
        return arr(array, 3);
    }

    private static float[] arr3OrZero(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? arr3(object.getAsJsonArray(key)) : new float[]{0, 0, 0};
    }

    private static int intOr(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static String optString(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static final class RawBone {
        String name;
        String parent;
        float[] pivot;
        float[] rotation;
        final List<CustomModel.Cube> cubes = new ArrayList<>();
    }
}
