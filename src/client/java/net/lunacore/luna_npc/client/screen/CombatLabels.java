package net.lunacore.luna_npc.client.screen;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcCombatAction;
import net.lunacore.luna_npc.api.NpcCombatActionType;
import net.lunacore.luna_npc.api.NpcCombatAttackKind;
import net.lunacore.luna_npc.api.NpcCombatComparator;
import net.lunacore.luna_npc.api.NpcCombatCondition;
import net.lunacore.luna_npc.api.NpcCombatPlacement;
import net.lunacore.luna_npc.api.NpcCombatTriggerType;
import net.lunacore.luna_npc.api.NpcNavMode;
import net.lunacore.luna_npc.api.NpcPlacementCentre;
import net.lunacore.luna_npc.api.NpcPlacementShape;
import net.lunacore.luna_npc.api.NpcTargetSelector;
import net.lunacore.luna_npc.api.NpcValueSource;
import net.lunacore.luna_npc.api.NpcValueSourceType;

// The one place the advanced-combat enums turn into display text and every card/list row builds its
// one-line summary, so the graph, dock and leaf editors can never disagree on how a rule reads.
final class CombatLabels {

    private CombatLabels() {
    }

    static Component trigger(NpcCombatTriggerType type) {
        return Component.translatable("gui.luna_npc.combat.trigger." + type.getSerializedName());
    }

    static Component valueSource(NpcValueSourceType type) {
        return Component.translatable("gui.luna_npc.combat.value." + type.getSerializedName());
    }

    static Component action(NpcCombatActionType type) {
        return Component.translatable("gui.luna_npc.combat.action." + type.getSerializedName());
    }

    static Component target(NpcTargetSelector selector) {
        return Component.translatable("gui.luna_npc.combat.target." + selector.getSerializedName());
    }

    static Component centre(NpcPlacementCentre centre) {
        return Component.translatable("gui.luna_npc.combat.centre." + centre.getSerializedName());
    }

    static Component shape(NpcPlacementShape shape) {
        return Component.translatable("gui.luna_npc.combat.shape." + shape.getSerializedName());
    }

    static Component navMode(NpcNavMode mode) {
        return Component.translatable("gui.luna_npc.combat.nav." + mode.getSerializedName());
    }

    static Component attackKind(NpcCombatAttackKind kind) {
        return Component.translatable("gui.luna_npc.combat.kind." + kind.getSerializedName());
    }

    static Component yMode(int yMode) {
        return Component.translatable("gui.luna_npc.combat.ymode." + Math.clamp(yMode, 0, 2));
    }

    static Component comparatorText(NpcCombatComparator comparator) {
        return Component.translatable("gui.luna_npc.combat.comparator." + comparator.getSerializedName());
    }

    static String comparatorGlyph(NpcCombatComparator comparator) {
        return switch (comparator) {
            case LESS -> "<";
            case LESS_EQUAL -> "≤";
            case EQUAL -> "=";
            case NOT_EQUAL -> "≠";
            case GREATER_EQUAL -> "≥";
            case GREATER -> ">";
        };
    }

    static Component valueSourceSummary(NpcValueSource value) {
        return switch (value.type()) {
            case CONSTANT -> Component.literal(trim(value.value()));
            case RANDOM -> Component.literal(trim(value.value()) + ".." + trim(value.value2()));
            case HOSTILES_IN_RADIUS, PLAYERS_IN_RADIUS ->
                    valueSource(value.type()).copy().append(" r" + value.radius());
            case FLAG_VALUE, COUNTER_VALUE ->
                    valueSource(value.type()).copy().append(" " + value.key());
            default -> valueSource(value.type());
        };
    }

    static Component conditionSummary(NpcCombatCondition condition) {
        Component body = valueSourceSummary(condition.left()).copy()
                .append(" " + comparatorGlyph(condition.comparator()) + " ")
                .append(valueSourceSummary(condition.right()));
        return condition.negate()
                ? Component.literal("¬ ").append(body)
                : body;
    }

    static Component actionSummary(NpcCombatAction action) {
        String arg = action.stringArg();
        Component base = action(action.type());
        return arg == null || arg.isBlank() ? base : base.copy().append(": " + arg);
    }

    static Component placementSummary(NpcCombatPlacement placement) {
        return centre(placement.centre()).copy().append(" · ").append(shape(placement.shape()));
    }

    private static String trim(float value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.1f", value);
    }
}
