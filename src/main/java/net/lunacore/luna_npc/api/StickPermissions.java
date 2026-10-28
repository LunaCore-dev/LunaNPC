package net.lunacore.luna_npc.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

// In-game access control for the admin/builder sticks, plus the audit log. Obtain via
// LunaNpcApi.permissions(server). Admins (OP level >= 2 or the singleplayer/LAN host) always pass canUse
// regardless of grants, so solo play is never restricted. Non-admins are default-deny: a stick is usable
// only once its player UUID, or an alliance the player belongs to, is granted. Granting the built-in
// "players" alliance therefore permits everyone.
public interface StickPermissions {

    boolean canUse(ServerPlayer player, LunaStick stick);

    Set<LunaStick> allowedSticks(ServerPlayer player);

    boolean isPlayerGranted(LunaStick stick, UUID player);

    boolean isAllianceGranted(LunaStick stick, String allianceId);

    Set<UUID> grantedPlayers(LunaStick stick);

    Set<String> grantedAlliances(LunaStick stick);

    void grantPlayer(LunaStick stick, UUID player);

    void revokePlayer(LunaStick stick, UUID player);

    void grantAlliance(LunaStick stick, String allianceId);

    void revokeAlliance(LunaStick stick, String allianceId);

    // Newest-first window of the audit log: offset entries back from the most recent, up to limit. The log
    // is capped at 10 000 entries; older ones are dropped.
    List<PermissionLogEntry> log(int offset, int limit);

    int logSize();
}
