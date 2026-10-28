package net.lunacore.luna_npc.combat;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

// Shared flight behavior of the NPC projectiles: the gentle homing (about 10° per 25 blocks of
// flight) and, for straight shots, a constant speed — vanilla drag would otherwise slow them
// into floating decorations instead of letting them fly out and despawn crisply.
public final class ProjectileMotion {

    private static final float HOMING_DEGREES_PER_BLOCK = 10.0F / 25.0F;

    private ProjectileMotion() {
    }

    public static void tick(Projectile projectile, UUID targetId, boolean homing, double straightSpeed) {
        Vec3 velocity = projectile.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0E-4) {
            return;
        }
        Vec3 direction = velocity.normalize();
        if (homing && targetId != null && projectile.level() instanceof ServerLevel level) {
            Entity target = level.getEntity(targetId);
            if (target instanceof LivingEntity living && !living.isDeadOrDying()) {
                Vec3 desired = living.getBoundingBox().getCenter().subtract(projectile.position()).normalize();
                double maxTurnRadians = Math.toRadians(HOMING_DEGREES_PER_BLOCK) * speed;
                double angle = Math.acos(Mth.clamp(direction.dot(desired), -1.0, 1.0));
                if (angle > 1.0E-3) {
                    direction = direction.lerp(desired, Math.min(1.0, maxTurnRadians / angle)).normalize();
                }
            }
        }
        projectile.setDeltaMovement(direction.scale(straightSpeed > 0.0 ? straightSpeed : speed));
    }
}
