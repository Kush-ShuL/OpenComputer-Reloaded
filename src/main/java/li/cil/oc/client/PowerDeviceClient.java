package li.cil.oc.client;

import li.cil.oc.OpenComputers;
import li.cil.oc.client.renderer.PowerDistributorRenderer;
import li.cil.oc.common.init.PowerDevices;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = OpenComputers.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PowerDeviceClient {
    private PowerDeviceClient() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PowerDevices.POWER_DISTRIBUTOR_TYPE.get(), PowerDistributorRenderer::new);
    }
}
