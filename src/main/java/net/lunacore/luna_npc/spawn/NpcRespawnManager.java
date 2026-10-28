package net.lunacore.luna_npc.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.lunacore.luna_npc.LunaNpc;

// Persistent queue of NPCs waiting to respawn: each remembers which NPC (lunaId), where (dimension +
// position), and when (game tick). Survives restarts so a delayed respawn still fires. Pending
// entries also count toward a cube's max, so a killed-but-returning NPC still holds its slot.
public final class NpcRespawnManager extends SavedData {

    // suppressed carries a natural-spawned farmer's dropped job through its respawn, so the fresh
    // MOB_SUMMONED entity stays jobless instead of re-granting the farming job. Optional for back-compat
    // with queues written before it existed.
    public record Pending(long lunaId, String dimension, BlockPos pos, long dueTick, boolean suppressed) {
        public static final Codec<Pending> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("luna_id").forGetter(Pending::lunaId),
                Codec.STRING.fieldOf("dimension").forGetter(Pending::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Pending::pos),
                Codec.LONG.fieldOf("due_tick").forGetter(Pending::dueTick),
                Codec.BOOL.optionalFieldOf("suppressed", false).forGetter(Pending::suppressed)
        ).apply(instance, Pending::new));
    }

    private static final Codec<NpcRespawnManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Pending.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(manager -> manager.pending)
    ).apply(instance, NpcRespawnManager::new));

    private static final SavedDataType<NpcRespawnManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "respawns"),
            NpcRespawnManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final List<Pending> pending = new ArrayList<>();

    private NpcRespawnManager() {
    }

    private NpcRespawnManager(List<Pending> stored) {
        pending.addAll(stored);
    }

    public static NpcRespawnManager get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void schedule(long lunaId, String dimension, BlockPos pos, long dueTick, boolean suppressed) {
        pending.add(new Pending(lunaId, dimension, pos, dueTick, suppressed));
        setDirty();
    }

    // Removes and returns every respawn whose time has come.
    public List<Pending> pollDue(long now) {
        List<Pending> due = new ArrayList<>();
        Iterator<Pending> iterator = pending.iterator();
        while (iterator.hasNext()) {
            Pending entry = iterator.next();
            if (entry.dueTick() <= now) {
                due.add(entry);
                iterator.remove();
            }
        }
        if (!due.isEmpty()) {
            setDirty();
        }
        return due;
    }

    // Drops any queued respawn for one NPC at one spot, so a permanently removed instance never
    // returns. Matched on (lunaId, dimension, pos) so other placements of the same definition, or the
    // same definition in another dimension, keep their respawns. Returns how many entries were removed.
    public int removeMatching(long lunaId, String dimension, BlockPos pos) {
        int before = pending.size();
        pending.removeIf(entry -> entry.lunaId() == lunaId
                && entry.dimension().equals(dimension) && entry.pos().equals(pos));
        int removed = before - pending.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    public List<Pending> pending() {
        return List.copyOf(pending);
    }
}
