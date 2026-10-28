package net.lunacore.luna_npc.job;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.NpcStockScope;

// Persistent per-trade stock: how many times each stock-limited trade has been used and, once it hit
// its limit, the game tick it restocks at. Restock is reconciled lazily on read (used resets to 0
// when the due tick has passed) so it survives chunk unload, restarts and downtime with no tick loop,
// mirroring NpcRespawnManager's game-time model. The key encodes the trade's scope: per player, per
// NPC instance, or one world-wide pool shared by every instance of the definition.
public final class JobStockManager extends SavedData {

    public record StockEntry(String key, int used, long restockDueTick) {
        public static final Codec<StockEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("key").forGetter(StockEntry::key),
                Codec.INT.optionalFieldOf("used", 0).forGetter(StockEntry::used),
                Codec.LONG.optionalFieldOf("restock_due", 0L).forGetter(StockEntry::restockDueTick)
        ).apply(instance, StockEntry::new));
    }

    private static final Codec<JobStockManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StockEntry.CODEC.listOf().optionalFieldOf("entries", List.of())
                    .forGetter(manager -> List.copyOf(manager.entries.values()))
    ).apply(instance, JobStockManager::new));

    private static final SavedDataType<JobStockManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "job_stock"),
            JobStockManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<String, StockEntry> entries = new LinkedHashMap<>();

    private JobStockManager() {
    }

    private JobStockManager(List<StockEntry> stored) {
        for (StockEntry entry : stored) {
            entries.put(entry.key(), entry);
        }
    }

    public static JobStockManager get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static String key(NpcStockScope scope, UUID player, UUID npc, long lunaId,
                             int subTypeIndex, int tradeIndex) {
        return switch (scope) {
            case PER_PLAYER -> "p:" + player + ":" + npc + ":" + tradeIndex;
            case PER_NPC -> "n:" + npc + ":" + tradeIndex;
            case PER_WORLD -> "w:" + lunaId + ":" + subTypeIndex + ":" + tradeIndex;
        };
    }

    public boolean hasStock(MinecraftServer server, String key, int stockCount) {
        reconcile(server, key);
        StockEntry entry = entries.get(key);
        return entry == null || entry.used() < stockCount;
    }

    public TradeMenuData.TradeStockView view(MinecraftServer server, int tradeIndex, String key, int stockCount) {
        reconcile(server, key);
        StockEntry entry = entries.get(key);
        int used = entry == null ? 0 : entry.used();
        int remaining = Math.max(0, stockCount - used);
        long due = entry == null ? 0L : entry.restockDueTick();
        int secondsLeft = remaining <= 0 && due > 0
                ? (int) Math.max(0L, (due - server.overworld().getGameTime()) / 20L)
                : 0;
        return new TradeMenuData.TradeStockView(tradeIndex, remaining, secondsLeft);
    }

    public void recordUse(MinecraftServer server, String key, int stockCount, int restockSeconds) {
        reconcile(server, key);
        StockEntry entry = entries.get(key);
        int used = (entry == null ? 0 : entry.used()) + 1;
        long due = entry == null ? 0L : entry.restockDueTick();
        if (used >= stockCount && restockSeconds > 0) {
            due = server.overworld().getGameTime() + restockSeconds * 20L;
        }
        entries.put(key, new StockEntry(key, used, due));
        setDirty();
    }

    private void reconcile(MinecraftServer server, String key) {
        StockEntry entry = entries.get(key);
        if (entry != null && entry.restockDueTick() > 0
                && entry.restockDueTick() <= server.overworld().getGameTime()) {
            entries.put(key, new StockEntry(key, 0, 0L));
            setDirty();
        }
    }
}
