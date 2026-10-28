package net.lunacore.luna_npc.npc;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.lunacore.luna_npc.LunaNpcChat;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcWarning;
import net.lunacore.luna_npc.permission.PermissionService;

// Tells every online admin, in red chat, about the configuration warnings an in-game save just introduced —
// the same checks the API reports through NpcDefinition.warnings(). Only new ones are shown, so re-saving a
// setup that was already flagged stays quiet. Notes are informational and never sent.
public final class NpcWarningNotifier {

    private NpcWarningNotifier() {
    }

    public static Set<NpcWarning.Id> idsOf(NpcDefinition npc) {
        Set<NpcWarning.Id> ids = EnumSet.noneOf(NpcWarning.Id.class);
        npc.warnings().forEach(warning -> ids.add(warning.id()));
        return ids;
    }

    public static void notifyNew(MinecraftServer server, NpcDefinition npc, Set<NpcWarning.Id> before) {
        List<NpcWarning> fresh = npc.warnings().stream()
                .filter(warning -> warning.severity() != NpcWarning.Severity.NOTE && !before.contains(warning.id()))
                .toList();
        if (fresh.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!PermissionService.isAdmin(server, player)) {
                continue;
            }
            for (NpcWarning warning : fresh) {
                LunaNpcChat.send(player, Component.literal("⚠ ").withStyle(ChatFormatting.RED)
                        .append(LunaNpcChat.highlight(npc.name()))
                        .append(Component.literal(" (LID " + npc.lunaId() + "): " + warning.message())
                                .withStyle(ChatFormatting.RED)));
            }
        }
    }
}
