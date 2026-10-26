package li.cil.oc.api.prefab;

import li.cil.oc.api.network.EnvironmentHost;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

/**
 * If you wish to create item components such as the network card or hard drives
 * you will need an item driver.
 * <br>
 * This prefab allows creating a driver that works for a specified list of item
 * stacks (to support different items with the same id but different damage
 * values). It reads persistent data from the item's custom data component. After
 * modifying the returned data, call saveDataTag to commit it.
 * <br>
 * You still have to specify your component's slot type and provide the
 * implementation for creating its environment, if any.
 *
 * @see li.cil.oc.api.network.ManagedEnvironment
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class DriverItem implements li.cil.oc.api.driver.DriverItem {
    protected final ItemStack[] items;

    protected DriverItem(final ItemStack... items) {
        this.items = items.clone();
    }

    @Override
    public boolean worksWith(final ItemStack stack) {
        if (!stack.isEmpty()) {
            for (ItemStack item : items) {
                if (!item.isEmpty() && ItemStack.isSameItem(item, stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int tier(final ItemStack stack) {
        return 0;
    }

    @Override
    public CompoundTag dataTag(final ItemStack stack) {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("oc:data");
    }

    // Convenience methods provided for HostAware drivers.

    protected boolean isAdapter(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Adapter.class.isAssignableFrom(host);
    }

    protected boolean isComputer(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Case.class.isAssignableFrom(host);
    }

    protected boolean isRobot(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Robot.class.isAssignableFrom(host);
    }

    protected boolean isRotatable(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Rotatable.class.isAssignableFrom(host);
    }

    protected boolean isServer(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Server.class.isAssignableFrom(host);
    }

    protected boolean isTablet(Class<? extends EnvironmentHost> host) {
        return li.cil.oc.api.internal.Tablet.class.isAssignableFrom(host);
    }
}
