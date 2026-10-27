package li.cil.oc.common.integration.forestry;

import li.cil.oc.api.Driver;

/** Java migration of the original ModForestry at OC commit 258943363ea8fc4ef14948e6bc6f67375061580c (MIT). */
final class ForestryLoaded {
    private ForestryLoaded() {}

    static void initialize() {
        Driver.add(new ConverterIAlleles());
        Driver.add(new ConverterIIndividual());
        Driver.add(new ConverterItemStack());
        Driver.add(new DriverAnalyzer());
        Driver.add(new DriverBeeHouse());
    }
}
