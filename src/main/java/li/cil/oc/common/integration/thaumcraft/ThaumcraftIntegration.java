package li.cil.oc.common.integration.thaumcraft;

import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import net.neoforged.fml.ModList;

/** Optional original Thaumcraft item-data converter; no foreign API classes are required. */
public final class ThaumcraftIntegration {
    private static boolean initialized;
    private ThaumcraftIntegration() {}

    public static boolean available() {
        return ModList.get().isLoaded("thaumcraft") && Settings.get().modBlacklist.stream()
            .noneMatch(id -> id.trim().equalsIgnoreCase("thaumcraft"));
    }

    public static synchronized void initializeDrivers() {
        if (initialized || !available()) return;
        Driver.add(new ConverterThaumcraftItems());
        initialized = true;
    }
}
