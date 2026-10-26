package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.common.block.entity.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Original side LEDs: fading relay traffic and effective splitter-side state. */
public final class NetworkDeviceRenderer {
    private static final ResourceLocation RELAY=texture("switch_side_on"),SPLITTER=texture("netsplitter_on");
    private static ResourceLocation texture(String name){return ResourceLocation.fromNamespaceAndPath("opencomputers","textures/blocks/overlay/"+name+".png");}
    private NetworkDeviceRenderer(){}
    private static void face(PoseStack pose,MultiBufferSource buffers,ResourceLocation texture,Direction side,float alpha,int overlay){
        float[][] vertices=switch(side){
            case DOWN->new float[][]{{0,1,0,1,0},{1,1,0,0,0},{1,1,1,0,1},{0,1,1,1,1}};
            case UP->new float[][]{{0,0,0,1,1},{0,0,1,1,0},{1,0,1,0,0},{1,0,0,0,1}};
            case NORTH->new float[][]{{1,1,0,0,1},{0,1,0,1,1},{0,0,0,1,0},{1,0,0,0,0}};
            case SOUTH->new float[][]{{0,1,1,0,1},{1,1,1,1,1},{1,0,1,1,0},{0,0,1,0,0}};
            case WEST->new float[][]{{0,1,0,0,1},{0,1,1,1,1},{0,0,1,1,0},{0,0,0,0,0}};
            case EAST->new float[][]{{1,1,1,0,1},{1,1,0,1,1},{1,0,0,1,0},{1,0,1,0,0}};
        };
        var out=buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        for(float[] vertex:vertices)out.addVertex(pose.last(),vertex[0],vertex[1],vertex[2]).setColor(1f,1f,1f,alpha).setUv(vertex[3],vertex[4]).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),side.getStepX(),-side.getStepY(),side.getStepZ());
    }
    private static void begin(PoseStack pose){pose.pushPose();pose.translate(.5,.5,.5);pose.scale(1.0025f,-1.0025f,1.0025f);pose.translate(-.5,-.5,-.5);}
    public static final class Relay implements BlockEntityRenderer<RelayBlockEntity>{
        public Relay(BlockEntityRendererProvider.Context context){}
        @Override public void render(RelayBlockEntity relay,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){float alpha=(float)Math.max(0,1-(System.currentTimeMillis()-relay.lastMessage)/1000.0);if(alpha<=0)return;begin(pose);for(Direction side:Direction.Plane.HORIZONTAL)face(pose,buffers,RELAY,side,alpha,overlay);pose.popPose();}
    }
    public static final class Splitter implements BlockEntityRenderer<NetSplitterBlockEntity>{
        public Splitter(BlockEntityRendererProvider.Context context){}
        @Override public void render(NetSplitterBlockEntity splitter,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){if(splitter.sideMask()==0)return;begin(pose);for(Direction side:Direction.values())if(splitter.isSideOpen(side))face(pose,buffers,SPLITTER,side,1,overlay);pose.popPose();}
    }
}
