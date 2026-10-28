package net.lunacore.luna_npc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.lunacore.luna_npc.npc.NpcModelCatalog;

// Wraps the vanilla held-item layer so it only runs for models with real hands: humanoids, and adapted
// mobs whose vanilla model is armed (illagers, vex, allay). The layer positions items via the active
// model's translateToHand — running it on a handless mob model would crash. Raw types for the same
// reason as NpcArmorLayer.
@SuppressWarnings({"unchecked", "rawtypes"})
public class NpcHeldItemLayer extends RenderLayer<NpcRenderState, EntityModel<NpcRenderState>> {

    private final ItemInHandLayer vanillaLayer;

    public NpcHeldItemLayer(RenderLayerParent<NpcRenderState, EntityModel<NpcRenderState>> parent) {
        super(parent);
        this.vanillaLayer = new ItemInHandLayer((RenderLayerParent) parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, NpcRenderState state,
                       float yRot, float xRot) {
        boolean armed = NpcModelCatalog.isStretchable(state.modelType)
                || getParentModel() instanceof AdaptedEntityModel<?> adapted && adapted.hasHands();
        if (armed && NpcModelCatalog.showsGear(state.modelType, NpcModelCatalog.GearSlot.MAIN_HAND)) {
            vanillaLayer.submit(poseStack, collector, light, state, yRot, xRot);
        }
    }
}
