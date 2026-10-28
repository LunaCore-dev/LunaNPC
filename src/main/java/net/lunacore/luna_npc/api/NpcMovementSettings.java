package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.npc.NpcValidation;
import net.lunacore.luna_npc.npc.NpcDefaults;

// Movement configuration. Walk speed drives the movement-speed attribute, knockback scales (or
// with negative values inverts) received knockback, and pathfinding/wander drive the ground
// navigation. The water/air-specific fields (hover, flight height, depth) are stored for the
// upcoming swim/fly movement; type-specific fields are all kept regardless of the active type so
// switching types never loses values.
public record NpcMovementSettings(String movementType, String pathfindingType, float walkSpeed,
                                  boolean runSpeedEnabled, float runSpeed, float jumpHeight,
                                  List<String> avoidedBlocks,
                                  boolean hoverEnabled, int hoverIntervalMs, float hoverAmount, int flightHeight,
                                  NpcHeightPreference flightHeightPreference, int waterDepth,
                                  NpcHeightPreference waterDepthPreference, float knockback,
                                  NpcWanderSettings wander) {

    public NpcMovementSettings {
        avoidedBlocks = List.copyOf(avoidedBlocks);
    }

    public static final Codec<NpcMovementSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("type", NpcDefaults.MOVEMENT_TYPE)
                    .forGetter(NpcMovementSettings::movementType),
            Codec.STRING.optionalFieldOf("pathfinding_type", NpcDefaults.PATHFINDING_TYPE)
                    .forGetter(NpcMovementSettings::pathfindingType),
            Codec.FLOAT.optionalFieldOf("walk_speed", NpcDefaults.WALK_SPEED)
                    .forGetter(NpcMovementSettings::walkSpeed),
            Codec.BOOL.optionalFieldOf("run_speed_enabled", false).forGetter(NpcMovementSettings::runSpeedEnabled),
            Codec.FLOAT.optionalFieldOf("run_speed", NpcDefaults.WALK_SPEED)
                    .forGetter(NpcMovementSettings::runSpeed),
            Codec.FLOAT.optionalFieldOf("jump_height", NpcDefaults.JUMP_HEIGHT)
                    .forGetter(NpcMovementSettings::jumpHeight),
            Codec.STRING.listOf().optionalFieldOf("avoided_blocks", List.of())
                    .forGetter(NpcMovementSettings::avoidedBlocks),
            Codec.BOOL.optionalFieldOf("hover_enabled", false).forGetter(NpcMovementSettings::hoverEnabled),
            Codec.INT.optionalFieldOf("hover_interval_ms", NpcDefaults.HOVER_INTERVAL_MS)
                    .forGetter(NpcMovementSettings::hoverIntervalMs),
            Codec.FLOAT.optionalFieldOf("hover_amount", NpcDefaults.HOVER_AMOUNT)
                    .forGetter(NpcMovementSettings::hoverAmount),
            Codec.INT.optionalFieldOf("flight_height", NpcDefaults.FLIGHT_HEIGHT)
                    .forGetter(NpcMovementSettings::flightHeight),
            NpcHeightPreference.CODEC.optionalFieldOf("flight_height_preference", NpcHeightPreference.defaults())
                    .forGetter(NpcMovementSettings::flightHeightPreference),
            Codec.INT.optionalFieldOf("water_depth", NpcDefaults.WATER_DEPTH)
                    .forGetter(NpcMovementSettings::waterDepth),
            NpcHeightPreference.CODEC.optionalFieldOf("water_depth_preference", NpcHeightPreference.defaults())
                    .forGetter(NpcMovementSettings::waterDepthPreference),
            Codec.FLOAT.optionalFieldOf("knockback", NpcDefaults.KNOCKBACK)
                    .forGetter(NpcMovementSettings::knockback),
            NpcWanderSettings.CODEC.optionalFieldOf("wander", NpcWanderSettings.defaults())
                    .forGetter(NpcMovementSettings::wander)
    ).apply(instance, NpcMovementSettings::new));

    // 13 fields is beyond StreamCodec.composite's 12-parameter ceiling, so this one is manual.
    public static final StreamCodec<ByteBuf, NpcMovementSettings> STREAM_CODEC = StreamCodec.of(
            NpcMovementSettings::encode, NpcMovementSettings::decode);

    private static void encode(ByteBuf buffer, NpcMovementSettings settings) {
        ByteBufCodecs.STRING_UTF8.encode(buffer, settings.movementType);
        ByteBufCodecs.STRING_UTF8.encode(buffer, settings.pathfindingType);
        ByteBufCodecs.FLOAT.encode(buffer, settings.walkSpeed);
        ByteBufCodecs.BOOL.encode(buffer, settings.runSpeedEnabled);
        ByteBufCodecs.FLOAT.encode(buffer, settings.runSpeed);
        ByteBufCodecs.FLOAT.encode(buffer, settings.jumpHeight);
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8)
                .encode(buffer, new ArrayList<>(settings.avoidedBlocks));
        ByteBufCodecs.BOOL.encode(buffer, settings.hoverEnabled);
        ByteBufCodecs.VAR_INT.encode(buffer, settings.hoverIntervalMs);
        ByteBufCodecs.FLOAT.encode(buffer, settings.hoverAmount);
        ByteBufCodecs.VAR_INT.encode(buffer, settings.flightHeight);
        NpcHeightPreference.STREAM_CODEC.encode(buffer, settings.flightHeightPreference);
        ByteBufCodecs.VAR_INT.encode(buffer, settings.waterDepth);
        NpcHeightPreference.STREAM_CODEC.encode(buffer, settings.waterDepthPreference);
        ByteBufCodecs.FLOAT.encode(buffer, settings.knockback);
        NpcWanderSettings.STREAM_CODEC.encode(buffer, settings.wander);
    }

    private static NpcMovementSettings decode(ByteBuf buffer) {
        return new NpcMovementSettings(
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.FLOAT.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.FLOAT.decode(buffer),
                ByteBufCodecs.FLOAT.decode(buffer),
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8).decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.FLOAT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                NpcHeightPreference.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                NpcHeightPreference.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.FLOAT.decode(buffer),
                NpcWanderSettings.STREAM_CODEC.decode(buffer));
    }

    public static NpcMovementSettings defaults() {
        return new NpcMovementSettings(NpcDefaults.MOVEMENT_TYPE, NpcDefaults.PATHFINDING_TYPE,
                NpcDefaults.WALK_SPEED, false, NpcDefaults.WALK_SPEED, NpcDefaults.JUMP_HEIGHT,
                List.of(), false, NpcDefaults.HOVER_INTERVAL_MS, NpcDefaults.HOVER_AMOUNT,
                NpcDefaults.FLIGHT_HEIGHT, NpcHeightPreference.defaults(), NpcDefaults.WATER_DEPTH,
                NpcHeightPreference.defaults(), NpcDefaults.KNOCKBACK, NpcWanderSettings.defaults());
    }

    // Additive convenience copies for scripting/dev use: each returns a new record with a single
    // field changed via the canonical constructor, preserving immutability.
    public NpcMovementSettings withWalkSpeed(float walkSpeed) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    // Sets the run speed and turns run speed on, since a bare run speed with the flag off is inert.
    public NpcMovementSettings withRunSpeed(float runSpeed) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, true,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    public NpcMovementSettings withMovementType(String movementType) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    public NpcMovementSettings withPathfinding(String pathfindingType) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    public NpcMovementSettings withKnockback(float knockback) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    public NpcMovementSettings withJumpHeight(float jumpHeight) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    // Toggles run speed without touching the stored run-speed value, so it can be turned off and
    // back on without losing the number.
    public NpcMovementSettings withRunSpeedEnabled(boolean runSpeedEnabled) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    public NpcMovementSettings withAvoidedBlocks(List<String> avoidedBlocks) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, new ArrayList<>(avoidedBlocks), hoverEnabled, hoverIntervalMs,
                hoverAmount, flightHeight, flightHeightPreference, waterDepth, waterDepthPreference,
                knockback, wander);
    }

    public NpcMovementSettings withHover(boolean hoverEnabled) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    public NpcMovementSettings withHoverInterval(int hoverIntervalMs) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    public NpcMovementSettings withHoverAmount(float hoverAmount) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    public NpcMovementSettings withFlightHeight(int flightHeight) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    public NpcMovementSettings withFlightHeightPreference(NpcHeightPreference flightHeightPreference) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    public NpcMovementSettings withWaterDepth(int waterDepth) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    public NpcMovementSettings withWaterDepthPreference(NpcHeightPreference waterDepthPreference) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    public NpcMovementSettings withWander(NpcWanderSettings wander) {
        return new NpcMovementSettings(movementType, pathfindingType, walkSpeed, runSpeedEnabled,
                runSpeed, jumpHeight, avoidedBlocks, hoverEnabled, hoverIntervalMs, hoverAmount,
                flightHeight, flightHeightPreference, waterDepth, waterDepthPreference, knockback, wander);
    }

    public float effectiveRunSpeed() {
        return runSpeedEnabled ? runSpeed : walkSpeed;
    }

    // Speeds are stored in blocks per second. A vanilla player walks ~4.317 B/S at the default
    // 0.1 movement-speed attribute, so this ratio converts wherever the attribute scale is
    // needed (ground locomotion runs through the same travel physics as players).
    public static float speedAttributeFromBlocksPerSecond(float blocksPerSecond) {
        return blocksPerSecond * (0.1F / 4.317F);
    }

    public boolean isValid() {
        if (!NpcValidation.MOVEMENT_TYPES.contains(movementType)
                || !NpcValidation.isValidPathfindingType(pathfindingType)
                || !NpcValidation.isValidMoveSpeed(walkSpeed)
                || !NpcValidation.isValidMoveSpeed(runSpeed)
                || jumpHeight < NpcValidation.MIN_JUMP_HEIGHT || jumpHeight > NpcValidation.MAX_JUMP_HEIGHT
                || avoidedBlocks.size() > NpcValidation.MAX_AVOIDED_BLOCKS
                || hoverIntervalMs < NpcValidation.MIN_HOVER_INTERVAL_MS
                || hoverIntervalMs > NpcValidation.MAX_HOVER_INTERVAL_MS
                || hoverAmount < 0.0F || hoverAmount > NpcValidation.MAX_HOVER_AMOUNT
                || flightHeight < 0 || flightHeight > NpcValidation.MAX_FLIGHT_HEIGHT
                || waterDepth < 0 || waterDepth > NpcValidation.MAX_WATER_DEPTH
                || knockback < NpcValidation.MIN_KNOCKBACK || knockback > NpcValidation.MAX_KNOCKBACK
                || !flightHeightPreference.isValid() || !waterDepthPreference.isValid()
                || !wander.isValid()) {
            return false;
        }
        for (String blockId : avoidedBlocks) {
            if (Identifier.tryParse(blockId) == null) {
                return false;
            }
        }
        return true;
    }
}
