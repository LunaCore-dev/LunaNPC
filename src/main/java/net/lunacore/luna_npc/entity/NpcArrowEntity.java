package net.lunacore.luna_npc.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.lunacore.luna_npc.combat.ImpactEffects;
import net.lunacore.luna_npc.combat.NpcCombatController;
import net.lunacore.luna_npc.combat.ProjectileMotion;
import net.lunacore.luna_npc.api.NpcImpactSettings;

// The NPC's arrow projectile: a real arrow with the authentic model, sticking and dropping
// like one (tipped arrow cosmetics color it, with no potion effect of their own). Damage and
// effects come from the NPC's ranged attack settings, never from the arrow itself.
public class NpcArrowEntity extends Arrow {

    private float damage;
    private boolean homing;
    private boolean curve;
    private float curveMaxDistance = 100.0F;
    private int pierceRemaining;
    private UUID targetId;
    private double traveled;
    private double straightSpeed;
    private java.util.List<net.lunacore.luna_npc.api.NpcAttackEffect> advancedEffects;

    public NpcArrowEntity(EntityType<? extends Arrow> type, Level level) {
        super(type, level);
        // Vanilla AbstractArrow plays a hardcoded arrow-hit sound on impact (its super.onHitBlock). Silence
        // the entity so the only impact sound is the creator's configured one via ImpactEffects — an empty
        // impact id must stay truly silent, with no vanilla arrow thunk leaking through.
        setSilent(true);
    }

    // Marks this shot as an advanced-combat attack: on hit it deals its own damage and these effects,
    // bypassing the NPC's ranged attack settings entirely (the attack library is independent).
    public void setAdvancedEffects(java.util.List<net.lunacore.luna_npc.api.NpcAttackEffect> effects) {
        this.advancedEffects = effects;
    }

    public void configure(float damage, boolean homing, boolean curve, float curveMaxDistance,
                          int pierceRemaining, LivingEntity target, ItemStack cosmetic, double speedPerTick) {
        this.damage = damage;
        this.homing = homing;
        this.curve = curve;
        this.curveMaxDistance = curveMaxDistance;
        this.pierceRemaining = pierceRemaining;
        this.targetId = target.getUUID();
        this.straightSpeed = speedPerTick;
        this.pickup = AbstractArrow.Pickup.DISALLOWED;
        if (!cosmetic.isEmpty()) {
            // The pickup stack drives the tipped arrow color; purely cosmetic.
            setPickupItemStack(cosmetic.copy());
        }
        setNoGravity(!curve);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || isRemoved()) {
            return;
        }
        double speed = getDeltaMovement().length();
        traveled += speed;
        if (traveled > (curve ? curveMaxDistance : 128.0) || tickCount > 600) {
            discard();
            return;
        }
        // A stuck arrow keeps its vanilla behavior (it dropped); only flying arrows steer.
        if (speed > 0.05) {
            ProjectileMotion.tick(this, targetId, homing, curve ? 0.0 : straightSpeed);
        }
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
            NpcImpactSettings impact = npc.combatSettings().ranged().impact();
            ImpactEffects.trigger(level, result.getLocation(), impact, npc.farImpactSoundId());
            // An arrow cannot stay stuck in a block the impact just blew away.
            if (impact.destroyBlocks()) {
                discard();
            }
        }
    }
}
