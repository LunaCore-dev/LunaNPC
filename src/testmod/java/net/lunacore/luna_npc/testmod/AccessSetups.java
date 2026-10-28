package net.lunacore.luna_npc.testmod;

import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.lunacore.luna_npc.api.LunaNpcApi;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcEditAccess;

// /lunatest access: one standing NPC per edit access, each probed with an API write so the chat shows
// which side may change it. Open the Edit NPC stick list to see the lock and try editing each one.
final class AccessSetups {

    private AccessSetups() {
    }

    private record Variant(String name, NpcEditAccess access, String what) {
    }

    static int setUp(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel level = source.getLevel();
        TestNpcs.Layout layout = TestNpcs.Layout.of(source);
        List<Variant> variants = List.of(
                new Variant("TestAccessApi", NpcEditAccess.API_ONLY,
                        "API only: locked and greyed in the Edit NPC list, still spawnable."),
                new Variant("TestAccessGui", NpcEditAccess.GUI_ONLY,
                        "in-game only: editable in-game; the API can't change or clear it."),
                new Variant("TestAccessBoth", NpcEditAccess.SHARED,
                        "in-game and API: both can change it, last save wins."));
        for (int index = 0; index < variants.size(); index++) {
            Variant variant = variants.get(index);
            NpcDefinition npc = LunaNpcApi.npcs(server).findByName(variant.name()).orElse(null);
            if (npc == null || npc.editAccess().apiCanEdit()) {
                npc = TestNpcs.define(server, variant.name(), NpcEditAccess.SHARED);
                TestNpcs.unarmed(npc);
                TestNpcs.standStill(npc);
                npc.setEditAccess(variant.access());
            }
            TestNpcs.spawn(level, npc, layout.ground(level, 4, (index - 1) * 3.0));
            TestNpcs.report(source, npc, variant.what() + " §7API write: " + probeApiWrite(npc));
        }
        source.sendSuccess(() -> Component.literal("§aEdit access test set up. §7/lunatest clear removes it."), false);
        return variants.size();
    }

    private static String probeApiWrite(NpcDefinition npc) {
        try {
            npc.setHealth(npc.health());
            return "§aallowed";
        } catch (IllegalStateException refused) {
            return "§crefused";
        }
    }
}
