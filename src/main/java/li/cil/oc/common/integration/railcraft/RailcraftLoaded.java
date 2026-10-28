package li.cil.oc.common.integration.railcraft;

import li.cil.oc.api.Driver;
import li.cil.oc.common.integration.WrenchTools;

/** Migration of OC's ModRailcraft at 258943363ea8fc4ef14948e6bc6f67375061580c (MIT). */
final class RailcraftLoaded {
    private RailcraftLoaded() {}

    static void initialize() {
        WrenchTools.addCheck(EventHandlerRailcraft::isWrench);
        WrenchTools.addUsage(EventHandlerRailcraft::useWrench);
        Driver.add(new DriverWorldspike());
    }
}
