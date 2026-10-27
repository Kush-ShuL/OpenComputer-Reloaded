package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.world.item.ItemStack;

/** The original geolyzer block item also installs as a tier-one upgrade. */
public final class Geolyzer extends li.cil.oc.api.prefab.DriverItem implements HostAware, EnvironmentProvider {
    @Override public boolean worksWith(ItemStack stack) {
        var descriptor = li.cil.oc.api.Items.get("geolyzer");
        return descriptor != null && !stack.isEmpty() && ItemStack.isSameItem(stack, descriptor.createItemStack(1));
    }

    @Override public boolean worksWith(ItemStack stack, Class<? extends EnvironmentHost> host) {
        return worksWith(stack);
    }

    @Override public String slot(ItemStack stack) { return Slot.Upgrade; }

    @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
        return host.world() != null && host.world().isClientSide ? null : new li.cil.oc.server.component.Geolyzer(host);
    }

    @Override public Class<?> getEnvironment(ItemStack stack) {
        return worksWith(stack) ? li.cil.oc.server.component.Geolyzer.class : null;
    }
}
