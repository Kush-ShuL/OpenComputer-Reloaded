package li.cil.oc.common.menu;

import li.cil.oc.common.init.Racks;
import li.cil.oc.common.inventory.ServerInventory;
import li.cil.oc.common.item.ServerItem;
import li.cil.oc.server.component.Server;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ServerMenu extends AbstractContainerMenu {
    public final int tier,rackSlot;
    public final BlockPos position;
    public final InteractionHand hand;
    public final boolean mounted;
    private final Container inventory;
    private final ContainerData status;
    private final int componentSlots;
    private final ItemStack locked;
    public static void openHeld(ServerPlayer player,InteractionHand hand){ItemStack stack=player.getItemInHand(hand);if(!(stack.getItem() instanceof ServerItem item))return;ServerInventory inventory=new ServerInventory(stack,player.registryAccess(),player.getInventory()::setChanged);player.openMenu(new SimpleMenuProvider((id,inv,who)->new ServerMenu(id,inv,inventory,new SimpleContainerData(3),BlockPos.ZERO,-1,hand,item.tier(),stack),Component.translatable("oc:container.server")),buffer->{buffer.writeBoolean(false);buffer.writeVarInt(item.tier());buffer.writeEnum(hand);});}
    public static void openMounted(ServerPlayer player,Server server){if(!server.stillValid(player))return;ContainerData status=new ContainerData(){public int get(int index){var machine=server.machine();return switch(index){case 0->machine.isRunning()?1:0;case 1->{int value=0;for(int i=1;i<CaseMenu.ERRORS.length;i++)if(CaseMenu.ERRORS[i].equals(machine.lastError()))value=i;yield value;}case 2->machine.node().globalBufferSize()<=0?0:(int)(machine.node().globalBuffer()*100/machine.node().globalBufferSize());default->0;};}public void set(int index,int value){}public int getCount(){return 3;}};
        player.openMenu(new SimpleMenuProvider((id,inv,who)->new ServerMenu(id,inv,server,status,server.rack().getBlockPos(),server.slot(),InteractionHand.MAIN_HAND,server.tier(),ItemStack.EMPTY),Component.translatable("oc:container.server")),buffer->{buffer.writeBoolean(true);buffer.writeVarInt(server.tier());buffer.writeBlockPos(server.rack().getBlockPos());buffer.writeVarInt(server.slot());});
    }
    public ServerMenu(int id,Inventory player,boolean mounted,int tier,BlockPos pos,int slot,InteractionHand hand){this(id,player,new SimpleContainer(ServerInventory.layout(tier).length),new SimpleContainerData(3),pos,mounted?slot:-1,hand,tier,ItemStack.EMPTY);}
    private ServerMenu(int id,Inventory player,Container inventory,ContainerData status,BlockPos pos,int rackSlot,InteractionHand hand,int tier,ItemStack locked){super(Racks.SERVER_MENU.get(),id);this.inventory=inventory;this.status=status;this.position=pos;this.rackSlot=rackSlot;this.hand=hand;this.tier=tier;this.locked=locked;mounted=rackSlot>=0;componentSlots=inventory.getContainerSize();int slot=0;
        for(int i=0;i<2;i++)addComponent(slot++,76,7+i*18);
        int vertical=Math.min(3,1+tier);for(int i=0;i<=vertical;i++)addComponent(slot++,100,7+i*18);for(int i=0;i<=vertical;i++)addComponent(slot++,124,7+i*18);for(int i=0;i<=vertical;i++)addComponent(slot++,148,7+i*18);for(int i=2;i<=vertical;i++)addComponent(slot++,76,7+i*18);addComponent(slot,26,34);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,9+row*9+col,8+col*18,84+row*18));for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,142));addDataSlots(status);
    }
    private void addComponent(int index,int x,int y){addSlot(new Slot(inventory,index,x,y){@Override public boolean mayPlace(ItemStack stack){var driver=li.cil.oc.api.Driver.driverFor(stack,Server.class);var spec=ServerInventory.layout(tier)[index];return driver!=null&&spec.slot().equals(driver.slot(stack))&&driver.tier(stack)<=spec.tier()&&inventory.canPlaceItem(index,stack);}@Override public int getMaxStackSize(){return 1;}});}
    public boolean running(){return status.get(0)!=0;}
    public String error(){int index=status.get(1);return index>=0&&index<CaseMenu.ERRORS.length?CaseMenu.ERRORS[index]:"gui.Error.InternalError";}
    public int energyPercent(){return Math.clamp(status.get(2),0,100);}
    @Override public boolean stillValid(Player player){return mounted?inventory.stillValid(player):locked.isEmpty()||player.getItemInHand(hand)==locked;}
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(!locked.isEmpty()&&(slot>=0&&slot<slots.size()&&slots.get(slot).getItem()==locked||type==ClickType.SWAP&&(button==40&&hand==InteractionHand.OFF_HAND||button==player.getInventory().selected&&hand==InteractionHand.MAIN_HAND)))return;super.clicked(slot,button,type,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){Slot source=slots.get(index);if(!source.hasItem()||source.getItem()==locked)return ItemStack.EMPTY;ItemStack stack=source.getItem(),copy=stack.copy();boolean moved;if(index<componentSlots)moved=moveItemStackTo(stack,componentSlots,slots.size(),true);else{moved=false;for(int slot=0;slot<componentSlots&&!stack.isEmpty();slot++)if(slots.get(slot).mayPlace(stack))moved|=moveItemStackTo(stack,slot,slot+1,false);}if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())source.setByPlayer(ItemStack.EMPTY);else source.setChanged();return copy;}
}
