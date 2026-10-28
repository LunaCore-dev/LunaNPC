package net.lunacore.luna_npc.client.render.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// Vanilla ModelPart.Cube only knows the box-UV cross unwrap, so it cannot carry the per-face UVs a
// Blockbench model uses. ModelPart dispatches cube.compile() virtually (verified in the 26.3 bytecode),
// so this subclass supplies pre-built per-face polygons and emits them exactly like vanilla Cube.compile
// (transform normal, /16 vertex positions, addVertex with color/uv/overlay/light/normal).
final class PerFaceCube extends ModelPart.Cube {

    private final ModelPart.Polygon[] faces;

    PerFaceCube(float minX, float minY, float minZ, float dimX, float dimY, float dimZ,
                int textureWidth, int textureHeight, ModelPart.Polygon[] faces) {
        super(0, 0, minX, minY, minZ, dimX, dimY, dimZ, 0.0F, 0.0F, 0.0F, false,
                textureWidth, textureHeight, Set.<Direction>of());
        this.faces = faces;
    }

    @Override
    public void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        Matrix4f matrix = pose.pose();
        Vector3f normal = new Vector3f();
        for (ModelPart.Polygon polygon : faces) {
            pose.transformNormal(polygon.normal(), normal);
            for (ModelPart.Vertex vertex : polygon.vertices()) {
                Vector3f position = matrix.transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(),
                        new Vector3f());
                consumer.addVertex(position.x(), position.y(), position.z(), color, vertex.u(), vertex.v(),
                        overlay, light, normal.x(), normal.y(), normal.z());
            }
        }
    }
}
