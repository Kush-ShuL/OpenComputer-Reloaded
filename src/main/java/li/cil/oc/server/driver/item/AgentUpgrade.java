package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.item.*;
import li.cil.oc.api.network.*;
import li.cil.oc.common.item.AgentUpgradeItem;
import net.minecraft.world.item.ItemStack;

/** Hot-swap containers provide slot metadata without a component node. */
public final class AgentUpgrade extends li.cil.oc.api.prefab.DriverItem implements Container,HostAware {
    @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof AgentUpgradeItem item&&!item.kind.equals("inventory");}
    @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&(item(stack).kind.equals(Slot.Card)||item(stack).kind.equals(Slot.Upgrade)||li.cil.oc.api.internal.Agent.class.isAssignableFrom(host));}
    private AgentUpgradeItem item(ItemStack stack){return (AgentUpgradeItem)stack.getItem();}
    @Override public String slot(ItemStack stack){return Slot.Container;}
    @Override public int tier(ItemStack stack){return item(stack).tier();}
    @Override public String providedSlot(ItemStack stack){return item(stack).kind;}
    @Override public int providedTier(ItemStack stack){return tier(stack);}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){return null;}
}
