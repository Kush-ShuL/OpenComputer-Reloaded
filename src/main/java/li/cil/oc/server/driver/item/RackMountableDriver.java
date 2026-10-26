package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.common.block.entity.RackBlockEntity;
import li.cil.oc.common.item.ServerItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class RackMountableDriver extends li.cil.oc.api.prefab.DriverItem implements HostAware {
    @Override public boolean worksWith(ItemStack stack){return stack!=null&&!stack.isEmpty()&&(stack.getItem() instanceof ServerItem||stack.is(li.cil.oc.common.init.Racks.TERMINAL_SERVER.get()));}
    @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&RackBlockEntity.class.isAssignableFrom(host);}
    @Override public String slot(ItemStack stack){return Slot.RackMountable;}
    @Override public int tier(ItemStack stack){return stack.getItem() instanceof ServerItem server?server.tier():0;}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){
        if(!(host instanceof RackBlockEntity rack))return null;int slot=rack.indexOfStack(stack);if(slot<0)return null;
        return stack.getItem() instanceof ServerItem?new li.cil.oc.server.component.Server(rack,slot):new li.cil.oc.common.component.TerminalServer(rack,slot);
    }
    @Override public CompoundTag dataTag(ItemStack stack){return stack.getItem() instanceof ServerItem?stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag():super.dataTag(stack);}
    @Override public void saveDataTag(ItemStack stack,CompoundTag data){if(stack.getItem() instanceof ServerItem)stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data.copy()));else super.saveDataTag(stack,data);}
}
