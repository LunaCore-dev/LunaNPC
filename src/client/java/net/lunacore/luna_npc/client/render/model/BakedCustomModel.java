package net.lunacore.luna_npc.client.render.model;

import java.util.List;
import java.util.Map;
import net.lunacore.luna_npc.client.render.NpcRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

// A runtime-baked custom model: the ModelPart tree drawn by the vanilla renderer, a name->part index so
// keyframe animation can pose bones, and the measured collision box. Clips bind to NPC state by
// convention (a "walk"/"move" clip while moving, else "idle"); values are Bedrock-authored, so the same
// axis/sign flips CustomModelBaker applies to geometry apply to animation deltas here.
public class BakedCustomModel extends EntityModel<NpcRenderState> {

    private static final boolean ANIMATE = true;

    private final Map<String, ModelPart> bones;
    private final Map<String, CustomModel.Animation> animations;
    private final float width;
    private final float height;
    private final float eyeHeight;

    public BakedCustomModel(ModelPart root, Map<String, ModelPart> bones,
                            Map<String, CustomModel.Animation> animations,
                            float width, float height, float eyeHeight) {
        super(root);
        this.bones = bones;
        this.animations = animations;
        this.width = width;
        this.height = height;
        this.eyeHeight = eyeHeight;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float eyeHeight() {
        return eyeHeight;
    }

    @Override
    public void setupAnim(NpcRenderState state) {
        // Reset to the rest pose each frame so animation deltas are applied fresh, never accumulated.
        resetPose();
        if (!ANIMATE) {
            return;
        }
        CustomModel.Animation clip = pickClip(state);
        if (clip == null || clip.length() <= 0.0) {
            return;
        }
        double time = state.ageInTicks / 20.0;
        time = clip.loop() ? time % clip.length() : Math.min(time, clip.length());
        for (Map.Entry<String, CustomModel.BoneChannels> entry : clip.bones().entrySet()) {
            ModelPart part = bones.get(entry.getKey());
            if (part != null) {
                apply(part, entry.getValue(), time);
            }
        }
    }

    private CustomModel.Animation pickClip(NpcRenderState state) {
        boolean moving = state.walkAnimationSpeed > 0.01F;
        if (moving) {
            CustomModel.Animation walk = firstNamed("walk", "move", "walking");
            if (walk != null) {
                return walk;
            }
        }
        if (state.attackTicksRemaining > 0.0F) {
            CustomModel.Animation attack = firstNamed("attack", "attacking");
            if (attack != null) {
                return attack;
            }
        }
        CustomModel.Animation idle = firstNamed("idle", "idle_pose", "look_at_target");
        return idle != null ? idle : (animations.isEmpty() ? null : animations.values().iterator().next());
    }

    private CustomModel.Animation firstNamed(String... needles) {
        for (Map.Entry<String, CustomModel.Animation> entry : animations.entrySet()) {
            String key = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            for (String needle : needles) {
                if (key.contains(needle)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static void apply(ModelPart part, CustomModel.BoneChannels channels, double time) {
        float[] position = sample(channels.position(), time, 0.0F);
        float[] rotation = sample(channels.rotation(), time, 0.0F);
        float[] scale = sample(channels.scale(), time, 1.0F);
        // Same Bedrock->vanilla flip as the geometry (M = diag(-1,-1,1)): X and Y negate, Z keeps its sign.
        part.x += -position[0];
        part.y += -position[1];
        part.z += position[2];
        part.xRot += (float) -Math.toRadians(rotation[0]);
        part.yRot += (float) -Math.toRadians(rotation[1]);
        part.zRot += (float) Math.toRadians(rotation[2]);
        part.xScale *= scale[0];
        part.yScale *= scale[1];
        part.zScale *= scale[2];
    }

    // Catmull-rom interpolation (Blockbench's default keyframe smoothing) using the two surrounding keys.
    private static float[] sample(List<CustomModel.Key> keys, double time, float fallback) {
        if (keys == null || keys.isEmpty()) {
            return new float[]{fallback, fallback, fallback};
        }
        if (keys.size() == 1 || time <= keys.getFirst().time()) {
            return xyz(keys.getFirst());
        }
        if (time >= keys.getLast().time()) {
            return xyz(keys.getLast());
        }
        for (int i = 0; i < keys.size() - 1; i++) {
            CustomModel.Key a = keys.get(i);
            CustomModel.Key b = keys.get(i + 1);
            if (time >= a.time() && time <= b.time()) {
                float t = b.time() == a.time() ? 0.0F : (float) ((time - a.time()) / (b.time() - a.time()));
                CustomModel.Key p0 = i > 0 ? keys.get(i - 1) : a;
                CustomModel.Key p3 = i + 2 < keys.size() ? keys.get(i + 2) : b;
                return new float[]{
                        catmull(p0.x(), a.x(), b.x(), p3.x(), t),
                        catmull(p0.y(), a.y(), b.y(), p3.y(), t),
                        catmull(p0.z(), a.z(), b.z(), p3.z(), t)};
            }
        }
        return xyz(keys.getLast());
    }

    private static float catmull(float p0, float p1, float p2, float p3, float t) {
        return 0.5F * (2.0F * p1 + (-p0 + p2) * t
                + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t * t
                + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t * t * t);
    }

    private static float[] xyz(CustomModel.Key key) {
        return new float[]{key.x(), key.y(), key.z()};
    }
}
