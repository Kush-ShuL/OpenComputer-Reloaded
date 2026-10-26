package li.cil.oc.client;

import li.cil.oc.client.gui.ChargerScreen;
import li.cil.oc.client.renderer.ChargerBlockEntityRenderer;
import li.cil.oc.common.init.Charging;
import li.cil.oc.common.menu.ChargerMenu;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ChargingClient {
    private ChargingClient() {}
    public static void register(IEventBus bus) { bus.addListener(ChargingClient::screens); bus.addListener(ChargingClient::renderers); }
    private static void screens(RegisterMenuScreensEvent event) { event.register(ChargerMenu.TYPE.get(), ChargerScreen::new); }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(Charging.TYPE.get(), ChargerBlockEntityRenderer::new); }
}
