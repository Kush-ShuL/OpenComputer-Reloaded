package li.cil.oc.common.integration.enderstorage;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import net.neoforged.fml.ModList;

/**
 * Optional entry point derived from MightyPirates/OpenComputers, commit
 * 258943363ea8fc4ef14948e6bc6f67375061580c. MIT, see LICENSE and NOTICE.
 * No EnderStorage or CodeChickenLib types occur in its signatures or fields.
 */
public final class EnderStorageIntegration {
    private static boolean initialized;

    private EnderStorageIntegration() {}

    public static boolean available() {
        return Settings.get().modBlacklist.stream().noneMatch(id -> id.trim().equalsIgnoreCase("enderstorage"))
            && ModList.get().isLoaded("enderstorage");
    }

    /** Register during common setup, while the OpenComputers driver registry is open. */
    public static synchronized void initializeDrivers() {
        if (initialized) return;
        initialized = true;
        if (!available()) return;
        try {
            Driver.add(new DriverFrequencyOwner(new EnderStorageAccess()));
        } catch (ReflectiveOperationException | LinkageError exception) {
            OpenComputers.LOGGER.error("EnderStorage frequency integration could not bind its public API; "
                + "expected the Minecraft 1.21.1 EnderStorage 2.13 API", exception);
        }
    }
}
