package li.cil.oc.common.integration.railcraft;

import li.cil.oc.Settings;
import net.neoforged.fml.ModList;

/** Optional entry point; Railcraft types are resolved only after the mod/blacklist gate. */
public final class RailcraftIntegration {
    private static boolean initialized;
    private RailcraftIntegration() {}

    public static boolean available() {
        return ModList.get().isLoaded("railcraft") && Settings.get().modBlacklist.stream()
            .noneMatch(id -> id.trim().equalsIgnoreCase("railcraft"));
    }

    public static synchronized void initializeDrivers() {
        if (initialized || !available()) return;
        RailcraftLoaded.initialize();
        initialized = true;
    }
}
