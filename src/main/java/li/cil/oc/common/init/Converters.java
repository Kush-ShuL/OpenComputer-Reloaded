package li.cil.oc.common.init;

/** Core callback conversions; available independently of adapter registration. */
public final class Converters {
    private static boolean initialized;
    private Converters() {}
    public static synchronized void initialize() {
        if (initialized) return;
        if (li.cil.oc.api.API.driver == null) throw new IllegalStateException("Initialize the OpenComputers driver API before core converters");
        li.cil.oc.api.Driver.add(new li.cil.oc.server.driver.converter.NBT());
        li.cil.oc.api.Driver.add(new li.cil.oc.server.driver.converter.ItemStack());
        li.cil.oc.api.Driver.add(new li.cil.oc.server.driver.converter.FluidStack());
        li.cil.oc.api.Driver.add(new li.cil.oc.server.driver.converter.FluidContainerItem());
        initialized = true;
    }
}
