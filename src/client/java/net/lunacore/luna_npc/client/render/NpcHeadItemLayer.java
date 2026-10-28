package net.lunacore.luna_npc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.lunacore.luna_npc.npc.NpcModelCatalog;

// Heads, skulls and blocks worn in the helmet slot (armor helmets go through NpcArmorLayer), on every
// model whose vanilla mob shows them. Villagers sit their head item lower, like VillagerRenderer does.
// Raw types for the same reason as NpcArmorLayer.
@SuppressWarnings({"unchecked", "rawtypes"})
public class NpcHeadItemLayer extends RenderLayer<NpcRenderState, EntityModel<NpcRenderState>> {

    private static final CustomHeadLayer.Transforms VILLAGER_TRANSFORMS =
            new CustomHeadLayer.Transforms(-0.1171875F, -0.07421875F, 1.0F);

    private final CustomHeadLayer defaultLayer;
    private final CustomHeadLayer villagerLayer;

    public NpcHeadItemLayer(RenderLayerParent<NpcRenderState, EntityModel<NpcRenderState>> parent,
                            EntityRendererProvider.Context context) {
        super(parent);
        this.defaultLayer = new CustomHeadLayer((RenderLayerParent) parent, context.getModelSet(),
                context.getPlayerSkinRenderCache());
        this.villagerLayer = new CustomHeadLayer((RenderLayerParent) parent, context.getModelSet(),
                context.getPlayerSkinRenderCache(), VILLAGER_TRANSFORMS);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, NpcRenderState state,
                       float yRot, float xRot) {
        if (!NpcModelCatalog.showsGear(state.modelType, NpcModelCatalog.GearSlot.HEAD)) {
            return;
        }
        if (getParentModel() instanceof AdaptedEntityModel<?> adapted) {
            if (adapted.hasHead()) {
                (adapted.holdsInCrossedArms() ? villagerLayer : defaultLayer)
                        .submit(poseStack, collector, light, state, yRot, xRot);
            }
        } else if (NpcModelCatalog.isStretchable(state.modelType)) {
            defaultLayer.submit(poseStack, collector, light, state, yRot, xRot);
        }
    }
}
