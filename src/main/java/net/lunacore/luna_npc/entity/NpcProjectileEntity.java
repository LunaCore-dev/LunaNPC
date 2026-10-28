package net.lunacore.luna_npc.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.lunacore.luna_npc.combat.ImpactEffects;
import net.lunacore.luna_npc.combat.NpcCombatController;
import net.lunacore.luna_npc.combat.ProjectileMotion;

// The NPC's non-arrow projectile (fireball, TNT, wind charge, any item look — rendered big and
// glowing). The look is purely cosmetic; damage and effects come from the NPC's ranged attack
// settings on impact. Straight shots hold their speed and despawn crisply at max range instead
// of dragging to a floating stop; curved shots arc down and die on the ground.
public class NpcProjectileEntity extends ThrowableItemProjectile {

    private float damage;
    private boolean homing;
    private boolean curve;
    private float curveMaxDistance = 100.0F;
    private int pierceRemaining;
    private UUID targetId;
    private double traveled;
    private double straightSpeed;
    private java.util.List<net.lunacore.luna_npc.api.NpcAttackEffect> advancedEffects;

    public NpcProjectileEntity(EntityType<? extends NpcProjectileEntity> type, Level level) {
        super(type, level);
    }

    // Marks this shot as an advanced-combat attack: on hit it deals its own damage and these effects,
    // bypassing the NPC's ranged attack settings entirely (the attack library is independent).
    public void setAdvancedEffects(java.util.List<net.lunacore.luna_npc.api.NpcAttackEffect> effects) {
        this.advancedEffects = effects;
    }

    public void configure(float damage, boolean homing, boolean curve, float curveMaxDistance,
                          int pierceRemaining, LivingEntity target, double speedPerTick) {
        this.damage = damage;
        this.homing = homing;
        this.curve = curve;
        this.curveMaxDistance = curveMaxDistance;
        this.pierceRemaining = pierceRemaining;
        this.targetId = target.getUUID();
        this.straightSpeed = speedPerTick;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.ARROW;
    }

    // Straight shots fly gravity-free; the curve arc uses a gravity that bends the shot roughly
    // 90° downwards over curveMaxDistance at its launch speed.
    @Override
    protected double getDefaultGravity() {
        if (!curve) {
            return 0.0;
        }
        double speed = getDeltaMovement().length();
        return speed * speed / Math.max(1.0F, curveMaxDistance);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || isRemoved()) {
            return;
        }
        traveled += getDeltaMovement().length();
        if (traveled > (curve ? curveMaxDistance : 128.0) || tickCount > 400) {
            discard();
            return;
        }
        ProjectileMotion.tick(this, targetId, homing, curve ? 0.0 : straightSpeed);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel level)
                || !(getOwner() instanceof NpcEntity npc)
                || !(result.getEntity() instanceof LivingEntity living)
                || living == npc) {
            return;
        }
        if (advancedEffects != null) {
            NpcCombatController.applyAdvancedHit(level, npc, this, living, damage, advancedEffects);
        } else {
            NpcCombatController.applyRangedHit(level, npc, this, living, damage);
        }
        ImpactEffects.trigger(level, result.getLocation(), npc.combatSettings().ranged().impact(),
                npc.farImpactSoundId());
        if (--pierceRemaining < 0) {
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel level && getOwner() instanceof NpcEntity npc) {
            ImpactEffects.trigger(level, result.getLocation(), npc.combatSettings().ranged().impact(),
                    npc.farImpactSoundId());
        }
        if (!level().isClientSide()) {
            discard();
        }
    }
}
