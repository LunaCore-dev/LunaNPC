package net.lunacore.luna_npc.combat.advanced;

import net.minecraft.world.entity.LivingEntity;
import net.lunacore.luna_npc.api.NpcCombatCondition;
import net.lunacore.luna_npc.api.NpcValueSource;

// Resolves a NpcValueSource to a live number and a NpcCombatCondition to a boolean. Every count source is
// scoped to the engagement bubble — the NPC never reads the world beyond its own awareness radius.
final class CombatValueEvaluator {

    private CombatValueEvaluator() {
    }

    static float evaluate(NpcValueSource source, CombatContext ctx) {
        AdvancedCombatEngine engine = ctx.engine;
        LivingEntity target = ctx.primaryTarget;
        return switch (source.type()) {
            case CONSTANT -> source.value();
            case SELF_HP -> (float) ctx.npc.lunaHealth();
            case SELF_HP_PERCENT -> percent(ctx.npc.lunaHealth(), ctx.npc.lunaMaxHealth());
            case MISSING_HP_PERCENT -> 100.0F - percent(ctx.npc.lunaHealth(), ctx.npc.lunaMaxHealth());
            case TARGET_HP -> target == null ? 0.0F : target.getHealth();
            case TARGET_HP_PERCENT -> target == null ? 0.0F : percent(target.getHealth(), target.getMaxHealth());
            case DISTANCE_TO_TARGET -> target == null ? Float.MAX_VALUE : (float) ctx.npc.distanceTo(target);
            case HOSTILES_IN_RADIUS -> engine.countHostilesInRadius(source.radius());
            case PLAYERS_IN_RADIUS -> engine.countPlayersInRadius(source.radius());
            case SUMMONS_ALIVE -> engine.summonsAlive();
            case TIME_IN_COMBAT -> engine.secondsInCombat();
            case TIME_SINCE_HIT -> engine.secondsSinceHit();
            case FLAG_VALUE -> engine.flag(source.key());
            case COUNTER_VALUE -> engine.counter(source.key());
            case RANDOM -> source.value() + engine.random().nextFloat() * Math.max(0.0F, source.value2() - source.value());
            case PLAYER_COUNT -> engine.playersInEngagement();
            case TARGET_IN_LINE_OF_SIGHT -> target != null && ctx.npc.hasLineOfSight(target) ? 1.0F : 0.0F;
        };
    }

    static boolean matches(NpcCombatCondition condition, CombatContext ctx) {
        float left = evaluate(condition.left(), ctx);
        float right = evaluate(condition.right(), ctx);
        boolean result = condition.comparator().test(left, right);
        return condition.negate() != result;
    }

    static boolean allMatch(java.util.List<NpcCombatCondition> conditions, CombatContext ctx) {
        for (NpcCombatCondition condition : conditions) {
            if (!matches(condition, ctx)) {
                return false;
            }
        }
        return true;
    }

    private static float percent(double current, double max) {
        return max <= 0.0 ? 0.0F : (float) (current / max * 100.0);
    }
}
