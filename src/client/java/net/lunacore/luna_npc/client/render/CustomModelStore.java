package net.lunacore.luna_npc.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.lunacore.luna_npc.client.render.model.CustomModel;
import net.lunacore.luna_npc.client.render.model.CustomModelBaker;
import net.lunacore.luna_npc.client.render.model.CustomModelParsers;
import net.lunacore.luna_npc.client.util.NativeFileDialog;
import net.lunacore.luna_npc.npc.NpcValidation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Client-local custom-geometry categories under <game dir>/LunaNPC/custom_models/<category>/. A category
// holds its source geometry (geometry.geo.json or model.bbmodel), one or more texture variants, an
// optional animations.animation.json, and category.json (display name + the measured hitbox). The server
// only ever stores the category/variant id strings and the transmitted box, so these files are purely a
// client rendering concern — like imported skins, a client missing them renders a fallback.
public final class CustomModelStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("luna_npc");
    private static final long MAX_MODEL_BYTES = 8L * 1024 * 1024;
    private static final long MAX_TEXTURE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_TEXTURE_SIZE = 2048;
    private static final int MAX_BONES = 512;
    private static final int MAX_CUBES = 4096;
    private static final String GEO_FILE = "geometry.geo.json";
    private static final String BBMODEL_FILE = "model.bbmodel";
    private static final String ANIM_FILE = "animations.animation.json";
    private static final String META_FILE = "category.json";

    public record CategoryMeta(String category, String displayName, float width, float height, float eyeHeight) {
    }

    private record LoadedTexture(Identifier texture, long fileTime) {
    }

    private static final Map<String, LoadedTexture> TEXTURES = new HashMap<>();

    private CustomModelStore() {
    }

    public static Path root() {
        return FabricLoader.getInstance().getGameDir().resolve("LunaNPC").resolve("custom_models");
    }

    private static Path categoryDir(String category) {
        return root().resolve(category);
    }

    public static List<String> listCategories() {
        Path root = root();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<String> categories = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve(GEO_FILE)) || Files.isRegularFile(dir.resolve(BBMODEL_FILE)))
                    .map(dir -> dir.getFileName().toString())
                    .filter(NpcValidation::isValidModelCategory)
                    .sorted()
                    .forEach(categories::add);
        } catch (IOException exception) {
            LOGGER.warn("Could not list custom model categories", exception);
        }
        return categories;
    }

    public static List<String> variantsOf(String category) {
        Path dir = categoryDir(category);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> variants = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(file -> file.getFileName().toString().endsWith(".png"))
                    .map(file -> stem(file.getFileName().toString()))
                    .filter(NpcValidation::isValidCustomModelId)
                    .sorted()
                    .forEach(variants::add);
        } catch (IOException exception) {
            LOGGER.warn("Could not list variants for {}", category, exception);
        }
        return variants;
    }

    public static Optional<CategoryMeta> meta(String category) {
        Path file = categoryDir(category).resolve(META_FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            return Optional.of(new CategoryMeta(category,
                    json.has("display_name") ? json.get("display_name").getAsString() : category,
                    json.has("width") ? json.get("width").getAsFloat() : 0.0F,
                    json.has("height") ? json.get("height").getAsFloat() : 0.0F,
                    json.has("eye_height") ? json.get("eye_height").getAsFloat() : 0.0F));
        } catch (RuntimeException | IOException exception) {
            LOGGER.warn("Could not read category meta for {}", category, exception);
            return Optional.empty();
        }
    }

    public static String displayName(String category) {
        return meta(category).map(CategoryMeta::displayName).orElse(category);
    }

    // Reads and re-parses a category's geometry (plus a sibling .animation.json for the geo path) so the
    // cache can bake it. Returns null if the files are missing or unparseable.
    public static CustomModel loadModel(String category) {
        Path dir = categoryDir(category);
        try {
            if (Files.isRegularFile(dir.resolve(BBMODEL_FILE))) {
                JsonObject json = JsonParser.parseString(Files.readString(dir.resolve(BBMODEL_FILE))).getAsJsonObject();
                return CustomModelParsers.parseBbmodel(json);
            }
            if (Files.isRegularFile(dir.resolve(GEO_FILE))) {
                JsonObject geo = JsonParser.parseString(Files.readString(dir.resolve(GEO_FILE))).getAsJsonObject();
                CustomModel model = CustomModelParsers.parseGeo(geo);
                if (Files.isRegularFile(dir.resolve(ANIM_FILE))) {
                    JsonObject animJson = JsonParser.parseString(Files.readString(dir.resolve(ANIM_FILE))).getAsJsonObject();
                    return new CustomModel(model.textureWidth(), model.textureHeight(), model.roots(),
                            CustomModelParsers.parseGeoAnimations(animJson), model.embeddedTexturePng());
                }
                return model;
            }
        } catch (RuntimeException | IOException exception) {
            LOGGER.warn("Could not load custom model {}", category, exception);
        }
        return null;
    }

    public static long geometryFileTime(String category) {
        Path dir = categoryDir(category);
        Path geo = Files.isRegularFile(dir.resolve(BBMODEL_FILE)) ? dir.resolve(BBMODEL_FILE) : dir.resolve(GEO_FILE);
        try {
            return Files.isRegularFile(geo) ? Files.getLastModifiedTime(geo).toMillis() : 0L;
        } catch (IOException exception) {
            return 0L;
        }
    }

    // Parses the chosen geometry, measures its box, and writes a new category folder. textureSource may be
    // null when the geometry embeds its own texture (a .bbmodel). Returns the id + measured box, or empty
    // with a translation-key reason for the picker's status line.
    public static Optional<CategoryMeta> importModel(Path geometrySource, Path textureSource, String category,
                                                     String displayName, StringBuilder failureKey) {
        if (!NpcValidation.isValidModelCategory(category)) {
            failureKey.append("gui.luna_npc.model_picker.bad_category");
            return Optional.empty();
        }
        if (listCategories().contains(category) || net.lunacore.luna_npc.npc.NpcModelCatalog.byId(category).isPresent()) {
            failureKey.append("gui.luna_npc.model_picker.category_exists");
            return Optional.empty();
        }
        if (listCategories().size() >= NpcValidation.MAX_CUSTOM_MODEL_CATEGORIES) {
            failureKey.append("gui.luna_npc.model_picker.too_many_categories");
            return Optional.empty();
        }
        CustomModel model = readModelFile(geometrySource, failureKey);
        if (model == null) {
            return Optional.empty();
        }
        byte[] textureBytes = textureBytes(textureSource, model, failureKey);
        if (textureBytes == null) {
            return Optional.empty();
        }
        float[] box = CustomModelBaker.measure(model);
        String variant = textureSource != null ? sanitize(stem(textureSource.getFileName().toString())) : "default";
        try {
            Path dir = categoryDir(category);
            Files.createDirectories(dir);
            String name = geometrySource.getFileName().toString().toLowerCase(Locale.ROOT);
            Files.copy(geometrySource, dir.resolve(name.endsWith(".bbmodel") ? BBMODEL_FILE : GEO_FILE),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.write(dir.resolve(variant + ".png"), textureBytes);
            writeMeta(dir, displayName, box);
            return Optional.of(new CategoryMeta(category, displayName, box[0], box[1], box[2]));
        } catch (IOException exception) {
            LOGGER.warn("Could not import custom model {}", category, exception);
            failureKey.append("gui.luna_npc.model_picker.import_failed");
            return Optional.empty();
        }
    }

    public static Optional<String> importVariant(String category, Path textureSource) {
        String variant = sanitize(stem(textureSource.getFileName().toString()));
        if (!NpcValidation.isValidCustomModelId(variant) || readValidImage(textureSource) == null) {
            return Optional.empty();
        }
        try {
            Files.copy(textureSource, categoryDir(category).resolve(variant + ".png"),
                    StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(variant);
        } catch (IOException exception) {
            LOGGER.warn("Could not import variant into {}", category, exception);
            return Optional.empty();
        }
    }

    public static boolean importAnimations(String category, Path animationSource) {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(animationSource)).getAsJsonObject();
            if (!json.has("animations")) {
                return false;
            }
            Files.copy(animationSource, categoryDir(category).resolve(ANIM_FILE), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (RuntimeException | IOException exception) {
            LOGGER.warn("Could not import animations into {}", category, exception);
            return false;
        }
    }

    // Loads a variant texture into a dynamic texture, reloading when the file changes. Render thread only.
    public static Identifier textureFor(String category, String variant) {
        Path file = categoryDir(category).resolve(variant + ".png");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        String key = category + "/" + variant;
        long fileTime;
        try {
            fileTime = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException exception) {
            return null;
        }
        LoadedTexture loaded = TEXTURES.get(key);
        if (loaded != null && loaded.fileTime() == fileTime) {
            return loaded.texture();
        }
        NativeImage image = readValidImage(file);
        if (image == null) {
            return null;
        }
        Identifier textureId = Identifier.fromNamespaceAndPath("luna_npc", "custom_models/" + key);
        Minecraft.getInstance().getTextureManager().register(textureId, new DynamicTexture(textureId::toString, image));
        TEXTURES.put(key, new LoadedTexture(textureId, fileTime));
        return textureId;
    }

    // Removes one texture variant; when it was the last one, the whole category folder goes too (a
    // category with no texture is useless). Only ever called for user-imported content.
    public static void removeVariant(String category, String variant) {
        Path dir = categoryDir(category);
        try {
            Files.deleteIfExists(dir.resolve(variant + ".png"));
            TEXTURES.remove(category + "/" + variant);
            if (variantsOf(category).isEmpty()) {
                removeCategory(category);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not remove variant {}/{}", category, variant, exception);
        }
    }

    public static void removeCategory(String category) {
        Path dir = categoryDir(category);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort: a locked file just stays; the folder no longer lists as a category.
                }
            });
        } catch (IOException exception) {
            LOGGER.warn("Could not remove category {}", category, exception);
        }
        TEXTURES.keySet().removeIf(key -> key.startsWith(category + "/"));
    }

    // True when the geometry file carries its own texture (a .bbmodel with an embedded data-URI), so the
    // import flow can skip prompting for a PNG.
    public static boolean hasEmbeddedTexture(Path source) {
        try {
            if (!source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel")) {
                return false;
            }
            JsonObject json = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
            if (!json.has("textures") || json.getAsJsonArray("textures").isEmpty()) {
                return false;
            }
            JsonObject texture = json.getAsJsonArray("textures").get(0).getAsJsonObject();
            return texture.has("source") && texture.get("source").getAsString().startsWith("data:");
        } catch (RuntimeException | IOException exception) {
            return false;
        }
    }

    public static CompletableFuture<String> openGeometryFileDialog() {
        return NativeFileDialog.open("Import Model (Bedrock .geo.json or .bbmodel)",
                "Model (*.geo.json, *.bbmodel)", "json;bbmodel");
    }

    private static CustomModel readModelFile(Path source, StringBuilder failureKey) {
        try {
            if (Files.size(source) > MAX_MODEL_BYTES) {
                failureKey.append("gui.luna_npc.model_picker.model_too_big");
                return null;
            }
            JsonObject json = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
            CustomModel model;
            if (source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel")) {
                model = CustomModelParsers.parseBbmodel(json);
            } else {
                if (CustomModelParsers.rejectedFormatVersion(json)) {
                    failureKey.append("gui.luna_npc.model_picker.old_format");
                    return null;
                }
                model = CustomModelParsers.parseGeo(json);
            }
            if (model.roots().isEmpty() || countBones(model) > MAX_BONES || countCubes(model) > MAX_CUBES) {
                failureKey.append("gui.luna_npc.model_picker.model_invalid");
                return null;
            }
            return model;
        } catch (RuntimeException | IOException exception) {
            LOGGER.warn("Could not parse model file {}", source, exception);
            failureKey.append("gui.luna_npc.model_picker.model_invalid");
            return null;
        }
    }

    private static byte[] textureBytes(Path textureSource, CustomModel model, StringBuilder failureKey) {
        if (textureSource != null) {
            if (readValidImage(textureSource) == null) {
                failureKey.append("gui.luna_npc.model_picker.bad_texture");
                return null;
            }
            try {
                return Files.readAllBytes(textureSource);
            } catch (IOException exception) {
                failureKey.append("gui.luna_npc.model_picker.bad_texture");
                return null;
            }
        }
        if (model.embeddedTexturePng() != null) {
            return model.embeddedTexturePng();
        }
        failureKey.append("gui.luna_npc.model_picker.needs_texture");
        return null;
    }

    private static void writeMeta(Path dir, String displayName, float[] box) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("display_name", displayName);
        json.addProperty("width", box[0]);
        json.addProperty("height", box[1]);
        json.addProperty("eye_height", box[2]);
        Files.writeString(dir.resolve(META_FILE), json.toString());
    }

    private static int countBones(CustomModel model) {
        return model.roots().stream().mapToInt(CustomModelStore::countBones).sum();
    }

    private static int countBones(CustomModel.Bone bone) {
        return 1 + bone.children().stream().mapToInt(CustomModelStore::countBones).sum();
    }

    private static int countCubes(CustomModel model) {
        return model.roots().stream().mapToInt(CustomModelStore::countCubes).sum();
    }

    private static int countCubes(CustomModel.Bone bone) {
        return bone.cubes().size() + bone.children().stream().mapToInt(CustomModelStore::countCubes).sum();
    }

    private static NativeImage readValidImage(Path file) {
        try {
            if (Files.size(file) > MAX_TEXTURE_BYTES) {
                return null;
            }
            try (InputStream stream = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(stream);
                if (image.getWidth() < 8 || image.getHeight() < 8
                        || image.getWidth() > MAX_TEXTURE_SIZE || image.getHeight() > MAX_TEXTURE_SIZE) {
                    image.close();
                    return null;
                }
                return image;
            }
        } catch (IOException exception) {
            return null;
        }
    }

    private static String stem(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".geo.json")) {
            return fileName.substring(0, fileName.length() - ".geo.json".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private static String sanitize(String stem) {
        String cleaned = stem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-]", "_");
        if (cleaned.isEmpty()) {
            return "default";
        }
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }
}
