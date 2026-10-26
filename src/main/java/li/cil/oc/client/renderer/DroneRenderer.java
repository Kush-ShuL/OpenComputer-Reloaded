package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import li.cil.oc.common.entity.DroneEntity;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Original ModelQuadcopter's body, flap/pin geometry, UVs and independent flap lights. */
public final class DroneRenderer extends EntityRenderer<DroneEntity> {
    public static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("opencomputers","textures/model/drone.png");
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart[] wings=new ModelPart[4],lights=new ModelPart[4];
    public DroneRenderer(EntityRendererProvider.Context context){super(context);root=createModel();body=root.getChild("body");for(int i=0;i<4;i++){wings[i]=root.getChild("wing"+i);lights[i]=root.getChild("light"+i);}shadowRadius=.3f;}
    public static ModelPart createModel(){MeshDefinition mesh=new MeshDefinition();var root=mesh.getRoot();root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,1).addBox(-3,1,-3,6,1,6).texOffs(0,23).addBox(-1,0,-1,2,1,2).texOffs(0,17).addBox(-2,-1,-2,4,1,4),PartPose.rotation(0,(float)Math.PI/4,0));int[][] xy={{1,-7,2,-3},{1,1,2,2},{-7,1,-3,2},{-7,-7,-3,-3}};for(int i=0;i<4;i++){int[] p=xy[i];root.addOrReplaceChild("wing"+i,CubeListBuilder.create().texOffs(0,9).addBox(p[0],0,p[1],6,1,6).texOffs(0,27).addBox(p[2],-1,p[3],1,3,1),PartPose.ZERO);root.addOrReplaceChild("light"+i,CubeListBuilder.create().texOffs(24,0).addBox(p[0],0,p[1],6,1,6),PartPose.ZERO);}return LayerDefinition.create(mesh,64,32).bakeRoot();}
    @Override public ResourceLocation getTextureLocation(DroneEntity entity){return TEXTURE;}
    @Override public void render(DroneEntity drone,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){li.cil.oc.client.RenderDiagnostics.checkError("DroneRenderer.render.enter");pose.pushPose();pose.translate(0,.125+(drone.running()?Math.sin((drone.tickCount+partial)/20+drone.hashCode())/16:0),0);var velocity=drone.getVelocity();if(velocity.horizontalDistanceSqr()>0){pose.mulPose(Axis.ZP.rotationDegrees((float)(-velocity.x/.4*20)));pose.mulPose(Axis.XP.rotationDegrees((float)(velocity.z/.4*20)));}pose.mulPose(Axis.YP.rotationDegrees(drone.bodyAngle));draw(pose,buffers,light,drone.running(),drone.lightColor(),drone.flapAngles);pose.popPose();super.render(drone,yaw,partial,pose,buffers,light);li.cil.oc.client.RenderDiagnostics.checkError("DroneRenderer.render");}
    public void draw(PoseStack pose,MultiBufferSource buffers,int light,boolean running,int color,float[][] angles){drawModel(root,pose,buffers,light,running,color,angles);}
    public static void drawModel(ModelPart root,PoseStack pose,MultiBufferSource buffers,int light,boolean running,int color,float[][] angles){ModelPart body=root.getChild("body");ModelPart[] wings=new ModelPart[4],lights=new ModelPart[4];for(int i=0;i<4;i++){wings[i]=root.getChild("wing"+i);lights[i]=root.getChild("light"+i);}var normal=buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));body.render(pose,normal,light,OverlayTexture.NO_OVERLAY);for(int i=0;i<4;i++){wings[i].xRot=lights[i].xRot=angles[i][0];wings[i].zRot=lights[i].zRot=angles[i][1];wings[i].render(pose,normal,light,OverlayTexture.NO_OVERLAY);}if(running){var glow=buffers.getBuffer(RenderType.eyes(TEXTURE));for(var part:lights)part.render(pose,glow,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0xFF000000|color);}}
}
