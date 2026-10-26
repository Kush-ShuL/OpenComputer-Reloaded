package li.cil.oc.client;

import li.cil.oc.common.init.RaidDevices;
import li.cil.oc.common.menu.RaidMenu;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.*;

public final class RaidClient {
    private RaidClient(){}
    public static void register(IEventBus bus){bus.addListener(RaidClient::screens);bus.addListener(RaidClient::renderers);}
    private static void screens(RegisterMenuScreensEvent event){event.register(RaidMenu.TYPE.get(),li.cil.oc.client.gui.RaidScreen::new);}
    private static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(RaidDevices.TYPE.get(),li.cil.oc.client.renderer.RaidBlockEntityRenderer::new);}
}
