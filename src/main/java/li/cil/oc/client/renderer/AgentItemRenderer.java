package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.common.init.Agents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.*;

public final class AgentItemRenderer extends BlockEntityWithoutLevelRenderer {
    private net.minecraft.client.model.geom.ModelPart drone;
    public AgentItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        pose.pushPose();
        if(stack.is(Agents.ROBOT_ITEM.get()))RobotBlockEntityRenderer.drawChassis(pose,buffers,light,overlay,false,0xF23030,0,null);
        else if(stack.is(Agents.DRONE.get())){
            if(drone==null)drone=DroneRenderer.createModel();
            pose.translate(.5,.5,.5);DroneRenderer.drawModel(drone,pose,buffers,light,true,0x66DD55,new float[][]{{.035f,.035f},{-.035f,.035f},{-.035f,-.035f},{.035f,-.035f}});
        }
        pose.popPose();
    }
}
