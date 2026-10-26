package li.cil.oc.server.agent;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.function.IntSupplier;
import java.util.function.IntConsumer;

/** Fixed backing storage with the agent's upgrade-dependent visible capacity. */
public final class AgentStorage implements Container {
    public final NonNullList<ItemStack> items;
    private final IntSupplier size;
    private final Runnable dirty;
    private final IntConsumer beforeChange;
    public AgentStorage(int maximum,IntSupplier size,Runnable dirty){this(maximum,size,dirty,slot->{});}
    public AgentStorage(int maximum,IntSupplier size,Runnable dirty,IntConsumer beforeChange){items=NonNullList.withSize(maximum,ItemStack.EMPTY);this.size=size;this.dirty=dirty;this.beforeChange=beforeChange;}
    public int getContainerSize(){return Math.max(0,Math.min(items.size(),size.getAsInt()));}
    public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    public ItemStack getItem(int slot){return slot>=0&&slot<getContainerSize()?items.get(slot):ItemStack.EMPTY;}
    public ItemStack removeItem(int slot,int count){if(slot<0||slot>=getContainerSize()||count<=0||items.get(slot).isEmpty())return ItemStack.EMPTY;beforeChange.accept(slot);ItemStack stack=ContainerHelper.removeItem(items,slot,count);if(items.get(slot).isEmpty())items.set(slot,ItemStack.EMPTY);if(!stack.isEmpty())setChanged();return stack;}
    public ItemStack removeItemNoUpdate(int slot){if(slot<0||slot>=getContainerSize()||items.get(slot).isEmpty())return ItemStack.EMPTY;beforeChange.accept(slot);ItemStack stack=ContainerHelper.takeItem(items,slot);setChanged();return stack;}
    public void setItem(int slot,ItemStack stack){if(slot>=0&&slot<getContainerSize()){beforeChange.accept(slot);items.set(slot,stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(Math.min(stack.getCount(),Math.min(getMaxStackSize(),stack.getMaxStackSize()))));setChanged();}}
    public void setChanged(){dirty.run();}
    public boolean stillValid(Player player){return true;}
    public void clearContent(){for(int slot=0;slot<items.size();slot++)if(!items.get(slot).isEmpty())beforeChange.accept(slot);items.replaceAll(stack->ItemStack.EMPTY);setChanged();}
    public void load(CompoundTag tag,HolderLookup.Provider registries){ContainerHelper.loadAllItems(tag,items,registries);}
    public void save(CompoundTag tag,HolderLookup.Provider registries){ContainerHelper.saveAllItems(tag,items,registries);}
}
