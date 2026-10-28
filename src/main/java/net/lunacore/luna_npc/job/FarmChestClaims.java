package net.lunacore.luna_npc.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;

// Runtime-only record of which farming NPC owns which deposit chest, so two farmers never fight over
// one chest. Never persisted (a temporary marker; claims re-form on the next deposit after a restart).
// Keyed by MinecraftServer through a WeakHashMap so a stopped server's claims drop out on their own
// without a shutdown hook. All access is on the server thread.
public final class FarmChestClaims {

    private static final Map<MinecraftServer, FarmChestClaims> INSTANCES = new WeakHashMap<>();

    private final Map<GlobalPos, UUID> owners = new HashMap<>();

    private FarmChestClaims() {
    }

    public static FarmChestClaims get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ignored -> new FarmChestClaims());
    }

    public boolean isClaimable(GlobalPos chest, UUID by) {
        UUID owner = owners.get(chest);
        return owner == null || owner.equals(by);
    }

    public void claim(GlobalPos chest, UUID by) {
        owners.put(chest, by);
    }

    public void release(GlobalPos chest, UUID by) {
        if (by.equals(owners.get(chest))) {
            owners.remove(chest);
        }
    }

    public void releaseAll(UUID by) {
        owners.values().removeIf(by::equals);
    }
}
