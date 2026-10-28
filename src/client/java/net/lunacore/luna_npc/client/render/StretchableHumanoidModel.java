package net.lunacore.luna_npc.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.lunacore.luna_npc.npc.NpcModelCatalog;

public class StretchableHumanoidModel extends HumanoidModel<NpcRenderState> {

    // A zombie-textured NPC uses this humanoid model (so it keeps per-part scaling), so the vanilla
    // zombie arms-forward stance is applied here instead of coming from a ZombieModel.
    private final boolean zombieArms;

    public StretchableHumanoidModel(ModelPart root) {
        this(root, false);
    }

    public StretchableHumanoidModel(ModelPart root, boolean zombieArms) {
        super(root);
        this.zombieArms = zombieArms;
    }

    // Part scaling must be applied here and not in the renderer: the deferred render pass calls
    // setupAnim again right before drawing, and its resetPose() wipes any scale set earlier.
    // Overlay parts (hat, sleeves, pants, jacket, ears) are children of their limbs and follow
    // along — touching them here doubles the transform (in 26.3 hat is a child of head, so scaling
    // or lifting it too renders a second, oversized head floating above the real one).
    @Override
    public void setupAnim(NpcRenderState state) {
        super.setupAnim(state);
        // Overrides the humanoid arm pose with the zombie stance while aggressive, unless the NPC is
        // aiming a bow/crossbow (its use-item pose wins).
        if (zombieArms && state.aggressive && !state.isUsingItem) {
            applyZombieArms(state);
        }
        scalePart(head, state.headScale);
        scalePart(body, state.bodyScale);
        scalePart(leftArm, state.leftArmScale);
        scalePart(rightArm, state.rightArmScale);
        scalePart(leftLeg, state.leftLegScale);
        scalePart(rightLeg, state.rightLegScale);

        float maxLegScale = Math.max(state.leftLegScale, state.rightLegScale);
        float maxArmScale = Math.max(state.leftArmScale, state.rightArmScale);
        // Legs scale downward from the hip pivot, so keep the feet on the ground by lifting every
        // root part by the gained leg length; if an oversized arm's tip would still end up below
        // the ground plane, lift the body further so the arm tip rests on the ground instead of
        // clipping under it (mirrored by the hitbox math in NpcEntity). Model y grows downward,
        // hence the subtraction. Leg length is read from the render state's model type so the same
        // model instance works as an armor model across all humanoid types.
        float legPixels = NpcModelCatalog.legPixels(state.modelType);
        float liftPixels = (maxLegScale - 1.0F) * legPixels
                + NpcModelCatalog.armOverhangPixels(state.modelType, maxArmScale, maxLegScale);
        if (liftPixels != 0.0F) {
            for (ModelPart part : new ModelPart[]{head, body, leftArm, rightArm, leftLeg, rightLeg}) {
                part.y -= liftPixels;
            }
        }
    }

    // Mirrors vanilla AnimationUtils.animateZombieArms: both arms reach forward, the reach deepening
    // and the arms bobbing through the melee swing (swingAnimation), plus a slow idle sway (ageInTicks).
    private void applyZombieArms(NpcRenderState state) {
        float attack = state.swingAnimation;
        float reach = Mth.sin(attack * (float) Math.PI);
        float recover = Mth.sin((1.0F - (1.0F - attack) * (1.0F - attack)) * (float) Math.PI);
        rightArm.zRot = 0.0F;
        leftArm.zRot = 0.0F;
        rightArm.yRot = -(0.1F - reach * 0.6F);
        leftArm.yRot = 0.1F - reach * 0.6F;
        rightArm.xRot = -1.5707964F - (reach * 1.2F - recover * 0.4F);
        leftArm.xRot = -1.5707964F - (reach * 1.2F - recover * 0.4F);
        rightArm.zRot += Mth.cos(state.ageInTicks * 0.09F) * 0.05F + 0.05F;
        leftArm.zRot -= Mth.cos(state.ageInTicks * 0.09F) * 0.05F + 0.05F;
        rightArm.xRot += Mth.sin(state.ageInTicks * 0.067F) * 0.05F;
        leftArm.xRot -= Mth.sin(state.ageInTicks * 0.067F) * 0.05F;
    }

    private static void scalePart(ModelPart part, float scale) {
        part.xScale *= scale;
        part.yScale *= scale;
        part.zScale *= scale;
    }
}
