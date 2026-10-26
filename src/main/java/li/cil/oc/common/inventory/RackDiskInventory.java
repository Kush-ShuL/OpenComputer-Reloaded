package li.cil.oc.common.inventory;

import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.item.Slot;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Original ItemStackInventory serialization; a held drive has no live filesystem. */
public class RackDiskInventory implements Container {
    protected final ItemStack container;
    protected final HolderLookup.Provider registries;
    protected final NonNullList<ItemStack> items=NonNullList.withSize(1,ItemStack.EMPTY);
    private final Runnable dirty;
    public RackDiskInventory(ItemStack container,HolderLookup.Provider registries,Runnable dirty){
        this.container=container;this.registries=registries;this.dirty=dirty;
        ServerInventory.loadItems(container.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag(),items,registries);
    }
    @Override public int getContainerSize(){return 1;}
    @Override public int getMaxStackSize(){return 1;}
    @Override public boolean isEmpty(){return items.getFirst().isEmpty();}
    @Override public ItemStack getItem(int slot){return slot==0?items.getFirst():ItemStack.EMPTY;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){if(slot!=0||stack.isEmpty())return false;var driver=Driver.driverFor(stack);return driver!=null&&Slot.Floppy.equals(driver.slot(stack));}
    protected void beforeChange(){}
    protected void afterChange(){setChanged();}
    @Override public ItemStack removeItem(int slot,int count){if(slot!=0||count<=0||isEmpty())return ItemStack.EMPTY;beforeChange();ItemStack result=items.getFirst().split(count);if(items.getFirst().isEmpty())items.set(0,ItemStack.EMPTY);afterChange();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,1);}
    @Override public void setItem(int slot,ItemStack stack){if(slot!=0)return;beforeChange();items.set(0,stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(1));afterChange();}
    @Override public void clearContent(){removeItem(0,1);}
    @Override public void setChanged(){ServerInventory.saveItems(container,items,registries);dirty.run();}
    @Override public boolean stillValid(Player player){return true;}
}
