package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import li.cil.oc.api.event.RackMountableRenderEvent;
import li.cil.oc.common.block.entity.RackBlockEntity;
import li.cil.oc.common.init.Racks;
import li.cil.oc.common.item.ServerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;

/** Original bay boxes and independently extensible front textures/LED overlays. */
public final class RackBlockEntityRenderer implements BlockEntityRenderer<RackBlockEntity> {
    public RackBlockEntityRenderer(BlockEntityRendererProvider.Context context){}
    private static ResourceLocation sprite(String name){return ResourceLocation.fromNamespaceAndPath("opencomputers","blocks/"+name);}
    private static TextureAtlasSprite sprite(ResourceLocation name){return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(name);}
    @Override public void render(RackBlockEntity rack,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        pose.pushPose();pose.translate(.5,.5,.5);pose.mulPose(Axis.YP.rotationDegrees(switch(rack.facing()){case WEST->-90;case NORTH->180;case EAST->90;default->0;}));pose.translate(-.5,-.5,-.5);
        TextureAtlasSprite top=sprite(sprite("generic_top"));
        for(int slot=0;slot<4;slot++)if(!rack.getItem(slot).isEmpty()){
            var data=rack.getMountableData(slot);var item=rack.getItem(slot);
            String front=item.getItem() instanceof ServerItem?"rack_server":item.is(Racks.TERMINAL_SERVER.get())?"rack_terminal_server":"rack_front";
            var event=new RackMountableRenderEvent.Block(rack,slot,data,rack.facing());NeoForge.EVENT_BUS.post(event);
            if(!event.isCanceled()){
                TextureAtlasSprite side=event.getFrontTextureOverride()==null?sprite(sprite(front)):event.getFrontTextureOverride();
                box(pose,buffers,top,side,.5f/16,(11-slot*3)/16f,.5f/16,15.5f/16,(14-slot*3)/16f,15.5f/16,light,overlay);
            }
            pose.pushPose();pose.translate(0,1,.505f-.5f/16+.5f);pose.scale(1,-1,1);
            var dynamic=new RackMountableRenderEvent.TileEntity(rack,slot,data,(2+slot*3)/16f,(5+slot*3)/16f,pose,buffers,LightTexture.FULL_BRIGHT,overlay);
            if(data!=null&&item.getItem() instanceof ServerItem){
                if(data.getBoolean("isRunning"))dynamic.renderOverlayFromAtlas(sprite("overlay/rack_server_on"));
                if(data.getBoolean("hasErrored")&&Math.floorMod(System.currentTimeMillis()+rack.hashCode()*(slot+1),1000)<500)dynamic.renderOverlayFromAtlas(sprite("overlay/rack_server_error"));
                if(System.currentTimeMillis()-data.getLong("lastFileSystemAccess")<400&&rack.getLevel()!=null&&rack.getLevel().random.nextDouble()>.1)dynamic.renderOverlayFromAtlas(sprite("overlay/rack_server_activity"));
                if(data.getBoolean("isRunning")&&System.currentTimeMillis()-data.getLong("lastNetworkActivity")<300&&System.currentTimeMillis()%200>100)dynamic.renderOverlayFromAtlas(sprite("overlay/rack_server_network_activity"));
            }else if(data!=null&&item.is(Racks.TERMINAL_SERVER.get())){
                dynamic.renderOverlayFromAtlas(sprite("overlay/rack_terminal_server_on"));int count=Math.min(4,data.getList("keys",net.minecraft.nbt.Tag.TAG_STRING).size());if(count>0)dynamic.renderOverlayFromAtlas(sprite("overlay/rack_terminal_server_presence"),7/16f,(6+count*2)/16f);
            }
            NeoForge.EVENT_BUS.post(dynamic);pose.popPose();
        }
        pose.popPose();
    }
    private static void box(PoseStack pose,MultiBufferSource buffers,TextureAtlasSprite top,TextureAtlasSprite side,float x0,float y0,float z0,float x1,float y1,float z1,int light,int overlay){
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        face(out,pose,top,Direction.DOWN,new float[][]{{x0,y0,z0},{x1,y0,z0},{x1,y0,z1},{x0,y0,z1}},light,overlay);
        face(out,pose,top,Direction.UP,new float[][]{{x0,y1,z1},{x1,y1,z1},{x1,y1,z0},{x0,y1,z0}},light,overlay);
        face(out,pose,side,Direction.NORTH,new float[][]{{x1,y0,z0},{x0,y0,z0},{x0,y1,z0},{x1,y1,z0}},light,overlay);
        face(out,pose,side,Direction.SOUTH,new float[][]{{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}},light,overlay);
        face(out,pose,side,Direction.WEST,new float[][]{{x0,y0,z0},{x0,y0,z1},{x0,y1,z1},{x0,y1,z0}},light,overlay);
        face(out,pose,side,Direction.EAST,new float[][]{{x1,y0,z1},{x1,y0,z0},{x1,y1,z0},{x1,y1,z1}},light,overlay);
    }
    private static void face(VertexConsumer out,PoseStack pose,TextureAtlasSprite sprite,Direction side,float[][] vertices,int light,int overlay){for(float[] point:vertices){float u=side.getAxis()==Direction.Axis.X?(side==Direction.WEST?point[2]:1-point[2]):side==Direction.NORTH?1-point[0]:point[0];float v=side.getAxis()==Direction.Axis.Y?point[2]:1-point[1];out.addVertex(pose.last(),point[0],point[1],point[2]).setColor(-1).setUv(sprite.getU0()+(sprite.getU1()-sprite.getU0())*u,sprite.getV0()+(sprite.getV1()-sprite.getV0())*v).setOverlay(overlay).setLight(light).setNormal(pose.last(),side.getStepX(),side.getStepY(),side.getStepZ());}}
}
