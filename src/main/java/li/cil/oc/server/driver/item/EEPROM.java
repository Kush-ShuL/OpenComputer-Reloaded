package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.common.item.ComponentItem;
import net.minecraft.world.item.ItemStack;

public final class EEPROM extends ItemDriver implements EnvironmentProvider {
    @Override protected boolean supports(ComponentItem item) { return item.kind() == ComponentItem.Kind.EEPROM; }
    @Override public String slot(ItemStack stack) { return "eeprom"; }
    @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
        return host.world() != null && host.world().isClientSide ? null : new li.cil.oc.server.component.EEPROM();
    }
    @Override public Class<?> getEnvironment(ItemStack stack) { return worksWith(stack) ? li.cil.oc.server.component.EEPROM.class : null; }
}
