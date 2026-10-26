package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import li.cil.oc.common.block.entity.ChargerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** Atlas sprites preserve the original animated side overlay and bottom-up speed indicator. */
public final class ChargerBlockEntityRenderer implements BlockEntityRenderer<ChargerBlockEntity> {
    private static final ResourceLocation FRONT = ResourceLocation.fromNamespaceAndPath("opencomputers", "blocks/overlay/charger_front_on"), SIDE = ResourceLocation.fromNamespaceAndPath("opencomputers", "blocks/overlay/charger_side_on");
    public ChargerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(ChargerBlockEntity charger, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (charger.chargeSpeed <= 0) return;
        pose.pushPose(); pose.translate(.5, .5, .5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (charger.facing()) { case SOUTH -> 180; case WEST -> 90; case EAST -> -90; default -> 0; }));
        pose.translate(-.5, -.5, -.5);
        var vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FRONT);
        float height = (float) charger.chargeSpeed;
        quad(vertices, pose, sprite, overlay, new float[][]{{1,0,-.002f,0,1},{0,0,-.002f,1,1},{0,height,-.002f,1,1-height},{1,height,-.002f,0,1-height}}, 0, 0, -1);
        if (charger.hasPower) {
            sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(SIDE);
            quad(vertices, pose, sprite, overlay, new float[][]{{0,0,1.002f,0,1},{1,0,1.002f,1,1},{1,1,1.002f,1,0},{0,1,1.002f,0,0}}, 0, 0, 1);
            quad(vertices, pose, sprite, overlay, new float[][]{{-.002f,0,0,0,1},{-.002f,0,1,1,1},{-.002f,1,1,1,0},{-.002f,1,0,0,0}}, -1, 0, 0);
            quad(vertices, pose, sprite, overlay, new float[][]{{1.002f,0,1,0,1},{1.002f,0,0,1,1},{1.002f,1,0,1,0},{1.002f,1,1,0,0}}, 1, 0, 0);
        }
        pose.popPose();
    }
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer out, PoseStack pose, TextureAtlasSprite sprite, int overlay, float[][] points, int nx, int ny, int nz) {
        for (float[] point : points) out.addVertex(pose.last(), point[0], point[1], point[2]).setColor(-1).setUv(sprite.getU(point[3]), sprite.getV(point[4])).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), nx, ny, nz);
    }
}
