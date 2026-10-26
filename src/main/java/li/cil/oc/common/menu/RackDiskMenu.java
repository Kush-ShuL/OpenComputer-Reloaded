package li.cil.oc.common.menu;

import li.cil.oc.common.init.RackDisks;
import li.cil.oc.common.inventory.RackDiskInventory;
import li.cil.oc.common.item.RackDiskDriveItem;
import li.cil.oc.server.component.DiskDriveMountable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Vanilla menu synchronization carries the single real floppy slot in either location. */
public final class RackDiskMenu extends AbstractContainerMenu {
    public final BlockPos position;
    public final int rackSlot;
    private final Container drive;
    private final InteractionHand hand;
    private final ItemStack locked;
    public static void openHeld(ServerPlayer player,InteractionHand hand){
        ItemStack held=player.getItemInHand(hand);if(player.isSpectator()||!(held.getItem() instanceof RackDiskDriveItem))return;
        var inventory=new RackDiskInventory(held,player.registryAccess(),player.getInventory()::setChanged);
        player.openMenu(new SimpleMenuProvider((id,inv,who)->new RackDiskMenu(id,inv,inventory,BlockPos.ZERO,-1,hand,held),Component.translatable("oc:container.diskdrive")),buffer->{buffer.writeBoolean(false);buffer.writeEnum(hand);});
    }
    public static void openMounted(ServerPlayer player,DiskDriveMountable disk){
        if(player.isSpectator()||!disk.stillValid(player))return;
        player.openMenu(new SimpleMenuProvider((id,inv,who)->new RackDiskMenu(id,inv,disk,disk.rack.getBlockPos(),disk.slot,InteractionHand.MAIN_HAND,ItemStack.EMPTY),Component.translatable("oc:container.diskdrive")),buffer->{buffer.writeBoolean(true);buffer.writeBlockPos(disk.rack.getBlockPos());buffer.writeVarInt(disk.slot);});
    }
    public RackDiskMenu(int id,Inventory player,RegistryFriendlyByteBuf buffer){this(id,player,readLocation(player,buffer));}
    private record Location(BlockPos position,int slot,InteractionHand hand,ItemStack held){}
    private static Location readLocation(Inventory player,RegistryFriendlyByteBuf buffer){return buffer.readBoolean()?new Location(buffer.readBlockPos(),buffer.readVarInt(),InteractionHand.MAIN_HAND,ItemStack.EMPTY):heldLocation(player,buffer.readEnum(InteractionHand.class));}
    private static Location heldLocation(Inventory player,InteractionHand hand){return new Location(BlockPos.ZERO,-1,hand,player.player.getItemInHand(hand));}
    private RackDiskMenu(int id,Inventory player,Location where){this(id,player,new SimpleContainer(1),where.position,where.slot,where.hand,where.held);}
    private RackDiskMenu(int id,Inventory player,Container drive,BlockPos position,int rackSlot,InteractionHand hand,ItemStack locked){
        super(RackDisks.MENU.get(),id);this.drive=drive;this.position=position;this.rackSlot=rackSlot;this.hand=hand;this.locked=locked;drive.startOpen(player.player);
        addSlot(new Slot(drive,0,80,35){@Override public boolean mayPlace(ItemStack stack){var driver=li.cil.oc.api.Driver.driverFor(stack);return driver!=null&&li.cil.oc.api.driver.item.Slot.Floppy.equals(driver.slot(stack))&&drive.canPlaceItem(0,stack);}@Override public int getMaxStackSize(){return 1;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,9+row*9+col,8+col*18,84+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,142));
    }
    @Override public boolean stillValid(Player player){return rackSlot>=0?drive.stillValid(player):!locked.isEmpty()&&player.getItemInHand(hand)==locked;}
    @Override public void removed(Player player){super.removed(player);drive.stopOpen(player);}
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(!locked.isEmpty()&&(slot>=0&&slot<slots.size()&&slots.get(slot).getItem()==locked||type==ClickType.SWAP&&(button==40&&hand==InteractionHand.OFF_HAND||button==player.getInventory().selected&&hand==InteractionHand.MAIN_HAND)))return;
        super.clicked(slot,button,type,player);
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot source=slots.get(index);if(!source.hasItem()||source.getItem()==locked)return ItemStack.EMPTY;
        if(index==0){ItemStack removed=source.remove(1),copy=removed.copy();if(!moveItemStackTo(removed,1,slots.size(),true)){drive.setItem(0,removed);return ItemStack.EMPTY;}source.onTake(player,removed);return copy;}
        ItemStack stack=source.getItem(),copy=stack.copy();
        boolean moved=slots.getFirst().mayPlace(stack)&&moveItemStackTo(stack,0,1,false);
        if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())source.setByPlayer(ItemStack.EMPTY);else source.setChanged();source.onTake(player,stack);return copy;
    }
}
