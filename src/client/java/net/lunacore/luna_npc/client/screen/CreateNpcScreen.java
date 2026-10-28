package net.lunacore.luna_npc.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.client.ClientZones;
import net.lunacore.luna_npc.client.util.FuzzySearch;
import net.lunacore.luna_npc.client.widget.AccuracyCurveWidget;
import net.lunacore.luna_npc.client.widget.ColorSwatchButton;
import net.lunacore.luna_npc.client.widget.ItemSlotButton;
import net.lunacore.luna_npc.client.widget.LabeledSlider;
import net.lunacore.luna_npc.client.widget.RangeSlider;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.api.NpcWarnings;
import net.lunacore.luna_npc.api.NpcZone;
import net.lunacore.luna_npc.api.NpcDialog;
import net.lunacore.luna_npc.api.NpcJobSettings;
import net.lunacore.luna_npc.api.NpcJobType;
import net.lunacore.luna_npc.api.NpcTradeSubType;
import net.lunacore.luna_npc.api.NpcQuestSubType;
import net.lunacore.luna_npc.api.NpcFarmSubType;
import net.lunacore.luna_npc.api.SurfaceMode;
import net.lunacore.luna_npc.entity.LunaNpcEntities;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.network.CreateNpcPayload;
import net.lunacore.luna_npc.network.NpcSummary;
import net.lunacore.luna_npc.network.UpdateNpcPayload;
import net.lunacore.luna_npc.api.NpcAccuracyAnchor;
import net.lunacore.luna_npc.api.NpcAttackEffect;
import net.lunacore.luna_npc.api.NpcAttackSettings;
import net.lunacore.luna_npc.api.NpcProjectileSettings;
import net.lunacore.luna_npc.api.NpcAdvancedCombat;
import net.lunacore.luna_npc.api.NpcCombatMode;
import net.lunacore.luna_npc.api.NpcRangeSwitch;
import net.lunacore.luna_npc.api.NpcBossBarSettings;
import net.lunacore.luna_npc.api.NpcCombatSettings;
import net.lunacore.luna_npc.api.NpcCombatRule;
import net.lunacore.luna_npc.api.NpcCombatTrigger;
import net.lunacore.luna_npc.api.NpcCombatTriggerType;
import net.lunacore.luna_npc.api.NpcCombatAction;
import net.lunacore.luna_npc.api.NpcCombatActionType;
import net.lunacore.luna_npc.api.NpcCombatPlacement;
import net.lunacore.luna_npc.api.NpcTargetSelector;
import net.lunacore.luna_npc.api.NpcValueSource;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.api.NpcDropSettings;
import net.lunacore.luna_npc.npc.NpcEquipment;
import net.lunacore.luna_npc.client.util.ClientRegistries;
import net.lunacore.luna_npc.api.NpcEquipmentSettings;
import net.lunacore.luna_npc.api.NpcItemEnchant;
import net.lunacore.luna_npc.npc.NpcModelCatalog;
import net.lunacore.luna_npc.api.NpcModelSettings;
import net.lunacore.luna_npc.api.NpcHeightPreference;
import net.lunacore.luna_npc.api.NpcImpactSettings;
import net.lunacore.luna_npc.api.NpcReactionSettings;
import net.lunacore.luna_npc.api.NpcMovementSettings;
import net.lunacore.luna_npc.npc.NpcSoundSettings;
import net.lunacore.luna_npc.api.NpcZoneSpawn;
import net.lunacore.luna_npc.api.NpcSpawnSettings;
import net.lunacore.luna_npc.api.NpcAirSpawn;
import net.lunacore.luna_npc.api.NpcWaterSpawn;
import net.lunacore.luna_npc.api.NpcTraitsSettings;
import net.lunacore.luna_npc.api.NpcRegenSettings;
import net.lunacore.luna_npc.npc.NpcValidation;
import net.lunacore.luna_npc.api.NpcWanderSettings;
import net.lunacore.luna_npc.pathfinding.PathfindingType;

public class CreateNpcScreen extends LunaScreen {

    private static final Component TAB_GENERAL = Component.translatable("gui.luna_npc.create_npc.tab.general");
    private static final Component TAB_SOUNDS = Component.translatable("gui.luna_npc.create_npc.tab.sounds");
    private static final Component TAB_INVENTORY = Component.translatable("gui.luna_npc.create_npc.tab.inventory");
    private static final Component TAB_SPAWN = Component.translatable("gui.luna_npc.create_npc.tab.spawn");
    private static final Component TAB_JOBS = Component.translatable("gui.luna_npc.create_npc.tab.jobs");
    private static final Component LABEL_NATURAL_SPAWN = Component.translatable("gui.luna_npc.create_npc.natural_spawn");
    private static final Component NATURAL_SPAWN_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.natural_spawn.tooltip");
    private static final Component LABEL_RESPAWN = Component.translatable("gui.luna_npc.create_npc.respawn");
    private static final Component RESPAWN_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.respawn.tooltip");
    private static final Component LABEL_RESPAWN_TIME = Component.translatable("gui.luna_npc.create_npc.respawn_time");
    private static final Component LABEL_RESPAWN_AT_DEATH = Component.translatable("gui.luna_npc.create_npc.respawn_at_death");
    private static final Component RESPAWN_AT_DEATH_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.respawn_at_death.tooltip");
    private static final Component LABEL_SPAWN_LIGHT = Component.translatable("gui.luna_npc.create_npc.spawn_light");
    private static final Component LABEL_MAX_NEARBY = Component.translatable("gui.luna_npc.create_npc.max_nearby");
    private static final Component MAX_NEARBY_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.max_nearby.tooltip");
    private static final Component LABEL_SPAWN_BIOMES = Component.translatable("gui.luna_npc.create_npc.spawn_biomes");
    private static final Component LABEL_SPAWN_STRUCTURES = Component.translatable("gui.luna_npc.create_npc.spawn_structures");
    private static final Component LABEL_SPAWN_ZONES = Component.translatable("gui.luna_npc.create_npc.spawn_zones");
    private static final Component LABEL_SPAWN_GLOBAL = Component.translatable("gui.luna_npc.create_npc.spawn_global");
    private static final Component SPAWN_GLOBAL_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.spawn_global.tooltip");
    private static final Component LABEL_GLOBAL_DENSITY = Component.translatable("gui.luna_npc.create_npc.global_density");
    private static final Component LABEL_DENSITY = Component.translatable("gui.luna_npc.create_npc.density");
    private static final Component LABEL_ZONE_MAX = Component.translatable("gui.luna_npc.create_npc.zone_max");
    private static final Component ZONE_MAX_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.zone_max.tooltip");
    private static final Component LABEL_AIR_HEIGHT = Component.translatable("gui.luna_npc.create_npc.air_height");
    private static final Component AIR_HEIGHT_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.air_height.tooltip");
    private static final Component LABEL_AIR_ABOVE_GROUND = Component.translatable("gui.luna_npc.create_npc.air_above_ground");
    private static final Component AIR_ABOVE_GROUND_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.air_above_ground.tooltip");
    private static final Component LABEL_WATER_SPAWN_DEPTH = Component.translatable("gui.luna_npc.create_npc.water_spawn_depth");
    private static final Component WATER_SPAWN_DEPTH_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.water_spawn_depth.tooltip");
    private static final Component LABEL_PLACEMENT = Component.translatable("gui.luna_npc.create_npc.placement");
    private static final Component SPAWN_GLOBAL_HINT = Component.translatable("gui.luna_npc.create_npc.spawn_global.hint");
    private static final Component SURFACE_MASTER_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.surface_master.tooltip");
    private static final Component NATURAL_SPAWN_HINT = Component.translatable("gui.luna_npc.create_npc.natural_spawn.hint");
    private static final Component SPAWN_LOOP_WARNING = Component.translatable("gui.luna_npc.create_npc.spawn_loop_warning");
    private static final Component SPAWN_NONE = Component.translatable("gui.luna_npc.create_npc.spawn_none");
    private static final Component LABEL_LEAVE_AREA = Component.translatable("gui.luna_npc.create_npc.leave_area");
    private static final Component LABEL_LEAVE_AREA_COMBAT = Component.translatable("gui.luna_npc.create_npc.leave_area_combat");
    private static final Component LEAVE_AREA_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.leave_area.tooltip");
    private static final Component LEAVE_AREA_COMBAT_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.leave_area_combat.tooltip");
    // Persistence toggles: placedPersistent lives on the General tab, naturalPersistent on the Spawn tab.
    private static final Component LABEL_PERSISTENT = Component.translatable("gui.luna_npc.create_npc.persistent");
    private static final Component PERSISTENT_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.persistent.tooltip");
    private static final Component LABEL_SPAWN_PERSISTENT = Component.translatable("gui.luna_npc.create_npc.spawn_persistent");
    private static final Component SPAWN_PERSISTENT_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.spawn_persistent.tooltip");
    private static final Component SPAWN_PERSISTENT_WARNING = Component.translatable("gui.luna_npc.create_npc.spawn_persistent_warning");
    private static final Component TAB_FACTION = Component.translatable("gui.luna_npc.create_npc.tab.faction");
    private static final Component LABEL_FACTION = Component.translatable("gui.luna_npc.create_npc.faction");
    private static final Component FACTION_HINT = Component.translatable("gui.luna_npc.create_npc.faction_hint");
    private static final Component LABEL_ENEMIES = Component.translatable("gui.luna_npc.create_npc.enemies");
    private static final Component LABEL_MATES = Component.translatable("gui.luna_npc.create_npc.mates");
    private static final Component LABEL_FRIENDLY_FIRE = Component.translatable("gui.luna_npc.create_npc.friendly_fire");
    private static final Component FRIENDLY_FIRE_LOCKED =
            Component.translatable("gui.luna_npc.create_npc.friendly_fire_locked");
    private static final Component TAB_MOVEMENT = Component.translatable("gui.luna_npc.create_npc.tab.movement");
    private static final Component TAB_NAVIGATION = Component.translatable("gui.luna_npc.create_npc.tab.navigation");
    private static final Component TAB_TRAITS = Component.translatable("gui.luna_npc.create_npc.tab.traits");
    private static final Component LABEL_NPC_TYPE = Component.translatable("gui.luna_npc.create_npc.npc_type");
    private static final Component LABEL_CAN_DROWN = Component.translatable("gui.luna_npc.create_npc.can_drown");
    private static final Component LABEL_CAN_SWIM = Component.translatable("gui.luna_npc.create_npc.can_swim");
    private static final Component CAN_SWIM_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.can_swim.tooltip");
    private static final Component LABEL_CAN_OPEN_DOORS = Component.translatable("gui.luna_npc.create_npc.can_open_doors");
    private static final Component CAN_OPEN_DOORS_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.can_open_doors.tooltip");
    private static final Component LABEL_CAN_BREAK_DOORS = Component.translatable("gui.luna_npc.create_npc.can_break_doors");
    private static final Component CAN_BREAK_DOORS_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.can_break_doors.tooltip");
    private static final Component LABEL_TAKE_FALL_DAMAGE = Component.translatable("gui.luna_npc.create_npc.take_fall_damage");
    private static final Component TAKE_FALL_DAMAGE_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.take_fall_damage.tooltip");
    private static final Component LABEL_PROJECTILE_IMMUNE = Component.translatable("gui.luna_npc.create_npc.projectile_immune");
    private static final Component PROJECTILE_IMMUNE_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.projectile_immune.tooltip");
    private static final Component LABEL_FIRE_IMMUNE = Component.translatable("gui.luna_npc.create_npc.fire_immune");
    private static final Component FIRE_IMMUNE_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.fire_immune.tooltip");
    private static final Component LABEL_CHUNK_LOADER = Component.translatable("gui.luna_npc.create_npc.chunk_loader");
    private static final Component CHUNK_LOADER_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.chunk_loader.tooltip");
    private static final Component LABEL_BURN_DAYLIGHT = Component.translatable("gui.luna_npc.create_npc.burn_daylight");
    private static final Component LABEL_AVOID_LIGHT = Component.translatable("gui.luna_npc.create_npc.avoid_light");
    private static final Component AVOID_LIGHT_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.avoid_light.tooltip");
    private static final Component LABEL_AVOID_NIGHT = Component.translatable("gui.luna_npc.create_npc.avoid_night");
    private static final Component AVOID_NIGHT_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.avoid_night.tooltip");
    private static final Component LABEL_LIGHT_LEVEL = Component.translatable("gui.luna_npc.create_npc.light_level");
    private static final Component LABEL_TOLERANCE = Component.translatable("gui.luna_npc.create_npc.tolerance");
    private static final Component HEIGHT_FIXED_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.height_fixed.tooltip");
    private static final Component TOLERANCE_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.tolerance.tooltip");
    private static final Component LABEL_LEAVE_DEPTH_COMBAT = Component.translatable("gui.luna_npc.create_npc.leave_depth_combat");
    private static final Component LEAVE_DEPTH_COMBAT_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.leave_depth_combat.tooltip");
    private static final Component WATER_DEPTH_TOLERANCE_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.water_depth_tolerance.tooltip");
    private static final Component LABEL_CLEAR_ALL = Component.translatable("gui.luna_npc.create_npc.clear_all");
    private static final Component TAB_COMBAT = Component.translatable("gui.luna_npc.create_npc.tab.combat");
    private static final Component SECTION_CLOSE_RANGE = Component.translatable("gui.luna_npc.create_npc.section.close_range");
    private static final Component SECTION_LONG_RANGE = Component.translatable("gui.luna_npc.create_npc.section.long_range");
    private static final Component LABEL_ATTACK_ENABLED = Component.translatable("gui.luna_npc.create_npc.attack_enabled");
    private static final Component LABEL_ATTACK_DAMAGE = Component.translatable("gui.luna_npc.create_npc.attack_damage");
    private static final Component LABEL_ATTACK_RANGE = Component.translatable("gui.luna_npc.create_npc.attack_range");
    private static final Component LABEL_ATTACK_SPEED = Component.translatable("gui.luna_npc.create_npc.attack_speed");
    private static final Component LABEL_ATTACK_EFFECTS = Component.translatable("gui.luna_npc.create_npc.attack_effects");
    private static final Component LABEL_GEAR_STATS = Component.translatable("gui.luna_npc.create_npc.gear_stats");
    private static final Component GEAR_STATS_WARNING = Component.translatable("gui.luna_npc.create_npc.gear_stats.warning");
    private static final Component LABEL_AGGRO_RANGE = Component.translatable("gui.luna_npc.create_npc.aggro_range");
    private static final Component SECTION_TARGETING = Component.translatable("gui.luna_npc.create_npc.section.targeting");
    private static final Component LABEL_REQUIRE_LOS = Component.translatable("gui.luna_npc.create_npc.require_los");
    private static final Component REQUIRE_LOS_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.require_los.tooltip");
    private static final Component LABEL_DIFFICULTY_SCALING = Component.translatable("gui.luna_npc.create_npc.difficulty_scaling");
    private static final Component DIFFICULTY_SCALING_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.difficulty_scaling.tooltip");
    private static final Component LABEL_GIVE_UP = Component.translatable("gui.luna_npc.create_npc.give_up");
    private static final Component GIVE_UP_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.give_up.tooltip");
    private static final Component GIVE_UP_HINT = Component.literal("∞");
    private static final Component LABEL_EMERGENCY_SEEK = Component.translatable("gui.luna_npc.create_npc.emergency_seek");
    private static final Component EMERGENCY_SEEK_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.emergency_seek.tooltip");
    private static final Component LABEL_REACTION = Component.translatable("gui.luna_npc.create_npc.reaction");
    private static final Component REACTION_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.reaction.tooltip");
    private static final Component LABEL_FLEE_TIME = Component.translatable("gui.luna_npc.create_npc.flee_time");
    private static final Component LABEL_FLEE_DISTANCE = Component.translatable("gui.luna_npc.create_npc.flee_distance");
    private static final Component LABEL_DISTANCE_PREFERENCE = Component.translatable("gui.luna_npc.create_npc.distance_preference");
    private static final Component LABEL_FLY_SPEED = Component.translatable("gui.luna_npc.create_npc.fly_speed_projectile");
    private static final Component LABEL_PROJECTILE = Component.translatable("gui.luna_npc.create_npc.projectile");
    private static final Component LABEL_HOMING = Component.translatable("gui.luna_npc.create_npc.homing");
    private static final Component LABEL_CURVE = Component.translatable("gui.luna_npc.create_npc.curve");
    private static final Component CURVE_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.curve.tooltip");
    private static final Component HOMING_WARNING = Component.translatable("gui.luna_npc.create_npc.homing_warning");
    private static final Component DRAW_HELD_WARNING = Component.translatable("gui.luna_npc.create_npc.draw_held_warning");
    private static final Component SECTION_PROJECTILE = Component.translatable("gui.luna_npc.create_npc.section.projectile");
    private static final Component SECTION_ACCURACY = Component.translatable("gui.luna_npc.create_npc.section.accuracy");
    private static final Component LABEL_CURVE_DISTANCE = Component.translatable("gui.luna_npc.create_npc.curve_distance");
    private static final Component LABEL_DYNAMIC_ACCURACY = Component.translatable("gui.luna_npc.create_npc.dynamic_accuracy");
    private static final Component DYNAMIC_ACCURACY_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.dynamic_accuracy.tooltip");
    private static final Component LABEL_FIXED_ACCURACY = Component.translatable("gui.luna_npc.create_npc.fixed_accuracy");
    private static final Component FIXED_ACCURACY_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.fixed_accuracy.tooltip");
    private static final Component ANCHOR_HINT = Component.translatable("gui.luna_npc.create_npc.anchor_hint");
    private static final Component LABEL_REMOVE_ANCHOR = Component.translatable("gui.luna_npc.target_effects.remove");
    private static final Component SECTION_CLOSE_IMPACT = Component.translatable("gui.luna_npc.create_npc.section.close_impact");
    private static final Component SECTION_CLOSE_VISUALS = Component.translatable("gui.luna_npc.create_npc.section.close_visuals");
    private static final Component SECTION_LONG_IMPACT = Component.translatable("gui.luna_npc.create_npc.section.long_impact");
    private static final Component SECTION_LONG_VISUALS = Component.translatable("gui.luna_npc.create_npc.section.long_visuals");
    private static final Component LABEL_IMPACT_DESTROY = Component.translatable("gui.luna_npc.create_npc.impact_destroy");
    private static final Component IMPACT_DESTROY_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.impact_destroy.tooltip");
    private static final Component LABEL_IMPACT_RADIUS = Component.translatable("gui.luna_npc.create_npc.impact_radius");
    private static final Component IMPACT_RADIUS_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.impact_radius.tooltip");
    private static final Component LABEL_IMPACT_PARTICLE = Component.translatable("gui.luna_npc.create_npc.impact_particle");
    private static final Component LABEL_IMPACT_DURATION = Component.translatable("gui.luna_npc.create_npc.impact_duration");
    private static final Component LABEL_PARTICLE_NONE = Component.translatable("gui.luna_npc.create_npc.impact_particle.none");
    private static final Component LABEL_AVOID_WATER = Component.translatable("gui.luna_npc.create_npc.avoid_water");
    private static final Component AVOID_WATER_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.avoid_water.tooltip");
    private static final Component LABEL_AVOID_LAVA = Component.translatable("gui.luna_npc.create_npc.avoid_lava");
    private static final Component AVOID_LAVA_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.avoid_lava.tooltip");
    private static final Component LABEL_PREVIEW = Component.translatable("gui.luna_npc.create_npc.preview");
    private static final Component LABEL_XP_DROP = Component.translatable("gui.luna_npc.create_npc.xp_drop");
    private static final Component LABEL_ITEM_DROP = Component.translatable("gui.luna_npc.create_npc.item_drop");
    private static final Component LABEL_EQUIPMENT = Component.translatable("gui.luna_npc.create_npc.equipment");
    private static final Component LABEL_DROP_CHANCE = Component.translatable("gui.luna_npc.create_npc.drop_chance");
    private static final Component LABEL_NAME = Component.translatable("gui.luna_npc.create_npc.name");
    private static final Component LABEL_SHOW_TAG = Component.translatable("gui.luna_npc.create_npc.show_tag");
    private static final Component LABEL_HEALTH = Component.translatable("gui.luna_npc.create_npc.health");
    private static final Component LABEL_HURT_SOUND = Component.translatable("gui.luna_npc.create_npc.hurt_sound");
    private static final Component LABEL_DIE_SOUND = Component.translatable("gui.luna_npc.create_npc.die_sound");
    private static final Component LABEL_AMBIENT_SOUND = Component.translatable("gui.luna_npc.create_npc.ambient_sound");
    private static final Component LABEL_AGGRO_SOUND = Component.translatable("gui.luna_npc.create_npc.aggro_sound");
    private static final Component LABEL_COMBAT_CLOSE_SOUND = Component.translatable("gui.luna_npc.create_npc.combat_close_sound");
    private static final Component LABEL_COMBAT_FAR_SOUND = Component.translatable("gui.luna_npc.create_npc.combat_far_sound");
    private static final Component LABEL_CLOSE_IMPACT_SOUND = Component.translatable("gui.luna_npc.create_npc.close_impact_sound");
    private static final Component LABEL_FAR_IMPACT_SOUND = Component.translatable("gui.luna_npc.create_npc.far_impact_sound");
    private static final Component SECTION_COMBAT_SOUNDS = Component.translatable("gui.luna_npc.create_npc.section.combat_sounds");
    private static final Component LABEL_AMBIENT_INTERVAL = Component.translatable("gui.luna_npc.create_npc.ambient_interval");
    private static final Component LABEL_CHANGE_MODEL = Component.translatable("gui.luna_npc.create_npc.change_model");
    private static final Component LABEL_TAG_COLOR = Component.translatable("gui.luna_npc.create_npc.tag_color");
    private static final Component LABEL_CATEGORY = Component.translatable("gui.luna_npc.create_npc.category");
    private static final Component CATEGORY_HINT = Component.translatable("gui.luna_npc.create_npc.category_hint");
    private static final Component NO_EQUIPMENT_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.no_equipment");
    private static final Component LABEL_MOVEMENT_TYPE = Component.translatable("gui.luna_npc.create_npc.movement_type");
    private static final Component LABEL_KNOCKBACK = Component.translatable("gui.luna_npc.create_npc.knockback");
    private static final Component LABEL_AVOIDED_BLOCKS = Component.translatable("gui.luna_npc.create_npc.avoided_blocks");
    private static final Component LABEL_HOVER = Component.translatable("gui.luna_npc.create_npc.hover");
    private static final Component LABEL_HOVER_INTERVAL = Component.translatable("gui.luna_npc.create_npc.hover_interval");
    private static final Component LABEL_HOVER_AMOUNT = Component.translatable("gui.luna_npc.create_npc.hover_amount");
    private static final Component LABEL_FLIGHT_HEIGHT = Component.translatable("gui.luna_npc.create_npc.flight_height");
    private static final Component LABEL_WATER_DEPTH = Component.translatable("gui.luna_npc.create_npc.water_depth");
    private static final Component KNOCKBACK_WARNING = Component.translatable("gui.luna_npc.create_npc.knockback_warning");
    private static final int WARNING_COLOR = 0xFFE33B3B;
    private static final int SUMMARY_ROW = 11;
    private static final int SUMMARY_FILL = 0x40000000;
    private static final int SUMMARY_STRIPE = 0x14FFFFFF;
    private static final Component SECTION_PATHFINDING = Component.translatable("gui.luna_npc.create_npc.section.pathfinding");
    private static final Component SECTION_WANDERING = Component.translatable("gui.luna_npc.create_npc.section.wandering");
    private static final Component LABEL_PATHFINDING = Component.translatable("gui.luna_npc.create_npc.pathfinding");
    private static final Component LABEL_PATH_QUALITY = Component.translatable("gui.luna_npc.pathfinding_picker.quality");
    private static final Component LABEL_PATH_COST = Component.translatable("gui.luna_npc.pathfinding_picker.cost");
    private static final Component LABEL_WANDER_BEHAVIOR = Component.translatable("gui.luna_npc.create_npc.wander_behavior");
    private static final Component LABEL_WANDER_RADIUS = Component.translatable("gui.luna_npc.create_npc.wander_radius");
    private static final Component LABEL_MIN_GOAL_DISTANCE = Component.translatable("gui.luna_npc.create_npc.min_goal_distance");
    private static final Component MIN_GOAL_DISTANCE_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.min_goal_distance.tooltip");
    private static final Component LABEL_WANDER_INTERVAL = Component.translatable("gui.luna_npc.create_npc.wander_interval");
    private static final Component LABEL_JUMP_HEIGHT = Component.translatable("gui.luna_npc.create_npc.jump_height");
    private static final Component LABEL_WALK_SPEED = Component.translatable("gui.luna_npc.create_npc.walk_speed");
    private static final Component LABEL_RUN_SPEED = Component.translatable("gui.luna_npc.create_npc.run_speed");
    private static final Component RUN_LINKED_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.run_linked");
    private static final Component RUN_CUSTOM_TOOLTIP = Component.translatable("gui.luna_npc.create_npc.run_custom");
    private static final Component LABEL_BOSS_BAR = Component.translatable("gui.luna_npc.create_npc.bossbar");
    private static final Component LABEL_BOSS_BAR_COLOR = Component.translatable("gui.luna_npc.create_npc.bossbar_color");
    private static final Component LABEL_BOSS_BAR_NAME = Component.translatable("gui.luna_npc.create_npc.bossbar_name");
    private static final Component LABEL_EDIT_ACCESS = Component.translatable("gui.luna_npc.create_npc.edit_access");
    private static final Component BOSS_BAR_NAME_HINT = Component.translatable("gui.luna_npc.create_npc.bossbar_name_hint");
    // Swatch colors match the vanilla boss bar colors in NpcValidation.BOSS_BAR_COLORS order.
    private static final int[] BOSS_BAR_SWATCHES = {0xFFED6EA4, 0xFF3E9BFF, 0xFFE33B3B, 0xFF3BE35C,
            0xFFE3D53B, 0xFFA43BE3, 0xFFF0F0F0};
    private static final Component LABEL_CREATE = Component.translatable("gui.luna_npc.create_npc.create");
    private static final Component LABEL_SAVE = Component.translatable("gui.luna_npc.edit_npc.save");
    private static final List<ChatFormatting> TAG_COLORS = NpcValidation.TAG_COLORS;

    // Speeds are in blocks per second: 80% of the slider travel covers the everyday 0-10 B/S
    // range, the last 20% climbs quadratically to the 50 B/S maximum.
    private static final LabeledSlider.Curve SPEED_CURVE = new LabeledSlider.Curve(
            position -> position <= 0.8 ? position / 0.8 * 10.0
                    : 10.0 + 40.0 * Math.pow((position - 0.8) / 0.2, 2),
            value -> value <= 10.0 ? value / 10.0 * 0.8
                    : 0.8 + 0.2 * Math.sqrt((value - 10.0) / 40.0));
    // Jump height in blocks: 80% of the travel covers 0-5, the rest accelerates to 20.
    private static final LabeledSlider.Curve JUMP_CURVE = new LabeledSlider.Curve(
            position -> position <= 0.8 ? position / 0.8 * 5.0
                    : 5.0 + 15.0 * Math.pow((position - 0.8) / 0.2, 2),
            value -> value <= 5.0 ? value / 5.0 * 0.8
                    : 0.8 + 0.2 * Math.sqrt((value - 5.0) / 15.0));
    // Attack interval in seconds: 80% of the travel covers 0.1-5s, the rest climbs to 120s.
    private static final LabeledSlider.Curve ATTACK_SPEED_CURVE = new LabeledSlider.Curve(
            position -> position <= 0.8 ? 0.1 + position / 0.8 * 4.9
                    : 5.0 + 115.0 * Math.pow((position - 0.8) / 0.2, 2),
            value -> value <= 5.0 ? (value - 0.1) / 4.9 * 0.8
                    : 0.8 + 0.2 * Math.sqrt((value - 5.0) / 115.0));

    private static final int DROP_GRID_COLUMNS = 9;
    private static final int DROP_GRID_ROWS = 4;
    private static final int CONTENT_TOP = 52;
    private static final int CONTENT_HEIGHT = 260;
    private static final int LEFT_PANEL_WIDTH = 150;
    private static final int RIGHT_PANEL_WIDTH = 380;

    // Two-column settings grid inside the right panel — every offset is measured from rightPanelX().
    // A row holds a label→control pair in column one and/or column two; a single control that needs
    // the full width (the ambient-delay field, a range slider, the accuracy graph) instead takes a
    // label at COL1_LABEL_X and its control at FULL_CONTROL_X. Rows are COL_ROW_HEIGHT apart; labels
    // sit at the *_LABEL_X, controls at the *_CONTROL_X, each COL_CONTROL_WIDTH wide.
    private static final int COL_ROW_HEIGHT = 22;
    private static final int COL1_LABEL_X = 10;
    private static final int COL1_CONTROL_X = 86;
    private static final int COL2_LABEL_X = 194;
    private static final int COL2_CONTROL_X = 270;
    private static final int COL_CONTROL_WIDTH = 96;
    private static final int FULL_CONTROL_X = 96;
    // The label→control gap in each grid column; a label wider than this wraps instead of overflowing.
    private static final int COL1_LABEL_MAX = COL1_CONTROL_X - COL1_LABEL_X - 4;
    private static final int COL2_LABEL_MAX = COL2_CONTROL_X - COL2_LABEL_X - 4;

    // The tab strip: as many 64px (2px gap) tab buttons as fit between a small < / > arrow pair.
    // With nine tabs the row no longer fits every window, so the arrows page the visible window.
    private static final int TAB_ROW_Y = 28;
    private static final int TAB_ROW_HEIGHT = 20;
    private static final int TAB_WIDTH = 64;
    private static final int TAB_GAP = 2;
    private static final int TAB_ARROW_WIDTH = 12;

    // The Spawn tab: the respawn band up top, then the natural-spawning band (a light range, the
    // nearby cap, a Biomes/Structures/Cubes method selector) over a searchable list with a weight
    // detail column on the right.
    private static final int SPAWN_RESPAWN_ROW_Y = CONTENT_TOP + 14;
    private static final int SPAWN_RESPAWN_TIME_LABEL_Y = CONTENT_TOP + 38;
    private static final int SPAWN_RESPAWN_TIME_SLIDER_Y = CONTENT_TOP + 50;
    private static final int SPAWN_NATURAL_ROW_Y = CONTENT_TOP + 72;
    private static final int SPAWN_LIGHT_LABEL_Y = CONTENT_TOP + 96;
    private static final int SPAWN_LIGHT_SLIDER_Y = CONTENT_TOP + 108;
    // The light slider spans the full width; the surface/cave placement moved into the detail column.
    private static final int SPAWN_LIGHT_SLIDER_WIDTH = RIGHT_PANEL_WIDTH - 20;
    private static final int SPAWN_METHOD_ROW_Y = CONTENT_TOP + 128;
    private static final int SPAWN_SEARCH_Y = CONTENT_TOP + 150;
    private static final int SPAWN_LIST_TOP = CONTENT_TOP + 170;
    // The list is shortened by a row so the wild-spawn persistence checkbox has a clean strip beneath it.
    private static final int SPAWN_LIST_HEIGHT = CONTENT_HEIGHT - 194;
    private static final int SPAWN_PERSISTENT_ROW_Y = CONTENT_TOP + CONTENT_HEIGHT - 20;
    private static final int SPAWN_LIST_WIDTH = 226;
    private static final int SPAWN_DETAIL_X = 244;
    private static final int SPAWN_DETAIL_WIDTH = 126;
    // Detail column: row 1 holds the biome Global toggle or the structure/zone chance field; row 2 the
    // density % slider (per-biome or global — same spot, so toggling Global never moves it) or a zone's
    // max count; the last two rows are the movement-type placement block (ground button / air ranges /
    // water depth).
    private static final int SPAWN_DETAIL_ROW1_LABEL_Y = CONTENT_TOP + 145;
    private static final int SPAWN_DETAIL_ROW1_CTRL_Y = CONTENT_TOP + 154;
    private static final int SPAWN_DETAIL_ROW2_LABEL_Y = CONTENT_TOP + 176;
    private static final int SPAWN_DETAIL_ROW2_CTRL_Y = CONTENT_TOP + 185;
    private static final int SPAWN_AIR_HEIGHT_LABEL_Y = CONTENT_TOP + 207;
    private static final int SPAWN_AIR_HEIGHT_SLIDER_Y = CONTENT_TOP + 216;
    private static final int SPAWN_AIR_GROUND_LABEL_Y = CONTENT_TOP + 234;
    private static final int SPAWN_AIR_GROUND_SLIDER_Y = CONTENT_TOP + 243;

    // The Jobs tab: the job-type row, an Interaction band (the two on-hit block toggles + their
    // second sliders), a Sounds row (the job's feedback sound overrides), then the weighted
    // sub-type builder list with its Add/Remove/Duplicate/Edit row.
    private static final int JOB_INTERACTION_TITLE_Y = CONTENT_TOP + 42;
    private static final int JOB_BLOCK_TOGGLE_ROW_Y = CONTENT_TOP + 54;
    private static final int JOB_BLOCK_SLIDER_ROW_Y = CONTENT_TOP + 76;
    private static final int JOB_SOUND_TITLE_Y = CONTENT_TOP + 102;
    private static final int JOB_SOUND_ROW_Y = CONTENT_TOP + 114;
    private static final int JOB_SUBTYPE_TITLE_Y = CONTENT_TOP + 140;
    private static final int JOB_SUBTYPE_LIST_TOP = CONTENT_TOP + 152;
    private static final int JOB_SUBTYPE_LIST_HEIGHT = CONTENT_HEIGHT - 188;
    private static final int JOB_BUTTON_ROW_Y = CONTENT_TOP + CONTENT_HEIGHT - 26;

    // Faction tab vertical layout: the picker list (top), the friendly-fire checkbox with its
    // "Set by alliance" hint, then the wrapped+clipped enemies/protects readout at the bottom.
    private static final int FACTION_LIST_TOP = CONTENT_TOP + 24;
    private static final int FACTION_LIST_HEIGHT = 150;
    private static final int FACTION_FF_ROW_Y = CONTENT_TOP + 178;
    private static final int FACTION_HINT_Y = CONTENT_TOP + 200;
    private static final int FACTION_READOUT_TOP = CONTENT_TOP + 214;

    // The Navigation tab: the pathfinding section (algorithm button with its quality/cost star
    // lines) sits above the wandering section — behavior and radius share a row, then the min walk
    // distance, the min-max interval band, and the avoid water/lava checkbox pair.
    private static final int NAV_PATH_TITLE_Y = CONTENT_TOP + 8;
    private static final int NAV_ALGORITHM_ROW_Y = CONTENT_TOP + 22;
    private static final int NAV_QUALITY_LINE_Y = CONTENT_TOP + 48;
    private static final int NAV_COST_LINE_Y = CONTENT_TOP + 62;
    private static final int NAV_WANDER_TITLE_Y = CONTENT_TOP + 84;
    private static final int NAV_BEHAVIOR_ROW_Y = CONTENT_TOP + 98;
    private static final int NAV_RADIUS_ROW_Y = CONTENT_TOP + 98;
    private static final int NAV_MIN_GOAL_ROW_Y = CONTENT_TOP + 122;
    private static final int NAV_INTERVAL_LABEL_Y = CONTENT_TOP + 148;
    private static final int NAV_INTERVAL_SLIDER_Y = CONTENT_TOP + 160;
    private static final int NAV_AVOID_FLUIDS_ROW_Y = CONTENT_TOP + 186;

    private enum EditorTab {
        GENERAL,
        TRAITS,
        MOVEMENT,
        NAVIGATION,
        COMBAT,
        FACTION,
        SOUNDS,
        INVENTORY,
        SPAWN,
        JOBS
    }

    private enum SpawnMethod { BIOMES, STRUCTURES, ZONES }

    private EditorTab activeTab = EditorTab.GENERAL;

    private String npcName = NpcValidation.generatePlaceholderName();
    private boolean nameTagShown = NpcDefaults.NAME_TAG_SHOWN;
    private String healthText = String.valueOf(NpcDefaults.HEALTH);
    private String hurtSoundId = NpcDefaults.HURT_SOUND_ID;
    private String dieSoundId = NpcDefaults.DIE_SOUND_ID;
    private String ambientSoundId = NpcDefaults.AMBIENT_SOUND_ID;
    private String aggroSoundId = NpcDefaults.AGGRO_SOUND_ID;
    private String combatCloseSoundId = NpcDefaults.COMBAT_CLOSE_SOUND_ID;
    private String combatFarSoundId = NpcDefaults.COMBAT_FAR_SOUND_ID;
    private String closeImpactSoundId = NpcDefaults.CLOSE_IMPACT_SOUND_ID;
    private String farImpactSoundId = NpcDefaults.FAR_IMPACT_SOUND_ID;
    private String ambientIntervalText = String.valueOf(NpcDefaults.AMBIENT_INTERVAL_SECONDS);
    private NpcModelSettings modelSettings = NpcModelSettings.defaults();
    private String tagColorId = NpcDefaults.TAG_COLOR;
    private final java.util.Set<String> allianceIds =
            new java.util.LinkedHashSet<>(java.util.List.of(NpcDefaults.ALLIANCE));
    private net.lunacore.luna_npc.api.NpcSpawnSettings spawnSettings =
            net.lunacore.luna_npc.api.NpcSpawnSettings.defaults();
    private NpcJobSettings jobSettings = NpcJobSettings.defaults();
    // Mutable working copy of jobSettings; every edit rebuilds the immutable record (mirrors spawn).
    private NpcJobType jobType = NpcJobType.NONE;
    private boolean blockOnAnyHit;
    private int blockAllSeconds;
    private boolean blockAttacker;
    private int blockAttackerSeconds;
    private final List<NpcTradeSubType> tradeSubTypes = new ArrayList<>();
    private final List<NpcQuestSubType> questSubTypes = new ArrayList<>();
    private final List<NpcFarmSubType> farmSubTypes = new ArrayList<>();
    private final List<net.lunacore.luna_npc.api.NpcPatrolSubType> patrolSubTypes = new ArrayList<>();
    private net.lunacore.luna_npc.api.NpcBodyguardSettings bodyguardSettings =
            net.lunacore.luna_npc.api.NpcBodyguardSettings.defaults();
    private boolean offerAllQuests;
    private String tradeSuccessSound = "";
    private String tradeFailSound = "";
    private String questCompleteSound = "";
    private NpcDialog dialog = NpcDialog.defaults();
    private boolean dialogSurvivesNpcDeath;
    private boolean dialogSurvivesPlayerDeath;
    private int jobSubTypeSelected = -1;
    private String categoryText = NpcDefaults.CATEGORY;
    private net.lunacore.luna_npc.api.NpcEditAccess editAccess = net.lunacore.luna_npc.api.NpcEditAccess.GUI_ONLY;
    private NpcEquipmentSettings equipmentSettings = NpcEquipmentSettings.empty();
    private int xpDropMin = NpcDefaults.XP_DROP_MIN;
    private int xpDropMax = NpcDefaults.XP_DROP_MAX;
    private final Map<Integer, NpcDropSettings.NpcItemDrop> itemDrops = new TreeMap<>();
    private int selectedDropSlot = -1;
    private float walkSpeed = NpcDefaults.WALK_SPEED;
    private boolean runSpeedEnabled;
    private float runSpeed = NpcDefaults.WALK_SPEED;
    private float jumpHeight = NpcDefaults.JUMP_HEIGHT;
    private String movementType = NpcDefaults.MOVEMENT_TYPE;
    private final List<String> avoidedBlocks = new ArrayList<>();
    private boolean hoverEnabled;
    private int hoverIntervalMs = NpcDefaults.HOVER_INTERVAL_MS;
    private float hoverAmount = NpcDefaults.HOVER_AMOUNT;
    private int flightHeight = NpcDefaults.FLIGHT_HEIGHT;
    private int waterDepth = NpcDefaults.WATER_DEPTH;
    private float knockback = NpcDefaults.KNOCKBACK;
    private String pathfindingType = NpcDefaults.PATHFINDING_TYPE;
    private String wanderBehavior = NpcWanderSettings.recommendedBehavior(NpcDefaults.MOVEMENT_TYPE);
    private int wanderMinSeconds = NpcDefaults.WANDER_MIN_SECONDS;
    private int wanderMaxSeconds = NpcDefaults.WANDER_MAX_SECONDS;
    private int wanderRadius = NpcDefaults.WANDER_RADIUS;
    private int wanderMinGoalDistance = NpcDefaults.WANDER_MIN_GOAL_DISTANCE;
    private boolean flightHeightFixed;
    private int flightHeightRange = NpcDefaults.HEIGHT_RANGE;
    private boolean waterDepthFixed;
    private int waterDepthRange = NpcDefaults.HEIGHT_RANGE;
    private String npcType = NpcDefaults.NPC_TYPE;
    private boolean avoidWater;
    private boolean avoidLava = true;
    private boolean canDrown = true;
    private boolean canSwim = true;
    private boolean burnInDaylight;
    private boolean avoidLight;
    private int avoidLightLevel = NpcDefaults.AVOID_LIGHT_LEVEL;
    private boolean avoidNight;
    private int avoidNightLevel = NpcDefaults.AVOID_LIGHT_LEVEL;
    private boolean emergencySeek = NpcDefaults.EMERGENCY_SEEK;
    private boolean canOpenDoors;
    private boolean canBreakDoors;
    private boolean takeFallDamage;
    private boolean projectileImmune;
    private boolean fireImmune;
    private boolean chunkLoader;
    private String reactionMode = NpcDefaults.REACTION;
    private String fleeTimeText = String.valueOf(NpcDefaults.FLEE_SECONDS);
    private int fleeDistance = NpcDefaults.FLEE_DISTANCE;
    private NpcRegenSettings regenSettings = NpcRegenSettings.defaults();
    private boolean meleeEnabled = true;
    private String meleeDamageText = trimNumber(NpcDefaults.ATTACK_DAMAGE);
    private float meleeRange = NpcDefaults.MELEE_RANGE;
    private float meleeIntervalSeconds = NpcDefaults.ATTACK_INTERVAL_SECONDS;
    private final List<NpcAttackEffect> meleeEffects = new ArrayList<>();
    private boolean meleeImpactDestroy;
    private int meleeImpactRadius = NpcDefaults.IMPACT_RADIUS;
    private String meleeImpactParticleId = NpcDefaults.IMPACT_PARTICLE;
    private float meleeImpactParticleSeconds = NpcDefaults.IMPACT_PARTICLE_SECONDS;
    private boolean rangedEnabled;
    private String rangedDamageText = trimNumber(NpcDefaults.ATTACK_DAMAGE);
    private float rangedIntervalSeconds = NpcDefaults.ATTACK_INTERVAL_SECONDS;
    private final List<NpcAttackEffect> rangedEffects = new ArrayList<>();
    private boolean rangedImpactDestroy;
    private int rangedImpactRadius = NpcDefaults.IMPACT_RADIUS;
    private String rangedImpactParticleId = NpcDefaults.IMPACT_PARTICLE;
    private float rangedImpactParticleSeconds = NpcDefaults.IMPACT_PARTICLE_SECONDS;
    private float aggroRange = NpcDefaults.AGGRO_RANGE;
    private boolean requireLos = NpcDefaults.REQUIRE_LINE_OF_SIGHT;
    private boolean difficultyScaling = NpcDefaults.DIFFICULTY_SCALING;
    // Empty means the infinite default; otherwise 1..1000 seconds.
    private String giveUpText = "";
    private float distancePreference = NpcDefaults.DISTANCE_PREFERENCE;
    // This NPC's own vote; the picked alliance's rule can override it (see refreshFriendlyFireControl).
    private boolean factionFriendlyFire = NpcDefaults.FRIENDLY_FIRE;
    private String projectileItemId = NpcDefaults.PROJECTILE_ITEM;
    private float projectileSpeed = NpcDefaults.PROJECTILE_SPEED;
    private boolean homingEnabled = true;
    private boolean curveEnabled;
    private float curveMaxDistance = NpcDefaults.CURVE_MAX_DISTANCE;
    private boolean dynamicAccuracy = true;
    private int fixedAccuracy = 100;
    private final List<NpcAccuracyAnchor> accuracyAnchors = new ArrayList<>();
    private boolean bossBarEnabled;
    private boolean bossBarColorExpanded;
    private String bossBarColor = NpcDefaults.BOSS_BAR_COLOR;
    private String bossBarName = "";

    // One button per EditorTab (indexed by ordinal) plus the two paging arrows; layoutTabBar()
    // positions the visible window and firstVisibleTab is the leftmost shown tab.
    private final Button[] tabButtons = new Button[EditorTab.values().length];
    private Button tabLeftArrow;
    private Button tabRightArrow;
    private int firstVisibleTab;
    private final List<AbstractWidget> combatTabWidgets = new ArrayList<>();
    // The Combat tab holds more rows than the panel; widgets scroll by their recorded base y.
    private final Map<AbstractWidget, Integer> combatWidgetBaseY = new java.util.LinkedHashMap<>();
    private int combatScroll;
    private EditBox meleeDamageField;
    private EditBox rangedDamageField;
    private LabeledSlider curveMaxDistanceSlider;
    private LabeledSlider fixedAccuracySlider;
    private Button meleeImpactParticleButton;
    private Button rangedImpactParticleButton;
    private AccuracyCurveWidget accuracyCurve;
    private Button removeAnchorButton;
    // Combat tab widgets kept as fields so layoutCombat() can position and collapse them.
    private LabeledSlider aggroSlider;
    private AbstractWidget meleeEnabledButton;
    private LabeledSlider meleeRangeSlider;
    private LabeledSlider meleeSpeedSlider;
    private Button meleeEffectsButton;
    private Checkbox meleeDestroyBox;
    private LabeledSlider meleeRadiusSlider;
    private LabeledSlider meleeDurationSlider;
    private AbstractWidget rangedEnabledButton;
    private LabeledSlider rangedDistanceSlider;
    private LabeledSlider rangedSpeedSlider;
    private Button rangedEffectsButton;
    private Checkbox rangedDestroyBox;
    private LabeledSlider rangedRadiusSlider;
    private LabeledSlider rangedDurationSlider;
    private ItemSlotButton projectileSlot;
    private LabeledSlider projectileSpeedSlider;
    private Checkbox homingBox;
    private Checkbox curveBox;
    private Checkbox dynamicAccuracyBox;
    private AbstractWidget requireLosButton;
    private AbstractWidget difficultyScalingButton;
    private EditBox giveUpField;
    private NpcRangeSwitch rangeSwitch = NpcRangeSwitch.defaults();
    private Button rangeSwitchButton;
    // The Combat tab's Simple/Advanced master switch chooses which combat brain runs: SIMPLE is the
    // scrolling attack config below; ADVANCED is the node-based rule engine edited on its own screen. Both
    // configs are kept; combatMode picks the live one.
    private boolean combatAdvancedView;
    private NpcAdvancedCombat advancedCombat = NpcAdvancedCombat.defaults();
    // ON_COMBAT_START re-applies the seeded nav hold at every fight, so it only has to outlast one fight;
    // an hour of ticks is comfortably longer than any realistic encounter.
    private static final int NAV_HOLD_TICKS = 72000;
    private Button combatNormalButton;
    private Button combatAdvancedButton;
    private Button combatRulesButton;
    private Checkbox gearStatsBox;
    private Button createButton;
    private EditBox nameField;
    private EditBox healthField;
    private CycleButton<Boolean> showTagButton;
    private Button tagColorButton;
    private EditBox categoryField;
    private Checkbox placedPersistentBox;
    private LabeledSlider walkSpeedSlider;
    private Button runLinkButton;
    private LabeledSlider runSpeedSlider;
    private final List<AbstractWidget> movementTabWidgets = new ArrayList<>();
    private final ItemSlotButton[] avoidedBlockSlots = new ItemSlotButton[NpcValidation.MAX_AVOIDED_BLOCKS];
    private LabeledSlider knockbackSlider;
    private CycleButton<Boolean> hoverButton;
    private LabeledSlider hoverIntervalSlider;
    private LabeledSlider hoverAmountSlider;
    private LabeledSlider flightHeightSlider;
    private LabeledSlider waterDepthSlider;
    private Button movementTypeButton;
    private LabeledSlider jumpHeightSlider;
    private Button clearAvoidedBlocksButton;
    private Checkbox flightHeightFixedBox;
    private LabeledSlider flightHeightRangeSlider;
    private Checkbox waterDepthLeaveBox;
    private LabeledSlider waterDepthRangeSlider;
    private final List<AbstractWidget> traitsTabWidgets = new ArrayList<>();
    private Button npcTypeButton;
    private Checkbox canSwimBox;
    private LabeledSlider avoidLightLevelSlider;
    private LabeledSlider avoidNightLevelSlider;
    private Checkbox emergencySeekBox;
    private Button reactionButton;
    private EditBox fleeTimeField;
    private LabeledSlider fleeDistanceSlider;
    private final List<AbstractWidget> navigationTabWidgets = new ArrayList<>();
    private Button pathfindingButton;
    private Button wanderBehaviorButton;
    private LabeledSlider wanderRadiusSlider;
    private LabeledSlider wanderMinGoalSlider;
    private RangeSlider wanderIntervalSlider;
    private CycleButton<Boolean> bossBarButton;
    private final ColorSwatchButton[] bossBarSwatches = new ColorSwatchButton[BOSS_BAR_SWATCHES.length];
    private EditBox bossBarNameField;
    private CycleButton<net.lunacore.luna_npc.api.NpcEditAccess> editAccessButton;
    private Button changeModelButton;
    private final List<AbstractWidget> soundTabWidgets = new ArrayList<>();
    private final List<AbstractWidget> factionTabWidgets = new ArrayList<>();
    private net.lunacore.luna_npc.client.widget.SimpleSelectionList factionList;
    private Checkbox friendlyFireBox;
    // True when the picked alliance forces the rule, so the "Set by alliance" hint should draw.
    private boolean friendlyFireLocked;
    private final List<AbstractWidget> inventoryTabWidgets = new ArrayList<>();
    private final ItemSlotButton[] equipmentSlots = new ItemSlotButton[7];
    private final ItemSlotButton[] dropSlots = new ItemSlotButton[DROP_GRID_COLUMNS * DROP_GRID_ROWS];
    private ChanceSlider chanceSlider;
    private EditBox ambientIntervalField;
    private final List<AbstractWidget> spawnTabWidgets = new ArrayList<>();
    // Mutable working copy of spawnSettings; every edit rebuilds the immutable record.
    private boolean respawnEnabled;
    private int respawnMinSeconds;
    private int respawnMaxSeconds;
    private boolean respawnAtDeath;
    private boolean naturalSpawnEnabled;
    private int globalBiomeWeight;
    private boolean useGlobalBiome;
    private NpcAirSpawn airSpawn = NpcAirSpawn.defaults();
    private NpcWaterSpawn waterSpawn = NpcWaterSpawn.defaults();
    private int maxNearby;
    private int minSpawnLight;
    private int maxSpawnLight;
    private boolean leaveArea;
    private boolean leaveAreaInCombat;
    // Persistence: placedPersistent (default on) keeps a hand-placed/API NPC from despawning; the two
    // live in the spawn settings record but placedPersistent is shown on the General tab and
    // naturalPersistent (default off — wild spawns despawn like vanilla mobs) on the Spawn tab.
    private boolean placedPersistent = true;
    private boolean naturalPersistent;
    private final Map<String, Integer> biomeWeights = new HashMap<>();
    private final Map<String, Integer> structureWeights = new HashMap<>();
    private final List<NpcZoneSpawn> zoneSpawns = new ArrayList<>();
    private SpawnMethod spawnMethod = SpawnMethod.BIOMES;
    private String spawnSelectedId = "";
    private String spawnQuery = "";
    private boolean populatingSpawnFields;
    private record SpawnOption(String id, Component label, String search) { }
    private final List<SpawnOption> biomeOptions = new ArrayList<>();
    private final List<SpawnOption> structureOptions = new ArrayList<>();
    private CycleButton<Boolean> respawnButton;
    private CycleButton<Boolean> respawnAtDeathButton;
    private RangeSlider respawnTimeSlider;
    private CycleButton<Boolean> naturalSpawnButton;
    private RangeSlider spawnLightSlider;
    private EditBox maxNearbyField;
    private Button spawnBiomesButton;
    private Button spawnStructuresButton;
    private Button spawnZonesButton;
    private EditBox spawnSearchField;
    private SimpleSelectionList spawnList;
    private CycleButton<Boolean> globalBiomeToggle;
    private LabeledSlider globalWeightSlider;
    private LabeledSlider densitySlider;
    private EditBox zoneMaxField;
    private RangeSlider airHeightSlider;
    private RangeSlider airGroundSlider;
    private RangeSlider waterDepthSpawnSlider;
    private SurfaceMode spawnSurfaceMode = SurfaceMode.ANYWHERE;
    private Button surfaceModeButton;
    private Checkbox naturalPersistentBox;
    private Checkbox leaveAreaBox;
    private Checkbox leaveAreaCombatBox;
    private final List<AbstractWidget> jobsTabWidgets = new ArrayList<>();
    private Button jobTypeButton;
    private Button bodyguardButton;
    private CycleButton<Boolean> offerAllQuestsButton;
    private CycleButton<Boolean> blockAnyHitButton;
    private CycleButton<Boolean> blockAttackerButton;
    private LabeledSlider blockAllSecondsSlider;
    private LabeledSlider blockAttackerSecondsSlider;
    private final List<AbstractWidget> tradeSoundWidgets = new ArrayList<>();
    private final List<AbstractWidget> questSoundWidgets = new ArrayList<>();
    private SimpleSelectionList subTypeList;
    private Button addSubTypeButton;
    private Button removeSubTypeButton;
    private Button duplicateSubTypeButton;
    private Button editSubTypeButton;
    private EditBox dialogNameField;
    private CycleButton<Boolean> dialogSurviveNpcButton;
    private CycleButton<Boolean> dialogSurvivePlayerButton;
    private Button dialogGraphButton;
    private NpcEntity previewEntity;

    private final NpcSummary editing;

    public CreateNpcScreen() {
        this(null);
    }

    public CreateNpcScreen(NpcSummary editing) {
        super(Component.translatable(editing == null
                ? "gui.luna_npc.create_npc.title" : "gui.luna_npc.edit_npc.title"));
        this.editing = editing;
        if (editing != null) {
            npcName = editing.name();
            nameTagShown = editing.nameTagShown();
            healthText = String.valueOf(editing.health());
            hurtSoundId = editing.sounds().hurtSoundId();
            dieSoundId = editing.sounds().dieSoundId();
            ambientSoundId = editing.sounds().ambientSoundId();
            aggroSoundId = editing.sounds().aggroSoundId();
            combatCloseSoundId = editing.sounds().combatCloseSoundId();
            combatFarSoundId = editing.sounds().combatFarSoundId();
            closeImpactSoundId = editing.sounds().closeImpactSoundId();
            farImpactSoundId = editing.sounds().farImpactSoundId();
            ambientIntervalText = String.valueOf(editing.sounds().ambientIntervalSeconds());
            modelSettings = editing.model();
            tagColorId = editing.tagColor();
            categoryText = editing.category();
            editAccess = editing.editAccess();
            equipmentSettings = editing.equipment();
            xpDropMin = editing.dropSettings().xpMin();
            xpDropMax = editing.dropSettings().xpMax();
            for (NpcDropSettings.NpcItemDrop drop : editing.dropSettings().drops()) {
                itemDrops.put(drop.slot(), drop);
            }
            walkSpeed = editing.movement().walkSpeed();
            runSpeedEnabled = editing.movement().runSpeedEnabled();
            runSpeed = editing.movement().runSpeed();
            jumpHeight = editing.movement().jumpHeight();
            movementType = editing.movement().movementType();
            avoidedBlocks.addAll(editing.movement().avoidedBlocks());
            hoverEnabled = editing.movement().hoverEnabled();
            hoverIntervalMs = editing.movement().hoverIntervalMs();
            hoverAmount = editing.movement().hoverAmount();
            flightHeight = editing.movement().flightHeight();
            waterDepth = editing.movement().waterDepth();
            knockback = editing.movement().knockback();
            pathfindingType = editing.movement().pathfindingType();
            wanderBehavior = editing.movement().wander().behavior();
            wanderMinSeconds = editing.movement().wander().minSeconds();
            wanderMaxSeconds = editing.movement().wander().maxSeconds();
            wanderRadius = editing.movement().wander().radius();
            wanderMinGoalDistance = editing.movement().wander().minGoalDistance();
            flightHeightFixed = editing.movement().flightHeightPreference().fixed();
            flightHeightRange = editing.movement().flightHeightPreference().range();
            waterDepthFixed = editing.movement().waterDepthPreference().fixed();
            waterDepthRange = editing.movement().waterDepthPreference().range();
            npcType = editing.traits().npcType();
            avoidWater = editing.traits().avoidWater();
            avoidLava = editing.traits().avoidLava();
            canDrown = editing.traits().canDrown();
            canSwim = editing.traits().canSwim();
            burnInDaylight = editing.traits().burnInDaylight();
            avoidLight = editing.traits().avoidLight();
            avoidLightLevel = editing.traits().avoidLightLevel();
            avoidNight = editing.traits().avoidNight();
            avoidNightLevel = editing.traits().avoidNightLevel();
            emergencySeek = editing.traits().emergencySeek();
            canOpenDoors = editing.traits().canOpenDoors();
            canBreakDoors = editing.traits().canBreakDoors();
            takeFallDamage = editing.traits().takeFallDamage();
            projectileImmune = editing.traits().projectileImmune();
            fireImmune = editing.traits().fireImmune();
            chunkLoader = editing.traits().chunkLoader();
            reactionMode = editing.traits().reaction().mode();
            fleeTimeText = String.valueOf(editing.traits().reaction().fleeSeconds());
            fleeDistance = editing.traits().reaction().fleeDistance();
            regenSettings = editing.traits().regen();
            meleeEnabled = editing.combat().melee().enabled();
            meleeDamageText = trimNumber(editing.combat().melee().damage());
            meleeRange = editing.combat().melee().range();
            meleeIntervalSeconds = editing.combat().melee().intervalSeconds();
            meleeEffects.addAll(editing.combat().melee().effects());
            meleeImpactDestroy = editing.combat().melee().impact().destroyBlocks();
            meleeImpactRadius = editing.combat().melee().impact().radius();
            meleeImpactParticleId = editing.combat().melee().impact().particleId();
            meleeImpactParticleSeconds = editing.combat().melee().impact().particleSeconds();
            rangedEnabled = editing.combat().ranged().enabled();
            rangedDamageText = trimNumber(editing.combat().ranged().damage());
            rangedIntervalSeconds = editing.combat().ranged().intervalSeconds();
            rangedEffects.addAll(editing.combat().ranged().effects());
            rangedImpactDestroy = editing.combat().ranged().impact().destroyBlocks();
            rangedImpactRadius = editing.combat().ranged().impact().radius();
            rangedImpactParticleId = editing.combat().ranged().impact().particleId();
            rangedImpactParticleSeconds = editing.combat().ranged().impact().particleSeconds();
            aggroRange = editing.combat().aggroRange();
            requireLos = editing.combat().requireLineOfSight();
            difficultyScaling = editing.combat().difficultyScaling();
            giveUpText = editing.combat().giveUpSeconds() <= 0 ? ""
                    : String.valueOf(editing.combat().giveUpSeconds());
            distancePreference = editing.combat().distancePreference();
            factionFriendlyFire = editing.combat().friendlyFire();
            projectileItemId = editing.combat().projectile().itemId();
            projectileSpeed = editing.combat().projectile().speedBps();
            homingEnabled = editing.combat().projectile().homing();
            curveEnabled = editing.combat().projectile().curve();
            curveMaxDistance = editing.combat().projectile().curveMaxDistance();
            dynamicAccuracy = editing.combat().projectile().dynamicAccuracy();
            fixedAccuracy = editing.combat().projectile().fixedAccuracy();
            accuracyAnchors.addAll(editing.combat().projectile().accuracyAnchors());
            rangeSwitch = editing.combat().rangeSwitch();
            advancedCombat = editing.combat().advanced();
            combatAdvancedView = editing.combat().combatMode() == NpcCombatMode.ADVANCED;
            bossBarEnabled = editing.bossBar().enabled();
            bossBarColor = editing.bossBar().color();
            bossBarName = editing.bossBar().customName();
            allianceIds.clear();
            allianceIds.addAll(editing.allianceIds());
            spawnSettings = editing.spawn();
            jobSettings = editing.jobs();
        }
        // Seed the spawn working fields (from a loaded NPC, or from defaults for a new one) so a
        // leave-area toggle on the Movement tab rebuilds the settings without the never-opened Spawn
        // tab's fields defaulting to zero and wiping them — and so a fresh NPC starts from the real
        // defaults (roams freely, full light band).
        seedSpawnState();
        seedJobState();
    }

    private int leftPanelX() {
        return this.width / 2 - 271;
    }

    private int rightPanelX() {
        return this.width / 2 - 109;
    }

    private int bottomButtonsY() {
        return Math.min(this.height - 26, CONTENT_TOP + CONTENT_HEIGHT + 10);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int leftX = leftPanelX();
        int rightX = rightPanelX();

        if (previewEntity == null && Minecraft.getInstance().level != null) {
            previewEntity = new NpcEntity(LunaNpcEntities.NPC, Minecraft.getInstance().level);
            // Entity ids are only assigned when an entity joins a world; the renderer requires one,
            // so this detached preview entity needs a manual id. Negative avoids real entity ids.
            previewEntity.setId(-1);
        }
        if (previewEntity != null) {
            previewEntity.applyModel(modelSettings);
            previewEntity.applyEquipment(equipmentSettings);
        }

        // Nine tabs no longer fit one row, so a computed strip pages between a < / > arrow pair
        // (see layoutTabBar). Every button starts at x 0 and is positioned by the layout.
        for (EditorTab tab : EditorTab.values()) {
            tabButtons[tab.ordinal()] = addRenderableWidget(Button.builder(tabLabel(tab),
                    button -> setActiveTab(tab)).bounds(0, TAB_ROW_Y, TAB_WIDTH, TAB_ROW_HEIGHT).build());
        }
        tabLeftArrow = addRenderableWidget(Button.builder(Component.literal("<"), button -> pageTabs(-1))
                .bounds(0, TAB_ROW_Y, TAB_ARROW_WIDTH, TAB_ROW_HEIGHT).build());
        tabRightArrow = addRenderableWidget(Button.builder(Component.literal(">"), button -> pageTabs(1))
                .bounds(0, TAB_ROW_Y, TAB_ARROW_WIDTH, TAB_ROW_HEIGHT).build());

        buildGeneralTab(leftX, rightX);
        buildTraitsTab(rightX);
        buildMovementTab(rightX);
        buildNavigationTab(rightX);
        buildCombatTab(rightX);
        buildFactionTab(rightX);
        buildSoundsTab(rightX);
        buildInventoryTab(leftX, rightX);
        buildSpawnTab(rightX);
        buildJobsTab(rightX);

        createButton = addRenderableWidget(Button.builder(editing == null ? LABEL_CREATE : LABEL_SAVE,
                button -> confirm()).bounds(centerX - 104, bottomButtonsY(), 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX + 4, bottomButtonsY(), 100, 20).build());

        setActiveTab(activeTab);
        updateCreateButtonState();
    }

    private int colRowY(int row) {
        return CONTENT_TOP + 14 + row * COL_ROW_HEIGHT;
    }

    private static Component tabLabel(EditorTab tab) {
        return switch (tab) {
            case GENERAL -> TAB_GENERAL;
            case TRAITS -> TAB_TRAITS;
            case MOVEMENT -> TAB_MOVEMENT;
            case NAVIGATION -> TAB_NAVIGATION;
            case COMBAT -> TAB_COMBAT;
            case FACTION -> TAB_FACTION;
            case SOUNDS -> TAB_SOUNDS;
            case INVENTORY -> TAB_INVENTORY;
            case SPAWN -> TAB_SPAWN;
            case JOBS -> TAB_JOBS;
        };
    }

    private void pageTabs(int direction) {
        firstVisibleTab += direction;
        layoutTabBar();
    }

    // How many tab buttons fit between the arrows; equals every tab when the whole row fits (arrows
    // are then hidden).
    private int visibleTabCount() {
        int totalWidth = rightPanelX() + RIGHT_PANEL_WIDTH - leftPanelX();
        int tabCount = EditorTab.values().length;
        int fullTabsWidth = tabCount * TAB_WIDTH + (tabCount - 1) * TAB_GAP;
        if (fullTabsWidth <= totalWidth) {
            return tabCount;
        }
        int areaWidth = totalWidth - 2 * (TAB_ARROW_WIDTH + TAB_GAP);
        return Math.clamp((areaWidth + TAB_GAP) / (TAB_WIDTH + TAB_GAP), 1, tabCount);
    }

    // Keeps the active tab inside the visible window without otherwise moving it, so switching to a
    // scrolled-off tab pages the strip to it while manual arrow paging is left alone.
    private void ensureActiveTabVisible() {
        int visible = visibleTabCount();
        int index = activeTab.ordinal();
        if (index < firstVisibleTab) {
            firstVisibleTab = index;
        } else if (index >= firstVisibleTab + visible) {
            firstVisibleTab = index - visible + 1;
        }
    }

    private void layoutTabBar() {
        int left = leftPanelX();
        int right = rightPanelX() + RIGHT_PANEL_WIDTH;
        int totalWidth = right - left;
        int tabCount = EditorTab.values().length;
        int visible = visibleTabCount();
        boolean allFit = visible >= tabCount;
        firstVisibleTab = Math.clamp(firstVisibleTab, 0, tabCount - visible);
        tabLeftArrow.visible = !allFit;
        tabRightArrow.visible = !allFit;
        if (allFit) {
            int fullTabsWidth = tabCount * TAB_WIDTH + (tabCount - 1) * TAB_GAP;
            int startX = left + (totalWidth - fullTabsWidth) / 2;
            for (int index = 0; index < tabCount; index++) {
                tabButtons[index].setX(startX + index * (TAB_WIDTH + TAB_GAP));
                tabButtons[index].visible = true;
            }
            return;
        }
        tabLeftArrow.setX(left);
        tabRightArrow.setX(right - TAB_ARROW_WIDTH);
        tabLeftArrow.active = firstVisibleTab > 0;
        tabRightArrow.active = firstVisibleTab + visible < tabCount;
        int areaLeft = left + TAB_ARROW_WIDTH + TAB_GAP;
        for (int index = 0; index < tabCount; index++) {
            int slot = index - firstVisibleTab;
            boolean show = slot >= 0 && slot < visible;
            tabButtons[index].visible = show;
            if (show) {
                tabButtons[index].setX(areaLeft + slot * (TAB_WIDTH + TAB_GAP));
            }
        }
    }

    private void buildGeneralTab(int leftX, int rightX) {
        int c1 = rightX + COL1_CONTROL_X;
        int c2 = rightX + COL2_CONTROL_X;

        nameField = addRenderableWidget(new EditBox(this.font, c1, colRowY(0), COL_CONTROL_WIDTH, 20, LABEL_NAME));
        nameField.setMaxLength(NpcValidation.MAX_NAME_LENGTH);
        nameField.setValue(npcName);
        nameField.setResponder(this::onNameChanged);

        healthField = addRenderableWidget(new EditBox(this.font, c2, colRowY(0), COL_CONTROL_WIDTH, 20, LABEL_HEALTH));
        healthField.setMaxLength(6);
        healthField.setValue(healthText);
        healthField.setResponder(this::onHealthChanged);

        showTagButton = addRenderableWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, nameTagShown)
                .displayOnlyValue()
                .create(c1, colRowY(1), COL_CONTROL_WIDTH, 20, LABEL_SHOW_TAG, (button, value) -> nameTagShown = value));

        tagColorButton = addRenderableWidget(Button.builder(tagColorLabel(), button -> {
            tagColorId = colorName(TAG_COLORS.get((tagColorIndex() + 1) % TAG_COLORS.size()));
            tagColorButton.setMessage(tagColorLabel());
        }).bounds(c2, colRowY(1), COL_CONTROL_WIDTH, 20).build());

        categoryField = addRenderableWidget(new EditBox(this.font, c1, colRowY(2), COL_CONTROL_WIDTH, 20, LABEL_CATEGORY));
        categoryField.setMaxLength(NpcValidation.MAX_CATEGORY_LENGTH);
        categoryField.setHint(CATEGORY_HINT);
        categoryField.setValue(categoryText);
        categoryField.setResponder(value -> {
            String sanitized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!sanitized.equals(value)) {
                categoryField.setValue(sanitized);
                return;
            }
            categoryText = sanitized;
            updateCreateButtonState();
        });

        bossBarButton = addRenderableWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, bossBarEnabled)
                .displayOnlyValue()
                .create(c2, colRowY(2), COL_CONTROL_WIDTH, 20, LABEL_BOSS_BAR, (button, value) -> {
                    bossBarEnabled = value;
                    updateBossBarControls();
                }));

        // The color swatch strip sits in column one and expands rightward across the panel, so its
        // row keeps column two clear; the name field takes the next row.
        for (int index = 0; index < BOSS_BAR_SWATCHES.length; index++) {
            String colorId = NpcValidation.BOSS_BAR_COLORS.get(index);
            ColorSwatchButton swatch = new ColorSwatchButton(c1 + index * 20, colRowY(3) + 2,
                    BOSS_BAR_SWATCHES[index], LABEL_BOSS_BAR_COLOR, () -> onBossBarSwatchClicked(colorId));
            bossBarSwatches[index] = swatch;
            addRenderableWidget(swatch);
        }

        bossBarNameField = addRenderableWidget(new EditBox(this.font, c1, colRowY(4), COL_CONTROL_WIDTH, 20,
                LABEL_BOSS_BAR_NAME));
        bossBarNameField.setMaxLength(NpcValidation.MAX_BOSS_BAR_NAME_LENGTH);
        bossBarNameField.setHint(BOSS_BAR_NAME_HINT);
        bossBarNameField.setValue(bossBarName);
        bossBarNameField.setResponder(value -> bossBarName = value);

        // The editor only opens on NPCs it may edit, so the API-only choice is never offered here.
        editAccessButton = addRenderableWidget(CycleButton.<net.lunacore.luna_npc.api.NpcEditAccess>builder(access ->
                                Component.translatable("gui.luna_npc.create_npc.edit_access."
                                        + access.getSerializedName()), editAccess)
                .withValues(net.lunacore.luna_npc.api.NpcEditAccess.GUI_ONLY,
                        net.lunacore.luna_npc.api.NpcEditAccess.SHARED)
                .displayOnlyValue()
                .withTooltip(access -> Tooltip.create(Component.translatable(
                        "gui.luna_npc.create_npc.edit_access." + access.getSerializedName() + ".tooltip")))
                .create(c2, colRowY(4), COL_CONTROL_WIDTH, 20, LABEL_EDIT_ACCESS,
                        (button, value) -> editAccess = value));

        updateBossBarControls();

        // Persistence for hand-placed / API-spawned NPCs (on by default). Stored in the spawn settings,
        // so toggling it rebuilds that record. Row five is free below the boss-bar band.
        placedPersistentBox = addRenderableWidget(Checkbox.builder(LABEL_PERSISTENT, this.font)
                .pos(rightX + COL1_LABEL_X, colRowY(5) + 1).selected(placedPersistent)
                .onValueChange((box, value) -> {
                    placedPersistent = value;
                    rebuildSpawnSettings();
                })
                .tooltip(Tooltip.create(PERSISTENT_TOOLTIP)).build());

        changeModelButton = addRenderableWidget(Button.builder(LABEL_CHANGE_MODEL, button ->
                        Minecraft.getInstance().setScreenAndShow(new ModelPickerScreen(this, modelSettings,
                                chosen -> modelSettings = chosen)))
                .bounds(leftX + 8, CONTENT_TOP + CONTENT_HEIGHT - 28, LEFT_PANEL_WIDTH - 16, 20).build());
    }

    private void buildMovementTab(int rightX) {
        int c1 = rightX + COL1_CONTROL_X;
        int c2 = rightX + COL2_CONTROL_X;
        movementTabWidgets.clear();

        movementTypeButton = addRenderableWidget(Button.builder(movementTypeLabel(), button -> {
            int next = (NpcValidation.MOVEMENT_TYPES.indexOf(movementType) + 1)
                    % NpcValidation.MOVEMENT_TYPES.size();
            movementType = NpcValidation.MOVEMENT_TYPES.get(next);
            movementTypeButton.setMessage(movementTypeLabel());
            // Each movement type brings its recommended wander default (ground strolls, water
            // and air are restless); the behavior stays freely changeable on the Navigation tab.
            wanderBehavior = NpcWanderSettings.recommendedBehavior(movementType);
            updateWanderControls();
            refreshInventoryWidgets();
            updateMovementTypeControls();
        }).bounds(c1, colRowY(0), COL_CONTROL_WIDTH, 20).build());
        movementTabWidgets.add(movementTypeButton);

        walkSpeedSlider = new LabeledSlider(c1, colRowY(1), COL_CONTROL_WIDTH, 20,
                Component.empty(), SPEED_CURVE, walkSpeed, CreateNpcScreen::formatSpeed, value -> {
            walkSpeed = value;
            if (!runSpeedEnabled) {
                runSpeedSlider.setValue(value);
            }
        });
        movementTabWidgets.add(addRenderableWidget(walkSpeedSlider));

        // Run speed sits in column two next to a link toggle that keeps it tied to the walk speed.
        runLinkButton = Button.builder(runLinkLabel(), button -> {
            runSpeedEnabled = !runSpeedEnabled;
            if (!runSpeedEnabled) {
                runSpeedSlider.setValue(walkSpeed);
            }
            updateRunSpeedControls();
        }).bounds(c2, colRowY(1), 20, 20).build();
        movementTabWidgets.add(addRenderableWidget(runLinkButton));
        runSpeedSlider = new LabeledSlider(c2 + 24, colRowY(1), COL_CONTROL_WIDTH - 24, 20,
                Component.empty(), SPEED_CURVE, runSpeedEnabled ? runSpeed : walkSpeed,
                CreateNpcScreen::formatSpeed, value -> runSpeed = value);
        movementTabWidgets.add(addRenderableWidget(runSpeedSlider));

        knockbackSlider = new LabeledSlider(c1, colRowY(2), COL_CONTROL_WIDTH, 20,
                Component.empty(), NpcValidation.MIN_KNOCKBACK, NpcValidation.MAX_KNOCKBACK, knockback,
                value -> String.format(Locale.ROOT, "%.1f", value),
                value -> knockback = Math.round(value * 10.0) / 10.0F);
        movementTabWidgets.add(addRenderableWidget(knockbackSlider));

        // Jumping is ground-only, so it shares knockback's row in column two.
        jumpHeightSlider = new LabeledSlider(c2, colRowY(2), COL_CONTROL_WIDTH, 20,
                Component.empty(), JUMP_CURVE, jumpHeight,
                value -> String.format(Locale.ROOT, "%.2f blocks", value), value -> jumpHeight = value);
        movementTabWidgets.add(addRenderableWidget(jumpHeightSlider));

        int gridLeft = rightX + 50;
        for (int index = 0; index < avoidedBlockSlots.length; index++) {
            int slotIndex = index;
            int x = gridLeft + (index % 9) * ItemSlotButton.SIZE;
            int y = colRowY(4) + 16 + (index / 9) * ItemSlotButton.SIZE;
            ItemSlotButton slot = new ItemSlotButton(x, y, LABEL_AVOIDED_BLOCKS, event -> {
                if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    if (slotIndex < avoidedBlocks.size()) {
                        avoidedBlocks.remove(slotIndex);
                        refreshAvoidedBlockSlots();
                    }
                } else {
                    Minecraft.getInstance().setScreenAndShow(new ItemPickerScreen(this, null, true, blockId -> {
                        // The picker's Clear Slot button reports an empty id.
                        if (blockId.isEmpty()) {
                            if (slotIndex < avoidedBlocks.size()) {
                                avoidedBlocks.remove(slotIndex);
                            }
                        } else if (slotIndex < avoidedBlocks.size()) {
                            avoidedBlocks.set(slotIndex, blockId);
                        } else if (avoidedBlocks.size() < NpcValidation.MAX_AVOIDED_BLOCKS) {
                            avoidedBlocks.add(blockId);
                        }
                    }));
                }
            });
            avoidedBlockSlots[index] = slot;
            movementTabWidgets.add(addRenderableWidget(slot));
        }
        clearAvoidedBlocksButton = Button.builder(LABEL_CLEAR_ALL, button -> {
            avoidedBlocks.clear();
            refreshAvoidedBlockSlots();
        }).bounds(rightX + RIGHT_PANEL_WIDTH - 70, colRowY(4) - 2, 60, 16).build();
        movementTabWidgets.add(addRenderableWidget(clearAvoidedBlocksButton));

        // Water and air share row four with ground's avoided-block grid; only the picked movement
        // type's controls are ever visible, so the overlapping positions never collide on screen.
        waterDepthSlider = new LabeledSlider(c1, colRowY(4), COL_CONTROL_WIDTH, 20,
                Component.empty(), 0.0F, NpcValidation.MAX_WATER_DEPTH, waterDepth,
                value -> String.valueOf(Math.round(value)), value -> waterDepth = Math.round(value));
        movementTabWidgets.add(addRenderableWidget(waterDepthSlider));
        // On ignores the preferred depth and follows the target through the whole water column; off
        // fights only within the preferred depth (± tolerance), so the tolerance enables with off.
        waterDepthLeaveBox = Checkbox.builder(LABEL_LEAVE_DEPTH_COMBAT, this.font)
                .pos(rightX + COL1_LABEL_X, colRowY(5) + 1).selected(!waterDepthFixed)
                .onValueChange((box, value) -> {
                    waterDepthFixed = !value;
                    waterDepthRangeSlider.active = !value;
                }).tooltip(Tooltip.create(LEAVE_DEPTH_COMBAT_TOOLTIP)).build();
        movementTabWidgets.add(addRenderableWidget(waterDepthLeaveBox));
        waterDepthRangeSlider = new LabeledSlider(c2, colRowY(4), COL_CONTROL_WIDTH, 20,
                Component.empty(), NpcValidation.MIN_HEIGHT_RANGE, NpcValidation.MAX_HEIGHT_RANGE,
                waterDepthRange, value -> "±" + Math.round(value),
                value -> waterDepthRange = Math.round(value));
        waterDepthRangeSlider.active = waterDepthFixed;
        waterDepthRangeSlider.setTooltip(Tooltip.create(WATER_DEPTH_TOLERANCE_TOOLTIP));
        movementTabWidgets.add(addRenderableWidget(waterDepthRangeSlider));

        hoverButton = CycleButton.booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, hoverEnabled)
                .displayOnlyValue()
                .create(c1, colRowY(4), COL_CONTROL_WIDTH, 20, LABEL_HOVER, (button, value) -> {
                    hoverEnabled = value;
                    updateMovementTypeControls();
                });
        movementTabWidgets.add(addRenderableWidget(hoverButton));
        hoverIntervalSlider = new LabeledSlider(c1, colRowY(5), COL_CONTROL_WIDTH, 20,
                Component.empty(), NpcValidation.MIN_HOVER_INTERVAL_MS, NpcValidation.MAX_HOVER_INTERVAL_MS,
                hoverIntervalMs, value -> Math.round(value) + " ms", value -> hoverIntervalMs = Math.round(value));
        movementTabWidgets.add(addRenderableWidget(hoverIntervalSlider));
        hoverAmountSlider = new LabeledSlider(c1, colRowY(6), COL_CONTROL_WIDTH, 20,
                Component.empty(), 0.0F, NpcValidation.MAX_HOVER_AMOUNT, hoverAmount,
                value -> String.format(Locale.ROOT, "%.2f", value), value -> hoverAmount = value);
        movementTabWidgets.add(addRenderableWidget(hoverAmountSlider));
        flightHeightSlider = new LabeledSlider(c2, colRowY(4), COL_CONTROL_WIDTH - 18, 20,
                Component.empty(), 0.0F, NpcValidation.MAX_FLIGHT_HEIGHT, flightHeight,
                value -> String.valueOf(Math.round(value)), value -> flightHeight = Math.round(value));
        movementTabWidgets.add(addRenderableWidget(flightHeightSlider));
        flightHeightFixedBox = Checkbox.builder(Component.empty(), this.font)
                .pos(c2 + COL_CONTROL_WIDTH - 14, colRowY(4) + 1).selected(flightHeightFixed)
                .onValueChange((box, value) -> {
                    flightHeightFixed = value;
                    flightHeightRangeSlider.active = !value;
                }).tooltip(Tooltip.create(HEIGHT_FIXED_TOOLTIP)).build();
        movementTabWidgets.add(addRenderableWidget(flightHeightFixedBox));
        flightHeightRangeSlider = new LabeledSlider(c2, colRowY(5), COL_CONTROL_WIDTH, 20,
                Component.empty(), NpcValidation.MIN_HEIGHT_RANGE, NpcValidation.MAX_HEIGHT_RANGE,
                flightHeightRange, value -> "±" + Math.round(value),
                value -> flightHeightRange = Math.round(value));
        flightHeightRangeSlider.active = !flightHeightFixed;
        flightHeightRangeSlider.setTooltip(Tooltip.create(TOLERANCE_TOOLTIP));
        movementTabWidgets.add(addRenderableWidget(flightHeightRangeSlider));

        updateRunSpeedControls();
    }

    private void buildTraitsTab(int rightX) {
        int c1 = rightX + COL1_CONTROL_X;
        int c2 = rightX + COL2_CONTROL_X;
        int l1 = rightX + COL1_LABEL_X;
        int l2 = rightX + COL2_LABEL_X;
        traitsTabWidgets.clear();

        npcTypeButton = Button.builder(npcTypeLabel(), button -> {
            int next = (NpcValidation.NPC_TYPES.indexOf(npcType) + 1) % NpcValidation.NPC_TYPES.size();
            npcType = NpcValidation.NPC_TYPES.get(next);
            npcTypeButton.setMessage(npcTypeLabel());
            npcTypeButton.setTooltip(npcTypeTooltip());
        }).bounds(c1, colRowY(0), COL_CONTROL_WIDTH, 20).build();
        npcTypeButton.setTooltip(npcTypeTooltip());
        traitsTabWidgets.add(addRenderableWidget(npcTypeButton));

        traitsTabWidgets.add(addRenderableWidget(Checkbox.builder(LABEL_CAN_DROWN, this.font)
                .pos(l1, colRowY(1) + 1).selected(canDrown)
                .onValueChange((box, value) -> canDrown = value).build()));
        canSwimBox = Checkbox.builder(LABEL_CAN_SWIM, this.font)
                .pos(l2, colRowY(1) + 1).selected(canSwim)
                .onValueChange((box, value) -> canSwim = value)
                .tooltip(Tooltip.create(CAN_SWIM_TOOLTIP)).build();
        traitsTabWidgets.add(addRenderableWidget(canSwimBox));
        traitsTabWidgets.add(addRenderableWidget(Checkbox.builder(LABEL_BURN_DAYLIGHT, this.font)
                .pos(l1, colRowY(2) + 1).selected(burnInDaylight)
                .onValueChange((box, value) -> {
                    burnInDaylight = value;
                    updateEmergencySeekVisibility();
                }).build()));

        traitsTabWidgets.add(addRenderableWidget(Checkbox.builder(LABEL_AVOID_LIGHT, this.font)
                .pos(l1, colRowY(3) + 1).selected(avoidLight)
                .onValueChange((box, value) -> {
                    avoidLight = value;
                    avoidLightLevelSlider.active = value;
                    updateEmergencySeekVisibility();
                }).tooltip(Tooltip.create(AVOID_LIGHT_TOOLTIP)).build()));
        avoidLightLevelSlider = new LabeledSlider(c2, colRowY(3), COL_CONTROL_WIDTH, 20,
                LABEL_LIGHT_LEVEL, NpcValidation.MIN_LIGHT_LEVEL, NpcValidation.MAX_LIGHT_LEVEL,
                avoidLightLevel, value -> String.valueOf(Math.round(value)),
                value -> avoidLightLevel = Math.round(value));
        avoidLightLevelSlider.active = avoidLight;
        traitsTabWidgets.add(addRenderableWidget(avoidLightLevelSlider));

        traitsTabWidgets.add(addRenderableWidget(Checkbox.builder(LABEL_AVOID_NIGHT, this.font)
                .pos(l1, colRowY(4) + 1).selected(avoidNight)
                .onValueChange((box, value) -> {
                    avoidNight = value;
                    avoidNightLevelSlider.active = value;
                    updateEmergencySeekVisibility();
                }).tooltip(Tooltip.create(AVOID_NIGHT_TOOLTIP)).build()));
        avoidNightLevelSlider = new LabeledSlider(c2, colRowY(4), COL_CONTROL_WIDTH, 20,
                LABEL_LIGHT_LEVEL, NpcValidation.MIN_LIGHT_LEVEL, NpcValidation.MAX_LIGHT_LEVEL,
                avoidNightLevel, value -> String.valueOf(Math.round(value)),
                value -> avoidNightLevel = Math.round(value));
        avoidNightLevelSlider.active = avoidNight;
        traitsTabWidgets.add(addRenderableWidget(avoidNightLevelSlider));

        // Only meaningful once a light/burn condition is on; shown just for those. Spans both columns.
        emergencySeekBox = Checkbox.builder(LABEL_EMERGENCY_SEEK, this.font)
                .pos(l1, colRowY(5) + 1).selected(emergencySeek)
                .maxWidth(RIGHT_PANEL_WIDTH - 20)
                .onValueChange((box, value) -> emergencySeek = value)
                .tooltip(Tooltip.create(EMERGENCY_SEEK_TOOLTIP)).build();
        traitsTabWidgets.add(addRenderableWidget(emergencySeekBox));

        reactionButton = Button.builder(reactionLabel(), button -> {
            int next = (NpcReactionSettings.MODES.indexOf(reactionMode) + 1) % NpcReactionSettings.MODES.size();
            reactionMode = NpcReactionSettings.MODES.get(next);
            reactionButton.setMessage(reactionLabel());
            updateReactionControls();
        }).bounds(c1, colRowY(6), COL_CONTROL_WIDTH, 20).build();
        reactionButton.setTooltip(Tooltip.create(REACTION_TOOLTIP));
        traitsTabWidgets.add(addRenderableWidget(reactionButton));

        traitsTabWidgets.add(addRenderableWidget(Button.builder(
                Component.translatable("gui.luna_npc.create_npc.regeneration"), button -> openRegenScreen())
                .bounds(c2, colRowY(6), COL_CONTROL_WIDTH, 20).build()));

        fleeTimeField = new EditBox(this.font, c1, colRowY(7), COL_CONTROL_WIDTH, 20, LABEL_FLEE_TIME);
        fleeTimeField.setMaxLength(4);
        fleeTimeField.setValue(fleeTimeText);
        fleeTimeField.setResponder(this::onFleeTimeTyped);
        traitsTabWidgets.add(addRenderableWidget(fleeTimeField));

        fleeDistanceSlider = new LabeledSlider(c2, colRowY(7), COL_CONTROL_WIDTH, 20, Component.empty(),
                NpcValidation.MIN_FLEE_DISTANCE, NpcValidation.MAX_FLEE_DISTANCE, fleeDistance,
                value -> Math.round(value) + " blocks", value -> fleeDistance = Math.round(value));
        traitsTabWidgets.add(addRenderableWidget(fleeDistanceSlider));

        traitsTabWidgets.add(addRenderableWidget(Checkbox.builder(LABEL_CAN_OPEN_DOORS, this.font)
                .pos(l1, colRowY(8) + 1).selected(canOpenDoors)
                .onValueChange((box, value) -> canOpenDoors = value)
                .tooltip(Tooltip.create(CAN_OPEN_DOORS_TOOLTIP)).build()));
        traitsTabWidgets.add(addRenderableWidget(Checkbox.builder(LABEL_CAN_BREAK_DOORS, this.font)
                .pos(l2, colRowY(8) + 1).selected(canBreakDoors)
                .onValueChange((box, value) -> canBreakDoors = value)
                .tooltip(Tooltip.create(CAN_BREAK_DOORS_TOOLTIP)).build()));
        traitsTabWidgets.add(addRenderableWidget(Checkbox.builder(LABEL_TAKE_FALL_DAMAGE, this.font)
                .pos(l1, colRowY(9) + 1).selected(takeFallDamage)
                .maxWidth(COL2_LABEL_X - COL1_LABEL_X - 4)
                .onValueChange((box, value) -> takeFallDamage = value)
                .tooltip(Tooltip.create(TAKE_FALL_DAMAGE_TOOLTIP)).build()));
        traitsTabWidgets.add(addRenderableWidget(Checkbox.builder(LABEL_CHUNK_LOADER, this.font)
                .pos(l2, colRowY(9) + 1).selected(chunkLoader)
                .onValueChange((box, value) -> chunkLoader = value)
                .tooltip(Tooltip.create(CHUNK_LOADER_TOOLTIP)).build()));
        traitsTabWidgets.add(addRenderableWidget(Checkbox.builder(LABEL_PROJECTILE_IMMUNE, this.font)
                .pos(l1, colRowY(10) + 1).selected(projectileImmune)
                .onValueChange((box, value) -> projectileImmune = value)
                .tooltip(Tooltip.create(PROJECTILE_IMMUNE_TOOLTIP)).build()));
        traitsTabWidgets.add(addRenderableWidget(Checkbox.builder(LABEL_FIRE_IMMUNE, this.font)
                .pos(l2, colRowY(10) + 1).selected(fireImmune)
                .onValueChange((box, value) -> fireImmune = value)
                .tooltip(Tooltip.create(FIRE_IMMUNE_TOOLTIP)).build()));
    }

    private Component reactionLabel() {
        return Component.translatable("gui.luna_npc.create_npc.reaction." + reactionMode);
    }

    private void openRegenScreen() {
        int maxHp = Math.max(1, parsedHealth() != -1 ? parsedHealth() : NpcDefaults.HEALTH);
        Minecraft.getInstance().setScreenAndShow(new EditRegenScreen(this, regenSettings, maxHp,
                updated -> regenSettings = updated));
    }

    private void onFleeTimeTyped(String value) {
        String sanitized = value.replaceAll("[^0-9]", "");
        if (!sanitized.equals(value)) {
            fleeTimeField.setValue(sanitized);
            return;
        }
        if (!sanitized.isEmpty() && Integer.parseInt(sanitized) > NpcValidation.MAX_FLEE_SECONDS) {
            fleeTimeField.setValue(String.valueOf(NpcValidation.MAX_FLEE_SECONDS));
            return;
        }
        fleeTimeText = sanitized;
    }

    private int parsedFleeSeconds() {
        if (fleeTimeText.isEmpty()) {
            return NpcDefaults.FLEE_SECONDS;
        }
        return Math.clamp(Integer.parseInt(fleeTimeText), NpcValidation.MIN_FLEE_SECONDS,
                NpcValidation.MAX_FLEE_SECONDS);
    }

    private void updateEmergencySeekVisibility() {
        if (emergencySeekBox != null) {
            emergencySeekBox.visible = activeTab == EditorTab.TRAITS
                    && (burnInDaylight || avoidLight || avoidNight);
        }
        updateReactionControls();
    }

    // The flee timing controls only apply in the Flee reaction.
    private void updateReactionControls() {
        if (fleeTimeField == null) {
            return;
        }
        boolean flee = activeTab == EditorTab.TRAITS && NpcReactionSettings.FEARFUL.equals(reactionMode);
        fleeTimeField.visible = flee;
        fleeDistanceSlider.visible = flee;
    }

    private Component npcTypeLabel() {
        return Component.translatable("gui.luna_npc.create_npc.npc_type." + npcType);
    }

    private Tooltip npcTypeTooltip() {
        return Tooltip.create(Component.translatable("gui.luna_npc.create_npc.npc_type." + npcType + ".tooltip"));
    }

    // Scrollable Combat tab: the sections lay out top-to-bottom and any row a toggle turns off
    // collapses entirely (destroy-blocks off hides the crater size, no particle hides the
    // duration, homing on hides its warning), so the panel never shows a dead gap. layoutCombat()
    // recomputes every y into combatY, and both the widgets and the text labels read from it.
    private static final int COMBAT_ROW = 24;
    private static final int COMBAT_TITLE = 16;
    private static final int COMBAT_GAP = 6;
    private static final int ACCURACY_GRAPH_HEIGHT = 82;
    private static final int COMBAT_HIDDEN_Y = -10_000;
    // The pinned Normal/Advanced switch reserves a band at the top of the Combat panel; the scrolling
    // content and its scrollbar start below it.
    private static final int COMBAT_MODE_H = 18;
    private static final int COMBAT_MODE_BAND = COMBAT_MODE_H + 6;

    private final Map<String, Integer> combatY = new java.util.LinkedHashMap<>();
    private int combatContentBottom;

    private int combatClipTop() {
        return CONTENT_TOP + 4 + COMBAT_MODE_BAND;
    }

    private int maxCombatScroll() {
        return Math.max(0, combatContentBottom - (CONTENT_TOP + CONTENT_HEIGHT - 8));
    }

    private void addCombatWidget(AbstractWidget widget) {
        combatTabWidgets.add(addRenderableWidget(widget));
    }

    private record ImpactWidgets(Checkbox destroy, LabeledSlider radius, Button particle,
                                 LabeledSlider duration) {
    }

    private void buildCombatTab(int rightX) {
        int c1 = rightX + COL1_CONTROL_X;
        int c2 = rightX + COL2_CONTROL_X;
        combatTabWidgets.clear();
        combatWidgetBaseY.clear();

        int modeWidth = (RIGHT_PANEL_WIDTH - 24) / 2;
        combatNormalButton = Button.builder(Component.translatable("gui.luna_npc.create_npc.combat_normal"),
                        button -> setCombatAdvancedView(false))
                .bounds(rightX + 10, CONTENT_TOP + 4, modeWidth, COMBAT_MODE_H).build();
        combatAdvancedButton = Button.builder(Component.translatable("gui.luna_npc.create_npc.combat_advanced"),
                        button -> setCombatAdvancedView(true))
                .bounds(rightX + 14 + modeWidth, CONTENT_TOP + 4, modeWidth, COMBAT_MODE_H).build();
        combatNormalButton.visible = activeTab == EditorTab.COMBAT;
        combatAdvancedButton.visible = activeTab == EditorTab.COMBAT;
        addRenderableWidget(combatNormalButton);
        addRenderableWidget(combatAdvancedButton);

        combatRulesButton = Button.builder(combatRulesLabel(), button -> openCombatEditor())
                .bounds(rightX + 10, CONTENT_TOP + 4 + COMBAT_MODE_BAND + 24, RIGHT_PANEL_WIDTH - 20, 20).build();
        combatRulesButton.visible = activeTab == EditorTab.COMBAT && combatAdvancedView;
        addRenderableWidget(combatRulesButton);
        refreshCombatModeButtons();

        rangeSwitchButton = Button.builder(rangeSwitchLabel(), button -> openRangeSwitchEditor())
                .bounds(rightX + COL1_LABEL_X, 0, RIGHT_PANEL_WIDTH - 20, 20).build();
        addCombatWidget(rangeSwitchButton);

        aggroSlider = new LabeledSlider(c1, 0, COL_CONTROL_WIDTH, 20, Component.empty(),
                0.0F, NpcValidation.MAX_AGGRO_RANGE, aggroRange,
                value -> String.format(Locale.ROOT, "%.0f blocks", value), value -> aggroRange = value);
        addCombatWidget(aggroSlider);

        requireLosButton = CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, requireLos)
                .displayOnlyValue()
                .withTooltip(value -> Tooltip.create(REQUIRE_LOS_TOOLTIP))
                .create(c2, 0, COL_CONTROL_WIDTH, 20, LABEL_REQUIRE_LOS, (button, value) -> requireLos = value);
        addCombatWidget(requireLosButton);

        // A damage rule, not a targeting rule: it gets its own row above the attack sections.
        difficultyScalingButton = CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, difficultyScaling)
                .displayOnlyValue()
                .withTooltip(value -> Tooltip.create(DIFFICULTY_SCALING_TOOLTIP))
                .create(c1, 0, COL_CONTROL_WIDTH, 20, LABEL_DIFFICULTY_SCALING,
                        (button, value) -> difficultyScaling = value);
        addCombatWidget(difficultyScalingButton);

        giveUpField = new EditBox(this.font, c1, 0, COL_CONTROL_WIDTH, 20, LABEL_GIVE_UP);
        giveUpField.setMaxLength(4);
        giveUpField.setHint(GIVE_UP_HINT);
        giveUpField.setValue(giveUpText);
        giveUpField.setTooltip(Tooltip.create(GIVE_UP_TOOLTIP));
        giveUpField.setResponder(this::onGiveUpTyped);
        addCombatWidget(giveUpField);

        meleeEnabledButton = CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, meleeEnabled)
                .displayOnlyValue()
                .create(c1, 0, COL_CONTROL_WIDTH, 20, LABEL_ATTACK_ENABLED, (button, value) -> meleeEnabled = value);
        addCombatWidget(meleeEnabledButton);

        meleeDamageField = new EditBox(this.font, c2, 0, COL_CONTROL_WIDTH, 20, LABEL_ATTACK_DAMAGE);
        meleeDamageField.setMaxLength(7);
        meleeDamageField.setValue(meleeDamageText);
        meleeDamageField.setResponder(value -> onDamageTyped(value, meleeDamageField, true));
        addCombatWidget(meleeDamageField);

        meleeRangeSlider = new LabeledSlider(c1, 0, COL_CONTROL_WIDTH, 20, Component.empty(),
                0.0F, NpcValidation.MAX_ATTACK_RANGE, meleeRange,
                value -> String.format(Locale.ROOT, "%.1f blocks", value), value -> meleeRange = value);
        addCombatWidget(meleeRangeSlider);

        meleeSpeedSlider = new LabeledSlider(c2, 0, COL_CONTROL_WIDTH, 20, Component.empty(),
                ATTACK_SPEED_CURVE, meleeIntervalSeconds,
                value -> String.format(Locale.ROOT, "%.1fs", value), value -> meleeIntervalSeconds = value);
        addCombatWidget(meleeSpeedSlider);

        meleeEffectsButton = Button.builder(effectsButtonLabel(meleeEffects), button ->
                Minecraft.getInstance().setScreenAndShow(new TargetEffectScreen(this,
                        SECTION_CLOSE_RANGE, meleeEffects, NpcValidation.MELEE_ENCHANTS)))
                .bounds(c1, 0, COL_CONTROL_WIDTH, 20).build();
        meleeEffectsButton.setTooltip(effectsTooltip(meleeEffects));
        addCombatWidget(meleeEffectsButton);

        ImpactWidgets meleeImpact = addImpactRows(rightX,
                meleeImpactDestroy, value -> meleeImpactDestroy = value,
                meleeImpactRadius, value -> meleeImpactRadius = value,
                () -> meleeImpactParticleId, value -> meleeImpactParticleId = value,
                meleeImpactParticleSeconds, value -> meleeImpactParticleSeconds = value);
        meleeDestroyBox = meleeImpact.destroy();
        meleeRadiusSlider = meleeImpact.radius();
        meleeImpactParticleButton = meleeImpact.particle();
        meleeDurationSlider = meleeImpact.duration();

        rangedEnabledButton = CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, rangedEnabled)
                .displayOnlyValue()
                .create(c1, 0, COL_CONTROL_WIDTH, 20, LABEL_ATTACK_ENABLED, (button, value) -> rangedEnabled = value);
        addCombatWidget(rangedEnabledButton);

        rangedDamageField = new EditBox(this.font, c2, 0, COL_CONTROL_WIDTH, 20, LABEL_ATTACK_DAMAGE);
        rangedDamageField.setMaxLength(7);
        rangedDamageField.setValue(rangedDamageText);
        rangedDamageField.setResponder(value -> onDamageTyped(value, rangedDamageField, false));
        addCombatWidget(rangedDamageField);

        rangedDistanceSlider = new LabeledSlider(c1, 0, COL_CONTROL_WIDTH, 20, Component.empty(),
                0.0F, NpcValidation.MAX_DISTANCE_PREFERENCE, distancePreference,
                value -> String.format(Locale.ROOT, "%.0f blocks", value), value -> distancePreference = value);
        addCombatWidget(rangedDistanceSlider);

        rangedSpeedSlider = new LabeledSlider(c2, 0, COL_CONTROL_WIDTH, 20, Component.empty(),
                ATTACK_SPEED_CURVE, rangedIntervalSeconds,
                value -> String.format(Locale.ROOT, "%.1fs", value), value -> rangedIntervalSeconds = value);
        addCombatWidget(rangedSpeedSlider);

        rangedEffectsButton = Button.builder(effectsButtonLabel(rangedEffects), button ->
                Minecraft.getInstance().setScreenAndShow(new TargetEffectScreen(this,
                        SECTION_LONG_RANGE, rangedEffects, NpcValidation.RANGED_ENCHANTS)))
                .bounds(c1, 0, COL_CONTROL_WIDTH, 20).build();
        rangedEffectsButton.setTooltip(effectsTooltip(rangedEffects));
        addCombatWidget(rangedEffectsButton);

        ImpactWidgets rangedImpact = addImpactRows(rightX,
                rangedImpactDestroy, value -> rangedImpactDestroy = value,
                rangedImpactRadius, value -> rangedImpactRadius = value,
                () -> rangedImpactParticleId, value -> rangedImpactParticleId = value,
                rangedImpactParticleSeconds, value -> rangedImpactParticleSeconds = value);
        rangedDestroyBox = rangedImpact.destroy();
        rangedRadiusSlider = rangedImpact.radius();
        rangedImpactParticleButton = rangedImpact.particle();
        rangedDurationSlider = rangedImpact.duration();

        projectileSlot = new ItemSlotButton(c1, 0, LABEL_PROJECTILE, event -> {
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                projectileItemId = NpcDefaults.PROJECTILE_ITEM;
                projectileSlot.setStack(NpcEquipment.resolve(projectileItemId));
            } else {
                Minecraft.getInstance().setScreenAndShow(new ItemPickerScreen(this, null, itemId -> {
                    if (!itemId.isEmpty()) {
                        projectileItemId = itemId;
                        projectileSlot.setStack(NpcEquipment.resolve(projectileItemId));
                    }
                }));
            }
        });
        projectileSlot.setStack(NpcEquipment.resolve(projectileItemId));
        projectileSlot.setTooltip(Tooltip.create(LABEL_PROJECTILE));
        addCombatWidget(projectileSlot);

        projectileSpeedSlider = new LabeledSlider(c2, 0, COL_CONTROL_WIDTH, 20, Component.empty(),
                0.0F, NpcValidation.MAX_PROJECTILE_SPEED, projectileSpeed,
                value -> String.format(Locale.ROOT, "%.0f B/S", value), value -> projectileSpeed = value);
        addCombatWidget(projectileSpeedSlider);

        homingBox = Checkbox.builder(LABEL_HOMING, this.font)
                .pos(rightX + COL1_LABEL_X, 0).selected(homingEnabled)
                .onValueChange((box, value) -> {
                    homingEnabled = value;
                    layoutCombat();
                    updateCombatScroll();
                }).build();
        addCombatWidget(homingBox);
        curveBox = Checkbox.builder(LABEL_CURVE, this.font)
                .pos(rightX + COL2_LABEL_X, 0).selected(curveEnabled)
                .onValueChange((box, value) -> {
                    curveEnabled = value;
                    curveMaxDistanceSlider.active = value;
                    layoutCombat();
                    updateCombatScroll();
                }).tooltip(Tooltip.create(CURVE_TOOLTIP)).build();
        addCombatWidget(curveBox);

        curveMaxDistanceSlider = new LabeledSlider(c1, 0, COL_CONTROL_WIDTH, 20, Component.empty(),
                NpcValidation.MIN_CURVE_DISTANCE, NpcValidation.MAX_CURVE_DISTANCE, curveMaxDistance,
                value -> String.format(Locale.ROOT, "%.0f blocks", value), value -> curveMaxDistance = value);
        curveMaxDistanceSlider.active = curveEnabled;
        addCombatWidget(curveMaxDistanceSlider);

        dynamicAccuracyBox = Checkbox.builder(LABEL_DYNAMIC_ACCURACY, this.font)
                .pos(rightX + COL1_LABEL_X, 0).selected(dynamicAccuracy)
                .onValueChange((box, value) -> {
                    dynamicAccuracy = value;
                    updateAccuracyControls();
                    layoutCombat();
                    updateCombatScroll();
                }).tooltip(Tooltip.create(DYNAMIC_ACCURACY_TOOLTIP)).build();
        addCombatWidget(dynamicAccuracyBox);

        fixedAccuracySlider = new LabeledSlider(c1, 0, COL_CONTROL_WIDTH, 20, Component.empty(),
                0.0F, 100.0F, fixedAccuracy, value -> Math.round(value) + "%",
                value -> fixedAccuracy = Math.round(value));
        fixedAccuracySlider.setTooltip(Tooltip.create(FIXED_ACCURACY_TOOLTIP));
        addCombatWidget(fixedAccuracySlider);

        accuracyCurve = new AccuracyCurveWidget(rightX + 10, 0, RIGHT_PANEL_WIDTH - 20,
                ACCURACY_GRAPH_HEIGHT, accuracyAnchors, this::onAccuracyAnchorsChanged);
        addCombatWidget(accuracyCurve);

        removeAnchorButton = Button.builder(LABEL_REMOVE_ANCHOR, button -> accuracyCurve.removeSelected())
                .bounds(rightX + RIGHT_PANEL_WIDTH - 76, 0, 66, 18).build();
        addCombatWidget(removeAnchorButton);

        updateAccuracyControls();
        layoutCombat();
    }

    // One attack range's impact + visuals rows: the destroy-blocks toggle with its crater size,
    // then the impact particle and how long it showers. Toggling destroy or clearing the particle
    // collapses the dependent slider, so both call back into the layout.
    private ImpactWidgets addImpactRows(int rightX,
                                        boolean destroySelected, Consumer<Boolean> onDestroyChanged,
                                        int radius, Consumer<Integer> onRadiusChanged,
                                        java.util.function.Supplier<String> particleId,
                                        Consumer<String> onParticleChanged,
                                        float particleSeconds, Consumer<Float> onSecondsChanged) {
        // The Impact section pairs the destroy toggle (column one) with its crater size (column
        // two); the Visuals section pairs the particle button (column one) with its duration.
        Checkbox destroy = Checkbox.builder(LABEL_IMPACT_DESTROY, this.font)
                .pos(rightX + COL1_LABEL_X, 0).selected(destroySelected)
                .onValueChange((box, value) -> {
                    onDestroyChanged.accept(value);
                    layoutCombat();
                    updateCombatScroll();
                }).tooltip(Tooltip.create(IMPACT_DESTROY_TOOLTIP)).build();
        addCombatWidget(destroy);

        LabeledSlider radiusSlider = new LabeledSlider(rightX + COL2_CONTROL_X, 0, COL_CONTROL_WIDTH, 20,
                Component.empty(), NpcValidation.MIN_IMPACT_RADIUS, NpcValidation.MAX_IMPACT_RADIUS,
                radius, value -> Math.round(value) + " blocks",
                value -> onRadiusChanged.accept(Math.round(value)));
        radiusSlider.setTooltip(Tooltip.create(IMPACT_RADIUS_TOOLTIP));
        addCombatWidget(radiusSlider);

        Button particleButton = Button.builder(particleLabel(particleId.get()), button ->
                Minecraft.getInstance().setScreenAndShow(new ParticlePickerScreen(this,
                        particleId.get(), chosen -> {
                    onParticleChanged.accept(chosen);
                    button.setMessage(particleLabel(chosen));
                    layoutCombat();
                    updateCombatScroll();
                }))).bounds(rightX + COL1_CONTROL_X, 0, COL_CONTROL_WIDTH, 20).build();
        addCombatWidget(particleButton);

        LabeledSlider durationSlider = new LabeledSlider(rightX + COL2_CONTROL_X, 0, COL_CONTROL_WIDTH, 20,
                Component.empty(), 0.0F, NpcValidation.MAX_IMPACT_PARTICLE_SECONDS, particleSeconds,
                value -> String.format(Locale.ROOT, "%.1fs", value), onSecondsChanged::accept);
        addCombatWidget(durationSlider);
        return new ImpactWidgets(destroy, radiusSlider, particleButton, durationSlider);
    }

    private static Component particleLabel(String particleId) {
        return particleId.isEmpty() ? LABEL_PARTICLE_NONE
                : Component.literal(particleId.replace("minecraft:", ""));
    }

    // Give-up seconds: digits only, capped at the max; empty keeps the infinite default.
    private void onGiveUpTyped(String value) {
        String sanitized = value.replaceAll("[^0-9]", "");
        if (!sanitized.equals(value)) {
            giveUpField.setValue(sanitized);
            return;
        }
        if (!sanitized.isEmpty() && Integer.parseInt(sanitized) > NpcValidation.MAX_GIVE_UP_SECONDS) {
            giveUpField.setValue(String.valueOf(NpcValidation.MAX_GIVE_UP_SECONDS));
            return;
        }
        giveUpText = sanitized;
    }

    private int parsedGiveUpSeconds() {
        return giveUpText.isEmpty() ? 0 : Math.min(Integer.parseInt(giveUpText), NpcValidation.MAX_GIVE_UP_SECONDS);
    }

    private void combatPlace(String key, AbstractWidget widget, int y) {
        combatWidgetBaseY.put(widget, y);
        if (key != null) {
            combatY.put(key, y);
        }
    }

    private void combatHide(AbstractWidget widget) {
        combatWidgetBaseY.put(widget, COMBAT_HIDDEN_Y);
    }

    // Walks the sections top-to-bottom, giving each row a y and skipping the ones a toggle
    // collapsed; every widget ends up in combatWidgetBaseY (hidden ones off-screen) and every
    // label/section title in combatY.
    private void layoutCombat() {
        combatWidgetBaseY.clear();
        combatY.clear();
        int y = combatClipTop();

        // Targeting: aggro range beside line-of-sight, then the give-up timer.
        combatY.put("targeting_title", y);
        y += COMBAT_TITLE;
        combatPlace("aggro", aggroSlider, y);
        combatPlace("require_los", requireLosButton, y);
        y += COMBAT_ROW;
        combatPlace("give_up", giveUpField, y);
        y += COMBAT_ROW;

        // Difficulty scaling is a damage rule shared by both attacks: its own full-width row.
        y += COMBAT_GAP;
        combatPlace("difficulty_scaling", difficultyScalingButton, y);
        y += COMBAT_ROW;

        y += COMBAT_GAP;
        combatY.put("close_title", y);
        y += COMBAT_TITLE;
        combatPlace("close_enabled", meleeEnabledButton, y);
        combatPlace("close_damage", meleeDamageField, y);
        y += COMBAT_ROW;
        combatPlace("close_range", meleeRangeSlider, y);
        combatPlace("close_speed", meleeSpeedSlider, y);
        y += COMBAT_ROW;
        combatPlace("close_effects", meleeEffectsButton, y);
        y += COMBAT_ROW;

        // Close impact: the destroy toggle beside its crater size.
        y += COMBAT_GAP;
        combatY.put("close_impact_title", y);
        y += COMBAT_TITLE;
        combatPlace(null, meleeDestroyBox, y);
        if (meleeImpactDestroy) {
            combatPlace("close_radius", meleeRadiusSlider, y);
        } else {
            combatHide(meleeRadiusSlider);
        }
        y += COMBAT_ROW;

        // Close visuals: the particle beside how long it showers.
        y += COMBAT_GAP;
        combatY.put("close_visuals_title", y);
        y += COMBAT_TITLE;
        combatPlace("close_particle", meleeImpactParticleButton, y);
        if (!meleeImpactParticleId.isEmpty()) {
            combatPlace("close_duration", meleeDurationSlider, y);
        } else {
            combatHide(meleeDurationSlider);
        }
        y += COMBAT_ROW;

        y += COMBAT_GAP;
        combatY.put("long_title", y);
        y += COMBAT_TITLE;
        combatPlace("long_enabled", rangedEnabledButton, y);
        combatPlace("long_damage", rangedDamageField, y);
        y += COMBAT_ROW;
        combatPlace("long_distance", rangedDistanceSlider, y);
        combatPlace("long_speed", rangedSpeedSlider, y);
        y += COMBAT_ROW;
        combatPlace("long_effects", rangedEffectsButton, y);
        y += COMBAT_ROW;

        y += COMBAT_GAP;
        combatY.put("long_impact_title", y);
        y += COMBAT_TITLE;
        combatPlace(null, rangedDestroyBox, y);
        if (rangedImpactDestroy) {
            combatPlace("long_radius", rangedRadiusSlider, y);
        } else {
            combatHide(rangedRadiusSlider);
        }
        y += COMBAT_ROW;

        y += COMBAT_GAP;
        combatY.put("long_visuals_title", y);
        y += COMBAT_TITLE;
        combatPlace("long_particle", rangedImpactParticleButton, y);
        if (!rangedImpactParticleId.isEmpty()) {
            combatPlace("long_duration", rangedDurationSlider, y);
        } else {
            combatHide(rangedDurationSlider);
        }
        y += COMBAT_ROW;

        // Projectile: the item beside its launch speed, then the homing/curve toggle pair.
        y += COMBAT_GAP;
        combatY.put("projectile_title", y);
        y += COMBAT_TITLE;
        combatPlace("projectile_label", projectileSlot, y);
        combatPlace("projectile_speed", projectileSpeedSlider, y);
        y += COMBAT_ROW;
        combatPlace(null, homingBox, y);
        combatPlace(null, curveBox, y);
        y += COMBAT_ROW;
        if (NpcWarnings.rangedWithoutHoming(rangedEnabled, homingEnabled)) {
            combatY.put("homing_warning", y);
            y += 12;
        }
        if (NpcWarnings.rangedDrawHeld(rangedEnabled,
                NpcEquipment.isDrawnWeapon(equipmentSettings.rangedWeaponId()), rangedIntervalSeconds)) {
            combatY.put("draw_held_warning", y);
            y += 12;
        }
        if (curveEnabled) {
            combatPlace("curve_distance", curveMaxDistanceSlider, y);
            y += COMBAT_ROW;
        } else {
            combatHide(curveMaxDistanceSlider);
        }

        y += COMBAT_GAP;
        combatY.put("accuracy_title", y);
        y += COMBAT_TITLE;
        combatPlace(null, dynamicAccuracyBox, y);
        y += COMBAT_ROW;
        if (dynamicAccuracy) {
            combatHide(fixedAccuracySlider);
            combatPlace(null, accuracyCurve, y);
            combatY.put("accuracy_graph", y);
            y += ACCURACY_GRAPH_HEIGHT + 6;
            combatY.put("accuracy_card", y);
            combatPlace(null, removeAnchorButton, y);
            y += 20 + 6;
        } else {
            combatHide(accuracyCurve);
            combatHide(removeAnchorButton);
            combatPlace("fixed_accuracy", fixedAccuracySlider, y);
            y += COMBAT_ROW;
        }

        // Range switching: one button opens the rule editor (a popup, like the regen editor), so the
        // unbounded rule list stays out of this already-scrolling tab.
        y += COMBAT_GAP;
        combatY.put("range_switch_title", y);
        y += COMBAT_TITLE;
        combatPlace("range_switch_button", rangeSwitchButton, y);
        y += COMBAT_ROW;

        combatContentBottom = y;
        combatScroll = Math.min(combatScroll, maxCombatScroll());
    }

    private void updateAccuracyControls() {
        accuracyCurve.active = dynamicAccuracy;
        removeAnchorButton.active = dynamicAccuracy;
    }

    private void onAccuracyAnchorsChanged() {
        removeAnchorButton.active = dynamicAccuracy && accuracyCurve.selectedAnchor() != null;
        updateCombatScroll();
    }

    // Repositions the Combat tab widgets by the scroll offset; rows fully outside the panel hide.
    // The tall accuracy graph stays while it partly overlaps the panel and clips itself to the
    // panel window, so it scrolls smoothly instead of popping in and out at the edge.
    private void updateCombatScroll() {
        boolean combat = activeTab == EditorTab.COMBAT && !combatAdvancedView;
        int panelTop = combatClipTop();
        int panelBottom = CONTENT_TOP + CONTENT_HEIGHT - 4;
        for (Map.Entry<AbstractWidget, Integer> entry : combatWidgetBaseY.entrySet()) {
            AbstractWidget widget = entry.getKey();
            int y = entry.getValue() - combatScroll;
            widget.setY(y);
            if (widget == accuracyCurve) {
                widget.visible = combat && y + widget.getHeight() > panelTop && y < panelBottom;
                accuracyCurve.setClipBounds(panelTop, panelBottom);
            } else {
                widget.visible = combat && y >= panelTop && y + widget.getHeight() <= panelBottom;
            }
        }
        if (combat) {
            // The Remove button belongs to the card only while an anchor is actually selected.
            removeAnchorButton.visible = removeAnchorButton.visible
                    && dynamicAccuracy && accuracyCurve.selectedAnchor() != null;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeTab == EditorTab.COMBAT && mouseX >= rightPanelX()
                && mouseX <= rightPanelX() + RIGHT_PANEL_WIDTH
                && mouseY >= CONTENT_TOP && mouseY <= CONTENT_TOP + CONTENT_HEIGHT) {
            combatScroll = Math.clamp((int) (combatScroll - scrollY * 16), 0, maxCombatScroll());
            updateCombatScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void setCombatAdvancedView(boolean advanced) {
        if (advanced && advancedCombat.rules().isEmpty()) {
            seedNavHoldRule();
        }
        combatAdvancedView = advanced;
        refreshCombatModeButtons();
        updateCombatScroll();
    }

    // Advanced combat suppresses the default movement AI, so an air/water NPC would stop flying/swimming
    // unless a rule holds the nav mode for the fight. Entering advanced with no rules seeds one editable
    // ON_COMBAT_START node that pins the NPC's own Movement-tab medium; the empty-rules guard means a creator
    // who deletes or edits it is never overruled.
    private void seedNavHoldRule() {
        String navMode = switch (movementType) {
            case "air" -> "fly";
            case "water" -> "water";
            default -> "ground";
        };
        int height = switch (movementType) {
            case "air" -> flightHeight;
            case "water" -> waterDepth;
            default -> 0;
        };
        boolean fixed = switch (movementType) {
            case "air" -> flightHeightFixed;
            case "water" -> waterDepthFixed;
            default -> false;
        };
        NpcCombatAction hold = new NpcCombatAction(NpcCombatActionType.SWITCH_NAV_MODE, NpcTargetSelector.SELF,
                NpcCombatPlacement.atSelf(), NpcValueSource.constant(height), NAV_HOLD_TICKS, 0, navMode,
                List.of(), fixed);
        NpcCombatRule rule = new NpcCombatRule(
                new NpcCombatTrigger(NpcCombatTriggerType.ON_COMBAT_START, 0.0F, 0.0F, ""),
                List.of(), List.of(hold), 0.0F, false, 0, 0);
        advancedCombat = advancedCombat.withRules(List.of(rule));
    }

    private void refreshCombatModeButtons() {
        combatNormalButton.active = combatAdvancedView;
        combatAdvancedButton.active = !combatAdvancedView;
        if (combatRulesButton != null) {
            combatRulesButton.visible = activeTab == EditorTab.COMBAT && combatAdvancedView;
            combatRulesButton.setMessage(combatRulesLabel());
        }
    }

    private Component combatRulesLabel() {
        return Component.translatable("gui.luna_npc.create_npc.combat_edit_rules", advancedCombat.rules().size());
    }

    private void openCombatEditor() {
        Minecraft.getInstance().setScreenAndShow(new CombatGraphScreen(this, advancedCombat,
                updated -> advancedCombat = updated));
    }

    private Component rangeSwitchLabel() {
        return rangeSwitch.enabled()
                ? Component.translatable("gui.luna_npc.create_npc.range_switch_on", rangeSwitch.rules().size())
                : Component.translatable("gui.luna_npc.create_npc.range_switch_off");
    }

    private void openRangeSwitchEditor() {
        Minecraft.getInstance().setScreenAndShow(new EditRangeSwitchScreen(this, rangeSwitch,
                rangeSwitchMaxHealth(), updated -> rangeSwitch = updated));
    }

    // The self-HP rule slider tops out at the NPC's configured max health, read from the live field
    // (falling back to the default while the field is mid-edit or empty).
    private int rangeSwitchMaxHealth() {
        try {
            return Math.clamp(Math.round(Float.parseFloat(healthField.getValue().trim())),
                    1, NpcValidation.MAX_HEALTH);
        } catch (NumberFormatException ignored) {
            return (int) NpcDefaults.HEALTH;
        }
    }

    // Up to five effects preview in the tooltip; the sub-screen is the full page.
    private static Tooltip effectsTooltip(List<NpcAttackEffect> effects) {
        if (effects.isEmpty()) {
            return null;
        }
        var lines = Component.empty();
        for (int index = 0; index < Math.min(5, effects.size()); index++) {
            if (index > 0) {
                lines.append("\n");
            }
            lines.append(TargetEffectScreen.entryLabel(effects.get(index)));
        }
        if (effects.size() > 5) {
            lines.append("\n…");
        }
        return Tooltip.create(lines);
    }

    // The button shows the count; the tooltip previews up to five effects, the sub-screen is
    // the full page.
    private Component effectsButtonLabel(List<NpcAttackEffect> effects) {
        return Component.translatable("gui.luna_npc.create_npc.edit_effects", effects.size());
    }

    private void onDamageTyped(String value, EditBox field, boolean melee) {
        String sanitized = value.replaceAll("[^0-9.]", "");
        if (!sanitized.equals(value)) {
            field.setValue(sanitized);
            return;
        }
        if (melee) {
            meleeDamageText = sanitized;
        } else {
            rangedDamageText = sanitized;
        }
        updateCreateButtonState();
    }

    private static String trimNumber(float value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    // -1 marks an invalid value; both attack damage fields share the rule.
    private static float parsedDamage(String text) {
        try {
            float damage = Float.parseFloat(text);
            return damage >= NpcValidation.MIN_ATTACK_DAMAGE && damage <= NpcValidation.MAX_ATTACK_DAMAGE
                    ? damage : -1.0F;
        } catch (NumberFormatException invalidNumber) {
            return -1.0F;
        }
    }

    private void buildNavigationTab(int rightX) {
        int c1 = rightX + COL1_CONTROL_X;
        int c2 = rightX + COL2_CONTROL_X;
        navigationTabWidgets.clear();

        // The algorithm names can be long, so this button reaches past column one on its own row;
        // its quality/cost stars sit on the lines below rather than beside it.
        pathfindingButton = Button.builder(pathfindingLabel(), button ->
                Minecraft.getInstance().setScreenAndShow(new PathfindingPickerScreen(this, pathfindingType,
                        chosen -> pathfindingType = chosen)))
                .bounds(c1, NAV_ALGORITHM_ROW_Y, 156, 20).build();
        navigationTabWidgets.add(addRenderableWidget(pathfindingButton));

        wanderBehaviorButton = Button.builder(wanderBehaviorLabel(), button -> {
            int next = (NpcValidation.WANDER_BEHAVIORS.indexOf(wanderBehavior) + 1)
                    % NpcValidation.WANDER_BEHAVIORS.size();
            wanderBehavior = NpcValidation.WANDER_BEHAVIORS.get(next);
            updateWanderControls();
        }).bounds(c1, NAV_BEHAVIOR_ROW_Y, COL_CONTROL_WIDTH, 20).build();
        navigationTabWidgets.add(addRenderableWidget(wanderBehaviorButton));

        wanderRadiusSlider = new LabeledSlider(c2, NAV_RADIUS_ROW_Y, COL_CONTROL_WIDTH, 20,
                Component.empty(), NpcValidation.MIN_WANDER_RADIUS, NpcValidation.MAX_WANDER_RADIUS,
                wanderRadius, value -> Math.round(value) + " blocks", value -> {
            wanderRadius = Math.round(value);
            // The minimum walk distance can never exceed the radius; shrinking the radius
            // drags it down with it.
            if (wanderMinGoalDistance > wanderRadius) {
                wanderMinGoalDistance = wanderRadius;
                wanderMinGoalSlider.setValue(wanderMinGoalDistance);
            }
        });
        navigationTabWidgets.add(addRenderableWidget(wanderRadiusSlider));

        wanderMinGoalSlider = new LabeledSlider(c1, NAV_MIN_GOAL_ROW_Y, COL_CONTROL_WIDTH, 20,
                Component.empty(), 0.0F, NpcValidation.MAX_WANDER_RADIUS, wanderMinGoalDistance,
                CreateNpcScreen::formatMinGoalDistance, value -> {
            wanderMinGoalDistance = Math.min(Math.round(value), wanderRadius);
            if (Math.round(value) != wanderMinGoalDistance) {
                wanderMinGoalSlider.setValue(wanderMinGoalDistance);
            }
        });
        wanderMinGoalSlider.setTooltip(Tooltip.create(MIN_GOAL_DISTANCE_TOOLTIP));
        navigationTabWidgets.add(addRenderableWidget(wanderMinGoalSlider));

        wanderIntervalSlider = new RangeSlider(rightX + COL1_LABEL_X, NAV_INTERVAL_SLIDER_Y,
                RIGHT_PANEL_WIDTH - 20, 14, LABEL_WANDER_INTERVAL, NpcValidation.MIN_WANDER_INTERVAL_SECONDS,
                NpcValidation.MAX_WANDER_INTERVAL_SECONDS, wanderMinSeconds, wanderMaxSeconds, (min, max) -> {
            wanderMinSeconds = min;
            wanderMaxSeconds = max;
        });
        navigationTabWidgets.add(addRenderableWidget(wanderIntervalSlider));

        navigationTabWidgets.add(addRenderableWidget(Checkbox.builder(LABEL_AVOID_WATER, this.font)
                .pos(rightX + COL1_LABEL_X, NAV_AVOID_FLUIDS_ROW_Y).selected(avoidWater)
                .onValueChange((box, value) -> avoidWater = value)
                .tooltip(Tooltip.create(AVOID_WATER_TOOLTIP)).build()));
        navigationTabWidgets.add(addRenderableWidget(Checkbox.builder(LABEL_AVOID_LAVA, this.font)
                .pos(rightX + COL2_LABEL_X, NAV_AVOID_FLUIDS_ROW_Y).selected(avoidLava)
                .onValueChange((box, value) -> avoidLava = value)
                .tooltip(Tooltip.create(AVOID_LAVA_TOOLTIP)).build()));

        updateWanderControls();
    }

    private Component pathfindingLabel() {
        return Component.translatable("gui.luna_npc.pathfinding." + pathfindingType);
    }

    private Component wanderBehaviorLabel() {
        return Component.translatable("gui.luna_npc.create_npc.wander." + wanderBehavior);
    }

    // The radius only matters while the NPC actually wanders; the rest interval is a
    // stroll-only concept (restless walks without breaks, static never walks).
    private void updateWanderControls() {
        wanderBehaviorButton.setMessage(wanderBehaviorLabel());
        wanderBehaviorButton.setTooltip(Tooltip.create(
                Component.translatable("gui.luna_npc.create_npc.wander." + wanderBehavior + ".tooltip")));
        wanderRadiusSlider.active = !NpcWanderSettings.STATIC.equals(wanderBehavior);
        wanderMinGoalSlider.active = !NpcWanderSettings.STATIC.equals(wanderBehavior);
        wanderIntervalSlider.active = NpcWanderSettings.STROLL.equals(wanderBehavior);
    }

    private static String formatMinGoalDistance(float value) {
        int blocks = Math.round(value);
        return blocks <= 0
                ? Component.translatable("gui.luna_npc.create_npc.min_goal_distance.none").getString()
                : blocks + " blocks";
    }

    private static String formatSpeed(float blocksPerSecond) {
        return String.format(Locale.ROOT, "%.1f B/S", blocksPerSecond);
    }

    private static String formatPercent(float value) {
        return Math.round(value) + "%";
    }

    // The speed pair is named per movement type: walk/run, swim/swim boost, fly/fly boost.
    private Component speedLabel(boolean boost) {
        String key = switch (movementType) {
            case "water" -> boost ? "swim_boost" : "swim_speed";
            case "air" -> boost ? "fly_boost" : "fly_speed";
            default -> boost ? "run_speed" : "walk_speed";
        };
        return Component.translatable("gui.luna_npc.create_npc." + key);
    }

    private Component movementTypeLabel() {
        return Component.translatable("gui.luna_npc.create_npc.movement_type." + movementType);
    }

    // Only the widgets of the active movement type show; all values are kept when switching.
    private void updateMovementTypeControls() {
        boolean movement = activeTab == EditorTab.MOVEMENT;
        boolean ground = "ground".equals(movementType);
        boolean water = "water".equals(movementType);
        boolean air = "air".equals(movementType);
        for (ItemSlotButton slot : avoidedBlockSlots) {
            slot.visible = movement && ground;
        }
        clearAvoidedBlocksButton.visible = movement && ground;
        // Jumping is ground-only and shares knockback's row in column two; knockback itself keeps
        // its fixed column-one spot for every movement type, so nothing needs repositioning.
        jumpHeightSlider.visible = movement && ground;
        waterDepthSlider.visible = movement && water;
        waterDepthLeaveBox.visible = movement && water;
        waterDepthRangeSlider.visible = movement && water;
        hoverButton.visible = movement && air;
        hoverIntervalSlider.visible = movement && air;
        hoverAmountSlider.visible = movement && air;
        flightHeightSlider.visible = movement && air;
        flightHeightFixedBox.visible = movement && air;
        flightHeightRangeSlider.visible = movement && air;
        hoverIntervalSlider.active = hoverEnabled;
        hoverAmountSlider.active = hoverEnabled;
        // Swimming is handled by the dedicated water movement for water NPCs, so the trait only
        // shows for the other types.
        canSwimBox.visible = activeTab == EditorTab.TRAITS && !water;
        if (movement && ground) {
            refreshAvoidedBlockSlots();
        }
    }

    private void refreshAvoidedBlockSlots() {
        for (int index = 0; index < avoidedBlockSlots.length; index++) {
            avoidedBlockSlots[index].setStack(index < avoidedBlocks.size()
                    ? NpcEquipment.resolve(avoidedBlocks.get(index)) : ItemStack.EMPTY);
        }
    }

    private void buildFactionTab(int rightX) {
        factionTabWidgets.clear();
        friendlyFireBox = null;
        factionList = new net.lunacore.luna_npc.client.widget.SimpleSelectionList(
                RIGHT_PANEL_WIDTH - 20, FACTION_LIST_HEIGHT, FACTION_LIST_TOP);
        factionList.setX(rightX + 10);
        factionTabWidgets.add(addRenderableWidget(factionList));
        rebuildFactionList();
        refreshFriendlyFireControl();
    }

    // The alliance-wide rule can override this NPC's own friendly-fire vote. Checkbox has no
    // selected setter, so the override is applied by rebuilding the box: per_npc shows/edits the
    // vote, allow locks it checked, block locks it unchecked. The vote itself is never overwritten.
    private void refreshFriendlyFireControl() {
        if (friendlyFireBox != null) {
            removeWidget(friendlyFireBox);
            factionTabWidgets.remove(friendlyFireBox);
        }
        net.lunacore.luna_npc.api.NpcAlliance selected =
                net.lunacore.luna_npc.client.ClientAlliances.byId(primaryAlliance());
        String rule = selected == null
                ? net.lunacore.luna_npc.api.NpcAlliance.FF_PER_NPC : selected.friendlyFire();
        boolean perNpc = rule.equals(net.lunacore.luna_npc.api.NpcAlliance.FF_PER_NPC);
        friendlyFireLocked = !perNpc;
        boolean shown = perNpc ? factionFriendlyFire
                : rule.equals(net.lunacore.luna_npc.api.NpcAlliance.FF_ALLOW);
        friendlyFireBox = Checkbox.builder(LABEL_FRIENDLY_FIRE, this.font)
                .pos(rightPanelX() + 10, FACTION_FF_ROW_Y).selected(shown)
                .maxWidth(RIGHT_PANEL_WIDTH - 20)
                .onValueChange((box, value) -> factionFriendlyFire = value).build();
        friendlyFireBox.active = perNpc;
        friendlyFireBox.visible = activeTab == EditorTab.FACTION;
        factionTabWidgets.add(addRenderableWidget(friendlyFireBox));
    }

    private void rebuildFactionList() {
        List<net.lunacore.luna_npc.client.widget.SimpleSelectionList.Entry> entries = new ArrayList<>();
        for (net.lunacore.luna_npc.api.NpcAlliance alliance
                : net.lunacore.luna_npc.client.ClientAlliances.all()) {
            String id = alliance.id();
            // Multi-select: an NPC can belong to several factions at once. Each row toggles membership
            // and shows a check when it's on.
            entries.add(new net.lunacore.luna_npc.client.widget.SimpleSelectionList.Entry(
                    id, Component.literal(alliance.name()), this::toggleAlliance,
                    null, () -> allianceIds.contains(id)));
        }
        factionList.fill(entries, null);
    }

    private void toggleAlliance(String id) {
        if (!allianceIds.remove(id)) {
            allianceIds.add(id);
        }
        refreshFriendlyFireControl();
    }

    // The single alliance whose friendly-fire rule the FF control reflects: the primary (first) faction.
    private String primaryAlliance() {
        return allianceIds.isEmpty() ? null : allianceIds.iterator().next();
    }

    // "Players, Villagers" or "None" — the picked alliance's relationship lists as readable names.
    private Component allianceNames(List<String> ids) {
        if (ids.isEmpty()) {
            return Component.translatable("gui.luna_npc.create_npc.faction_none");
        }
        StringBuilder joined = new StringBuilder();
        for (String id : ids) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(net.lunacore.luna_npc.client.ClientAlliances.nameOf(id));
        }
        return Component.literal(joined.toString());
    }

    // The sound cells are their own little two-column grid: a narrow id field with a ♪ picker
    // button fits two side by side, so the label sits at COL*_LABEL_X and the field/button here.
    private static final int SOUND_C1_EDIT_X = 90;
    private static final int SOUND_C1_PLAY_X = 164;
    private static final int SOUND_C2_LABEL_X = 196;
    private static final int SOUND_C2_EDIT_X = 276;
    private static final int SOUND_C2_PLAY_X = 350;
    private static final int SOUND_EDIT_WIDTH = 72;
    private static final int SOUND_PLAY_WIDTH = 18;
    private static final int SOUND_C1_LABEL_MAX = SOUND_C1_EDIT_X - COL1_LABEL_X - 4;
    private static final int SOUND_C2_LABEL_MAX = SOUND_C2_EDIT_X - SOUND_C2_LABEL_X - 4;

    private void buildSoundsTab(int rightX) {
        soundTabWidgets.clear();
        addSoundCell(rightX, 0, soundRowY(0), LABEL_HURT_SOUND, hurtSoundId, value -> hurtSoundId = value);
        addSoundCell(rightX, 1, soundRowY(0), LABEL_DIE_SOUND, dieSoundId, value -> dieSoundId = value);
        addSoundCell(rightX, 0, soundRowY(1), LABEL_AMBIENT_SOUND, ambientSoundId, value -> ambientSoundId = value);
        addSoundCell(rightX, 1, soundRowY(1), LABEL_AGGRO_SOUND, aggroSoundId, value -> aggroSoundId = value);
        addSoundCell(rightX, 0, soundCombatRowY(0), LABEL_COMBAT_CLOSE_SOUND, combatCloseSoundId,
                value -> combatCloseSoundId = value);
        addSoundCell(rightX, 1, soundCombatRowY(0), LABEL_COMBAT_FAR_SOUND, combatFarSoundId,
                value -> combatFarSoundId = value);
        addSoundCell(rightX, 0, soundCombatRowY(1), LABEL_CLOSE_IMPACT_SOUND, closeImpactSoundId,
                value -> closeImpactSoundId = value);
        addSoundCell(rightX, 1, soundCombatRowY(1), LABEL_FAR_IMPACT_SOUND, farImpactSoundId,
                value -> farImpactSoundId = value);

        ambientIntervalField = new EditBox(this.font, rightX + FULL_CONTROL_X, soundIntervalY(), 60, 20,
                LABEL_AMBIENT_INTERVAL);
        ambientIntervalField.setMaxLength(3);
        ambientIntervalField.setValue(ambientIntervalText);
        ambientIntervalField.setResponder(value -> {
            String sanitized = value.replaceAll("[^0-9]", "");
            if (!sanitized.equals(value)) {
                ambientIntervalField.setValue(sanitized);
                return;
            }
            ambientIntervalText = sanitized;
            updateCreateButtonState();
        });
        soundTabWidgets.add(addRenderableWidget(ambientIntervalField));
    }

    private int soundRowY(int row) {
        return CONTENT_TOP + 14 + row * 24;
    }

    // The four combat sounds (attack close/long, impact close/long) sit under their own gold
    // section title below the four base sounds.
    private int soundCombatTitleY() {
        return CONTENT_TOP + 66;
    }

    private int soundCombatRowY(int row) {
        return CONTENT_TOP + 80 + row * 24;
    }

    private int soundIntervalY() {
        return CONTENT_TOP + 132;
    }

    private void addSoundCell(int rightX, int column, int y, Component label, String currentValue,
                              Consumer<String> onChanged) {
        int editX = rightX + (column == 0 ? SOUND_C1_EDIT_X : SOUND_C2_EDIT_X);
        int playX = rightX + (column == 0 ? SOUND_C1_PLAY_X : SOUND_C2_PLAY_X);
        EditBox field = new EditBox(this.font, editX, y, SOUND_EDIT_WIDTH, 20, label);
        field.setMaxLength(200);
        field.setValue(currentValue);
        field.setResponder(onChanged);
        soundTabWidgets.add(addRenderableWidget(field));
        soundTabWidgets.add(addRenderableWidget(Button.builder(Component.literal("♪"), button ->
                        Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(this, field.getValue(),
                                onChanged)))
                .bounds(playX, y, SOUND_PLAY_WIDTH, 20).build()));
    }

    // Slot order: helmet, chestplate, leggings, boots, main hand, ranged weapon, off hand.
    private static final String[] EQUIPMENT_SLOT_KEYS = {"helmet", "chestplate", "leggings", "boots",
            "main_hand", "ranged_weapon", "off_hand"};

    private static Component equipmentSlotName(int slotIndex) {
        return Component.translatable("gui.luna_npc.create_npc.slot." + EQUIPMENT_SLOT_KEYS[slotIndex]);
    }

    // The two weapon slots behave differently from what their names alone suggest, so their tooltip
    // spells out when each is held and what it decides.
    private Component equipmentSlotTooltip(int slotIndex) {
        Component name = equipmentSlotName(slotIndex);
        String key = EQUIPMENT_SLOT_KEYS[slotIndex];
        String hint;
        if (key.equals("main_hand") || key.equals("ranged_weapon")) {
            hint = "gui.luna_npc.create_npc.slot." + key + ".hint";
        } else if (slotIndex == 0 && !NpcModelCatalog.isStretchable(modelSettings.modelType())) {
            hint = "gui.luna_npc.create_npc.slot.head_items_only.hint";
        } else {
            return name;
        }
        return name.copy().append("\n").append(Component.translatable(hint).withStyle(ChatFormatting.DARK_GRAY));
    }

    // Helmet, the three armor pieces, main hand, ranged weapon (held in the main hand), off hand.
    private static final NpcModelCatalog.GearSlot[] EQUIPMENT_GEAR = {NpcModelCatalog.GearSlot.HEAD,
            NpcModelCatalog.GearSlot.ARMOR, NpcModelCatalog.GearSlot.ARMOR, NpcModelCatalog.GearSlot.ARMOR,
            NpcModelCatalog.GearSlot.MAIN_HAND, NpcModelCatalog.GearSlot.MAIN_HAND, NpcModelCatalog.GearSlot.OFF_HAND};

    private void buildInventoryTab(int leftX, int rightX) {
        inventoryTabWidgets.clear();
        EquipmentSlot[] pickerFilters = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, null, null, null};
        for (int index = 0; index < equipmentSlots.length; index++) {
            int slotIndex = index;
            EquipmentSlot filter = pickerFilters[index];
            ItemSlotButton button = new ItemSlotButton(leftX + 7, CONTENT_TOP + 16 + index * 22,
                    LABEL_EQUIPMENT, event -> {
                if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    // Right-click still clears the slot (and drops its enchantments with it).
                    setEquipmentId(slotIndex, "");
                } else if (NpcEquipment.resolve(equipmentIdFor(slotIndex)).isEmpty()) {
                    // Empty slot: pick an item straight away, as before.
                    openItemPicker(slotIndex, filter);
                } else {
                    // Filled slot: offer choosing a different item or enchanting the current one.
                    Minecraft.getInstance().setScreenAndShow(new SlotActionScreen(this,
                            NpcEquipment.resolve(equipmentIdFor(slotIndex)), equipmentSlotName(slotIndex),
                            () -> openItemPicker(slotIndex, filter),
                            () -> openEnchantScreen(slotIndex), null));
                }
            });
            equipmentSlots[index] = button;
            inventoryTabWidgets.add(addRenderableWidget(button));
        }

        gearStatsBox = Checkbox.builder(LABEL_GEAR_STATS, this.font)
                .pos(leftX + 7, CONTENT_TOP + 178).selected(equipmentSettings.gearAffectsStats())
                .maxWidth(LEFT_PANEL_WIDTH - 14)
                .onValueChange((box, value) -> equipmentSettings = new NpcEquipmentSettings(
                        equipmentSettings.helmetId(), equipmentSettings.chestplateId(),
                        equipmentSettings.leggingsId(), equipmentSettings.bootsId(),
                        equipmentSettings.mainHandId(), equipmentSettings.rangedWeaponId(),
                        equipmentSettings.offHandId(), value, equipmentSettings.enchantments()))
                .build();
        inventoryTabWidgets.add(addRenderableWidget(gearStatsBox));

        inventoryTabWidgets.add(addRenderableWidget(new RangeSlider(rightX + 10, CONTENT_TOP + 30,
                RIGHT_PANEL_WIDTH - 20, 14,
                LABEL_XP_DROP, 0, NpcValidation.MAX_XP_DROP, xpDropMin, xpDropMax, (min, max) -> {
            xpDropMin = min;
            xpDropMax = max;
        })));

        // Centre the fixed 9-wide drop grid in the widened panel instead of leaving it left-aligned.
        int gridLeft = rightX + (RIGHT_PANEL_WIDTH - DROP_GRID_COLUMNS * ItemSlotButton.SIZE) / 2;
        int gridTop = CONTENT_TOP + 64;
        for (int index = 0; index < dropSlots.length; index++) {
            int slotIndex = index;
            int x = gridLeft + (index % DROP_GRID_COLUMNS) * ItemSlotButton.SIZE;
            int y = gridTop + (index / DROP_GRID_COLUMNS) * ItemSlotButton.SIZE;
            ItemSlotButton button = new ItemSlotButton(x, y, LABEL_ITEM_DROP, event -> {
                if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    itemDrops.remove(slotIndex);
                    if (selectedDropSlot == slotIndex) {
                        selectedDropSlot = -1;
                    }
                    refreshInventoryWidgets();
                } else if (itemDrops.containsKey(slotIndex)) {
                    selectedDropSlot = slotIndex;
                    refreshInventoryWidgets();
                } else {
                    Minecraft.getInstance().setScreenAndShow(new ItemPickerScreen(this, null, itemId -> {
                        if (!itemId.isEmpty()) {
                            itemDrops.put(slotIndex, new NpcDropSettings.NpcItemDrop(slotIndex, itemId,
                                    NpcDefaults.DROP_CHANCE_PERCENT));
                            selectedDropSlot = slotIndex;
                        }
                    }));
                }
            });
            dropSlots[index] = button;
            inventoryTabWidgets.add(addRenderableWidget(button));
        }

        chanceSlider = new ChanceSlider(gridLeft, gridTop + DROP_GRID_ROWS * ItemSlotButton.SIZE + 6, 162, 18);
        inventoryTabWidgets.add(addRenderableWidget(chanceSlider));
        refreshInventoryWidgets();
    }

    private void buildSpawnTab(int rightX) {
        spawnTabWidgets.clear();
        seedSpawnState();
        buildSpawnOptions();

        int c1 = rightX + COL1_CONTROL_X;
        int c2 = rightX + COL2_CONTROL_X;

        respawnButton = addSpawnWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, respawnEnabled)
                .displayOnlyValue().withTooltip(value -> Tooltip.create(RESPAWN_TOOLTIP))
                .create(c1, SPAWN_RESPAWN_ROW_Y, COL_CONTROL_WIDTH, 20, LABEL_RESPAWN, (button, value) -> {
                    respawnEnabled = value;
                    rebuildSpawnSettings();
                    updateSpawnControls();
                }));

        respawnAtDeathButton = addSpawnWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, respawnAtDeath)
                .displayOnlyValue().withTooltip(value -> Tooltip.create(RESPAWN_AT_DEATH_TOOLTIP))
                .create(c2, SPAWN_RESPAWN_ROW_Y, COL_CONTROL_WIDTH, 20, LABEL_RESPAWN_AT_DEATH, (button, value) -> {
                    respawnAtDeath = value;
                    rebuildSpawnSettings();
                }));

        respawnTimeSlider = addSpawnWidget(new RangeSlider(rightX + COL1_LABEL_X, SPAWN_RESPAWN_TIME_SLIDER_Y,
                RIGHT_PANEL_WIDTH - 20, 14, LABEL_RESPAWN_TIME, 0, NpcSpawnSettings.MAX_RESPAWN_SECONDS,
                respawnMinSeconds, respawnMaxSeconds, (min, max) -> {
            respawnMinSeconds = min;
            respawnMaxSeconds = max;
            rebuildSpawnSettings();
        }));

        naturalSpawnButton = addSpawnWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, naturalSpawnEnabled)
                .displayOnlyValue().withTooltip(value -> Tooltip.create(NATURAL_SPAWN_TOOLTIP))
                .create(c1, SPAWN_NATURAL_ROW_Y, COL_CONTROL_WIDTH, 20, LABEL_NATURAL_SPAWN, (button, value) -> {
                    naturalSpawnEnabled = value;
                    rebuildSpawnSettings();
                    updateSpawnControls();
                }));

        maxNearbyField = new EditBox(this.font, c2, SPAWN_NATURAL_ROW_Y, COL_CONTROL_WIDTH, 20, LABEL_MAX_NEARBY);
        maxNearbyField.setMaxLength(5);
        maxNearbyField.setValue(String.valueOf(maxNearby));
        maxNearbyField.setTooltip(Tooltip.create(MAX_NEARBY_TOOLTIP));
        maxNearbyField.setResponder(value -> {
            String sanitized = digitsOnly(maxNearbyField, value);
            if (sanitized == null) {
                return;
            }
            maxNearby = sanitized.isEmpty() ? 0 : Integer.parseInt(sanitized);
            rebuildSpawnSettings();
        });
        addSpawnWidget(maxNearbyField);

        spawnLightSlider = addSpawnWidget(new RangeSlider(rightX + COL1_LABEL_X, SPAWN_LIGHT_SLIDER_Y,
                SPAWN_LIGHT_SLIDER_WIDTH, 14, LABEL_SPAWN_LIGHT, 0, NpcSpawnSettings.MAX_LIGHT,
                minSpawnLight, maxSpawnLight, (min, max) -> {
            minSpawnLight = min;
            maxSpawnLight = max;
            rebuildSpawnSettings();
        }));

        // Ground-only surface/cave placement for biome and structure spawns (zones use their own
        // per-zone setting). Lives in the detail column's placement block; cycles Anywhere -> Surface
        // only -> Caves only. Air and water swap in their own controls in the same slot.
        surfaceModeButton = addSpawnWidget(Button.builder(surfaceMasterLabel(), button -> {
            SurfaceMode[] modes = SurfaceMode.values();
            spawnSurfaceMode = modes[(spawnSurfaceMode.ordinal() + 1) % modes.length];
            surfaceModeButton.setMessage(surfaceMasterLabel());
            rebuildSpawnSettings();
        }).bounds(rightX + SPAWN_DETAIL_X + 4, SPAWN_AIR_HEIGHT_SLIDER_Y,
                SPAWN_DETAIL_WIDTH - 8, 18).build());
        surfaceModeButton.setTooltip(Tooltip.create(SURFACE_MASTER_TOOLTIP));

        // Kept inside the left list column so they never reach under the detail column's Global/Placement
        // controls; they line up with the list beneath them.
        int methodWidth = (SPAWN_LIST_WIDTH - 8) / 3;
        int methodLeft = rightX + COL1_LABEL_X;
        spawnBiomesButton = addSpawnWidget(Button.builder(LABEL_SPAWN_BIOMES,
                button -> selectSpawnMethod(SpawnMethod.BIOMES))
                .bounds(methodLeft, SPAWN_METHOD_ROW_Y, methodWidth, 20).build());
        spawnStructuresButton = addSpawnWidget(Button.builder(LABEL_SPAWN_STRUCTURES,
                button -> selectSpawnMethod(SpawnMethod.STRUCTURES))
                .bounds(methodLeft + methodWidth + 4, SPAWN_METHOD_ROW_Y, methodWidth, 20).build());
        spawnZonesButton = addSpawnWidget(Button.builder(LABEL_SPAWN_ZONES,
                button -> selectSpawnMethod(SpawnMethod.ZONES))
                .bounds(methodLeft + 2 * (methodWidth + 4), SPAWN_METHOD_ROW_Y, methodWidth, 20).build());

        spawnSearchField = new EditBox(this.font, rightX + COL1_LABEL_X, SPAWN_SEARCH_Y, SPAWN_LIST_WIDTH, 16,
                Component.translatable("gui.luna_npc.item_picker.search"));
        spawnSearchField.setMaxLength(100);
        spawnSearchField.setHint(Component.translatable("gui.luna_npc.item_picker.search"));
        spawnSearchField.setValue(spawnQuery);
        spawnSearchField.setResponder(value -> {
            spawnQuery = value;
            rebuildSpawnList();
        });
        addSpawnWidget(spawnSearchField);

        spawnList = new SimpleSelectionList(SPAWN_LIST_WIDTH, SPAWN_LIST_HEIGHT, SPAWN_LIST_TOP);
        spawnList.setX(rightX + COL1_LABEL_X);
        addSpawnWidget(spawnList);

        int detailX = rightX + SPAWN_DETAIL_X + 4;
        int detailWidth = SPAWN_DETAIL_WIDTH - 8;

        // Global mode is an either/or for the biome method: one density covers every biome and dimension,
        // or the per-biome list edits each one. Toggling swaps the two live through updateSpawnControls().
        globalBiomeToggle = addSpawnWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, useGlobalBiome)
                .displayOnlyValue().withTooltip(value -> Tooltip.create(SPAWN_GLOBAL_TOOLTIP))
                .create(detailX, SPAWN_DETAIL_ROW1_CTRL_Y, detailWidth, 20, LABEL_SPAWN_GLOBAL, (button, value) -> {
                    useGlobalBiome = value;
                    rebuildSpawnSettings();
                    updateSpawnControls();
                }));

        // The global density slider shares the per-biome slider's exact slot (detail row 2), so flipping
        // Global never makes the density control jump or resize — only the biome list on the left swaps.
        globalWeightSlider = addSpawnWidget(new LabeledSlider(detailX, SPAWN_DETAIL_ROW2_CTRL_Y,
                detailWidth, 18, LABEL_GLOBAL_DENSITY, 0, 100, globalBiomeWeight,
                CreateNpcScreen::formatPercent, value -> {
            globalBiomeWeight = Math.clamp(Math.round(value), 0, 100);
            rebuildSpawnSettings();
        }));

        // One density % slider for the selected biome, structure or zone (its weight into the density
        // cap). Global mode swaps it for globalWeightSlider; zones add a max-count field above it.
        densitySlider = addSpawnWidget(new LabeledSlider(detailX, SPAWN_DETAIL_ROW2_CTRL_Y, detailWidth, 18,
                LABEL_DENSITY, 0, 100, currentItemWeight(), CreateNpcScreen::formatPercent, value -> {
            applyItemWeight(Math.clamp(Math.round(value), 0, 100));
            rebuildSpawnSettings();
        }));

        zoneMaxField = spawnWeightField(detailX, SPAWN_DETAIL_ROW1_CTRL_Y, detailWidth, LABEL_ZONE_MAX, value -> {
            updateZoneSpawn(spawnSelectedId, currentItemWeight(), value);
            rebuildSpawnSettings();
        });
        zoneMaxField.setTooltip(Tooltip.create(ZONE_MAX_TOOLTIP));

        // Fly-only: where in the sky a naturally-spawned air NPC may appear (world-Y window + a
        // height-above-terrain window). Shown only for the "air" movement type.
        airHeightSlider = addSpawnWidget(new RangeSlider(detailX, SPAWN_AIR_HEIGHT_SLIDER_Y, detailWidth, 14,
                LABEL_AIR_HEIGHT, NpcAirSpawn.WORLD_Y_FLOOR, NpcAirSpawn.WORLD_Y_CEIL,
                airSpawn.minWorldY(), airSpawn.maxWorldY(), (min, max) -> {
            airSpawn = new NpcAirSpawn(min, max, airSpawn.minAboveGround(), airSpawn.maxAboveGround());
            rebuildSpawnSettings();
        }));
        airHeightSlider.setTooltip(Tooltip.create(AIR_HEIGHT_TOOLTIP));

        airGroundSlider = addSpawnWidget(new RangeSlider(detailX, SPAWN_AIR_GROUND_SLIDER_Y, detailWidth, 14,
                LABEL_AIR_ABOVE_GROUND, 0, NpcAirSpawn.MAX_ABOVE_GROUND,
                airSpawn.minAboveGround(), airSpawn.maxAboveGround(), (min, max) -> {
            airSpawn = new NpcAirSpawn(airSpawn.minWorldY(), airSpawn.maxWorldY(), min, max);
            rebuildSpawnSettings();
        }));
        airGroundSlider.setTooltip(Tooltip.create(AIR_ABOVE_GROUND_TOOLTIP));

        // Swim-only: how deep below the water surface a naturally-spawned water NPC may appear. Shares
        // the placement block's first slot with the ground button and air-height slider; only the
        // current movement type's control shows.
        waterDepthSpawnSlider = addSpawnWidget(new RangeSlider(detailX, SPAWN_AIR_HEIGHT_SLIDER_Y, detailWidth, 14,
                LABEL_WATER_SPAWN_DEPTH, 0, NpcWaterSpawn.MAX_DEPTH,
                waterSpawn.minDepth(), waterSpawn.maxDepth(), (min, max) -> {
            waterSpawn = new NpcWaterSpawn(min, max);
            rebuildSpawnSettings();
        }));
        waterDepthSpawnSlider.setTooltip(Tooltip.create(WATER_SPAWN_DEPTH_TOOLTIP));

        // Wild-spawn persistence (off by default so natural spawns thin out like vanilla mobs); it only
        // makes sense with natural spawning on, so updateSpawnControls() hides it alongside the rest.
        naturalPersistentBox = addSpawnWidget(Checkbox.builder(LABEL_SPAWN_PERSISTENT, this.font)
                .pos(rightX + COL1_LABEL_X, SPAWN_PERSISTENT_ROW_Y).selected(naturalPersistent)
                .onValueChange((box, value) -> {
                    naturalPersistent = value;
                    rebuildSpawnSettings();
                })
                .tooltip(Tooltip.create(SPAWN_PERSISTENT_TOOLTIP)).build());

        // The leave-area tether toggles only govern a zone/natural spawn's home area, so they belong with
        // the Zones method rather than on Navigation next to the always-on movement traits. refreshSpawnDetail
        // shows them only for the Zones method; hand-placed NPCs ignore them entirely (server-side).
        leaveAreaBox = addSpawnWidget(Checkbox.builder(LABEL_LEAVE_AREA, this.font)
                .pos(rightX + 110, SPAWN_PERSISTENT_ROW_Y).selected(leaveArea)
                .onValueChange((box, value) -> {
                    leaveArea = value;
                    rebuildSpawnSettings();
                })
                .tooltip(Tooltip.create(LEAVE_AREA_TOOLTIP)).build());
        leaveAreaCombatBox = addSpawnWidget(Checkbox.builder(LABEL_LEAVE_AREA_COMBAT, this.font)
                .pos(rightX + 210, SPAWN_PERSISTENT_ROW_Y).selected(leaveAreaInCombat)
                .onValueChange((box, value) -> {
                    leaveAreaInCombat = value;
                    rebuildSpawnSettings();
                })
                .tooltip(Tooltip.create(LEAVE_AREA_COMBAT_TOOLTIP)).build());

        rebuildSpawnList();
        updateSpawnControls();
    }

    private <T extends AbstractWidget> T addSpawnWidget(T widget) {
        addRenderableWidget(widget);
        spawnTabWidgets.add(widget);
        return widget;
    }

    // A digit-only weight field for the detail column; populating it (on selection changes) skips the
    // callback so merely showing a value never writes a spurious override.
    private EditBox spawnWeightField(int x, int y, int width, Component label,
                                     java.util.function.IntConsumer onChange) {
        EditBox field = new EditBox(this.font, x, y, width, 18, label);
        field.setMaxLength(6);
        field.setResponder(value -> {
            if (populatingSpawnFields) {
                return;
            }
            String sanitized = digitsOnly(field, value);
            if (sanitized == null) {
                return;
            }
            onChange.accept(sanitized.isEmpty() ? 0 : Integer.parseInt(sanitized));
        });
        addSpawnWidget(field);
        return field;
    }

    // Strips non-digits; re-sets the field and returns null when the raw text differed (the re-set
    // re-enters the responder with the clean value), otherwise returns the digits. Shared with the
    // job sub-type editor screens (same package), so digit sanitisation lives in one place.
    static String digitsOnly(EditBox field, String value) {
        String sanitized = value.replaceAll("[^0-9]", "");
        if (!sanitized.equals(value)) {
            field.setValue(sanitized);
            return null;
        }
        return sanitized;
    }

    // Copies spawnSettings into the mutable working fields. Idempotent: every edit keeps spawnSettings
    // in sync, so re-seeding on an init() rebuild (a resize) restores the same working state.
    private void seedSpawnState() {
        respawnEnabled = spawnSettings.respawnEnabled();
        respawnMinSeconds = spawnSettings.respawnMinSeconds();
        respawnMaxSeconds = spawnSettings.respawnMaxSeconds();
        respawnAtDeath = spawnSettings.respawnAtDeath();
        naturalSpawnEnabled = spawnSettings.naturalSpawnEnabled();
        globalBiomeWeight = spawnSettings.globalBiomeChance();
        useGlobalBiome = spawnSettings.useGlobalBiome();
        airSpawn = spawnSettings.airSpawn();
        waterSpawn = spawnSettings.waterSpawn();
        maxNearby = spawnSettings.maxNearby();
        minSpawnLight = spawnSettings.minSpawnLight();
        maxSpawnLight = spawnSettings.maxSpawnLight();
        leaveArea = spawnSettings.leaveArea();
        leaveAreaInCombat = spawnSettings.leaveAreaInCombat();
        placedPersistent = spawnSettings.placedPersistent();
        naturalPersistent = spawnSettings.naturalPersistent();
        spawnSurfaceMode = spawnSettings.surfaceMode();
        biomeWeights.clear();
        biomeWeights.putAll(spawnSettings.biomeChances());
        structureWeights.clear();
        structureWeights.putAll(spawnSettings.structureChances());
        zoneSpawns.clear();
        zoneSpawns.addAll(spawnSettings.zoneSpawns());
    }

    // Biomes come from the client's synced registry (defensively — never throw here, or the whole
    // tab build aborts). Structures are server-only (not synced), so they arrive via ClientStructures.
    private void buildSpawnOptions() {
        var connection = Minecraft.getInstance().getConnection();
        if (biomeOptions.isEmpty() && connection != null) {
            connection.registryAccess().lookup(Registries.BIOME).ifPresent(registry ->
                    registry.listElements().forEach(ref -> {
                        Identifier id = ref.key().identifier();
                        Component label = Component.translatable("biome." + id.getNamespace() + "." + id.getPath());
                        biomeOptions.add(new SpawnOption(id.toString(), label, id + " " + label.getString()));
                    }));
            biomeOptions.sort(Comparator.comparing(option -> option.label().getString().toLowerCase(Locale.ROOT)));
        }
        structureOptions.clear();
        for (String id : net.lunacore.luna_npc.client.ClientStructures.all()) {
            Identifier parsed = Identifier.tryParse(id);
            String path = parsed == null ? id : parsed.getPath();
            structureOptions.add(new SpawnOption(id, Component.literal(prettify(path)), id));
        }
        structureOptions.sort(Comparator.comparing(option -> option.label().getString().toLowerCase(Locale.ROOT)));
    }

    // Structures have no lang keys, so a path like "village_plains" becomes "Village Plains".
    static String prettify(String path) {
        StringBuilder pretty = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (pretty.length() > 0) {
                pretty.append(' ');
            }
            pretty.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return pretty.toString();
    }

    // "Placement: Anywhere / Surface only / Caves only" for the master button; reuses the zone
    // screen's surface-mode value labels.
    private Component surfaceMasterLabel() {
        return Component.translatable("gui.luna_npc.create_zone.surface." + spawnSurfaceMode.getSerializedName());
    }

    private void rebuildSpawnSettings() {
        spawnSettings = new NpcSpawnSettings(respawnEnabled, respawnMinSeconds, respawnMaxSeconds,
                respawnAtDeath, naturalSpawnEnabled, globalBiomeWeight, Map.copyOf(biomeWeights),
                Map.copyOf(structureWeights), List.copyOf(zoneSpawns), maxNearby, minSpawnLight, maxSpawnLight,
                leaveArea, leaveAreaInCombat, spawnSurfaceMode, placedPersistent, naturalPersistent,
                useGlobalBiome, airSpawn, waterSpawn);
    }

    // Copies jobSettings into the mutable working fields; idempotent like seedSpawnState, so an
    // init() rebuild (a resize, or returning from a sub-type editor) restores the same working state.
    private void seedJobState() {
        jobType = jobSettings.jobType();
        blockOnAnyHit = jobSettings.blockOnAnyHit();
        blockAllSeconds = jobSettings.blockAllSeconds();
        blockAttacker = jobSettings.blockAttacker();
        blockAttackerSeconds = jobSettings.blockAttackerSeconds();
        tradeSubTypes.clear();
        tradeSubTypes.addAll(jobSettings.tradeSubTypes());
        questSubTypes.clear();
        questSubTypes.addAll(jobSettings.questSubTypes());
        farmSubTypes.clear();
        farmSubTypes.addAll(jobSettings.farmSubTypes());
        patrolSubTypes.clear();
        patrolSubTypes.addAll(jobSettings.patrolSubTypes());
        bodyguardSettings = jobSettings.bodyguard();
        offerAllQuests = jobSettings.offerAllQuests();
        tradeSuccessSound = jobSettings.tradeSuccessSound();
        tradeFailSound = jobSettings.tradeFailSound();
        questCompleteSound = jobSettings.questCompleteSound();
        dialog = jobSettings.dialog();
        dialogSurvivesNpcDeath = dialog.storySurvivesNpcDeath();
        dialogSurvivesPlayerDeath = dialog.storySurvivesPlayerDeath();
    }

    private void updateDialogRecord() {
        String id = dialog.dialogId().isBlank() ? UUID.randomUUID().toString() : dialog.dialogId();
        dialog = new NpcDialog(id, dialogNameField.getValue(), dialog.startNodeId(), dialog.nodes(),
                dialogSurvivesNpcDeath, dialogSurvivesPlayerDeath);
        rebuildJobSettings();
    }

    private void rebuildJobSettings() {
        jobSettings = new NpcJobSettings(jobType, blockOnAnyHit, blockAllSeconds, blockAttacker,
                blockAttackerSeconds, List.copyOf(tradeSubTypes), List.copyOf(questSubTypes),
                List.copyOf(farmSubTypes), offerAllQuests, tradeSuccessSound, tradeFailSound,
                questCompleteSound, dialog, List.copyOf(patrolSubTypes), bodyguardSettings);
        updateCreateButtonState();
    }

    private void buildJobsTab(int rightX) {
        jobsTabWidgets.clear();
        seedJobState();
        int c1 = rightX + COL1_CONTROL_X;
        int c2 = rightX + COL2_CONTROL_X;

        jobTypeButton = addJobsWidget(Button.builder(jobTypeLabel(), button -> {
            NpcJobType[] types = NpcJobType.values();
            jobType = types[(jobType.ordinal() + 1) % types.length];
            jobTypeButton.setMessage(jobTypeLabel());
            jobSubTypeSelected = -1;
            rebuildJobSettings();
            updateJobControls();
            rebuildJobSubTypeList();
        }).bounds(c1, colRowY(0), COL_CONTROL_WIDTH, 20).build());

        offerAllQuestsButton = addJobsWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, offerAllQuests)
                .displayOnlyValue().withTooltip(value -> Tooltip.create(
                        Component.translatable("gui.luna_npc.create_npc.offer_all_quests.tooltip")))
                .create(c2, colRowY(0), COL_CONTROL_WIDTH, 20,
                        Component.translatable("gui.luna_npc.create_npc.offer_all_quests"), (button, value) -> {
                    offerAllQuests = value;
                    rebuildJobSettings();
                }));

        blockAnyHitButton = addJobsWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, blockOnAnyHit)
                .displayOnlyValue().withTooltip(value -> Tooltip.create(
                        Component.translatable("gui.luna_npc.create_npc.block_any_hit.tooltip")))
                .create(c1, JOB_BLOCK_TOGGLE_ROW_Y, COL_CONTROL_WIDTH, 20,
                        Component.translatable("gui.luna_npc.create_npc.block_any_hit"), (button, value) -> {
                    blockOnAnyHit = value;
                    rebuildJobSettings();
                    updateJobControls();
                }));

        blockAttackerButton = addJobsWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, blockAttacker)
                .displayOnlyValue().withTooltip(value -> Tooltip.create(
                        Component.translatable("gui.luna_npc.create_npc.block_attacker.tooltip")))
                .create(c2, JOB_BLOCK_TOGGLE_ROW_Y, COL_CONTROL_WIDTH, 20,
                        Component.translatable("gui.luna_npc.create_npc.block_attacker"), (button, value) -> {
                    blockAttacker = value;
                    rebuildJobSettings();
                    updateJobControls();
                }));

        blockAllSecondsSlider = addJobsWidget(new LabeledSlider(c1, JOB_BLOCK_SLIDER_ROW_Y, COL_CONTROL_WIDTH, 20,
                Component.translatable("gui.luna_npc.create_npc.block_any_seconds"), 0,
                NpcValidation.MAX_INTERACTION_BLOCK_SECONDS, blockAllSeconds,
                value -> String.valueOf(Math.round(value)), value -> {
            blockAllSeconds = Math.round(value);
            rebuildJobSettings();
        }));

        blockAttackerSecondsSlider = addJobsWidget(new LabeledSlider(c2, JOB_BLOCK_SLIDER_ROW_Y, COL_CONTROL_WIDTH, 20,
                Component.translatable("gui.luna_npc.create_npc.block_attacker_seconds"), 0,
                NpcValidation.MAX_INTERACTION_BLOCK_SECONDS, blockAttackerSeconds,
                value -> String.valueOf(Math.round(value)), value -> {
            blockAttackerSeconds = Math.round(value);
            rebuildJobSettings();
        }));

        tradeSoundWidgets.clear();
        questSoundWidgets.clear();
        addJobSoundCell(c1, tradeSoundWidgets,
                Component.translatable("gui.luna_npc.create_npc.trade_success_sound"),
                tradeSuccessSound, value -> {
                    tradeSuccessSound = value;
                    rebuildJobSettings();
                });
        addJobSoundCell(c2, tradeSoundWidgets,
                Component.translatable("gui.luna_npc.create_npc.trade_fail_sound"),
                tradeFailSound, value -> {
                    tradeFailSound = value;
                    rebuildJobSettings();
                });
        addJobSoundCell(c1, questSoundWidgets,
                Component.translatable("gui.luna_npc.create_npc.quest_complete_sound"),
                questCompleteSound, value -> {
                    questCompleteSound = value;
                    rebuildJobSettings();
                });

        dialogNameField = addJobsWidget(new EditBox(this.font, c2, colRowY(0), COL_CONTROL_WIDTH, 20,
                Component.translatable("gui.luna_npc.create_npc.dialog_name")));
        dialogNameField.setMaxLength(NpcValidation.MAX_DIALOG_NAME_LENGTH);
        dialogNameField.setValue(dialog.name());
        dialogNameField.setResponder(value -> updateDialogRecord());

        dialogSurviveNpcButton = addJobsWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, dialogSurvivesNpcDeath)
                .displayOnlyValue().create(c1, JOB_SOUND_ROW_Y, COL_CONTROL_WIDTH, 20,
                        Component.translatable("gui.luna_npc.create_npc.dialog_survive_npc_death"), (button, value) -> {
                    dialogSurvivesNpcDeath = value;
                    updateDialogRecord();
                }));
        dialogSurvivePlayerButton = addJobsWidget(CycleButton
                .booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, dialogSurvivesPlayerDeath)
                .displayOnlyValue().create(c2, JOB_SOUND_ROW_Y, COL_CONTROL_WIDTH, 20,
                        Component.translatable("gui.luna_npc.create_npc.dialog_survive_player_death"), (button, value) -> {
                    dialogSurvivesPlayerDeath = value;
                    updateDialogRecord();
                }));
        dialogGraphButton = addJobsWidget(Button.builder(
                Component.translatable("gui.luna_npc.create_npc.edit_dialog_graph"), button ->
                Minecraft.getInstance().setScreenAndShow(new DialogGraphScreen(this, dialog, updated -> {
                    this.dialog = updated;
                    rebuildJobSettings();
                }))).bounds(rightX + COL1_LABEL_X, JOB_SUBTYPE_LIST_TOP, RIGHT_PANEL_WIDTH - 20, 20).build());

        bodyguardButton = addJobsWidget(Button.builder(
                Component.translatable("gui.luna_npc.create_npc.edit_bodyguard"), button ->
                Minecraft.getInstance().setScreenAndShow(new EditBodyguardScreen(this, bodyguardSettings, updated -> {
                    this.bodyguardSettings = updated;
                    rebuildJobSettings();
                }))).bounds(rightX + COL1_LABEL_X, JOB_SOUND_TITLE_Y, RIGHT_PANEL_WIDTH - 20, 20).build());

        subTypeList = new SimpleSelectionList(RIGHT_PANEL_WIDTH - 20, JOB_SUBTYPE_LIST_HEIGHT, JOB_SUBTYPE_LIST_TOP);
        subTypeList.setX(rightX + COL1_LABEL_X);
        addJobsWidget(subTypeList);

        int buttonWidth = (RIGHT_PANEL_WIDTH - 20 - 9) / 4;
        int bx = rightX + COL1_LABEL_X;
        addSubTypeButton = addJobsWidget(Button.builder(
                Component.translatable("gui.luna_npc.create_npc.add_sub_type"), button -> addSubType())
                .bounds(bx, JOB_BUTTON_ROW_Y, buttonWidth, 20).build());
        removeSubTypeButton = addJobsWidget(Button.builder(
                Component.translatable("gui.luna_npc.create_npc.remove_sub_type"), button -> removeSubType())
                .bounds(bx + buttonWidth + 3, JOB_BUTTON_ROW_Y, buttonWidth, 20).build());
        duplicateSubTypeButton = addJobsWidget(Button.builder(
                Component.translatable("gui.luna_npc.create_npc.duplicate_sub_type"), button -> duplicateSubType())
                .bounds(bx + 2 * (buttonWidth + 3), JOB_BUTTON_ROW_Y, buttonWidth, 20).build());
        editSubTypeButton = addJobsWidget(Button.builder(
                Component.translatable("gui.luna_npc.create_npc.edit_sub_type"), button -> editSubType())
                .bounds(bx + 3 * (buttonWidth + 3), JOB_BUTTON_ROW_Y, buttonWidth, 20).build());

        rebuildJobSubTypeList();
        updateJobControls();
    }

    private <T extends AbstractWidget> T addJobsWidget(T widget) {
        addRenderableWidget(widget);
        jobsTabWidgets.add(widget);
        return widget;
    }

    // A grid-sized copy of the Sounds tab's cell: an id field with a ♪ button that opens the picker.
    private void addJobSoundCell(int controlX, List<AbstractWidget> group, Component label,
                                 String currentValue, Consumer<String> onChanged) {
        EditBox field = new EditBox(this.font, controlX, JOB_SOUND_ROW_Y, COL_CONTROL_WIDTH - 22, 20, label);
        field.setMaxLength(200);
        field.setValue(currentValue);
        field.setResponder(onChanged);
        field.setTooltip(Tooltip.create(Component.translatable("gui.luna_npc.create_npc.job_sound.tooltip")));
        group.add(addJobsWidget(field));
        group.add(addJobsWidget(Button.builder(Component.literal("♪"), button ->
                        Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(this, field.getValue(),
                                onChanged)))
                .bounds(controlX + COL_CONTROL_WIDTH - 20, JOB_SOUND_ROW_Y, 20, 20).build()));
    }

    private Component jobTypeLabel() {
        return Component.translatable("gui.luna_npc.create_npc.job_type." + jobType.getSerializedName());
    }

    // Farming and patrol have no player-facing menu, so their interaction-block toggles are hidden; the
    // sub-type builder is shown for every rolled job. The second sliders are gated by their own toggle.
    private void updateJobControls() {
        if (jobTypeButton == null) {
            return;
        }
        boolean jobs = activeTab == EditorTab.JOBS;
        boolean interactable = jobType != NpcJobType.NONE;
        boolean blocksApply = jobs && interactable && jobType != NpcJobType.FARMING && jobType != NpcJobType.PATROL;
        jobTypeButton.visible = jobs;
        offerAllQuestsButton.visible = jobs && jobType == NpcJobType.QUESTS;
        blockAnyHitButton.visible = blocksApply;
        blockAttackerButton.visible = blocksApply;
        blockAllSecondsSlider.visible = blocksApply;
        blockAllSecondsSlider.active = blockOnAnyHit;
        blockAttackerSecondsSlider.visible = blocksApply;
        blockAttackerSecondsSlider.active = blockAttacker;
        for (AbstractWidget widget : tradeSoundWidgets) {
            widget.visible = jobs && jobType == NpcJobType.TRADING;
        }
        for (AbstractWidget widget : questSoundWidgets) {
            widget.visible = jobs && jobType == NpcJobType.QUESTS;
        }
        boolean dialogJob = jobs && jobType == NpcJobType.DIALOG;
        dialogNameField.visible = dialogJob;
        dialogSurviveNpcButton.visible = dialogJob;
        dialogSurvivePlayerButton.visible = dialogJob;
        dialogGraphButton.visible = dialogJob;
        bodyguardButton.visible = jobs && jobType == NpcJobType.BODYGUARD;
        boolean hasSubTypes = jobs && interactable && jobType != NpcJobType.DIALOG && jobType != NpcJobType.BODYGUARD;
        subTypeList.visible = hasSubTypes;
        addSubTypeButton.visible = hasSubTypes;
        removeSubTypeButton.visible = hasSubTypes;
        duplicateSubTypeButton.visible = hasSubTypes;
        editSubTypeButton.visible = hasSubTypes;
        refreshSubTypeButtons();
    }

    private void refreshSubTypeButtons() {
        int count = liveSubTypeCount();
        boolean roomForMore = count < NpcValidation.MAX_JOB_SUB_TYPES;
        boolean hasSelection = jobSubTypeSelected >= 0 && jobSubTypeSelected < count;
        addSubTypeButton.active = roomForMore && jobType != NpcJobType.NONE;
        removeSubTypeButton.active = hasSelection;
        duplicateSubTypeButton.active = hasSelection && roomForMore;
        editSubTypeButton.active = hasSelection;
    }

    private int liveSubTypeCount() {
        return switch (jobType) {
            case NONE, DIALOG, BODYGUARD -> 0;
            case PATROL -> patrolSubTypes.size();
            case TRADING -> tradeSubTypes.size();
            case QUESTS -> questSubTypes.size();
            case FARMING -> farmSubTypes.size();
        };
    }

    private int liveSubTypeWeight(int index) {
        return switch (jobType) {
            case NONE, DIALOG, BODYGUARD -> 0;
            case PATROL -> patrolSubTypes.get(index).weightPercent();
            case TRADING -> tradeSubTypes.get(index).weightPercent();
            case QUESTS -> questSubTypes.get(index).weightPercent();
            case FARMING -> farmSubTypes.get(index).weightPercent();
        };
    }

    private int liveWeightSum() {
        int sum = 0;
        for (int index = 0; index < liveSubTypeCount(); index++) {
            sum += liveSubTypeWeight(index);
        }
        return sum;
    }

    private void rebuildJobSubTypeList() {
        if (subTypeList == null) {
            return;
        }
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        int count = liveSubTypeCount();
        // Rows are shown highest-weight first; the entry id stays the real list index, so selecting,
        // editing, duplicating and removing still address the right sub-type after the reorder.
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            order.add(index);
        }
        order.sort((a, b) -> Integer.compare(liveSubTypeWeight(b), liveSubTypeWeight(a)));
        for (int index : order) {
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(String.valueOf(index),
                    subTypeRowLabel(index), value -> {
                        jobSubTypeSelected = Integer.parseInt(value);
                        refreshSubTypeButtons();
                    });
            entries.add(entry);
            if (index == jobSubTypeSelected) {
                selected = entry;
            }
        }
        if (selected == null) {
            jobSubTypeSelected = -1;
        }
        subTypeList.fill(entries, selected);
        refreshSubTypeButtons();
    }

    private Component subTypeRowLabel(int index) {
        Component summary = switch (jobType) {
            case TRADING -> Component.translatable("gui.luna_npc.create_npc.summary_trades",
                    tradeSubTypes.get(index).trades().size());
            case QUESTS -> Component.translatable("gui.luna_npc.create_npc.summary_quests",
                    questSubTypes.get(index).quests().size());
            case FARMING -> Component.translatable("gui.luna_npc.create_npc.summary_farm",
                    farmSubTypes.get(index).radius(), farmSubTypes.get(index).chestRadius());
            case PATROL -> Component.translatable("gui.luna_npc.create_npc.summary_patrol",
                    net.lunacore.luna_npc.client.ClientRoutes.nameOf(patrolSubTypes.get(index).routeId()),
                    Component.translatable("gui.luna_npc.edit_patrol.mode."
                            + patrolSubTypes.get(index).mode().getSerializedName()));
            case NONE, DIALOG, BODYGUARD -> Component.empty();
        };
        return Component.literal(liveSubTypeWeight(index) + "%  ")
                .withStyle(style -> style.withColor(SECTION_COLOR & 0xFFFFFF))
                .append(summary);
    }

    private void addSubType() {
        if (jobType == NpcJobType.NONE || liveSubTypeCount() >= NpcValidation.MAX_JOB_SUB_TYPES) {
            return;
        }
        int defaultWeight = Math.clamp(NpcValidation.JOB_WEIGHT_TOTAL_PERCENT - liveWeightSum(), 0,
                NpcValidation.JOB_WEIGHT_TOTAL_PERCENT);
        switch (jobType) {
            case TRADING -> Minecraft.getInstance().setScreenAndShow(new EditTradeSubTypeScreen(this,
                    new NpcTradeSubType(defaultWeight, List.of()), subType -> {
                        tradeSubTypes.add(subType);
                        afterSubTypeChange(tradeSubTypes.size() - 1);
                    }));
            case QUESTS -> Minecraft.getInstance().setScreenAndShow(new EditQuestSubTypeScreen(this,
                    new NpcQuestSubType(defaultWeight, List.of()), subType -> {
                        questSubTypes.add(subType);
                        afterSubTypeChange(questSubTypes.size() - 1);
                    }));
            case FARMING -> Minecraft.getInstance().setScreenAndShow(new EditFarmSubTypeScreen(this,
                    new NpcFarmSubType(defaultWeight, 16, 8, 32, 15, List.of()), subType -> {
                        farmSubTypes.add(subType);
                        afterSubTypeChange(farmSubTypes.size() - 1);
                    }));
            case PATROL -> Minecraft.getInstance().setScreenAndShow(new EditPatrolSubTypeScreen(this,
                    new net.lunacore.luna_npc.api.NpcPatrolSubType(defaultWeight, "",
                            net.lunacore.luna_npc.api.NpcPatrolMode.LOOP, net.lunacore.luna_npc.api.NpcPatrolSpeed.ROUTE),
                    subType -> {
                        patrolSubTypes.add(subType);
                        afterSubTypeChange(patrolSubTypes.size() - 1);
                    }));
            case NONE, DIALOG, BODYGUARD -> { }
        }
    }

    private void editSubType() {
        int index = jobSubTypeSelected;
        if (index < 0 || index >= liveSubTypeCount()) {
            return;
        }
        switch (jobType) {
            case TRADING -> Minecraft.getInstance().setScreenAndShow(new EditTradeSubTypeScreen(this,
                    tradeSubTypes.get(index), subType -> {
                        tradeSubTypes.set(index, subType);
                        afterSubTypeChange(index);
                    }));
            case QUESTS -> Minecraft.getInstance().setScreenAndShow(new EditQuestSubTypeScreen(this,
                    questSubTypes.get(index), subType -> {
                        questSubTypes.set(index, subType);
                        afterSubTypeChange(index);
                    }));
            case FARMING -> Minecraft.getInstance().setScreenAndShow(new EditFarmSubTypeScreen(this,
                    farmSubTypes.get(index), subType -> {
                        farmSubTypes.set(index, subType);
                        afterSubTypeChange(index);
                    }));
            case PATROL -> Minecraft.getInstance().setScreenAndShow(new EditPatrolSubTypeScreen(this,
                    patrolSubTypes.get(index), subType -> {
                        patrolSubTypes.set(index, subType);
                        afterSubTypeChange(index);
                    }));
            case NONE, DIALOG, BODYGUARD -> { }
        }
    }

    private void duplicateSubType() {
        int index = jobSubTypeSelected;
        if (index < 0 || index >= liveSubTypeCount()
                || liveSubTypeCount() >= NpcValidation.MAX_JOB_SUB_TYPES) {
            return;
        }
        switch (jobType) {
            case TRADING -> tradeSubTypes.add(index + 1, tradeSubTypes.get(index));
            case QUESTS -> questSubTypes.add(index + 1, questSubTypes.get(index));
            case FARMING -> farmSubTypes.add(index + 1, farmSubTypes.get(index));
            case PATROL -> patrolSubTypes.add(index + 1, patrolSubTypes.get(index));
            case NONE, DIALOG, BODYGUARD -> { }
        }
        afterSubTypeChange(index + 1);
    }

    private void removeSubType() {
        int index = jobSubTypeSelected;
        if (index < 0 || index >= liveSubTypeCount()) {
            return;
        }
        switch (jobType) {
            case TRADING -> tradeSubTypes.remove(index);
            case QUESTS -> questSubTypes.remove(index);
            case FARMING -> farmSubTypes.remove(index);
            case PATROL -> patrolSubTypes.remove(index);
            case NONE, DIALOG, BODYGUARD -> { }
        }
        jobSubTypeSelected = -1;
        rebuildJobSettings();
        rebuildJobSubTypeList();
    }

    private void afterSubTypeChange(int newIndex) {
        jobSubTypeSelected = newIndex;
        rebuildJobSettings();
        rebuildJobSubTypeList();
    }

    private void selectSpawnMethod(SpawnMethod method) {
        spawnMethod = method;
        spawnSelectedId = "";
        rebuildSpawnList();
        updateSpawnControls();
    }

    private void rebuildSpawnList() {
        if (spawnList == null) {
            return;
        }
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        switch (spawnMethod) {
            case BIOMES -> {
                for (SpawnOption option : biomeOptions) {
                    if (!FuzzySearch.matches(spawnQuery, option.search())) {
                        continue;
                    }
                    SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(option.id(), option.label(),
                            this::onSpawnEntrySelected, null, () -> effectiveBiomeWeight(option.id()) > 0);
                    entries.add(entry);
                    if (option.id().equals(spawnSelectedId)) {
                        selected = entry;
                    }
                }
            }
            case STRUCTURES -> {
                for (SpawnOption option : structureOptions) {
                    if (!FuzzySearch.matches(spawnQuery, option.search())) {
                        continue;
                    }
                    SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(option.id(), option.label(),
                            this::onSpawnEntrySelected, null, () -> structureWeights.getOrDefault(option.id(), 0) > 0);
                    entries.add(entry);
                    if (option.id().equals(spawnSelectedId)) {
                        selected = entry;
                    }
                }
            }
            case ZONES -> {
                for (NpcZone cube : ClientZones.all()) {
                    if (!FuzzySearch.matches(spawnQuery, cube.id(), cube.name())) {
                        continue;
                    }
                    SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(cube.id(),
                            Component.literal(cube.name()), this::onSpawnEntrySelected, null, () -> {
                        NpcZoneSpawn spawn = zoneSpawn(cube.id());
                        return spawn != null && spawn.chance() > 0;
                    });
                    entries.add(entry);
                    if (cube.id().equals(spawnSelectedId)) {
                        selected = entry;
                    }
                }
            }
        }
        spawnList.fill(entries, selected);
        if (selected == null) {
            spawnSelectedId = "";
        }
        refreshSpawnDetail();
    }

    private void onSpawnEntrySelected(String id) {
        spawnSelectedId = id;
        refreshSpawnDetail();
    }

    private int effectiveBiomeWeight(String biomeId) {
        return useGlobalBiome ? globalBiomeWeight : biomeWeights.getOrDefault(biomeId, 0);
    }

    private NpcZoneSpawn zoneSpawn(String zoneId) {
        for (NpcZoneSpawn cube : zoneSpawns) {
            if (cube.zoneId().equals(zoneId)) {
                return cube;
            }
        }
        return null;
    }

    private int currentItemWeight() {
        return switch (spawnMethod) {
            case BIOMES -> biomeWeights.getOrDefault(spawnSelectedId, 0);
            case STRUCTURES -> structureWeights.getOrDefault(spawnSelectedId, 0);
            case ZONES -> {
                NpcZoneSpawn zone = zoneSpawn(spawnSelectedId);
                yield zone == null ? 0 : zone.chance();
            }
        };
    }

    private int currentZoneMax() {
        NpcZoneSpawn cube = zoneSpawn(spawnSelectedId);
        return cube == null ? 0 : cube.maxCount();
    }

    private void applyItemWeight(int weight) {
        if (spawnSelectedId.isEmpty()) {
            return;
        }
        switch (spawnMethod) {
            case BIOMES -> biomeWeights.put(spawnSelectedId, weight);
            case STRUCTURES -> structureWeights.put(spawnSelectedId, weight);
            case ZONES -> updateZoneSpawn(spawnSelectedId, weight, currentZoneMax());
        }
    }

    private void updateZoneSpawn(String zoneId, int weight, int maxCount) {
        if (zoneId.isEmpty()) {
            return;
        }
        for (int index = 0; index < zoneSpawns.size(); index++) {
            if (zoneSpawns.get(index).zoneId().equals(zoneId)) {
                zoneSpawns.set(index, new NpcZoneSpawn(zoneId, weight, maxCount));
                return;
            }
        }
        zoneSpawns.add(new NpcZoneSpawn(zoneId, weight, maxCount));
    }

    private void updateSpawnMethodButtons() {
        spawnBiomesButton.active = spawnMethod != SpawnMethod.BIOMES;
        spawnStructuresButton.active = spawnMethod != SpawnMethod.STRUCTURES;
        spawnZonesButton.active = spawnMethod != SpawnMethod.ZONES;
    }

    // Only the respawn band shows when natural spawning is off; turning it on reveals the light band,
    // the nearby cap, the method selector and the list + weight detail.
    private void updateSpawnControls() {
        if (respawnButton == null) {
            return;
        }
        boolean spawn = activeTab == EditorTab.SPAWN;
        boolean natural = spawn && naturalSpawnEnabled;
        respawnButton.visible = spawn;
        respawnAtDeathButton.visible = spawn && respawnEnabled;
        respawnTimeSlider.visible = spawn && respawnEnabled;
        naturalSpawnButton.visible = spawn;
        maxNearbyField.visible = natural;
        spawnLightSlider.visible = natural;
        spawnBiomesButton.visible = natural;
        spawnStructuresButton.visible = natural;
        spawnZonesButton.visible = natural;
        // Movement-type placement block: ground gets the surface/cave button, air its two height ranges,
        // water its depth range — only the current type's control shows, in the same detail-column slot.
        surfaceModeButton.visible = natural && "ground".equals(movementType);
        // Global mode hides the biome list + search; the density slider stays put in the detail column.
        boolean listMode = natural && !(spawnMethod == SpawnMethod.BIOMES && useGlobalBiome);
        spawnSearchField.visible = listMode;
        spawnList.visible = listMode;
        naturalPersistentBox.visible = natural;
        boolean air = natural && "air".equals(movementType);
        airHeightSlider.visible = air;
        airGroundSlider.visible = air;
        waterDepthSpawnSlider.visible = natural && "water".equals(movementType);
        updateSpawnMethodButtons();
        refreshSpawnDetail();
    }

    private void refreshSpawnDetail() {
        boolean natural = activeTab == EditorTab.SPAWN && naturalSpawnEnabled;
        boolean biomes = spawnMethod == SpawnMethod.BIOMES;
        boolean cubes = spawnMethod == SpawnMethod.ZONES;
        boolean hasSelection = !spawnSelectedId.isEmpty();
        globalBiomeToggle.visible = natural && biomes;
        globalWeightSlider.visible = natural && biomes && useGlobalBiome;
        densitySlider.visible = natural && hasSelection && !(biomes && useGlobalBiome);
        zoneMaxField.visible = natural && cubes && hasSelection;
        leaveAreaBox.visible = natural && cubes;
        leaveAreaCombatBox.visible = natural && cubes;
        populatingSpawnFields = true;
        if (globalWeightSlider.visible) {
            globalWeightSlider.setValue(globalBiomeWeight);
        }
        if (densitySlider.visible) {
            densitySlider.setValue(currentItemWeight());
        }
        if (zoneMaxField.visible) {
            zoneMaxField.setValue(String.valueOf(currentZoneMax()));
        }
        populatingSpawnFields = false;
    }

    private void setActiveTab(EditorTab tab) {
        activeTab = tab;
        boolean general = tab == EditorTab.GENERAL;
        boolean traits = tab == EditorTab.TRAITS;
        boolean movement = tab == EditorTab.MOVEMENT;
        boolean navigation = tab == EditorTab.NAVIGATION;
        boolean faction = tab == EditorTab.FACTION;
        boolean sounds = tab == EditorTab.SOUNDS;
        boolean inventory = tab == EditorTab.INVENTORY;
        boolean spawn = tab == EditorTab.SPAWN;
        for (EditorTab entry : EditorTab.values()) {
            tabButtons[entry.ordinal()].active = entry != tab;
        }
        ensureActiveTabVisible();
        layoutTabBar();
        for (AbstractWidget widget : traitsTabWidgets) {
            widget.visible = traits;
        }
        updateEmergencySeekVisibility();
        for (AbstractWidget widget : movementTabWidgets) {
            widget.visible = movement;
        }
        for (AbstractWidget widget : navigationTabWidgets) {
            widget.visible = navigation;
        }
        combatNormalButton.visible = tab == EditorTab.COMBAT;
        combatAdvancedButton.visible = tab == EditorTab.COMBAT;
        combatRulesButton.visible = tab == EditorTab.COMBAT && combatAdvancedView;
        updateCombatScroll();
        updateMovementTypeControls();
        nameField.visible = general;
        healthField.visible = general;
        showTagButton.visible = general;
        tagColorButton.visible = general;
        categoryField.visible = general;
        bossBarButton.visible = general;
        bossBarNameField.visible = general;
        editAccessButton.visible = general;
        placedPersistentBox.visible = general;
        updateBossBarControls();
        changeModelButton.visible = general;
        for (AbstractWidget widget : soundTabWidgets) {
            widget.visible = sounds;
        }
        for (AbstractWidget widget : factionTabWidgets) {
            widget.visible = faction;
        }
        if (faction) {
            // The alliance set and its friendly-fire rule may have changed since the screen opened.
            rebuildFactionList();
            refreshFriendlyFireControl();
        }
        for (AbstractWidget widget : inventoryTabWidgets) {
            widget.visible = inventory;
        }
        for (AbstractWidget widget : spawnTabWidgets) {
            widget.visible = spawn;
        }
        if (spawn) {
            rebuildSpawnList();
        }
        for (AbstractWidget widget : jobsTabWidgets) {
            widget.visible = tab == EditorTab.JOBS;
        }
        if (tab == EditorTab.JOBS) {
            rebuildJobSubTypeList();
        }
        updateJobControls();
        updateSpawnControls();
        updateChanceSlider();
    }

    private void setEquipmentId(int slotIndex, String itemId) {
        Map<String, List<NpcItemEnchant>> enchants = new HashMap<>(equipmentSettings.enchantments());
        // A new item may not accept the old enchantments, so clear them when the item changes.
        if (!equipmentIdFor(slotIndex).equals(itemId)) {
            enchants.remove(slotKey(slotIndex));
        }
        equipmentSettings = new NpcEquipmentSettings(
                slotIndex == 0 ? itemId : equipmentSettings.helmetId(),
                slotIndex == 1 ? itemId : equipmentSettings.chestplateId(),
                slotIndex == 2 ? itemId : equipmentSettings.leggingsId(),
                slotIndex == 3 ? itemId : equipmentSettings.bootsId(),
                slotIndex == 4 ? itemId : equipmentSettings.mainHandId(),
                slotIndex == 5 ? itemId : equipmentSettings.rangedWeaponId(),
                slotIndex == 6 ? itemId : equipmentSettings.offHandId(),
                equipmentSettings.gearAffectsStats(), enchants);
        refreshInventoryWidgets();
    }

    // Slot index 0..6 mapped to the equipment-settings slot key used by the enchantment map.
    private static String slotKey(int slotIndex) {
        return switch (slotIndex) {
            case 0 -> NpcEquipmentSettings.SLOT_HELMET;
            case 1 -> NpcEquipmentSettings.SLOT_CHESTPLATE;
            case 2 -> NpcEquipmentSettings.SLOT_LEGGINGS;
            case 3 -> NpcEquipmentSettings.SLOT_BOOTS;
            case 4 -> NpcEquipmentSettings.SLOT_MAIN_HAND;
            case 5 -> NpcEquipmentSettings.SLOT_RANGED_WEAPON;
            default -> NpcEquipmentSettings.SLOT_OFF_HAND;
        };
    }

    // The item id currently held in a given equipment slot (0..6).
    private String equipmentIdFor(int slotIndex) {
        return switch (slotIndex) {
            case 0 -> equipmentSettings.helmetId();
            case 1 -> equipmentSettings.chestplateId();
            case 2 -> equipmentSettings.leggingsId();
            case 3 -> equipmentSettings.bootsId();
            case 4 -> equipmentSettings.mainHandId();
            case 5 -> equipmentSettings.rangedWeaponId();
            default -> equipmentSettings.offHandId();
        };
    }

    // Rebuilds equipmentSettings with a fresh enchantment map for one slot; an empty list drops the
    // slot's key, keeping every other equipment field untouched.
    private void setSlotEnchants(String slotKey, List<NpcItemEnchant> enchants) {
        Map<String, List<NpcItemEnchant>> map = new HashMap<>(equipmentSettings.enchantments());
        if (enchants == null || enchants.isEmpty()) {
            map.remove(slotKey);
        } else {
            map.put(slotKey, List.copyOf(enchants));
        }
        equipmentSettings = new NpcEquipmentSettings(
                equipmentSettings.helmetId(), equipmentSettings.chestplateId(),
                equipmentSettings.leggingsId(), equipmentSettings.bootsId(),
                equipmentSettings.mainHandId(), equipmentSettings.rangedWeaponId(),
                equipmentSettings.offHandId(), equipmentSettings.gearAffectsStats(), map);
        refreshInventoryWidgets();
    }

    private void openItemPicker(int slotIndex, EquipmentSlot filter) {
        Minecraft.getInstance().setScreenAndShow(new ItemPickerScreen(this, filter,
                itemId -> setEquipmentId(slotIndex, itemId)));
    }

    private void openEnchantScreen(int slotIndex) {
        String key = slotKey(slotIndex);
        Minecraft.getInstance().setScreenAndShow(new EnchantItemScreen(this, equipmentIdFor(slotIndex),
                equipmentSettings.enchantsFor(key), updated -> setSlotEnchants(key, updated)));
    }

    private void refreshInventoryWidgets() {
        String[] equipmentIds = {equipmentSettings.helmetId(), equipmentSettings.chestplateId(),
                equipmentSettings.leggingsId(), equipmentSettings.bootsId(),
                equipmentSettings.mainHandId(), equipmentSettings.rangedWeaponId(),
                equipmentSettings.offHandId()};
        for (int index = 0; index < equipmentSlots.length; index++) {
            ItemStack stack = NpcEquipment.resolve(equipmentIds[index]);
            boolean canWearEquipment = NpcModelCatalog.showsGear(modelSettings.modelType(), EQUIPMENT_GEAR[index]);
            equipmentSlots[index].setStack(stack);
            equipmentSlots[index].active = canWearEquipment;
            equipmentSlots[index].setTooltip(canWearEquipment
                    ? (stack.isEmpty() ? Tooltip.create(equipmentSlotTooltip(index))
                            : Tooltip.create(stack.getHoverName().copy().append("\n")
                                    .append(equipmentSlotTooltip(index).copy().withStyle(ChatFormatting.GRAY))))
                    : Tooltip.create(NO_EQUIPMENT_TOOLTIP));
        }
        for (int index = 0; index < dropSlots.length; index++) {
            NpcDropSettings.NpcItemDrop drop = itemDrops.get(index);
            ItemStack stack = drop == null ? ItemStack.EMPTY : NpcEquipment.resolve(drop.itemId());
            dropSlots[index].setStack(stack);
            dropSlots[index].setOverlayText(drop == null ? null : shortChance(drop.chancePercent()));
            dropSlots[index].setSelected(index == selectedDropSlot);
            dropSlots[index].setTooltip(drop == null ? null
                    : Tooltip.create(stack.getHoverName().copy()
                            .append(Component.literal(" - " + formatChance(drop.chancePercent()))
                                    .withStyle(ChatFormatting.GRAY))));
        }
        if (previewEntity != null) {
            previewEntity.applyEquipment(equipmentSettings);
        }
        updateChanceSlider();
    }

    private void updateChanceSlider() {
        NpcDropSettings.NpcItemDrop selected = itemDrops.get(selectedDropSlot);
        boolean show = activeTab == EditorTab.INVENTORY && selected != null;
        chanceSlider.visible = show;
        if (show) {
            chanceSlider.setChance(selected.chancePercent());
        }
    }

    // Slots are 18px wide, so the in-slot marker is the rounded percentage without the sign; the
    // exact value lives in the tooltip and the chance slider.
    private static String shortChance(float chancePercent) {
        return String.valueOf(Math.round(chancePercent));
    }

    private static String formatChance(float chancePercent) {
        String text = String.format(Locale.ROOT, "%.2f", chancePercent);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "%";
    }

    private void onNameChanged(String value) {
        String sanitized = sanitizeName(value);
        if (!sanitized.equals(value)) {
            nameField.setValue(sanitized);
            return;
        }
        npcName = sanitized;
        updateCreateButtonState();
    }

    private void onHealthChanged(String value) {
        String sanitized = value.replaceAll("[^0-9]", "");
        if (!sanitized.equals(value)) {
            healthField.setValue(sanitized);
            return;
        }
        healthText = sanitized;
        updateCreateButtonState();
    }

    private static String sanitizeName(String value) {
        StringBuilder allowed = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            if (NpcValidation.isAllowedNameCharacter(character)) {
                allowed.append(character);
            }
        }
        return allowed.toString();
    }

    private static String colorName(ChatFormatting color) {
        return color.name().toLowerCase(Locale.ROOT);
    }

    private int tagColorIndex() {
        for (int index = 0; index < TAG_COLORS.size(); index++) {
            if (colorName(TAG_COLORS.get(index)).equals(tagColorId)) {
                return index;
            }
        }
        return TAG_COLORS.size() - 1;
    }

    private Component runLinkLabel() {
        return Component.literal(runSpeedEnabled ? "\u2260" : "=");
    }

    private void updateRunSpeedControls() {
        runLinkButton.setMessage(runLinkLabel());
        runLinkButton.setTooltip(Tooltip.create(runSpeedEnabled ? RUN_CUSTOM_TOOLTIP : RUN_LINKED_TOOLTIP));
        runSpeedSlider.active = runSpeedEnabled;
    }

    // A single swatch shows the current color; clicking it expands the full color strip, and
    // picking one collapses it again.
    private void onBossBarSwatchClicked(String colorId) {
        if (!bossBarEnabled) {
            return;
        }
        if (bossBarColorExpanded) {
            bossBarColor = colorId;
            bossBarColorExpanded = false;
        } else {
            bossBarColorExpanded = true;
        }
        updateBossBarControls();
    }

    private void updateBossBarControls() {
        int controlX = rightPanelX() + COL1_CONTROL_X;
        boolean general = activeTab == EditorTab.GENERAL;
        for (int index = 0; index < bossBarSwatches.length; index++) {
            String colorId = NpcValidation.BOSS_BAR_COLORS.get(index);
            boolean isCurrent = colorId.equals(bossBarColor);
            ColorSwatchButton swatch = bossBarSwatches[index];
            swatch.active = bossBarEnabled;
            swatch.setSelected(bossBarEnabled && isCurrent);
            if (bossBarColorExpanded) {
                swatch.setX(controlX + index * 20);
                swatch.visible = general;
            } else {
                swatch.setX(controlX);
                swatch.visible = general && isCurrent;
            }
        }
        bossBarNameField.active = bossBarEnabled;
        bossBarNameField.setEditable(bossBarEnabled);
    }

    private Component tagColorLabel() {
        ChatFormatting color = TAG_COLORS.get(tagColorIndex());
        String prettyName = colorName(color).replace('_', ' ');
        return Component.literal(prettyName).withStyle(color);
    }

    private void updateCreateButtonState() {
        createButton.active = NpcValidation.isValidName(npcName) && parsedHealth() != -1
                && parsedAmbientInterval() != -1 && NpcValidation.isValidCategory(categoryText)
                && parsedDamage(meleeDamageText) >= 0.0F && parsedDamage(rangedDamageText) >= 0.0F
                && jobSettings.isValid();
    }

    private int parsedHealth() {
        try {
            int health = Integer.parseInt(healthText);
            return NpcValidation.isValidHealth(health) ? health : -1;
        } catch (NumberFormatException invalidNumber) {
            return -1;
        }
    }

    private int parsedAmbientInterval() {
        try {
            int seconds = Integer.parseInt(ambientIntervalText);
            return NpcValidation.isValidAmbientInterval(seconds) ? seconds : -1;
        } catch (NumberFormatException invalidNumber) {
            return -1;
        }
    }

    private void confirm() {
        int health = parsedHealth();
        int ambientInterval = parsedAmbientInterval();
        float meleeDamage = parsedDamage(meleeDamageText);
        float rangedDamage = parsedDamage(rangedDamageText);
        if (!NpcValidation.isValidName(npcName) || health == -1 || ambientInterval == -1
                || !NpcValidation.isValidCategory(categoryText)
                || meleeDamage < 0.0F || rangedDamage < 0.0F) {
            return;
        }
        // The ranged max range has no UI (the distance preference drives engagement); an edited
        // NPC keeps its stored value instead of losing it to the default.
        NpcAttackSettings storedRanged = editing == null
                ? NpcAttackSettings.defaultRanged() : editing.combat().ranged();
        // The Simple/Advanced master switch decides which combat brain runs; both configs are stored so
        // toggling never discards the other side's work.
        NpcCombatMode storedMode = combatAdvancedView ? NpcCombatMode.ADVANCED : NpcCombatMode.SIMPLE;
        NpcAdvancedCombat storedAdvanced = advancedCombat;
        NpcCombatSettings combat = new NpcCombatSettings(
                new NpcAttackSettings(meleeEnabled, meleeDamage, meleeRange, meleeIntervalSeconds,
                        List.copyOf(meleeEffects), new NpcImpactSettings(meleeImpactDestroy,
                                meleeImpactRadius, meleeImpactParticleId, meleeImpactParticleSeconds)),
                new NpcAttackSettings(rangedEnabled, rangedDamage, storedRanged.range(),
                        rangedIntervalSeconds, List.copyOf(rangedEffects),
                        new NpcImpactSettings(rangedImpactDestroy, rangedImpactRadius,
                                rangedImpactParticleId, rangedImpactParticleSeconds)),
                new NpcProjectileSettings(projectileItemId, projectileSpeed, homingEnabled,
                        curveEnabled, curveMaxDistance, dynamicAccuracy, fixedAccuracy,
                        List.copyOf(accuracyAnchors)),
                distancePreference, aggroRange, requireLos, parsedGiveUpSeconds(), factionFriendlyFire,
                difficultyScaling, rangeSwitch, storedMode, storedAdvanced);
        NpcSoundSettings sounds = new NpcSoundSettings(hurtSoundId, dieSoundId, ambientSoundId, aggroSoundId,
                combatCloseSoundId, combatFarSoundId, closeImpactSoundId, farImpactSoundId, ambientInterval);
        NpcDropSettings dropSettings = new NpcDropSettings(xpDropMin, xpDropMax, List.copyOf(itemDrops.values()));
        NpcWanderSettings wander = new NpcWanderSettings(wanderBehavior, wanderMinSeconds,
                wanderMaxSeconds, wanderRadius, Math.min(wanderMinGoalDistance, wanderRadius));
        NpcMovementSettings movement = new NpcMovementSettings(movementType, pathfindingType, walkSpeed,
                runSpeedEnabled, runSpeed, jumpHeight, List.copyOf(avoidedBlocks), hoverEnabled,
                hoverIntervalMs, hoverAmount, flightHeight,
                new NpcHeightPreference(flightHeightFixed, flightHeightRange),
                waterDepth, new NpcHeightPreference(waterDepthFixed, waterDepthRange), knockback, wander);
        NpcTraitsSettings traits = new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight,
                avoidWater, avoidLava, avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek,
                canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune,
                new NpcReactionSettings(reactionMode, parsedFleeSeconds(), fleeDistance), regenSettings,
                chunkLoader);
        NpcBossBarSettings bossBar = new NpcBossBarSettings(bossBarEnabled, bossBarColor, bossBarName);
        if (editing == null) {
            ClientPlayNetworking.send(new CreateNpcPayload(npcName, nameTagShown, health, sounds, modelSettings,
                    tagColorId, categoryText, equipmentSettings, dropSettings, movement, bossBar, traits,
                    combat, java.util.List.copyOf(allianceIds), spawnSettings, jobSettings, editAccess));
        } else {
            ClientPlayNetworking.send(new UpdateNpcPayload(editing.lunaId(), npcName, nameTagShown, health,
                    sounds, modelSettings, tagColorId, categoryText, equipmentSettings, dropSettings,
                    movement, bossBar, traits, combat, java.util.List.copyOf(allianceIds), spawnSettings,
                    jobSettings, editAccess));
        }
        onClose();
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, leftPanelX(), CONTENT_TOP, LEFT_PANEL_WIDTH, CONTENT_HEIGHT,
                activeTab == EditorTab.INVENTORY ? LABEL_EQUIPMENT : LABEL_PREVIEW);
        int rightX = rightPanelX();
        panel(graphics, rightX, CONTENT_TOP, RIGHT_PANEL_WIDTH, CONTENT_HEIGHT);
        // Every embedded list carries a thin inset border (the CLAUDE.md UI rules).
        if (activeTab == EditorTab.FACTION) {
            graphics.outline(rightX + 10, FACTION_LIST_TOP, RIGHT_PANEL_WIDTH - 20, FACTION_LIST_HEIGHT, PANEL_BORDER);
        }
        if (activeTab == EditorTab.SPAWN && naturalSpawnEnabled
                && !(spawnMethod == SpawnMethod.BIOMES && useGlobalBiome)) {
            graphics.outline(rightX + COL1_LABEL_X, SPAWN_LIST_TOP, SPAWN_LIST_WIDTH, SPAWN_LIST_HEIGHT, PANEL_BORDER);
        }
        if (activeTab == EditorTab.JOBS && jobType != NpcJobType.NONE && jobType != NpcJobType.DIALOG
                && jobType != NpcJobType.BODYGUARD) {
            graphics.outline(rightX + COL1_LABEL_X, JOB_SUBTYPE_LIST_TOP, RIGHT_PANEL_WIDTH - 20,
                    JOB_SUBTYPE_LIST_HEIGHT, PANEL_BORDER);
        }
    }


    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int leftX = leftPanelX();
        int rightX = rightPanelX();
        screenTitle(graphics);
        if (previewEntity != null) {
            int previewLeft = activeTab == EditorTab.INVENTORY ? leftX + 30 : leftX + 8;
            int previewBottom = activeTab == EditorTab.GENERAL
                    ? CONTENT_TOP + CONTENT_HEIGHT - 34 : CONTENT_TOP + CONTENT_HEIGHT - 8;
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics,
                    previewLeft, CONTENT_TOP + 16, leftX + LEFT_PANEL_WIDTH - 8, previewBottom,
                    32, 0.0625F, mouseX, mouseY, previewEntity);
        }

        switch (activeTab) {
            case GENERAL -> {
                int l1 = rightX + COL1_LABEL_X;
                int l2 = rightX + COL2_LABEL_X;
                columnLabel(graphics, LABEL_NAME, l1, colRowY(0), COL1_LABEL_MAX);
                columnLabel(graphics, LABEL_HEALTH, l2, colRowY(0), COL2_LABEL_MAX);
                columnLabel(graphics, LABEL_SHOW_TAG, l1, colRowY(1), COL1_LABEL_MAX);
                columnLabel(graphics, LABEL_TAG_COLOR, l2, colRowY(1), COL2_LABEL_MAX);
                columnLabel(graphics, LABEL_CATEGORY, l1, colRowY(2), COL1_LABEL_MAX);
                columnLabel(graphics, LABEL_BOSS_BAR, l2, colRowY(2), COL2_LABEL_MAX);
                columnLabel(graphics, LABEL_BOSS_BAR_COLOR, l1, colRowY(3), COL1_LABEL_MAX);
                columnLabel(graphics, LABEL_BOSS_BAR_NAME, l1, colRowY(4), COL1_LABEL_MAX);
                columnLabel(graphics, LABEL_EDIT_ACCESS, l2, colRowY(4), COL2_LABEL_MAX);
            }
            case MOVEMENT -> {
                int l1 = rightX + COL1_LABEL_X;
                int l2 = rightX + COL2_LABEL_X;
                columnLabel(graphics, LABEL_MOVEMENT_TYPE, l1, colRowY(0), COL1_LABEL_MAX);
                columnLabel(graphics, speedLabel(false), l1, colRowY(1), COL1_LABEL_MAX);
                columnLabel(graphics, speedLabel(true), l2, colRowY(1), COL2_LABEL_MAX);
                columnLabel(graphics, LABEL_KNOCKBACK, l1, colRowY(2), COL1_LABEL_MAX);
                // Jumping is ground-only and shares knockback's row in column two.
                if ("ground".equals(movementType)) {
                    columnLabel(graphics, LABEL_JUMP_HEIGHT, l2, colRowY(2), COL2_LABEL_MAX);
                }
                if (NpcWarnings.negativeKnockback(knockback)) {
                    // Track segment from the 0.0 point to the negative value turns red; the warning
                    // sits on the reserved row directly under the knockback slider.
                    int sliderLeft = rightX + COL1_CONTROL_X + 4;
                    int sliderSpan = COL_CONTROL_WIDTH - 8;
                    float range = NpcValidation.MAX_KNOCKBACK - NpcValidation.MIN_KNOCKBACK;
                    int zeroX = sliderLeft + Math.round((0.0F - NpcValidation.MIN_KNOCKBACK) / range * sliderSpan);
                    int valueX = sliderLeft + Math.round((knockback - NpcValidation.MIN_KNOCKBACK) / range * sliderSpan);
                    graphics.fill(valueX, colRowY(2) + 16, zeroX, colRowY(2) + 19, WARNING_COLOR);
                    graphics.text(this.font, KNOCKBACK_WARNING, l1, colRowY(3) + 2, WARNING_COLOR);
                }
                switch (movementType) {
                    case "ground" -> graphics.text(this.font, LABEL_AVOIDED_BLOCKS,
                            l1, colRowY(4) + 2, LABEL_COLOR);
                    case "water" -> {
                        columnLabel(graphics, LABEL_WATER_DEPTH, l1, colRowY(4), COL1_LABEL_MAX);
                        columnLabel(graphics, LABEL_TOLERANCE, l2, colRowY(4), COL2_LABEL_MAX);
                    }
                    case "air" -> {
                        columnLabel(graphics, LABEL_HOVER, l1, colRowY(4), COL1_LABEL_MAX);
                        columnLabel(graphics, LABEL_FLIGHT_HEIGHT, l2, colRowY(4), COL2_LABEL_MAX);
                        columnLabel(graphics, LABEL_HOVER_INTERVAL, l1, colRowY(5), COL1_LABEL_MAX);
                        columnLabel(graphics, LABEL_TOLERANCE, l2, colRowY(5), COL2_LABEL_MAX);
                        columnLabel(graphics, LABEL_HOVER_AMOUNT, l1, colRowY(6), COL1_LABEL_MAX);
                    }
                    default -> { }
                }
            }
            case TRAITS -> {
                int l1 = rightX + COL1_LABEL_X;
                int l2 = rightX + COL2_LABEL_X;
                columnLabel(graphics, LABEL_NPC_TYPE, l1, colRowY(0), COL1_LABEL_MAX);
                columnLabel(graphics, LABEL_REACTION, l1, colRowY(6), COL1_LABEL_MAX);
                if (NpcReactionSettings.FEARFUL.equals(reactionMode)) {
                    columnLabel(graphics, LABEL_FLEE_TIME, l1, colRowY(7), COL1_LABEL_MAX);
                    columnLabel(graphics, LABEL_FLEE_DISTANCE, l2, colRowY(7), COL2_LABEL_MAX);
                }
                if (NpcWarnings.chunkLoaderOnNaturalSpawn(chunkLoader, spawnSettings)) {
                    drawWarningStack(graphics, leftX, List.of(
                            Component.translatable("gui.luna_npc.create_npc.chunk_loader_natural_warning")));
                }
            }
            case NAVIGATION -> {
                int l1 = rightX + COL1_LABEL_X;
                int l2 = rightX + COL2_LABEL_X;
                sectionTitle(graphics, SECTION_PATHFINDING, l1, NAV_PATH_TITLE_Y);
                columnLabel(graphics, LABEL_PATHFINDING, l1, NAV_ALGORITHM_ROW_Y, COL1_LABEL_MAX);
                PathfindingType type = PathfindingType.byId(pathfindingType);
                if (type != null) {
                    graphics.text(this.font, LABEL_PATH_QUALITY, l1, NAV_QUALITY_LINE_Y, LABEL_COLOR);
                    PathfindingPickerScreen.stars(graphics, this.font, type.qualityStars(),
                            rightX + 106, NAV_QUALITY_LINE_Y);
                    graphics.text(this.font, LABEL_PATH_COST, l1, NAV_COST_LINE_Y, LABEL_COLOR);
                    PathfindingPickerScreen.stars(graphics, this.font, type.costStars(),
                            rightX + 106, NAV_COST_LINE_Y);
                }
                sectionTitle(graphics, SECTION_WANDERING, l1, NAV_WANDER_TITLE_Y);
                columnLabel(graphics, LABEL_WANDER_BEHAVIOR, l1, NAV_BEHAVIOR_ROW_Y, COL1_LABEL_MAX);
                columnLabel(graphics, LABEL_WANDER_RADIUS, l2, NAV_RADIUS_ROW_Y, COL2_LABEL_MAX);
                columnLabel(graphics, LABEL_MIN_GOAL_DISTANCE, l1, NAV_MIN_GOAL_ROW_Y, COL1_LABEL_MAX);
                graphics.text(this.font, LABEL_WANDER_INTERVAL, l1, NAV_INTERVAL_LABEL_Y, LABEL_COLOR);
                Component intervalValue = Component.literal(wanderMinSeconds + "s - " + wanderMaxSeconds + "s");
                graphics.text(this.font, intervalValue,
                        rightX + RIGHT_PANEL_WIDTH - 10 - this.font.width(intervalValue),
                        NAV_INTERVAL_LABEL_Y, LABEL_COLOR);
            }
            case COMBAT -> {
                if (combatAdvancedView) {
                    Component hint = Component.translatable("gui.luna_npc.create_npc.combat_advanced_hint");
                    graphics.centeredText(this.font, hint, rightX + RIGHT_PANEL_WIDTH / 2,
                            CONTENT_TOP + 4 + COMBAT_MODE_BAND + 6, LABEL_COLOR);
                    break;
                }
                int l1 = rightX + COL1_LABEL_X;
                int l2 = rightX + COL2_LABEL_X;
                combatSection(graphics, SECTION_TARGETING, combatY.get("targeting_title"), rightX);
                combatColumnLabel(graphics, LABEL_AGGRO_RANGE, l1, combatY.get("aggro"));
                combatColumnLabel(graphics, LABEL_REQUIRE_LOS, l2, combatY.get("require_los"));
                combatColumnLabel(graphics, LABEL_GIVE_UP, l1, combatY.get("give_up"));

                combatColumnLabel(graphics, LABEL_DIFFICULTY_SCALING, l1, combatY.get("difficulty_scaling"));

                combatSection(graphics, SECTION_CLOSE_RANGE, combatY.get("close_title"), rightX);
                combatColumnLabel(graphics, LABEL_ATTACK_ENABLED, l1, combatY.get("close_enabled"));
                combatColumnLabel(graphics, LABEL_ATTACK_DAMAGE, l2, combatY.get("close_damage"));
                combatColumnLabel(graphics, LABEL_ATTACK_RANGE, l1, combatY.get("close_range"));
                combatColumnLabel(graphics, LABEL_ATTACK_SPEED, l2, combatY.get("close_speed"));
                combatColumnLabel(graphics, LABEL_ATTACK_EFFECTS, l1, combatY.get("close_effects"));

                combatSection(graphics, SECTION_CLOSE_IMPACT, combatY.get("close_impact_title"), rightX);
                combatOptionalLabel(graphics, LABEL_IMPACT_RADIUS, l2, "close_radius");

                combatSection(graphics, SECTION_CLOSE_VISUALS, combatY.get("close_visuals_title"), rightX);
                combatColumnLabel(graphics, LABEL_IMPACT_PARTICLE, l1, combatY.get("close_particle"));
                combatOptionalLabel(graphics, LABEL_IMPACT_DURATION, l2, "close_duration");

                combatSection(graphics, SECTION_LONG_RANGE, combatY.get("long_title"), rightX);
                combatColumnLabel(graphics, LABEL_ATTACK_ENABLED, l1, combatY.get("long_enabled"));
                combatColumnLabel(graphics, LABEL_ATTACK_DAMAGE, l2, combatY.get("long_damage"));
                combatColumnLabel(graphics, LABEL_DISTANCE_PREFERENCE, l1, combatY.get("long_distance"));
                combatColumnLabel(graphics, LABEL_ATTACK_SPEED, l2, combatY.get("long_speed"));
                combatColumnLabel(graphics, LABEL_ATTACK_EFFECTS, l1, combatY.get("long_effects"));

                combatSection(graphics, SECTION_LONG_IMPACT, combatY.get("long_impact_title"), rightX);
                combatOptionalLabel(graphics, LABEL_IMPACT_RADIUS, l2, "long_radius");

                combatSection(graphics, SECTION_LONG_VISUALS, combatY.get("long_visuals_title"), rightX);
                combatColumnLabel(graphics, LABEL_IMPACT_PARTICLE, l1, combatY.get("long_particle"));
                combatOptionalLabel(graphics, LABEL_IMPACT_DURATION, l2, "long_duration");

                combatSection(graphics, SECTION_PROJECTILE, combatY.get("projectile_title"), rightX);
                combatColumnLabel(graphics, LABEL_PROJECTILE, l1, combatY.get("projectile_label"));
                combatColumnLabel(graphics, LABEL_FLY_SPEED, l2, combatY.get("projectile_speed"));
                if (combatY.containsKey("homing_warning")) {
                    combatText(graphics, HOMING_WARNING, l1, combatY.get("homing_warning"), WARNING_COLOR);
                }
                if (combatY.containsKey("draw_held_warning")) {
                    combatText(graphics, DRAW_HELD_WARNING, l1, combatY.get("draw_held_warning"), SECTION_COLOR);
                }
                combatOptionalLabel(graphics, LABEL_CURVE_DISTANCE, l1, "curve_distance");

                combatSection(graphics, SECTION_ACCURACY, combatY.get("accuracy_title"), rightX);
                combatOptionalLabel(graphics, LABEL_FIXED_ACCURACY, l1, "fixed_accuracy");
                if (dynamicAccuracy) {
                    NpcAccuracyAnchor selectedAnchor = accuracyCurve.selectedAnchor();
                    combatText(graphics, selectedAnchor == null ? ANCHOR_HINT
                                    : Component.translatable("gui.luna_npc.create_npc.anchor_card",
                                            selectedAnchor.distanceBlocks(), selectedAnchor.percent()),
                            l1, combatY.get("accuracy_card") + 5, LABEL_COLOR);
                }
                combatSection(graphics, Component.translatable("gui.luna_npc.create_npc.range_switch_section"),
                        combatY.get("range_switch_title"), rightX);
                extractCombatScrollbar(graphics, rightX);
            }
            case FACTION -> {
                sectionTitle(graphics, LABEL_FACTION, rightX + 10, CONTENT_TOP + 8);
                if (friendlyFireLocked) {
                    graphics.text(this.font, FRIENDLY_FIRE_LOCKED, rightX + 10, FACTION_HINT_Y, LABEL_COLOR);
                }
                net.lunacore.luna_npc.api.NpcAlliance selected =
                        net.lunacore.luna_npc.client.ClientAlliances.byId(primaryAlliance());
                // Long enemy/protect lists wrap and are clipped to the panel bottom so they never
                // spill past the right edge or below the box.
                int maxWidth = RIGHT_PANEL_WIDTH - 20;
                graphics.enableScissor(rightX + 10, FACTION_READOUT_TOP,
                        rightX + RIGHT_PANEL_WIDTH - 10, CONTENT_TOP + CONTENT_HEIGHT - 2);
                if (selected == null) {
                    graphics.textWithWordWrap(this.font, FACTION_HINT, rightX + 10, FACTION_READOUT_TOP,
                            maxWidth, LABEL_COLOR);
                } else {
                    Component enemies = LABEL_ENEMIES.copy().append(": ")
                            .append(allianceNames(selected.enemies()));
                    Component mates = LABEL_MATES.copy().append(": ").append(allianceNames(selected.mates()));
                    graphics.textWithWordWrap(this.font, enemies, rightX + 10, FACTION_READOUT_TOP,
                            maxWidth, LABEL_COLOR);
                    int matesY = FACTION_READOUT_TOP + this.font.wordWrapHeight(enemies, maxWidth) + 2;
                    graphics.textWithWordWrap(this.font, mates, rightX + 10, matesY, maxWidth, LABEL_COLOR);
                }
                graphics.disableScissor();
            }
            case SOUNDS -> {
                int l1 = rightX + COL1_LABEL_X;
                int sc2 = rightX + SOUND_C2_LABEL_X;
                columnLabel(graphics, LABEL_HURT_SOUND, l1, soundRowY(0), SOUND_C1_LABEL_MAX);
                columnLabel(graphics, LABEL_DIE_SOUND, sc2, soundRowY(0), SOUND_C2_LABEL_MAX);
                columnLabel(graphics, LABEL_AMBIENT_SOUND, l1, soundRowY(1), SOUND_C1_LABEL_MAX);
                columnLabel(graphics, LABEL_AGGRO_SOUND, sc2, soundRowY(1), SOUND_C2_LABEL_MAX);
                sectionTitle(graphics, SECTION_COMBAT_SOUNDS, l1, soundCombatTitleY());
                columnLabel(graphics, LABEL_COMBAT_CLOSE_SOUND, l1, soundCombatRowY(0), SOUND_C1_LABEL_MAX);
                columnLabel(graphics, LABEL_COMBAT_FAR_SOUND, sc2, soundCombatRowY(0), SOUND_C2_LABEL_MAX);
                columnLabel(graphics, LABEL_CLOSE_IMPACT_SOUND, l1, soundCombatRowY(1), SOUND_C1_LABEL_MAX);
                columnLabel(graphics, LABEL_FAR_IMPACT_SOUND, sc2, soundCombatRowY(1), SOUND_C2_LABEL_MAX);
                columnLabel(graphics, LABEL_AMBIENT_INTERVAL, l1, soundIntervalY(), SOUND_C1_LABEL_MAX);
            }
            case INVENTORY -> {
                sectionTitle(graphics, LABEL_XP_DROP, rightX + 10, CONTENT_TOP + 8);
                graphics.text(this.font, Component.literal("min: " + xpDropMin),
                        rightX + 10, CONTENT_TOP + 19, LABEL_COLOR);
                Component maxLabel = Component.literal("max: " + xpDropMax);
                graphics.text(this.font, maxLabel,
                        rightX + RIGHT_PANEL_WIDTH - 10 - this.font.width(maxLabel), CONTENT_TOP + 19, LABEL_COLOR);
                sectionTitle(graphics, LABEL_ITEM_DROP, rightX + 10, CONTENT_TOP + 52);
                if (chanceSlider.visible) {
                    graphics.text(this.font, LABEL_DROP_CHANCE, rightX + 10,
                            CONTENT_TOP + 64 + DROP_GRID_ROWS * ItemSlotButton.SIZE + 11, LABEL_COLOR);
                }
                if (NpcWarnings.gearOverridesCombat(equipmentSettings.gearAffectsStats())) {
                    graphics.textWithWordWrap(this.font, GEAR_STATS_WARNING, leftX + 7,
                            CONTENT_TOP + 202, LEFT_PANEL_WIDTH - 14, SECTION_COLOR);
                }
            }
            case SPAWN -> {
                int l1 = rightX + COL1_LABEL_X;
                int l2 = rightX + COL2_LABEL_X;
                int detailX = rightX + SPAWN_DETAIL_X + 4;
                columnLabel(graphics, LABEL_RESPAWN, l1, SPAWN_RESPAWN_ROW_Y, COL1_LABEL_MAX);
                // Natural spawning + respawn is a runaway-population combo: flag it with a ⚠ next to
                // the Respawn toggle and spell it out under the preview (API users get a console warning).
                // Both warning bodies stack up from the panel's bottom edge, each offset by its own wrapped
                // height, so a long message can never spill past the box no matter how many lines it needs.
                int spawnWarnBottom = CONTENT_TOP + CONTENT_HEIGHT - 6;
                int spawnWarnWidth = LEFT_PANEL_WIDTH - 14;
                if (NpcWarnings.spawnLoop(spawnSettings)) {
                    graphics.text(this.font, Component.literal("⚠"),
                            l1 + this.font.width(LABEL_RESPAWN) + 3, SPAWN_RESPAWN_ROW_Y + 6, WARNING_COLOR);
                    spawnWarnBottom -= this.font.wordWrapHeight(SPAWN_LOOP_WARNING, spawnWarnWidth);
                    graphics.textWithWordWrap(this.font, SPAWN_LOOP_WARNING, leftX + 7,
                            spawnWarnBottom, spawnWarnWidth, WARNING_COLOR);
                    spawnWarnBottom -= 4;
                }
                // Persistent wild spawns never despawn: flag it by the Persistent checkbox and spell it out
                // above the spawn-loop warning when both apply.
                if (NpcWarnings.spawnPersistentPileup(spawnSettings)) {
                    graphics.text(this.font, Component.literal("⚠"),
                            naturalPersistentBox.getX() + naturalPersistentBox.getWidth() + 3,
                            SPAWN_PERSISTENT_ROW_Y + 1, WARNING_COLOR);
                    spawnWarnBottom -= this.font.wordWrapHeight(SPAWN_PERSISTENT_WARNING, spawnWarnWidth);
                    graphics.textWithWordWrap(this.font, SPAWN_PERSISTENT_WARNING, leftX + 7,
                            spawnWarnBottom, spawnWarnWidth, WARNING_COLOR);
                }
                if (respawnEnabled) {
                    columnLabel(graphics, LABEL_RESPAWN_AT_DEATH, l2, SPAWN_RESPAWN_ROW_Y, COL2_LABEL_MAX);
                    graphics.text(this.font, LABEL_RESPAWN_TIME, l1, SPAWN_RESPAWN_TIME_LABEL_Y, LABEL_COLOR);
                    Component respawnValue = Component.literal(respawnMinSeconds + "s - " + respawnMaxSeconds + "s");
                    graphics.text(this.font, respawnValue,
                            rightX + RIGHT_PANEL_WIDTH - 10 - this.font.width(respawnValue),
                            SPAWN_RESPAWN_TIME_LABEL_Y, LABEL_COLOR);
                }
                columnLabel(graphics, LABEL_NATURAL_SPAWN, l1, SPAWN_NATURAL_ROW_Y, COL1_LABEL_MAX);
                if (naturalSpawnEnabled) {
                    columnLabel(graphics, LABEL_MAX_NEARBY, l2, SPAWN_NATURAL_ROW_Y, COL2_LABEL_MAX);
                    graphics.text(this.font, LABEL_SPAWN_LIGHT, l1, SPAWN_LIGHT_LABEL_Y, LABEL_COLOR);
                    // The light range sits at the right end of the (full-width) light slider's label row.
                    Component lightValue = Component.literal(minSpawnLight + " - " + maxSpawnLight);
                    graphics.text(this.font, lightValue,
                            rightX + COL1_LABEL_X + SPAWN_LIGHT_SLIDER_WIDTH - this.font.width(lightValue),
                            SPAWN_LIGHT_LABEL_Y, LABEL_COLOR);
                    int detailWidth = SPAWN_DETAIL_WIDTH - 8;
                    if (globalBiomeToggle.visible) {
                        graphics.text(this.font, LABEL_SPAWN_GLOBAL, detailX, SPAWN_DETAIL_ROW1_LABEL_Y, LABEL_COLOR);
                    }
                    if (zoneMaxField.visible) {
                        graphics.text(this.font, LABEL_ZONE_MAX, detailX, SPAWN_DETAIL_ROW1_LABEL_Y, LABEL_COLOR);
                    }
                    if (surfaceModeButton.visible) {
                        graphics.text(this.font, LABEL_PLACEMENT, detailX, SPAWN_AIR_HEIGHT_LABEL_Y, LABEL_COLOR);
                    }
                    if (airHeightSlider.visible) {
                        graphics.text(this.font, LABEL_AIR_HEIGHT, detailX, SPAWN_AIR_HEIGHT_LABEL_Y, LABEL_COLOR);
                        Component airY = Component.literal(airSpawn.minWorldY() + " - " + airSpawn.maxWorldY());
                        graphics.text(this.font, airY, detailX + detailWidth - this.font.width(airY),
                                SPAWN_AIR_HEIGHT_LABEL_Y, LABEL_COLOR);
                        graphics.text(this.font, LABEL_AIR_ABOVE_GROUND, detailX, SPAWN_AIR_GROUND_LABEL_Y, LABEL_COLOR);
                        Component airGround = Component.literal(airSpawn.minAboveGround() + " - " + airSpawn.maxAboveGround());
                        graphics.text(this.font, airGround, detailX + detailWidth - this.font.width(airGround),
                                SPAWN_AIR_GROUND_LABEL_Y, LABEL_COLOR);
                    }
                    if (waterDepthSpawnSlider.visible) {
                        graphics.text(this.font, LABEL_WATER_SPAWN_DEPTH, detailX, SPAWN_AIR_HEIGHT_LABEL_Y, LABEL_COLOR);
                        Component depth = Component.literal(waterSpawn.minDepth() + " - " + waterSpawn.maxDepth());
                        graphics.text(this.font, depth, detailX + detailWidth - this.font.width(depth),
                                SPAWN_AIR_HEIGHT_LABEL_Y, LABEL_COLOR);
                    }
                    if (spawnMethod == SpawnMethod.ZONES && ClientZones.all().isEmpty()) {
                        graphics.textWithWordWrap(this.font, SPAWN_NONE, l1 + 4, SPAWN_LIST_TOP + 6,
                                SPAWN_LIST_WIDTH - 8, LABEL_COLOR);
                    }
                    // Global mode hides the biome list, so spell out that the one density covers every
                    // biome and dimension in the freed space.
                    if (spawnMethod == SpawnMethod.BIOMES && useGlobalBiome) {
                        graphics.textWithWordWrap(this.font, SPAWN_GLOBAL_HINT, l1 + 4, SPAWN_LIST_TOP + 6,
                                SPAWN_LIST_WIDTH - 8, LABEL_COLOR);
                    }
                } else {
                    graphics.textWithWordWrap(this.font, NATURAL_SPAWN_HINT, l1, SPAWN_NATURAL_ROW_Y + 24,
                            RIGHT_PANEL_WIDTH - 20, LABEL_COLOR);
                }
            }
            case JOBS -> extractJobsTab(graphics, leftX, rightX);
        }
    }

    private void extractJobsTab(GuiGraphicsExtractor graphics, int leftX, int rightX) {
        int l1 = rightX + COL1_LABEL_X;
        int l2 = rightX + COL2_LABEL_X;
        columnLabel(graphics, Component.translatable("gui.luna_npc.create_npc.job_type"),
                l1, colRowY(0), COL1_LABEL_MAX);
        if (jobType == NpcJobType.NONE) {
            graphics.textWithWordWrap(this.font, Component.translatable("gui.luna_npc.create_npc.job_type_hint"),
                    l1, colRowY(1) + 4, RIGHT_PANEL_WIDTH - 20, LABEL_COLOR);
            return;
        }
        if (jobType == NpcJobType.QUESTS) {
            columnLabel(graphics, Component.translatable("gui.luna_npc.create_npc.offer_all_quests"),
                    l2, colRowY(0), COL2_LABEL_MAX);
        }
        if (jobType == NpcJobType.DIALOG) {
            columnLabel(graphics, Component.translatable("gui.luna_npc.create_npc.dialog_name"),
                    l2, colRowY(0), COL2_LABEL_MAX);
        }
        if (jobType != NpcJobType.FARMING && jobType != NpcJobType.PATROL) {
            sectionTitle(graphics, Component.translatable("gui.luna_npc.create_npc.interaction"),
                    l1, JOB_INTERACTION_TITLE_Y);
            columnLabel(graphics, Component.translatable("gui.luna_npc.create_npc.block_any_hit"),
                    l1, JOB_BLOCK_TOGGLE_ROW_Y, COL1_LABEL_MAX);
            columnLabel(graphics, Component.translatable("gui.luna_npc.create_npc.block_attacker"),
                    l2, JOB_BLOCK_TOGGLE_ROW_Y, COL2_LABEL_MAX);
        }
        if (jobType == NpcJobType.TRADING || jobType == NpcJobType.QUESTS) {
            sectionTitle(graphics, Component.translatable("gui.luna_npc.create_npc.job_sounds"),
                    l1, JOB_SOUND_TITLE_Y);
        }
        if (jobType == NpcJobType.DIALOG) {
            sectionTitle(graphics, Component.translatable("gui.luna_npc.create_npc.dialog_story"),
                    l1, JOB_SOUND_TITLE_Y);
            columnLabel(graphics, Component.translatable("gui.luna_npc.create_npc.dialog_survive_npc_death"),
                    l1, JOB_SOUND_ROW_Y, COL1_LABEL_MAX);
            columnLabel(graphics, Component.translatable("gui.luna_npc.create_npc.dialog_survive_player_death"),
                    l2, JOB_SOUND_ROW_Y, COL2_LABEL_MAX);
            graphics.text(this.font, Component.translatable("gui.luna_npc.create_npc.dialog_nodes",
                    dialog.nodes().size()), l1, JOB_SUBTYPE_LIST_TOP + 26, LABEL_COLOR);
            return;
        }
        if (jobType == NpcJobType.BODYGUARD) {
            extractBodyguardSummary(graphics, leftX, l1);
            return;
        }
        if (jobType == NpcJobType.TRADING) {
            columnLabel(graphics, Component.translatable("gui.luna_npc.create_npc.trade_success_sound"),
                    l1, JOB_SOUND_ROW_Y, COL1_LABEL_MAX);
            columnLabel(graphics, Component.translatable("gui.luna_npc.create_npc.trade_fail_sound"),
                    l2, JOB_SOUND_ROW_Y, COL2_LABEL_MAX);
        }
        if (jobType == NpcJobType.QUESTS) {
            columnLabel(graphics, Component.translatable("gui.luna_npc.create_npc.quest_complete_sound"),
                    l1, JOB_SOUND_ROW_Y, COL1_LABEL_MAX);
        }
        int count = liveSubTypeCount();
        int sum = liveWeightSum();
        // Quest weights are relative (normalized at roll), so they never need to total 100.
        boolean weightsOff = count > 0 && sum != NpcValidation.JOB_WEIGHT_TOTAL_PERCENT
                && jobType != NpcJobType.QUESTS;
        Component header = Component.translatable("gui.luna_npc.create_npc.sub_types", count, sum + "%");
        sectionTitle(graphics, header, l1, JOB_SUBTYPE_TITLE_Y);
        // Warnings stack up from the panel's bottom, each anchored by its own wrapped height, so a long
        // one can never spill past the panel edge.
        int warnWidth = LEFT_PANEL_WIDTH - 14;
        int stackBottom = CONTENT_TOP + CONTENT_HEIGHT - 6;
        if (weightsOff) {
            graphics.text(this.font, Component.literal("⚠"),
                    l1 + this.font.width(header) + 4, JOB_SUBTYPE_TITLE_Y, WARNING_COLOR);
            Component warning = Component.translatable("gui.luna_npc.create_npc.weight_sum_warning");
            int y = stackBottom - this.font.wordWrapHeight(warning, warnWidth);
            graphics.textWithWordWrap(this.font, warning, leftX + 7, y, warnWidth, WARNING_COLOR);
            stackBottom = y - 6;
        }
        List<Component> jobWarnings = new ArrayList<>();
        if (NpcWarnings.farmingOnNaturalSpawn(jobSettings, spawnSettings)) {
            jobWarnings.add(Component.translatable("gui.luna_npc.create_npc.farming_natural_warning"));
        }
        if (NpcWarnings.patrolOnNaturalSpawn(jobSettings, spawnSettings)) {
            jobWarnings.add(Component.translatable("gui.luna_npc.create_npc.patrol_natural_warning"));
        }
        if (NpcWarnings.patrolWithoutRoute(jobSettings)) {
            jobWarnings.add(Component.translatable("gui.luna_npc.create_npc.patrol_no_route_warning"));
        }
        drawWarningStack(graphics, leftX, stackBottom, jobWarnings);
    }

    // A read-only card under the Edit Bodyguard button: one striped label/value row per setting group,
    // values cut to fit, so the whole setup reads at a glance without opening the editor.
    private void extractBodyguardSummary(GuiGraphicsExtractor graphics, int leftX, int x) {
        int top = JOB_SOUND_TITLE_Y + 26;
        int width = RIGHT_PANEL_WIDTH - 20;
        List<Component[]> rows = bodyguardSummaryRows(bodyguardSettings);
        int labelWidth = 0;
        for (Component[] row : rows) {
            labelWidth = Math.max(labelWidth, this.font.width(row[0]));
        }
        int height = rows.size() * SUMMARY_ROW + 6;
        graphics.fill(x, top, x + width, top + height, SUMMARY_FILL);
        graphics.outline(x, top, width, height, PANEL_BORDER);
        int valueX = x + 8 + labelWidth + 10;
        int valueWidth = x + width - 6 - valueX;
        for (int index = 0; index < rows.size(); index++) {
            int y = top + 3 + index * SUMMARY_ROW;
            if (index % 2 == 1) {
                graphics.fill(x + 1, y, x + width - 1, y + SUMMARY_ROW, SUMMARY_STRIPE);
            }
            graphics.text(this.font, rows.get(index)[0], x + 8, y + 2, LABEL_COLOR);
            String value = rows.get(index)[1].getString();
            if (this.font.width(value) > valueWidth) {
                value = this.font.plainSubstrByWidth(value, valueWidth - this.font.width("…")) + "…";
            }
            graphics.text(this.font, Component.literal(value), valueX, y + 2, TITLE_COLOR);
        }
        List<Component> warnings = new ArrayList<>();
        if (NpcWarnings.bodyguardWithoutAttack(jobSettings, meleeEnabled, rangedEnabled)) {
            warnings.add(Component.translatable("gui.luna_npc.create_npc.bodyguard_no_attack_warning"));
        }
        warnings.addAll(EditBodyguardScreen.warnings(bodyguardSettings));
        drawWarningStack(graphics, leftX, warnings);
    }

    private static List<Component[]> bodyguardSummaryRows(net.lunacore.luna_npc.api.NpcBodyguardSettings settings) {
        String summary = "gui.luna_npc.create_npc.bodyguard_summary.";
        List<Component[]> rows = new ArrayList<>();
        if (settings.source() == net.lunacore.luna_npc.api.NpcBodyguardSource.HIRE) {
            rows.add(summaryRow(summary + "guards", Component.translatable("gui.luna_npc.edit_bodyguard.source.hire")));
            rows.add(summaryRow(summary + "hire", Component.translatable(summary + "hire_value",
                    hirePriceText(settings.hire().cost()), settings.hire().hireSeconds() == 0
                            ? Component.translatable("gui.luna_npc.edit_bodyguard.until_gives_up")
                            : Component.literal(net.lunacore.luna_npc.job.QuestEvents.formatDuration(
                                    settings.hire().hireSeconds())))));
        } else {
            rows.add(summaryRow(summary + "guards", EditBodyguardScreen.protectSummary(settings.protect())));
        }
        rows.add(summaryRow(summary + "range", Component.translatable(summary + "blocks", settings.range())));
        rows.add(summaryRow(summary + "follow", Component.translatable(summary + "follow_value",
                settings.stayRadius(), settings.walkWithin())));
        rows.add(summaryRow(summary + "teleport", settings.teleportDistance() == 0
                ? Component.translatable("gui.luna_npc.edit_bodyguard.off")
                : Component.translatable(summary + "beyond", settings.teleportDistance())));
        Component defense = settings.defenseSeconds() == 0
                ? Component.translatable("gui.luna_npc.edit_bodyguard.until_dead")
                : Component.literal(settings.defenseSeconds() + "s");
        rows.add(summaryRow(summary + "defends", settings.assistAttacks()
                ? Component.translatable(summary + "assists", defense) : defense));
        rows.add(summaryRow(summary + "retreat", settings.retreat()
                ? Component.translatable(summary + "beyond", settings.retreatDistance())
                : Component.translatable("gui.luna_npc.edit_bodyguard.off")));
        rows.add(summaryRow(summary + "friendly_fire",
                settings.friendlyFire() ? CommonComponents.GUI_YES : CommonComponents.GUI_NO));
        rows.add(summaryRow(summary + "after_death", Component.translatable(settings.keepAfterDeath()
                ? "gui.luna_npc.edit_bodyguard.keep_after_death.keep"
                : "gui.luna_npc.edit_bodyguard.keep_after_death.give_up")));
        rows.add(summaryRow(summary + "give_up", Component.translatable(
                "gui.luna_npc.edit_bodyguard.give_up." + settings.giveUp().getSerializedName())));
        return rows;
    }

    private static Component[] summaryRow(String labelKey, Component value) {
        return new Component[] {Component.translatable(labelKey), value};
    }

    // "Free", or the items as "3 Emerald, 1 Diamond".
    private static Component hirePriceText(List<net.lunacore.luna_npc.api.NpcTradeStack> cost) {
        if (cost.isEmpty()) {
            return Component.translatable("gui.luna_npc.bodyguard.free");
        }
        List<String> parts = new ArrayList<>();
        for (net.lunacore.luna_npc.api.NpcTradeStack stack : cost) {
            ItemStack resolved = NpcEquipment.resolve(stack.itemId(), List.of(), ClientRegistries.lookup());
            parts.add(stack.count() + " " + (resolved.isEmpty() ? stack.itemId() : resolved.getHoverName().getString()));
        }
        return Component.literal(String.join(", ", parts));
    }

    // Wrapped warnings stacked up from the bottom of the preview panel (above anything already drawn
    // there), each anchored by its own height so a long one never spills past the panel edge.
    private void drawWarningStack(GuiGraphicsExtractor graphics, int leftX, List<Component> warnings) {
        drawWarningStack(graphics, leftX, CONTENT_TOP + CONTENT_HEIGHT - 6, warnings);
    }

    private void drawWarningStack(GuiGraphicsExtractor graphics, int leftX, int bottom, List<Component> warnings) {
        int width = LEFT_PANEL_WIDTH - 14;
        for (Component warning : warnings) {
            int y = bottom - this.font.wordWrapHeight(warning, width);
            graphics.textWithWordWrap(this.font, warning, leftX + 7, y, width, WARNING_COLOR);
            bottom = y - 6;
        }
    }

    // Combat tab text with the scroll offset applied; lines outside the panel are culled.
    private void combatText(GuiGraphicsExtractor graphics, Component text, int x, int baseY, int color) {
        int y = baseY - combatScroll;
        if (y >= combatClipTop() && y + 10 <= CONTENT_TOP + CONTENT_HEIGHT - 2) {
            graphics.text(this.font, text, x, y, color);
        }
    }

    // A Combat-tab column label with the scroll offset applied; wraps to two lines when it would
    // overflow its control (like columnLabel). baseY is the row top (a combatY value).
    private void combatColumnLabel(GuiGraphicsExtractor graphics, Component text, int x, int baseY) {
        int rowTop = baseY - combatScroll;
        if (rowTop + 6 >= combatClipTop() && rowTop + 16 <= CONTENT_TOP + CONTENT_HEIGHT - 2) {
            columnLabel(graphics, text, x, rowTop, COL1_LABEL_MAX);
        }
    }

    // A label for a row that may be collapsed away: only drawn when the layout kept its key.
    private void combatOptionalLabel(GuiGraphicsExtractor graphics, Component text, int labelX, String key) {
        Integer base = combatY.get(key);
        if (base != null) {
            combatColumnLabel(graphics, text, labelX, base);
        }
    }

    // A gold section title with a divider line filling the rest of the panel width.
    private void combatSection(GuiGraphicsExtractor graphics, Component title, int baseY, int rightX) {
        int y = baseY - combatScroll;
        if (y < combatClipTop() || y + 10 > CONTENT_TOP + CONTENT_HEIGHT - 2) {
            return;
        }
        graphics.text(this.font, title, rightX + 10, y, SECTION_COLOR);
        graphics.fill(rightX + 16 + this.font.width(title), y + 3,
                rightX + RIGHT_PANEL_WIDTH - 12, y + 4, SECTION_COLOR);
    }

    private void extractCombatScrollbar(GuiGraphicsExtractor graphics, int rightX) {
        int maxScroll = maxCombatScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackTop = combatClipTop();
        int trackHeight = CONTENT_HEIGHT - 8 - COMBAT_MODE_BAND;
        int thumbHeight = Math.max(16, trackHeight * trackHeight / (trackHeight + maxScroll));
        int thumbY = trackTop + (trackHeight - thumbHeight) * combatScroll / maxScroll;
        int x = rightX + RIGHT_PANEL_WIDTH - 6;
        graphics.fill(x, trackTop, x + 3, trackTop + trackHeight, 0xFF2A2A34);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xFF5A5A68);
    }

    // Drop chance for the selected drop slot: 0.01% to 100%.
    private class ChanceSlider extends AbstractSliderButton {

        ChanceSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), 0.0);
        }

        void setChance(float chancePercent) {
            this.value = (chancePercent - NpcValidation.MIN_DROP_CHANCE_PERCENT)
                    / (NpcValidation.MAX_DROP_CHANCE_PERCENT - NpcValidation.MIN_DROP_CHANCE_PERCENT);
            updateMessage();
        }

        private float chance() {
            return NpcValidation.MIN_DROP_CHANCE_PERCENT + (float) value
                    * (NpcValidation.MAX_DROP_CHANCE_PERCENT - NpcValidation.MIN_DROP_CHANCE_PERCENT);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(formatChance(chance())));
        }

        @Override
        protected void applyValue() {
            NpcDropSettings.NpcItemDrop selected = itemDrops.get(selectedDropSlot);
            if (selected != null) {
                itemDrops.put(selectedDropSlot,
                        new NpcDropSettings.NpcItemDrop(selected.slot(), selected.itemId(), chance()));
                dropSlots[selectedDropSlot].setOverlayText(shortChance(chance()));
            }
        }
    }
}
