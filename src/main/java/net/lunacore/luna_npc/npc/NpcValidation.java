package net.lunacore.luna_npc.npc;

import java.util.Random;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.lunacore.luna_npc.api.NpcTraitsSettings;
import net.lunacore.luna_npc.api.NpcWanderSettings;

public final class NpcValidation {

    public static final int MAX_NAME_LENGTH = 16;
    public static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9]{1," + MAX_NAME_LENGTH + "}");
    public static final int MIN_HEALTH = 1;
    public static final int MAX_HEALTH = 100_000;
    public static final int MIN_AMBIENT_INTERVAL_SECONDS = 1;
    public static final int MAX_AMBIENT_INTERVAL_SECONDS = 999;
    public static final float MIN_MODEL_SIZE = 0.1F;
    public static final float MAX_MODEL_SIZE = 10.0F;
    public static final float MIN_LIMB_STRETCH = 0.5F;
    public static final float MAX_LIMB_STRETCH = 2.0F;
    // Imported skin ids are lowercase png file stems from LunaNPC/assets/npc_models/<type>/.
    public static final Pattern CUSTOM_MODEL_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_.\\-]{0,63}");
    // A custom-geometry model category (a user-named model type, unrelated to the spawn CATEGORY_PATTERN):
    // the folder name under LunaNPC/custom_models/<category>/.
    public static final int MIN_MODEL_CATEGORY_LENGTH = 3;
    public static final int MAX_MODEL_CATEGORY_LENGTH = 32;
    public static final Pattern MODEL_CATEGORY_PATTERN =
            Pattern.compile("[a-z0-9][a-z0-9_\\-]{" + (MIN_MODEL_CATEGORY_LENGTH - 1) + "," + (MAX_MODEL_CATEGORY_LENGTH - 1) + "}");
    public static final int MAX_CUSTOM_MODEL_CATEGORIES = 64;
    public static final int MAX_XP_DROP = 1_000;
    public static final int DROP_GRID_SLOTS = 36;
    public static final float MIN_DROP_CHANCE_PERCENT = 0.01F;
    public static final float MAX_DROP_CHANCE_PERCENT = 100.0F;
    // Move speeds are in blocks per second (vanilla player walking is ~4.3 B/S).
    public static final float MIN_MOVE_SPEED = 0.0F;
    public static final float MAX_MOVE_SPEED = 50.0F;
    // Jump height is in blocks (vanilla jumps clear 1.25).
    public static final float MIN_JUMP_HEIGHT = 0.0F;
    public static final float MAX_JUMP_HEIGHT = 20.0F;
    public static final java.util.List<String> MOVEMENT_TYPES = java.util.List.of("ground", "water", "air");
    public static final int MAX_AVOIDED_BLOCKS = 18;
    public static final int MIN_HOVER_INTERVAL_MS = 100;
    public static final int MAX_HOVER_INTERVAL_MS = 10_000;
    public static final float MAX_HOVER_AMOUNT = 5.0F;
    public static final int MAX_FLIGHT_HEIGHT = 64;
    public static final int MAX_WATER_DEPTH = 64;
    public static final float MIN_KNOCKBACK = -2.0F;
    public static final float MAX_KNOCKBACK = 10.0F;
    public static final java.util.List<String> NPC_TYPES =
            java.util.List.of(NpcTraitsSettings.TYPE_ALIVE, NpcTraitsSettings.TYPE_CREATURE);
    public static final int MIN_LIGHT_LEVEL = 0;
    public static final int MAX_LIGHT_LEVEL = 15;
    public static final int MAX_REGEN_INTERVAL_SECONDS = 60;
    public static final int MAX_REGEN_NO_DAMAGE_SECONDS = 1000;
    public static final int MAX_REGEN_AMOUNT = 1000;
    // The data model and API take an unbounded condition list; only the editor caps it.
    public static final int MAX_REGEN_CONDITIONS_GUI = 10;
    // 0 is meaningful: a depth-holding water NPC then only fights at exactly its preferred depth.
    public static final int MIN_HEIGHT_RANGE = 0;
    public static final int MAX_HEIGHT_RANGE = 10;
    public static final java.util.List<String> WANDER_BEHAVIORS =
            java.util.List.of(NpcWanderSettings.STATIC, NpcWanderSettings.STROLL, NpcWanderSettings.RESTLESS);
    public static final int MIN_WANDER_INTERVAL_SECONDS = 1;
    public static final int MAX_WANDER_INTERVAL_SECONDS = 120;
    public static final int MIN_WANDER_RADIUS = 1;
    public static final int MAX_WANDER_RADIUS = 64;
    public static final float MIN_ATTACK_DAMAGE = 0.0F;
    public static final float MAX_ATTACK_DAMAGE = 1000.0F;
    public static final float MAX_ATTACK_RANGE = 32.0F;
    public static final float MIN_ATTACK_INTERVAL_SECONDS = 0.1F;
    public static final float MAX_ATTACK_INTERVAL_SECONDS = 120.0F;
    public static final float MAX_EFFECT_DURATION_SECONDS = 1000.0F;
    public static final int MAX_EFFECT_AMPLIFIER = 10;
    public static final int MAX_ATTACK_EFFECTS = 20;
    // The enchantment-like target effects, allowed up to level 10 (beyond the vanilla caps).
    // Melee and ranged have their own sets: sharpness/power boost damage, knockback/punch push,
    // fire_aspect/flame ignite, multishot fires extra projectiles, piercing goes through targets.
    public static final java.util.List<String> MELEE_ENCHANTS = java.util.List.of(
            "sharpness", "knockback", "fire_aspect");
    public static final java.util.List<String> RANGED_ENCHANTS = java.util.List.of(
            "power", "punch", "flame", "multishot", "piercing");
    public static final java.util.List<String> ENCHANT_EFFECTS = java.util.List.of(
            "sharpness", "knockback", "fire_aspect", "power", "punch", "flame", "multishot", "piercing");
    public static final float MAX_DISTANCE_PREFERENCE = 50.0F;
    public static final float MAX_AGGRO_RANGE = 50.0F;
    public static final int MAX_GIVE_UP_SECONDS = 1000;
    // Range switching: the data model and API take an unbounded rule list; only the editor caps it. The
    // decision is re-run on this interval (not every tick) and staggered per NPC so the one world-querying
    // metric (enemies in radius) can't spike with many NPCs in combat at once.
    public static final int MAX_RANGE_RULES_GUI = 8;
    public static final float MAX_RANGE_RULE_DISTANCE = 64.0F;
    public static final int MAX_RANGE_RULE_RADIUS = 64;
    public static final int MAX_RANGE_ENEMY_COUNT = 999;
    public static final int RANGE_SWITCH_EVAL_INTERVAL_TICKS = 10;
    // A bow's full-pull ticks: the NPC draws this long before releasing its (still simulated) shot, and
    // a ranged interval shorter than it holds the fully-drawn pose instead of re-drawing (see NpcWarnings).
    public static final int RANGED_DRAW_TICKS = 20;
    // Advanced combat (node system): the engagement radius is the boss's awareness bubble; list caps are
    // editor-only (the data model and API stay unbounded).
    public static final float MAX_ENGAGEMENT_RADIUS = 64.0F;
    public static final float MAX_PLACEMENT_DISTANCE = 64.0F;
    public static final int MAX_PLACEMENT_POINTS = 32;
    public static final int MAX_COMBAT_RULES_GUI = 40;
    public static final int MAX_COMBAT_ATTACKS_GUI = 32;
    public static final int MAX_COMBAT_CONDITIONS_GUI = 12;
    public static final int MAX_COMBAT_ACTIONS_GUI = 16;
    public static final int MIN_FLEE_SECONDS = 1;
    public static final int MAX_FLEE_SECONDS = 1000;
    public static final int MIN_FLEE_DISTANCE = 1;
    public static final int MAX_FLEE_DISTANCE = 64;
    public static final float MAX_PROJECTILE_SPEED = 100.0F;
    public static final float MIN_CURVE_DISTANCE = 10.0F;
    public static final float MAX_CURVE_DISTANCE = 200.0F;
    public static final int MAX_ACCURACY_DISTANCE = 50;
    public static final int MIN_IMPACT_RADIUS = 1;
    public static final int MAX_IMPACT_RADIUS = 32;
    public static final float MAX_IMPACT_PARTICLE_SECONDS = 10.0F;
    public static final int MAX_BOSS_BAR_NAME_LENGTH = 32;
    // Matches the vanilla boss bar colors (BossEvent.BossBarColor).
    public static final java.util.List<String> BOSS_BAR_COLORS =
            java.util.List.of("pink", "blue", "red", "green", "yellow", "purple", "white");
    public static final int MAX_ALLIANCE_NAME_LENGTH = 24;
    public static final int MAX_ALLIANCES = 50;
    public static final int MAX_ZONE_NAME_LENGTH = 24;
    public static final int MAX_ZONES = 50;
    public static final int MIN_CATEGORY_LENGTH = 4;
    public static final int MAX_CATEGORY_LENGTH = 30;
    public static final int MAX_CATEGORIES = 25;
    public static final Pattern CATEGORY_PATTERN =
            Pattern.compile("[a-z0-9]{" + MIN_CATEGORY_LENGTH + "," + MAX_CATEGORY_LENGTH + "}");
    public static final int MAX_JOB_SUB_TYPES = 25;
    public static final int JOB_WEIGHT_TOTAL_PERCENT = 100;
    public static final int MAX_TRADES_PER_SUB_TYPE = 10;
    public static final int MAX_TRADE_SLOTS = 4;
    public static final int MAX_TRADE_SLOT_COUNT = 64;
    public static final int MAX_JOB_XP = 1_000;
    public static final int MAX_QUEST_REWARD_SLOTS = 27;
    public static final int MAX_TRADE_STOCK = 10_000;
    public static final int MAX_RESTOCK_SECONDS = 360_000;
    public static final int MAX_QUEST_TIME_LIMIT_SECONDS = 360_000;
    public static final int MAX_INTERACTION_BLOCK_SECONDS = 1_000;
    public static final int MAX_FARM_CHEST_RADIUS = 32;
    public static final int MAX_FARM_CAPACITY = 1_000;
    public static final int MAX_FARM_IDLE_SECONDS = 1_000;
    // Dialog has no node/choice/depth caps by design — the tree is unbounded. These bound only the
    // text and per-action payloads.
    public static final int MAX_DIALOG_LINE_LENGTH = 512;
    public static final int MAX_DIALOG_CHOICE_LENGTH = 128;
    public static final int MAX_DIALOG_NAME_LENGTH = 32;
    public static final int MAX_DIALOG_FLAG_LENGTH = 64;
    public static final int MAX_DIALOG_COMMAND_LENGTH = 256;
    public static final int MAX_DIALOG_ACTION_ITEMS = 27;
    public static final int MAX_DIALOG_ANCHOR_RADIUS = 64;
    // Patrol routes: every point stays within MAX_ROUTE_ANCHOR_DISTANCE of the first (the anchor) and
    // within MAX_ROUTE_POINT_SPACING of the point before it. The editor stops at the GUI point cap; the
    // API may go up to MAX_ROUTE_POINTS.
    public static final int MAX_ROUTE_NAME_LENGTH = 24;
    public static final int MAX_ROUTES = 100;
    public static final int MIN_ROUTE_POINTS = 2;
    public static final int MAX_ROUTE_POINTS_GUI = 50;
    public static final int MAX_ROUTE_POINTS = 500;
    public static final double MAX_ROUTE_ANCHOR_DISTANCE = 300.0;
    public static final double MIN_ROUTE_POINT_SPACING = 1.0;
    public static final double MAX_ROUTE_POINT_SPACING = 50.0;
    public static final int MAX_ROUTE_WAIT_SECONDS = 3_600;
    // Bodyguard ranges (blocks / seconds). A defense time of 0 means "until the attacker is dead"; a
    // teleport distance of 0 means "never teleport"; a hire time of 0 means "until it gives up".
    public static final int MAX_BODYGUARD_RANGE = 128;
    public static final int MAX_BODYGUARD_STAY_RADIUS = 16;
    public static final int MAX_BODYGUARD_WALK_WITHIN = 25;
    public static final int MAX_BODYGUARD_DEFENSE_SECONDS = 60;
    public static final int MAX_BODYGUARD_RETREAT_DISTANCE = 128;
    public static final int MAX_BODYGUARD_TELEPORT_DISTANCE = 256;
    public static final int MAX_BODYGUARD_HIRE_SECONDS = 360_000;
    public static final int MAX_BODYGUARD_PROTECTED = 50;
    // A teleporting bodyguard lands within this many blocks of its protectee.
    public static final int BODYGUARD_TELEPORT_LANDING_RADIUS = 3;
    // Downloaded player skins kept per world; a skin is name-capped like a custom model id.
    public static final int MAX_DOWNLOADED_SKINS = 500;

    private static final Random RANDOM = new Random();

    private NpcValidation() {
    }

    public static boolean isValidCustomModelId(String modelId) {
        return modelId != null && CUSTOM_MODEL_ID_PATTERN.matcher(modelId).matches();
    }

    public static boolean isValidModelCategory(String category) {
        return category != null && MODEL_CATEGORY_PATTERN.matcher(category).matches();
    }

    // The empty category means uncategorized; it only shows under "All" in the spawn list.
    public static boolean isValidCategory(String category) {
        return category != null && (category.isEmpty() || CATEGORY_PATTERN.matcher(category).matches());
    }

    // 26.2's ChatFormatting no longer exposes getByName/isColor, so the 16 colors are listed
    // explicitly; tag colors are stored as the lowercase enum name.
    public static final java.util.List<ChatFormatting> TAG_COLORS = java.util.List.of(
            ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
            ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
            ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE);

    public static ChatFormatting tagColorByName(String tagColor) {
        if (tagColor == null) {
            return null;
        }
        for (ChatFormatting color : TAG_COLORS) {
            if (color.name().toLowerCase(java.util.Locale.ROOT).equals(tagColor)) {
                return color;
            }
        }
        return null;
    }

    public static boolean isValidTagColor(String tagColor) {
        return tagColorByName(tagColor) != null;
    }

    public static boolean isValidDropChance(float chancePercent) {
        return chancePercent >= MIN_DROP_CHANCE_PERCENT && chancePercent <= MAX_DROP_CHANCE_PERCENT;
    }

    public static boolean isValidMoveSpeed(float speed) {
        return speed >= MIN_MOVE_SPEED && speed <= MAX_MOVE_SPEED;
    }

    public static boolean isValidPathfindingType(String pathfindingType) {
        return net.lunacore.luna_npc.pathfinding.PathfindingType.byId(pathfindingType) != null;
    }

    public static boolean isValidBossBarColor(String color) {
        return color != null && BOSS_BAR_COLORS.contains(color);
    }

    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    public static boolean isValidHealth(int health) {
        return health >= MIN_HEALTH && health <= MAX_HEALTH;
    }

    public static boolean isValidAmbientInterval(int seconds) {
        return seconds >= MIN_AMBIENT_INTERVAL_SECONDS && seconds <= MAX_AMBIENT_INTERVAL_SECONDS;
    }

    public static boolean isValidModelSize(float size) {
        return size >= MIN_MODEL_SIZE && size <= MAX_MODEL_SIZE;
    }

    public static boolean isValidLimbStretch(float stretch) {
        return stretch >= MIN_LIMB_STRETCH && stretch <= MAX_LIMB_STRETCH;
    }

    public static boolean isAllowedNameCharacter(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9');
    }

    public static String generatePlaceholderName() {
        return "npc" + (100_000 + RANDOM.nextInt(900_000));
    }
}
