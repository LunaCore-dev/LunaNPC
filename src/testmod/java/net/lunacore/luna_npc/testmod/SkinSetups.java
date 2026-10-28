package net.lunacore.luna_npc.testmod;

import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.lunacore.luna_npc.api.LunaNpcApi;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcSkinArms;
import net.lunacore.luna_npc.api.SkinRegistry;

// /lunatest skin: downloads real skins by name and by URL, plus inputs that must fail, through the skins
// API, and stands an NPC wearing each skin that came in. Results arrive in chat as the downloads finish.
// Test skins are named lunatest_*, so /lunatest clear deletes them again. Each result also goes to the
// server log, since RCON only sees what a command says before it returns.
final class SkinSetups {

    static final String NAME_PREFIX = "lunatest_";
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LunaNPC-Test");

    private SkinSetups() {
    }

    private record Case(String input, String name, NpcSkinArms arms, String expect) {
    }

    static int setUp(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel level = source.getLevel();
        TestNpcs.Layout layout = TestNpcs.Layout.of(source);
        SkinRegistry skins = LunaNpcApi.skins(server);
        skins.all().stream().filter(skin -> skin.name().startsWith(NAME_PREFIX)).toList()
                .forEach(skin -> skins.delete(skin.id()));
        List<Case> cases = List.of(
                new Case("Notch", NAME_PREFIX + "notch", null, "works, classic arms from the profile"),
                new Case("jeb_", NAME_PREFIX + "jeb", NpcSkinArms.SLIM, "works, forced slim arms"),
                new Case("http://textures.minecraft.net/texture/292009a4925b58f02c77dadc3ecef07ea4c7472f64e0fdc32ce5522489362680",
                        NAME_PREFIX + "url", null, "works, a URL (classic arms)"),
                new Case("not a name!", NAME_PREFIX + "bad", null, "fails: INVALID_INPUT"),
                new Case("http://127.0.0.1/skin.png", NAME_PREFIX + "local", null, "fails: BLOCKED_ADDRESS"),
                new Case("https://lunacore.dev/", NAME_PREFIX + "html", null, "fails: NOT_A_PNG"),
                new Case("Notch", "Bad Name", null, "fails: INVALID_NAME"),
                new Case("zq9_noone_x7k", NAME_PREFIX + "nobody", null, "fails: NO_SUCH_PLAYER"),
                new Case("https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png",
                        NAME_PREFIX + "big", null, "fails: WRONG_SIZE"));
        for (int index = 0; index < cases.size(); index++) {
            Case test = cases.get(index);
            int slot = index;
            skins.download(test.input(), test.name(), test.arms()).thenAccept(result -> {
                String outcome = result.succeeded()
                        ? "§aok§7 → " + result.skin().id() + " (" + result.skin().arms().getSerializedName() + ")"
                        : "§c" + result.failure();
                source.sendSuccess(() -> Component.literal("§e" + test.input() + "§7: " + outcome
                        + " §8(expected: " + test.expect() + ")"), false);
                LOGGER.info("skin test {}: {} (expected: {})", test.input(), outcome.replaceAll("§.", ""), test.expect());
                if (result.succeeded()) {
                    NpcDefinition npc = TestNpcs.define(server, "TestSkin" + slot);
                    TestNpcs.unarmed(npc);
                    TestNpcs.standStill(npc);
                    npc.setModelSettings(result.skin().asModel());
                    TestNpcs.spawn(level, npc, layout.ground(level, 4, (slot - 1) * 2.0));
                }
            });
        }
        source.sendSuccess(() -> Component.literal("§aDownloading " + cases.size() + " skins… §7results follow. "
                + "/lunatest clear removes them."), false);
        return cases.size();
    }
}
