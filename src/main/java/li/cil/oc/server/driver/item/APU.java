package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.common.item.ComponentItem;
import net.minecraft.world.item.ItemStack;

/** Original combined CPU/GPU: its CPU tier is one above its graphics tier. */
public final class APU extends Processor implements HostAware, EnvironmentProvider {
    @Override protected boolean supports(ComponentItem item) { return item.kind() == ComponentItem.Kind.APU; }
    public int cpuTier(ItemStack stack) { return Math.min(2, gpuTier(stack) + 1); }
    public int gpuTier(ItemStack stack) { return item(stack).level(); }
    @Override public int tier(ItemStack stack) { return cpuTier(stack); }
    @Override public boolean worksWith(ItemStack stack, Class<? extends EnvironmentHost> host) {
        if (!worksWith(stack) || li.cil.oc.server.driver.Registry.INSTANCE.isBlacklistedHost(stack, host)) return false;
        // Original ModOpenComputers.blacklistHost excludes only the two survival APUs from these hosts.
        // Creative APUs remain installable unless an addon explicitly blacklists them through IMC.
        return gpuTier(stack) == 2 || host == null ||
                !li.cil.oc.api.internal.Drone.class.isAssignableFrom(host) &&
                !li.cil.oc.api.internal.Microcontroller.class.isAssignableFrom(host);
    }
    @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
        return host.world() != null && host.world().isClientSide ? null : new li.cil.oc.server.component.APU(gpuTier(stack));
    }
    @Override public Class<?> getEnvironment(ItemStack stack) {
        return worksWith(stack) ? li.cil.oc.server.component.GraphicsCard.class : null;
    }
}
