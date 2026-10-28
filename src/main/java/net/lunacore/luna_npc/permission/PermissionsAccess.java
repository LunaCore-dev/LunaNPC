package net.lunacore.luna_npc.permission;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.lunacore.luna_npc.api.LunaStick;
import net.lunacore.luna_npc.api.PermissionLogEntry;
import net.lunacore.luna_npc.api.StickPermissions;

// Server-bound API face over the two permission managers and PermissionService. Handed out by
// LunaNpcApi.permissions(server); holds only the server, so it never leaks internal state.
public final class PermissionsAccess implements StickPermissions {

    private final MinecraftServer server;

    public PermissionsAccess(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public boolean canUse(ServerPlayer player, LunaStick stick) {
        return PermissionService.canUse(server, player, stick);
    }

    @Override
    public Set<LunaStick> allowedSticks(ServerPlayer player) {
        return PermissionService.allowedSticks(server, player);
    }

    @Override
    public boolean isPlayerGranted(LunaStick stick, UUID player) {
        return StickPermissionManager.get(server).isPlayerGranted(stick, player);
    }

    @Override
    public boolean isAllianceGranted(LunaStick stick, String allianceId) {
        return StickPermissionManager.get(server).isAllianceGranted(stick, allianceId);
    }

    @Override
    public Set<UUID> grantedPlayers(LunaStick stick) {
        return StickPermissionManager.get(server).grantedPlayers(stick);
    }

    @Override
    public Set<String> grantedAlliances(LunaStick stick) {
        return StickPermissionManager.get(server).grantedAlliances(stick);
    }

    @Override
    public void grantPlayer(LunaStick stick, UUID player) {
        StickPermissionManager.get(server).grantPlayer(stick, player);
    }

    @Override
    public void revokePlayer(LunaStick stick, UUID player) {
        StickPermissionManager.get(server).revokePlayer(stick, player);
    }

    @Override
    public void grantAlliance(LunaStick stick, String allianceId) {
        StickPermissionManager.get(server).grantAlliance(stick, allianceId);
    }

    @Override
    public void revokeAlliance(LunaStick stick, String allianceId) {
        StickPermissionManager.get(server).revokeAlliance(stick, allianceId);
    }

    @Override
    public List<PermissionLogEntry> log(int offset, int limit) {
        return PermissionLogManager.get(server).window(offset, limit);
    }

    @Override
    public int logSize() {
        return PermissionLogManager.get(server).size();
    }
}
