package net.lunacore.luna_npc.combat.advanced;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.api.LunaNpcEvents;
import net.lunacore.luna_npc.api.LunaNpcApi;
import net.lunacore.luna_npc.api.NpcAttackEffect;
import net.lunacore.luna_npc.api.NpcCombatAction;
import net.lunacore.luna_npc.api.NpcCombatAttack;
import net.lunacore.luna_npc.api.NpcCombatPlacement;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.combat.NpcCombatController;
import net.lunacore.luna_npc.combat.NpcDamageSources;
import net.lunacore.luna_npc.entity.LunaNpcEntities;
import net.lunacore.luna_npc.entity.NpcArrowEntity;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.entity.NpcProjectileEntity;
import net.lunacore.luna_npc.npc.NpcEquipment;

// Runs one advanced-combat action against its resolved targets and placement points. Every case is a small,
// self-contained executor; the shared damage/effect/projectile helpers keep attacks consistent with the
// simple combat pipeline. WAIT is handled by the engine's rule scheduler, not here.
final class CombatActionExecutor {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LunaNPC");
    private static final float CURVE_MAX_DISTANCE = 64.0F;
    private static final double DEFAULT_BLAST_RADIUS = 3.0;
    private static final int HAZARD_DAMAGE_INTERVAL = 10;

    private CombatActionExecutor() {
    }

    static void execute(NpcCombatAction action, CombatContext ctx, AdvancedCombatEngine engine) {
        ServerLevel level = ctx.level;
        NpcEntity npc = ctx.npc;
        List<LivingEntity> targets = engine.resolveTargets(action.target(), ctx.primaryTarget);
        float amount = CombatValueEvaluator.evaluate(action.amount(), ctx);
        switch (action.type()) {
            case USE_ATTACK -> useAttack(action, ctx, engine);
            case EXPLOSION -> explosion(action, ctx, engine, amount);
            case TELEGRAPH -> engine.addTelegraph(PlacementResolver.resolve(action.placement(), ctx, ctx.primaryTarget),
                    blastRadius(action.placement()), action.durationTicks(), action.stringArg());
            case SUMMON -> summon(action, ctx, engine);
            case TELEPORT -> teleport(action, ctx);
            case MOVE_TO -> moveTo(action, ctx);
            case HEAL -> npc.healLuna(amount);
            case BUFF_SELF -> {
                NpcCombatController.applyAttackEffects(level, npc, npc, action.effects());
                engine.markStatusApplied();
            }
            case APPLY_STATUS -> {
                for (LivingEntity target : targets) {
                    NpcCombatController.applyAttackEffects(level, npc, target, action.effects());
                }
            }
            case CLEANSE -> {
                for (LivingEntity target : targets) {
                    cleanse(target);
                }
            }
            case SHIELD -> engine.grantShield(amount, action.durationTicks());
            case INVULN -> engine.grantInvuln(action.durationTicks());
            case GUARD -> engine.grantGuard(amount / 100.0F, action.durationTicks());
            case UNTARGETABLE -> engine.grantUntargetable(action.durationTicks());
            case SWITCH_NAV_MODE -> npc.overrideNavMode(navMedium(action.stringArg()), action.durationTicks(),
                    Math.round(amount), action.flag());
            case KNOCKBACK -> shove(npc, targets, amount, true);
            case PULL -> shove(npc, targets, amount, false);
            case SWITCH_AGGRO -> engine.switchAggro(targets.isEmpty() ? null : targets.get(0));
            case SET_FLAG -> engine.setFlag(action.stringArg(),
                    amount != 0.0F ? amount : (action.flag() ? 1.0F : 0.0F));
            case CLEAR_FLAG -> engine.clearFlag(action.stringArg());
            case INC_COUNTER -> engine.incCounter(action.stringArg(), amount != 0.0F ? amount : 1.0F);
            case START_TIMER -> engine.startTimer(action.stringArg(), action.durationTicks());
            case ENRAGE -> engine.enrage(amount <= 0.0F ? 2.0F : amount, action.durationTicks());
            case BOSS_BAR -> npc.setBossBarText(action.stringArg());
            case PARTICLE -> particleBurst(level, PlacementResolver.resolve(action.placement(), ctx, ctx.primaryTarget),
                    action.stringArg());
            case SOUND -> playSound(level, npc, action.stringArg());
            case PLACE_HAZARD -> engine.addHazard(PlacementResolver.resolve(action.placement(), ctx, ctx.primaryTarget),
                    blastRadius(action.placement()), action.durationTicks(), amount, action.effects());
            case TAUNT -> {
                for (LivingEntity target : targets) {
                    if (target instanceof Mob mob) {
                        mob.setTarget(npc);
                    }
                }
            }
            case RUN_COMMAND -> runCommand(level, npc, action.stringArg());
            case CUSTOM_EVENT -> LunaNpcEvents.fireCombatEvent(npc, action.stringArg(), ctx.primaryTarget);
            case END_COMBAT -> engine.endCombat();
            case WAIT -> {
            }
        }
    }

    // ---- Attacks ----

    private static void useAttack(NpcCombatAction action, CombatContext ctx, AdvancedCombatEngine engine) {
        NpcCombatAttack attack = ctx.npc.combatSettings().advanced().attackByName(action.stringArg());
        if (attack == null) {
            return;
        }
        List<LivingEntity> targets = engine.resolveTargets(action.target(), ctx.primaryTarget);
        float mult = engine.enrageDamageMultiplier();
        NpcEntity npc = ctx.npc;
        switch (attack.kind()) {
            case MELEE -> {
                double reach = Math.max(1.0, attack.range());
                for (LivingEntity target : targets) {
                    if (target != npc && npc.distanceTo(target) <= reach) {
                        dealDirect(ctx, npc, target, attack.damage() * mult, attack.effects());
                    }
                }
                npc.swing(InteractionHand.MAIN_HAND, npc.getMainHandItem().getAttackAnimation(), true);
            }
            case AOE -> {
                double reach = Math.max(1.0, attack.range());
                for (Vec3 point : PlacementResolver.resolve(attack.placement(), ctx, ctx.primaryTarget)) {
                    damageAround(ctx, engine, point, reach, attack.damage() * mult, attack.effects());
                    ctx.level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y + 0.2, point.z, 1, 0, 0, 0, 0);
                }
            }
            case BEAM -> {
                for (LivingEntity target : targets) {
                    beam(ctx, npc, target, attack, mult);
                }
            }
            case PROJECTILE -> {
                for (LivingEntity target : targets) {
                    fireShots(ctx, attack, target, mult);
                }
            }
            case PATTERN -> {
                LivingEntity nominal = targets.isEmpty() ? ctx.primaryTarget : targets.get(0);
                for (Vec3 point : PlacementResolver.resolve(attack.placement(), ctx, nominal)) {
                    fireStraight(ctx, attack, point, nominal, mult);
                }
            }
        }
    }

    private static void fireShots(CombatContext ctx, NpcCombatAttack attack, LivingEntity target, float mult) {
        int shots = Math.max(1, attack.projectileCount());
        double speedPerTick = attack.speedBps() / 20.0;
        ItemStack displayed = NpcEquipment.resolve(attack.projectileItemId());
        boolean arrowStyle = displayed.isEmpty() || displayed.getItem() instanceof ArrowItem;
        for (int i = 0; i < shots; i++) {
            Projectile bullet = makeProjectile(ctx, attack, target, displayed, arrowStyle, speedPerTick, mult,
                    attack.homing(), attack.curve());
            if (bullet == null) {
                return;
            }
            bullet.setPos(ctx.npc.getX(), ctx.npc.getEyeY() - 0.1, ctx.npc.getZ());
            Vec3 direction = target.getBoundingBox().getCenter().subtract(bullet.position());
            bullet.shoot(direction.x, direction.y, direction.z, (float) speedPerTick, attack.spreadDegrees());
            ctx.level.addFreshEntity(bullet);
        }
    }

    private static void fireStraight(CombatContext ctx, NpcCombatAttack attack, Vec3 point, LivingEntity nominal,
                                     float mult) {
        if (nominal == null) {
            return;
        }
        ItemStack displayed = NpcEquipment.resolve(attack.projectileItemId());
        boolean arrowStyle = displayed.isEmpty() || displayed.getItem() instanceof ArrowItem;
        double speedPerTick = attack.speedBps() / 20.0;
        Projectile bullet = makeProjectile(ctx, attack, nominal, displayed, arrowStyle, speedPerTick, mult, false, false);
        if (bullet == null) {
            return;
        }
        bullet.setPos(ctx.npc.getX(), ctx.npc.getEyeY() - 0.1, ctx.npc.getZ());
        Vec3 direction = point.subtract(bullet.position());
        bullet.shoot(direction.x, direction.y, direction.z, (float) speedPerTick, attack.spreadDegrees());
        ctx.level.addFreshEntity(bullet);
    }

    private static Projectile makeProjectile(CombatContext ctx, NpcCombatAttack attack, LivingEntity target,
                                             ItemStack displayed, boolean arrowStyle, double speedPerTick, float mult,
                                             boolean homing, boolean curve) {
        float damage = attack.damage() * mult;
        if (arrowStyle) {
            NpcArrowEntity arrow = LunaNpcEntities.ARROW.create(ctx.level, EntitySpawnReason.TRIGGERED);
            if (arrow == null) {
                return null;
            }
            arrow.configure(damage, homing, curve, CURVE_MAX_DISTANCE, 0, target, displayed, speedPerTick);
            arrow.setAdvancedEffects(attack.effects());
            arrow.setOwner(ctx.npc);
            return arrow;
        }
        NpcProjectileEntity thrown = LunaNpcEntities.PROJECTILE.create(ctx.level, EntitySpawnReason.TRIGGERED);
        if (thrown == null) {
            return null;
        }
        thrown.setItem(displayed);
        thrown.configure(damage, homing, curve, CURVE_MAX_DISTANCE, 0, target, speedPerTick);
        thrown.setAdvancedEffects(attack.effects());
        thrown.setOwner(ctx.npc);
        return thrown;
    }

    // A hitscan beam: everything hostile within a short perpendicular reach of the line to the target takes
    // the hit at once, and a particle trail marks it.
    private static void beam(CombatContext ctx, NpcEntity npc, LivingEntity target, NpcCombatAttack attack, float mult) {
        Vec3 from = new Vec3(npc.getX(), npc.getEyeY(), npc.getZ());
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        if (length < 0.01) {
            return;
        }
        Vec3 unit = axis.scale(1.0 / length);
        double reach = Math.max(0.75, attack.range());
        for (LivingEntity candidate : ctx.engine.hostiles()) {
            Vec3 rel = candidate.getBoundingBox().getCenter().subtract(from);
            double along = rel.dot(unit);
            if (along < 0.0 || along > length) {
                continue;
            }
            if (rel.subtract(unit.scale(along)).length() <= reach) {
                dealDirect(ctx, npc, candidate, attack.damage() * mult, attack.effects());
            }
        }
        int steps = Math.max(4, (int) (length * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.add(unit.scale(length * i / steps));
            ctx.level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    // ---- Radial helpers ----

    private static void explosion(NpcCombatAction action, CombatContext ctx, AdvancedCombatEngine engine, float amount) {
        double radius = blastRadius(action.placement());
        float damage = amount * engine.enrageDamageMultiplier();
        for (Vec3 point : PlacementResolver.resolve(action.placement(), ctx, ctx.primaryTarget)) {
            for (LivingEntity target : engine.hostiles()) {
                double distSq = target.position().distanceToSqr(point);
                if (distSq > radius * radius) {
                    continue;
                }
                if (dealDirect(ctx, ctx.npc, target, damage, action.effects())) {
                    Vec3 outward = target.position().subtract(point);
                    Vec3 direction = new Vec3(outward.x, 0.0, outward.z).normalize();
                    double strength = (1.0 - Math.sqrt(distSq) / radius) * 1.2;
                    target.push(direction.x * strength, 0.4, direction.z * strength);
                    target.syncVelocity = true;
                    if (action.flag()) {
                        target.igniteForTicks(60);
                    }
                }
            }
            ctx.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        playSound(ctx.level, ctx.npc, "minecraft:entity.generic.explode");
    }

    private static void damageAround(CombatContext ctx, AdvancedCombatEngine engine, Vec3 centre, double radius,
                                     float damage, List<NpcAttackEffect> effects) {
        for (LivingEntity target : engine.hostiles()) {
            if (target.position().distanceToSqr(centre) <= radius * radius) {
                dealDirect(ctx, ctx.npc, target, damage, effects);
            }
        }
    }

    // Called by the engine each tick for a lingering hazard zone: damages hostiles standing in it on the
    // interval and keeps its warning particles alive.
    static void applyHazardTick(AdvancedCombatEngine engine, List<Vec3> points, double radius, float damage,
                                List<NpcAttackEffect> effects) {
        ServerLevel level = engine.level();
        NpcEntity npc = engine.npc();
        boolean damageTick = npc.tickCount % HAZARD_DAMAGE_INTERVAL == 0;
        for (Vec3 centre : points) {
            if (damageTick) {
                for (LivingEntity target : engine.hostiles()) {
                    if (target.position().distanceToSqr(centre) <= radius * radius
                            && target.hurtServer(level, NpcDamageSources.npcAttack(level, npc), damage)) {
                        NpcCombatController.applyAttackEffects(level, npc, target, effects);
                    }
                }
            }
            level.sendParticles(ParticleTypes.FLAME, centre.x, centre.y + 0.1, centre.z, 3, radius * 0.4, 0.1,
                    radius * 0.4, 0.01);
        }
    }

    // Called by the engine each tick for an active telegraph: draws a warning ring at each marked point.
    static void spawnMarkerRing(ServerLevel level, List<Vec3> points, double radius, String particleId) {
        ParticleOptions options = particleOptions(particleId);
        int steps = 16;
        for (Vec3 centre : points) {
            for (int i = 0; i < steps; i++) {
                double angle = i * Math.PI * 2.0 / steps;
                level.sendParticles(options, centre.x + Math.cos(angle) * radius, centre.y + 0.1,
                        centre.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
            }
        }
    }

    // ---- Movement / summons ----

    private static void teleport(NpcCombatAction action, CombatContext ctx) {
        List<Vec3> points = PlacementResolver.resolve(action.placement(), ctx, ctx.primaryTarget);
        if (!points.isEmpty()) {
            Vec3 point = points.get(0);
            ctx.npc.teleport(point.x, point.y, point.z);
        }
    }

    private static void moveTo(NpcCombatAction action, CombatContext ctx) {
        List<Vec3> points = PlacementResolver.resolve(action.placement(), ctx, ctx.primaryTarget);
        if (!points.isEmpty()) {
            ctx.npc.navigation().moveTo(points.get(0), true, true);
        }
    }

    private static void summon(NpcCombatAction action, CombatContext ctx, AdvancedCombatEngine engine) {
        for (Vec3 point : PlacementResolver.resolve(action.placement(), ctx, ctx.primaryTarget)) {
            Entity spawned = summonOne(ctx.level, action.stringArg(), point);
            if (spawned != null) {
                engine.addSummon(spawned.getUUID());
                warnAboutSummon(ctx.npc, engine, action.stringArg(), spawned);
            }
        }
    }

    // At spawn time, log a one-off console advisory (the API-facing warning) when a summoned add would
    // misbehave: a LunaNPC that persists or respawns piles up or won't die for good, and any add that isn't
    // allied with its summoner may ignore or even turn on it.
    private static void warnAboutSummon(NpcEntity boss, AdvancedCombatEngine engine, String id, Entity spawned) {
        if (!engine.shouldWarnSummon(id)) {
            return;
        }
        List<String> issues = new ArrayList<>();
        if (id.startsWith("luna:")) {
            try {
                NpcDefinition definition = LunaNpcApi.npcs(engine.level().getServer())
                        .byLunaId(Long.parseLong(id.substring("luna:".length()))).orElse(null);
                if (definition != null && definition.spawn().placedPersistent()) {
                    issues.add("persistence is on (its adds never despawn)");
                }
                if (definition != null && definition.spawn().respawnEnabled()) {
                    issues.add("respawn is on (killed adds return)");
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (spawned instanceof net.minecraft.world.entity.LivingEntity living
                && net.lunacore.luna_npc.alliance.NpcAllianceManager.get(engine.level().getServer())
                        .classify(boss, living) != net.lunacore.luna_npc.api.NpcAlliance.Relation.MATE) {
            issues.add("it is not allied with its summoner");
        }
        if (!issues.isEmpty()) {
            LOGGER.warn("Advanced-combat NPC (lunaId {}) summons '{}' — {}.", boss.lunaId(), id,
                    String.join("; ", issues));
        }
    }

    private static Entity summonOne(ServerLevel level, String id, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        if (id.startsWith("luna:")) {
            try {
                long lunaId = Long.parseLong(id.substring("luna:".length()));
                NpcDefinition definition = LunaNpcApi.npcs(level.getServer()).byLunaId(lunaId).orElse(null);
                if (definition == null) {
                    return null;
                }
                return LunaNpcApi.spawn(level, definition, pos) instanceof NpcEntity npc ? npc : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(parsed).orElse(null);
        if (type == null) {
            return null;
        }
        Entity entity = type.spawn(level, pos, EntitySpawnReason.MOB_SUMMONED);
        if (entity != null) {
            entity.snapTo(point.x, point.y, point.z, entity.getYRot(), entity.getXRot());
        }
        return entity;
    }

    // ---- Small shared helpers ----

    private static boolean dealDirect(CombatContext ctx, NpcEntity npc, LivingEntity target, float damage,
                                      List<NpcAttackEffect> effects) {
        DamageSource source = NpcDamageSources.npcAttack(ctx.level, npc);
        if (!target.hurtServer(ctx.level, source, damage)) {
            return false;
        }
        NpcCombatController.applyAttackEffects(ctx.level, npc, target, effects);
        npc.onCombatDamageDealt(target);
        return true;
    }

    private static void shove(NpcEntity npc, List<LivingEntity> targets, float strength, boolean away) {
        for (LivingEntity target : targets) {
            if (target == npc) {
                continue;
            }
            Vec3 base = away
                    ? target.position().subtract(npc.position())
                    : npc.position().subtract(target.position());
            Vec3 direction = new Vec3(base.x, 0.0, base.z).normalize();
            target.push(direction.x * strength * 0.5, 0.3, direction.z * strength * 0.5);
            target.syncVelocity = true;
        }
    }

    private static void cleanse(LivingEntity target) {
        List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> harmful = new java.util.ArrayList<>();
        for (MobEffectInstance instance : target.getActiveEffects()) {
            if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                harmful.add(instance.getEffect());
            }
        }
        for (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect : harmful) {
            target.removeEffect(effect);
        }
    }

    private static void particleBurst(ServerLevel level, List<Vec3> points, String particleId) {
        ParticleOptions options = particleOptions(particleId);
        for (Vec3 point : points) {
            level.sendParticles(options, point.x, point.y + 0.2, point.z, 8, 0.3, 0.3, 0.3, 0.02);
        }
    }

    private static void playSound(ServerLevel level, NpcEntity npc, String soundId) {
        Identifier parsed = Identifier.tryParse(soundId);
        if (parsed == null) {
            return;
        }
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getOptional(parsed).orElse(null);
        if (event != null) {
            level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), event, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    private static void runCommand(ServerLevel level, NpcEntity npc, String command) {
        if (command.isBlank()) {
            return;
        }
        MinecraftServer server = level.getServer();
        CommandSourceStack source = server.createCommandSourceStack()
                .withEntity(npc)
                .withPosition(npc.position())
                .withPermission(LevelBasedPermissionSet.GAMEMASTER)
                .withSuppressedOutput()
                .withLevel(level);
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static ParticleOptions particleOptions(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed != null && BuiltInRegistries.PARTICLE_TYPE.getOptional(parsed).orElse(null)
                instanceof ParticleOptions options) {
            return options;
        }
        return ParticleTypes.FLAME;
    }

    private static String navMedium(String navMode) {
        return switch (navMode) {
            case "fly", "air" -> "air";
            case "water" -> "water";
            default -> "ground";
        };
    }

    private static double blastRadius(NpcCombatPlacement placement) {
        return placement.maxDistance() > 0.0F ? placement.maxDistance() : DEFAULT_BLAST_RADIUS;
    }
}
