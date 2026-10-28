package net.lunacore.luna_npc.client.render;

import java.util.HashMap;
import java.util.Map;
import net.lunacore.luna_npc.client.render.model.BakedCustomModel;
import net.lunacore.luna_npc.client.render.model.CustomModel;
import net.lunacore.luna_npc.client.render.model.CustomModelBaker;

// Bakes a custom category's geometry into a live model on first render and keeps it until the source file
// changes on disk (same mtime pattern as imported skins). Baking allocates a ModelPart tree but touches no
// GL, so it is safe to run on the render thread; it must not be re-baked every frame, hence the cache.
public final class CustomModelCache {

    private record Cached(BakedCustomModel model, long fileTime) {
    }

    private static final Map<String, Cached> CACHE = new HashMap<>();

    private CustomModelCache() {
    }

    public static BakedCustomModel get(String category) {
        long fileTime = CustomModelStore.geometryFileTime(category);
        Cached cached = CACHE.get(category);
        if (cached != null && cached.fileTime() == fileTime) {
            return cached.model();
        }
        CustomModel model = CustomModelStore.loadModel(category);
        if (model == null) {
            return null;
        }
        BakedCustomModel baked = CustomModelBaker.bake(model);
        CACHE.put(category, new Cached(baked, fileTime));
        return baked;
    }
}
