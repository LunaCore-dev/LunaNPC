package net.lunacore.luna_npc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.VillagerLikeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.HumanoidArm;

// Lets our renderer draw any vanilla mob model even though those models are typed to their own
// render states: the adapter shares the vanilla model's part tree (parts are mutable and shared
// by reference), copies the common animation fields into a private instance of the vanilla state,
// and delegates setupAnim — so the vanilla model poses the very parts this adapter renders. It also
// forwards the hand/head/arms hooks the vanilla item layers need, valid only when the delegate has them.
@SuppressWarnings("unchecked")
public class AdaptedEntityModel<S extends EntityRenderState> extends EntityModel<NpcRenderState>
        implements ArmedModel<NpcRenderState>, HeadedModel {

    private final EntityModel<? super S> delegateModel;
    private final S delegateState;
    // Copies the NPC's own attack state onto the delegate's mob-specific fields (a golem's
    // attackTicksRemaining), which the generic field copy below can't reach. Null for models with none.
    private final BiConsumer<NpcRenderState, S> animator;

    public AdaptedEntityModel(ModelPart root, Function<ModelPart, EntityModel<? super S>> modelFactory,
                              Supplier<S> stateFactory, Consumer<S> stateInitializer,
                              BiConsumer<NpcRenderState, S> animator) {
        super(root);
        this.delegateModel = modelFactory.apply(root);
        this.delegateState = stateFactory.get();
        this.animator = animator;
        if (stateInitializer != null) {
            stateInitializer.accept(delegateState);
        }
    }

    @Override
    public void setupAnim(NpcRenderState state) {
        copyAnimationFields(state, delegateState);
        if (animator != null) {
            animator.accept(state, delegateState);
        }
        delegateModel.setupAnim(delegateState);
    }

    public boolean hasHands() {
        return delegateModel instanceof ArmedModel;
    }

    public boolean hasHead() {
        return delegateModel instanceof HeadedModel;
    }

    public boolean holdsInCrossedArms() {
        return delegateModel instanceof VillagerLikeModel;
    }

    public EntityModel<? super S> delegateModel() {
        return delegateModel;
    }

    @Override
    public void translateToHand(NpcRenderState state, HumanoidArm arm, PoseStack poseStack) {
        ((ArmedModel<? super S>) delegateModel).translateToHand(delegateState, arm, poseStack);
    }

    @Override
    public ModelPart getHead() {
        return ((HeadedModel) delegateModel).getHead();
    }

    public void translateToArms(PoseStack poseStack) {
        ((VillagerLikeModel<? super S>) delegateModel).translateToArms(delegateState, poseStack);
    }

    private static void copyAnimationFields(NpcRenderState source, EntityRenderState target) {
        target.ageInTicks = source.ageInTicks;
        if (target instanceof LivingEntityRenderState living) {
            living.bodyRot = source.bodyRot;
            living.yRot = source.yRot;
            living.xRot = source.xRot;
            living.deathTime = source.deathTime;
            living.walkAnimationPos = source.walkAnimationPos;
            living.walkAnimationSpeed = source.walkAnimationSpeed;
            living.scale = source.scale;
            living.ageScale = source.ageScale;
            living.pose = source.pose;
            living.isBaby = false;
            living.isInWater = source.isInWater;
            living.isUpsideDown = source.isUpsideDown;
        }
        if (target instanceof ArmedEntityRenderState armed) {
            armed.mainArm = source.mainArm;
            armed.swingAnimation = source.swingAnimation;
            armed.currentSwing = source.currentSwing;
        }
    }
}
