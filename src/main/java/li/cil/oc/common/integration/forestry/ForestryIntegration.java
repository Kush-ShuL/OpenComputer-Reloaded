package li.cil.oc.common.integration.forestry;

import li.cil.oc.Settings;
import net.neoforged.fml.ModList;

/** Optional entry point; Forestry classes are loaded only after the mod/blacklist gate. */
public final class ForestryIntegration {
    private static boolean initialized;
    private ForestryIntegration() {}

    public static boolean available() {
        return ModList.get().isLoaded("forestry") && Settings.get().modBlacklist.stream()
            .noneMatch(id -> id.trim().equalsIgnoreCase("forestry"));
    }

    public static synchronized void initializeDrivers() {
        if (initialized || !available()) return;
        ForestryLoaded.initialize();
        initialized = true;
    }
}
