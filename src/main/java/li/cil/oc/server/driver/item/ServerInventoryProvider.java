package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.InventoryProvider;
import li.cil.oc.common.inventory.ServerInventory;
import li.cil.oc.common.item.ServerItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** World-aware item inventory access; data components are never decoded against a global server. */
public final class ServerInventoryProvider implements InventoryProvider {
    @Override public boolean worksWith(ItemStack stack,Player player){return player!=null&&!stack.isEmpty()&&stack.getItem() instanceof ServerItem;}
    @Override public Container getInventory(ItemStack stack,Player player){return worksWith(stack,player)?new ServerInventory(stack,player.level().registryAccess(),player.getInventory()::setChanged):null;}
}
