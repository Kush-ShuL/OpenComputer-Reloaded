package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.*;
import li.cil.oc.common.block.KeyboardBlock;
import li.cil.oc.server.agent.AgentHost;
import net.minecraft.world.item.*;

public final class AgentKeyboard extends li.cil.oc.api.prefab.DriverItem implements HostAware,EnvironmentProvider {
    @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof BlockItem item&&item.getBlock() instanceof KeyboardBlock;}
    @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&li.cil.oc.api.internal.Robot.class.isAssignableFrom(host);}
    @Override public String slot(ItemStack stack){return Slot.Upgrade;}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){if(!(host instanceof AgentHost agent)||host.world().isClientSide)return null;var keyboard=new li.cil.oc.server.component.Keyboard(host);keyboard.setUsableOverride((ignored,player)->player.level()==host.world()&&player.distanceToSqr(host.xPosition(),host.yPosition(),host.zPosition())<=64&&agent.machine().canInteract(player.getGameProfile().getName()));return keyboard;}
    @Override public Class<?> getEnvironment(ItemStack stack){return worksWith(stack)?li.cil.oc.server.component.Keyboard.class:null;}
}
