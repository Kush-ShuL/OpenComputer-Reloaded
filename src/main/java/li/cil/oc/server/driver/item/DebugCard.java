package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.*;
import li.cil.oc.common.item.DebugCardItem;
import net.minecraft.world.item.ItemStack;

public final class DebugCard extends li.cil.oc.api.prefab.DriverItem implements EnvironmentProvider {
    @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof DebugCardItem;}
    @Override public String slot(ItemStack stack){return Slot.Card;}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){return host.world()!=null&&host.world().isClientSide?null:new li.cil.oc.server.component.DebugCard(host);}
    @Override public Class<?> getEnvironment(ItemStack stack){return worksWith(stack)?li.cil.oc.server.component.DebugCard.class:null;}
}
