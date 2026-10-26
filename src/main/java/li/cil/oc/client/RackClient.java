package li.cil.oc.client;

import li.cil.oc.client.gui.RackScreen;
import li.cil.oc.client.gui.ServerScreen;
import li.cil.oc.client.gui.TerminalScreen;
import li.cil.oc.client.renderer.RackBlockEntityRenderer;
import li.cil.oc.common.init.Racks;
import li.cil.oc.common.network.RackNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class RackClient {
    private RackClient(){}
    public static void initialize(IEventBus bus){bus.addListener(RackClient::menus);bus.addListener(RackClient::renderers);bus.addListener(RackClient::setup);}
    private static void menus(RegisterMenuScreensEvent event){event.register(Racks.RACK_MENU.get(),RackScreen::new);event.register(Racks.SERVER_MENU.get(),ServerScreen::new);}
    private static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(Racks.RACK_TYPE.get(),RackBlockEntityRenderer::new);}
    private static void setup(FMLClientSetupEvent event){event.enqueueWork(()->ItemProperties.register(Racks.TERMINAL.get(),ResourceLocation.fromNamespaceAndPath("opencomputers","running"),(stack,level,entity,seed)->stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("oc:server").isEmpty()?0:1));}
    public static void open(RackNetworking.TerminalOpen packet){var minecraft=Minecraft.getInstance();if(minecraft.level!=null&&minecraft.level.dimension().location().equals(packet.dimension()))minecraft.setScreen(new TerminalScreen(packet));}
    public static void update(RackNetworking.TerminalUpdate packet){if(Minecraft.getInstance().screen instanceof TerminalScreen screen)screen.update(packet);}
    public static void requestTerminal(String server){if(Minecraft.getInstance().screen instanceof TerminalScreen screen&&screen.serverAddress.equals(server))screen.request();}
    public static void sendTerminalInput(String server,String type,Object... payload){if(Minecraft.getInstance().screen instanceof TerminalScreen screen&&screen.serverAddress.equals(server))screen.forwardInput(type,payload);}
}
