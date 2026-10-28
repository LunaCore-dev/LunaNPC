package net.lunacore.luna_npc.testmod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.lunacore.luna_npc.api.LunaNpcApi;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcJobSettings;
import net.lunacore.luna_npc.api.NpcPatrolMode;
import net.lunacore.luna_npc.api.NpcPatrolSpeed;
import net.lunacore.luna_npc.api.NpcPatrolSubType;
import net.lunacore.luna_npc.api.NpcRoute;
import net.lunacore.luna_npc.api.NpcRoutePoint;
import net.lunacore.luna_npc.api.NpcRouteSpeed;
import net.lunacore.luna_npc.api.RouteRegistry;

// /lunatest patrol: two square routes in front of you (one walked, one with per-point speeds) and a
// patrol NPC for each mode and speed override, plus a chunk-loading one.
final class PatrolSetups {

    static final String ROUTE_PREFIX = "Test Route ";
    private static final int SIDE = 12;

    private PatrolSetups() {
    }

    private record Variant(String name, String route, NpcPatrolMode mode, NpcPatrolSpeed speed, boolean chunkLoader,
                           String what) {
    }

    static int setUp(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel level = source.getLevel();
        TestNpcs.Layout layout = TestNpcs.Layout.of(source);
        String walkRoute = upsertRoute(level, layout, ROUTE_PREFIX + "Walk", -SIDE - 2, NpcRouteSpeed.WALK);
        String perPointRoute = upsertRoute(level, layout, ROUTE_PREFIX + "Per Point", 2, NpcRouteSpeed.PER_POINT);
        List<Variant> variants = List.of(
                new Variant("TestPatrolLoop", walkRoute, NpcPatrolMode.LOOP, NpcPatrolSpeed.ROUTE, false,
                        "walk route in a loop; waits 2 s at the first corner, 1 s facing east at the third."),
                new Variant("TestPatrolPong", walkRoute, NpcPatrolMode.PING_PONG, NpcPatrolSpeed.RUN, false,
                        "same route back and forth, always running (NPC overrides the route speed)."),
                new Variant("TestPatrolRandom", perPointRoute, NpcPatrolMode.RANDOM, NpcPatrolSpeed.ROUTE, false,
                        "per-point route in random order; runs every other leg."),
                new Variant("TestPatrolLoader", perPointRoute, NpcPatrolMode.LOOP, NpcPatrolSpeed.WALK, true,
                        "per-point route in a loop, always walking, keeps its chunks loaded."));
        RouteRegistry routes = LunaNpcApi.routes(server);
        for (Variant variant : variants) {
            NpcDefinition npc = TestNpcs.define(server, variant.name());
            TestNpcs.unarmed(npc);
            npc.setJobs(NpcJobSettings.patrol(new NpcPatrolSubType(100, variant.route(), variant.mode(), variant.speed())));
            npc.setChunkLoader(variant.chunkLoader());
            NpcRoute route = routes.byId(variant.route());
            TestNpcs.spawn(level, npc, route.point(0).pos());
            TestNpcs.report(source, npc, variant.what());
        }
        source.sendSuccess(() -> Component.literal("§aPatrol test set up. §7/lunatest clear removes it."), false);
        return variants.size();
    }

    // A SIDE x SIDE square on the ground, starting `side` blocks to the right of the source.
    private static String upsertRoute(ServerLevel level, TestNpcs.Layout layout, String name, int side,
                                      NpcRouteSpeed speed) {
        int[][] corners = {{4, side}, {4, side + SIDE}, {4 + SIDE, side + SIDE}, {4 + SIDE, side}};
        List<NpcRoutePoint> points = new ArrayList<>();
        for (int index = 0; index < corners.length; index++) {
            NpcRoutePoint point = NpcRoutePoint.at(layout.ground(level, corners[index][0], corners[index][1]));
            if (index == 0) {
                point = point.withWaitSeconds(2);
            }
            if (index == 2) {
                point = point.withWaitSeconds(1).withLook(true, -90.0F);
            }
            points.add(point.withRun(index % 2 == 1));
        }
        RouteRegistry routes = LunaNpcApi.routes(level.getServer());
        NpcRoute existing = routes.byName(name);
        String dimension = level.dimension().identifier().toString();
        if (existing != null && existing.dimension().equals(dimension)) {
            routes.update(existing.withPoints(points).withSpeed(speed));
            return existing.id();
        }
        if (existing != null) {
            routes.delete(existing.id());
        }
        return routes.create(name, dimension, points, speed).id();
    }
}
