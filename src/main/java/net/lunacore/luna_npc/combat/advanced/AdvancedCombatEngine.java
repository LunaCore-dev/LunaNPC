package net.lunacore.luna_npc.combat.advanced;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.lunacore.luna_npc.api.NpcAdvancedCombat;
import net.lunacore.luna_npc.api.NpcAlliance;
import net.lunacore.luna_npc.api.NpcCombatAction;
import net.lunacore.luna_npc.api.NpcCombatActionType;
import net.lunacore.luna_npc.api.NpcCombatRule;
import net.lunacore.luna_npc.api.NpcCombatTrigger;
import net.lunacore.luna_npc.api.NpcTargetSelector;
import net.lunacore.luna_npc.alliance.NpcAllianceManager;
import net.lunacore.luna_npc.combat.NpcCombatController;
import net.lunacore.luna_npc.entity.NpcEntity;

// The node-based advanced-combat brain of one NPC. Each server tick it refreshes the engagement bubble,
// keeps a primary target, then walks its flat rule list: a rule fires when its trigger fires this tick, its
// cooldown is up, and every condition passes. HP is just a condition source, so "phases" emerge from the
// rules themselves. Runtime state (flags, counters, timers, summons, telegraphs, hazards, temporary
// defensive buffs, delayed actions) lives here for the actions to read and mutate. Only ticks while the
// NPC's combatMode is ADVANCED — the controller gates the call.
public final class AdvancedCombatEngine {

    private static final int ENGAGEMENT_REFRESH_TICKS = 5;
    private static final int DAMAGE_BURST_WINDOW_TICKS = 20;
    private static final int MAX_SUMMONS = 64;

    private final NpcEntity npc;
    private final NpcCombatController controller;

    private ServerLevel level;
    private boolean inCombat;
    private int combatStartTick;
    private int lastHitTick;
    private float prevHpPercent = 100.0F;

    private final Map<String, Float> flags = new HashMap<>();
    private final Map<String, Float> counters = new HashMap<>();
    private final Map<String, Integer> timers = new HashMap<>();
    private final List<UUID> summons = new ArrayList<>();
    private final java.util.Set<String> warnedSummons = new java.util.HashSet<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final List<Telegraph> telegraphs = new ArrayList<>();
    private final List<Hazard> hazards = new ArrayList<>();
    private final Map<UUID, Float> damageByAttacker = new HashMap<>();

    private int shieldTicks;
    private float shieldAmount;
    private int invulnTicks;
    private int guardTicks;
    private float guardReduction;
    private int untargetableTicks;
    private int enrageTicks;
    private float enrageDamageMultiplier = 1.0F;
    private int castingUntilTick;

    private List<LivingEntity> engaged = List.of();
    private List<LivingEntity> hostiles = List.of();
    private List<LivingEntity> players = List.of();

    private NpcCombatRule[] activeRules = new NpcCombatRule[0];
    private int[] ruleCooldownTicks = new int[0];
    private boolean[] ruleFiredOnce = new boolean[0];

    private boolean evCombatStart;
    private boolean evTargetAcquired;
    private boolean evTargetLost;
    private boolean evTookDamage;
    private boolean evDealtDamage;
    private boolean evKilledTarget;
    private boolean evAddDied;
    private boolean evAllAddsDead;
    private boolean evInterrupted;
    private boolean evTargetAction;
    private boolean evStatusApplied;
    private final java.util.Set<String> elapsedTimers = new java.util.HashSet<>();
    private final List<int[]> damageBurstLog = new ArrayList<>();

    public AdvancedCombatEngine(NpcEntity npc, NpcCombatController controller) {
        this.npc = npc;
        this.controller = controller;
    }

    NpcEntity npc() {
        return npc;
    }

    ServerLevel level() {
        return level;
    }

    RandomSource random() {
        return npc.getRandom();
    }

    // ---- Main tick ----

    public void tick() {
        if (!(npc.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        level = serverLevel;
        NpcAdvancedCombat advanced = npc.combatSettings().advanced();
        syncRules(advanced);
        if ((npc.tickCount + npc.getId()) % ENGAGEMENT_REFRESH_TICKS == 0) {
            refreshEngagement(advanced.engagementRadius());
        }
        LivingEntity primary = updatePrimaryTarget();
        CombatContext ctx = new CombatContext(this, primary);
        evaluateRules(advanced, ctx);
        runScheduled();
        tickHazards();
        tickTelegraphs();
        tickTimers();
        tickTemporaryStates();
        tickUntargetable();
        pruneSummons();
        if (primary != null && !npc.navigation().isScripted()) {
            faceEntity(primary);
        }
        prevHpPercent = hpPercent();
        clearTickEvents();
    }

    private void syncRules(NpcAdvancedCombat advanced) {
        List<NpcCombatRule> rules = advanced.rules();
        if (activeRules.length == rules.size()) {
            boolean same = true;
            for (int i = 0; i < activeRules.length; i++) {
                if (activeRules[i] != rules.get(i)) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return;
            }
        }
        activeRules = rules.toArray(new NpcCombatRule[0]);
        ruleCooldownTicks = new int[activeRules.length];
        ruleFiredOnce = new boolean[activeRules.length];
    }

    private void refreshEngagement(double radius) {
        double radiusSq = radius * radius;
        AABB box = npc.getBoundingBox().inflate(radius);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box,
                other -> other != npc && other.isAlive() && npc.distanceToSqr(other) <= radiusSq);
        NpcAllianceManager alliances = NpcAllianceManager.get(level.getServer());
        java.util.Set<String> mine = alliances.alliancesOf(npc);
        List<LivingEntity> engagedList = new ArrayList<>();
        List<LivingEntity> hostileList = new ArrayList<>();
        List<LivingEntity> playerList = new ArrayList<>();
        for (LivingEntity candidate : found) {
            // Creative/spectator players are out of the fight entirely, exactly as simple combat treats
            // them — so they never count toward targets, hostiles, players or the surrounded/burst scans.
            if (NpcCombatController.isIgnoredPlayer(candidate)) {
                continue;
            }
            engagedList.add(candidate);
            if (candidate instanceof Player player) {
                playerList.add(player);
            }
            if (alliances.classify(mine, alliances.alliancesOf(candidate)) == NpcAlliance.Relation.ENEMY) {
                hostileList.add(candidate);
            }
        }
        engaged = engagedList;
        hostiles = hostileList;
        players = playerList;
    }

    private LivingEntity updatePrimaryTarget() {
        LivingEntity current = controller.target();
        boolean valid = current != null && current.isAlive() && !current.isRemoved()
                && current.level() == level && withinEngagement(current);
        if (current != null && !valid) {
            controller.disengage();
            current = null;
            evTargetLost = true;
        }
        if (current == null) {
            LivingEntity nearest = nearestHostile();
            if (nearest != null) {
                controller.engage(nearest);
                current = nearest;
                evTargetAcquired = true;
            }
        }
        boolean nowInCombat = current != null;
        if (nowInCombat && !inCombat) {
            inCombat = true;
            combatStartTick = npc.tickCount;
            prevHpPercent = hpPercent();
            evCombatStart = true;
            // "Once per fight" resets each new engagement.
            java.util.Arrays.fill(ruleFiredOnce, false);
        } else if (!nowInCombat) {
            inCombat = false;
        }
        return current;
    }

    private boolean withinEngagement(LivingEntity entity) {
        double radius = npc.combatSettings().advanced().engagementRadius();
        return npc.distanceToSqr(entity) <= radius * radius;
    }

    private LivingEntity nearestHostile() {
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (LivingEntity candidate : hostiles) {
            double distSq = npc.distanceToSqr(candidate);
            if (distSq < bestSq) {
                best = candidate;
                bestSq = distSq;
            }
        }
        return best;
    }

    // ---- Rule evaluation ----

    private void evaluateRules(NpcAdvancedCombat advanced, CombatContext ctx) {
        for (int i = 0; i < activeRules.length; i++) {
            if (ruleCooldownTicks[i] > 0) {
                ruleCooldownTicks[i]--;
            }
            NpcCombatRule rule = activeRules[i];
            if (ruleCooldownTicks[i] > 0 || (rule.once() && ruleFiredOnce[i])) {
                continue;
            }
            if (!triggerFires(rule.trigger(), ctx) || !CombatValueEvaluator.allMatch(rule.conditions(), ctx)) {
                continue;
            }
            executeRule(rule, ctx);
            ruleFiredOnce[i] = true;
            ruleCooldownTicks[i] = Math.max(1, Mth.ceil(rule.cooldownSeconds() * 20.0F));
        }
    }

    private boolean triggerFires(NpcCombatTrigger trigger, CombatContext ctx) {
        return switch (trigger.type()) {
            case ALWAYS -> true;
            case INTERVAL -> {
                int period = Math.max(1, Mth.ceil(trigger.seconds() * 20.0F));
                yield inCombat && (npc.tickCount - combatStartTick) % period == 0;
            }
            case ON_COMBAT_START -> evCombatStart;
            case ON_HP_CROSSES -> prevHpPercent > trigger.threshold() && hpPercent() <= trigger.threshold();
            case ON_TOOK_DAMAGE -> evTookDamage;
            case ON_DEALT_DAMAGE -> evDealtDamage;
            case ON_KILLED_TARGET -> evKilledTarget;
            case ON_ADD_DIED -> evAddDied;
            case ON_ALL_ADDS_DEAD -> evAllAddsDead;
            case ON_TARGET_ACQUIRED -> evTargetAcquired;
            case ON_TARGET_LOST -> evTargetLost;
            case ON_TIMER_ELAPSED -> elapsedTimers.contains(trigger.key());
            case ON_INTERRUPTED -> evInterrupted;
            case ON_TARGET_ACTION -> evTargetAction;
            case ON_STATUS_APPLIED -> evStatusApplied;
            case ON_SURROUNDED -> hostiles.size() >= trigger.threshold();
            case ON_DAMAGE_BURST -> damageInWindow() >= trigger.threshold();
            case ON_ENRAGE_TIMER -> {
                int at = Math.max(1, Mth.ceil(trigger.seconds() * 20.0F));
                yield inCombat && npc.tickCount - combatStartTick == at;
            }
        };
    }

    private void executeRule(NpcCombatRule rule, CombatContext ctx) {
        int cumulativeDelay = 0;
        for (NpcCombatAction action : rule.actions()) {
            if (action.type() == NpcCombatActionType.WAIT) {
                cumulativeDelay += action.durationTicks();
                continue;
            }
            int total = cumulativeDelay + action.delayTicks();
            if (total <= 0) {
                CombatActionExecutor.execute(action, ctx, this);
            } else {
                scheduled.add(new Scheduled(action, targetId(ctx.primaryTarget), npc.tickCount + total));
                castingUntilTick = Math.max(castingUntilTick, npc.tickCount + total);
            }
        }
    }

    private void runScheduled() {
        if (scheduled.isEmpty()) {
            return;
        }
        List<Scheduled> due = new ArrayList<>();
        scheduled.removeIf(entry -> {
            if (entry.fireTick <= npc.tickCount) {
                due.add(entry);
                return true;
            }
            return false;
        });
        for (Scheduled entry : due) {
            LivingEntity target = controller.target();
            if (entry.targetId != null && level.getEntity(entry.targetId) instanceof LivingEntity living
                    && living.isAlive()) {
                target = living;
            }
            CombatActionExecutor.execute(entry.action, new CombatContext(this, target), this);
        }
    }

    // ---- Target selection (used by the action executor) ----

    List<LivingEntity> resolveTargets(NpcTargetSelector selector, LivingEntity primary) {
        return switch (selector) {
            case PRIMARY -> primary != null ? List.of(primary) : List.of();
            case SELF -> List.of(npc);
            case ALL_IN_RANGE, EACH -> new ArrayList<>(hostiles);
            case ALL_PLAYERS -> new ArrayList<>(players);
            case NEAREST -> singletonOrEmpty(nearestHostile());
            case FARTHEST -> singletonOrEmpty(extremeHostile(false, false));
            case LOWEST_HP -> singletonOrEmpty(extremeHostile(true, true));
            case HIGHEST_DAMAGE -> singletonOrEmpty(highestDamageHostile(primary));
            case RANDOM -> hostiles.isEmpty() ? List.of()
                    : List.of(hostiles.get(random().nextInt(hostiles.size())));
        };
    }

    private List<LivingEntity> singletonOrEmpty(LivingEntity entity) {
        return entity == null ? List.of() : List.of(entity);
    }

    private LivingEntity extremeHostile(boolean byHealth, boolean lowest) {
        LivingEntity best = null;
        double bestValue = lowest ? Double.MAX_VALUE : -Double.MAX_VALUE;
        for (LivingEntity candidate : hostiles) {
            double value = byHealth ? candidate.getHealth() : npc.distanceToSqr(candidate);
            if (lowest ? value < bestValue : value > bestValue) {
                best = candidate;
                bestValue = value;
            }
        }
        return best;
    }

    private LivingEntity highestDamageHostile(LivingEntity fallback) {
        LivingEntity best = null;
        float bestDamage = -1.0F;
        for (LivingEntity candidate : hostiles) {
            float dealt = damageByAttacker.getOrDefault(candidate.getUUID(), 0.0F);
            if (dealt > bestDamage) {
                best = candidate;
                bestDamage = dealt;
            }
        }
        return best != null ? best : fallback;
    }

    // ---- Value sources scoped to the engagement bubble ----

    int countHostilesInRadius(int radius) {
        return countWithin(hostiles, radius);
    }

    int countPlayersInRadius(int radius) {
        return countWithin(players, radius);
    }

    private int countWithin(List<LivingEntity> list, int radius) {
        if (radius <= 0) {
            return list.size();
        }
        double radiusSq = (double) radius * radius;
        int count = 0;
        for (LivingEntity entity : list) {
            if (npc.distanceToSqr(entity) <= radiusSq) {
                count++;
            }
        }
        return count;
    }

    int summonsAlive() {
        return summons.size();
    }

    int playersInEngagement() {
        return players.size();
    }

    float secondsInCombat() {
        return inCombat ? (npc.tickCount - combatStartTick) / 20.0F : 0.0F;
    }

    float secondsSinceHit() {
        return (npc.tickCount - lastHitTick) / 20.0F;
    }

    float flag(String key) {
        return flags.getOrDefault(key, 0.0F);
    }

    float counter(String key) {
        return counters.getOrDefault(key, 0.0F);
    }

    List<LivingEntity> hostiles() {
        return hostiles;
    }

    List<LivingEntity> engaged() {
        return engaged;
    }

    private float hpPercent() {
        double max = npc.lunaMaxHealth();
        return max <= 0.0 ? 0.0F : (float) (npc.lunaHealth() / max * 100.0);
    }

    // ---- State mutators (called by the action executor) ----

    void setFlag(String key, float value) {
        if (!key.isEmpty()) {
            flags.put(key, value);
        }
    }

    void clearFlag(String key) {
        flags.remove(key);
    }

    void incCounter(String key, float delta) {
        if (!key.isEmpty()) {
            counters.merge(key, delta, Float::sum);
        }
    }

    void startTimer(String key, int ticks) {
        if (!key.isEmpty()) {
            timers.put(key, Math.max(1, ticks));
        }
    }

    void addSummon(UUID uuid) {
        if (summons.size() < MAX_SUMMONS) {
            summons.add(uuid);
        }
    }

    // True the first time a given summon id trips a warning this fight, so a boss summoning every few
    // seconds logs the advisory once instead of spamming the console.
    boolean shouldWarnSummon(String id) {
        return warnedSummons.add(id);
    }

    void addTelegraph(List<net.minecraft.world.phys.Vec3> points, double radius, int ticks, String particleId) {
        telegraphs.add(new Telegraph(points, radius, Math.max(1, ticks), particleId));
    }

    void addHazard(List<net.minecraft.world.phys.Vec3> points, double radius, int ticks, float damagePerTick,
                   java.util.List<net.lunacore.luna_npc.api.NpcAttackEffect> effects) {
        hazards.add(new Hazard(points, radius, Math.max(1, ticks), damagePerTick, effects));
    }

    void grantShield(float amount, int ticks) {
        shieldAmount = amount;
        shieldTicks = Math.max(1, ticks);
    }

    void grantInvuln(int ticks) {
        invulnTicks = Math.max(1, ticks);
    }

    void grantGuard(float reduction, int ticks) {
        guardReduction = Mth.clamp(reduction, 0.0F, 1.0F);
        guardTicks = Math.max(1, ticks);
    }

    void grantUntargetable(int ticks) {
        untargetableTicks = Math.max(1, ticks);
    }

    void enrage(float damageMultiplier, int ticks) {
        enrageDamageMultiplier = Math.max(0.0F, damageMultiplier);
        enrageTicks = Math.max(1, ticks);
    }

    float enrageDamageMultiplier() {
        return enrageTicks > 0 ? enrageDamageMultiplier : 1.0F;
    }

    void switchAggro(LivingEntity newTarget) {
        if (newTarget != null && newTarget != npc) {
            controller.engage(newTarget);
        }
    }

    void endCombat() {
        controller.disengage();
        inCombat = false;
        scheduled.clear();
    }

    void markStatusApplied() {
        evStatusApplied = true;
    }

    NpcCombatController controller() {
        return controller;
    }

    // ---- Event hooks (called from the entity / controller) ----

    public void onTookDamage(float amount, LivingEntity attacker) {
        lastHitTick = npc.tickCount;
        evTookDamage = true;
        damageBurstLog.add(new int[]{npc.tickCount, Mth.ceil(amount)});
        if (attacker != null) {
            damageByAttacker.merge(attacker.getUUID(), amount, Float::sum);
        }
        if (npc.tickCount < castingUntilTick) {
            evInterrupted = true;
        }
    }

    public void onDealtDamage(LivingEntity victim) {
        evDealtDamage = true;
        if (victim != null && (victim.isDeadOrDying() || victim.isRemoved())) {
            evKilledTarget = true;
        }
    }

    // Incoming damage passed through the active defensive buffs. Full block (invuln) returns 0.
    public float mitigate(float amount) {
        if (invulnTicks > 0) {
            return 0.0F;
        }
        float result = amount;
        if (guardTicks > 0) {
            result *= (1.0F - guardReduction);
        }
        if (shieldTicks > 0 && shieldAmount > 0.0F) {
            float absorbed = Math.min(shieldAmount, result);
            shieldAmount -= absorbed;
            result -= absorbed;
            if (shieldAmount <= 0.0F) {
                shieldTicks = 0;
            }
        }
        return result;
    }

    public boolean isInvulnerable() {
        return invulnTicks > 0;
    }

    public boolean isUntargetable() {
        return untargetableTicks > 0;
    }

    // ---- Per-tick upkeep ----

    private void tickHazards() {
        if (hazards.isEmpty()) {
            return;
        }
        hazards.removeIf(hazard -> {
            hazard.tick(this);
            return --hazard.ticksRemaining <= 0;
        });
    }

    private void tickTelegraphs() {
        if (telegraphs.isEmpty()) {
            return;
        }
        telegraphs.removeIf(telegraph -> {
            telegraph.tick(this);
            return --telegraph.ticksRemaining <= 0;
        });
    }

    private void tickTimers() {
        elapsedTimers.clear();
        if (timers.isEmpty()) {
            return;
        }
        timers.entrySet().removeIf(entry -> {
            int left = entry.getValue() - 1;
            if (left <= 0) {
                elapsedTimers.add(entry.getKey());
                return true;
            }
            entry.setValue(left);
            return false;
        });
    }

    private void tickTemporaryStates() {
        if (shieldTicks > 0) {
            shieldTicks--;
        }
        if (invulnTicks > 0) {
            invulnTicks--;
        }
        if (guardTicks > 0) {
            guardTicks--;
        }
        if (enrageTicks > 0) {
            enrageTicks--;
        }
        damageBurstLog.removeIf(entry -> npc.tickCount - entry[0] > DAMAGE_BURST_WINDOW_TICKS);
    }

    private void tickUntargetable() {
        if (untargetableTicks <= 0) {
            return;
        }
        untargetableTicks--;
        for (LivingEntity candidate : engaged) {
            if (candidate instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == npc) {
                mob.setTarget(null);
            }
        }
    }

    private void pruneSummons() {
        if (summons.isEmpty()) {
            return;
        }
        int before = summons.size();
        summons.removeIf(uuid -> {
            net.minecraft.world.entity.Entity entity = level.getEntity(uuid);
            return entity == null || !entity.isAlive();
        });
        if (summons.size() < before) {
            evAddDied = true;
            if (summons.isEmpty()) {
                evAllAddsDead = true;
            }
        }
    }

    private float damageInWindow() {
        float total = 0.0F;
        for (int[] entry : damageBurstLog) {
            if (npc.tickCount - entry[0] <= DAMAGE_BURST_WINDOW_TICKS) {
                total += entry[1];
            }
        }
        return total;
    }

    private void clearTickEvents() {
        evCombatStart = false;
        evTargetAcquired = false;
        evTargetLost = false;
        evTookDamage = false;
        evDealtDamage = false;
        evKilledTarget = false;
        evAddDied = false;
        evAllAddsDead = false;
        evInterrupted = false;
        evTargetAction = false;
        evStatusApplied = false;
    }

    private void faceEntity(LivingEntity entity) {
        double dx = entity.getX() - npc.getX();
        double dz = entity.getZ() - npc.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        npc.setYRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setYHeadRot(yaw);
    }

    private UUID targetId(LivingEntity target) {
        return target == null ? null : target.getUUID();
    }

    // ---- Runtime-only records for scheduled work and lingering effects ----

    private record Scheduled(NpcCombatAction action, UUID targetId, int fireTick) {
    }

    private static final class Telegraph {
        final List<net.minecraft.world.phys.Vec3> points;
        final double radius;
        int ticksRemaining;
        final String particleId;

        Telegraph(List<net.minecraft.world.phys.Vec3> points, double radius, int ticks, String particleId) {
            this.points = points;
            this.radius = radius;
            this.ticksRemaining = ticks;
            this.particleId = particleId;
        }

        void tick(AdvancedCombatEngine engine) {
            CombatActionExecutor.spawnMarkerRing(engine.level(), points, radius, particleId);
        }
    }

    private static final class Hazard {
        final List<net.minecraft.world.phys.Vec3> points;
        final double radius;
        int ticksRemaining;
        final float damagePerTick;
        final java.util.List<net.lunacore.luna_npc.api.NpcAttackEffect> effects;

        Hazard(List<net.minecraft.world.phys.Vec3> points, double radius, int ticks, float damagePerTick,
               java.util.List<net.lunacore.luna_npc.api.NpcAttackEffect> effects) {
            this.points = points;
            this.radius = radius;
            this.ticksRemaining = ticks;
            this.damagePerTick = damagePerTick;
            this.effects = effects;
        }

        void tick(AdvancedCombatEngine engine) {
            CombatActionExecutor.applyHazardTick(engine, points, radius, damagePerTick, effects);
        }
    }
}
