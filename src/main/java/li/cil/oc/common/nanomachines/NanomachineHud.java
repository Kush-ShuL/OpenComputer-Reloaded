package li.cil.oc.common.nanomachines;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
@EventBusSubscriber(modid=OpenComputers.ID,value=Dist.CLIENT)
public final class NanomachineHud {
    private static final ResourceLocation BACKGROUND=ResourceLocation.fromNamespaceAndPath(OpenComputers.ID,"textures/gui/nanomachines_power.png"),BAR=ResourceLocation.fromNamespaceAndPath(OpenComputers.ID,"textures/gui/nanomachines_power_bar.png");
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){var minecraft=Minecraft.getInstance();if(minecraft.player==null||minecraft.options.hideGui)return;var controller=Nanomachines.INSTANCE.getController(minecraft.player);if(controller==null)return;var graphics=event.getGuiGraphics();int width=graphics.guiWidth(),height=graphics.guiHeight();double[] position=Settings.get().nanomachineHudPos;double x=position.length>0?position[0]:-1,y=position.length>1?position[1]:-1;int left=(int)Math.min(width-8,x<0?width/2d-91-12:x<1?width*x:x),top=(int)Math.min(height-12,y<0?height-39:y<1?height*y:y);graphics.blit(BACKGROUND,left,top,0F,0F,8,12,8,12);double ratio=Math.max(0,Math.min(1,controller.getLocalBuffer()/Math.max(1,controller.getLocalBufferSize())));int fill=(int)Math.ceil(12*ratio);if(fill>0)graphics.blit(BAR,left,top+12-fill,0F,12-fill,8,fill,8,12);}
}
