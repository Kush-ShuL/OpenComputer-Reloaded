package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.common.block.entity.RackBlockEntity;
import li.cil.oc.common.item.RackDiskDriveItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** The mountable uses the whole item tag, as did the original rack drive driver. */
public final class DiskDriveMountable extends li.cil.oc.api.prefab.DriverItem implements HostAware {
    @Override public boolean worksWith(ItemStack stack){return stack!=null&&!stack.isEmpty()&&stack.getItem() instanceof RackDiskDriveItem;}
    @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&RackBlockEntity.class.isAssignableFrom(host);}
    @Override public String slot(ItemStack stack){return li.cil.oc.api.driver.item.Slot.RackMountable;}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){if(!(host instanceof RackBlockEntity rack))return null;int slot=rack.indexOfStack(stack);return slot<0?null:new li.cil.oc.server.component.DiskDriveMountable(rack,slot);}
    @Override public CompoundTag dataTag(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    @Override public void saveDataTag(ItemStack stack,CompoundTag tag){stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag.copy()));}
}
