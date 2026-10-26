package li.cil.oc.client;

import li.cil.oc.OpenComputers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Clear only the client snapshot; integrated-server registrations have independent ownership. */
@EventBusSubscriber(modid = OpenComputers.ID, value = Dist.CLIENT)
public final class LootClientEvents {
    private LootClientEvents() {}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        LootClientLifecycle.logout(event.getPlayer());
    }
}
