package net.lunacore.luna_npc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.lunacore.luna_npc.npc.NpcModelCatalog;

// Wraps the vanilla armor layer so it only runs for humanoid (stretchable) model types — the
// adapted mob models have no humanoid parts to attach armor to. The armor models are our
// stretchable humanoids, so armor pieces scale and lift together with the stretched body. The raw
// types are unavoidable: the vanilla layer requires M extends HumanoidModel, while our renderer's
// model type must stay EntityModel to also carry the adapted mob models.
@SuppressWarnings({"unchecked", "rawtypes"})
public class NpcArmorLayer extends RenderLayer<NpcRenderState, EntityModel<NpcRenderState>> {

    private final HumanoidArmorLayer vanillaLayer;

    public NpcArmorLayer(RenderLayerParent<NpcRenderState, EntityModel<NpcRenderState>> parent,
                         EntityRendererProvider.Context context) {
        super(parent);
        ArmorModelSet<StretchableHumanoidModel> armorModels = ArmorModelSet.bake(
                ModelLayers.PLAYER_ARMOR, context.getModelSet(), StretchableHumanoidModel::new);
        this.vanillaLayer = new HumanoidArmorLayer((RenderLayerParent) parent, armorModels,
                context.getEquipmentRenderer());
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, NpcRenderState state,
                       float yRot, float xRot) {
        if (NpcModelCatalog.isStretchable(state.modelType)) {
            vanillaLayer.submit(poseStack, collector, light, state, yRot, xRot);
        }
    }
}
