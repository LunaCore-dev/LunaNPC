package net.lunacore.luna_npc.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.LunaNpcConfig;
import net.lunacore.luna_npc.api.NpcBodyguardMode;
import net.lunacore.luna_npc.api.NpcJobType;
import net.lunacore.luna_npc.entity.LunaNpcEntities;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.pathfinding.NpcPath;
import net.lunacore.luna_npc.pathfinding.PathWorldView;
import net.lunacore.luna_npc.pathfinding.PathfindingType;

public final class LunaNpcCommand {

    private LunaNpcCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("lunanpc")
                    .then(Commands.literal("info").executes(LunaNpcCommand::showInfo))
                    .then(bodyguardCommand());
            // The walk/run test commands are debug tools: without pathfinding_debug in
            // config/luna_npc.json they do not exist at all.
            if (LunaNpcConfig.pathfindingDebug()) {
                root.then(moveCommand("walk", false)).then(moveCommand("run", true));
            }
            dispatcher.register(root);
        });
    }

    // /lunanpc bodyguard assign <npcs> <player> [seconds] | dismiss <npcs> | order <npcs> follow|stay|home —
    // the command side of bodyguard hires. Seconds 0 = until it gives up; left out = the job's hire time.
    private static LiteralArgumentBuilder<CommandSourceStack> bodyguardCommand() {
        LiteralArgumentBuilder<CommandSourceStack> order = Commands.literal("order");
        var orderTargets = Commands.argument("npcs", EntityArgument.entities());
        for (NpcBodyguardMode mode : NpcBodyguardMode.values()) {
            orderTargets.then(Commands.literal(mode.getSerializedName()).executes(context ->
                    forEachGuard(context, guard -> guard.bodyguardController().order(mode))));
        }
        return Commands.literal("bodyguard")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("assign").then(Commands.argument("npcs", EntityArgument.entities())
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> assignGuards(context, -1))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0,
                                                net.lunacore.luna_npc.npc.NpcValidation.MAX_BODYGUARD_HIRE_SECONDS))
                                        .executes(context -> assignGuards(context,
                                                IntegerArgumentType.getInteger(context, "seconds")))))))
                .then(Commands.literal("dismiss").then(Commands.argument("npcs", EntityArgument.entities())
                        .executes(context -> forEachGuard(context, guard -> guard.bodyguardController()
                                .release(net.lunacore.luna_npc.api.NpcBodyguardRelease.DISMISSED)))))
                .then(order.then(orderTargets));
    }

    private static int assignGuards(CommandContext<CommandSourceStack> context, int seconds)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer owner = EntityArgument.getPlayer(context, "player");
        return forEachGuard(context, guard -> guard.bodyguardController()
                .hire(owner.getUUID(), owner.getGameProfile().name(), seconds));
    }

    // Applies the change to every selected NPC whose job is BODYGUARD; reports how many it reached.
    private static int forEachGuard(CommandContext<CommandSourceStack> context, java.util.function.Consumer<NpcEntity> change)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int count = 0;
        for (Entity selected : EntityArgument.getEntities(context, "npcs")) {
            if (selected instanceof NpcEntity guard && guard.jobSettings().jobType() == NpcJobType.BODYGUARD) {
                change.accept(guard);
                count++;
            }
        }
        if (count == 0) {
            context.getSource().sendFailure(Component.literal("No bodyguard NPC selected"));
            return 0;
        }
        int reached = count;
        context.getSource().sendSuccess(() -> Component.literal("Updated " + reached + " bodyguard(s)"), true);
        return reached;
    }

    // /lunanpc walk|run <lid> <pos>|here — sends every spawned entity of that NPC in the
    // sender's dimension pathfinding to the position; a debug-only navigation test tool.
    private static LiteralArgumentBuilder<CommandSourceStack> moveCommand(String name, boolean run) {
        return Commands.literal(name)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("lid", LongArgumentType.longArg(0))
                        .then(Commands.literal("here").executes(context ->
                                moveNpcs(context, context.getSource().getPosition(), run)))
                        .then(Commands.argument("pos", Vec3Argument.vec3()).executes(context ->
                                moveNpcs(context, Vec3Argument.getVec3(context, "pos"), run))));
    }

    private static int moveNpcs(CommandContext<CommandSourceStack> context, Vec3 target, boolean run) {
        long lunaId = LongArgumentType.getLong(context, "lid");
        List<? extends NpcEntity> entities = context.getSource().getLevel()
                .getEntities(LunaNpcEntities.NPC, entity -> entity.lunaId() == lunaId);
        int moving = 0;
        for (NpcEntity entity : entities) {
            if (entity.navigation().moveTo(target, run)) {
                moving++;
            }
        }
        if (entities.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No spawned NPC with LID " + lunaId
                    + " in this dimension"));
            return 0;
        }
        int count = moving;
        context.getSource().sendSuccess(() -> Component.literal(count + "/" + entities.size()
                + " NPC(s) moving towards " + String.format(java.util.Locale.ROOT,
                "%.1f %.1f %.1f", target.x, target.y, target.z)), false);
        printPlannerComparison(context.getSource(), entities.getFirst(), target);
        return count;
    }

    // Debug companion of the walk command (pathfinding_debug only): plans the same route with
    // every algorithm on an effectively uncapped budget and prints what each one would find.
    // Display only — the NPC itself always runs its own capped planner.
    private static void printPlannerComparison(CommandSourceStack source, NpcEntity entity, Vec3 target) {
        PathWorldView world = new PathWorldView(entity.level(),
                entity.movementSettings().avoidedBlocks(), 3,
                net.lunacore.luna_npc.npc.NpcValidation.MAX_LIGHT_LEVEL,
                net.lunacore.luna_npc.npc.NpcValidation.MIN_LIGHT_LEVEL,
                entity.traitsSettings().avoidWater(), entity.traitsSettings().avoidLava(),
                entity.traitsSettings().canSwim() && !entity.traitsSettings().avoidWater(),
                Math.max(1, net.minecraft.util.Mth.ceil(entity.getBbHeight())),
                PathWorldView.footprintCells(entity.getBbWidth()),
                entity.traitsSettings().canTraverseDoors());
        double offset = world.waypointOffset();
        BlockPos targetBlock = BlockPos.containing(target.x - offset, target.y, target.z - offset);
        int goalY = world.snapToGround(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), 2, 8);
        if (goalY == PathWorldView.NO_STEP) {
            source.sendSuccess(() -> Component.literal(
                    "debug: goal snap failed - no standable spot near the target column")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        BlockPos start = BlockPos.containing(entity.getX() - offset, entity.getY(), entity.getZ() - offset);
        BlockPos goal = new BlockPos(targetBlock.getX(), goalY, targetBlock.getZ());
        source.sendSuccess(() -> Component.literal("debug: uncapped planner comparison "
                + start.toShortString() + " -> " + goal.toShortString()).withStyle(ChatFormatting.GRAY), false);
        for (PathfindingType type : PathfindingType.values()) {
            long startedAt = System.nanoTime();
            NpcPath path = type.createDiagnosticPlanner().plan(world, start, goal);
            double milliseconds = (System.nanoTime() - startedAt) / 1.0E6;
            String status = path == null ? "no path" : path.reachedGoal() ? "full path" : "partial path";
            ChatFormatting color = path == null ? ChatFormatting.RED
                    : path.reachedGoal() ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
            String detail = path == null ? "" : ", " + path.waypoints().size() + " waypoints";
            String line = String.format(java.util.Locale.ROOT, "  %s: %s%s, %.1f ms",
                    type.id(), status, detail, milliseconds);
            source.sendSuccess(() -> Component.literal(line).withStyle(color), false);
        }
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) {
        String modVersion = installedVersionOf(LunaNpc.MOD_ID);
        String minecraftVersion = installedVersionOf("minecraft");
        String fabricLoaderVersion = installedVersionOf("fabricloader");
        boolean everythingResolved = modVersion != null && minecraftVersion != null && fabricLoaderVersion != null;

        MutableComponent info = Component.empty()
                .append(fieldLine("Current Version", modVersion)).append(Component.literal("\n"))
                .append(fieldLine("MC Version", minecraftVersion)).append(Component.literal("\n"))
                .append(fieldLine("Fabric Version", fabricLoaderVersion)).append(Component.literal("\n"))
                .append(statusLine(everythingResolved));

        context.getSource().sendSuccess(() -> info, false);
        return Command.SINGLE_SUCCESS;
    }

    private static MutableComponent fieldLine(String label, String value) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(value == null ? "Unknown" : value).withStyle(ChatFormatting.GOLD));
    }

    private static MutableComponent statusLine(boolean everythingResolved) {
        return Component.literal("Status: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(everythingResolved ? "OK" : "Failed")
                        .withStyle(everythingResolved ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static String installedVersionOf(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse(null);
    }
}
