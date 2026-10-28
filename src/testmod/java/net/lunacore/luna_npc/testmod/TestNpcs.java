package net.lunacore.luna_npc.testmod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.api.LunaNpcApi;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcEditAccess;
import net.lunacore.luna_npc.api.NpcInstance;
import net.lunacore.luna_npc.api.NpcWanderSettings;

// Shared helpers for the test setups — public API only. Every test definition lives in one category so
// /lunatest clear can find it, and is upserted by name so its LID stays the same between runs. Test NPCs
// are SHARED so their variants can be tweaked in the editor too.
final class TestNpcs {

    static final String CATEGORY = "lunatests";

    private TestNpcs() {
    }

    static NpcDefinition define(MinecraftServer server, String name) {
        return define(server, name, NpcEditAccess.SHARED);
    }

    static NpcDefinition define(MinecraftServer server, String name, NpcEditAccess access) {
        NpcDefinition npc = LunaNpcApi.npcs(server).getOrCreate(name, access);
        npc.setCategory(CATEGORY);
        npc.setNameTagShown(true);
        npc.setHealth(200);
        return npc;
    }

    static void unarmed(NpcDefinition npc) {
        npc.setCombat(npc.combat().withMelee(npc.combat().melee().withEnabled(false))
                .withRanged(npc.combat().ranged().withEnabled(false)));
    }

    static void standStill(NpcDefinition npc) {
        npc.setMovement(npc.movement().withWander(npc.movement().wander().withBehavior(NpcWanderSettings.STATIC)));
    }

    // The command source's position and facing: forward/side offsets turn into world positions, so a
    // setup always unfolds in front of whoever runs it (RCON: use "execute positioned ... run").
    record Layout(Vec3 origin, double yawRadians) {

        static Layout of(CommandSourceStack source) {
            Vec2 rotation = source.getRotation();
            return new Layout(source.getPosition(), Math.toRadians(rotation.y));
        }

        Vec3 at(double forward, double side) {
            double fx = -Math.sin(yawRadians);
            double fz = Math.cos(yawRadians);
            return new Vec3(origin.x + fx * forward - fz * side, origin.y, origin.z + fz * forward + fx * side);
        }

        BlockPos ground(ServerLevel level, double forward, double side) {
            Vec3 spot = at(forward, side);
            int x = Mth.floor(spot.x);
            int z = Mth.floor(spot.z);
            return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        }
    }

    // Spawns one fresh copy (older live copies of the definition are removed first).
    static NpcInstance spawn(ServerLevel level, NpcDefinition npc, BlockPos feet) {
        removeLive(level.getServer(), lunaId -> lunaId == npc.lunaId());
        return LunaNpcApi.spawn(level, npc, feet);
    }

    static int removeLive(MinecraftServer server, LongPredicate which) {
        List<NpcInstance> found = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof NpcInstance npc && which.test(npc.lunaId())) {
                    found.add(npc);
                }
            }
        }
        found.forEach(NpcInstance::discard);
        return found.size();
    }

    static void report(CommandSourceStack source, NpcDefinition npc, String what) {
        source.sendSuccess(() -> Component.literal("§e" + npc.name() + "§7 (LID " + npc.lunaId() + "): §f" + what), false);
        npc.warnings().forEach(warning -> source.sendSuccess(
                () -> Component.literal("   §c⚠ " + warning.message()), false));
    }
}
