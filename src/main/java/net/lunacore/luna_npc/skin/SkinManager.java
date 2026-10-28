package net.lunacore.luna_npc.skin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.NpcSkin;
import net.lunacore.luna_npc.api.NpcSkinArms;
import net.lunacore.luna_npc.api.NpcSkinFailure;
import net.lunacore.luna_npc.api.NpcSkinResult;
import net.lunacore.luna_npc.api.SkinRegistry;
import net.lunacore.luna_npc.network.SkinPayload;
import net.lunacore.luna_npc.network.SkinRemovedPayload;
import net.lunacore.luna_npc.npc.NpcValidation;

// The world's downloaded player skins, persisted to skins.dat with their PNGs and sent to every player:
// on join (sendAll) and whenever one is added, renamed, re-armed or deleted. Downloads run one at a time
// on a background thread and land back on the server thread. Ids (dl_1, dl_2, ...) never change, so a
// rename keeps every NPC wearing the skin.
public final class SkinManager extends SavedData implements SkinRegistry {

    public static final String DEFAULT_NAME = "playermodel";
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LunaNPC");
    private static final ExecutorService DOWNLOADS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LunaNPC skin download");
        thread.setDaemon(true);
        return thread;
    });

    record Entry(NpcSkin skin, byte[] png) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(entry -> entry.skin().id()),
                Codec.STRING.fieldOf("name").forGetter(entry -> entry.skin().name()),
                NpcSkinArms.CODEC.optionalFieldOf("arms", NpcSkinArms.CLASSIC).forGetter(entry -> entry.skin().arms()),
                Codec.STRING.optionalFieldOf("source", "").forGetter(entry -> entry.skin().source()),
                Codec.BYTE_BUFFER.fieldOf("png").forGetter(entry -> ByteBuffer.wrap(entry.png()))
        ).apply(instance, (id, name, arms, source, png) -> {
            byte[] bytes = new byte[png.remaining()];
            png.get(bytes);
            return new Entry(new NpcSkin(id, name, arms, source), bytes);
        }));
    }

    private static final Codec<SkinManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(manager -> manager.nextId),
            Entry.CODEC.listOf().optionalFieldOf("skins", List.of())
                    .forGetter(manager -> List.copyOf(manager.skins.values()))
    ).apply(instance, SkinManager::new));

    private static final SavedDataType<SkinManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "skins"),
            SkinManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<String, Entry> skins = new LinkedHashMap<>();
    private int nextId = 1;
    private MinecraftServer server;

    private SkinManager() {
    }

    private SkinManager(int nextId, List<Entry> stored) {
        this.nextId = nextId;
        stored.forEach(entry -> skins.put(entry.skin().id(), entry));
    }

    public static SkinManager get(MinecraftServer server) {
        SkinManager manager = server.getDataStorage().computeIfAbsent(TYPE);
        manager.server = server;
        return manager;
    }

    @Override
    public CompletableFuture<NpcSkinResult> download(String playerNameOrUrl, String name, NpcSkinArms arms) {
        Optional<NpcSkinFailure> problem = name == null ? Optional.empty() : nameProblem(name, null);
        if (problem.isEmpty() && skins.size() >= NpcValidation.MAX_DOWNLOADED_SKINS) {
            problem = Optional.of(NpcSkinFailure.LIMIT_REACHED);
        }
        if (problem.isPresent()) {
            return CompletableFuture.completedFuture(NpcSkinResult.failed(problem.get()));
        }
        CompletableFuture<NpcSkinResult> result = new CompletableFuture<>();
        DOWNLOADS.execute(() -> {
            try {
                SkinDownloader.Fetched fetched = SkinDownloader.fetch(playerNameOrUrl);
                server.execute(() -> result.complete(store(fetched, name, arms)));
            } catch (SkinDownloader.Failed failed) {
                server.execute(() -> result.complete(NpcSkinResult.failed(failed.failure)));
            } catch (RuntimeException exception) {
                LOGGER.warn("Skin download failed for {}", playerNameOrUrl, exception);
                server.execute(() -> result.complete(NpcSkinResult.failed(NpcSkinFailure.NETWORK_ERROR)));
            }
        });
        return result;
    }

    // Back on the server thread: the name and the cap are checked again, another download may have won.
    private NpcSkinResult store(SkinDownloader.Fetched fetched, String name, NpcSkinArms arms) {
        if (skins.size() >= NpcValidation.MAX_DOWNLOADED_SKINS) {
            return NpcSkinResult.failed(NpcSkinFailure.LIMIT_REACHED);
        }
        String chosen = name == null ? freeName() : name;
        Optional<NpcSkinFailure> problem = nameProblem(chosen, null);
        if (problem.isPresent()) {
            return NpcSkinResult.failed(problem.get());
        }
        NpcSkin skin = new NpcSkin("dl_" + nextId++, chosen, arms == null ? fetched.arms() : arms, fetched.source());
        put(new Entry(skin, fetched.png()));
        return NpcSkinResult.success(skin);
    }

    private String freeName() {
        String name = DEFAULT_NAME;
        for (int suffix = 2; byName(name).isPresent(); suffix++) {
            name = DEFAULT_NAME + "_" + suffix;
        }
        return name;
    }

    private Optional<NpcSkinFailure> nameProblem(String name, String ownId) {
        if (!NpcValidation.isValidCustomModelId(name)) {
            return Optional.of(NpcSkinFailure.INVALID_NAME);
        }
        Optional<NpcSkin> taken = byName(name);
        return taken.isPresent() && !taken.get().id().equals(ownId) ? Optional.of(NpcSkinFailure.INVALID_NAME) : Optional.empty();
    }

    @Override
    public List<NpcSkin> all() {
        return skins.values().stream().map(Entry::skin).toList();
    }

    @Override
    public Optional<NpcSkin> byId(String id) {
        Entry entry = skins.get(id);
        return entry == null ? Optional.empty() : Optional.of(entry.skin());
    }

    @Override
    public Optional<NpcSkin> byName(String name) {
        return all().stream().filter(skin -> skin.name().equals(name)).findFirst();
    }

    @Override
    public Optional<NpcSkinFailure> rename(String id, String name) {
        Entry entry = skins.get(id);
        if (entry == null) {
            return Optional.of(NpcSkinFailure.NO_SUCH_SKIN);
        }
        Optional<NpcSkinFailure> problem = nameProblem(name, id);
        if (problem.isEmpty()) {
            NpcSkin skin = entry.skin();
            put(new Entry(new NpcSkin(id, name, skin.arms(), skin.source()), entry.png()));
        }
        return problem;
    }

    @Override
    public Optional<NpcSkinFailure> setArms(String id, NpcSkinArms arms) {
        Entry entry = skins.get(id);
        if (entry == null || arms == null) {
            return Optional.of(NpcSkinFailure.NO_SUCH_SKIN);
        }
        NpcSkin skin = entry.skin();
        put(new Entry(new NpcSkin(id, skin.name(), arms, skin.source()), entry.png()));
        return Optional.empty();
    }

    @Override
    public boolean delete(String id) {
        if (skins.remove(id) == null) {
            return false;
        }
        setDirty();
        broadcast(new SkinRemovedPayload(id));
        return true;
    }

    private void put(Entry entry) {
        skins.put(entry.skin().id(), entry);
        setDirty();
        broadcast(payload(entry));
    }

    private static SkinPayload payload(Entry entry) {
        NpcSkin skin = entry.skin();
        return new SkinPayload(skin.id(), skin.name(), skin.arms(), entry.png());
    }

    public void sendAll(ServerPlayer player) {
        skins.values().forEach(entry -> ServerPlayNetworking.send(player, payload(entry)));
    }

    private void broadcast(CustomPacketPayload payload) {
        if (server != null) {
            server.getPlayerList().getPlayers().forEach(player -> ServerPlayNetworking.send(player, payload));
        }
    }
}
