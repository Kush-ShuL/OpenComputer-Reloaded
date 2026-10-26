package li.cil.oc.common.menu;

import li.cil.oc.common.block.entity.RelayBlockEntity;
import li.cil.oc.common.init.NetworkBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class RelayMenu extends AbstractContainerMenu {
    private final Container relay;
    private final ContainerData data;
    public final BlockPos position;
    public RelayMenu(int id,Inventory player,BlockPos pos){this(id,player,new SimpleContainer(4),new SimpleContainerData(5),pos);}
    public RelayMenu(int id,Inventory player,RelayBlockEntity relay){this(id,player,relay,new ContainerData(){@Override public int get(int index){return switch(index){case 0->relay.relayDelay();case 1->relay.relayAmount();case 2->relay.maxQueueSize();case 3->relay.packetsPerCycle();case 4->relay.queueSize();default->0;};}@Override public void set(int index,int value){}@Override public int getCount(){return 5;}},relay.getBlockPos());}
    private RelayMenu(int id,Inventory player,Container relay,ContainerData data,BlockPos pos){
        super(NetworkBlocks.RELAY_MENU.get(),id);this.relay=relay;this.data=data;position=pos;addDataSlots(data);
        int[][] positions={{151,15},{151,34},{151,53},{178,15}};
        for(int i=0;i<4;i++){int index=i;addSlot(new Slot(relay,i,positions[i][0],positions[i][1]){@Override public boolean mayPlace(ItemStack stack){return accepts(index,stack);}@Override public int getMaxStackSize(){return 1;}});}
        for(int row=0;row<3;row++)for(int column=0;column<9;column++)addSlot(new Slot(player,9+row*9+column,8+column*18,84+row*18));
        for(int column=0;column<9;column++)addSlot(new Slot(player,column,8+column*18,142));
    }
    private boolean accepts(int slot,ItemStack stack){if(relay instanceof RelayBlockEntity entity)return entity.canPlaceItem(slot,stack);var driver=li.cil.oc.api.Driver.driverFor(stack,RelayBlockEntity.class);if(driver==null||driver.tier(stack)>2)return false;String[] names={li.cil.oc.api.driver.item.Slot.CPU,li.cil.oc.api.driver.item.Slot.Memory,li.cil.oc.api.driver.item.Slot.HDD,li.cil.oc.api.driver.item.Slot.Card};if(!names[slot].equals(driver.slot(stack)))return false;if(slot!=3)return true;var descriptor=li.cil.oc.api.Items.get(stack);return descriptor!=null&&java.util.Set.of("wlancard1","wlancard2","linkedcard").contains(descriptor.name());}
    public int statistic(int index){return data.get(index);}
    @Override public boolean stillValid(Player player){return relay.stillValid(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot source=slots.get(index);if(!source.hasItem())return ItemStack.EMPTY;ItemStack stack=source.getItem(),original=stack.copy();boolean moved=false;if(index<4)moved=moveItemStackTo(stack,4,slots.size(),true);else for(int i=0;i<4&&!moved;i++)if(slots.get(i).mayPlace(stack))moved=moveItemStackTo(stack,i,i+1,false);if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())source.setByPlayer(ItemStack.EMPTY);else source.setChanged();source.onTake(player,stack);return original;}
}
