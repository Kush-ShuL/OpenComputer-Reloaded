package li.cil.oc.server.agent;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.function.IntSupplier;

/** Fixed backing storage with the agent's upgrade-dependent visible capacity. */
public final class AgentStorage implements Container {
    public final NonNullList<ItemStack> items;
    private final IntSupplier size;
    private final Runnable dirty;
    public AgentStorage(int maximum,IntSupplier size,Runnable dirty){items=NonNullList.withSize(maximum,ItemStack.EMPTY);this.size=size;this.dirty=dirty;}
    public int getContainerSize(){return Math.max(0,Math.min(items.size(),size.getAsInt()));}
    public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    public ItemStack getItem(int slot){return slot>=0&&slot<getContainerSize()?items.get(slot):ItemStack.EMPTY;}
    public ItemStack removeItem(int slot,int count){if(slot<0||slot>=getContainerSize())return ItemStack.EMPTY;ItemStack stack=ContainerHelper.removeItem(items,slot,count);if(!stack.isEmpty())setChanged();return stack;}
    public ItemStack removeItemNoUpdate(int slot){if(slot<0||slot>=getContainerSize())return ItemStack.EMPTY;return ContainerHelper.takeItem(items,slot);}
    public void setItem(int slot,ItemStack stack){if(slot>=0&&slot<getContainerSize()){items.set(slot,stack.copyWithCount(Math.min(stack.getCount(),Math.min(getMaxStackSize(),stack.getMaxStackSize()))));setChanged();}}
    public void setChanged(){dirty.run();}
    public boolean stillValid(Player player){return true;}
    public void clearContent(){items.replaceAll(stack->ItemStack.EMPTY);setChanged();}
    public void load(CompoundTag tag,HolderLookup.Provider registries){ContainerHelper.loadAllItems(tag,items,registries);}
    public void save(CompoundTag tag,HolderLookup.Provider registries){ContainerHelper.saveAllItems(tag,items,registries);}
}
