package li.cil.oc.client;

import li.cil.oc.common.menu.TabletMenu;
import li.cil.oc.common.menu.MicrocontrollerMenu;
import li.cil.oc.common.network.PortableNetworking;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class PortableClient {
    private PortableClient(){}
    public static void register(IEventBus bus){bus.addListener(PortableClient::screens);bus.addListener(PortableClient::renderers);}
    private static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(li.cil.oc.common.init.PortableDevices.MICROCONTROLLER_TYPE.get(),li.cil.oc.client.renderer.MicrocontrollerBlockEntityRenderer::new);}
    private static void screens(RegisterMenuScreensEvent event){event.register(TabletMenu.TYPE.get(),li.cil.oc.client.gui.TabletInventoryScreen::new);event.register(MicrocontrollerMenu.TYPE.get(),li.cil.oc.client.gui.MicrocontrollerScreen::new);}
    public static void open(PortableNetworking.TabletOpen packet){Minecraft.getInstance().setScreen(new li.cil.oc.client.gui.TabletScreen(packet));}
    public static void update(PortableNetworking.TabletUpdate packet){if(Minecraft.getInstance().screen instanceof li.cil.oc.client.gui.TabletScreen screen)screen.update(packet);}
}
