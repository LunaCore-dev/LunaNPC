package net.lunacore.luna_npc.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.lunacore.luna_npc.LunaNpcConfig;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.api.NpcAlliance;
import net.lunacore.luna_npc.alliance.NpcAllianceManager;
import net.lunacore.luna_npc.entity.LunaNpcEntities;
import net.lunacore.luna_npc.entity.NpcArrowEntity;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.entity.NpcProjectileEntity;
import net.lunacore.luna_npc.api.NpcAttackEffect;
import net.lunacore.luna_npc.api.NpcAttackRange;
import net.lunacore.luna_npc.api.NpcAttackSettings;
import net.lunacore.luna_npc.api.NpcCombatMode;
import net.lunacore.luna_npc.api.NpcCombatSettings;
import net.lunacore.luna_npc.combat.advanced.AdvancedCombatEngine;
import net.lunacore.luna_npc.npc.NpcEquipment;
import net.lunacore.luna_npc.npc.NpcValidation;
import net.lunacore.luna_npc.api.NpcProjectileSettings;
import net.lunacore.luna_npc.api.NpcRangeRule;
import net.lunacore.luna_npc.api.NpcRangeSwitch;
import net.lunacore.luna_npc.api.NpcReactionSettings;

// Retaliation combat: the NPC fights back against whatever attacked it, within its aggro range.
// Ranged NPCs kite at their distance preference — approaching when too far, backing off when the
// target crowds them, and switching to the blade once the target dives under half the preferred
// distance. With real gear stats enabled, the equipped weapons' damage and enchantments stack
// onto the configured values, exactly like they would for a player. Target picking beyond
// retaliation (aggro on sight, factions) is a future system on top of this.
public class NpcCombatController {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int REPATH_INTERVAL_TICKS = 10;
    // The closest a melee NPC positions to its target: its configured close reach, floored only so it never
    // tries to stand inside the target. Low enough to honour short reaches (a 1.0 reach now means ~1.0).
    private static final double MIN_MELEE_REACH = 0.5;
    private static final float BASE_ENCHANT_BONUS = 0.5F;
    // Vanilla player base attack damage, baked into the attack-damage number shown on item tooltips.
    private static final float PLAYER_BASE_ATTACK_DAMAGE = 1.0F;
    private static final float DISTANCE_BAND = 2.0F;
    private static final float MAX_SPREAD_DEGREES = 20.0F;
    // Half of the ~6-tick melee swing: a landed blow is delayed by this so the hit lands at the swing's
    // peak (its mid-point). Pulled in to half the attack interval when the NPC swings faster than that.
    private static final int MELEE_SWING_PEAK_TICKS = 3;
    // Vanilla drops a hit landing within this many ticks of the previous one (unless it is stronger).
    private static final int VANILLA_HIT_COOLDOWN_TICKS = 10;
    // Range-switch hysteresis: the auto pick needs this much extra distance to go back to long range, and
    // any switch is held this long, so a target hovering on the boundary can't flip weapon and pose.
    private static final double RANGE_SWITCH_MARGIN = 2.0;
    private static final int RANGE_SWITCH_HOLD_TICKS = 20;
    // How recently the NPC must have landed a hit on its target to count as mid-duel and ignore a new attacker.
    private static final int DUEL_HIT_MEMORY_TICKS = 60;
    // A ranged NPC holds fire while backing away, but only for this long — a cornered or
    // equally-fast target would otherwise leave it a sitting duck that never shoots.
    private static final int BACK_AWAY_GRACE_TICKS = 40;

    // Cap on remembered give-up grudges; a mob farm could otherwise grow this without bound.
    private static final int MAX_GIVE_UP_MEMORY = 16;
    private static final int MIN_GRUDGE_TICKS = 200;
    // How long the NPC remembers a recent attacker so it can resume that fight after a diversion (even
    // when its temperament never proactively hunts), and the cap on that memory.
    private static final int RECENT_ATTACKER_MEMORY_TICKS = 200;
    private static final int MAX_RECENT_ATTACKERS = 8;
    // Target lock: once engaged, a fresh hit from someone else only steals the target after the current
    // one has been out of attack range (fled, or blocked by a crowd) for this long. Stops the NPC from
    // spinning to face every attacker in a swarm, while still turning on whoever pins it unreachable.
    private static final int TARGET_LOCK_TICKS = 40;
    // A re-evaluation only steals the lock for a clearly better target, not a marginal one, so two
    // similar enemies can't make the NPC dither between them.
    private static final int TARGET_SWITCH_MARGIN = 20;
    // Target scoring (used to rank everyone in aggro range). An enemy currently dealing damage dominates;
    // among the rest, closer and in line of sight win, and merely being in range still counts for a
    // little. All tunable — none of it overrides the NPC's temperament, which gates who is eligible.
    private static final double SCORE_BASE = 10.0;
    private static final double SCORE_PROXIMITY = 1.0;
    private static final double SCORE_LINE_OF_SIGHT = 6.0;
    private static final double SCORE_ATTACKER = 100.0;
    private static final double SCORE_ATTACKER_RECENCY = 0.4;
    private static final double SCORE_DEFEND_MATE = 40.0;

    // A water NPC strikes only from beside its prey at the same height, never under/over/inside it:
    // horizontally clear of the target's column but within reach, and vertically level within the slack.
    private static final double FLANK_MIN_HORIZONTAL_DISTANCE = 0.9;
    private static final double FLANK_VERTICAL_SLACK = 0.5;
    // Hysteresis so prey bobbing across the band edge can't jitter engage/disengage: keep the fight
    // through a wider band than acquisition, and only drop after a full second outside it.
    private static final double KEEP_ENGAGED_DEPTH_MARGIN = 1.0;
    private static final int OUT_OF_DEPTH_GRACE_TICKS = 20;

    private final NpcEntity entity;
    private final AdvancedCombatEngine advancedEngine;
    private LivingEntity target;
    private int meleeCooldownTicks;
    private int rangedCooldownTicks;
    private int repathCooldownTicks;
    // Consecutive ticks spent backing away; once it exceeds the grace the NPC shoots anyway.
    private int backAwayTicks;
    private int rangeHoldTicks;
    private float pendingChargeBonus;
    private int lastLandedHitTick = Integer.MIN_VALUE / 2;
    // Ticks since the NPC last landed damage on the current target; drives the give-up timeout.
    private int ticksSinceDamage;
    // Ticks the current target has been outside attack range; drives the target lock (see engageIfArmed).
    private int targetOutOfReachTicks;
    private int outOfDepthBandTicks;
    // A melee blow in flight: the swing has played and the damage lands when the countdown reaches zero
    // (the swing's peak). -1 = nothing pending. Cleared if the target is dropped mid-swing.
    private int pendingMeleeTicks = -1;
    private NpcAttackSettings pendingMelee;
    // The range the range-switch rules last chose; null when the feature is off or the choice is due for
    // a fresh evaluation (a new engagement, or the staggered interval for a world-scanning rule).
    private NpcAttackRange forcedRange;
    // Targets the NPC gave up on: value = whether line of sight has been lost since. It stays
    // suppressed until sight is lost and regained, or it lands a fresh hit on the NPC.
    private final java.util.Map<java.util.UUID, Boolean> gaveUp = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> gaveUpAtTick = new java.util.HashMap<>();
    // Enemies that hit the NPC recently (UUID -> tickCount). Lets a defensive/passive NPC resume an
    // attacker it was diverted from, instead of forgetting it the moment the second fight ends.
    private final java.util.Map<java.util.UUID, Integer> recentAttackers = new java.util.HashMap<>();
    // Fear reaction: run from this entity, keeping fleeDistance, for fleeTicks.
    private LivingEntity fleeFrom;
    private int fleeTicks;
    private int fleeDistance;

    public NpcCombatController(NpcEntity entity) {
        this.entity = entity;
        this.advancedEngine = new AdvancedCombatEngine(entity, this);
    }

    public AdvancedCombatEngine advancedEngine() {
        return advancedEngine;
    }

    // Reaction to being hit, per the NPC's temperament. A protected ally is never turned on. A
    // fearful NPC runs from any other attacker; everyone else (aggressive, defensive, passive)
    // fights back.
    public void onAttacked(LivingEntity attacker) {
        if (attacker == entity) {
            return;
        }
        // A fresh hit lifts any give-up grudge — the attacker earns the NPC's attention again.
        gaveUp.remove(attacker.getUUID());
        boolean protectedAlly = isProtectedAlly(attacker);
        NpcReactionSettings reaction = entity.traitsSettings().reaction();
        if (LunaNpcConfig.combatDebug() && entity.level() instanceof ServerLevel debugLevel) {
            NpcCombatSettings combat = entity.combatSettings();
            LOGGER.info("[combat] {} (luna {}) hit by {} — relation={} reaction={} protectedAlly={} armed={} canEngage={} target={}",
                    entity.getName().getString(), entity.lunaId(), attacker.getName().getString(),
                    NpcAllianceManager.get(debugLevel.getServer()).classify(entity, attacker),
                    reaction.mode(), protectedAlly,
                    combat.melee().enabled() || combat.ranged().enabled(), canEngage(attacker),
                    target != null ? target.getName().getString() : "none");
        }
        // A bodyguard never turns on the one it guards, even after a stray hit from them.
        if (protectedAlly || entity.bodyguardController().isGuarding(attacker)) {
            return;
        }
        if (reaction.flees()) {
            startFleeing(attacker, reaction.fleeSeconds(), reaction.fleeDistance());
        } else {
            rememberRecentAttacker(attacker);
            engageIfArmed(attacker);
        }
    }

    private void rememberRecentAttacker(LivingEntity attacker) {
        if (recentAttackers.size() >= MAX_RECENT_ATTACKERS && !recentAttackers.containsKey(attacker.getUUID())) {
            pruneRecentAttackers();
            if (recentAttackers.size() >= MAX_RECENT_ATTACKERS) {
                recentAttackers.clear();
            }
        }
        recentAttackers.put(attacker.getUUID(), entity.tickCount);
    }

    private void pruneRecentAttackers() {
        recentAttackers.values().removeIf(tick -> entity.tickCount - tick > RECENT_ATTACKER_MEMORY_TICKS);
    }

    // An ally the NPC refuses to fight back against: a member of an allied faction (always), or a
    // member of its OWN faction while friendly fire is off — the alliance-wide rule decides, and
    // defers to this NPC's own toggle only when it is set to "per NPC".
    private boolean isProtectedAlly(LivingEntity other) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        NpcAllianceManager alliances = NpcAllianceManager.get(level.getServer());
        if (alliances.classify(entity, other) == NpcAlliance.Relation.MATE) {
            return true;
        }
        // Same-faction friendly fire: shielded if any faction they share blocks members fighting.
        java.util.Set<String> mine = alliances.alliancesOf(entity);
        boolean friendlyFire = entity.combatSettings().friendlyFire();
        for (String shared : alliances.alliancesOf(other)) {
            if (mine.contains(shared) && !alliances.friendlyFireAllowed(shared, friendlyFire)) {
                return true;
            }
        }
        return false;
    }

    private void engageIfArmed(LivingEntity attacker) {
        NpcCombatSettings combat = entity.combatSettings();
        if (!((combat.melee().enabled() || combat.ranged().enabled()) && canEngage(attacker))
                || entity.bodyguardController().isRetreating()) {
            return;
        }
        // Stay on the current target only mid-duel — in attack range and landing hits on it: the new
        // attacker is remembered (recentAttackers) but does not yank the NPC out of the fight. An NPC that
        // is chasing, or stuck beside a target it can't actually hit, turns on whoever hits it right away.
        if (target != null && target != attacker && target.isAlive() && !target.isRemoved()
                && targetOutOfReachTicks == 0 && entity.tickCount - lastLandedHitTick <= DUEL_HIT_MEMORY_TICKS) {
            return;
        }
        engage(attacker);
    }

    // A tethered NPC (not allowed to leave its area in combat) only fights targets inside that area —
    // the zone rule beats aggro range, so it never locks onto or chases something it could not reach
    // without leaving, and it won't jitter at the border trying.
    private boolean canEngage(LivingEntity candidate) {
        if (!inReachableMedium(candidate) || isIgnoredPlayer(candidate)
                || entity.bodyguardController().isGuarding(candidate)) {
            return false;
        }
        // A depth-holding swimmer only picks fights inside its depth band (preferred ± tolerance).
        if (isWaterMover() && !entity.navigation().depthWithinHoldBand(candidate.getY(), 0.0)) {
            return false;
        }
        return entity.leaveAreaInCombatAllowed() || entity.isInHomeArea(candidate.blockPosition());
    }

    // A water NPC lives in the water and cannot follow anything onto land: it only fights a target
    // that is itself in water, so it never locks onto — nor swims to the shore reaching for — someone
    // standing on the beach. Like a real fish, dry land is simply out of its world. Ground and air
    // NPCs have no such restriction.
    private boolean inReachableMedium(LivingEntity candidate) {
        return !isWaterMover() || candidate.isInWater();
    }

    // Players in creative or spectator are out of the fight entirely — the NPC never targets or keeps
    // them, matching how vanilla mobs leave a creative player alone. Survival and adventure are fair game.
    // Shared with the advanced-combat engine so both modes filter players identically.
    public static boolean isIgnoredPlayer(LivingEntity candidate) {
        return candidate instanceof net.minecraft.world.entity.player.Player player
                && (player.isCreative() || player.isSpectator());
    }

    // Whether the NPC could land its attack on the target right now: within melee reach (or beside it for
    // a swimmer), or within ranged aggro range with a clear shot. Drives the target lock's reach timer.
    private boolean isTargetInAttackRange(NpcCombatSettings combat, double distance) {
        if (combat.ranged().enabled() && distance <= Math.max(1.0F, combat.aggroRange())
                && (!combat.requireLineOfSight() || entity.hasLineOfSight(target))) {
            return true;
        }
        if (combat.melee().enabled()) {
            return inMeleeStrikePosition(combat.melee(), distance);
        }
        return false;
    }

    private boolean isWaterMover() {
        return "water".equals(entity.movementSettings().movementType());
    }

    // Fearful NPCs run from whatever hit them, keeping their flee distance, for the fear duration;
    // a new hit refreshes the timer. Fleeing works even for combat-less NPCs.
    public void startFleeing(LivingEntity from, int seconds, int distance) {
        fleeFrom = from;
        fleeTicks = Math.max(1, seconds) * 20;
        fleeDistance = distance;
        target = null;
        stopChase();
    }

    public boolean isFleeing() {
        return fleeTicks > 0;
    }

    private boolean tickFlee() {
        if (fleeTicks <= 0) {
            return false;
        }
        if (fleeFrom == null || fleeFrom.isRemoved() || fleeFrom.isDeadOrDying()
                || fleeFrom.level() != entity.level()) {
            fleeTicks = 0;
            fleeFrom = null;
            return false;
        }
        fleeTicks--;
        target = null;
        double distance = entity.distanceTo(fleeFrom);
        if (distance < fleeDistance) {
            if (--repathCooldownTicks <= 0) {
                Vec3 away = entity.position().subtract(fleeFrom.position()).normalize();
                entity.navigation().moveTo(entity.position()
                        .add(away.scale(fleeDistance - distance + 4)), true, true);
                repathCooldownTicks = REPATH_INTERVAL_TICKS;
            }
        } else {
            stopChase();
        }
        return true;
    }

    // Scores everyone in aggro range and picks the best target — an active attacker first, then the
    // closest / most-visible enemy. Runs both to acquire (no target) and to re-evaluate while engaged, but
    // only once the locked target has gone unreachable, so a reachable one is never dropped mid-fight. The
    // NPC's temperament still gates who is even eligible: aggressive hunts any enemy, defensive rescues a
    // mate, anyone who fights back answers a recent attacker; passive/fearful never open a fight.
    public void acquireTarget(NpcAllianceManager alliances) {
        NpcCombatSettings combat = entity.combatSettings();
        NpcReactionSettings reaction = entity.traitsSettings().reaction();
        pruneRecentAttackers();
        boolean mayScan = reaction.huntsEnemies() || reaction.defendsMates()
                || (reaction.fightsBack() && !recentAttackers.isEmpty());
        if ((!combat.melee().enabled() && !combat.ranged().enabled()) || !mayScan
                || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        // Locked and still reachable: no rescan, no churn — the fight stays on its current target.
        if (target != null && targetOutOfReachTicks < TARGET_LOCK_TICKS) {
            return;
        }
        double range = Math.max(1.0, combat.aggroRange());
        double rangeSq = range * range;
        java.util.Set<String> myAlliances = alliances.alliancesOf(entity);
        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double currentScore = Double.NEGATIVE_INFINITY;
        java.util.Set<java.util.UUID> grudgesInRange = new java.util.HashSet<>();
        AABB box = entity.getBoundingBox().inflate(range);
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box,
                other -> other != entity && other.isAlive())) {
            double distSq = entity.distanceToSqr(candidate);
            if (distSq > rangeSq) {
                continue;
            }
            // A given-up target stays off the menu until it breaks away and comes back, or until the
            // give-up time has passed once more — a fresh try, since the way to it may have opened up.
            if (gaveUp.containsKey(candidate.getUUID()) && grudgeExpired(candidate.getUUID())) {
                gaveUp.remove(candidate.getUUID());
                gaveUpAtTick.remove(candidate.getUUID());
            }
            if (gaveUp.containsKey(candidate.getUUID())) {
                grudgesInRange.add(candidate.getUUID());
                updateGaveUpSight(candidate);
                continue;
            }
            NpcAlliance.Relation relation = alliances.classify(myAlliances, alliances.alliancesOf(candidate));
            boolean recentAttacker = relation != NpcAlliance.Relation.MATE
                    && recentAttackers.containsKey(candidate.getUUID());
            // A direct target: an enemy the NPC hunts, or a fresh attacker it fights back against.
            if (((reaction.huntsEnemies() && relation == NpcAlliance.Relation.ENEMY)
                    || (reaction.fightsBack() && recentAttacker)) && canEngage(candidate)) {
                double score = scoreTarget(candidate, distSq, range, recentAttacker);
                if (candidate == target) {
                    currentScore = Math.max(currentScore, score);
                }
                if (score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
            // Defensive/aggressive rescue: the enemy caught attacking a nearby mate becomes the target.
            if (reaction.defendsMates() && relation == NpcAlliance.Relation.MATE) {
                LivingEntity attacker = candidate.getLastHurtByMob();
                if (attacker != null && attacker != entity && attacker.isAlive()
                        && !gaveUp.containsKey(attacker.getUUID())
                        && entity.distanceToSqr(attacker) <= rangeSq
                        && alliances.classify(myAlliances, alliances.alliancesOf(attacker)) == NpcAlliance.Relation.ENEMY
                        && canEngage(attacker)) {
                    double score = scoreTarget(attacker, entity.distanceToSqr(attacker), range,
                            recentAttackers.containsKey(attacker.getUUID())) + SCORE_DEFEND_MATE;
                    if (attacker == target) {
                        currentScore = Math.max(currentScore, score);
                    }
                    if (score > bestScore) {
                        best = attacker;
                        bestScore = score;
                    }
                }
            }
        }
        // A grudge whose target was not in range this scan has broken away (walked off, died,
        // respawned elsewhere): flag it so it is re-acquired the instant it returns, instead of
        // staying suppressed forever because its sight was never "lost" while still within range.
        for (java.util.Map.Entry<java.util.UUID, Boolean> grudge : gaveUp.entrySet()) {
            if (!grudgesInRange.contains(grudge.getKey())) {
                grudge.setValue(Boolean.TRUE);
            }
        }
        if (best == null) {
            return;
        }
        if (target == null) {
            engage(best);
        } else if (best != target && bestScore > currentScore + TARGET_SWITCH_MARGIN) {
            // Reached only past the lock check above: the current target is unreachable and this one
            // scores clearly higher (a nearby attacker over a far one the NPC can't get to).
            engage(best);
        }
    }

    // Ranks a candidate: a base for being in range, more the closer it is, a bonus for a clear line of
    // sight, and a large bonus (fading with age) for one currently dealing the NPC damage.
    private double scoreTarget(LivingEntity candidate, double distSq, double range, boolean recentAttacker) {
        double distance = Math.sqrt(distSq);
        double score = SCORE_BASE + Math.max(0.0, range - distance) * SCORE_PROXIMITY;
        if (recentAttacker) {
            Integer hitTick = recentAttackers.get(candidate.getUUID());
            int age = hitTick != null ? entity.tickCount - hitTick : RECENT_ATTACKER_MEMORY_TICKS;
            score += SCORE_ATTACKER + Math.max(0, RECENT_ATTACKER_MEMORY_TICKS - age) * SCORE_ATTACKER_RECENCY;
        }
        if (entity.hasLineOfSight(candidate)) {
            score += SCORE_LINE_OF_SIGHT;
        }
        return score;
    }

    // Sets a target and resets the give-up timer for the new engagement.
    public void engage(LivingEntity newTarget) {
        if (target != newTarget) {
            LivingEntity previous = target;
            target = newTarget;
            ticksSinceDamage = 0;
            targetOutOfReachTicks = 0;
            outOfDepthBandTicks = 0;
            forcedRange = null;
            // Drop whatever the NPC was doing (a wander stroll, a job route, an old chase) the instant it
            // engages, so combat takes over now instead of drifting toward the stale goal until the next
            // repath. The wander and farm controllers already stand down while a fight is on.
            stopChase();
            net.lunacore.luna_npc.api.LunaNpcEvents.fireTargetChanged(entity, previous, newTarget);
        }
    }

    // Drops the current target through the same switch, firing the target-changed event.
    public void disengage() {
        engage(null);
    }

    public LivingEntity target() {
        return target;
    }

    public boolean isFighting() {
        return target != null;
    }

    // After a player dies and respawns, the server replaces its entity with a fresh one for the same
    // UUID. A target still pointing at the old, replaced instance would pin the NPC forever — it never
    // re-runs acquisition while it believes it is fighting — leaving it "half stale": ignoring the
    // returned player yet still retaliating (a fresh hit re-targets the live entity). Treat such a
    // reference as gone so the target clears and the NPC re-acquires the returned player.
    private boolean isStalePlayerReference(LivingEntity candidate) {
        if (!(candidate instanceof net.minecraft.world.entity.player.Player)
                || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        return level.getServer().getPlayerList().getPlayer(candidate.getUUID()) != candidate;
    }

    // Resets the give-up timer whenever the NPC actually lands a hit on its current target.
    public void notifyDamageDealt(LivingEntity victim) {
        if (victim == target) {
            ticksSinceDamage = 0;
            lastLandedHitTick = entity.tickCount;
        }
    }

    private void rememberGaveUp(LivingEntity victim) {
        if (victim == null) {
            return;
        }
        if (gaveUp.size() >= MAX_GIVE_UP_MEMORY) {
            gaveUp.clear();
            gaveUpAtTick.clear();
        }
        // false = sight not yet lost since giving up.
        gaveUp.put(victim.getUUID(), Boolean.FALSE);
        gaveUpAtTick.put(victim.getUUID(), entity.tickCount);
    }

    private boolean grudgeExpired(java.util.UUID id) {
        Integer since = gaveUpAtTick.get(id);
        int forgetTicks = Math.max(MIN_GRUDGE_TICKS, entity.combatSettings().giveUpSeconds() * 20);
        return since == null || entity.tickCount - since >= forgetTicks;
    }

    // Tracks a given-up target's sight so it becomes eligible again only after it leaves view and
    // comes back — not merely because it is still standing there unreachable.
    private void updateGaveUpSight(LivingEntity candidate) {
        boolean visible = entity.hasLineOfSight(candidate);
        if (!visible) {
            gaveUp.put(candidate.getUUID(), Boolean.TRUE);
        } else if (Boolean.TRUE.equals(gaveUp.get(candidate.getUUID()))) {
            gaveUp.remove(candidate.getUUID());
        }
    }

    public void tick() {
        // Advanced combat replaces the whole simple loop: the node engine drives targeting, movement and
        // every attack itself.
        if (entity.combatSettings().combatMode() == NpcCombatMode.ADVANCED) {
            advancedEngine.tick();
            return;
        }
        // Fear overrides everything: a fleeing NPC only runs.
        if (tickFlee()) {
            return;
        }
        if (target == null) {
            return;
        }
        NpcCombatSettings combat = entity.combatSettings();
        boolean melee = combat.melee().enabled();
        boolean ranged = combat.ranged().enabled();
        if ((!melee && !ranged) || target.isRemoved() || target.isDeadOrDying()
                || target.level() != entity.level()
                || entity.distanceTo(target) > Math.max(1.0F, combat.aggroRange())
                || !inReachableMedium(target)
                || isIgnoredPlayer(target)
                || isStalePlayerReference(target)) {
            clearTarget();
            return;
        }
        // A depth-holding swimmer disengages from a target that leaves its depth band — but with
        // hysteresis (the wider keep-engaged band plus a grace period), never on the first tick out.
        if (isWaterMover()) {
            if (entity.navigation().depthWithinHoldBand(target.getY(), KEEP_ENGAGED_DEPTH_MARGIN)) {
                outOfDepthBandTicks = 0;
            } else if (++outOfDepthBandTicks > OUT_OF_DEPTH_GRACE_TICKS) {
                clearTarget();
                return;
            }
        }
        // Leave-area tether: unless the creator allows leaving in combat, the NPC drops any target
        // that would take it out of its spawn area — whether it has strayed out itself, or the target
        // has left the zone — so the wander tether can walk it home instead of jittering at the border.
        if (!entity.leaveAreaInCombatAllowed()
                && (!entity.isInHomeArea(entity.blockPosition()) || !entity.isInHomeArea(target.blockPosition()))) {
            clearTarget();
            return;
        }
        // The target sat in range without taking a hit for the whole timeout: give up on it and
        // remember not to re-acquire it until it breaks and re-enters sight or hits us again.
        ticksSinceDamage++;
        if (combat.giveUpSeconds() > 0 && ticksSinceDamage >= combat.giveUpSeconds() * 20) {
            rememberGaveUp(target);
            clearTarget();
            return;
        }
        if (meleeCooldownTicks > 0) {
            meleeCooldownTicks--;
        }
        if (pendingMeleeTicks > 0 && --pendingMeleeTicks == 0) {
            resolveMeleeSwing();
        }
        if (rangedCooldownTicks > 0) {
            rangedCooldownTicks--;
        }
        // Line of sight required but blocked: close in / reposition to get a clear shot instead
        // of firing blindly through walls.
        if (combat.requireLineOfSight() && !entity.hasLineOfSight(target)) {
            entity.releaseDraw();
            faceTarget();
            if (--repathCooldownTicks <= 0) {
                chaseMoveTo(target.position());
                repathCooldownTicks = REPATH_INTERVAL_TICKS;
            }
            return;
        }
        double distance = entity.distanceTo(target);
        if (isTargetInAttackRange(combat, distance)) {
            targetOutOfReachTicks = 0;
        } else {
            targetOutOfReachTicks++;
        }
        if (fightAtLongRange(combat, melee, ranged, distance)) {
            entity.showAttackRange(NpcAttackRange.LONG);
            tickRanged(combat, distance);
        } else {
            entity.showAttackRange(NpcAttackRange.CLOSE);
            tickMelee(combat.melee(), distance);
        }
    }

    // Whether the NPC uses its long-range attack this tick. With the range switch off this is the
    // built-in auto pick (ranged unless the target dove under half the preferred distance). With it on,
    // the rules decide, falling back to whichever attack is actually armed if they pick a disabled one.
    private boolean fightAtLongRange(NpcCombatSettings combat, boolean melee, boolean ranged, double distance) {
        boolean currentlyLong = entity.activeAttackRange() == NpcAttackRange.LONG;
        boolean wantLong = desiredLongRange(combat, melee, ranged, distance, currentlyLong);
        if (rangeHoldTicks > 0) {
            rangeHoldTicks--;
            // Held only while the current attack is still armed — never stuck on a disabled one.
            if (wantLong != currentlyLong && (currentlyLong ? ranged : melee)) {
                return currentlyLong;
            }
        }
        if (wantLong != currentlyLong) {
            rangeHoldTicks = RANGE_SWITCH_HOLD_TICKS;
        }
        return wantLong;
    }

    private boolean desiredLongRange(NpcCombatSettings combat, boolean melee, boolean ranged, double distance,
                                     boolean currentlyLong) {
        NpcAttackRange forced = updateForcedRange(combat, distance);
        if (forced == NpcAttackRange.LONG) {
            return ranged;
        }
        if (forced == NpcAttackRange.CLOSE) {
            return !melee;
        }
        double closeBelow = combat.distancePreference() * 0.5 + (currentlyLong ? 0.0 : RANGE_SWITCH_MARGIN);
        return ranged && !(melee && distance < closeBelow);
    }

    // The range the rules pick, cached between evaluations. Cheap metrics (self/target HP, distance) are
    // re-decided every tick; a rule that scans the world for nearby enemies is re-decided only on the
    // interval and staggered by entity id, so many NPCs in combat never all scan on the same tick.
    private NpcAttackRange updateForcedRange(NpcCombatSettings combat, double distance) {
        NpcRangeSwitch rangeSwitch = combat.rangeSwitch();
        if (!rangeSwitch.enabled()) {
            forcedRange = null;
            return null;
        }
        if (rangeSwitch.scansNearbyEntities() && forcedRange != null
                && (entity.tickCount + entity.getId()) % NpcValidation.RANGE_SWITCH_EVAL_INTERVAL_TICKS != 0) {
            return forcedRange;
        }
        forcedRange = resolveRange(rangeSwitch, distance);
        return forcedRange;
    }

    // First rule that passes wins (an OTHERWISE rule always passes, so it is the creator's "else"); with
    // no rule to catch it the NPC fights close.
    private NpcAttackRange resolveRange(NpcRangeSwitch rangeSwitch, double distance) {
        for (NpcRangeRule rule : rangeSwitch.rules()) {
            if (ruleMatches(rule, distance)) {
                return rule.result();
            }
        }
        return NpcAttackRange.CLOSE;
    }

    private boolean ruleMatches(NpcRangeRule rule, double distance) {
        return switch (rule.metric()) {
            case OTHERWISE -> true;
            case SELF_HP -> rule.comparator().test(entity.getHealth(), rule.value());
            case TARGET_HP_PERCENT -> rule.comparator().test(target.getMaxHealth() <= 0.0F
                    ? 0.0F : target.getHealth() / target.getMaxHealth() * 100.0F, rule.value());
            case DISTANCE -> rule.comparator().test((float) distance, rule.value());
            case ENEMIES_IN_RADIUS -> rule.comparator().test(countNearbyEnemies(rule.radius()), rule.value());
        };
    }

    // Enemies of this NPC (by faction) within the radius. The only range metric that queries the world,
    // so it reuses the same alliance classification as acquireTarget and rides the staggered interval.
    private float countNearbyEnemies(int radius) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return 0.0F;
        }
        NpcAllianceManager alliances = NpcAllianceManager.get(level.getServer());
        java.util.Set<String> mine = alliances.alliancesOf(entity);
        double radiusSq = (double) radius * radius;
        AABB box = entity.getBoundingBox().inflate(radius);
        int count = 0;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box,
                other -> other != entity && other.isAlive())) {
            if (entity.distanceToSqr(candidate) <= radiusSq
                    && alliances.classify(mine, alliances.alliancesOf(candidate)) == NpcAlliance.Relation.ENEMY) {
                count++;
            }
        }
        return count;
    }

    // Where a blow can actually land: within reach with a clear line to the target (a melee hit never
    // goes through walls), or flanking it for a swimmer.
    private boolean inMeleeStrikePosition(NpcAttackSettings melee, double distance) {
        double reach = Math.max(MIN_MELEE_REACH, melee.range());
        return isWaterMover() ? flanksTarget(reach) : distance <= reach && entity.hasLineOfSight(target);
    }

    private void tickMelee(NpcAttackSettings melee, double distance) {
        double reach = Math.max(MIN_MELEE_REACH, melee.range());
        boolean water = isWaterMover();
        if (inMeleeStrikePosition(melee, distance)) {
            stopChase();
            faceTarget();
            if (meleeCooldownTicks <= 0) {
                int interval = Math.max(1, Mth.ceil(melee.intervalSeconds() * 20.0F));
                beginMeleeSwing(melee, interval);
                meleeCooldownTicks = interval;
            }
            return;
        }
        // A spear is carried couched on the approach, like a vanilla spear charge; the blow releases it.
        net.minecraft.world.item.component.KineticWeapon kinetic =
                entity.getMainHandItem().get(DataComponents.KINETIC_WEAPON);
        if (kinetic != null && !entity.isUsingItem()) {
            entity.beginDraw();
            kinetic.makeSound(entity);
        }
        if (--repathCooldownTicks <= 0) {
            Vec3 destination;
            if (water) {
                Vec3 standoff = entity.navigation().waterCombatStandoff(target);
                destination = standoff != null ? standoff : target.position();
            } else {
                destination = meleeApproachPoint(reach);
            }
            entity.navigation().moveTo(destination, true, true);
            repathCooldownTicks = REPATH_INTERVAL_TICKS;
        }
    }

    // A standable spot just inside strike range on the NPC's side of the target, instead of the target's
    // own (blocked) cell — so the approach ends with the NPC able to hit rather than stopped a hair short.
    // A small random sidestep keeps two NPCs from meeting dead-centre and jamming each other in place.
    // The spot is only used when it is solid ground on the target's level: over a pit or a drop it would
    // snap to the floor below and lead the NPC down there, so it then heads for the target itself.
    private Vec3 meleeApproachPoint(double reach) {
        if (Math.abs(target.getY() - entity.getY()) > 0.5) {
            return target.position();
        }
        Vec3 fromTarget = entity.position().subtract(target.position());
        double horizontal = Math.sqrt(fromTarget.x * fromTarget.x + fromTarget.z * fromTarget.z);
        Vec3 dir = horizontal > 1.0E-3
                ? new Vec3(fromTarget.x / horizontal, 0.0, fromTarget.z / horizontal)
                : new Vec3(1.0, 0.0, 0.0);
        double sidestep = (entity.getRandom().nextDouble() - 0.5) * 0.8;
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x).scale(sidestep);
        Vec3 spot = target.position().add(dir.scale(reach * 0.7)).add(side);
        return isGroundAtTargetLevel(spot) ? spot : target.position();
    }

    private boolean isGroundAtTargetLevel(Vec3 spot) {
        BlockPos feet = BlockPos.containing(spot.x, target.getY() + 0.01, spot.z);
        net.minecraft.world.level.Level level = entity.level();
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    // True only beside the target at the same height — the one posture a water NPC strikes from
    // (plain 3D reach would let it bite from under or above). Failing it re-routes it to a side cell.
    private boolean flanksTarget(double reach) {
        double dx = target.getX() - entity.getX();
        double dz = target.getZ() - entity.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < FLANK_MIN_HORIZONTAL_DISTANCE || horizontal > reach) {
            return false;
        }
        AABB mine = entity.getBoundingBox();
        AABB theirs = target.getBoundingBox();
        double verticalGap = Math.max(theirs.minY - mine.maxY, mine.minY - theirs.maxY);
        return verticalGap <= FLANK_VERTICAL_SLACK;
    }

    // Kiting: hold the preferred distance. Too far -> close in (still firing). Crowded but not
    // yet past half the preference -> walk back to restore the distance, holding fire until it is
    // back in position. At the preferred band, or dived under half (stand ground) -> shoot.
    private void tickRanged(NpcCombatSettings combat, double distance) {
        double preference = combat.distancePreference();
        boolean holdFire = false;
        if (distance > preference + DISTANCE_BAND) {
            backAwayTicks = 0;
            if (--repathCooldownTicks <= 0) {
                chaseMoveTo(target.position());
                repathCooldownTicks = REPATH_INTERVAL_TICKS;
            }
        } else if (distance > preference * 0.5 && distance < preference - DISTANCE_BAND) {
            // Backing away to restore the distance; hold fire while doing so, but only up to the
            // grace — a target that keeps pace or corners the NPC gets shot rather than chased.
            backAwayTicks++;
            holdFire = backAwayTicks <= BACK_AWAY_GRACE_TICKS;
            if (--repathCooldownTicks <= 0) {
                Vec3 away = entity.position().subtract(target.position()).normalize();
                chaseMoveTo(entity.position().add(away.scale(preference - distance + DISTANCE_BAND)));
                repathCooldownTicks = REPATH_INTERVAL_TICKS;
            }
        } else {
            backAwayTicks = 0;
            stopChase();
        }
        faceTarget();
        tickRangedFire(combat, distance, holdFire);
    }

    // Drives the bow-draw pose around the (still fully simulated) shot; a crossbow has its own load-and-hold
    // cycle. A weapon that isn't drawn fires instantly with a swing. A bow draws during the tail of the
    // interval and releases at full draw; when the fire rate outruns the draw it holds the fully-drawn
    // pose and fires without re-drawing (NpcWarnings.rangedDrawHeld flags that config).
    private void tickRangedFire(NpcCombatSettings combat, double distance, boolean holdFire) {
        if (entity.getMainHandItem().getItem() instanceof CrossbowItem) {
            tickCrossbow(combat, distance, holdFire);
            return;
        }
        int drawTicks = drawDurationTicks(entity.getMainHandItem());
        if (drawTicks <= 0) {
            if (!holdFire && rangedCooldownTicks <= 0) {
                shoot(combat, distance);
                entity.swing(InteractionHand.MAIN_HAND, entity.getMainHandItem().getAttackAnimation(), true);
                rangedCooldownTicks = rangedIntervalTicks(combat);
            }
            return;
        }
        if (holdFire) {
            entity.releaseDraw();
            return;
        }
        boolean holdMode = rangedIntervalTicks(combat) < drawTicks;
        if (holdMode || rangedCooldownTicks <= drawTicks) {
            entity.beginDraw();
        } else {
            entity.releaseDraw();
        }
        // Fire only once the draw is actually complete, so the first shot of an engagement waits for the
        // pull and (in hold mode) the draw plays through once before the NPC starts firing on the interval.
        if (rangedCooldownTicks <= 0 && entity.getTicksUsingItem() >= drawTicks) {
            shoot(combat, distance);
            if (!holdMode) {
                entity.releaseDraw();
            }
            rangedCooldownTicks = rangedIntervalTicks(combat);
        }
    }

    // A crossbow loads once, then holds the loaded pose like a pillager — also while repositioning — and
    // fires on the interval, reloading right after. A fire rate faster than the reload keeps it loaded.
    private void tickCrossbow(NpcCombatSettings combat, double distance, boolean holdFire) {
        boolean holdMode = rangedIntervalTicks(combat) < NpcValidation.RANGED_DRAW_TICKS;
        if (!entity.isCrossbowLoaded()) {
            entity.beginDraw();
            if (entity.getTicksUsingItem() < NpcValidation.RANGED_DRAW_TICKS) {
                return;
            }
            entity.releaseDraw();
            entity.setCrossbowLoaded(true, combat.projectile().itemId());
        }
        if (!holdFire && rangedCooldownTicks <= 0) {
            shoot(combat, distance);
            rangedCooldownTicks = rangedIntervalTicks(combat);
            if (!holdMode) {
                entity.setCrossbowLoaded(false, "");
            }
        }
    }

    private int rangedIntervalTicks(NpcCombatSettings combat) {
        return Math.max(1, Mth.ceil(combat.ranged().intervalSeconds() * 20.0F));
    }

    // An edited attack speed applies on the next attack instead of after the old cooldown runs out.
    public void onCombatSettingsChanged(NpcCombatSettings combat) {
        meleeCooldownTicks = Math.min(meleeCooldownTicks, Math.max(1, Mth.ceil(combat.melee().intervalSeconds() * 20.0F)));
        rangedCooldownTicks = Math.min(rangedCooldownTicks, rangedIntervalTicks(combat));
    }

    // The draw time before a bow shot, or 0 for anything that is not drawn (which fires instantly). The
    // crossbow reuses the same length as its reload (tickCrossbow), so the item never fires itself.
    private int drawDurationTicks(ItemStack weapon) {
        if (weapon.getItem() instanceof BowItem) {
            return NpcValidation.RANGED_DRAW_TICKS;
        }
        return 0;
    }

    private void shoot(NpcCombatSettings combat, double distance) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        NpcAttackSettings ranged = combat.ranged();
        NpcProjectileSettings projectile = combat.projectile();
        float accuracy = projectile.accuracyAt((float) distance) / 100.0F;
        float inaccuracy = (1.0F - accuracy) * MAX_SPREAD_DEGREES;
        // Multishot/piercing come from the ranged attack effects, or (when gear affects stats, like every
        // other real-gear enchant) from the ranged weapon's own enchantment — a Multishot crossbow. The
        // higher of the two wins so neither path is ignored; a vanilla Multishot (level 1) fires three,
        // matching the "+1 over the level" mapping here.
        ItemStack rangedWeapon = entity.rangedWeaponStack();
        boolean realGear = entity.gearAffectsStats() && !rangedWeapon.isEmpty();
        int itemMultishot = realGear ? itemEnchantLevel(rangedWeapon, Enchantments.MULTISHOT, level) : 0;
        int itemPierce = realGear ? itemEnchantLevel(rangedWeapon, Enchantments.PIERCING, level) : 0;
        int shots = 1 + Math.max(ranged.enchantLevel("multishot"), itemMultishot > 0 ? itemMultishot + 1 : 0);
        int pierce = Math.max(ranged.enchantLevel("piercing"), itemPierce);
        float speedPerTick = projectile.speedBps() / 20.0F;
        ItemStack displayed = NpcEquipment.resolve(projectile.itemId());
        // Arrow items fly as real arrows (authentic model, sticks and drops); everything else
        // is the big glowing item projectile.
        boolean arrowStyle = displayed.isEmpty()
                || displayed.getItem() instanceof net.minecraft.world.item.ArrowItem;

        for (int shot = 0; shot < shots; shot++) {
            net.minecraft.world.entity.projectile.Projectile bullet;
            if (arrowStyle) {
                NpcArrowEntity arrow = LunaNpcEntities.ARROW.create(level,
                        net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                if (arrow == null) {
                    return;
                }
                arrow.configure(ranged.damage(), projectile.homing(), projectile.curve(),
                        projectile.curveMaxDistance(), pierce, target, displayed, speedPerTick);
                bullet = arrow;
            } else {
                NpcProjectileEntity thrown = LunaNpcEntities.PROJECTILE.create(level,
                        net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                if (thrown == null) {
                    return;
                }
                thrown.setItem(displayed);
                thrown.configure(ranged.damage(), projectile.homing(), projectile.curve(),
                        projectile.curveMaxDistance(), pierce, target, speedPerTick);
                bullet = thrown;
            }
            bullet.setOwner(entity);
            bullet.setPos(entity.getX(), entity.getEyeY() - 0.1, entity.getZ());
            // Aim at the target's body center, not its eyes: the center sits solidly inside the
            // hitbox, so shots land square on the body and impacts fall where expected.
            Vec3 direction = target.getBoundingBox().getCenter().subtract(bullet.position());
            // Extra multishot projectiles fan out sideways like a crossbow volley.
            if (shot > 0) {
                float fan = (shot % 2 == 0 ? 1 : -1) * ((shot + 1) / 2) * 6.0F;
                direction = direction.yRot((float) Math.toRadians(fan));
            }
            bullet.shoot(direction.x, direction.y, direction.z, speedPerTick, inaccuracy);
            level.addFreshEntity(bullet);
        }
        // The far sound plays on the shot here and again where the projectile lands (in
        // ImpactEffects.trigger from the projectile's hit).
        ImpactEffects.playSoundAt(level, entity.position(), entity.combatFarSoundId());
    }

    private static int itemEnchantLevel(ItemStack stack, ResourceKey<Enchantment> key, ServerLevel level) {
        if (stack.isEmpty()) {
            return 0;
        }
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
                .orElse(0);
    }

    // Projectile impacts funnel back here so ranged hits share the same effect pipeline.
    public static void applyRangedHit(ServerLevel level, NpcEntity npc,
                                      net.minecraft.world.entity.projectile.Projectile projectile,
                                      LivingEntity target, float damage) {
        NpcAttackSettings ranged = npc.combatSettings().ranged();
        ItemStack weapon = npc.rangedWeaponStack();
        DamageSource source = npc.combatSettings().difficultyScaling()
                ? level.damageSources().mobProjectile(projectile, npc)
                : NpcDamageSources.npcProjectile(level, projectile, npc);
        if (applyHit(level, npc, target, ranged, weapon, source, damage)) {
            npc.onCombatDamageDealt(target);
        }
    }

    // Advanced-attack impact: an attack-library projectile deals its own flat damage and effects, never the
    // NPC's simple ranged settings, so the library stays independent (no gear, no ranged enchants mixed in).
    public static void applyAdvancedHit(ServerLevel level, NpcEntity npc,
                                        net.minecraft.world.entity.projectile.Projectile projectile,
                                        LivingEntity target, float damage, java.util.List<NpcAttackEffect> effects) {
        DamageSource source = NpcDamageSources.npcProjectile(level, projectile, npc);
        if (target.hurtServer(level, source, damage)) {
            applyAttackEffects(level, npc, target, effects);
            npc.onCombatDamageDealt(target);
        }
    }

    // Applies an advanced attack's effect list to a target: namespaced ids are potion effects; the plain
    // fire/knockback ids fire and shove (their amplifier is the level). Flat damage is set by the attack.
    public static void applyAttackEffects(ServerLevel level, NpcEntity npc, LivingEntity target,
                                          java.util.List<NpcAttackEffect> effects) {
        for (NpcAttackEffect effect : effects) {
            if (effect.isEnchantLike()) {
                int strength = effect.amplifier() + 1;
                switch (effect.effectId()) {
                    case "flame", "fire_aspect" -> target.igniteForTicks(80 * strength);
                    case "knockback", "punch" -> {
                        Vec3 direction = new Vec3(target.getX() - npc.getX(), 0.0,
                                target.getZ() - npc.getZ()).normalize();
                        target.push(direction.x * 0.5 * strength, 0.1, direction.z * 0.5 * strength);
                        target.syncVelocity = true;
                    }
                    default -> {
                    }
                }
                continue;
            }
            if (effect.durationSeconds() <= 0.0F) {
                continue;
            }
            Identifier parsed = Identifier.tryParse(effect.effectId());
            if (parsed == null) {
                continue;
            }
            BuiltInRegistries.MOB_EFFECT.getOptional(parsed)
                    .map(BuiltInRegistries.MOB_EFFECT::wrapAsHolder)
                    .ifPresent(holder -> target.addEffect(new MobEffectInstance(holder,
                            Mth.ceil(effect.durationSeconds() * 20.0F), effect.amplifier()), npc));
        }
    }

    // Starts the swing now and schedules the blow for its peak, so the animation reads as connecting on
    // impact. The close (swing) sound plays here; the hit itself lands in resolveMeleeSwing.
    private void beginMeleeSwing(NpcAttackSettings melee, int intervalTicks) {
        pendingChargeBonus = spearChargeBonus();
        if (entity.getMainHandItem().has(DataComponents.KINETIC_WEAPON)) {
            entity.releaseDraw();
        }
        net.minecraft.world.item.component.PiercingWeapon piercing =
                entity.getMainHandItem().get(DataComponents.PIERCING_WEAPON);
        if (piercing != null) {
            piercing.makeSound(entity);
        }
        entity.swing(InteractionHand.MAIN_HAND, entity.getMainHandItem().getAttackAnimation(), true);
        if (entity.level() instanceof ServerLevel level) {
            ImpactEffects.playSoundAt(level, entity.position(), entity.combatCloseSoundId());
        }
        pendingMelee = melee;
        pendingMeleeTicks = Math.min(MELEE_SWING_PEAK_TICKS, intervalTicks / 2);
        if (pendingMeleeTicks <= 0) {
            resolveMeleeSwing();
        }
    }

    // Vanilla's kinetic formula, so a couched spear lands as hard as a player's charge: the closing speed
    // (blocks/s toward the target) times the spear's damage multiplier. Real-gear NPCs only, like every
    // other weapon stat.
    private float spearChargeBonus() {
        net.minecraft.world.item.component.KineticWeapon kinetic =
                entity.getMainHandItem().get(DataComponents.KINETIC_WEAPON);
        if (kinetic == null || !entity.isUsingItem() || !entity.gearAffectsStats()) {
            return 0.0F;
        }
        Vec3 toTarget = target.position().subtract(entity.position()).normalize();
        double closing = entity.getKnownSpeed().subtract(target.getKnownSpeed()).scale(20.0).dot(toTarget);
        return Mth.floor(Math.max(0.0, closing) * kinetic.damageMultiplier());
    }

    // The spear's own vanilla hit sound: the charge impact after a couched run, the jab's otherwise.
    private void playSpearHitSound(ServerLevel level, ItemStack weapon) {
        net.minecraft.world.item.component.KineticWeapon kinetic = weapon.get(DataComponents.KINETIC_WEAPON);
        if (pendingChargeBonus > 0.0F && kinetic != null && kinetic.hitSound().isPresent()) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(), kinetic.hitSound().get(),
                    entity.getSoundSource(), 1.0F, 1.0F);
            return;
        }
        net.minecraft.world.item.component.PiercingWeapon piercing = weapon.get(DataComponents.PIERCING_WEAPON);
        if (piercing != null) {
            piercing.makeHitSound(target);
        }
    }

    // Lands the pending blow at the swing's peak. A target that stepped out of reach during the wind-up
    // dodges it — the swing still played, but nothing connects.
    private void resolveMeleeSwing() {
        NpcAttackSettings melee = pendingMelee;
        pendingMelee = null;
        pendingMeleeTicks = -1;
        if (melee == null || target == null || target.isRemoved() || target.isDeadOrDying()
                || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        double reach = Math.max(MIN_MELEE_REACH, melee.range());
        boolean inReach = isWaterMover() ? flanksTarget(reach)
                : entity.distanceTo(target) <= reach + 0.5 && entity.hasLineOfSight(target);
        if (!inReach) {
            return;
        }
        ItemStack weapon = entity.getMainHandItem();
        DamageSource source = entity.combatSettings().difficultyScaling()
                ? level.damageSources().mobAttack(entity)
                : NpcDamageSources.npcAttack(level, entity);
        if (applyHit(level, entity, target, melee, weapon, source, melee.damage() + pendingChargeBonus)) {
            ticksSinceDamage = 0;
            lastLandedHitTick = entity.tickCount;
            playSpearHitSound(level, weapon);
            // The impact sound plays on the target where the blow lands, with the crater and particles.
            ImpactEffects.trigger(level, target.getBoundingBox().getCenter(), melee.impact(),
                    entity.closeImpactSoundId());
        }
    }

    // The shared hit pipeline: real-gear weapon damage and enchantments, the enchant-like target
    // effects (damage, push, fire) and the potion effects.
    private static boolean applyHit(ServerLevel level, NpcEntity npc, LivingEntity target,
                                    NpcAttackSettings attack, ItemStack weapon, DamageSource source,
                                    float damage) {
        boolean realGear = npc.gearAffectsStats() && !weapon.isEmpty();
        if (realGear) {
            damage += weaponAttackDamage(weapon);
            damage = EnchantmentHelper.modifyDamage(level, weapon, target, source, damage);
        }
        int damageLevels = attack.enchantLevel("sharpness") + attack.enchantLevel("power");
        if (damageLevels > 0) {
            damage += BASE_ENCHANT_BONUS * damageLevels + 0.5F;
        }
        // An attack set faster than vanilla's hit cooldown would otherwise only land every 0.5s.
        if (attack.intervalSeconds() * 20.0F < VANILLA_HIT_COOLDOWN_TICKS) {
            target.setInvulnerableTime(0);
        }
        if (!target.hurtServer(level, source, damage)) {
            return false;
        }
        int knockbackLevels = attack.enchantLevel("knockback") + attack.enchantLevel("punch");
        if (knockbackLevels > 0) {
            Vec3 direction = new Vec3(target.getX() - npc.getX(), 0.0,
                    target.getZ() - npc.getZ()).normalize();
            target.push(direction.x * 0.5 * knockbackLevels, 0.1, direction.z * 0.5 * knockbackLevels);
            target.syncVelocity = true;
        }
        int fireLevels = attack.enchantLevel("fire_aspect") + attack.enchantLevel("flame");
        if (fireLevels > 0) {
            target.igniteForTicks(80 * fireLevels);
        }
        for (NpcAttackEffect effect : attack.effects()) {
            if (effect.isEnchantLike() || effect.durationSeconds() <= 0.0F) {
                continue;
            }
            Identifier parsed = Identifier.tryParse(effect.effectId());
            if (parsed == null) {
                continue;
            }
            BuiltInRegistries.MOB_EFFECT.getOptional(parsed)
                    .map(BuiltInRegistries.MOB_EFFECT::wrapAsHolder)
                    .ifPresent(holder -> target.addEffect(new MobEffectInstance(holder,
                            Mth.ceil(effect.durationSeconds() * 20.0F), effect.amplifier()), npc));
        }
        if (realGear) {
            EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, weapon);
        }
        return true;
    }

    // The weapon's full displayed attack damage — the number shown in the item tooltip — so an NPC
    // holding a netherite sword adds its 8, not the raw +7 modifier. The tooltip value is the
    // vanilla player base attack damage plus the item's ATTACK_DAMAGE modifiers; a non-weapon (no
    // such modifier) adds nothing.
    private static float weaponAttackDamage(ItemStack weapon) {
        ItemAttributeModifiers modifiers = weapon.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return 0.0F;
        }
        float modifierTotal = 0.0F;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().value() == Attributes.ATTACK_DAMAGE.value()
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                modifierTotal += (float) entry.modifier().amount();
            }
        }
        return modifierTotal > 0.0F ? modifierTotal + PLAYER_BASE_ATTACK_DAMAGE : 0.0F;
    }

    private void stopChase() {
        if (entity.navigation().isNavigating()) {
            entity.navigation().stop();
        }
        repathCooldownTicks = 0;
    }

    // During combat the NPC only paths to spots inside its home area, unless the creator lets it
    // leave in combat. A refused move (false) simply leaves it where it is, holding the line.
    private boolean chaseMoveTo(Vec3 destination) {
        if (!entity.leaveAreaInCombatAllowed() && !entity.isInHomeArea(BlockPos.containing(destination))) {
            return false;
        }
        return entity.navigation().moveTo(destination, true, true);
    }

    private void clearTarget() {
        LivingEntity previous = target;
        target = null;
        entity.releaseDraw();
        forcedRange = null;
        pendingMelee = null;
        pendingMeleeTicks = -1;
        targetOutOfReachTicks = 0;
        entity.showAttackRange(NpcAttackRange.CLOSE);
        if (previous != null) {
            net.lunacore.luna_npc.api.LunaNpcEvents.fireTargetChanged(entity, previous, null);
        }
        if (entity.navigation().isScripted()) {
            entity.navigation().stop();
        }
    }

    // While a route is being walked the facing belongs to navigation: turning the NPC back to its target
    // every tick fought the steering, so one routing around a wall between it and its target kept facing
    // the wall and edged along it instead of going around.
    private void faceTarget() {
        if (entity.navigation().isNavigating()) {
            return;
        }
        double dx = target.getX() - entity.getX();
        double dz = target.getZ() - entity.getZ();
        float desiredYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        entity.setYRot(desiredYaw);
        entity.setYBodyRot(desiredYaw);
        entity.setYHeadRot(desiredYaw);
    }
}
