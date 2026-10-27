package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Inventory;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.common.item.AgentUpgradeItem;
import net.minecraft.world.item.ItemStack;

/** Original structural +16 inventory upgrade; it is not a hot-swap slot container. */
public final class InventoryUpgrade extends li.cil.oc.api.prefab.DriverItem implements Inventory, HostAware {
    @Override public boolean worksWith(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof AgentUpgradeItem item
                && item.kind.equals("inventory");
    }

    @Override public boolean worksWith(ItemStack stack, Class<? extends EnvironmentHost> host) {
        if (!worksWith(stack) || li.cil.oc.server.driver.Registry.INSTANCE.isBlacklistedHost(stack, host)) return false;
        // The source uses a blacklist, not an Agent-only whitelist: custom compatible hosts remain supported.
        return host == null || !li.cil.oc.api.internal.Adapter.class.isAssignableFrom(host)
                && !li.cil.oc.api.internal.Microcontroller.class.isAssignableFrom(host)
                && !li.cil.oc.api.internal.Tablet.class.isAssignableFrom(host);
    }

    @Override public String slot(ItemStack stack) { return Slot.Upgrade; }
    @Override public int inventoryCapacity(ItemStack stack) { return 16; }
    @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) { return null; }
}
