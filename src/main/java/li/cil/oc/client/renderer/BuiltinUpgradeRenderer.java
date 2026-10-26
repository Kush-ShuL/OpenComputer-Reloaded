package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.*;
import li.cil.oc.api.driver.item.UpgradeRenderer;
import li.cil.oc.api.event.RobotRenderEvent;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.common.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.joml.Quaternionf;
import java.util.Set;

/** Original three external upgrade models, isolated to the physical client. */
public final class BuiltinUpgradeRenderer implements UpgradeRenderer {
    private static final BuiltinUpgradeRenderer INSTANCE=new BuiltinUpgradeRenderer();
    private BuiltinUpgradeRenderer(){}
    public static UpgradeRenderer forStack(ItemStack stack){return inventory(stack)||stack.getItem() instanceof FunctionalUpgradeItem item&&(item.kind==FunctionalUpgradeItem.Kind.CRAFTING||item.kind==FunctionalUpgradeItem.Kind.GENERATOR)?INSTANCE:null;}
    private static boolean inventory(ItemStack stack){return stack.getItem() instanceof AgentUpgradeItem item&&item.kind.equals("inventory");}
    public static int chassisColor(li.cil.oc.common.block.entity.RobotBlockEntity robot){int level=0;if(robot!=null)for(var slot:robot.controller().componentStacks())if(slot.stack().getItem() instanceof FunctionalUpgradeItem item&&item.kind==FunctionalUpgradeItem.Kind.EXPERIENCE){double xp=slot.stack().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data").getDouble("oc:xp");level+=Math.max(0,Math.min(30,(int)(Math.pow(Math.max(0,xp-li.cil.oc.Settings.get().baseXpToLevel),1/li.cil.oc.Settings.get().exponentialXpGrowth)/li.cil.oc.Settings.get().constantXpGrowth)));}return level>19?0xff66ffff:level>9?0xffffff66:0xff808080;}
    @Override public String computePreferredMountPoint(ItemStack stack,Robot robot,Set<String> available){if(inventory(stack)&&available.contains(MountPointName.TopBack))return MountPointName.TopBack;if(stack.getItem() instanceof FunctionalUpgradeItem item&&item.kind==FunctionalUpgradeItem.Kind.GENERATOR&&available.contains(MountPointName.BottomBack))return MountPointName.BottomBack;return MountPointName.Any;}
    @Override public void render(ItemStack stack,RobotRenderEvent.MountPoint mount,Robot robot,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        String name=inventory(stack)?"inventory":((FunctionalUpgradeItem)stack.getItem()).kind==FunctionalUpgradeItem.Kind.CRAFTING?"crafting":"generator";float front=name.equals("generator")&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data").getInt("remainingTicks")>0?.5f:0;
        pose.mulPose(new Quaternionf().rotationAxis((float)Math.toRadians(mount.rotation.w),mount.rotation.x,mount.rotation.y,mount.rotation.z));pose.translate(mount.offset.x,mount.offset.y,mount.offset.z);var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ResourceLocation.fromNamespaceAndPath("opencomputers","textures/model/"+name+"_upgrade.png")));
        float a=-.1f,b=.1f;
        face(out,pose,new float[][]{{a,a,b,front,.5f},{b,a,b,front+.5f,.5f},{b,b,b,front+.5f,0},{a,b,b,front,0}},0,0,1,light,overlay);
        face(out,pose,new float[][]{{b,b,b,1,.5f},{b,b,a,1,1},{a,b,a,.5f,1},{a,b,b,.5f,.5f}},0,1,0,light,overlay);
        face(out,pose,new float[][]{{a,a,b,.5f,.5f},{a,a,a,.5f,1},{b,a,a,1,1},{b,a,b,1,.5f}},0,-1,0,light,overlay);
        face(out,pose,new float[][]{{b,b,b,0,.5f},{b,a,b,0,1},{b,a,a,.5f,1},{b,b,a,.5f,.5f}},1,0,0,light,overlay);
        face(out,pose,new float[][]{{a,a,b,0,1},{a,b,b,0,.5f},{a,b,a,.5f,.5f},{a,a,a,.5f,1}},-1,0,0,light,overlay);
        face(out,pose,new float[][]{{a,a,a,.5f,1},{a,b,a,.5f,.5f},{b,b,a,1,.5f},{b,a,a,1,1}},0,0,-1,light,overlay);
    }
    private static void face(VertexConsumer out,PoseStack pose,float[][] vertices,float nx,float ny,float nz,int light,int overlay){for(float[] vertex:vertices)out.addVertex(pose.last(),vertex[0],vertex[1],vertex[2]).setColor(-1).setUv(vertex[3],vertex[4]).setOverlay(overlay).setLight(light).setNormal(pose.last(),nx,ny,nz);}
}
