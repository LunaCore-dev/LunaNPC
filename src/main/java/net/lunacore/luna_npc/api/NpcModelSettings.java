package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcModelCatalog;
import net.lunacore.luna_npc.npc.NpcValidation;

// customWidth/customHeight/customEyeHeight are 0 for every built-in model type; a custom-geometry
// category fills them with the box measured from its imported mesh, so the server-authoritative hitbox
// is correct even on clients that lack the geometry file (they render a fallback on the right-sized box).
public record NpcModelSettings(String modelType, String modelId, float modelSize,
                               float headScale, float bodyScale,
                               float leftArmScale, float rightArmScale,
                               float leftLegScale, float rightLegScale,
                               float customWidth, float customHeight, float customEyeHeight) {

    public static final Codec<NpcModelSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("model_type").forGetter(NpcModelSettings::modelType),
            Codec.STRING.fieldOf("model_id").forGetter(NpcModelSettings::modelId),
            Codec.FLOAT.optionalFieldOf("model_size", NpcDefaults.MODEL_SIZE).forGetter(NpcModelSettings::modelSize),
            Codec.FLOAT.optionalFieldOf("head_scale", NpcDefaults.PART_SCALE).forGetter(NpcModelSettings::headScale),
            Codec.FLOAT.optionalFieldOf("body_scale", NpcDefaults.PART_SCALE).forGetter(NpcModelSettings::bodyScale),
            Codec.FLOAT.optionalFieldOf("left_arm_scale", NpcDefaults.PART_SCALE).forGetter(NpcModelSettings::leftArmScale),
            Codec.FLOAT.optionalFieldOf("right_arm_scale", NpcDefaults.PART_SCALE).forGetter(NpcModelSettings::rightArmScale),
            Codec.FLOAT.optionalFieldOf("left_leg_scale", NpcDefaults.PART_SCALE).forGetter(NpcModelSettings::leftLegScale),
            Codec.FLOAT.optionalFieldOf("right_leg_scale", NpcDefaults.PART_SCALE).forGetter(NpcModelSettings::rightLegScale),
            Codec.FLOAT.optionalFieldOf("custom_width", 0.0F).forGetter(NpcModelSettings::customWidth),
            Codec.FLOAT.optionalFieldOf("custom_height", 0.0F).forGetter(NpcModelSettings::customHeight),
            Codec.FLOAT.optionalFieldOf("custom_eye_height", 0.0F).forGetter(NpcModelSettings::customEyeHeight)
    ).apply(instance, NpcModelSettings::new));

    public static final StreamCodec<ByteBuf, NpcModelSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcModelSettings::modelType,
            ByteBufCodecs.STRING_UTF8, NpcModelSettings::modelId,
            ByteBufCodecs.FLOAT, NpcModelSettings::modelSize,
            ByteBufCodecs.FLOAT, NpcModelSettings::headScale,
            ByteBufCodecs.FLOAT, NpcModelSettings::bodyScale,
            ByteBufCodecs.FLOAT, NpcModelSettings::leftArmScale,
            ByteBufCodecs.FLOAT, NpcModelSettings::rightArmScale,
            ByteBufCodecs.FLOAT, NpcModelSettings::leftLegScale,
            ByteBufCodecs.FLOAT, NpcModelSettings::rightLegScale,
            ByteBufCodecs.FLOAT, NpcModelSettings::customWidth,
            ByteBufCodecs.FLOAT, NpcModelSettings::customHeight,
            ByteBufCodecs.FLOAT, NpcModelSettings::customEyeHeight,
            NpcModelSettings::new);

    public static NpcModelSettings defaults() {
        return new NpcModelSettings(NpcDefaults.MODEL_TYPE, NpcDefaults.MODEL_ID, NpcDefaults.MODEL_SIZE,
                NpcDefaults.PART_SCALE, NpcDefaults.PART_SCALE, NpcDefaults.PART_SCALE,
                NpcDefaults.PART_SCALE, NpcDefaults.PART_SCALE, NpcDefaults.PART_SCALE, 0.0F, 0.0F, 0.0F);
    }

    // Additive convenience copies for scripting/dev use: each returns a new record with the named
    // fields changed via the canonical constructor, preserving immutability.
    public NpcModelSettings withModel(String modelType, String modelId) {
        return new NpcModelSettings(modelType, modelId, modelSize, headScale, bodyScale,
                leftArmScale, rightArmScale, leftLegScale, rightLegScale, customWidth, customHeight, customEyeHeight);
    }

    public NpcModelSettings withSize(float modelSize) {
        return new NpcModelSettings(modelType, modelId, modelSize, headScale, bodyScale,
                leftArmScale, rightArmScale, leftLegScale, rightLegScale, customWidth, customHeight, customEyeHeight);
    }

    public NpcModelSettings withModelType(String modelType) {
        return new NpcModelSettings(modelType, modelId, modelSize, headScale, bodyScale,
                leftArmScale, rightArmScale, leftLegScale, rightLegScale, customWidth, customHeight, customEyeHeight);
    }

    public NpcModelSettings withModelId(String modelId) {
        return new NpcModelSettings(modelType, modelId, modelSize, headScale, bodyScale,
                leftArmScale, rightArmScale, leftLegScale, rightLegScale, customWidth, customHeight, customEyeHeight);
    }

    public NpcModelSettings withHeadScale(float headScale) {
        return new NpcModelSettings(modelType, modelId, modelSize, headScale, bodyScale,
                leftArmScale, rightArmScale, leftLegScale, rightLegScale, customWidth, customHeight, customEyeHeight);
    }

    public NpcModelSettings withBodyScale(float bodyScale) {
        return new NpcModelSettings(modelType, modelId, modelSize, headScale, bodyScale,
                leftArmScale, rightArmScale, leftLegScale, rightLegScale, customWidth, customHeight, customEyeHeight);
    }

    public NpcModelSettings withLeftArmScale(float leftArmScale) {
        return new NpcModelSettings(modelType, modelId, modelSize, headScale, bodyScale,
                leftArmScale, rightArmScale, leftLegScale, rightLegScale, customWidth, customHeight, customEyeHeight);
    }

    public NpcModelSettings withRightArmScale(float rightArmScale) {
        return new NpcModelSettings(modelType, modelId, modelSize, headScale, bodyScale,
                leftArmScale, rightArmScale, leftLegScale, rightLegScale, customWidth, customHeight, customEyeHeight);
    }

    public NpcModelSettings withLeftLegScale(float leftLegScale) {
        return new NpcModelSettings(modelType, modelId, modelSize, headScale, bodyScale,
                leftArmScale, rightArmScale, leftLegScale, rightLegScale, customWidth, customHeight, customEyeHeight);
    }

    public NpcModelSettings withRightLegScale(float rightLegScale) {
        return new NpcModelSettings(modelType, modelId, modelSize, headScale, bodyScale,
                leftArmScale, rightArmScale, leftLegScale, rightLegScale, customWidth, customHeight, customEyeHeight);
    }

    // Sets all six part scales to the same value.
    public NpcModelSettings withAllPartScales(float scale) {
        return new NpcModelSettings(modelType, modelId, modelSize, scale, scale,
                scale, scale, scale, scale, customWidth, customHeight, customEyeHeight);
    }

    // The measured collision box of an imported custom mesh; 0/0/0 clears it back to a built-in type.
    public NpcModelSettings withCustomBox(float customWidth, float customHeight, float customEyeHeight) {
        return new NpcModelSettings(modelType, modelId, modelSize, headScale, bodyScale,
                leftArmScale, rightArmScale, leftLegScale, rightLegScale, customWidth, customHeight, customEyeHeight);
    }

    // Builds from defaults (part scales stay at NpcDefaults.PART_SCALE) with just the model set.
    public static NpcModelSettings of(String modelType, String modelId) {
        return defaults().withModel(modelType, modelId);
    }

    public static NpcModelSettings of(String modelType, String modelId, float modelSize) {
        return defaults().withModel(modelType, modelId).withSize(modelSize);
    }

    public boolean isValid() {
        return NpcModelCatalog.isValidModel(modelType, modelId)
                && NpcValidation.isValidModelSize(modelSize)
                && NpcValidation.isValidLimbStretch(headScale)
                && NpcValidation.isValidLimbStretch(bodyScale)
                && NpcValidation.isValidLimbStretch(leftArmScale)
                && NpcValidation.isValidLimbStretch(rightArmScale)
                && NpcValidation.isValidLimbStretch(leftLegScale)
                && NpcValidation.isValidLimbStretch(rightLegScale)
                && isFiniteNonNegative(customWidth)
                && isFiniteNonNegative(customHeight)
                && isFiniteNonNegative(customEyeHeight);
    }

    private static boolean isFiniteNonNegative(float value) {
        return Float.isFinite(value) && value >= 0.0F;
    }
}
