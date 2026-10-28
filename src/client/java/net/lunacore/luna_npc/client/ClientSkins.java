package net.lunacore.luna_npc.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.api.NpcSkin;
import net.lunacore.luna_npc.api.NpcSkinArms;
import net.lunacore.luna_npc.network.SkinPayload;
import net.lunacore.luna_npc.network.SkinResultPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// The server's downloaded player skins, received one SkinPayload at a time and kept as dynamic textures
// for the current connection. The renderer asks here first for a player-type NPC's texture and arm width.
public final class ClientSkins {

    private static final Logger LOGGER = LoggerFactory.getLogger("luna_npc");

    private record Loaded(NpcSkin skin, Identifier texture) {
    }

    private static final Map<String, Loaded> SKINS = new LinkedHashMap<>();
    private static Consumer<SkinResultPayload> resultListener;

    private ClientSkins() {
    }

    public static void put(SkinPayload payload) {
        remove(payload.id());
        NativeImage image;
        try {
            image = NativeImage.read(payload.png());
        } catch (IOException exception) {
            LOGGER.warn("Could not read downloaded skin {}", payload.id(), exception);
            return;
        }
        Identifier texture = Identifier.fromNamespaceAndPath("luna_npc", "downloaded_skins/" + payload.id());
        Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(texture::toString, image));
        SKINS.put(payload.id(), new Loaded(new NpcSkin(payload.id(), payload.name(), payload.arms(), ""), texture));
    }

    public static void remove(String id) {
        Loaded old = SKINS.remove(id);
        if (old != null) {
            Minecraft.getInstance().getTextureManager().release(old.texture());
        }
    }

    public static void clear() {
        List.copyOf(SKINS.keySet()).forEach(ClientSkins::remove);
    }

    public static List<NpcSkin> all() {
        List<NpcSkin> skins = new ArrayList<>();
        SKINS.values().forEach(loaded -> skins.add(loaded.skin()));
        return skins;
    }

    public static NpcSkin byId(String id) {
        Loaded loaded = SKINS.get(id);
        return loaded == null ? null : loaded.skin();
    }

    // Only player-type models can wear a downloaded skin.
    public static Identifier textureFor(String modelType, String modelId) {
        Loaded loaded = NpcSkin.MODEL_TYPE.equals(modelType) ? SKINS.get(modelId) : null;
        return loaded == null ? null : loaded.texture();
    }

    public static boolean isSlim(String modelType, String modelId) {
        Loaded loaded = NpcSkin.MODEL_TYPE.equals(modelType) ? SKINS.get(modelId) : null;
        return loaded != null && loaded.skin().arms() == NpcSkinArms.SLIM;
    }

    // The open model picker listens for the answer to its own download or edit.
    public static void setResultListener(Consumer<SkinResultPayload> listener) {
        resultListener = listener;
    }

    public static void onResult(SkinResultPayload payload) {
        if (resultListener != null) {
            resultListener.accept(payload);
        }
    }
}
