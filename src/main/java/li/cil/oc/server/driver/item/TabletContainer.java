package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.item.*;
import li.cil.oc.api.network.*;
import li.cil.oc.common.item.AgentUpgradeItem;
import net.minecraft.world.item.ItemStack;

/** The tablet bay accepts the same original card/upgrade/floppy containers as robots. */
public final class TabletContainer extends li.cil.oc.api.prefab.DriverItem implements Container,HostAware {
    @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof AgentUpgradeItem item&&!item.kind.equals("inventory");}
    @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&li.cil.oc.api.internal.Tablet.class.isAssignableFrom(host);}
    @Override public String slot(ItemStack stack){return Slot.Container;}
    @Override public int tier(ItemStack stack){return ((AgentUpgradeItem)stack.getItem()).tier();}
    @Override public String providedSlot(ItemStack stack){return ((AgentUpgradeItem)stack.getItem()).kind;}
    @Override public int providedTier(ItemStack stack){return tier(stack);}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){return null;}
}
