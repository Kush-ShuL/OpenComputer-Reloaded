package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import li.cil.oc.common.block.entity.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** The original emissive side and top overlays, using animated block-atlas sprites. */
public final class WorkstationBlockEntityRenderer<T extends WorkstationBlockEntity> implements BlockEntityRenderer<T> {
    @Override public void render(T machine, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        boolean assembler = machine instanceof AssemblerBlockEntity;
        if (!assembler && !machine.active()) return;
        var out = buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));
        pose.pushPose();
        if (assembler) {
            pose.translate(.5, .5, .5);
            quad(out, pose, "assembler_top_on", new float[][]{{-.5f,.55f,.5f,0,1},{.5f,.55f,.5f,1,1},{.5f,.55f,-.5f,1,0},{-.5f,.55f,-.5f,0,0}}, 0, 1, 0, overlay);
            for (int index = 0; index < 4; index++) {
                if (machine.busy()) { float indent = 6 / 16f + .005f; quad(out, pose, "assembler_side_assembling", new float[][]{{indent,.5f,-indent,.5f-indent,1},{indent,.5f,indent,.5f+indent,1},{indent,-.5f,indent,.5f+indent,0},{indent,-.5f,-indent,.5f-indent,0}}, 1, 0, 0, overlay); }
                quad(out, pose, "assembler_side_on", new float[][]{{.5005f,.5f,-.5f,0,1},{.5005f,.5f,.5f,1,1},{.5005f,-.5f,.5f,1,0},{.5005f,-.5f,-.5f,0,0}}, 1, 0, 0, overlay);
                pose.mulPose(Axis.YP.rotationDegrees(90));
            }
        } else {
            pose.translate(.5, .5, .5); pose.scale(1.0025f, -1.0025f, 1.0025f); pose.translate(-.5, -.5, -.5);
            quad(out, pose, "disassembler_top_on", new float[][]{{0,0,1,0,1},{1,0,1,1,1},{1,0,0,1,0},{0,0,0,0,0}}, 0, -1, 0, overlay);
            float[][][] faces = {{{1,1,0,0,1},{0,1,0,1,1},{0,0,0,1,0},{1,0,0,0,0}},{{0,1,1,0,1},{1,1,1,1,1},{1,0,1,1,0},{0,0,1,0,0}},{{0,1,0,0,1},{0,1,1,1,1},{0,0,1,1,0},{0,0,0,0,0}},{{1,1,1,0,1},{1,1,0,1,1},{1,0,0,1,0},{1,0,1,0,0}}};
            Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
            for (int index = 0; index < faces.length; index++) { Direction side = directions[index]; quad(out, pose, "disassembler_side_on", faces[index], side.getStepX(), 0, side.getStepZ(), overlay); }
        }
        pose.popPose();
    }
    private static void quad(VertexConsumer out, PoseStack pose, String name, float[][] vertices, float nx, float ny, float nz, int overlay) {
        var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.fromNamespaceAndPath("opencomputers", "blocks/overlay/" + name));
        for (float[] vertex : vertices) out.addVertex(pose.last(), vertex[0], vertex[1], vertex[2]).setColor(-1).setUv(sprite.getU(vertex[3]), sprite.getV(vertex[4])).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), nx, ny, nz);
    }
}
