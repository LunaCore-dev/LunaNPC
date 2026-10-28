package net.lunacore.luna_npc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.client.render.model.BakedCustomModel;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcModelCatalog;

public class NpcRenderer extends LivingEntityRenderer<NpcEntity, NpcRenderState, EntityModel<NpcRenderState>> {

    private static final Identifier FALLBACK_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private final Map<String, EntityModel<NpcRenderState>> models;
    private final ItemModelResolver itemModelResolver;

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new StretchableHumanoidModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.itemModelResolver = context.getItemModelResolver();
        this.models = NpcClientModels.buildModels(context);
        addLayer(new NpcArmorLayer(this, context));
        addLayer(new NpcHeadItemLayer(this, context));
        addLayer(new NpcHeldItemLayer(this));
        addLayer(new NpcMobItemLayer(this));
    }

    @Override
    public NpcRenderState createRenderState() {
        return new NpcRenderState();
    }

    // Vanilla shows the tag when the crosshair points at the entity even when the custom name is
    // not set to visible, which makes the "show tag" toggle look ignored. Gate the tag hard on the
    // toggle: hidden means never shown, shown keeps the vanilla distance/team/invisibility rules.
    private static final double NAME_TAG_RANGE_SQ = 32.0 * 32.0;

    @Override
    protected boolean shouldShowName(NpcEntity entity, double distanceToCameraSq) {
        return distanceToCameraSq <= NAME_TAG_RANGE_SQ
                && entity.isCustomNameVisible() && super.shouldShowName(entity, distanceToCameraSq);
    }

    @Override
    public void extractRenderState(NpcEntity entity, NpcRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTick, itemModelResolver);
        applyBowPose(entity, state);
        state.aggressive = entity.isCombatAggressive();
        // Map the NPC's swing (0..1) to the golem's own countdown range (10..0) so its attack arms slam
        // through a full cycle; 0 at rest leaves the model in its idle/walk branch.
        float swing = entity.getSwingAnimation(partialTick);
        state.attackTicksRemaining = swing > 0.0F ? (1.0F - swing) * 10.0F : 0.0F;
        state.modelType = entity.modelTypeId();
        state.modelId = entity.modelId();
        state.headScale = entity.headScale();
        state.bodyScale = entity.bodyScale();
        state.leftArmScale = entity.leftArmScale();
        state.rightArmScale = entity.rightArmScale();
        state.leftLegScale = entity.leftLegScale();
        state.rightLegScale = entity.rightLegScale();
    }

    // NpcRenderer isn't a HumanoidMobRenderer, so it sets the weapon arm pose itself — the vanilla
    // extractHumanoidRenderState above fills the use-item ticks but never the arm pose. Like the
    // skeleton/pillager renderers, a bow stays aimed and a loaded crossbow stays raised for the whole
    // fight (not just mid-draw), so repositioning between shots never drops the weapon; a spear couches
    // while it is used. The pose sits on the main arm.
    private static void applyBowPose(NpcEntity entity, NpcRenderState state) {
        ItemStack main = entity.getMainHandItem();
        HumanoidModel.ArmPose pose = null;
        if (entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            ItemStack used = entity.getUseItem();
            if (used.getItem() instanceof CrossbowItem) {
                pose = HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            } else if (used.getItem() instanceof BowItem) {
                pose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            } else if (used.has(DataComponents.KINETIC_WEAPON)) {
                pose = HumanoidModel.ArmPose.SPEAR;
            }
        } else if (entity.isCombatAggressive()) {
            if (main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main)) {
                pose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
            } else if (main.getItem() instanceof BowItem) {
                pose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
        }
        if (pose == null) {
            return;
        }
        if (entity.getMainArm() == HumanoidArm.RIGHT) {
            state.rightArmPose = pose;
        } else {
            state.leftArmPose = pose;
        }
    }

    // Swapping the model field here is safe: the draw node captures it synchronously inside
    // super.submit, before the next entity's submit call runs.
    @Override
    public void submit(NpcRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState cameraState) {
        this.model = modelFor(state);
        super.submit(state, poseStack, collector, cameraState);
    }

    private EntityModel<NpcRenderState> modelFor(NpcRenderState state) {
        EntityModel<NpcRenderState> perModel = models.get(state.modelType + "/" + state.modelId);
        if (perModel != null) {
            return perModel;
        }
        // A downloaded slim skin borrows Alex's model; classic ones fall through to the wide player model.
        if (net.lunacore.luna_npc.client.ClientSkins.isSlim(state.modelType, state.modelId)) {
            return models.get("player/alex");
        }
        EntityModel<NpcRenderState> perType = models.get(state.modelType);
        if (perType != null) {
            return perType;
        }
        if (NpcModelCatalog.isCustomCategory(state.modelType)) {
            BakedCustomModel custom = CustomModelCache.get(state.modelType);
            if (custom != null) {
                return custom;
            }
        }
        return models.get(NpcDefaults.MODEL_TYPE + "/" + NpcDefaults.MODEL_ID);
    }

    // Custom rigs have no vanilla humanoid slots, so armor/held-item layers would attach to nonexistent
    // bones; skip every layer for a custom category (P1).
    @Override
    protected boolean shouldRenderLayers(NpcRenderState state) {
        return !NpcModelCatalog.isCustomCategory(state.modelType) && super.shouldRenderLayers(state);
    }

    @Override
    public Identifier getTextureLocation(NpcRenderState state) {
        if (NpcModelCatalog.isCustomCategory(state.modelType)) {
            Identifier custom = CustomModelStore.textureFor(state.modelType, state.modelId);
            return custom != null ? custom : FALLBACK_TEXTURE;
        }
        return NpcClientModels.textureFor(state.modelType + "/" + state.modelId, FALLBACK_TEXTURE);
    }
}
