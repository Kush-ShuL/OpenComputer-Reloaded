package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import li.cil.oc.client.RenderDiagnostics;
import li.cil.oc.common.command.OriginalCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Original depth-independent, level-colored spatial-tree wireframes for integrated-server debugging. */
public final class WirelessNetworkDebugRenderer {
    private static final int[] COLORS={0xff0000,0x00ffff,0x00ff00,0x0000ff,0xff00ff,0xffff00,0xffffff,0x000000};
    private static final RenderType LINES=RenderType.create("opencomputers_wireless_debug",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,false,false,
            RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL).setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    private WirelessNetworkDebugRenderer(){}
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||!OriginalCommands.wirelessRendering())return;
        var minecraft=Minecraft.getInstance();
        if(minecraft.level==null||!minecraft.hasSingleplayerServer())return;
        var bounds=OriginalCommands.wirelessBounds(minecraft.level.dimension());
        if(bounds.isEmpty())return;
        RenderDiagnostics.checkError("wireless network debug: entering");
        var pose=event.getPoseStack();
        var camera=event.getCamera().getPosition();
        var buffers=minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        try{
            // AFTER_LEVEL supplies an empty pose stack; its model-view matrix contains the camera rotation.
            pose.mulPose(event.getModelViewMatrix());
            pose.translate(-camera.x,-camera.y,-camera.z);
            var consumer=buffers.getBuffer(LINES);
            for(var bound:bounds){
                int color=COLORS[bound.depth()%COLORS.length];
                double size=.5-bound.depth()*.05;
                LevelRenderer.renderLineBox(pose,consumer,bound.minX()-size,bound.minY()-size,bound.minZ()-size,
                        bound.maxX()+size,bound.maxY()+size,bound.maxZ()+size,
                        ((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,.25f);
            }
            buffers.endBatch(LINES);
        }finally{pose.popPose();}
        RenderDiagnostics.checkError("wireless network debug: leaving");
    }
}
