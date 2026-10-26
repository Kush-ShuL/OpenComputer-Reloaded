package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.LivingEntity;

/** Original drone-textured hover platforms, including the four colored lights. */
public final class HoverBootsModel extends HumanoidModel<LivingEntity> {
    public int lightColor = 0x66dd55;
    private final List<ModelPart> lights = new ArrayList<>(), bodies = new ArrayList<>(), wings = new ArrayList<>();
    public HoverBootsModel() {
        super(layer().bakeRoot());
        for (ModelPart leg : List.of(leftLeg, rightLeg)) {
            ModelPart boot = leg.getChild("hover_boot");
            bodies.add(boot.getChild("platform"));
            for (int i = 0; i < 2; i++) {
                ModelPart wing = boot.getChild("wing" + i);
                wings.add(wing); lights.add(wing.getChild("light"));
            }
        }
        lights.forEach(part -> part.visible = false);
    }
    private static LayerDefinition layer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        var root = mesh.getRoot();
        var left = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9f, 12, 0));
        var right = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9f, 12, 0));
        platform(left, true); platform(right, false);
        return LayerDefinition.create(mesh, 64, 32);
    }
    private static void platform(PartDefinition leg, boolean left) {
        var boot = leg.addOrReplaceChild("hover_boot", CubeListBuilder.create(), PartPose.offset(0, left ? 10.11f : 10.1f, 0));
        boot.addOrReplaceChild("platform", CubeListBuilder.create()
                .texOffs(0, 1).addBox(-3, 1, -3, 6, 1, 6)
                .texOffs(0, 23).addBox(-1, 0, -1, 2, 1, 2)
                .texOffs(0, 17).addBox(-2, -1, -2, 4, 1, 4), PartPose.rotation(0, (float) Math.PI / 4, 0));
        for (int index = 0; index < 2; index++) {
            float x = left ? -1 : -5;
            float z = index == 0 ? -7 : 1;
            float pinX = left ? 0 : -1;
            float pinZ = index == 0 ? -3 : 2;
            var wing = boot.addOrReplaceChild("wing" + index, CubeListBuilder.create()
                    .texOffs(0, 9).addBox(x, 0, z, 6, 1, 6)
                    .texOffs(0, 27).addBox(pinX, -1, pinZ, 1, 3, 1), PartPose.ZERO);
            wing.addOrReplaceChild("light", CubeListBuilder.create().texOffs(24, 0).addBox(x, 0, z, 6, 1, 6), PartPose.ZERO);
        }
    }
    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int packedLight, int overlay, int color) {
        lights.forEach(part -> part.visible = false);
        super.renderToBuffer(pose, vertices, packedLight, overlay, color);
        bodies.forEach(part -> part.visible = false);
        wings.forEach(part -> part.skipDraw = true);
        lights.forEach(part -> part.visible = true);
        try {
            super.renderToBuffer(pose, vertices, LightTexture.FULL_BRIGHT, overlay, 0xff000000 | lightColor);
        } finally {
            bodies.forEach(part -> part.visible = true);
            wings.forEach(part -> part.skipDraw = false);
            lights.forEach(part -> part.visible = false);
        }
    }
}
