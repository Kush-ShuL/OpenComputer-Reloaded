package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import li.cil.oc.common.block.entity.MicrocontrollerBlockEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;

/** Original always-lit face, running light and blinking error light. */
public final class MicrocontrollerBlockEntityRenderer implements BlockEntityRenderer<MicrocontrollerBlockEntity> {
    private static ResourceLocation overlay(String name){return ResourceLocation.fromNamespaceAndPath("opencomputers","textures/blocks/overlay/microcontroller_front_"+name+".png");}
    private static final ResourceLocation LIGHT=overlay("light"),ON=overlay("on"),ERROR=overlay("error");
    public MicrocontrollerBlockEntityRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(MicrocontrollerBlockEntity host,float partial,PoseStack pose,MultiBufferSource buffers,int light,int packedOverlay){pose.pushPose();pose.translate(.5,.5,.5);float rotation=switch(host.facing()){case WEST->-90;case NORTH->180;case EAST->90;default->0;};pose.mulPose(Axis.YP.rotationDegrees(rotation));pose.translate(-.5,.5,.505);pose.scale(1,-1,1);quad(LIGHT,pose,buffers,packedOverlay);if(host.getBlockState().getValue(li.cil.oc.common.block.MicrocontrollerBlock.RUNNING))quad(ON,pose,buffers,packedOverlay);else if(host.getBlockState().getValue(li.cil.oc.common.block.MicrocontrollerBlock.ERROR)&&Math.floorMod(System.currentTimeMillis()+host.hashCode(),1000)<500)quad(ERROR,pose,buffers,packedOverlay);pose.popPose();}
    private static void quad(ResourceLocation texture,PoseStack pose,MultiBufferSource buffers,int overlay){var out=buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));out.addVertex(pose.last(),0,1,0).setColor(-1).setUv(0,1).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);out.addVertex(pose.last(),1,1,0).setColor(-1).setUv(1,1).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);out.addVertex(pose.last(),1,0,0).setColor(-1).setUv(1,0).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);out.addVertex(pose.last(),0,0,0).setColor(-1).setUv(0,0).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);}
}
