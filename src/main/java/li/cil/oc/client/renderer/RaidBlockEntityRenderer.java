package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import li.cil.oc.common.block.entity.RaidBlockEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;

/** Original per-slot missing-disk lights and live I/O flicker. */
public final class RaidBlockEntityRenderer implements BlockEntityRenderer<RaidBlockEntity> {
    private static ResourceLocation texture(String name){return ResourceLocation.fromNamespaceAndPath("opencomputers","textures/blocks/overlay/raid_front_"+name+".png");}
    private static final ResourceLocation ERROR=texture("error"),ACTIVITY=texture("activity");
    public RaidBlockEntityRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(RaidBlockEntity raid,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){pose.pushPose();pose.translate(.5,.5,.5);pose.mulPose(Axis.YP.rotationDegrees(switch(raid.facing()){case WEST->-90;case NORTH->180;case EAST->90;default->0;}));pose.translate(-.5,.5,.505);pose.scale(1,-1,1);for(int slot=0;slot<3;slot++){if(!raid.presence[slot])quad(ERROR,slot,pose,buffers,overlay);if(System.currentTimeMillis()-raid.lastAccess<400&&raid.getLevel()!=null&&raid.getLevel().random.nextDouble()>.1&&slot==Math.floorMod(raid.lastAccess,3))quad(ACTIVITY,slot,pose,buffers,overlay);}pose.popPose();}
    private static void quad(ResourceLocation texture,int slot,PoseStack pose,MultiBufferSource buffers,int overlay){float left=2/16f+slot*4/16f,right=left+4/16f;var out=buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));out.addVertex(pose.last(),left,1,0).setColor(-1).setUv(left,1).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);out.addVertex(pose.last(),right,1,0).setColor(-1).setUv(right,1).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);out.addVertex(pose.last(),right,0,0).setColor(-1).setUv(right,0).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);out.addVertex(pose.last(),left,0,0).setColor(-1).setUv(left,0).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);}
}
