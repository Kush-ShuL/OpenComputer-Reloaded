package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.item.Container;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.world.item.ItemStack;

/** The ordinary disk-drive block is also the original robot/tablet floppy bay. */
public final class ContainerFloppy extends li.cil.oc.api.prefab.DriverItem implements Container {
    @Override public boolean worksWith(ItemStack stack) {
        return !stack.isEmpty() && li.cil.oc.api.Items.get(stack) == li.cil.oc.api.Items.get("diskdrive");
    }
    @Override public String slot(ItemStack stack) { return Slot.Container; }
    @Override public String providedSlot(ItemStack stack) { return Slot.Floppy; }
    @Override public int providedTier(ItemStack stack) { return Integer.MAX_VALUE; }
    @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) { return null; }
}
