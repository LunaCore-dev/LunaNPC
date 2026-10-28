package net.lunacore.luna_npc.npc;

public final class NpcDefaults {

    public static final int HEALTH = 20;
    public static final boolean NAME_TAG_SHOWN = true;
    public static final String TAG_COLOR = "white";
    public static final String CATEGORY = "";
    public static final String HURT_SOUND_ID = "minecraft:entity.player.hurt";
    public static final String DIE_SOUND_ID = "minecraft:entity.player.death";
    public static final String AMBIENT_SOUND_ID = "";
    public static final String AGGRO_SOUND_ID = "";
    public static final String COMBAT_CLOSE_SOUND_ID = "";
    public static final String COMBAT_FAR_SOUND_ID = "minecraft:entity.arrow.shoot";
    public static final String CLOSE_IMPACT_SOUND_ID = "";
    public static final String FAR_IMPACT_SOUND_ID = "minecraft:entity.arrow.hit";
    public static final int AMBIENT_INTERVAL_SECONDS = 30;
    public static final String MODEL_TYPE = "player";
    public static final String MODEL_ID = "steve";
    public static final float MODEL_SIZE = 1.0F;
    public static final float PART_SCALE = 1.0F;
    public static final int XP_DROP_MIN = 0;
    public static final int XP_DROP_MAX = 20;
    public static final float DROP_CHANCE_PERCENT = 10.0F;
    // Blocks per second; 4.3 matches vanilla player walking speed.
    public static final float WALK_SPEED = 4.3F;
    // Blocks; 1.25 matches a vanilla jump.
    public static final float JUMP_HEIGHT = 1.25F;
    public static final String MOVEMENT_TYPE = "ground";
    public static final String PATHFINDING_TYPE = "vanilla";
    public static final int WANDER_MIN_SECONDS = 15;
    public static final int WANDER_MAX_SECONDS = 30;
    public static final int WANDER_RADIUS = 16;
    public static final int WANDER_MIN_GOAL_DISTANCE = 0;
    public static final int HOVER_INTERVAL_MS = 2000;
    public static final float HOVER_AMOUNT = 0.5F;
    public static final int FLIGHT_HEIGHT = 5;
    public static final int WATER_DEPTH = 5;
    public static final float KNOCKBACK = 1.0F;
    public static final int HEIGHT_RANGE = 3;
    public static final String NPC_TYPE = "alive";
    // New NPCs join the Creatures faction (hostile to Players) by default.
    public static final String ALLIANCE = "creatures";
    public static final int AVOID_LIGHT_LEVEL = 5;
    public static final String REACTION = "aggressive";
    public static final int FLEE_SECONDS = 15;
    public static final int FLEE_DISTANCE = 10;
    public static final String BOSS_BAR_COLOR = "red";
    public static final float ATTACK_DAMAGE = 5.0F;
    public static final float MELEE_RANGE = 3.0F;
    public static final float RANGED_RANGE = 16.0F;
    public static final float ATTACK_INTERVAL_SECONDS = 2.0F;
    public static final float EFFECT_DURATION_SECONDS = 5.0F;
    public static final float DISTANCE_PREFERENCE = 10.0F;
    public static final float AGGRO_RANGE = 25.0F;
    public static final boolean REQUIRE_LINE_OF_SIGHT = true;
    // Same-faction members leave each other alone unless friendly fire is turned on.
    public static final boolean FRIENDLY_FIRE = false;
    // Off by default: the configured attack damage lands exactly, ignoring world difficulty.
    public static final boolean DIFFICULTY_SCALING = false;
    // 0 seconds means the NPC never gives up on a target that stays in range.
    public static final int GIVE_UP_SECONDS = 0;
    public static final boolean EMERGENCY_SEEK = true;
    public static final int IMPACT_RADIUS = 4;
    public static final String IMPACT_PARTICLE = "";
    public static final float IMPACT_PARTICLE_SECONDS = 2.0F;
    public static final String PROJECTILE_ITEM = "minecraft:arrow";
    public static final float PROJECTILE_SPEED = 20.0F;
    public static final float CURVE_MAX_DISTANCE = 100.0F;

    private NpcDefaults() {
    }
}
