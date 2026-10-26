package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.common.block.entity.TransposerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** The original six-sided white activity texture fades out over one second. */
public final class TransposerBlockEntityRenderer implements BlockEntityRenderer<TransposerBlockEntity> {
    private static final ResourceLocation ACTIVITY=ResourceLocation.fromNamespaceAndPath("opencomputers","blocks/overlay/transposer_on");
    // Positions and texture coordinates are the upstream quads before the Y inversion.
    private static final float[][][] QUADS={
        {{0,1,0,1,0},{1,1,0,0,0},{1,1,1,0,1},{0,1,1,1,1}},
        {{0,0,0,1,1},{0,0,1,1,0},{1,0,1,0,0},{1,0,0,0,1}},
        {{1,1,0,0,1},{0,1,0,1,1},{0,0,0,1,0},{1,0,0,0,0}},
        {{0,1,1,0,1},{1,1,1,1,1},{1,0,1,1,0},{0,0,1,0,0}},
        {{0,1,0,0,1},{0,1,1,1,1},{0,0,1,1,0},{0,0,0,0,0}},
        {{1,1,1,0,1},{1,1,0,1,1},{1,0,0,1,0},{1,0,1,0,0}}
    };
    private static final float[][] NORMALS={{0,1,0},{0,-1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
    public TransposerBlockEntityRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(TransposerBlockEntity entity,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        double activity=Math.max(0,1-(System.currentTimeMillis()-entity.lastOperation)/1000d);if(activity<=0)return;
        TextureAtlasSprite sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ACTIVITY);var vertices=buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));int alpha=(int)Math.min(255,activity*255);
        pose.pushPose();pose.translate(.5,.5,.5);pose.scale(1.0025f,-1.0025f,1.0025f);pose.translate(-.5,-.5,-.5);
        for(int face=0;face<QUADS.length;face++)for(float[] point:QUADS[face])vertices.addVertex(pose.last(),point[0],point[1],point[2]).setColor(255,255,255,alpha)
            .setUv(sprite.getU0()+(sprite.getU1()-sprite.getU0())*point[3],sprite.getV0()+(sprite.getV1()-sprite.getV0())*point[4])
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),NORMALS[face][0],NORMALS[face][1],NORMALS[face][2]);
        pose.popPose();
    }
    @Override public AABB getRenderBoundingBox(TransposerBlockEntity entity){return new AABB(entity.getBlockPos()).inflate(.002);}
}
