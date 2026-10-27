package li.cil.oc.common.integration.projectred;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.common.integration.WrenchTools;
import li.cil.oc.common.redstone.BundledRedstone;
import net.neoforged.fml.ModList;

/**
 * Java migration of MightyPirates/OpenComputers ProjectRed integration at
 * 258943363ea8fc4ef14948e6bc6f67375061580c. Copyright (c) 2013-2015
 * Florian "Sangar" Nücke and contributors; MIT, see LICENSE and NOTICE.
 * Foreign API classes are resolved only after the optional-mod/configuration gate.
 */
public final class ProjectRedIntegration {
    private static boolean initialized;

    private ProjectRedIntegration() {}

    public static boolean available() {
        return ModList.get().isLoaded("projectred_transmission") && Settings.get().modBlacklist.stream()
            .noneMatch(id -> switch (id.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "projectred", "projectred-transmission", "projectred_transmission",
                     "projectred-core", "projectred_core" -> true;
                default -> false;
            });
    }

    /** Call from enqueued common setup after OC's own redstone/tool APIs exist. */
    public static synchronized void initializeDrivers() {
        if (initialized) return;
        initialized = true;
        if (!available()) return;
        try {
            ProjectRedAccess access = new ProjectRedAccess();
            ProjectRedRedstone redstone = new ProjectRedRedstone(access);
            access.register(redstone.interaction());
            EventHandlerProjectRed.initialize(access);
            BundledRedstone.addProvider(redstone);
            WrenchTools.addCheck(EventHandlerProjectRed::isWrench);
            WrenchTools.addUsage(EventHandlerProjectRed::useWrench);
        } catch (ReflectiveOperationException | LinkageError exception) {
            OpenComputers.LOGGER.error("ProjectRed bundled/tool integration could not bind its public API; "
                + "expected the Minecraft 1.21.1 ProjectRed 4.23 API", exception);
        }
    }
}
