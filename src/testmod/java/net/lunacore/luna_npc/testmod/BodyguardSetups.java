package net.lunacore.luna_npc.testmod;

import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.lunacore.luna_npc.api.AllianceRegistry;
import net.lunacore.luna_npc.api.LunaNpcApi;
import net.lunacore.luna_npc.api.NpcBodyguardHire;
import net.lunacore.luna_npc.api.NpcBodyguardSettings;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcGiveUpAction;
import net.lunacore.luna_npc.api.NpcJobSettings;
import net.lunacore.luna_npc.api.NpcProtectTargets;
import net.lunacore.luna_npc.api.NpcTradeStack;

// /lunatest bodyguard: a standing VIP NPC and one guard per bodyguard variant in a row in front of you.
final class BodyguardSetups {

    static final String ALL_PLAYERS_ALLIANCE = "LunaNPC Test: All Players";

    private BodyguardSetups() {
    }

    private record Variant(String name, NpcBodyguardSettings settings, boolean armed, String what) {
    }

    static int setUp(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel level = source.getLevel();
        TestNpcs.Layout layout = TestNpcs.Layout.of(source);

        NpcDefinition vip = TestNpcs.define(server, "TestBgVip");
        TestNpcs.unarmed(vip);
        TestNpcs.standStill(vip);
        vip.setJobs(NpcJobSettings.defaults());
        TestNpcs.spawn(level, vip, layout.ground(level, 8, 0));
        TestNpcs.report(source, vip, "stands still; the VIP-protecting guards pick it. Hit it to see them defend.");

        AllianceRegistry alliances = LunaNpcApi.alliances(server);
        String everyone = alliances.getOrCreate(ALL_PLAYERS_ALLIANCE).id();
        alliances.setMatchAllPlayers(everyone, true);

        NpcProtectTargets protectVip = NpcProtectTargets.npcs(vip.lunaId());
        List<Variant> variants = List.of(
                new Variant("TestBgTargets", NpcBodyguardSettings.protecting(protectVip), true,
                        "chosen target = the VIP, defends until the attacker is dead."),
                new Variant("TestBgEveryone",
                        NpcBodyguardSettings.protecting(NpcProtectTargets.alliances(everyone)).withTeleport(20), true,
                        "chosen target = an all-players alliance; locks onto the closest player and teleports past 20 blocks."),
                new Variant("TestBgHire",
                        NpcBodyguardSettings.forHire(new NpcBodyguardHire(
                                List.of(new NpcTradeStack("minecraft:emerald", 1)), 120))
                                .withTeleport(32).withGivingUp(true, NpcGiveUpAction.GO_HOME), true,
                        "hire for 1 emerald, 2 min; keeps you after death, teleports past 32, goes home when done."),
                new Variant("TestBgFreeGone",
                        NpcBodyguardSettings.forHire(new NpcBodyguardHire(List.of(), 30))
                                .withFollow(16, 3, 5).withGivingUp(false, NpcGiveUpAction.DESPAWN), true,
                        "free hire, 30 s; range 16 without teleport, gives up at your death, then despawns."),
                new Variant("TestBgAssist",
                        NpcBodyguardSettings.protecting(protectVip).withDefense(10, true).withRetreat(true, 8), true,
                        "guards the VIP, fights 10 s, joins the VIP's fights, retreats past 8 blocks."),
                new Variant("TestBgNoFF",
                        NpcBodyguardSettings.forHire(new NpcBodyguardHire(List.of(), 120)).withFriendlyFire(false), true,
                        "free hire, 2 min; once you hire it your hits do nothing to it, anyone else's still do."),
                new Variant("TestBgNoFFVip", NpcBodyguardSettings.protecting(protectVip).withFriendlyFire(false), true,
                        "guards the VIP; the VIP's hits do nothing to it, yours still do."),
                new Variant("TestBgBadSetup",
                        NpcBodyguardSettings.protecting(NpcProtectTargets.none()).withTeleport(2).withRetreat(true, 2),
                        false, "illogical on purpose — should list warnings below and in red op chat."));
        for (int index = 0; index < variants.size(); index++) {
            Variant variant = variants.get(index);
            NpcDefinition guard = TestNpcs.define(server, variant.name());
            if (variant.armed()) {
                guard.giveMeleeWeapon("minecraft:iron_sword", 4.0F);
                guard.setCombat(guard.combat().withRanged(guard.combat().ranged().withEnabled(false)));
            } else {
                TestNpcs.unarmed(guard);
            }
            guard.giveBodyguardJob(variant.settings());
            TestNpcs.spawn(level, guard, layout.ground(level, 3, (index - (variants.size() - 1) / 2.0) * 3.0));
            TestNpcs.report(source, guard, variant.what());
        }
        source.sendSuccess(() -> Component.literal("§aBodyguard test set up. §7/lunatest clear removes it."), false);
        return variants.size() + 1;
    }
}
