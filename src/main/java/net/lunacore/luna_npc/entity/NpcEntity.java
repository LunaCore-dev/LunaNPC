package net.lunacore.luna_npc.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.combat.NpcCombatController;
import net.lunacore.luna_npc.api.NpcBossBarSettings;
import net.lunacore.luna_npc.api.NpcCombatSettings;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.api.NpcDropSettings;
import net.lunacore.luna_npc.npc.NpcEquipment;
import net.lunacore.luna_npc.api.NpcEquipmentSettings;
import net.lunacore.luna_npc.api.NpcItemEnchant;
import net.lunacore.luna_npc.api.NpcJobSettings;
import net.lunacore.luna_npc.api.NpcJobType;
import net.lunacore.luna_npc.job.DialogEvents;
import net.lunacore.luna_npc.job.DialogProgressManager;
import net.lunacore.luna_npc.job.LunaNpcMenus;
import net.lunacore.luna_npc.job.NpcBodyguardController;
import net.lunacore.luna_npc.job.NpcFarmController;
import net.lunacore.luna_npc.job.NpcPatrolController;
import net.lunacore.luna_npc.job.QuestEvents;
import net.lunacore.luna_npc.api.NpcSpawnSettings;
import net.lunacore.luna_npc.npc.NpcModelCatalog;
import net.lunacore.luna_npc.api.NpcModelSettings;
import net.lunacore.luna_npc.api.NpcMovementSettings;
import net.lunacore.luna_npc.api.NpcTraitsSettings;
import net.lunacore.luna_npc.npc.NpcValidation;
import net.lunacore.luna_npc.pathfinding.NpcNavigation;
import net.lunacore.luna_npc.pathfinding.WanderController;
import net.lunacore.luna_npc.api.NpcZone;
import net.lunacore.luna_npc.api.NpcZoneSpawn;
import net.lunacore.luna_npc.zone.ZoneManager;

public class NpcEntity extends Mannequin implements net.lunacore.luna_npc.api.NpcInstance {

    private static final EntityDataAccessor<String> DATA_MODEL_TYPE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_MODEL_ID =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_HEAD_SCALE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BODY_SCALE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LEFT_ARM_SCALE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RIGHT_ARM_SCALE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LEFT_LEG_SCALE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RIGHT_LEG_SCALE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    // The collision box measured from a custom-geometry model's mesh; 0 for every built-in type, which
    // then falls back to the vanilla-derived box math below. Synced so every client boxes the NPC the
    // same even without the local geometry file.
    private static final EntityDataAccessor<Float> DATA_CUSTOM_WIDTH =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CUSTOM_HEIGHT =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CUSTOM_EYE_HEIGHT =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    // Synced so the client renderer can drive vanilla combat poses (a zombie model's arms-forward
    // stance) off it; true whenever the NPC currently has a combat target.
    private static final EntityDataAccessor<Boolean> DATA_AGGRESSIVE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);

    // Humanoid model-space sizes in pixels (16 px = 1 block): head cube is 8x8x8, so stretching it
    // raises the top by 8 px per scale unit and widens it by 4 px of half-width; body half-width is
    // 4 px; an arm's outer edge sits 3 px outside its pivot at x=+-5. Leg length is per model type
    // (see NpcModelCatalog.legPixels).
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float HEAD_PIXELS = 8.0F;
    private static final float HEAD_HALF_WIDTH_PIXELS = 4.0F;
    private static final float BODY_HALF_WIDTH_PIXELS = 4.0F;
    private static final float ARM_OUTER_PIXELS = 3.0F;
    private static final float MIN_HEIGHT = 0.2F;

    private long lunaId = -1;
    private String hurtSoundId = NpcDefaults.HURT_SOUND_ID;
    private String dieSoundId = NpcDefaults.DIE_SOUND_ID;
    private String ambientSoundId = NpcDefaults.AMBIENT_SOUND_ID;
    private String aggroSoundId = NpcDefaults.AGGRO_SOUND_ID;
    private String combatCloseSoundId = NpcDefaults.COMBAT_CLOSE_SOUND_ID;
    private String combatFarSoundId = NpcDefaults.COMBAT_FAR_SOUND_ID;
    private String closeImpactSoundId = NpcDefaults.CLOSE_IMPACT_SOUND_ID;
    private String farImpactSoundId = NpcDefaults.FAR_IMPACT_SOUND_ID;
    private int ambientIntervalSeconds = NpcDefaults.AMBIENT_INTERVAL_SECONDS;
    private double lunaHealth = NpcDefaults.HEALTH;
    private double lunaMaxHealth = NpcDefaults.HEALTH;
    // Temporary navigation medium ("ground"/"water"/"air") an advanced-combat rule forces, its remaining
    // ticks, and the flight-height / swim-depth preference the node chose for it (so a normally-ground NPC
    // told to fly still has a height to hold). Null medium = the NPC moves in its configured medium.
    private String navModeOverride;
    private int navModeOverrideTicks;
    private int navModeOverrideHeight = NpcDefaults.HEIGHT_RANGE;
    private boolean navModeOverrideFixed;
    private int ambientSoundCooldown = -1;
    private NpcDropSettings dropSettings = NpcDropSettings.defaults();
    private NpcBossBarSettings bossBarSettings = NpcBossBarSettings.defaults();
    private NpcMovementSettings movementSettings = NpcMovementSettings.defaults();
    private NpcTraitsSettings traitsSettings = NpcTraitsSettings.defaults();
    private NpcCombatSettings combatSettings = NpcCombatSettings.defaults();
    private NpcSpawnSettings spawnSettings = NpcSpawnSettings.defaults();
    private NpcJobSettings jobSettings = NpcJobSettings.defaults();
    // Which sub-type variant of its job this instance rolled at spawn (-1 = none), fixed for its
    // lifetime. jobSuppressed marks a natural spawn that dropped a farming job.
    private int rolledJobSubType = -1;
    private boolean jobSuppressed;
    private int interactionBlockAllTicks;
    private final java.util.Map<java.util.UUID, Integer> interactionBlockByPlayer = new java.util.HashMap<>();
    // A player interacting with a job NPC halts its walking so it can't wander out of its own menu.
    private static final int INTERACTION_HOLD_TICKS = 100;
    // While any nearby player is mid-dialog the NPC keeps holding, so it never wanders off during a
    // conversation; the set is transient and pruned by range/disconnect each tick.
    private static final double DIALOG_HOLD_RANGE_SQ = 6.0 * 6.0;
    private int interactionHoldTicks;
    private final java.util.Set<java.util.UUID> dialogPlayers = new java.util.HashSet<>();
    private java.util.List<String> allianceIds = java.util.List.of(NpcDefaults.ALLIANCE);
    // Where a world-spawned or respawned NPC first appeared; a respawn returns here unless the
    // NPC is set to respawn at its death spot. Manual spawns leave it null and fall back to their
    // current position on death.
    private BlockPos spawnOrigin;
    private boolean gearAffectsStats;
    // Whether this NPC entered the world as a wild natural spawn (vs hand-placed, API or respawn). Set
    // at spawn and NBT-persisted, it decides — read live, so a Spawn-tab edit takes effect at once —
    // which persistence toggle governs despawning: the Spawn tab's for a natural spawn, the general one
    // otherwise. noNearbyPlayerTicks counts how long it has gone without a close player.
    private boolean naturallySpawned;
    private int noNearbyPlayerTicks;
    // Transient regen bookkeeping (never persisted): ticks since the last real hit, and one elapsed-tick
    // accumulator per regen rule (reallocated when the rule count changes).
    private int ticksSinceHurt;
    private int[] regenRuleTicks = new int[0];
    private String rangedWeaponId = "";
    private java.util.List<net.lunacore.luna_npc.api.NpcItemEnchant> rangedWeaponEnchants = java.util.List.of();
    private String mainHandId = "";
    private java.util.List<net.lunacore.luna_npc.api.NpcItemEnchant> mainHandEnchants = java.util.List.of();
    // Which weapon the hand currently shows. Null until first decided (or after a reload) so the next
    // range decision always re-seats the hand; otherwise the item is only swapped when the range flips.
    private net.lunacore.luna_npc.api.NpcAttackRange shownRange;
    private final NpcNavigation navigation = new NpcNavigation(this);
    private final WanderController wanderController = new WanderController(this);
    private final net.lunacore.luna_npc.pathfinding.IdleLookController idleLookController =
            new net.lunacore.luna_npc.pathfinding.IdleLookController(this);
    private final NpcCombatController combatController = new NpcCombatController(this);
    private final NpcFarmController farmController = new NpcFarmController(this);
    private final NpcPatrolController patrolController = new NpcPatrolController(this);
    private final NpcBodyguardController bodyguardController = new NpcBodyguardController(this);
    private final net.lunacore.luna_npc.chunk.NpcChunkLoader chunkLoader =
            new net.lunacore.luna_npc.chunk.NpcChunkLoader(this);
    private ServerBossEvent bossEvent;
    // Once-per-load flag: a loaded NPC re-pulls its definition from the registry so edits made while
    // it was unloaded still take effect (the registry is the source of truth for a lunaId).
    private boolean definitionSynced;

    @SuppressWarnings("unchecked")
    public NpcEntity(EntityType<NpcEntity> type, Level level) {
        super((EntityType<Mannequin>) (EntityType<?>) type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODEL_TYPE, NpcDefaults.MODEL_TYPE);
        builder.define(DATA_MODEL_ID, NpcDefaults.MODEL_ID);
        builder.define(DATA_HEAD_SCALE, NpcDefaults.PART_SCALE);
        builder.define(DATA_BODY_SCALE, NpcDefaults.PART_SCALE);
        builder.define(DATA_LEFT_ARM_SCALE, NpcDefaults.PART_SCALE);
        builder.define(DATA_RIGHT_ARM_SCALE, NpcDefaults.PART_SCALE);
        builder.define(DATA_LEFT_LEG_SCALE, NpcDefaults.PART_SCALE);
        builder.define(DATA_RIGHT_LEG_SCALE, NpcDefaults.PART_SCALE);
        builder.define(DATA_CUSTOM_WIDTH, 0.0F);
        builder.define(DATA_CUSTOM_HEIGHT, 0.0F);
        builder.define(DATA_CUSTOM_EYE_HEIGHT, 0.0F);
        builder.define(DATA_AGGRESSIVE, false);
    }

    // Client-visible combat state: drives vanilla model attack poses (see NpcRenderer).
    public boolean isCombatAggressive() {
        return entityData.get(DATA_AGGRESSIVE);
    }

    public long lunaId() {
        return lunaId;
    }

    public NpcNavigation navigation() {
        return navigation;
    }

    public NpcMovementSettings movementSettings() {
        return movementSettings;
    }

    public NpcTraitsSettings traitsSettings() {
        return traitsSettings;
    }

    public NpcCombatSettings combatSettings() {
        return combatSettings;
    }

    public NpcSpawnSettings spawnSettings() {
        return spawnSettings;
    }

    public NpcJobSettings jobSettings() {
        return jobSettings;
    }

    @Override
    public int rolledJobSubType() {
        return jobSuppressed ? -1 : rolledJobSubType;
    }

    @Override
    public boolean jobSuppressed() {
        return jobSuppressed;
    }

    public boolean jobActive() {
        if (jobSuppressed || jobSettings.jobType() == NpcJobType.NONE) {
            return false;
        }
        // Dialog is fixed, not rolled: it's live as soon as its tree has a reachable start node.
        if (jobSettings.jobType() == NpcJobType.DIALOG) {
            return jobSettings.dialog().hasStart();
        }
        if (jobSettings.jobType() == NpcJobType.BODYGUARD) {
            return true;
        }
        return rolledJobSubType >= 0;
    }

    // Rolls which sub-type variant this instance works, fixed for its lifetime. Farming and patrol are
    // manual-spawn only, so a natural spawn keeps the NPC but drops the job.
    public void rollJobSubType(RandomSource random, EntitySpawnReason reason) {
        if (jobSettings.jobType() == NpcJobType.NONE) {
            return;
        }
        if (reason == EntitySpawnReason.NATURAL && isManualSpawnOnlyJob(jobSettings.jobType())) {
            jobSuppressed = true;
            return;
        }
        rolledJobSubType = NpcJobSettings.rollSubTypeIndex(jobSettings.liveSubTypeWeights(), random);
    }

    private static boolean isManualSpawnOnlyJob(NpcJobType type) {
        return type == NpcJobType.FARMING || type == NpcJobType.PATROL;
    }

    public void setSpawnOrigin(BlockPos origin) {
        this.spawnOrigin = origin;
    }

    public BlockPos spawnOrigin() {
        return spawnOrigin;
    }

    // Smallest home radius for a non-cube spawn, so even a static or tiny-wander NPC has a little
    // slack before the tether drags it back.
    private static final int MIN_HOME_LEASH = 16;
    // Knockback settings this close to zero count as "none": the slider can leave a float speck (e.g.
    // 2.6E-15) at the 0 mark, and a zero-knockback NPC must keep its own movement completely untouched.
    private static final float KNOCKBACK_DEADZONE = 1.0e-3F;

    public boolean leaveAreaAllowed() {
        return spawnSettings.leaveArea();
    }

    public boolean leaveAreaInCombatAllowed() {
        return spawnSettings.leaveAreaInCombat();
    }

    // True when pos is inside the area this NPC calls home. Only NPCs the world naturally spawned are
    // ever area-bound; a hand-placed or API-spawned NPC is always free, so the Spawn tab's area rules
    // never apply to it. For a natural spawn: the zone it spawned in (a configured zone whose bounds
    // hold its origin), otherwise a leash around the origin sized to its wander range.
    public boolean isInHomeArea(BlockPos pos) {
        if (!naturallySpawned || spawnOrigin == null || !(level() instanceof ServerLevel serverLevel)) {
            return true;
        }
        NpcZone zone = homeZone(serverLevel.getServer());
        if (zone != null) {
            return zone.contains(level().dimension().identifier().toString(), pos);
        }
        int leash = Math.max(MIN_HOME_LEASH, movementSettings.wander().radius());
        double dx = pos.getX() - spawnOrigin.getX();
        double dz = pos.getZ() - spawnOrigin.getZ();
        return dx * dx + dz * dz <= (double) leash * leash;
    }

    // The zone that bounds this NPC's home, or null when it isn't zone-bound.
    private NpcZone homeZone(MinecraftServer server) {
        if (spawnSettings.zoneSpawns().isEmpty()) {
            return null;
        }
        ZoneManager zones = ZoneManager.get(server);
        String dimension = level().dimension().identifier().toString();
        for (NpcZoneSpawn zoneSpawn : spawnSettings.zoneSpawns()) {
            NpcZone zone = zones.byId(zoneSpawn.zoneId());
            if (zone != null && zone.contains(dimension, spawnOrigin)) {
                return zone;
            }
        }
        return null;
    }

    // Where a strayed NPC walks back to: its spawn origin (or its current spot if it has none).
    public Vec3 homeAnchor() {
        return Vec3.atBottomCenterOf(spawnOrigin != null ? spawnOrigin : blockPosition());
    }

    public NpcCombatController combatController() {
        return combatController;
    }

    public NpcFarmController farmController() {
        return farmController;
    }

    public NpcPatrolController patrolController() {
        return patrolController;
    }

    public NpcBodyguardController bodyguardController() {
        return bodyguardController;
    }

    public net.lunacore.luna_npc.chunk.NpcChunkLoader chunkLoader() {
        return chunkLoader;
    }

    // True while a job (farm route, patrol, bodyguard) steers the NPC, so the wander controller stays out.
    public boolean jobOwnsMovement() {
        return farmController.isWorking() || patrolController.isWorking() || bodyguardController.isWorking();
    }

    @Override
    public String patrolRouteId() {
        return patrolController.routeId();
    }

    @Override
    public int patrolPointIndex() {
        return patrolController.pointIndex();
    }

    @Override
    public void setPatrolPointIndex(int pointIndex) {
        patrolController.setPointIndex(pointIndex);
    }

    @Override
    public java.util.UUID bodyguardHirer() {
        return bodyguardController.hirer();
    }

    @Override
    public void hireBodyguard(java.util.UUID playerId, String playerName, int seconds) {
        if (jobSettings.jobType() == NpcJobType.BODYGUARD && playerId != null) {
            bodyguardController.hire(playerId, playerName, seconds);
        }
    }

    @Override
    public void releaseBodyguard() {
        bodyguardController.release(net.lunacore.luna_npc.api.NpcBodyguardRelease.DISMISSED);
    }

    @Override
    public net.lunacore.luna_npc.api.NpcBodyguardMode bodyguardMode() {
        return bodyguardController.mode();
    }

    @Override
    public void setBodyguardMode(net.lunacore.luna_npc.api.NpcBodyguardMode mode) {
        if (mode != null) {
            bodyguardController.order(mode);
        }
    }

    @Override
    public net.minecraft.world.entity.LivingEntity bodyguardProtectee() {
        return bodyguardController.protectee();
    }

    @Override
    public long bodyguardRemainingSeconds() {
        return bodyguardController.remainingHireSeconds();
    }

    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> farmedItems() {
        return farmController.bufferSnapshot();
    }

    // Keeps a respawned natural farmer jobless: a MOB_SUMMONED respawn would otherwise re-roll and
    // re-grant the farming job that a natural spawn is meant to drop.
    public void suppressJob() {
        jobSuppressed = true;
        rolledJobSubType = -1;
    }

    public java.util.List<String> allianceIds() {
        return allianceIds;
    }

    // Force this NPC to engage a target now (the LunaNpcApi.requestAttack path); false if it has no
    // attack enabled to act on.
    public boolean forceAttack(net.minecraft.world.entity.LivingEntity target) {
        if (!combatSettings().melee().enabled() && !combatSettings().ranged().enabled()) {
            return false;
        }
        combatController.engage(target);
        return true;
    }

    @Override
    public net.minecraft.world.entity.LivingEntity combatTarget() {
        return combatController.target();
    }

    // Dialog SET_RELATION: hostile drives the same combat switch as retaliation and faction aggro
    // (forceAttack -> engage); friendly drops the player if the NPC is currently fighting them.
    public void setRelationHostile(net.minecraft.world.entity.LivingEntity player, boolean hostile) {
        if (hostile) {
            forceAttack(player);
        } else if (combatController.target() == player) {
            combatController.disengage();
        }
    }

    // Sends the NPC pathing to the anchor through its normal navigation; a positive radius the NPC is
    // already inside counts as arrived, so it does not shuffle for a spot it already occupies.
    public void moveToAnchor(BlockPos anchor, int radius) {
        if (anchor == null) {
            return;
        }
        Vec3 destination = Vec3.atBottomCenterOf(anchor);
        if (radius > 0 && position().distanceToSqr(destination) <= (double) radius * radius) {
            return;
        }
        navigation.moveTo(destination, false);
    }

    @Override
    public void teleport(double x, double y, double z) {
        snapTo(x, y, z, getYRot(), getXRot());
    }

    public void onCombatDamageDealt(net.minecraft.world.entity.LivingEntity victim) {
        combatController.notifyDamageDealt(victim);
        if (combatSettings.combatMode() == net.lunacore.luna_npc.api.NpcCombatMode.ADVANCED) {
            combatController.advancedEngine().onDealtDamage(victim);
        }
    }

    public double lunaHealth() {
        return lunaHealth;
    }

    public double lunaMaxHealth() {
        return lunaMaxHealth;
    }

    // Restores luna health (the NPC's real health), capped at its max — the HEAL advanced-combat action.
    public void healLuna(double amount) {
        lunaHealth = Math.min(lunaMaxHealth, lunaHealth + Math.max(0.0, amount));
        syncVanillaHealthMirror();
    }

    // The medium the NPC actually moves in right now: an advanced-combat override if one is active,
    // otherwise its configured movement type. Navigation and hovering read this, not the raw setting.
    public String effectiveMovementType() {
        return navModeOverride != null ? navModeOverride : movementSettings.movementType();
    }

    // True while an advanced-combat rule is forcing a navigation medium.
    public boolean navModeOverridden() {
        return navModeOverride != null;
    }

    public void overrideNavMode(String movementType, int ticks, int height, boolean fixed) {
        navModeOverride = movementType;
        navModeOverrideTicks = Math.max(1, ticks);
        navModeOverrideHeight = height;
        navModeOverrideFixed = fixed;
        setNoGravity("air".equals(navModeOverride));
    }

    // The flight height / swim depth navigation should hold: the value the forcing node chose while a
    // nav-mode override is active, otherwise the NPC's own configured value. Same setting, node-supplied
    // during combat — no second source of truth.
    public int effectiveFlightHeight() {
        return navModeOverride != null ? navModeOverrideHeight : movementSettings.flightHeight();
    }

    public int effectiveWaterDepth() {
        return navModeOverride != null ? navModeOverrideHeight : movementSettings.waterDepth();
    }

    public net.lunacore.luna_npc.api.NpcHeightPreference effectiveHeightPreference(boolean water) {
        if (navModeOverride != null) {
            return net.lunacore.luna_npc.api.NpcHeightPreference.defaults().withFixed(navModeOverrideFixed);
        }
        return water ? movementSettings.waterDepthPreference() : movementSettings.flightHeightPreference();
    }

    // Re-labels the boss bar mid-fight (the BOSS_BAR advanced-combat action); a no-op when the NPC has no
    // bar configured.
    public void setBossBarText(String text) {
        if (bossEvent != null && !text.isBlank()) {
            bossEvent.setName(net.minecraft.network.chat.Component.literal(text));
        }
    }

    public boolean gearAffectsStats() {
        return gearAffectsStats;
    }

    // The second primary weapon (bow, crossbow, throwable) used by the long-range attack.
    public String equipmentRangedWeaponId() {
        return rangedWeaponId;
    }

    // The ranged weapon as a real stack with its enchantments baked on, for long-range hit effects.
    public net.minecraft.world.item.ItemStack rangedWeaponStack() {
        return NpcEquipment.resolve(rangedWeaponId, rangedWeaponEnchants, level().registryAccess());
    }

    // The melee weapon as a real stack, so the hand can be swapped back to it after a long-range stretch.
    public net.minecraft.world.item.ItemStack meleeMainHandStack() {
        return NpcEquipment.resolve(mainHandId, mainHandEnchants, level().registryAccess());
    }

    // Show the weapon matching the range the NPC is fighting at — the ranged weapon at long range (when
    // one is set), the melee weapon otherwise. Only re-seats the hand slot when the range actually flips.
    public void showAttackRange(net.lunacore.luna_npc.api.NpcAttackRange range) {
        if (range == shownRange) {
            return;
        }
        shownRange = range;
        if (range == net.lunacore.luna_npc.api.NpcAttackRange.CLOSE) {
            releaseDraw();
        }
        boolean showRanged = range == net.lunacore.luna_npc.api.NpcAttackRange.LONG && !rangedWeaponId.isEmpty();
        setItemSlot(EquipmentSlot.MAINHAND, showRanged ? rangedWeaponStack() : meleeMainHandStack());
    }

    @Override
    public net.lunacore.luna_npc.api.NpcAttackRange activeAttackRange() {
        return shownRange == null ? net.lunacore.luna_npc.api.NpcAttackRange.CLOSE : shownRange;
    }

    // Bow-draw visuals: the combat controller begins the draw so the model shows the pull, and ends it
    // at the shot. releaseDraw uses stopUsingItem() (which only clears the use state), never
    // releaseUsingItem() (which would fire the bow) — the projectile stays fully simulated by
    // NpcCombatController for accuracy/homing/curve.
    public void beginDraw() {
        if (!isUsingItem()) {
            startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    public void releaseDraw() {
        if (isUsingItem()) {
            stopUsingItem();
        }
    }

    // A loaded crossbow is the charged-projectiles component on the held stack: it shows the loaded bolt and
    // drives the hold pose (see NpcRenderer). The shot itself stays simulated by NpcCombatController.
    public boolean isCrossbowLoaded() {
        return CrossbowItem.isCharged(getMainHandItem());
    }

    public void setCrossbowLoaded(boolean loaded, String projectileId) {
        ItemStack hand = getMainHandItem().copy();
        ItemStack ammo = NpcEquipment.resolve(projectileId);
        hand.set(DataComponents.CHARGED_PROJECTILES, loaded
                ? ChargedProjectiles.ofNonEmpty(java.util.List.of(ammo.isEmpty() ? new ItemStack(Items.ARROW) : ammo))
                : ChargedProjectiles.EMPTY);
        setItemSlot(EquipmentSlot.MAINHAND, hand);
    }

    // A couched spear only shows the charge: its hit lands through the combat controller (alliances, the
    // configured damage), so vanilla's own kinetic stab — which strikes anything along the line — never runs.
    @Override
    protected void updateUsingItem(ItemStack stack) {
        if (!stack.has(DataComponents.KINETIC_WEAPON)) {
            super.updateUsingItem(stack);
        } else if (useItemRemaining > 1) {
            useItemRemaining--;
        }
    }

    public String combatCloseSoundId() {
        return combatCloseSoundId;
    }

    public String combatFarSoundId() {
        return combatFarSoundId;
    }

    public String closeImpactSoundId() {
        return closeImpactSoundId;
    }

    public String farImpactSoundId() {
        return farImpactSoundId;
    }

    // A non-water NPC that cannot swim sinks and stops moving while it is in a liquid.
    public boolean isMovementBlockedByLiquid() {
        return !traitsSettings.canSwim() && !"water".equals(movementSettings.movementType())
                && (isInWater() || isInLava());
    }

    public String modelTypeId() {
        return entityData.get(DATA_MODEL_TYPE);
    }

    public String modelId() {
        return entityData.get(DATA_MODEL_ID);
    }

    public float headScale() {
        return entityData.get(DATA_HEAD_SCALE);
    }

    public float bodyScale() {
        return entityData.get(DATA_BODY_SCALE);
    }

    public float leftArmScale() {
        return entityData.get(DATA_LEFT_ARM_SCALE);
    }

    public float rightArmScale() {
        return entityData.get(DATA_RIGHT_ARM_SCALE);
    }

    public float leftLegScale() {
        return entityData.get(DATA_LEFT_LEG_SCALE);
    }

    public float rightLegScale() {
        return entityData.get(DATA_RIGHT_LEG_SCALE);
    }

    public float customWidth() {
        return entityData.get(DATA_CUSTOM_WIDTH);
    }

    public float customHeight() {
        return entityData.get(DATA_CUSTOM_HEIGHT);
    }

    public float customEyeHeight() {
        return entityData.get(DATA_CUSTOM_EYE_HEIGHT);
    }

    public void applyModel(NpcModelSettings model) {
        entityData.set(DATA_MODEL_TYPE, model.modelType());
        entityData.set(DATA_MODEL_ID, model.modelId());
        entityData.set(DATA_HEAD_SCALE, model.headScale());
        entityData.set(DATA_BODY_SCALE, model.bodyScale());
        entityData.set(DATA_LEFT_ARM_SCALE, model.leftArmScale());
        entityData.set(DATA_RIGHT_ARM_SCALE, model.rightArmScale());
        entityData.set(DATA_LEFT_LEG_SCALE, model.leftLegScale());
        entityData.set(DATA_RIGHT_LEG_SCALE, model.rightLegScale());
        entityData.set(DATA_CUSTOM_WIDTH, model.customWidth());
        entityData.set(DATA_CUSTOM_HEIGHT, model.customHeight());
        entityData.set(DATA_CUSTOM_EYE_HEIGHT, model.customEyeHeight());
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(model.modelSize());
        }
        refreshDimensions();
    }

    // The hitbox follows the stretched model so the physical box always sits on the visible skin:
    // the base box comes from the vanilla mob the visual model belongs to (an enderman NPC gets the
    // 0.6x2.9 enderman box, not the player one), longer legs lift the whole body, a bigger head
    // raises the top, and wider head/body/arms grow the width. The scale attribute (model size) is
    // applied on top of this by vanilla automatically. Only stretchable (humanoid) model types
    // apply part scales visually, so only they get the stretch math.
    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        // A custom-geometry category has no vanilla mob to borrow a box from, so it carries its own,
        // measured from the imported mesh at import time. The model-size SCALE attribute multiplies on
        // top automatically. Height <= 0 means the dims never arrived (old/edited data) — fall through
        // to the vanilla path so the NPC is never boxless.
        if (NpcModelCatalog.isCustomCategory(modelTypeId()) && customHeight() > 0.0F) {
            float width = Math.max(MIN_HEIGHT, customWidth());
            float height = Math.max(MIN_HEIGHT, customHeight());
            float eye = Math.max(MIN_HEIGHT * 0.5F, Math.min(height - 0.1F, customEyeHeight()));
            return EntityDimensions.scalable(width, height).withEyeHeight(eye);
        }
        EntityDimensions base = baseDimensions(pose);
        if (!NpcModelCatalog.isStretchable(modelTypeId())) {
            return base;
        }
        float legGrowth = (Math.max(leftLegScale(), rightLegScale()) - 1.0F)
                * NpcModelCatalog.legPixels(modelTypeId()) / PIXELS_PER_BLOCK;
        float headGrowth = (headScale() - 1.0F) * HEAD_PIXELS / PIXELS_PER_BLOCK;
        // Oversized arms reaching below the feet lift the whole body (the renderer does the same
        // lift), so their overhang adds to the height instead of clipping under the ground.
        float armOverhang = NpcModelCatalog.armOverhangPixels(modelTypeId(),
                Math.max(leftArmScale(), rightArmScale()),
                Math.max(leftLegScale(), rightLegScale())) / PIXELS_PER_BLOCK;
        float height = Math.max(MIN_HEIGHT, base.height() + legGrowth + headGrowth + armOverhang);

        // Width only ever grows: parts scale outward from the body center line, so the widest of
        // head, body and arm silhouettes beyond their default size pushes the box out on both sides.
        float extraHalfWidthPixels = Math.max(0.0F, Math.max(
                (headScale() - 1.0F) * HEAD_HALF_WIDTH_PIXELS, Math.max(
                        (bodyScale() - 1.0F) * BODY_HALF_WIDTH_PIXELS,
                        (Math.max(leftArmScale(), rightArmScale()) - 1.0F) * ARM_OUTER_PIXELS)));
        float width = base.width() + 2.0F * extraHalfWidthPixels / PIXELS_PER_BLOCK;

        // Eyes ride on the neck: they rise with the full leg growth and body lift plus half the
        // head growth.
        float eyeHeight = base.eyeHeight() + legGrowth + armOverhang + headGrowth * 0.5F;
        eyeHeight = Math.max(MIN_HEIGHT * 0.5F, Math.min(height - 0.1F, eyeHeight));
        return EntityDimensions.scalable(width, height).withEyeHeight(eyeHeight);
    }

    // Base box of the vanilla mob whose model is displayed. The specific model id is tried first
    // ("wither_skeleton" is taller than "skeleton", "magma_cube" exists next to "slime"), then the
    // type id. Player models keep the Mannequin/Avatar pose-aware boxes from super.
    private EntityDimensions baseDimensions(Pose pose) {
        if ("player".equals(modelTypeId())) {
            return super.getDefaultDimensions(pose);
        }
        EntityType<?> vanillaType = vanillaEntityType(modelId());
        if (vanillaType == null) {
            // "illager" is a mod-side grouping, not a vanilla entity id — its members share the
            // pillager-sized box.
            String typeId = "illager".equals(modelTypeId()) ? "pillager" : modelTypeId();
            vanillaType = vanillaEntityType(typeId);
        }
        if (vanillaType == null) {
            return super.getDefaultDimensions(pose);
        }
        EntityDimensions dims = vanillaType.getDimensions();
        // Rebuild as scalable so the model-size scale attribute keeps applying even if the vanilla
        // mob declares fixed dimensions.
        return EntityDimensions.scalable(dims.width(), dims.height()).withEyeHeight(dims.eyeHeight());
    }

    private static EntityType<?> vanillaEntityType(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(parsed).orElse(null);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_MODEL_TYPE.equals(accessor) || DATA_MODEL_ID.equals(accessor)
                || DATA_HEAD_SCALE.equals(accessor) || DATA_BODY_SCALE.equals(accessor)
                || DATA_LEFT_ARM_SCALE.equals(accessor) || DATA_RIGHT_ARM_SCALE.equals(accessor)
                || DATA_LEFT_LEG_SCALE.equals(accessor) || DATA_RIGHT_LEG_SCALE.equals(accessor)
                || DATA_CUSTOM_WIDTH.equals(accessor) || DATA_CUSTOM_HEIGHT.equals(accessor)
                || DATA_CUSTOM_EYE_HEIGHT.equals(accessor)) {
            refreshDimensions();
        }
    }

    // Spawns a live NPC of the given definition into the world: creates the entity, applies the
    // definition, records its spawn origin (so respawn-at-origin works) and adds it. Shared by the
    // Spawn stick, the world spawner and the developer API (LunaNpcApi.spawn).
    public static NpcEntity spawn(ServerLevel level, NpcDefinition definition, EntitySpawnReason reason,
                                  double x, double y, double z, float yaw, BlockPos spawnOrigin) {
        NpcEntity entity = LunaNpcEntities.NPC.create(level, reason);
        if (entity == null) {
            return null;
        }
        entity.snapTo(x, y, z, yaw, 0.0F);
        entity.applyDefinition(definition);
        entity.rollJobSubType(level.getRandom(), reason);
        entity.setSpawnOrigin(spawnOrigin);
        // Persistence splits by how the NPC entered the world: a wild world-spawn honours the Spawn tab's
        // setting (despawnable by default so natural spawns can't pile up), while a hand-placed, API or
        // respawned NPC honours the general setting. The decision is read live from these settings at
        // despawn time, so only the source flag needs recording here.
        entity.naturallySpawned = reason == EntitySpawnReason.NATURAL;
        level.addFreshEntity(entity);
        net.lunacore.luna_npc.api.LunaNpcEvents.fireSpawn(entity);
        return entity;
    }

    public void applyDefinition(NpcDefinition npc) {
        lunaId = npc.lunaId();
        hurtSoundId = npc.hurtSoundId();
        dieSoundId = npc.dieSoundId();
        ambientSoundId = npc.ambientSoundId();
        aggroSoundId = npc.aggroSoundId();
        combatCloseSoundId = npc.combatCloseSoundId();
        combatFarSoundId = npc.combatFarSoundId();
        closeImpactSoundId = npc.closeImpactSoundId();
        farImpactSoundId = npc.farImpactSoundId();
        ambientIntervalSeconds = npc.ambientIntervalSeconds();
        ambientSoundCooldown = -1;
        ChatFormatting tagColor = NpcValidation.tagColorByName(npc.tagColor());
        setCustomName(Component.literal(npc.name())
                .withStyle(tagColor != null ? tagColor : ChatFormatting.WHITE));
        setCustomNameVisible(npc.isNameTagShown());
        applyModel(npc.modelSettings());
        applyEquipment(npc.equipment());
        dropSettings = npc.dropSettings();
        movementSettings = npc.movement();
        traitsSettings = npc.traits();
        combatSettings = npc.combat();
        combatController.onCombatSettingsChanged(combatSettings);
        spawnSettings = npc.spawn();
        NpcJobType previousJobType = jobSettings.jobType();
        jobSettings = npc.jobs();
        // A definition edit may have switched the job or removed sub-types; a still-valid roll is
        // kept so live NPCs pick up content edits in place, anything else re-rolls.
        if (!jobSuppressed && jobSettings.jobType() != NpcJobType.NONE
                && (jobSettings.jobType() != previousJobType || rolledJobSubType < 0
                        || rolledJobSubType >= jobSettings.liveSubTypeWeights().size())) {
            rolledJobSubType = NpcJobSettings.rollSubTypeIndex(jobSettings.liveSubTypeWeights(), random);
        }
        allianceIds = npc.allianceIds();
        gearAffectsStats = npc.equipment().gearAffectsStats();
        rangedWeaponId = npc.equipment().rangedWeaponId();
        rangedWeaponEnchants = npc.equipment().enchantsFor(NpcEquipmentSettings.SLOT_RANGED_WEAPON);
        AttributeInstance moveSpeed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            moveSpeed.setBaseValue(
                    NpcMovementSettings.speedAttributeFromBlocksPerSecond(npc.movement().walkSpeed()));
        }
        setNoGravity("air".equals(movementSettings.movementType()));
        applyBossBar(npc.bossBar());
        applyHealth(npc.health());
    }

    private void applyBossBar(NpcBossBarSettings settings) {
        bossBarSettings = settings;
        if (!settings.enabled()) {
            if (bossEvent != null) {
                bossEvent.removeAllPlayers();
                bossEvent = null;
            }
            return;
        }
        Component barName = settings.customName().isEmpty()
                ? Component.literal(getCustomName() == null ? "" : getCustomName().getString())
                : Component.literal(settings.customName());
        BossEvent.BossBarColor color = bossBarColor(settings.color());
        if (bossEvent == null) {
            bossEvent = new ServerBossEvent(getUUID(), barName, color, BossEvent.BossBarOverlay.PROGRESS);
        } else {
            bossEvent.setName(barName);
            bossEvent.setColor(color);
        }
    }

    private static BossEvent.BossBarColor bossBarColor(String name) {
        for (BossEvent.BossBarColor color : BossEvent.BossBarColor.values()) {
            if (color.name().toLowerCase(java.util.Locale.ROOT).equals(name)) {
                return color;
            }
        }
        return BossEvent.BossBarColor.RED;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (bossEvent != null) {
            bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (bossEvent != null) {
            bossEvent.removePlayer(player);
        }
    }

    public void applyEquipment(NpcEquipmentSettings equipment) {
        net.minecraft.core.HolderLookup.Provider registries = level().registryAccess();
        setItemSlot(EquipmentSlot.HEAD, NpcEquipment.resolve(equipment.helmetId(),
                equipment.enchantsFor(NpcEquipmentSettings.SLOT_HELMET), registries));
        setItemSlot(EquipmentSlot.CHEST, NpcEquipment.resolve(equipment.chestplateId(),
                equipment.enchantsFor(NpcEquipmentSettings.SLOT_CHESTPLATE), registries));
        setItemSlot(EquipmentSlot.LEGS, NpcEquipment.resolve(equipment.leggingsId(),
                equipment.enchantsFor(NpcEquipmentSettings.SLOT_LEGGINGS), registries));
        setItemSlot(EquipmentSlot.FEET, NpcEquipment.resolve(equipment.bootsId(),
                equipment.enchantsFor(NpcEquipmentSettings.SLOT_BOOTS), registries));
        setItemSlot(EquipmentSlot.MAINHAND, NpcEquipment.resolve(equipment.mainHandId(),
                equipment.enchantsFor(NpcEquipmentSettings.SLOT_MAIN_HAND), registries));
        setItemSlot(EquipmentSlot.OFFHAND, NpcEquipment.resolve(equipment.offHandId(),
                equipment.enchantsFor(NpcEquipmentSettings.SLOT_OFF_HAND), registries));
        mainHandId = equipment.mainHandId();
        mainHandEnchants = equipment.enchantsFor(NpcEquipmentSettings.SLOT_MAIN_HAND);
        shownRange = net.lunacore.luna_npc.api.NpcAttackRange.CLOSE;
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        if (dropSettings.xpMax() <= dropSettings.xpMin()) {
            return dropSettings.xpMin();
        }
        return dropSettings.xpMin() + random.nextInt(dropSettings.xpMax() - dropSettings.xpMin() + 1);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean hitByPlayer) {
        super.dropCustomDeathLoot(level, source, hitByPlayer);
        net.lunacore.luna_npc.api.LunaNpcEvents.fireDeath(this, source);
        if (bossEvent != null) {
            bossEvent.removeAllPlayers();
        }
        for (NpcDropSettings.NpcItemDrop drop : dropSettings.drops()) {
            if (random.nextFloat() * 100.0F <= drop.chancePercent()) {
                spawnAtLocation(level, NpcEquipment.resolve(drop.itemId()));
            }
        }
        if (lunaId >= 0) {
            DialogProgressManager.get(level.getServer()).onNpcDeath(lunaId);
        }
        scheduleRespawn(level);
    }

    // Queues this NPC to come back after its configured delay, at its original spawn spot or where
    // it died. Manual spawns without an origin fall back to the death position.
    private void scheduleRespawn(ServerLevel level) {
        if (!spawnSettings.respawnEnabled() || lunaId < 0) {
            return;
        }
        int min = Math.max(0, spawnSettings.respawnMinSeconds());
        int max = Math.max(min, spawnSettings.respawnMaxSeconds());
        int delaySeconds = min == max ? min : net.minecraft.util.Mth.nextInt(random, min, max);
        long due = level.getGameTime() + delaySeconds * 20L;
        BlockPos where = spawnSettings.respawnAtDeath() || spawnOrigin == null
                ? blockPosition()
                : spawnOrigin;
        net.lunacore.luna_npc.spawn.NpcRespawnManager.get(level.getServer())
                .schedule(lunaId, level.dimension().identifier().toString(), where, due, jobSuppressed);
    }

    // Frees any deposit chest this farmer had claimed the moment it leaves the world (death, despawn
    // or unload) so another farmer can take it; the claim registry is runtime-only.
    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        super.remove(reason);
        if (level() instanceof ServerLevel serverLevel) {
            farmController.releaseClaims(serverLevel);
        }
        net.lunacore.luna_npc.job.BodyguardEvents.untrack(this);
    }

    // Permanently wipes this instance: purges any queued respawn for it (so a respawn-enabled NPC is
    // gone for good — a plain kill only re-queues it) and then discards the entity with no drops or
    // death event. This is what the Remove NPC stick invokes. Server-side.
    public void wipe() {
        if (level() instanceof ServerLevel serverLevel && lunaId >= 0) {
            String dimension = serverLevel.dimension().identifier().toString();
            net.lunacore.luna_npc.spawn.NpcRespawnManager respawns =
                    net.lunacore.luna_npc.spawn.NpcRespawnManager.get(serverLevel.getServer());
            respawns.removeMatching(lunaId, dimension, blockPosition());
            if (spawnOrigin != null) {
                respawns.removeMatching(lunaId, dimension, spawnOrigin);
            }
        }
        discard();
    }

    private void applyHealth(int health) {
        AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
        }
        lunaHealth = health;
        lunaMaxHealth = health;
        setHealth(getMaxHealth());
    }

    // Re-applies the registry definition to this live entity, preserving the health it currently
    // has (applyDefinition otherwise heals it to full through applyHealth).
    private void resyncDefinition(net.lunacore.luna_npc.api.NpcDefinition definition) {
        double keptHealth = lunaHealth;
        applyDefinition(definition);
        if (keptHealth > 0.0) {
            lunaHealth = Math.min(keptHealth, lunaMaxHealth);
            setHealth((float) Math.min(getMaxHealth(), lunaHealth));
        }
    }

    // Luna health is the NPC's only real health; vanilla health is just a mirror that keeps the
    // engine intact (damage feedback, death handling) and has no meaning of its own. This avoids
    // vanilla's 1024 max-health clamp entirely. The mirror is synced next tick instead of right
    // after a hit — an instant sync suppresses the vanilla lost-hearts damage feedback.
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Projectiles do nothing to a projectile-immune NPC; an explosive shot's blast still lands.
        if (traitsSettings.projectileImmune() && isProjectileDamage(source)) {
            return false;
        }
        if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker
                && bodyguardController.ignoresHitFrom(attacker)) {
            return false;
        }
        // An advanced-combat INVULN window swallows the hit whole — no damage, no feedback.
        if (combatSettings.combatMode() == net.lunacore.luna_npc.api.NpcCombatMode.ADVANCED
                && combatController.advancedEngine().isInvulnerable()) {
            return false;
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && !isDeadOrDying()) {
            // Walking acceleration would cancel the knockback within a tick or two; a short
            // input release lets the hit actually move the NPC. Only attacker hits knock back —
            // ambient damage like burning must not stutter the walk.
            if (source.getEntity() != null) {
                // ...but only when this NPC actually takes knockback. At (near-)zero knockback there is
                // no push to uncover, so pausing would just freeze a walking NPC for 0.4s on every hit.
                if (Math.abs(movementSettings.knockback()) >= KNOCKBACK_DEADZONE) {
                    navigation.pauseInputs(8);
                }
                if (jobSettings.blockOnAnyHit()) {
                    interactionBlockAllTicks = jobSettings.blockAllSeconds() * 20;
                }
                if (jobSettings.blockAttacker() && source.getEntity() instanceof ServerPlayer attackerPlayer) {
                    interactionBlockByPlayer.put(attackerPlayer.getUUID(),
                            jobSettings.blockAttackerSeconds() * 20);
                }
            }
            net.minecraft.world.entity.LivingEntity attacker =
                    source.getEntity() instanceof net.minecraft.world.entity.LivingEntity living && living != this
                            ? living : null;
            if (attacker != null) {
                combatController.onAttacked(attacker);
            }
            ticksSinceHurt = 0;
            float applied = amount;
            if (combatSettings.combatMode() == net.lunacore.luna_npc.api.NpcCombatMode.ADVANCED) {
                applied = combatController.advancedEngine().mitigate(amount);
                combatController.advancedEngine().onTookDamage(applied, attacker);
            }
            lunaHealth -= applied;
            if (lunaHealth > 0 && getHealth() < getMaxHealth() / 2.0F) {
                syncVanillaHealthMirror();
            }
        }
        return hurt;
    }

    @Override
    public boolean fireImmune() {
        return (traitsSettings != null && traitsSettings.fireImmune()) || super.fireImmune();
    }

    // Projectile-immune NPCs are not valid projectile targets, so shots pass through instead of hitting.
    @Override
    public boolean canBeHitByProjectile() {
        return super.canBeHitByProjectile() && (traitsSettings == null || !traitsSettings.projectileImmune());
    }

    // Any projectile-tagged hit, or one whose direct source is a projectile — but never an explosion.
    private static boolean isProjectileDamage(DamageSource source) {
        return !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                && (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                        || source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile);
    }

    // The received knockback scales with the movement setting; negative values pull the NPC
    // towards the attacker instead. Vanilla ignores non-positive strengths outright, so the
    // pull is done by flipping the direction vector and keeping the strength positive. All
    // vanilla knockback paths funnel through this overload.
    @Override
    public void knockback(double strength, double x, double z, DamageSource source, float power,
                          boolean limitY) {
        // Near-zero knockback is left entirely alone: vanilla knockback halves the current velocity even
        // for a speck of strength, which would stall a walking NPC. Zero must add nothing (pure pressure).
        if (Math.abs(movementSettings.knockback()) < KNOCKBACK_DEADZONE) {
            return;
        }
        double scaled = strength * movementSettings.knockback();
        if (scaled < 0.0) {
            super.knockback(-scaled, -x, -z, source, power, limitY);
        } else {
            super.knockback(scaled, x, z, source, power, limitY);
        }
    }

    private void tickInteractionBlock() {
        if (interactionBlockAllTicks > 0) {
            interactionBlockAllTicks--;
        }
        if (interactionBlockByPlayer.isEmpty()) {
            return;
        }
        var blocked = interactionBlockByPlayer.entrySet().iterator();
        while (blocked.hasNext()) {
            var entry = blocked.next();
            if (entry.getValue() <= 1) {
                blocked.remove();
            } else {
                entry.setValue(entry.getValue() - 1);
            }
        }
    }

    public boolean isInteractionBlockedFor(ServerPlayer player) {
        return interactionBlockAllTicks > 0
                || interactionBlockByPlayer.getOrDefault(player.getUUID(), 0) > 0;
    }

    // Halts the NPC for the interaction: the current path is dropped and wandering stays suppressed
    // (tick() skips the wander controller) until the hold runs out. Refresh it while a menu is open.
    public void holdForInteraction() {
        interactionHoldTicks = INTERACTION_HOLD_TICKS;
        navigation.stop();
    }

    public boolean isHoldingForInteraction() {
        return interactionHoldTicks > 0;
    }

    // True when nothing is steering the NPC — so the idle look-around may take the yaw without fighting
    // navigation, combat, farming or a player interaction for it.
    private boolean isIdleForLook() {
        return !navigation.isNavigating() && !combatController.isFighting()
                && !combatController.isFleeing() && !farmController.isWorking()
                && !patrolController.holdsLookDirection() && !bodyguardController.ownsLook()
                && !isHoldingForInteraction();
    }

    public void beginDialog(java.util.UUID player) {
        dialogPlayers.add(player);
        holdForInteraction();
    }

    public void endDialog(java.util.UUID player) {
        dialogPlayers.remove(player);
    }

    private void tickDialogHold() {
        if (dialogPlayers.isEmpty() || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        dialogPlayers.removeIf(id -> {
            ServerPlayer talker = serverLevel.getServer().getPlayerList().getPlayer(id);
            return talker == null || talker.level() != level()
                    || distanceToSqr(talker) > DIALOG_HOLD_RANGE_SQ;
        });
        if (!dialogPlayers.isEmpty()) {
            holdForInteraction();
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitVec) {
        if (hand != InteractionHand.MAIN_HAND || !jobActive()
                || jobSettings.jobType() == NpcJobType.FARMING || jobSettings.jobType() == NpcJobType.PATROL) {
            return super.interact(player, hand, hitVec);
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (isInteractionBlockedFor(serverPlayer)) {
                return InteractionResult.CONSUME;
            }
            if (jobSettings.jobType() == NpcJobType.BODYGUARD) {
                return net.lunacore.luna_npc.job.BodyguardEvents.openMenu(serverPlayer, this)
                        ? InteractionResult.CONSUME : super.interact(player, hand, hitVec);
            }
            holdForInteraction();
            openJobFor(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    private void openJobFor(ServerPlayer player) {
        switch (jobSettings.jobType()) {
            case TRADING -> LunaNpcMenus.openTradeMenu(player, this);
            case QUESTS -> QuestEvents.openQuestOffers(player, this);
            case DIALOG -> DialogEvents.open(player, this);
            case NONE, FARMING, PATROL, BODYGUARD -> {
            }
        }
    }

    // "Creature" NPCs react to potions like mobs such as zombies (harming heals, healing harms).
    @Override
    public boolean isInvertedHealAndHarm() {
        return NpcTraitsSettings.TYPE_CREATURE.equals(traitsSettings.npcType())
                || super.isInvertedHealAndHarm();
    }

    @Override
    public boolean canBreatheUnderwater() {
        return !traitsSettings.canDrown() || super.canBreatheUnderwater();
    }

    // Off by default so no NPC dies to fall damage — flyers that dip to the ground to attack read as
    // "falling" to the engine, and even a walker shouldn't die pathing down a cliff. The creator opts
    // back in per NPC; when on, vanilla fall damage applies unchanged.
    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        return traitsSettings.takeFallDamage() && super.causeFallDamage(fallDistance, damageMultiplier, source);
    }

    // Jump velocity comes from the configured jump height in blocks (vanilla's 0.42 velocity
    // clears 1.25 blocks) instead of the attribute, so it is independent of the model scale —
    // stretched NPCs still make their jumps. Jump-boost potions keep stacking on top.
    public float configuredJumpVelocity() {
        return 0.42F * net.minecraft.util.Mth.sqrt(movementSettings.jumpHeight() / 1.25F);
    }

    @Override
    protected float getJumpPower(float multiplier) {
        return configuredJumpVelocity() * multiplier * getBlockJumpFactor() + getJumpBoostPower();
    }

    @Override
    public void tick() {
        // Wandering and navigation feed vanilla locomotion inputs, so they run before the
        // physics step in super.tick() consumes them.
        if (!level().isClientSide() && !isDeadOrDying()) {
            // Reconcile with the saved definition once after loading, so an NPC edited while its
            // chunk was unloaded (which the live-update sweep skips) still reflects the change.
            if (!definitionSynced && level() instanceof ServerLevel definitionLevel) {
                definitionSynced = true;
                if (lunaId >= 0) {
                    net.lunacore.luna_npc.api.LunaNpcApi.npcs(definitionLevel.getServer())
                            .byLunaId(lunaId).ifPresent(this::resyncDefinition);
                }
            }
            // Faction targeting picks up nearby enemies (or mates under attack), and re-evaluates a stuck
            // engagement for a better target, but never while the NPC flees in fear. Staggered by id.
            if (combatSettings.combatMode() != net.lunacore.luna_npc.api.NpcCombatMode.ADVANCED
                    && !combatController.isFleeing() && !bodyguardController.isRetreating()
                    && (tickCount + getId()) % 10 == 0
                    && level() instanceof ServerLevel serverLevel) {
                combatController.acquireTarget(
                        net.lunacore.luna_npc.alliance.NpcAllianceManager.get(serverLevel.getServer()));
            }
            if (navModeOverrideTicks > 0 && --navModeOverrideTicks == 0) {
                navModeOverride = null;
                setNoGravity("air".equals(movementSettings.movementType()));
            }
            // Vanilla mobs hostile to this NPC's faction hunt it, without changing anything else
            // about their behaviour.
            if (tickCount % 20 == 0 && level() instanceof ServerLevel factionLevel) {
                tickFactionAggro(factionLevel);
            }
            tickInteractionBlock();
            tickDialogHold();
            if (interactionHoldTicks > 0) {
                interactionHoldTicks--;
            }
            // Combat runs first: its scripted chase keeps the wander controller paused. Farming runs
            // between the two and pauses wandering while it works its crop route. An open interaction
            // holds the NPC in place so it does not walk out of its own menu.
            combatController.tick();
            entityData.set(DATA_AGGRESSIVE, combatTarget() != null);
            farmController.tick();
            patrolController.tick();
            bodyguardController.tick();
            if (isRemoved()) {
                return;
            }
            if (!isHoldingForInteraction()) {
                wanderController.tick();
            }
            if (level() instanceof ServerLevel loaderLevel) {
                chunkLoader.tick(loaderLevel);
            }
            navigation.tick();
            // A standing-still NPC slowly turns to look around, so it reads as alive rather than frozen.
            // Any active system (navigation/combat/farm/interaction) owns the yaw instead, so this only
            // runs when the NPC is genuinely idle.
            if (isIdleForLook()) {
                idleLookController.tick();
            } else {
                idleLookController.reset();
            }
            tickHoverAndDepth();
            tickTraits();
        }
        super.tick();
        if (level().isClientSide() || isDeadOrDying()) {
            return;
        }
        if (tickCount % DESPAWN_CHECK_INTERVAL == 0) {
            tickDespawn();
            if (isRemoved()) {
                return;
            }
        }
        ticksSinceHurt++;
        tickRegen();
        if (lunaHealth > 0) {
            syncVanillaHealthMirror();
        }
        tickAmbientSound();
        if (bossEvent != null) {
            bossEvent.setProgress((float) Math.clamp(lunaHealth / lunaMaxHealth, 0.0, 1.0));
            // The seen-by-player hooks only cover players that start tracking after the bar
            // exists; enabling the bar on an already-spawned NPC would show nothing, so the
            // audience is synced by distance too.
            if (tickCount % 10 == 0) {
                for (ServerPlayer player : ((ServerLevel) level()).players()) {
                    double distance = player.distanceToSqr(this);
                    if (distance <= 64 * 64) {
                        bossEvent.addPlayer(player);
                    } else if (distance > 96 * 96) {
                        bossEvent.removePlayer(player);
                    }
                }
            }
        }
    }

    // Cosmetic gear (the default): worn armor gives no protection. With real gear stats the
    // vanilla armor and enchantment math runs untouched.
    @Override
    protected float getDamageAfterArmorAbsorb(DamageSource source, float amount) {
        return gearAffectsStats ? super.getDamageAfterArmorAbsorb(source, amount) : amount;
    }

    @Override
    protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        if (gearAffectsStats) {
            return super.getDamageAfterMagicAbsorb(source, amount);
        }
        // Gear enchantments are cosmetic, but the resistance potion effect is not gear — keep
        // vanilla's resistance formula.
        if (hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE)
                && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS)) {
            int factor = (getEffect(net.minecraft.world.effect.MobEffects.RESISTANCE).getAmplifier() + 1) * 5;
            amount = Math.max(0.0F, amount * (25 - factor) / 25.0F);
        }
        return amount;
    }

    private static final double FACTION_AGGRO_RANGE = 24.0;

    // Injects this NPC as a target into nearby vanilla mobs whose faction is hostile to it, so
    // e.g. zombies swarm an NPC their alliance treats as an enemy. Existing vanilla targets are
    // only overridden when the NPC is the closer threat; everything else about the mob is left
    // untouched.
    private void tickFactionAggro(ServerLevel level) {
        net.lunacore.luna_npc.alliance.NpcAllianceManager alliances =
                net.lunacore.luna_npc.alliance.NpcAllianceManager.get(level.getServer());
        if (allianceIds.stream().noneMatch(alliances::hasHostility)) {
            return;
        }
        // NPCs are Mannequins, not Mobs, so this scan only ever sees genuine vanilla/modded mobs.
        net.minecraft.world.phys.AABB box = getBoundingBox().inflate(FACTION_AGGRO_RANGE);
        for (net.minecraft.world.entity.Mob mob
                : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                        net.minecraft.world.entity.LivingEntity::isAlive)) {
            if (!alliances.mutuallyHostile(this, mob)) {
                continue;
            }
            net.minecraft.world.entity.LivingEntity current = mob.getTarget();
            if (current == null || current.isRemoved() || current.isDeadOrDying()
                    || mob.distanceToSqr(this) < mob.distanceToSqr(current)) {
                mob.setTarget(this);
            }
        }
    }


    private void tickTraits() {
        // Rain douses the daylight burn, exactly like it does for vanilla undead.
        if (traitsSettings.burnInDaylight() && !fireImmune() && level().isBrightOutside()
                && level().canSeeSky(blockPosition()) && !isInWater()
                && !level().isRainingAt(blockPosition())) {
            setRemainingFireTicks(Math.max(getRemainingFireTicks(), 100));
        }
        // Swimmers float on top of liquids (the dedicated water movement handles water NPCs).
        if (traitsSettings.canSwim() && !"water".equals(movementSettings.movementType())
                && (isInWater() || isInLava())) {
            var delta = getDeltaMovement();
            setDeltaMovement(delta.x, Math.min(delta.y + 0.06, 0.12), delta.z);
        }
    }

    // Slow health regen: each condition is an independent rule on its own timer, so a failing gate
    // pauses (rather than resets) that one rule's progress toward its next heal.
    private void tickRegen() {
        net.lunacore.luna_npc.api.NpcRegenSettings regen = traitsSettings.regen();
        java.util.List<net.lunacore.luna_npc.api.NpcRegenCondition> rules = regen.conditions();
        if (!regen.enabled() || lunaHealth >= lunaMaxHealth) {
            java.util.Arrays.fill(regenRuleTicks, 0);
            return;
        }
        if (regenRuleTicks.length != rules.size()) {
            regenRuleTicks = new int[rules.size()];
        }
        for (int i = 0; i < rules.size(); i++) {
            net.lunacore.luna_npc.api.NpcRegenCondition rule = rules.get(i);
            if (rule.intervalSeconds() <= 0 || !regenConditionPasses(rule)) {
                regenRuleTicks[i] = 0;
                continue;
            }
            if (lunaHealth >= lunaMaxHealth) {
                continue;
            }
            regenRuleTicks[i]++;
            if (regenRuleTicks[i] >= Math.max(1, Math.round(rule.intervalSeconds() * 20))) {
                lunaHealth = Math.min(lunaMaxHealth, lunaHealth + rule.amount());
                syncVanillaHealthMirror();
                regenRuleTicks[i] = 0;
            }
        }
    }

    private boolean regenConditionPasses(net.lunacore.luna_npc.api.NpcRegenCondition condition) {
        return switch (condition.type()) {
            case OUT_OF_COMBAT -> !combatController.isFighting();
            case NO_DAMAGE_SECONDS -> ticksSinceHurt >= condition.value() * 20;
            case HP_BELOW -> lunaHealth <= condition.value();
            case HP_ABOVE -> lunaHealth >= condition.value();
        };
    }

    // Idle air NPCs hold their flight height — giving way to a combat target's own height on approach,
    // exactly like the flight path goal does — and bob on top of it when hovering is enabled; idle water
    // NPCs drift back to their preferred (or combat give-way) depth. This is what keeps an air/water NPC
    // flying/swimming under its medium even in advanced combat, where nothing else drives it between the
    // engine's explicit MOVE_TO actions.
    private void tickHoverAndDepth() {
        if (navigation.isNavigating()) {
            return;
        }
        String type = effectiveMovementType();
        if ("air".equals(type)) {
            net.minecraft.world.entity.LivingEntity fightTarget = combatController.target();
            double holdY = fightTarget != null
                    ? navigation.combatFlightY(fightTarget.position())
                    : navigation.preferredFlightY();
            double bobVelocity = 0.0;
            if (movementSettings.hoverEnabled()) {
                int periodTicks = Math.max(2, movementSettings.hoverIntervalMs() / 50);
                double phase = (tickCount % periodTicks) * 2.0 * Math.PI / periodTicks;
                bobVelocity = Math.cos(phase) * movementSettings.hoverAmount() * Math.PI / periodTicks;
            }
            double pull = Math.clamp((holdY - getY()) * 0.1, -0.05, 0.05);
            setDeltaMovement(getDeltaMovement().multiply(0.6, 0.5, 0.6).add(0.0, pull + bobVelocity, 0.0));
        } else if ("water".equals(type) && isInWater()) {
            // Combat pauses navigation whenever the target is in reach, which lands here — so a
            // fighting swimmer holds the combat give-way depth (the same rule the path goal used to
            // swim up to the target), not the idle preferred depth. Drifting back to the idle depth
            // here is what used to sink it under the target it had just reached.
            net.minecraft.world.entity.LivingEntity fightTarget = combatController.target();
            double holdY = fightTarget != null && fightTarget.isInWater()
                    ? navigation.combatSwimY(fightTarget.position())
                    : navigation.preferredSwimY();
            double pull = Math.clamp((holdY - getY()) * 0.1, -0.05, 0.05);
            setDeltaMovement(getDeltaMovement().multiply(0.8, 0.5, 0.8).add(0.0, pull, 0.0));
        }
    }

    // Despawn radii mirror vanilla: gone at once past the hard distance, kept forever within the
    // casual one, and in between despawned after a spell with a per-check dice roll.
    private static final int DESPAWN_CHECK_INTERVAL = 20;
    private static final double HARD_DESPAWN_DISTANCE_SQUARED = 128.0 * 128.0;
    private static final double CASUAL_DESPAWN_DISTANCE_SQUARED = 32.0 * 32.0;
    private static final int CASUAL_DESPAWN_DELAY_TICKS = 600;
    private static final int CASUAL_DESPAWN_ROLL = 40;

    // A non-persistent NPC despawns like a vanilla mob so natural spawns can never pile up. NPCs
    // marked persistent (hand-placed/API/respawn by default, or a spawn set to stay) skip this
    // entirely and stay put. Runs on the server every DESPAWN_CHECK_INTERVAL ticks.
    // A natural spawn despawns unless the Spawn tab marks it persistent; anything else (placed, API,
    // respawn) despawns only if the general setting allows. Read from the live settings, so toggling
    // persistence in the editor applies to NPCs already in the world.
    private boolean canDespawn() {
        return naturallySpawned ? !spawnSettings.naturalPersistent() : !spawnSettings.placedPersistent();
    }

    private void tickDespawn() {
        if (!canDespawn()) {
            noNearbyPlayerTicks = 0;
            return;
        }
        net.minecraft.world.entity.player.Player nearest = level().getNearestPlayer(this, -1.0);
        if (nearest == null || nearest.distanceToSqr(this) > HARD_DESPAWN_DISTANCE_SQUARED) {
            discard();
            return;
        }
        if (nearest.distanceToSqr(this) < CASUAL_DESPAWN_DISTANCE_SQUARED) {
            noNearbyPlayerTicks = 0;
            return;
        }
        noNearbyPlayerTicks += DESPAWN_CHECK_INTERVAL;
        if (noNearbyPlayerTicks > CASUAL_DESPAWN_DELAY_TICKS && getRandom().nextInt(CASUAL_DESPAWN_ROLL) == 0) {
            discard();
        }
    }

    private void tickAmbientSound() {
        if (ambientSoundId.isBlank()) {
            return;
        }
        if (ambientSoundCooldown < 0) {
            ambientSoundCooldown = nextAmbientDelayTicks();
        }
        if (--ambientSoundCooldown == 0) {
            Identifier parsed = Identifier.tryParse(ambientSoundId);
            if (parsed != null) {
                BuiltInRegistries.SOUND_EVENT.getOptional(parsed)
                        .ifPresent(sound -> playSound(sound, 1.0F, 1.0F));
            }
            ambientSoundCooldown = nextAmbientDelayTicks();
        }
    }

    private int nextAmbientDelayTicks() {
        int baseTicks = ambientIntervalSeconds * 20;
        return baseTicks + random.nextInt(Math.max(1, baseTicks / 2));
    }

    private void syncVanillaHealthMirror() {
        setHealth((float) Math.min(getMaxHealth(), lunaHealth));
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return soundOrDefault(hurtSoundId, SoundEvents.PLAYER_HURT);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return soundOrDefault(dieSoundId, SoundEvents.PLAYER_DEATH);
    }

    public static SoundEvent soundOrDefault(String soundId, SoundEvent fallback) {
        Identifier parsed = Identifier.tryParse(soundId);
        if (parsed == null) {
            return fallback;
        }
        return BuiltInRegistries.SOUND_EVENT.getOptional(parsed).orElse(fallback);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putLong("luna_id", lunaId);
        output.putString("hurt_sound", hurtSoundId);
        output.putString("die_sound", dieSoundId);
        output.putString("ambient_sound", ambientSoundId);
        output.putString("aggro_sound", aggroSoundId);
        output.putString("combat_close_sound", combatCloseSoundId);
        output.putString("combat_far_sound", combatFarSoundId);
        output.putString("close_impact_sound", closeImpactSoundId);
        output.putString("far_impact_sound", farImpactSoundId);
        output.putInt("ambient_interval_seconds", ambientIntervalSeconds);
        output.putDouble("luna_health", lunaHealth);
        output.putDouble("luna_max_health", lunaMaxHealth);
        output.store("luna_drops", NpcDropSettings.CODEC, dropSettings);
        output.store("luna_boss_bar", NpcBossBarSettings.CODEC, bossBarSettings);
        output.store("luna_movement", NpcMovementSettings.CODEC, movementSettings);
        output.store("luna_traits", NpcTraitsSettings.CODEC, traitsSettings);
        output.store("luna_combat", NpcCombatSettings.CODEC, combatSettings);
        output.store("luna_spawn", NpcSpawnSettings.CODEC, spawnSettings);
        output.store("luna_jobs", NpcJobSettings.CODEC, jobSettings);
        output.storeNullable("luna_alliances", com.mojang.serialization.Codec.STRING.listOf(), allianceIds);
        output.storeNullable("luna_spawn_origin", BlockPos.CODEC, spawnOrigin);
        output.putBoolean("luna_natural_spawn", naturallySpawned);
        output.putInt("luna_job_subtype", rolledJobSubType);
        output.putBoolean("luna_job_suppressed", jobSuppressed);
        farmController.saveBuffer(output);
        patrolController.save(output);
        bodyguardController.save(output);
        output.putBoolean("luna_gear_stats", gearAffectsStats);
        output.putString("luna_ranged_weapon", rangedWeaponId);
        output.store("luna_ranged_enchants", NpcItemEnchant.CODEC.listOf(), rangedWeaponEnchants);
        output.putString("luna_main_weapon", mainHandId);
        output.store("luna_main_enchants", NpcItemEnchant.CODEC.listOf(), mainHandEnchants);
        output.putString("model_type", modelTypeId());
        output.putString("model_id", modelId());
        output.putFloat("head_scale", headScale());
        output.putFloat("body_scale", bodyScale());
        output.putFloat("left_arm_scale", leftArmScale());
        output.putFloat("right_arm_scale", rightArmScale());
        output.putFloat("left_leg_scale", leftLegScale());
        output.putFloat("right_leg_scale", rightLegScale());
        output.putFloat("custom_width", customWidth());
        output.putFloat("custom_height", customHeight());
        output.putFloat("custom_eye_height", customEyeHeight());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        lunaId = input.getLongOr("luna_id", -1);
        hurtSoundId = input.getStringOr("hurt_sound", NpcDefaults.HURT_SOUND_ID);
        dieSoundId = input.getStringOr("die_sound", NpcDefaults.DIE_SOUND_ID);
        ambientSoundId = input.getStringOr("ambient_sound", NpcDefaults.AMBIENT_SOUND_ID);
        aggroSoundId = input.getStringOr("aggro_sound", NpcDefaults.AGGRO_SOUND_ID);
        combatCloseSoundId = input.getStringOr("combat_close_sound", NpcDefaults.COMBAT_CLOSE_SOUND_ID);
        combatFarSoundId = input.getStringOr("combat_far_sound", NpcDefaults.COMBAT_FAR_SOUND_ID);
        closeImpactSoundId = input.getStringOr("close_impact_sound", NpcDefaults.CLOSE_IMPACT_SOUND_ID);
        farImpactSoundId = input.getStringOr("far_impact_sound", NpcDefaults.FAR_IMPACT_SOUND_ID);
        ambientIntervalSeconds = input.getIntOr("ambient_interval_seconds", NpcDefaults.AMBIENT_INTERVAL_SECONDS);
        lunaHealth = input.getDoubleOr("luna_health", NpcDefaults.HEALTH);
        lunaMaxHealth = input.getDoubleOr("luna_max_health", Math.max(lunaHealth, NpcDefaults.HEALTH));
        dropSettings = input.read("luna_drops", NpcDropSettings.CODEC).orElse(NpcDropSettings.defaults());
        movementSettings = input.read("luna_movement", NpcMovementSettings.CODEC)
                .orElse(NpcMovementSettings.defaults());
        traitsSettings = input.read("luna_traits", NpcTraitsSettings.CODEC)
                .orElse(NpcTraitsSettings.defaults());
        combatSettings = input.read("luna_combat", NpcCombatSettings.CODEC)
                .orElse(NpcCombatSettings.defaults());
        spawnSettings = input.read("luna_spawn", NpcSpawnSettings.CODEC)
                .orElse(NpcSpawnSettings.defaults());
        jobSettings = input.read("luna_jobs", NpcJobSettings.CODEC).orElse(NpcJobSettings.defaults());
        rolledJobSubType = input.getIntOr("luna_job_subtype", -1);
        jobSuppressed = input.getBooleanOr("luna_job_suppressed", false);
        farmController.loadBuffer(input);
        patrolController.load(input);
        bodyguardController.load(input);
        allianceIds = input.read("luna_alliances", com.mojang.serialization.Codec.STRING.listOf())
                .orElseGet(() -> java.util.List.of(input.getStringOr("luna_alliance", NpcDefaults.ALLIANCE)));
        spawnOrigin = input.read("luna_spawn_origin", BlockPos.CODEC).orElse(blockPosition());
        // Migrate old saves: a pre-flag NPC that was despawnable (luna_can_despawn) was a natural spawn;
        // anything else is treated as placed, so a pre-existing hand-placed NPC is never surprised away.
        naturallySpawned = input.getBooleanOr("luna_natural_spawn",
                input.getBooleanOr("luna_can_despawn", false));
        gearAffectsStats = input.getBooleanOr("luna_gear_stats", false);
        rangedWeaponId = input.getStringOr("luna_ranged_weapon", "");
        rangedWeaponEnchants = input.read("luna_ranged_enchants", NpcItemEnchant.CODEC.listOf())
                .orElse(java.util.List.of());
        mainHandId = input.getStringOr("luna_main_weapon", "");
        mainHandEnchants = input.read("luna_main_enchants", NpcItemEnchant.CODEC.listOf())
                .orElse(java.util.List.of());
        applyBossBar(input.read("luna_boss_bar", NpcBossBarSettings.CODEC).orElse(NpcBossBarSettings.defaults()));
        entityData.set(DATA_MODEL_TYPE, input.getStringOr("model_type", NpcDefaults.MODEL_TYPE));
        entityData.set(DATA_MODEL_ID, input.getStringOr("model_id", NpcDefaults.MODEL_ID));
        entityData.set(DATA_HEAD_SCALE, input.getFloatOr("head_scale", NpcDefaults.PART_SCALE));
        entityData.set(DATA_BODY_SCALE, input.getFloatOr("body_scale", NpcDefaults.PART_SCALE));
        entityData.set(DATA_LEFT_ARM_SCALE, input.getFloatOr("left_arm_scale", NpcDefaults.PART_SCALE));
        entityData.set(DATA_RIGHT_ARM_SCALE, input.getFloatOr("right_arm_scale", NpcDefaults.PART_SCALE));
        entityData.set(DATA_LEFT_LEG_SCALE, input.getFloatOr("left_leg_scale", NpcDefaults.PART_SCALE));
        entityData.set(DATA_RIGHT_LEG_SCALE, input.getFloatOr("right_leg_scale", NpcDefaults.PART_SCALE));
        entityData.set(DATA_CUSTOM_WIDTH, input.getFloatOr("custom_width", 0.0F));
        entityData.set(DATA_CUSTOM_HEIGHT, input.getFloatOr("custom_height", 0.0F));
        entityData.set(DATA_CUSTOM_EYE_HEIGHT, input.getFloatOr("custom_eye_height", 0.0F));
        refreshDimensions();
    }
}
