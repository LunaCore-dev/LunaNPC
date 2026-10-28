package net.lunacore.luna_npc.client.render;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.client.util.NativeFileDialog;
import net.lunacore.luna_npc.npc.NpcModelCatalog;
import net.lunacore.luna_npc.npc.NpcValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Player-imported skins: plain PNG files in <game dir>/LunaNPC/assets/npc_models/<type>/<id>.png.
// The folder tree is auto-generated on client start, files dropped in manually are picked up on
// the next scan, and the in-game Import Model button copies a chosen file here. Textures are
// loaded from disk into dynamic textures on demand and reloaded whenever the file changes.
public final class NpcCustomSkins {

    private static final Logger LOGGER = LoggerFactory.getLogger("luna_npc");
    private static final long MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_TEXTURE_SIZE = 2048;

    private record LoadedSkin(Identifier texture, long fileTime) {
    }

    private static final Map<String, LoadedSkin> LOADED = new HashMap<>();

    private NpcCustomSkins() {
    }

    public static Path root() {
        return FabricLoader.getInstance().getGameDir().resolve("LunaNPC").resolve("assets").resolve("npc_models");
    }

    public static void createFolders() {
        for (NpcModelCatalog.NpcModelType type : NpcModelCatalog.TYPES) {
            try {
                Files.createDirectories(root().resolve(type.id()));
            } catch (IOException exception) {
                LOGGER.warn("Could not create custom skin folder for {}", type.id(), exception);
            }
        }
    }

    // Skin ids for this type found on disk, sorted; only sanely named files are offered.
    public static List<String> scan(String typeId) {
        Path folder = root().resolve(typeId);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(file -> file.getFileName().toString().endsWith(".png"))
                    .map(file -> stem(file.getFileName().toString()))
                    .filter(NpcValidation::isValidCustomModelId)
                    .sorted()
                    .forEach(ids::add);
        } catch (IOException exception) {
            LOGGER.warn("Could not scan custom skin folder for {}", typeId, exception);
        }
        return ids;
    }

    // Returns the dynamic texture for type/id if a valid file exists, loading or reloading it as
    // needed. Must be called on the render thread.
    public static Identifier textureFor(String typeId, String modelId) {
        Path file = root().resolve(typeId).resolve(modelId + ".png");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        String key = typeId + "/" + modelId;
        long fileTime;
        try {
            fileTime = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException exception) {
            return null;
        }
        LoadedSkin loaded = LOADED.get(key);
        if (loaded != null && loaded.fileTime() == fileTime) {
            return loaded.texture();
        }
        NativeImage image = readValidImage(file);
        if (image == null) {
            return null;
        }
        Identifier textureId = Identifier.fromNamespaceAndPath("luna_npc", "npc_models/" + key);
        Minecraft.getInstance().getTextureManager()
                .register(textureId, new DynamicTexture(textureId::toString, image));
        LOADED.put(key, new LoadedSkin(textureId, fileTime));
        return textureId;
    }

    // Copies a user-chosen file into the type's folder after sanity checks; returns the new skin
    // id, or empty if the file was rejected.
    public static Optional<String> importFile(String typeId, Path source) {
        String id = sanitize(stem(source.getFileName().toString()));
        if (!NpcValidation.isValidCustomModelId(id) || readValidImage(source) == null) {
            return Optional.empty();
        }
        try {
            Path target = root().resolve(typeId).resolve(id + ".png");
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(id);
        } catch (IOException exception) {
            LOGGER.warn("Could not import custom skin {}", source, exception);
            return Optional.empty();
        }
    }

    // True when a model id corresponds to a user-imported skin file on disk (removable), not a bundled id.
    public static boolean isImportedSkin(String typeId, String modelId) {
        return Files.isRegularFile(root().resolve(typeId).resolve(modelId + ".png"));
    }

    public static void removeSkin(String typeId, String modelId) {
        try {
            Files.deleteIfExists(root().resolve(typeId).resolve(modelId + ".png"));
            LOADED.remove(typeId + "/" + modelId);
        } catch (IOException exception) {
            LOGGER.warn("Could not remove imported skin {}/{}", typeId, modelId, exception);
        }
    }

    public static CompletableFuture<String> openPngFileDialog() {
        return NativeFileDialog.open("Import NPC Texture", "PNG texture (*.png)", "png");
    }

    private static NativeImage readValidImage(Path file) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
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
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private static String sanitize(String stem) {
        String cleaned = stem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-]", "_");
        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64);
        }
        return cleaned;
    }
}
