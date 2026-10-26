package li.cil.oc.common.integration.mekanism;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.common.integration.WrenchTools;
import net.neoforged.fml.ModList;

/**
 * Optional Mekanism integration, derived from MightyPirates/OpenComputers
 * commit 258943363ea8fc4ef14948e6bc6f67375061580c (MIT; see LICENSE).
 * No Mekanism class is resolved unless its corresponding integration is enabled.
 */
public final class MekanismIntegration {
    private static boolean initialized;

    private MekanismIntegration() {}

    private static boolean enabled(String originalId) {
        return Settings.get().modBlacklist.stream()
            .noneMatch(id -> id.trim().equalsIgnoreCase(originalId));
    }

    /** Call during common setup, after the OpenComputers driver API is initialized. */
    public static synchronized void initializeDrivers() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("mekanism")) return;

        // The original wrench and gas modules have separate blacklist entries.
        if (enabled("mekanism")) {
            WrenchTools.addCheck(EventHandlerMekanism::isWrench);
            WrenchTools.addUsage(EventHandlerMekanism::useWrench);
        }
        if (enabled("MekanismAPI|gas") && enabled("mekanismgas")) {
            try {
                Driver.add(ChemicalStackConverter.create());
            } catch (ReflectiveOperationException | LinkageError exception) {
                OpenComputers.LOGGER.error("Mekanism chemical conversion could not bind its public API; "
                    + "expected the Minecraft 1.21.1 Mekanism 10.7 API", exception);
            }
        }
    }
}
