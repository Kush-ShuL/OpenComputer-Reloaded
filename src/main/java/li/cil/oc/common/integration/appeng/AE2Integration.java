package li.cil.oc.common.integration.appeng;

import li.cil.oc.Settings;
import net.neoforged.fml.ModList;

/** Optional entry point with no Applied Energistics types in its signatures. */
public final class AE2Integration {
    private static boolean initialized;
    private AE2Integration() {}
    public static boolean available() {
        return ModList.get().isLoaded("ae2") && Settings.get().modBlacklist.stream()
                .noneMatch(id -> id.trim().equalsIgnoreCase("appliedenergistics2") || id.trim().equalsIgnoreCase("ae2"));
    }
    public static synchronized void initializeDrivers() {
        if (initialized || !available()) return;
        AE2Loaded.initialize();
        initialized = true;
    }
}
