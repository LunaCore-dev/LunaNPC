package net.lunacore.luna_npc.testmod;

import java.util.List;
import java.util.function.ToIntFunction;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.lunacore.luna_npc.api.AllianceRegistry;
import net.lunacore.luna_npc.api.LunaNpcApi;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcInstance;
import net.lunacore.luna_npc.api.NpcRoute;
import net.lunacore.luna_npc.api.RouteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Dev-only mod (runServer only, never in the shipped jar) that builds ready-to-test variants of each
// feature through the public API alone — a standing API stress test and a shortcut for in-game testing.
// /lunatest <setup> unfolds in front of the caller, /lunatest status reports each test NPC's live job
// state, and /lunatest clear removes every test NPC, route and alliance again. Add a setup for every new
// feature (see CLAUDE.md).
public final class LunaNpcTestMod implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("LunaNPC-Test");

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("lunatest")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("bodyguard").executes(context -> run(context.getSource(), BodyguardSetups::setUp)))
                        .then(Commands.literal("patrol").executes(context -> run(context.getSource(), PatrolSetups::setUp)))
                        .then(Commands.literal("access").executes(context -> run(context.getSource(), AccessSetups::setUp)))
                        .then(Commands.literal("skin").executes(context -> run(context.getSource(), SkinSetups::setUp)))
                        .then(Commands.literal("status").executes(context -> run(context.getSource(), LunaNpcTestMod::status)))
                        .then(Commands.literal("clear").executes(context -> run(context.getSource(), LunaNpcTestMod::clear)))));
    }

    // Vanilla hides command exceptions; a failing setup is exactly what this mod is for, so log it whole.
    private static int run(CommandSourceStack source, ToIntFunction<CommandSourceStack> setup) {
        try {
            return setup.applyAsInt(source);
        } catch (RuntimeException exception) {
            LOGGER.error("/lunatest failed", exception);
            source.sendFailure(Component.literal("/lunatest failed: " + exception));
            return 0;
        }
    }

    // One line per live test NPC with its job state as the API reports it.
    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof NpcInstance npc)) {
                    continue;
                }
                NpcDefinition definition = LunaNpcApi.npcs(server).byLunaId(npc.lunaId()).orElse(null);
                if (definition == null || !TestNpcs.CATEGORY.equals(definition.category())) {
                    continue;
                }
                String line = switch (definition.jobs().jobType()) {
                    case BODYGUARD -> {
                        LivingEntity protectee = npc.bodyguardProtectee();
                        yield "protects " + (protectee == null ? "-" : protectee.getName().getString())
                                + " | hirer " + (npc.bodyguardHirer() == null ? "-" : npc.bodyguardHirer())
                                + " | " + npc.bodyguardMode().getSerializedName()
                                + " | hire left " + npc.bodyguardRemainingSeconds() + "s";
                    }
                    case PATROL -> "route " + npc.patrolRouteId() + " | point " + npc.patrolPointIndex();
                    default -> definition.jobs().jobType().name().toLowerCase(java.util.Locale.ROOT)
                            + " | " + definition.editAccess().getSerializedName();
                };
                String text = definition.name() + " @ " + entity.blockPosition().toShortString() + ": " + line;
                source.sendSuccess(() -> Component.literal(text), false);
                count++;
            }
        }
        return count;
    }

    private static int clear(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<NpcDefinition> tests = LunaNpcApi.npcs(server).all().stream()
                .filter(npc -> TestNpcs.CATEGORY.equals(npc.category())).toList();
        int removed = TestNpcs.removeLive(server, lunaId -> tests.stream().anyMatch(npc -> npc.lunaId() == lunaId));
        // The API may not remove an in-game-only NPC, so those stay until switched back in the editor.
        List<NpcDefinition> kept = tests.stream().filter(npc -> !npc.editAccess().apiCanEdit()).toList();
        tests.stream().filter(npc -> npc.editAccess().apiCanEdit())
                .forEach(npc -> LunaNpcApi.npcs(server).remove(npc.lunaId()));
        kept.forEach(npc -> source.sendSuccess(() -> Component.literal("§e" + npc.name()
                + "§7 is in-game only, so the API can't remove it. Set it to \"In-game and API\" in the editor"
                + " and clear again."), false));
        RouteRegistry routes = LunaNpcApi.routes(server);
        List<NpcRoute> testRoutes = routes.all().stream()
                .filter(route -> route.name().startsWith(PatrolSetups.ROUTE_PREFIX)).toList();
        testRoutes.forEach(route -> routes.delete(route.id()));
        net.lunacore.luna_npc.api.SkinRegistry skins = LunaNpcApi.skins(server);
        skins.all().stream().filter(skin -> skin.name().startsWith(SkinSetups.NAME_PREFIX)).toList()
                .forEach(skin -> skins.delete(skin.id()));
        AllianceRegistry alliances = LunaNpcApi.alliances(server);
        alliances.all().stream().filter(alliance -> alliance.name().equals(BodyguardSetups.ALL_PLAYERS_ALLIANCE))
                .toList().forEach(alliance -> alliances.delete(alliance.id()));
        int definitions = tests.size() - kept.size();
        source.sendSuccess(() -> Component.literal("§aRemoved " + definitions + " test NPC definitions ("
                + removed + " live), " + testRoutes.size() + " routes."), true);
        return definitions;
    }
}
