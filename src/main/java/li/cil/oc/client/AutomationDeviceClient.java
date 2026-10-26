package li.cil.oc.client;

import li.cil.oc.client.renderer.GeolyzerBlockEntityRenderer;
import li.cil.oc.client.renderer.TransposerBlockEntityRenderer;
import li.cil.oc.common.init.AutomationDevices;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client registration is reached only through AutomationDevices on the physical client. */
public final class AutomationDeviceClient {
    private AutomationDeviceClient(){}
    public static void register(IEventBus bus){bus.addListener(AutomationDeviceClient::renderers);}
    private static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(AutomationDevices.TRANSPOSER_TYPE.get(),TransposerBlockEntityRenderer::new);event.registerBlockEntityRenderer(AutomationDevices.GEOLYZER_TYPE.get(),GeolyzerBlockEntityRenderer::new);}
}
