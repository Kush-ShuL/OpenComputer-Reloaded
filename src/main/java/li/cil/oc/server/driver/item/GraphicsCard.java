package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.common.item.ComponentItem;
import net.minecraft.world.item.ItemStack;

public final class GraphicsCard extends ItemDriver implements HostAware, EnvironmentProvider {
    @Override protected boolean supports(ComponentItem item) { return item.kind() == ComponentItem.Kind.GRAPHICS; }
    @Override public String slot(ItemStack stack) { return Slot.Card; }
    @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host) {
        return worksWith(stack)&&!li.cil.oc.server.driver.Registry.INSTANCE.isBlacklistedHost(stack,host)
                &&!li.cil.oc.api.internal.Drone.class.isAssignableFrom(host)&&!li.cil.oc.api.internal.Microcontroller.class.isAssignableFrom(host);
    }
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host) {
        return host.world()!=null&&host.world().isClientSide?null:new li.cil.oc.server.component.GraphicsCard(tier(stack));
    }
    @Override public Class<?> getEnvironment(ItemStack stack) { return worksWith(stack)?li.cil.oc.server.component.GraphicsCard.class:null; }
}
