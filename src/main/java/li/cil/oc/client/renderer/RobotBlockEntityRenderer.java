package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import li.cil.oc.api.driver.item.UpgradeRenderer;
import li.cil.oc.api.event.RobotRenderEvent;
import li.cil.oc.common.block.entity.RobotBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;

/** Original split-pyramid chassis/UVs, activity ring, held tool and extension mount points. */
public final class RobotBlockEntityRenderer implements BlockEntityRenderer<RobotBlockEntity> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("opencomputers","textures/model/robot.png");
    public RobotBlockEntityRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(RobotBlockEntity proxy,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        li.cil.oc.client.RenderDiagnostics.checkError("RobotBlockEntityRenderer.render.enter");
        var robot=proxy.controller();pose.pushPose();
        if(robot.moveFrom!=null&&robot.animationTicks>0){float left=(robot.animationTicks-partial)/Math.max(1,robot.animationTotal);var delta=robot.moveFrom.subtract(proxy.getBlockPos());pose.translate(delta.getX()*left,delta.getY()*left,delta.getZ()*left);}
        pose.pushPose();
        pose.translate(.5,.5+(proxy.clientRunning?Math.sin((proxy.getLevel().getGameTime()+partial)/20+proxy.hashCode())*.03:-.03),.5);
        float turn=robot.animationTicks>0&&(robot.animationKind==3||robot.animationKind==4)?90f*(robot.animationTicks-partial)/Math.max(1,robot.animationTotal)*(robot.animationKind==3?1:-1):0;
        pose.mulPose(Axis.YP.rotationDegrees(-robot.facing().toYRot()+turn));pose.translate(-.5,-.5,-.5);
        drawChassis(pose,buffers,light,overlay,proxy.clientRunning,robot.info.lightColor,proxy.getLevel().getGameTime()+partial,proxy);
        var tool=robot.equipment.getItem(0);
        if(!tool.isEmpty()){pose.pushPose();pose.translate(.5,.35,.95);pose.scale(.5f,.5f,.5f);if(robot.animationKind==1&&robot.animationTicks>0)pose.mulPose(Axis.XP.rotationDegrees((float)Math.sin((robot.animationTicks-partial)/10*Math.PI)*45));Minecraft.getInstance().getItemRenderer().renderStatic(tool,ItemDisplayContext.FIXED,light,overlay,pose,buffers,proxy.getLevel(),0);pose.popPose();}
        pose.popPose();
        li.cil.oc.client.ClientBehaviors.robotLabel(proxy,pose,buffers,light);
        pose.popPose();
        li.cil.oc.client.RenderDiagnostics.checkError("RobotBlockEntityRenderer.render");
    }
    public static void drawChassis(PoseStack pose,MultiBufferSource buffers,int light,int overlay,boolean running,int color,double time,RobotBlockEntity robot){
        String[] names={"top_left","top_right","top_back","bottom_left","bottom_right","bottom_back","bottom_front"};
        RobotRenderEvent.MountPoint[] mounts=new RobotRenderEvent.MountPoint[7];int[] rotations={90,-90,180,90,-90,180,0};
        for(int i=0;i<7;i++){mounts[i]=new RobotRenderEvent.MountPoint(names[i]);mounts[i].offset.set(0,i<3?.2f:-.2f+(running?0:.06f),.24f);mounts[i].rotation.set(0,1,0,rotations[i]);}
        var event=new RobotRenderEvent(robot,mounts,pose,buffers,light,overlay);NeoForge.EVENT_BUS.post(event);
        if(!event.isCanceled()){
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
            Integer petColor=PetRenderer.chassisTint();int chassisColor=petColor==null?BuiltinUpgradeRenderer.chassisColor(robot):petColor;
            float l=.1f,h=.9f,g=1f/28,top=.5f+g-(running?0:4*g),bottom=.5f-g-(running?0:2*g);
            float[][] upper={{l,top,h,0,.5f},{h,top,h,.5f,.5f},{h,top,l,.5f,0},{l,top,l,0,0}};
            float[][] lower={{l,bottom,l,.5f,0},{h,bottom,l,1,0},{h,bottom,h,1,.5f},{l,bottom,h,.5f,.5f}};
            for(int i=0;i<4;i++){
                quad(out,pose,new float[][]{{.5f,1-(running?0:4*g),.5f,.25f,.25f},upper[i],upper[(i+1)%4],upper[(i+1)%4]},chassisColor,light,overlay);
                quad(out,pose,new float[][]{{.5f,.03f-(running?0:2*g),.5f,.75f,.25f},lower[i],lower[(i+1)%4],lower[(i+1)%4]},chassisColor,light,overlay);
            }
            quad(out,pose,new float[][]{{l,top,h,0,1},{l,top,l,0,.5f},{h,top,l,.5f,.5f},{h,top,h,.5f,1}},chassisColor,light,overlay);
            quad(out,pose,new float[][]{{l,bottom,l,0,.5f},{l,bottom,h,0,1},{h,bottom,h,.5f,1},{h,bottom,l,.5f,.5f}},chassisColor,light,overlay);
            if(running){var glow=buffers.getBuffer(RenderType.eyes(TEXTURE));float v=.5f+((int)(time/20*16)&15)/32f,v1=v+1f/32;float a=.2f,b=.8f;float[][] corners={{a,a},{b,a},{b,b},{a,b}};for(int i=0;i<4;i++){var p=corners[i];var q=corners[(i+1)%4];quad(glow,pose,new float[][]{{p[0],top,p[1],.5f,v},{p[0],bottom,p[1],.5f,v1},{q[0],bottom,q[1],1,v1},{q[0],top,q[1],1,v}},0xFF000000|color,LightTexture.FULL_BRIGHT,overlay);}}
        }
        if(robot!=null){Set<String> available=new LinkedHashSet<>(List.of(names));pose.pushPose();pose.translate(.5,.5,.5);for(var slot:robot.controller().componentStacks()){
            var stack=slot.stack();Object candidate=stack.getItem() instanceof UpgradeRenderer?stack.getItem():li.cil.oc.api.Driver.driverFor(stack,robot.controller().getClass());if(!(candidate instanceof UpgradeRenderer))candidate=BuiltinUpgradeRenderer.forStack(stack);if(!(candidate instanceof UpgradeRenderer renderer))continue;
            String wanted=renderer.computePreferredMountPoint(stack,robot,Collections.unmodifiableSet(available));if(wanted==null||wanted.equals("none"))continue;if(wanted.equals("any"))wanted=available.stream().findFirst().orElse(null);if(wanted==null||!available.remove(wanted))continue;for(var mount:mounts)if(wanted.equals(mount.name)){pose.pushPose();renderer.render(stack,mount,robot,(float)(time-Math.floor(time)),pose,buffers,light,overlay);pose.popPose();break;}
        }pose.popPose();}
    }
    private static void quad(VertexConsumer out,PoseStack pose,float[][] vertices,int color,int light,int overlay){var a=vertices[0];var b=vertices[1];var c=vertices[2];var normal=new org.joml.Vector3f(b[0]-a[0],b[1]-a[1],b[2]-a[2]).cross(c[0]-a[0],c[1]-a[1],c[2]-a[2]);if(normal.lengthSquared()>0)normal.normalize();else normal.set(0,1,0);for(float[] vertex:vertices)out.addVertex(pose.last(),vertex[0],vertex[1],vertex[2]).setColor(color).setUv(vertex[3],vertex[4]).setOverlay(overlay).setLight(light).setNormal(pose.last(),normal.x,normal.y,normal.z);}
    @Override public boolean shouldRenderOffScreen(RobotBlockEntity entity){return entity.controller().moveFrom!=null;}
}
