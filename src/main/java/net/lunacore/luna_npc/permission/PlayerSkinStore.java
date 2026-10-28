package net.lunacore.luna_npc.permission;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.lunacore.luna_npc.LunaNpc;

// Last-known name and signed skin of every player who joined, so player lists can show faces for players
// who are offline. Refreshed on each join, which is also when a changed skin replaces the stored one.
// Persisted as SavedData "player_skins".
public final class PlayerSkinStore extends SavedData {

    private static final String TEXTURES = "textures";

    private record Skin(String name, String textures, String signature) {
        static final Codec<Skin> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(Skin::name),
                Codec.STRING.optionalFieldOf("textures", "").forGetter(Skin::textures),
                Codec.STRING.optionalFieldOf("signature", "").forGetter(Skin::signature)
        ).apply(instance, Skin::new));
    }

    private static final Codec<PlayerSkinStore> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Skin.CODEC)
                    .optionalFieldOf("players", Map.of()).forGetter(store -> store.skins))
            .apply(instance, PlayerSkinStore::new));

    private static final SavedDataType<PlayerSkinStore> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "player_skins"),
            PlayerSkinStore::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Skin> skins = new HashMap<>();

    private PlayerSkinStore() {
    }

    private PlayerSkinStore(Map<UUID, Skin> stored) {
        skins.putAll(stored);
    }

    public static PlayerSkinStore get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void remember(GameProfile profile) {
        Property textures = profile.properties().get(TEXTURES).stream().findFirst().orElse(null);
        Skin skin = new Skin(profile.name(), textures == null ? "" : textures.value(),
                textures == null || textures.signature() == null ? "" : textures.signature());
        if (!skin.equals(skins.get(profile.id()))) {
            skins.put(profile.id(), skin);
            setDirty();
        }
    }

    // Falls back to a texture-less profile, which renders the default skin for that UUID.
    public ResolvableProfile profileOf(UUID id, String fallbackName) {
        Skin skin = skins.get(id);
        if (skin == null || skin.textures().isEmpty()) {
            return ResolvableProfile.createResolved(new GameProfile(id, skin != null ? skin.name() : fallbackName));
        }
        Property textures = skin.signature().isEmpty()
                ? new Property(TEXTURES, skin.textures())
                : new Property(TEXTURES, skin.textures(), skin.signature());
        return ResolvableProfile.createResolved(new GameProfile(id, skin.name(),
                new PropertyMap(ImmutableMultimap.of(TEXTURES, textures))));
    }
}
