package li.cil.oc.server.driver.item;

import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.item.Chargeable;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.*;
import li.cil.oc.common.item.TabletItem;
import li.cil.oc.common.item.data.TabletData;
import li.cil.oc.server.portable.TabletManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** The charger exposes the tablet's actual first disk, sharing its saved address and contents. */
public final class TabletStorage extends li.cil.oc.api.prefab.DriverItem implements Chargeable {
    private record Disk(TabletData data,int slot,li.cil.oc.api.driver.DriverItem driver){}
    @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof TabletItem;}
    @Override public String slot(ItemStack stack){return Slot.Tablet;}
    @Override public int tier(ItemStack stack){return 0;}
    private Disk disk(ItemStack stack,HolderLookup.Provider registries){TabletData data=new TabletData(stack,registries);for(int index=0;index<data.items.length;index++){ItemStack part=data.items[index];if(part.isEmpty())continue;var driver=Driver.driverFor(part);if(driver!=null&&(driver.slot(part).equals(Slot.HDD)||driver.slot(part).equals(Slot.Floppy)))return new Disk(data,index,driver);}return null;}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){if(host.world()==null||host.world().isClientSide)return null;TabletManager.invalidate(stack);Disk disk=disk(stack,host.world().registryAccess());if(disk==null)return null;var environment=disk.driver.createEnvironment(disk.data.items[disk.slot],host);disk.data.save(stack,host.world().registryAccess());if(environment!=null&&environment.node() instanceof li.cil.oc.api.network.Component component)component.setVisibility(Visibility.Network);return environment;}
    @Override public CompoundTag dataTag(ItemStack stack){var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return new CompoundTag();Disk disk=disk(stack,server.registryAccess());return disk==null?new CompoundTag():disk.driver.dataTag(disk.data.items[disk.slot]);}
    @Override public void saveDataTag(ItemStack stack,CompoundTag tag){var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;Disk disk=disk(stack,server.registryAccess());if(disk==null)return;disk.driver.saveDataTag(disk.data.items[disk.slot],tag);disk.data.save(stack,server.registryAccess());}
    @Override public boolean canCharge(ItemStack stack){return worksWith(stack);}
    @Override public double charge(ItemStack stack,double amount,boolean simulate){return stack.getItem() instanceof TabletItem item?item.charge(stack,amount,simulate):amount;}
}
