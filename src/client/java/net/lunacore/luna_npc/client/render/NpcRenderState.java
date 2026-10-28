package net.lunacore.luna_npc.client.render;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.lunacore.luna_npc.npc.NpcDefaults;

// Must extend HumanoidRenderState and NOT AvatarRenderState: the submit phase resolves the
// renderer from the state, and any AvatarRenderState is force-routed to the vanilla player
// renderer, which would silently replace our model and texture with a default player skin.
public class NpcRenderState extends HumanoidRenderState {

    public String modelType = NpcDefaults.MODEL_TYPE;
    public String modelId = NpcDefaults.MODEL_ID;
    public float headScale = NpcDefaults.PART_SCALE;
    public float bodyScale = NpcDefaults.PART_SCALE;
    public float leftArmScale = NpcDefaults.PART_SCALE;
    public float rightArmScale = NpcDefaults.PART_SCALE;
    public float leftLegScale = NpcDefaults.PART_SCALE;
    public float rightLegScale = NpcDefaults.PART_SCALE;
    // Drives vanilla model attack poses: aggressive raises a zombie model's arms; attackTicksRemaining
    // (0 at rest, up to 10 mid-swing) feeds a golem model's arm-slam the way its own render state would.
    public boolean aggressive;
    public float attackTicksRemaining;
}
