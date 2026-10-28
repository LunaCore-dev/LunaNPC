package net.lunacore.luna_npc.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.api.NpcTraitsSettings;
import net.lunacore.luna_npc.npc.NpcValidation;
import net.lunacore.luna_npc.api.NpcWanderSettings;

// Feeds the navigation with random wander targets per the NPC's wander settings. Stroll rests
// for a random interval between walks; restless walks all the time, chaining random points
// without a break (no interval). When an avoidance condition (avoid-light, avoid-darkness or
// burn-in-daylight) is active and the emergency-seek trait is on, a threatened NPC drops
// everything and hunts the nearest safe spot within range, bypassing the wander interval and
// even overriding static; once safe it only ever wanders to safe goals so it stays there. With
// emergency-seek off, it just prefers safe goals on its normal wander cadence. Scripted moves
// (API, commands) pause everything.
public class WanderController {

    private static final int NO_COOLDOWN = -1;
    private static final int FAILED_PLAN_RETRY_TICKS = 20;
    private static final int SEEK_ATTEMPT_INTERVAL_TICKS = 20;
    // Columns checked per seek attempt; large ranges finish over several attempts instead of
    // stalling one tick with a full scan.
    private static final int SEEK_COLUMN_BUDGET = 4000;
    private static final int SEEK_SNAP_UP = 3;
    private static final int SEEK_SNAP_DOWN = 4;
    private static final int SEEK_MAX_FALL = 3;
    // Emergency searches reach at least this far even when the wander radius is tiny or static.
    private static final int MIN_SEEK_RANGE = 8;
    // Nothing safe in range: the condition is tolerated for this long before rescanning.
    private static final int TOLERATED_RESCAN_TICKS = 100;
    private static final int SAFE_WANDER_ATTEMPTS = 12;
    // Wandering (and the leave-area tether) stays out for this long after the last combat tick, so the
    // advanced engine's momentary target drops — a boss whose target steps just outside the engagement
    // radius — can't let a stray route slip in and make the NPC walk off mid-fight.
    private static final int COMBAT_COOLDOWN_TICKS = 100;

    private final NpcEntity entity;
    private int cooldownTicks = NO_COOLDOWN;
    private int combatCooldown;
    // Expanding-ring scan cursor; survives between seek attempts so the scan resumes instead
    // of restarting.
    private int seekRing;
    private int toleratedTicks;

    public WanderController(NpcEntity entity) {
        this.entity = entity;
    }

    public void tick() {
        NpcWanderSettings wander = entity.movementSettings().wander();
        NpcNavigation navigation = entity.navigation();
        // Combat, fear and a working job (farm, patrol, bodyguard) own the movement; wandering stays out —
        // and keeps out through the combat cooldown so a brief target drop mid-fight never lets a stray
        // wander or leave-area route slip in and fight the combat engine for control.
        if (entity.combatController().isFighting() || entity.combatController().isFleeing()
                || entity.jobOwnsMovement()) {
            combatCooldown = COMBAT_COOLDOWN_TICKS;
            cooldownTicks = NO_COOLDOWN;
            return;
        }
        if (combatCooldown > 0) {
            combatCooldown--;
            cooldownTicks = NO_COOLDOWN;
            return;
        }
        if (navigation.isNavigating() && navigation.isScripted()) {
            cooldownTicks = NO_COOLDOWN;
            return;
        }
        if (entity.isMovementBlockedByLiquid() || tickEmergencySeek(navigation, wander)) {
            cooldownTicks = NO_COOLDOWN;
            return;
        }
        // Leave-area tether: an NPC that has strayed outside its spawn area walks back before any
        // normal wandering — even a static one, which otherwise never moves. Skipped when the creator
        // allows leaving the area.
        if (!entity.leaveAreaAllowed() && !entity.isInHomeArea(entity.blockPosition())) {
            if (!navigation.isNavigating()) {
                navigation.moveTo(entity.homeAnchor(), false, false);
            }
            cooldownTicks = NO_COOLDOWN;
            return;
        }
        if (NpcWanderSettings.STATIC.equals(wander.behavior()) || navigation.isNavigating()) {
            cooldownTicks = NO_COOLDOWN;
            return;
        }
        // Restless walks all the time: the next random point starts the moment the previous
        // walk ends. Only a failed plan gets a short retry delay so planning is not spammed.
        if (NpcWanderSettings.RESTLESS.equals(wander.behavior())) {
            if (cooldownTicks > 0) {
                cooldownTicks--;
                return;
            }
            if (!wanderToRandomPoint(wander)) {
                cooldownTicks = FAILED_PLAN_RETRY_TICKS;
            }
            return;
        }
        // Stroll rests for a random interval between walks.
        if (cooldownTicks == NO_COOLDOWN) {
            cooldownTicks = nextDelayTicks(wander);
        }
        if (--cooldownTicks <= 0) {
            wanderToRandomPoint(wander);
            cooldownTicks = NO_COOLDOWN;
        }
    }

    // Standing somewhere unsafe (forbidden light or burning in the open) sends the NPC to the
    // nearest safe standable spot within range, bypassing the wander interval. Only when the
    // whole range holds nothing safe does the NPC tolerate the condition for a while and behave
    // normally, rescanning periodically. Returns true while actively seeking, which suspends
    // normal wandering. Disabled entirely when the emergency-seek trait is off.
    private boolean tickEmergencySeek(NpcNavigation navigation, NpcWanderSettings wander) {
        NpcTraitsSettings traits = entity.traitsSettings();
        if (!traits.hasAvoidanceCondition() || !traits.emergencySeek()) {
            seekRing = 0;
            toleratedTicks = 0;
            return false;
        }
        if (isSafeSpot(entity.blockPosition())) {
            seekRing = 0;
            toleratedTicks = 0;
            return false;
        }
        if (toleratedTicks > 0) {
            toleratedTicks--;
            return false;
        }
        if (navigation.isNavigating() || entity.tickCount % SEEK_ATTEMPT_INTERVAL_TICKS != 0) {
            return true;
        }
        int range = Math.max(wander.radius(), MIN_SEEK_RANGE);
        BlockPos safe = scanForSafeSpot(traits, range);
        if (safe != null) {
            seekRing = 0;
            navigation.moveTo(Vec3.atBottomCenterOf(safe), true, false);
        } else if (seekRing > range) {
            seekRing = 0;
            toleratedTicks = TOLERATED_RESCAN_TICKS;
            return false;
        }
        return true;
    }

    // Scans Chebyshev rings outwards and returns the first safe standable column — which is also
    // (roughly) the nearest. Standability (with the light window) comes from PathWorldView, the
    // same rules pathfinding uses, and the burn check rules out sky-exposed daylight spots. Null
    // means the budget ran out or the range is exhausted; the ring cursor tells the two apart.
    private BlockPos scanForSafeSpot(NpcTraitsSettings traits, int range) {
        int maxLight = traits.avoidLight() ? traits.avoidLightLevel() : NpcValidation.MAX_LIGHT_LEVEL;
        int minLight = traits.avoidNight() ? traits.avoidNightLevel() : NpcValidation.MIN_LIGHT_LEVEL;
        PathWorldView world = new PathWorldView(entity.level(),
                entity.movementSettings().avoidedBlocks(), SEEK_MAX_FALL, maxLight, minLight,
                traits.avoidWater(), traits.avoidLava(),
                traits.canSwim() && !traits.avoidWater(),
                Math.max(1, Mth.ceil(entity.getBbHeight())),
                PathWorldView.footprintCells(entity.getBbWidth()),
                traits.canTraverseDoors());
        BlockPos origin = entity.blockPosition();
        int budget = SEEK_COLUMN_BUDGET;
        while (seekRing <= range && budget > 0) {
            int ring = seekRing;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    budget--;
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    int y = world.snapToGround(x, origin.getY(), z, SEEK_SNAP_UP, SEEK_SNAP_DOWN);
                    // Standability no longer filters light, so the compliance is checked here.
                    if (y != PathWorldView.NO_STEP) {
                        BlockPos spot = new BlockPos(x, y, z);
                        if (isLightCompliant(spot) && !isBurningExposed(spot)) {
                            return spot;
                        }
                    }
                }
            }
            seekRing++;
        }
        return null;
    }

    private boolean isSafeSpot(BlockPos pos) {
        return isLightCompliant(pos) && !isBurningExposed(pos);
    }

    private boolean isLightCompliant(BlockPos pos) {
        NpcTraitsSettings traits = entity.traitsSettings();
        int light = entity.level().getMaxLocalRawBrightness(pos);
        return (!traits.avoidLight() || light <= traits.avoidLightLevel())
                && (!traits.avoidNight() || light >= traits.avoidNightLevel());
    }

    // A burn-in-daylight NPC is only unsafe where it would actually catch fire: open sky, bright
    // day, no rain. Shade, night and rain are all fine, exactly like the daylight burn itself.
    private boolean isBurningExposed(BlockPos pos) {
        NpcTraitsSettings traits = entity.traitsSettings();
        if (!traits.burnInDaylight()) {
            return false;
        }
        return entity.level().isBrightOutside() && entity.level().canSeeSky(pos)
                && !entity.level().isRainingAt(pos);
    }

    private boolean wanderToRandomPoint(NpcWanderSettings wander) {
        NpcTraitsSettings traits = entity.traitsSettings();
        boolean keepSafe = traits.hasAvoidanceCondition() && toleratedTicks == 0
                && isSafeSpot(entity.blockPosition());
        boolean tethered = !entity.leaveAreaAllowed();
        // Ground NPCs keep their wander goals on dry land. The +4 water cost already keeps them off
        // water mid-route, but a goal sitting IN water still marched them straight in (the destination
        // itself is water, so every route to it ends wet) — that is how they used to stroll into ponds
        // and oceans. Combat is untouched: it still wades in to reach a target when avoid-water is off.
        boolean dryLand = "ground".equals(entity.movementSettings().movementType());
        if (!keepSafe && !tethered && !dryLand) {
            return entity.navigation().moveTo(randomPoint(wander), false, false);
        }
        // Filtered wander: keep-safe only accepts safe goals (the light-bounded pathfinding then
        // keeps the walk itself in tolerance too), the tether only accepts goals inside the home
        // area, dry-land rejects goals standing in water. A candidate must clear whichever filters are
        // active; when none fits this round the NPC just stays put.
        PathWorldView world = dryLand ? wanderWorldView() : null;
        for (int attempt = 0; attempt < SAFE_WANDER_ATTEMPTS; attempt++) {
            Vec3 candidate = randomPoint(wander);
            BlockPos block = BlockPos.containing(candidate);
            if (keepSafe && !isSafeSpot(block)) {
                continue;
            }
            if (tethered && !entity.isInHomeArea(block)) {
                continue;
            }
            if (dryLand && isWaterGoal(world, block)) {
                continue;
            }
            return entity.navigation().moveTo(candidate, false, false);
        }
        return false;
    }

    // A wander goal is "wet" when the ground the NPC would stand on there is water — a shallow cell it
    // would wade in, or a surface a swimmer would stand on. Ground NPCs skip those so they never pick a
    // water destination. Ground nothing finds in snap range is left to the planner to accept or reject.
    private boolean isWaterGoal(PathWorldView world, BlockPos block) {
        int y = world.snapToGround(block.getX(), block.getY(), block.getZ(), SEEK_SNAP_UP, SEEK_SNAP_DOWN);
        if (y == PathWorldView.NO_STEP) {
            return false;
        }
        BlockPos feet = new BlockPos(block.getX(), y, block.getZ());
        return entity.level().getFluidState(feet).is(FluidTags.WATER)
                || entity.level().getFluidState(feet.below()).is(FluidTags.WATER);
    }

    private PathWorldView wanderWorldView() {
        NpcTraitsSettings traits = entity.traitsSettings();
        return new PathWorldView(entity.level(), entity.movementSettings().avoidedBlocks(), SEEK_MAX_FALL,
                NpcValidation.MAX_LIGHT_LEVEL, NpcValidation.MIN_LIGHT_LEVEL,
                traits.avoidWater(), traits.avoidLava(), traits.canSwim() && !traits.avoidWater(),
                Math.max(1, Mth.ceil(entity.getBbHeight())),
                PathWorldView.footprintCells(entity.getBbWidth()),
                traits.canTraverseDoors());
    }

    private Vec3 randomPoint(NpcWanderSettings wander) {
        double angle = entity.getRandom().nextDouble() * Math.PI * 2.0;
        double minDistance = Math.min(wander.minGoalDistance(), wander.radius());
        // Interpolating the squared bounds and taking the root spreads the points evenly over
        // the ring area instead of clustering them at the inner edge.
        double distance = Math.sqrt(Mth.lerp(entity.getRandom().nextDouble(),
                minDistance * minDistance, (double) wander.radius() * wander.radius()));
        return entity.position().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
    }

    private int nextDelayTicks(NpcWanderSettings wander) {
        int span = wander.maxSeconds() - wander.minSeconds();
        return (wander.minSeconds() + (span <= 0 ? 0 : entity.getRandom().nextInt(span + 1))) * 20;
    }
}
