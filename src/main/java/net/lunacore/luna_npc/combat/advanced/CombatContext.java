package net.lunacore.luna_npc.combat.advanced;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.lunacore.luna_npc.entity.NpcEntity;

// The shared references one rule evaluation reads from: the running engine and the NPC's current primary
// target (null when it is fighting no one). Conditions and value sources resolve SELF against the NPC and
// TARGET against this primary; per-target action work carries its own anchor separately.
final class CombatContext {

    final AdvancedCombatEngine engine;
    final NpcEntity npc;
    final ServerLevel level;
    final LivingEntity primaryTarget;

    CombatContext(AdvancedCombatEngine engine, LivingEntity primaryTarget) {
        this.engine = engine;
        this.npc = engine.npc();
        this.level = engine.level();
        this.primaryTarget = primaryTarget;
    }
}
