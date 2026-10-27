package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import li.cil.oc.common.item.UpgradeMFItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Original MFU target outline and selected face, visible through walls while held in the main hand. */
@EventBusSubscriber(modid="opencomputers",value=Dist.CLIENT)
public final class MFUTargetRenderer {
    private static final RenderType OUTLINE=RenderType.create("opencomputers_mfu_outline",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,false,false,
            RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL).setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    private static final RenderType TARGET_FACE=RenderType.create("opencomputers_mfu_face",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,256,false,false,
            RenderType.CompositeState.builder().setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL).setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    private static final float[][][] FACE_VERTICES={
            {{-.1f,-.1f,-.1f},{-.1f,-.1f,1.1f},{1.1f,-.1f,1.1f},{1.1f,-.1f,-.1f}},
            {{1.1f,1.1f,-.1f},{1.1f,1.1f,1.1f},{-.1f,1.1f,1.1f},{-.1f,1.1f,-.1f}},
            {{-.1f,-.1f,-.1f},{1.1f,-.1f,-.1f},{1.1f,1.1f,-.1f},{-.1f,1.1f,-.1f}},
            {{1.1f,1.1f,1.1f},{1.1f,-.1f,1.1f},{-.1f,-.1f,1.1f},{-.1f,1.1f,1.1f}},
            {{-.1f,-.1f,-.1f},{-.1f,1.1f,-.1f},{-.1f,1.1f,1.1f},{-.1f,-.1f,1.1f}},
            {{1.1f,-.1f,-.1f},{1.1f,-.1f,1.1f},{1.1f,1.1f,1.1f},{1.1f,1.1f,-.1f}}
    };
    private MFUTargetRenderer(){}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var minecraft=Minecraft.getInstance();var player=minecraft.player;
        if(player==null||minecraft.level==null)return;
        var stack=player.getMainHandItem();
        if(!(stack.getItem() instanceof UpgradeMFItem))return;
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        int[] coordinates=data.getIntArray("oc:coord");
        if(coordinates.length!=5||!minecraft.level.dimension().equals(UpgradeMFItem.dimension(data)))return;
        int x=coordinates[0],y=coordinates[1],z=coordinates[2];
        if(player.distanceToSqr(x,y,z)>64*64)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        try{
            // AFTER_LEVEL uses an empty pose stack; retain its camera rotation and draw relative to the camera.
            pose.mulPose(event.getModelViewMatrix());pose.translate(x-camera.x,y-camera.y,z-camera.z);
            LevelRenderer.renderLineBox(pose,buffers.getBuffer(OUTLINE),-.1,-.1,-.1,1.1,1.1,1.1,0,1,0,.25f);
            buffers.endBatch(OUTLINE);
            int side=coordinates[4];
            if(side>=0&&side<FACE_VERTICES.length){
                var vertices=buffers.getBuffer(TARGET_FACE);
                for(float[] point:FACE_VERTICES[side])vertices.addVertex(pose.last(),point[0],point[1],point[2]).setColor(0f,1f,0f,.25f);
                buffers.endBatch(TARGET_FACE);
            }
        }finally{pose.popPose();}
    }
}
