package li.cil.oc.common.menu;

import li.cil.oc.common.InventorySlots;
import li.cil.oc.common.block.entity.CaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class CaseMenu extends AbstractContainerMenu {
    public static final int HEADER_SPACE=12;
    private static final DeferredRegister<MenuType<?>> TYPES=DeferredRegister.create(Registries.MENU,"opencomputers");
    public static final Supplier<MenuType<CaseMenu>> TYPE=TYPES.register("case",()->IMenuTypeExtension.create((id,inventory,data)->{
        BlockPos pos=data.readBlockPos();int tier=Math.max(0,Math.min(3,data.readVarInt()));return new CaseMenu(id,inventory,new SimpleContainer(InventorySlots.computer[tier].length),new SimpleContainerData(3),pos,tier);
    }));
    public static final String[] ERRORS={"","gui.Error.NoEnergy","gui.Error.NoCPU","gui.Error.NoRAM","gui.Error.ComponentOverflow","gui.Error.OutOfMemory","gui.Error.InternalError"};
    public final BlockPos position;
    public final int tier;
    private final Container inventory;
    private final ContainerData status;
    private final int componentSlots;
    public static void register(IEventBus bus){TYPES.register(bus);}
    public CaseMenu(int id,Inventory player,CaseBlockEntity computer){this(id,player,computer,new ContainerData(){
        public int get(int index){var machine=computer.machine();if(machine==null)return 0;return switch(index){case 0->machine.isRunning()?1:0;case 1->{int error=0;for(int i=1;i<ERRORS.length;i++)if(ERRORS[i].equals(machine.lastError()))error=i;yield error;}case 2->machine.node().globalBufferSize()<=0?0:(int)(machine.node().globalBuffer()*100/machine.node().globalBufferSize());default->0;};}
        public void set(int index,int value){}public int getCount(){return 3;}
    },computer.getBlockPos(),computer.tier);}
    private CaseMenu(int id,Inventory player,Container inventory,ContainerData status,BlockPos position,int tier){
        super(TYPE.get(),id);this.inventory=inventory;this.status=status;this.position=position;this.tier=tier;componentSlots=inventory.getContainerSize();inventory.startOpen(player.player);int slot=0;
        for(int i=0;i<=(tier>=2?2:1);i++)addComponentSlot(slot++,98,16+i*18);
        for(int i=0;i<=(tier==0?0:1);i++)addComponentSlot(slot++,120,34+i*18);
        for(int i=0;i<=(tier==0?0:1);i++)addComponentSlot(slot++,142,16+i*18);
        if(tier>=2)addComponentSlot(slot++,142,52);
        addComponentSlot(slot++,120,16);
        if(tier==0)addComponentSlot(slot++,120,52);
        addComponentSlot(slot,48,34);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,9+row*9+col,8+col*18,84+HEADER_SPACE+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,142+HEADER_SPACE));addDataSlots(status);
    }
    private void addComponentSlot(int index,int x,int y){addSlot(new Slot(inventory,index,x,y+HEADER_SPACE){@Override public boolean mayPlace(ItemStack stack){var driver=li.cil.oc.api.Driver.driverFor(stack,CaseBlockEntity.class);var allowed=InventorySlots.computer[tier][index];
            return driver!=null&&allowed.slot().equals(driver.slot(stack))&&driver.tier(stack)<=allowed.tier()&&inventory.canPlaceItem(index,stack);}@Override public int getMaxStackSize(){return 1;}});}
    public boolean running(){return status.get(0)!=0;}
    public String error(){int index=status.get(1);return index>=0&&index<ERRORS.length?ERRORS[index]:"gui.Error.InternalError";}
    public int energyPercent(){return Math.max(0,Math.min(100,status.get(2)));}
    @Override public boolean stillValid(Player player){return inventory.stillValid(player);}
    @Override public void removed(Player player){super.removed(player);inventory.stopOpen(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        Slot source=slots.get(index);if(!source.hasItem())return ItemStack.EMPTY;ItemStack stack=source.getItem(),copy=stack.copy();boolean moved;
        if(index<componentSlots)moved=moveItemStackTo(stack,componentSlots,slots.size(),true);
        else{moved=false;for(int i=0;i<componentSlots&&!stack.isEmpty();i++)if(slots.get(i).mayPlace(stack))moved|=moveItemStackTo(stack,i,i+1,false);}
        if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())source.setByPlayer(ItemStack.EMPTY);else source.setChanged();return copy;
    }
}
