package net.lunacore.luna_npc.combat;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.lunacore.luna_npc.LunaNpc;

// Attack damage sources that mirror the vanilla mob attacks but use "never" difficulty scaling, so
// an NPC deals its configured damage exactly. Used when an NPC's difficulty-scaling toggle is off;
// when it is on, the combat code uses the vanilla scaling sources instead.
public final class NpcDamageSources {

    private static final ResourceKey<DamageType> NPC_ATTACK = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "npc_attack"));
    private static final ResourceKey<DamageType> NPC_PROJECTILE = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "npc_projectile"));

    private NpcDamageSources() {
    }

    public static DamageSource npcAttack(ServerLevel level, Entity attacker) {
        return new DamageSource(holder(level, NPC_ATTACK), attacker);
    }

    public static DamageSource npcProjectile(ServerLevel level, Entity projectile, Entity attacker) {
        return new DamageSource(holder(level, NPC_PROJECTILE), projectile, attacker);
    }

    private static Holder<DamageType> holder(ServerLevel level, ResourceKey<DamageType> key) {
        return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
    }
}
