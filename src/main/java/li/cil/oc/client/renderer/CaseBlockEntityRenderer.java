package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import li.cil.oc.common.block.CaseBlock;
import li.cil.oc.common.block.entity.CaseBlockEntity;
import li.cil.oc.common.init.Blocks;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Original white, full-bright power/disk/error overlays, separate from case tint. */
@EventBusSubscriber(modid="opencomputers",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class CaseBlockEntityRenderer implements BlockEntityRenderer<CaseBlockEntity> {
    private static ResourceLocation overlay(String name){return ResourceLocation.fromNamespaceAndPath("opencomputers","textures/blocks/overlay/case_front_"+name+".png");}
    private static final ResourceLocation ON=overlay("on"),ACTIVITY=overlay("activity"),ERROR=overlay("error");
    public CaseBlockEntityRenderer(BlockEntityRendererProvider.Context context){}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){for(var type:Blocks.CASE_TYPES)event.registerBlockEntityRenderer(type.get(),CaseBlockEntityRenderer::new);}
    @Override public void render(CaseBlockEntity computer,float partial,PoseStack pose,MultiBufferSource buffers,int light,int packedOverlay){
        boolean running=computer.getBlockState().getValue(CaseBlock.RUNNING);
        if(!running&&!computer.hasErrored)return;
        pose.pushPose();pose.translate(.5,.5,.5);
        float rotation=switch(computer.facing()){case WEST->-90;case NORTH->180;case EAST->90;default->0;};
        pose.mulPose(Axis.YP.rotationDegrees(rotation));pose.translate(-.5,.5,.505);pose.scale(1,-1,1);
        if(running){
            quad(ON,pose,buffers,packedOverlay);
            long age=System.currentTimeMillis()-computer.lastFileSystemAccess;
            if(age<400&&computer.getLevel()!=null&&computer.getLevel().random.nextDouble()>.1)quad(ACTIVITY,pose,buffers,packedOverlay);
        }else if(Math.floorMod(System.currentTimeMillis()+computer.hashCode(),1000)<500)quad(ERROR,pose,buffers,packedOverlay);
        pose.popPose();
    }
    private static void quad(ResourceLocation texture,PoseStack pose,MultiBufferSource buffers,int overlay){
        var out=buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        out.addVertex(pose.last(),0,1,0).setColor(-1).setUv(0,1).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);
        out.addVertex(pose.last(),1,1,0).setColor(-1).setUv(1,1).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);
        out.addVertex(pose.last(),1,0,0).setColor(-1).setUv(1,0).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);
        out.addVertex(pose.last(),0,0,0).setColor(-1).setUv(0,0).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);
    }
}
