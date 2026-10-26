package li.cil.oc.client;

import com.mojang.math.Axis;
import li.cil.oc.api.event.RackMountableRenderEvent;
import li.cil.oc.client.gui.RackDiskScreen;
import li.cil.oc.common.init.RackDisks;
import li.cil.oc.common.item.RackDiskDriveItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Original mountable render-event extension: front, inserted media model and access LED. */
public final class RackDiskClient {
    private RackDiskClient(){}
    public static void initialize(IEventBus bus){bus.addListener(RackDiskClient::screens);NeoForge.EVENT_BUS.addListener(RackDiskClient::front);NeoForge.EVENT_BUS.addListener(RackDiskClient::contents);}
    private static ResourceLocation sprite(String path){return ResourceLocation.fromNamespaceAndPath("opencomputers","blocks/"+path);}
    private static void screens(RegisterMenuScreensEvent event){event.register(RackDisks.MENU.get(),RackDiskScreen::new);}
    private static boolean isDrive(RackMountableRenderEvent event){return event.rack.getItem(event.mountable).getItem() instanceof RackDiskDriveItem;}
    private static void front(RackMountableRenderEvent.Block event){if(isDrive(event))event.setFrontTextureOverride(Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(sprite("rack_disk_drive")));}
    private static void contents(RackMountableRenderEvent.TileEntity event){
        if(!isDrive(event)||event.data==null)return;
        var level=event.rack.world();ItemStack disk=ItemStack.parseOptional(level.registryAccess(),event.data.getCompound("disk"));
        if(!disk.isEmpty()){
            var pose=event.poseStack;pose.pushPose();pose.scale(1,-1,1);pose.translate(10/16f,-(3.5f+event.mountable*3f)/16f,-2/16f);pose.mulPose(Axis.XP.rotationDegrees(-90));pose.scale(.5f,.5f,.5f);
            var pos=net.minecraft.core.BlockPos.containing(event.rack.xPosition(),event.rack.yPosition(),event.rack.zPosition()).relative(event.rack.facing());
            Minecraft.getInstance().getItemRenderer().renderStatic(disk,ItemDisplayContext.FIXED,LevelRenderer.getLightColor(level,pos),event.packedOverlay,pose,event.buffers,level,0);pose.popPose();
        }
        if(System.currentTimeMillis()-event.data.getLong("lastAccess")<400&&level.random.nextDouble()>.1)event.renderOverlayFromAtlas(sprite("overlay/rack_disk_drive_activity"));
    }
}
