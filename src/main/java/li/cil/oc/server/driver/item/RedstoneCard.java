package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.common.item.RedstoneCardItem;
import li.cil.oc.common.redstone.RedstoneHost;
import net.minecraft.world.item.ItemStack;

/** Original tier selection: bundled callbacks on tier two; no fabricated wireless system. */
public final class RedstoneCard extends li.cil.oc.api.prefab.DriverItem implements HostAware, EnvironmentProvider {
    @Override public boolean worksWith(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.getItem() instanceof RedstoneCardItem; }
    @Override public boolean worksWith(ItemStack stack, Class<? extends EnvironmentHost> host) {
        return worksWith(stack) && RedstoneHost.class.isAssignableFrom(host)
                && !li.cil.oc.server.driver.Registry.INSTANCE.isBlacklistedHost(stack, host);
    }
    @Override public String slot(ItemStack stack) { return Slot.Card; }
    @Override public int tier(ItemStack stack) { return ((RedstoneCardItem) stack.getItem()).tier(); }
    @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
        if (!(host instanceof RedstoneHost redstone) || host.world() != null && host.world().isClientSide) return null;
        return tier(stack) == 1 && li.cil.oc.common.redstone.BundledRedstone.isAvailable()
                ? new li.cil.oc.server.component.RedstoneBundled(redstone, false)
                : new li.cil.oc.server.component.Redstone(redstone, false);
    }
    @Override public Class<?> getEnvironment(ItemStack stack) {
        return !worksWith(stack) ? null : tier(stack) == 1 && li.cil.oc.common.redstone.BundledRedstone.isAvailable()
                ? li.cil.oc.server.component.RedstoneBundled.class : li.cil.oc.server.component.Redstone.class;
    }
}
