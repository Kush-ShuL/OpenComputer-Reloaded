package li.cil.oc.server.agent;

import li.cil.oc.util.InventoryUtils;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;

/** Routes fake-player item pickup and tools to the agent's real inventory. */
final class AgentInventory extends Inventory {
    private final AgentHost host;
    AgentInventory(Player player, AgentHost host) { super(player); this.host = host; }
    @Override public int getContainerSize() { return host.mainInventory().getContainerSize(); }
    @Override public ItemStack getSelected() { return host.equipmentInventory().getContainerSize() > 0 ? host.equipmentInventory().getItem(0) : ItemStack.EMPTY; }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < getContainerSize() ? host.mainInventory().getItem(slot) : ItemStack.EMPTY; }
    @Override public void setItem(int slot, ItemStack stack) { if (slot >= 0 && slot < getContainerSize()) host.mainInventory().setItem(slot, stack); }
    @Override public ItemStack removeItem(int slot, int count) { return slot >= 0 && slot < getContainerSize() ? host.mainInventory().removeItem(slot, count) : ItemStack.EMPTY; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return slot >= 0 && slot < getContainerSize() ? host.mainInventory().removeItemNoUpdate(slot) : ItemStack.EMPTY; }
    @Override public boolean add(ItemStack stack) {
        var inventory = InventoryUtils.asItemHandler(host.mainInventory());
        int size = inventory.getSlots(), selected = Math.clamp(host.selectedSlot(), 0, size);
        var slots = new ArrayList<Integer>(size);
        for (int slot = selected; slot < size; slot++) slots.add(slot);
        for (int slot = 0; slot < selected; slot++) slots.add(slot);
        return InventoryUtils.insertIntoInventory(stack, inventory, stack.getCount(), false, slots);
    }
    @Override public boolean add(int slot, ItemStack stack) { return add(stack); }
    @Override public void setChanged() { host.inventoryChanged(); }
    @Override public void dropAll() {}
}
