package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.*;
import li.cil.oc.common.block.KeyboardBlock;
import li.cil.oc.server.portable.TabletHost;
import net.minecraft.world.item.*;

public final class TabletKeyboard extends li.cil.oc.api.prefab.DriverItem implements HostAware,EnvironmentProvider {
    @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof BlockItem block&&block.getBlock() instanceof KeyboardBlock;}
    @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&li.cil.oc.api.internal.Tablet.class.isAssignableFrom(host);}
    @Override public String slot(ItemStack stack){return Slot.Upgrade;}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){if(!(host instanceof TabletHost tablet)||host.world().isClientSide)return null;var keyboard=new li.cil.oc.server.component.Keyboard(host);keyboard.setUsableOverride((ignored,player)->tablet.stillValid(player));return keyboard;}
    @Override public Class<?> getEnvironment(ItemStack stack){return worksWith(stack)?li.cil.oc.server.component.Keyboard.class:null;}
}
