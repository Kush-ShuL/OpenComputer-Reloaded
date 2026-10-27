package li.cil.oc.common.integration.tis3d;

import li.cil.oc.OpenComputers;
import li.cil.tis3d.api.serial.SerialInterfaceProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The original SerialAPI provider registration, expressed through the current public registry. */
final class Tis3dRegistration {
    private static final DeferredRegister<SerialInterfaceProvider> PROVIDERS = DeferredRegister.create(SerialInterfaceProvider.REGISTRY, OpenComputers.ID);
    static { PROVIDERS.register("adapter", SerialInterfaceProviderAdapter::new); }
    private Tis3dRegistration() {}
    static void register(IEventBus modBus) { PROVIDERS.register(modBus); }
}
