package net.lunacore.luna_npc.job;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.LunaNpcChat;
import net.lunacore.luna_npc.alliance.NpcAllianceManager;
import net.lunacore.luna_npc.api.LunaNpcEvents;
import net.lunacore.luna_npc.api.NpcBodyguardMode;
import net.lunacore.luna_npc.api.NpcBodyguardRelease;
import net.lunacore.luna_npc.api.NpcBodyguardSettings;
import net.lunacore.luna_npc.api.NpcBodyguardSource;
import net.lunacore.luna_npc.api.NpcJobType;
import net.lunacore.luna_npc.api.NpcPlayerRef;
import net.lunacore.luna_npc.api.NpcProtectTargets;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.npc.NpcValidation;

// The BODYGUARD job. The guard locks onto one protectee — the closest protect target within range
// (TARGETS) or its hirer — and keeps it until it gives up: dismissed, hire time over, the protectee died
// (unless a player it keeps protecting through death), is gone, or left its range or dimension without
// teleporting. Only a hirer may order it to follow, stay or go home. It stays within its stay radius of
// the protectee (never on its spot), runs to catch up and walks once close, and drifts around a
// standing-still (AFK) protectee. BodyguardEvents reports hits on and by the protectee, which become
// fights here; retreat drops a fight that drags it too far away. Ticked from NpcEntity after patrol.
public final class NpcBodyguardController {

    private static final String HIRER_KEY = "luna_bodyguard_hirer";
    private static final String HIRER_NAME_KEY = "luna_bodyguard_hirer_name";
    private static final String HIRE_ENDS_KEY = "luna_bodyguard_hire_ends";
    private static final String MODE_KEY = "luna_bodyguard_mode";
    private static final String STAY_KEY = "luna_bodyguard_stay";
    private static final String HOMEWARD_KEY = "luna_bodyguard_homeward";
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int FOLLOW_REPATH_TICKS = 10;
    private static final int HOME_REPATH_TICKS = 40;
    // A protectee that has not moved this far in AFK_TICKS counts as standing still (AFK).
    private static final double AFK_MOVE_SQ = 0.25;
    private static final int AFK_TICKS = 100;
    private static final int IDLE_MIN_TICKS = 60;
    private static final int IDLE_RANDOM_TICKS = 80;
    private static final double TOO_CLOSE = 1.0;
    // Paths run through block centres, so an idle spot closer than this can route over the target's block.
    private static final double MIN_IDLE_OFFSET = 1.75;
    private static final double HOME_ARRIVE_SQ = 2.25;
    private static final int TELEPORT_ATTEMPTS = 20;

    private final NpcEntity entity;
    // The lock. It outlives a dead player protectee while keepAfterDeath waits for the respawn.
    private UUID protecteeId;
    private String protecteeName = "";
    private boolean protecteeIsPlayer;
    private boolean hired;
    private long hireEndsAt = -1L;
    // The protectee resolved this tick; null while waiting for a player to respawn.
    private LivingEntity protectee;
    private NpcBodyguardMode mode = NpcBodyguardMode.FOLLOW;
    private BlockPos stayPos;
    // Gave up with GO_HOME and is still walking back; it picks nobody new until it arrives.
    private boolean homeward;
    private LivingEntity defenseTarget;
    private long defenseEndsAt = -1L;
    private boolean retreating;
    private Vec3 protecteeRestPos;
    private int protecteeStillTicks;
    private int idleCooldown;
    private int scanCooldown;
    private int repathCooldown;

    public NpcBodyguardController(NpcEntity entity) {
        this.entity = entity;
    }

    private boolean isBodyguardJob() {
        return entity.jobSettings().jobType() == NpcJobType.BODYGUARD && entity.jobActive();
    }

    private NpcBodyguardSettings settings() {
        return entity.jobSettings().bodyguard();
    }

    // True while the guard owns the NPC's movement, so the wander controller stays out of the way.
    public boolean isWorking() {
        return isBodyguardJob() && (protecteeId != null || homeward);
    }

    // True while it watches a moving protectee, so the idle look-around does not turn it away.
    public boolean ownsLook() {
        return isBodyguardJob() && protectee != null && mode == NpcBodyguardMode.FOLLOW
                && protecteeStillTicks < AFK_TICKS;
    }

    public boolean isRetreating() {
        return retreating;
    }

    public UUID hirer() {
        return hired ? protecteeId : null;
    }

    public String protecteeName() {
        return protecteeName;
    }

    public boolean isLocked() {
        return protecteeId != null;
    }

    // True when friendly fire is off and the attacker is one it guards.
    public boolean ignoresHitFrom(LivingEntity attacker) {
        return isBodyguardJob() && !settings().friendlyFire() && isGuarding(attacker);
    }

    public NpcBodyguardMode mode() {
        return mode;
    }

    public LivingEntity protectee() {
        return protectee;
    }

    // Seconds of hire left; -1 when not hired or hired until it gives up.
    public long remainingHireSeconds() {
        if (!hired || hireEndsAt < 0) {
            return -1L;
        }
        return Math.max(0L, (hireEndsAt - entity.level().getGameTime()) / 20L);
    }

    // Whether this guard must never be turned against the entity: its protectee, or any protect target.
    public boolean isGuarding(LivingEntity candidate) {
        if (!isBodyguardJob() || candidate == null) {
            return false;
        }
        if (protecteeId != null && candidate.getUUID().equals(protecteeId)) {
            return true;
        }
        return settings().source() == NpcBodyguardSource.TARGETS && matchesProtectTargets(candidate);
    }

    // ---- The lock ----

    // Hires the guard for a player (seconds < 0 = the job's hire time, 0 = until it gives up), replacing
    // whoever it guards now. The same player hiring again only renews the time.
    public void hire(UUID player, String name, int seconds) {
        if (protecteeId != null && !player.equals(protecteeId)) {
            release(NpcBodyguardRelease.REPLACED);
        }
        boolean fresh = protecteeId == null;
        int duration = seconds < 0 ? settings().hire().hireSeconds() : seconds;
        protecteeId = player;
        protecteeName = name == null ? "" : name;
        protecteeIsPlayer = true;
        hired = true;
        hireEndsAt = duration > 0 ? entity.level().getGameTime() + duration * 20L : -1L;
        homeward = false;
        if (fresh) {
            mode = NpcBodyguardMode.FOLLOW;
            stayPos = null;
            protectee = onlinePlayer(player);
            protecteeRestPos = null;
            LunaNpcEvents.fireBodyguardLocked(entity, protectee, true);
        }
    }

    private void lockOn(LivingEntity target) {
        protecteeId = target.getUUID();
        protecteeName = target.getName().getString();
        protecteeIsPlayer = target instanceof Player;
        hired = false;
        hireEndsAt = -1L;
        mode = NpcBodyguardMode.FOLLOW;
        stayPos = null;
        protectee = target;
        protecteeRestPos = null;
        LunaNpcEvents.fireBodyguardLocked(entity, target, false);
    }

    // Lets go of the protectee, tells a hirer why, and runs the give-up action (not for REPLACED). The
    // NPC may be discarded afterwards (DESPAWN).
    public void release(NpcBodyguardRelease reason) {
        if (protecteeId == null) {
            return;
        }
        UUID former = protecteeId;
        ServerPlayer formerHirer = hired ? onlinePlayer(former) : null;
        protecteeId = null;
        protecteeName = "";
        protecteeIsPlayer = false;
        hired = false;
        hireEndsAt = -1L;
        protectee = null;
        mode = NpcBodyguardMode.FOLLOW;
        stayPos = null;
        retreating = false;
        dropDefense();
        entity.navigation().stop();
        scanCooldown = SCAN_INTERVAL_TICKS;
        if (formerHirer != null && reason != NpcBodyguardRelease.REPLACED) {
            LunaNpcChat.send(formerHirer, Component.translatable(
                    "message.luna_npc.bodyguard_released." + reason.name().toLowerCase(Locale.ROOT),
                    LunaNpcChat.highlight(entity.getName().getString())));
        }
        LunaNpcEvents.fireBodyguardReleased(entity, former, reason);
        if (reason == NpcBodyguardRelease.REPLACED) {
            return;
        }
        switch (settings().giveUp()) {
            case STAY -> { }
            case GO_HOME -> homeward = true;
            case DESPAWN -> entity.discard();
        }
    }

    // A hirer's order; ignored while it isn't hired.
    public void order(NpcBodyguardMode order) {
        if (!hired) {
            return;
        }
        mode = order;
        stayPos = order == NpcBodyguardMode.STAY ? entity.blockPosition() : null;
        retreating = false;
        entity.navigation().stop();
    }

    // ---- Defense, reported by BodyguardEvents ----

    // A TARGETS guard without a protectee locks onto a protect target that is hurt within its range.
    public void onProtectedHurt(LivingEntity victim, LivingEntity attacker) {
        if (protecteeId == null && !homeward && settings().source() == NpcBodyguardSource.TARGETS
                && victim.isAlive() && isWithinReach(victim) && matchesProtectTargets(victim)) {
            lockOn(victim);
        }
        if (protectee == victim && isWithinReach(victim)) {
            defend(attacker);
        }
    }

    public void onProtectedAttacked(LivingEntity attackerProtectee, LivingEntity victim) {
        if (settings().assistAttacks() && protectee == attackerProtectee && isWithinReach(attackerProtectee)) {
            defend(victim);
        }
    }

    private boolean isWithinReach(LivingEntity protectedEntity) {
        return protectedEntity.level() == entity.level() && entity.distanceTo(protectedEntity) <= settings().range();
    }

    private void defend(LivingEntity threat) {
        if (threat == null || threat == entity || isGuarding(threat) || retreating || mode == NpcBodyguardMode.HOME) {
            return;
        }
        if (entity.forceAttack(threat)) {
            defenseTarget = threat;
            int seconds = settings().defenseSeconds();
            defenseEndsAt = seconds > 0 ? entity.level().getGameTime() + seconds * 20L : -1L;
        }
    }

    private void dropDefense() {
        if (defenseTarget != null && entity.combatController().target() == defenseTarget) {
            entity.combatController().disengage();
        }
        defenseTarget = null;
        defenseEndsAt = -1L;
    }

    // ---- Tick ----

    public void tick() {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isBodyguardJob()) {
            BodyguardEvents.untrack(entity);
            protectee = null;
            defenseTarget = null;
            retreating = false;
            return;
        }
        BodyguardEvents.track(entity);
        NpcBodyguardSettings settings = settings();
        if (hired && hireEndsAt >= 0 && level.getGameTime() >= hireEndsAt) {
            release(NpcBodyguardRelease.EXPIRED);
        }
        if (protecteeId == null && !entity.isRemoved()) {
            if (homeward) {
                homeward = !walkHome();
                return;
            }
            if (settings.source() == NpcBodyguardSource.TARGETS && --scanCooldown <= 0) {
                scanCooldown = SCAN_INTERVAL_TICKS + (entity.getId() & 7);
                LivingEntity closest = closestProtectTarget(level, settings);
                if (closest != null) {
                    lockOn(closest);
                }
            }
        }
        if (protecteeId == null || entity.isRemoved()) {
            return;
        }
        protectee = resolveProtectee(level, settings);
        if (protecteeId == null || entity.isRemoved()) {
            return;
        }
        tickDefense(settings);
        if (protectee == null) {
            return;
        }
        switch (mode) {
            case HOME -> walkHome();
            case STAY -> tickStay(settings);
            case FOLLOW -> {
                if (!entity.combatController().isFighting() || retreating) {
                    tickFollow(settings);
                }
            }
        }
    }

    // The protectee this tick, releasing the lock when it died, is gone, or (following, out of a fight,
    // without teleporting) left the range or dimension; null too while a dead player is awaited.
    private LivingEntity resolveProtectee(ServerLevel level, NpcBodyguardSettings settings) {
        LivingEntity found;
        if (protecteeIsPlayer) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(protecteeId);
            if (player == null) {
                release(NpcBodyguardRelease.GONE);
                return null;
            }
            protecteeName = player.getGameProfile().name();
            if (player.isDeadOrDying()) {
                if (!settings.keepAfterDeath()) {
                    release(NpcBodyguardRelease.DIED);
                }
                return null;
            }
            found = player;
        } else {
            found = protectee != null && !protectee.isRemoved() ? protectee : findInAnyLevel(level);
            if (found == null || found.isDeadOrDying()) {
                release(found == null && (protectee == null || !protectee.isDeadOrDying())
                        ? NpcBodyguardRelease.GONE : NpcBodyguardRelease.DIED);
                return null;
            }
        }
        if (mode != NpcBodyguardMode.FOLLOW) {
            return found;
        }
        if (found.level() != level) {
            if (settings.teleportDistance() > 0) {
                teleportNear(found);
            } else {
                release(NpcBodyguardRelease.OUT_OF_RANGE);
            }
            return null;
        }
        if (!entity.combatController().isFighting() || retreating) {
            double distance = entity.distanceTo(found);
            if (settings.teleportDistance() > 0) {
                if (distance > settings.teleportDistance()) {
                    teleportNear(found);
                }
            } else if (distance > settings.range()) {
                release(NpcBodyguardRelease.OUT_OF_RANGE);
                return null;
            }
        }
        return found;
    }

    // A non-player protectee that went through a portal is a new entity with the same UUID elsewhere.
    private LivingEntity findInAnyLevel(ServerLevel level) {
        for (ServerLevel candidate : level.getServer().getAllLevels()) {
            Entity found = candidate.getEntity(protecteeId);
            if (found instanceof LivingEntity living && !living.isRemoved()) {
                return living;
            }
        }
        return null;
    }

    private LivingEntity closestProtectTarget(ServerLevel level, NpcBodyguardSettings settings) {
        NpcProtectTargets targets = settings.protect();
        double radius = settings.range();
        LivingEntity best = null;
        double bestDistance = radius * radius;
        for (Player player : level.players()) {
            double distance = entity.distanceToSqr(player);
            if (distance <= bestDistance && !player.isSpectator() && player.isAlive() && matchesPlayer(targets, player)) {
                best = player;
                bestDistance = distance;
            }
        }
        if (targets.allianceIds().isEmpty() && targets.npcLunaIds().isEmpty()) {
            return best;
        }
        AABB box = entity.getBoundingBox().inflate(radius);
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box,
                other -> other != entity && other.isAlive() && !(other instanceof Player))) {
            double distance = entity.distanceToSqr(candidate);
            if (distance <= bestDistance && matchesProtectTargets(candidate)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean matchesProtectTargets(LivingEntity candidate) {
        if (candidate == entity) {
            return false;
        }
        NpcProtectTargets targets = settings().protect();
        if (candidate instanceof Player player) {
            return matchesPlayer(targets, player);
        }
        if (candidate instanceof NpcEntity npc && targets.npcLunaIds().contains(npc.lunaId())) {
            return true;
        }
        return inProtectedAlliance(targets, candidate);
    }

    private boolean matchesPlayer(NpcProtectTargets targets, Player player) {
        for (NpcPlayerRef ref : targets.players()) {
            if (ref.id().equals(player.getUUID())) {
                return true;
            }
        }
        return inProtectedAlliance(targets, player);
    }

    private boolean inProtectedAlliance(NpcProtectTargets targets, LivingEntity candidate) {
        if (targets.allianceIds().isEmpty() || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        Set<String> memberships = NpcAllianceManager.get(level.getServer()).alliancesOf(candidate);
        return targets.allianceIds().stream().anyMatch(memberships::contains);
    }

    private void tickDefense(NpcBodyguardSettings settings) {
        if (defenseTarget != null && (!defenseTarget.isAlive() || defenseTarget.isRemoved()
                || (defenseEndsAt >= 0 && entity.level().getGameTime() >= defenseEndsAt))) {
            dropDefense();
        }
        Vec3 guardPoint = guardPoint();
        if (settings.retreat() && guardPoint != null && entity.combatController().isFighting()
                && entity.position().distanceTo(guardPoint) > settings.retreatDistance()) {
            entity.combatController().disengage();
            defenseTarget = null;
            retreating = true;
        }
        if (retreating && (guardPoint == null || entity.position().distanceTo(guardPoint) <= settings.walkWithin())) {
            retreating = false;
        }
    }

    // What the guard stays near: its post when ordered to stay, otherwise the protectee.
    private Vec3 guardPoint() {
        if (mode == NpcBodyguardMode.STAY && stayPos != null) {
            return Vec3.atBottomCenterOf(stayPos);
        }
        return protectee == null ? null : protectee.position();
    }

    private void tickFollow(NpcBodyguardSettings settings) {
        double distance = horizontalDistance(protectee.position());
        updateRestState();
        if (distance > settings.stayRadius()) {
            idleCooldown = 0;
            if (--repathCooldown <= 0 || !entity.navigation().isNavigating()) {
                boolean run = retreating || distance > settings.walkWithin();
                entity.navigation().moveTo(spotBeside(protectee.position(), settings.stayRadius()), run, false);
                repathCooldown = FOLLOW_REPATH_TICKS;
            }
            return;
        }
        if (distance < TOO_CLOSE && !entity.navigation().isNavigating()) {
            entity.navigation().moveTo(spotBeside(protectee.position(), settings.stayRadius()), false, false);
            return;
        }
        if (protecteeStillTicks >= AFK_TICKS) {
            if (!entity.navigation().isNavigating() && --idleCooldown <= 0) {
                entity.navigation().moveTo(randomSpotAround(protectee.position(), settings.stayRadius()), false, false);
                idleCooldown = IDLE_MIN_TICKS + entity.getRandom().nextInt(IDLE_RANDOM_TICKS);
            }
            return;
        }
        if (!entity.navigation().isNavigating()) {
            entity.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, protectee.getEyePosition());
        }
    }

    private void updateRestState() {
        Vec3 position = protectee.position();
        if (protecteeRestPos == null || protecteeRestPos.distanceToSqr(position) > AFK_MOVE_SQ) {
            protecteeRestPos = position;
            protecteeStillTicks = 0;
        } else {
            protecteeStillTicks++;
        }
    }

    private void tickStay(NpcBodyguardSettings settings) {
        if (stayPos == null) {
            stayPos = entity.blockPosition();
        }
        if (entity.combatController().isFighting() && !retreating) {
            return;
        }
        Vec3 post = Vec3.atBottomCenterOf(stayPos);
        if (horizontalDistance(post) > settings.stayRadius() && !entity.navigation().isNavigating()
                && --repathCooldown <= 0) {
            entity.navigation().moveTo(post, retreating, false);
            repathCooldown = HOME_REPATH_TICKS;
        }
    }

    // True once it stands at home.
    private boolean walkHome() {
        Vec3 home = entity.homeAnchor();
        if (entity.position().distanceToSqr(home) <= HOME_ARRIVE_SQ) {
            return true;
        }
        if (!entity.combatController().isFighting() && !entity.navigation().isNavigating() && --repathCooldown <= 0) {
            entity.navigation().moveTo(home, false, false);
            repathCooldown = HOME_REPATH_TICKS;
        }
        return false;
    }

    // A spot on the near side of the target, inside the stay radius but never on its exact spot.
    private Vec3 spotBeside(Vec3 target, int stayRadius) {
        double dx = entity.getX() - target.x;
        double dz = entity.getZ() - target.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-3) {
            double angle = entity.getRandom().nextDouble() * Math.PI * 2.0;
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            length = 1.0;
        }
        double offset = Math.max(MIN_IDLE_OFFSET, stayRadius * 0.6);
        return new Vec3(target.x + dx / length * offset, target.y, target.z + dz / length * offset);
    }

    // A random spot on the guard's own side of the target, so drifting around never walks it across the
    // target's spot.
    private Vec3 randomSpotAround(Vec3 target, int stayRadius) {
        double side = Math.atan2(entity.getZ() - target.z, entity.getX() - target.x);
        double angle = side + (entity.getRandom().nextDouble() - 0.5) * Math.PI * 0.5;
        double offset = MIN_IDLE_OFFSET + entity.getRandom().nextDouble() * Math.max(0.0, stayRadius - MIN_IDLE_OFFSET);
        return new Vec3(target.x + Math.cos(angle) * offset, target.y, target.z + Math.sin(angle) * offset);
    }

    // Snaps to a free spot 1 to 3 blocks from the target (any dimension), like a tamed wolf catching up.
    private void teleportNear(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel targetLevel)) {
            return;
        }
        int reach = NpcValidation.BODYGUARD_TELEPORT_LANDING_RADIUS;
        BlockPos base = target.blockPosition();
        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            int dx = entity.getRandom().nextInt(reach * 2 + 1) - reach;
            int dz = entity.getRandom().nextInt(reach * 2 + 1) - reach;
            int horizontal = dx * dx + dz * dz;
            BlockPos candidate = base.offset(dx, entity.getRandom().nextInt(3) - 1, dz);
            if (horizontal < 1 || horizontal > reach * reach || !isFreeStandingSpot(targetLevel, candidate)) {
                continue;
            }
            entity.navigation().stop();
            dropDefense();
            entity.teleport(new TeleportTransition(targetLevel, Vec3.atBottomCenterOf(candidate), Vec3.ZERO,
                    entity.getYRot(), entity.getXRot(), TeleportTransition.DO_NOTHING));
            return;
        }
    }

    private boolean isFreeStandingSpot(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                && level.noCollision(entity, entity.getDimensions(entity.getPose())
                        .makeBoundingBox(Vec3.atBottomCenterOf(feet)));
    }

    private double horizontalDistance(Vec3 target) {
        double dx = target.x - entity.getX();
        double dz = target.z - entity.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private ServerPlayer onlinePlayer(UUID id) {
        if (id == null || !(entity.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(id);
    }

    // Only a hire survives a save; a picked protect target is simply picked again.
    public void save(ValueOutput output) {
        output.storeNullable(HIRER_KEY, UUIDUtil.CODEC, hirer());
        output.putString(HIRER_NAME_KEY, hired ? protecteeName : "");
        output.putLong(HIRE_ENDS_KEY, hired ? hireEndsAt : -1L);
        output.putString(MODE_KEY, mode.getSerializedName());
        output.storeNullable(STAY_KEY, BlockPos.CODEC, stayPos);
        output.putBoolean(HOMEWARD_KEY, homeward);
    }

    public void load(ValueInput input) {
        protecteeId = input.read(HIRER_KEY, UUIDUtil.CODEC).orElse(null);
        hired = protecteeId != null;
        protecteeIsPlayer = hired;
        protecteeName = input.getStringOr(HIRER_NAME_KEY, "");
        hireEndsAt = input.getLongOr(HIRE_ENDS_KEY, -1L);
        String storedMode = input.getStringOr(MODE_KEY, NpcBodyguardMode.FOLLOW.getSerializedName());
        mode = hired ? java.util.Arrays.stream(NpcBodyguardMode.values())
                .filter(value -> value.getSerializedName().equals(storedMode))
                .findFirst().orElse(NpcBodyguardMode.FOLLOW) : NpcBodyguardMode.FOLLOW;
        stayPos = input.read(STAY_KEY, BlockPos.CODEC).orElse(null);
        homeward = input.getBooleanOr(HOMEWARD_KEY, false);
    }
}
