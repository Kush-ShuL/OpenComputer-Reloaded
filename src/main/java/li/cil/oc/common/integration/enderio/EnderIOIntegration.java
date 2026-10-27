package li.cil.oc.common.integration.enderio;

import li.cil.oc.Settings;
import li.cil.oc.common.integration.WrenchTools;
import net.neoforged.fml.ModList;

/**
 * Optional Java migration of the two original EnderIO integration files at
 * MightyPirates/OpenComputers 258943363ea8fc4ef14948e6bc6f67375061580c.
 * Original OC code is MIT; see LICENSE and NOTICE. No EnderIO types are linked.
 */
public final class EnderIOIntegration {
    private static boolean initialized;
    private EnderIOIntegration() {}

    public static boolean available() {
        return ModList.get().isLoaded("enderio") && Settings.get().modBlacklist.stream()
            .noneMatch(id -> id.trim().equalsIgnoreCase("enderio"));
    }

    /** Call from the existing common-setup integration initialization. */
    public static synchronized void initializeDrivers() {
        if (initialized) return;
        initialized = true;
        if (!available()) return;
        WrenchTools.addCheck(EventHandlerEnderIO::isWrench);
        WrenchTools.addUsage(EventHandlerEnderIO::useWrench);
    }
}
