package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.item.*;
import li.cil.oc.api.network.*;
import li.cil.oc.common.item.AgentUpgradeItem;
import net.minecraft.world.item.ItemStack;

/** Structural upgrades provide actual inventory capacity/slot types and intentionally no component node. */
public final class AgentUpgrade extends li.cil.oc.api.prefab.DriverItem implements Inventory,Container,HostAware {
    @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof AgentUpgradeItem;}
    @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&li.cil.oc.api.internal.Agent.class.isAssignableFrom(host);}
    private AgentUpgradeItem item(ItemStack stack){return (AgentUpgradeItem)stack.getItem();}
    @Override public String slot(ItemStack stack){return item(stack).kind.equals("inventory")?Slot.Upgrade:Slot.Container;}
    @Override public int tier(ItemStack stack){return item(stack).tier();}
    @Override public int inventoryCapacity(ItemStack stack){return item(stack).kind.equals("inventory")?16:0;}
    @Override public String providedSlot(ItemStack stack){return item(stack).kind;}
    @Override public int providedTier(ItemStack stack){return tier(stack);}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){return null;}
}
