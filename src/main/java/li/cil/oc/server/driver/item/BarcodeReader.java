package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.*;
import li.cil.oc.api.driver.item.*;
import li.cil.oc.api.network.*;
import li.cil.oc.server.component.UpgradeBarcodeReader;
import net.minecraft.world.item.ItemStack;

/** The original analyzer item doubles as the tablet barcode-reader upgrade. */
public final class BarcodeReader extends li.cil.oc.api.prefab.DriverItem implements HostAware,EnvironmentProvider {
    @Override public boolean worksWith(ItemStack stack){var info=li.cil.oc.api.Items.get("analyzer");return info!=null&&!stack.isEmpty()&&ItemStack.isSameItem(stack,info.createItemStack(1));}
    @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&li.cil.oc.api.internal.Tablet.class.isAssignableFrom(host);}
    @Override public String slot(ItemStack stack){return Slot.Upgrade;}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){return host.world().isClientSide?null:new UpgradeBarcodeReader(host);}
    @Override public Class<?> getEnvironment(ItemStack stack){return worksWith(stack)?UpgradeBarcodeReader.class:null;}
}
