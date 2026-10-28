package net.lunacore.luna_npc.permission;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.Item;
import net.lunacore.luna_npc.LunaNpcChat;
import net.lunacore.luna_npc.alliance.NpcAllianceManager;
import net.lunacore.luna_npc.api.LunaStick;
import net.lunacore.luna_npc.api.PermissionLogAction;
import net.lunacore.luna_npc.api.PermissionLogEntry;
import net.lunacore.luna_npc.item.LunaNpcItems;

// The one place that decides who may use a gated stick and records the audit log. Both the network handlers
// and the client-permission sync go through here, so the rule never diverges. Admins always pass — the
// singleplayer/LAN host and OPs are never gated, which is what keeps solo play unrestricted.
public final class PermissionService {

    private PermissionService() {
    }

    public static boolean isAdmin(MinecraftServer server, ServerPlayer player) {
        // The singleplayer/LAN host stays admin even with cheats off (its permission level is then 0),
        // which is what keeps solo play from ever locking itself out.
        return Commands.LEVEL_GAMEMASTERS.check(player.permissions())
                || server.isSingleplayerOwner(new NameAndId(player.getGameProfile()));
    }

    public static boolean canUse(MinecraftServer server, ServerPlayer player, LunaStick stick) {
        if (isAdmin(server, player)) {
            return true;
        }
        StickPermissionManager grants = StickPermissionManager.get(server);
        if (grants.isPlayerGranted(stick, player.getUUID())) {
            return true;
        }
        for (String allianceId : NpcAllianceManager.get(server).alliancesOfPlayer(player.getUUID())) {
            if (grants.isAllianceGranted(stick, allianceId)) {
                return true;
            }
        }
        return false;
    }

    public static Set<LunaStick> allowedSticks(MinecraftServer server, ServerPlayer player) {
        EnumSet<LunaStick> allowed = EnumSet.noneOf(LunaStick.class);
        for (LunaStick stick : LunaStick.values()) {
            if (canUse(server, player, stick)) {
                allowed.add(stick);
            }
        }
        return allowed;
    }

    // The gated stick an item represents, or null for the Manage Permissions stick and any non-stick item.
    public static LunaStick stickOf(Item item) {
        if (item == LunaNpcItems.CREATE_NPC) {
            return LunaStick.CREATE_NPC;
        }
        if (item == LunaNpcItems.EDIT_NPC) {
            return LunaStick.EDIT_NPC;
        }
        if (item == LunaNpcItems.SPAWN_NPC) {
            return LunaStick.SPAWN_NPC;
        }
        if (item == LunaNpcItems.REMOVE_NPC) {
            return LunaStick.REMOVE_NPC;
        }
        if (item == LunaNpcItems.EDIT_ALLIANCES) {
            return LunaStick.EDIT_ALLIANCES;
        }
        if (item == LunaNpcItems.ZONE_WAND) {
            return LunaStick.ZONE_WAND;
        }
        return null;
    }

    public static void denied(ServerPlayer player) {
        LunaNpcChat.send(player, Component.translatable("message.luna_npc.stick_denied"));
    }

    public static void log(MinecraftServer server, ServerPlayer actor, PermissionLogAction action,
                           String targetName, long targetId) {
        PermissionLogManager.get(server).append(new PermissionLogEntry(System.currentTimeMillis(),
                actor.getUUID(), actor.getGameProfile().name(), action,
                targetName == null ? "" : targetName, targetId));
    }
}
