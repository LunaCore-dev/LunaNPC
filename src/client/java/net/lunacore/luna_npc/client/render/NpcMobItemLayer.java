package net.lunacore.luna_npc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.animal.fox.FoxModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.lunacore.luna_npc.npc.NpcModelCatalog;

// The main-hand item for handless mobs that still hold things in vanilla: villagers and witches in
// their crossed arms, foxes in the mouth. Transforms mirror CrossedArmsItemLayer and FoxHeldItemLayer,
// which can't be reused directly because they read the held item from their own mob render state.
public class NpcMobItemLayer extends RenderLayer<NpcRenderState, EntityModel<NpcRenderState>> {

    public NpcMobItemLayer(RenderLayerParent<NpcRenderState, EntityModel<NpcRenderState>> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, NpcRenderState state,
                       float yRot, float xRot) {
        ItemStackRenderState item = state.getMainHandItemState();
        if (item.isEmpty() || !(getParentModel() instanceof AdaptedEntityModel<?> adapted)
                || !NpcModelCatalog.showsGear(state.modelType, NpcModelCatalog.GearSlot.MAIN_HAND)) {
            return;
        }
        if (adapted.holdsInCrossedArms()) {
            poseStack.pushPose();
            adapted.translateToArms(poseStack);
            poseStack.rotate(Axis.XP, 0.75F);
            poseStack.scale(1.07F, 1.07F, 1.07F);
            poseStack.translate(0.0F, 0.13F, -0.34F);
            poseStack.rotate(Axis.XP, (float) Math.PI);
            item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        } else if (adapted.delegateModel() instanceof FoxModel fox) {
            poseStack.pushPose();
            poseStack.translate(fox.head.x / 16.0F, fox.head.y / 16.0F, fox.head.z / 16.0F);
            poseStack.rotateDegrees(Axis.YP, yRot);
            poseStack.rotateDegrees(Axis.XP, xRot);
            poseStack.translate(0.06F, 0.27F, -0.5F);
            poseStack.rotateDegrees(Axis.XP, 90.0F);
            item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
    }
}
