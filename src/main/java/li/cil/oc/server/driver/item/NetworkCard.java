package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.common.item.ComponentItem;
import net.minecraft.world.item.ItemStack;

public final class NetworkCard extends ItemDriver implements HostAware, EnvironmentProvider {
    @Override protected boolean supports(ComponentItem item) { return item.kind() == ComponentItem.Kind.NETWORK || item.kind() == ComponentItem.Kind.WIRELESS; }
    @Override public String slot(ItemStack stack) { return Slot.Card; }
    @Override public boolean worksWith(ItemStack stack, Class<? extends EnvironmentHost> host) {
        if (!worksWith(stack) || li.cil.oc.server.driver.Registry.INSTANCE.isBlacklistedHost(stack, host)) return false;
        return item(stack).kind() != ComponentItem.Kind.NETWORK || (!li.cil.oc.api.internal.Drone.class.isAssignableFrom(host)
                && !li.cil.oc.api.internal.Tablet.class.isAssignableFrom(host));
    }
    @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
        if (host.world() != null && host.world().isClientSide) return null;
        return item(stack).kind() == ComponentItem.Kind.WIRELESS ? new li.cil.oc.server.component.WirelessNetworkCard(host, tier(stack))
                : new li.cil.oc.server.component.NetworkCard(host);
    }
    @Override public Class<?> getEnvironment(ItemStack stack) {
        if (!worksWith(stack)) return null;
        return item(stack).kind() == ComponentItem.Kind.WIRELESS ? li.cil.oc.server.component.WirelessNetworkCard.class : li.cil.oc.server.component.NetworkCard.class;
    }
}
