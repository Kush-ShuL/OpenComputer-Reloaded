package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.*;
import li.cil.oc.common.block.ScreenBlock;
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.server.agent.*;
import net.minecraft.world.item.*;

/** Actual installed screen environment for assembled robots. */
public final class AgentScreen extends li.cil.oc.api.prefab.DriverItem implements HostAware,EnvironmentProvider {
    @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof BlockItem item&&item.getBlock() instanceof ScreenBlock;}
    @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&li.cil.oc.api.internal.Robot.class.isAssignableFrom(host);}
    @Override public String slot(ItemStack stack){return Slot.Upgrade;}
    @Override public int tier(ItemStack stack){return ((ScreenBlock)((BlockItem)stack.getItem()).getBlock()).tier;}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){if(!(host instanceof AgentHost agent)||host.world().isClientSide)return null;TextBuffer buffer=new TextBuffer(agent,new AgentDisplayServices(agent));int tier=tier(stack);buffer.setMaximumResolution(li.cil.oc.Settings.screenResolutionsByTier[tier][0],li.cil.oc.Settings.screenResolutionsByTier[tier][1]);buffer.setMaximumColorDepth(li.cil.oc.Settings.screenDepthsByTier[tier]);return buffer;}
    @Override public Class<?> getEnvironment(ItemStack stack){return worksWith(stack)?TextBuffer.class:null;}
}
