package net.lunacore.luna_npc.permission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.PermissionLogEntry;

// The audit log: a bounded ring of the most recent MAX_ENTRIES actions, oldest first (newest at the tail).
// Persisted as SavedData "permissions_log". Reads are windowed so the client never loads the whole log.
public final class PermissionLogManager extends SavedData {

    public static final int MAX_ENTRIES = 10_000;

    private static final Codec<PermissionLogManager> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(PermissionLogEntry.CODEC.listOf().optionalFieldOf("entries", List.of())
                    .forGetter(manager -> List.copyOf(manager.entries)))
            .apply(instance, PermissionLogManager::new));

    private static final SavedDataType<PermissionLogManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "permissions_log"),
            PermissionLogManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final ArrayDeque<PermissionLogEntry> entries = new ArrayDeque<>();

    private PermissionLogManager() {
    }

    private PermissionLogManager(List<PermissionLogEntry> stored) {
        entries.addAll(stored);
        trim();
    }

    public static PermissionLogManager get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void append(PermissionLogEntry entry) {
        entries.addLast(entry);
        trim();
        setDirty();
    }

    private void trim() {
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
    }

    public int size() {
        return size(null);
    }

    // Entry count matching a category filter (null/blank = every category).
    public int size(String category) {
        if (category == null || category.isBlank()) {
            return entries.size();
        }
        int count = 0;
        for (PermissionLogEntry entry : entries) {
            if (entry.action().category().equals(category)) {
                count++;
            }
        }
        return count;
    }

    public List<PermissionLogEntry> window(int offset, int limit) {
        return window(offset, limit, null);
    }

    // Newest-first window over the entries matching the category filter (null/blank = every category):
    // skip the `offset` most recent matches, then take up to `limit`.
    public List<PermissionLogEntry> window(int offset, int limit, String category) {
        if (offset < 0 || limit <= 0) {
            return List.of();
        }
        boolean allCategories = category == null || category.isBlank();
        List<PermissionLogEntry> out = new ArrayList<>();
        int index = 0;
        Iterator<PermissionLogEntry> newestFirst = entries.descendingIterator();
        while (newestFirst.hasNext() && out.size() < limit) {
            PermissionLogEntry entry = newestFirst.next();
            if (!allCategories && !entry.action().category().equals(category)) {
                continue;
            }
            if (index++ >= offset) {
                out.add(entry);
            }
        }
        return List.copyOf(out);
    }
}
