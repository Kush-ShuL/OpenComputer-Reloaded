package li.cil.oc.common.integration.tis3d;

import li.cil.oc.Settings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

/** Optional entrypoint with no TIS-3D or MarkdownManual classes in its signatures or fields. */
public final class Tis3dIntegration {
    private static boolean registered;
    private Tis3dIntegration() {}

    public static boolean available() {
        return ModList.get().isLoaded("tis3d") && Settings.get().modBlacklist.stream()
                .noneMatch(id -> id.trim().equalsIgnoreCase("tis3d"));
    }

    /** Attach during OC construction, before registry events, after loading the OC configuration. */
    public static synchronized void register(IEventBus modBus) {
        if (registered || !available()) return;
        registered = true;
        Tis3dRegistration.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) Tis3dClient.register(modBus);
    }
}
