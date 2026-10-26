package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.common.block.entity.PowerDistributorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Original animated atlas overlays, with emissive intensity proportional to total network energy. */
public final class PowerDistributorRenderer implements BlockEntityRenderer<PowerDistributorBlockEntity> {
    private static final ResourceLocation TOP = ResourceLocation.fromNamespaceAndPath("opencomputers", "blocks/overlay/powerdistributor_top_on");
    private static final ResourceLocation SIDE = ResourceLocation.fromNamespaceAndPath("opencomputers", "blocks/overlay/powerdistributor_side_on");
    private static final float[][][] FACES = {
            {{0,0,1},{0,0,0},{1,0,0},{1,0,1}}, {{0,1,0},{0,1,1},{1,1,1},{1,1,0}},
            {{1,1,0},{1,0,0},{0,0,0},{0,1,0}}, {{0,1,1},{0,0,1},{1,0,1},{1,1,1}},
            {{0,1,0},{0,0,0},{0,0,1},{0,1,1}}, {{1,1,1},{1,0,1},{1,0,0},{1,1,0}}};
    public PowerDistributorRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(PowerDistributorBlockEntity distributor, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (distributor.globalBuffer() <= 0 || distributor.globalBufferSize() <= 0) return;
        int color = (Math.clamp((int) (255 * distributor.globalBuffer() / distributor.globalBufferSize()), 0, 255) << 24) | 0xFFFFFF;
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite top = atlas.apply(TOP), side = atlas.apply(SIDE);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5); pose.scale(1.0025F, 1.0025F, 1.0025F); pose.translate(-0.5, -0.5, -0.5);
        var out = buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));
        for (Direction face : Direction.values()) {
            if (face == Direction.DOWN) continue;
            var sprite = face == Direction.UP ? top : side;
            for (float[] vertex : FACES[face.get3DDataValue()]) {
                float u = switch (face) { case NORTH -> 1 - vertex[0]; case EAST -> 1 - vertex[2]; case WEST -> vertex[2]; default -> vertex[0]; };
                float v = face == Direction.UP ? vertex[2] : 1 - vertex[1];
                out.addVertex(pose.last(), vertex[0], vertex[1], vertex[2]).setColor(color)
                        .setUv(sprite.getU0() + (sprite.getU1() - sprite.getU0()) * u, sprite.getV0() + (sprite.getV1() - sprite.getV0()) * v)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(pose.last(), face.getStepX(), face.getStepY(), face.getStepZ());
            }
        }
        pose.popPose();
    }
    @Override public AABB getRenderBoundingBox(PowerDistributorBlockEntity entity) { return new AABB(entity.getBlockPos()).inflate(0.002); }
}
