package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.*;
import li.cil.oc.common.block.ScreenBlock;
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.server.portable.*;
import net.minecraft.world.item.*;

public final class TabletScreen extends li.cil.oc.api.prefab.DriverItem implements HostAware,EnvironmentProvider {
    @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof BlockItem block&&block.getBlock() instanceof ScreenBlock;}
    @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&li.cil.oc.api.internal.Tablet.class.isAssignableFrom(host);}
    @Override public String slot(ItemStack stack){return Slot.Upgrade;}
    @Override public int tier(ItemStack stack){return ((ScreenBlock)((BlockItem)stack.getItem()).getBlock()).tier;}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){if(!(host instanceof TabletHost tablet)||host.world().isClientSide)return null;var screen=new TextBuffer(host,new TabletDisplayServices(tablet));screen.setMaximumResolution(80,25);screen.setMaximumColorDepth(li.cil.oc.api.internal.TextBuffer.ColorDepth.FourBit);return screen;}
    @Override public Class<?> getEnvironment(ItemStack stack){return worksWith(stack)?TextBuffer.class:null;}
}
