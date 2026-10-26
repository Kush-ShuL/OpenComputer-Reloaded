package li.cil.oc.common.menu;

import li.cil.oc.common.block.entity.RackBlockEntity;
import li.cil.oc.common.init.Racks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class RackMenu extends AbstractContainerMenu {
    public final BlockPos position;
    private final Container inventory;
    private final ContainerData status;
    public RackMenu(int id,Inventory player,BlockPos pos){this(id,player,new SimpleContainer(4),new SimpleContainerData(18),pos);}
    public RackMenu(int id,Inventory player,RackBlockEntity rack){this(id,player,rack,new ContainerData(){
        public int get(int index){return index==0?rack.relayEnabled()?1:0:index==1?rack.presenceMask():rack.mapping((index-2)/4,(index-2)%4);}
        public void set(int index,int value){}public int getCount(){return 18;}
    },rack.getBlockPos());}
    private RackMenu(int id,Inventory player,Container inventory,ContainerData status,BlockPos pos){super(Racks.RACK_MENU.get(),id);this.inventory=inventory;this.status=status;position=pos;
        for(int i=0;i<4;i++){int slot=i;addSlot(new Slot(inventory,i,20,23+i*20){@Override public boolean mayPlace(ItemStack stack){var driver=li.cil.oc.api.Driver.driverFor(stack,RackBlockEntity.class);return driver!=null&&driver.slot(stack).equals("rack_mountable")&&inventory.canPlaceItem(slot,stack);}@Override public int getMaxStackSize(){return 1;}});}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,9+row*9+col,8+col*18,128+row*18));for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,186));addDataSlots(status);
    }
    public boolean relayEnabled(){return status.get(0)!=0;}
    public boolean present(int slot,int connection){return ((status.get(1)&0xffff)&1<<(slot*4+connection))!=0;}
    public int mapping(int slot,int connection){return status.get(2+slot*4+connection);}
    @Override public boolean stillValid(Player player){return inventory.stillValid(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){Slot source=slots.get(index);if(!source.hasItem())return ItemStack.EMPTY;
        if(index<4&&source.getItem().getItem() instanceof li.cil.oc.common.item.RackDiskDriveItem){ItemStack removed=source.remove(1),copy=removed.copy();if(!moveItemStackTo(removed,4,slots.size(),true)){inventory.setItem(index,removed);return ItemStack.EMPTY;}source.onTake(player,removed);return copy;}
        ItemStack stack=source.getItem(),copy=stack.copy();boolean moved=index<4?moveItemStackTo(stack,4,slots.size(),true):moveItemStackTo(stack,0,4,false);if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())source.setByPlayer(ItemStack.EMPTY);else source.setChanged();return copy;}
}
