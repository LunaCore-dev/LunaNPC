package net.lunacore.luna_npc.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.entity.NpcEntity;

// The Chunk Loader trait: keeps the chunks around an NPC loaded and ticking with no player near, so a
// patrol keeps walking and a bodyguard keeps up. It holds a timed chunk ticket (like a thrown ender pearl)
// that the NPC renews while it lives, so nothing is ever left loaded once it dies, despawns or loses the
// trait — the ticket simply runs out. The ticket is saved with the world, so after a restart it loads the
// NPC's chunk again and the NPC picks the renewal back up. Renewal is cheap (a map lookup every 40 ticks
// or on a chunk change); the cost is the loaded area itself: 3x3 ticking chunks per loader.
public final class NpcChunkLoader {

    // Ticket radius 3 makes the NPC's chunk and its 8 neighbours entity-ticking, so it never freezes the
    // moment it steps over a chunk border before the next renewal.
    private static final int TICKET_RADIUS = 3;
    // A leg's destination only needs its blocks readable for path planning, not ticking.
    private static final int DESTINATION_RADIUS = 1;
    private static final long TICKET_TIMEOUT_TICKS = 200L;
    private static final int RENEW_INTERVAL_TICKS = 40;

    public static final TicketType NPC_TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE,
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "npc_chunk_loader"),
            new TicketType(TICKET_TIMEOUT_TICKS, TicketType.FLAG_PERSIST | TicketType.FLAG_LOADING
                    | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

    private final NpcEntity entity;
    private long ticketedChunk = Long.MIN_VALUE;
    private int renewCooldown;

    public NpcChunkLoader(NpcEntity entity) {
        this.entity = entity;
    }

    public static void initialize() {
    }

    public void tick(ServerLevel level) {
        if (!entity.traitsSettings().chunkLoader()) {
            ticketedChunk = Long.MIN_VALUE;
            return;
        }
        ChunkPos chunk = entity.chunkPosition();
        if (chunk.pack() == ticketedChunk && --renewCooldown > 0) {
            return;
        }
        level.getChunkSource().addTicketWithRadius(NPC_TICKET, chunk, TICKET_RADIUS);
        ticketedChunk = chunk.pack();
        renewCooldown = RENEW_INTERVAL_TICKS;
    }

    // Loads the chunk a route leg ends in, so the planner reads real blocks there instead of the leg
    // stalling at the edge of the loaded area. Only for chunk-loading NPCs.
    public void keepDestinationLoaded(ServerLevel level, BlockPos destination) {
        if (entity.traitsSettings().chunkLoader()) {
            level.getChunkSource().addTicketWithRadius(NPC_TICKET, ChunkPos.containing(destination),
                    DESTINATION_RADIUS);
        }
    }
}
