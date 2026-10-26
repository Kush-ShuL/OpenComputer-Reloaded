package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.common.block.entity.GeolyzerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Original permanently illuminated animated scanner surface on the geolyzer's top face. */
public final class GeolyzerBlockEntityRenderer implements BlockEntityRenderer<GeolyzerBlockEntity> {
    private static final ResourceLocation TOP=ResourceLocation.fromNamespaceAndPath("opencomputers","blocks/overlay/geolyzer_top_on");
    public GeolyzerBlockEntityRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(GeolyzerBlockEntity entity,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        TextureAtlasSprite sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(TOP);var vertices=buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));
        pose.pushPose();pose.translate(.5,.5,.5);pose.scale(1.0025f,-1.0025f,1.0025f);pose.translate(-.5,-.5,-.5);
        vertices.addVertex(pose.last(),0,0,1).setColor(-1).setUv(sprite.getU0(),sprite.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,-1,0);
        vertices.addVertex(pose.last(),1,0,1).setColor(-1).setUv(sprite.getU1(),sprite.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,-1,0);
        vertices.addVertex(pose.last(),1,0,0).setColor(-1).setUv(sprite.getU1(),sprite.getV0()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,-1,0);
        vertices.addVertex(pose.last(),0,0,0).setColor(-1).setUv(sprite.getU0(),sprite.getV0()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,-1,0);
        pose.popPose();
    }
    @Override public AABB getRenderBoundingBox(GeolyzerBlockEntity entity){return new AABB(entity.getBlockPos()).inflate(.002);}
}
