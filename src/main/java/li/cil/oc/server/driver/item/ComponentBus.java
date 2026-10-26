package li.cil.oc.server.driver.item;

import li.cil.oc.Settings;
import li.cil.oc.api.driver.item.Processor;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.common.item.ComponentBusItem;
import net.minecraft.world.item.ItemStack;

public final class ComponentBus extends li.cil.oc.api.prefab.DriverItem implements Processor {
    @Override public boolean worksWith(ItemStack stack){return stack!=null&&!stack.isEmpty()&&stack.getItem() instanceof ComponentBusItem;}
    @Override public String slot(ItemStack stack){return Slot.ComponentBus;}
    @Override public int tier(ItemStack stack){return ((ComponentBusItem)stack.getItem()).tier();}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){return null;}
    @Override public int supportedComponents(ItemStack stack){return Settings.get().cpuComponentSupport[((ComponentBusItem)stack.getItem()).capacityTier];}
    @Override public Class<? extends Architecture> architecture(ItemStack stack){return null;}
}
