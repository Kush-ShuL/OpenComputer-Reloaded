package li.cil.oc.client;

import li.cil.oc.api.API;
import li.cil.oc.client.manual.ManualRegistry;

/** Called from the client branch of the mod constructor, before addon setup events. */
public final class ClientBootstrap {
    private ClientBootstrap() {}

    public static void initialize() {
        API.manual = ManualRegistry.INSTANCE;
    }
}
