package li.cil.oc.common.menu;

import java.util.function.Supplier;
import li.cil.oc.common.block.entity.MicrocontrollerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

/** EEPROM access is explicit; automated inventories cannot extract the assembled components. */
public final class MicrocontrollerMenu extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> TYPES=DeferredRegister.create(Registries.MENU,"opencomputers");
    public static final Supplier<MenuType<MicrocontrollerMenu>> TYPE=TYPES.register("microcontroller",()->IMenuTypeExtension.create(MicrocontrollerMenu::client));
    public final MicrocontrollerBlockEntity host;
    private final Container eeprom;
    private static MicrocontrollerMenu client(int id,Inventory inventory,RegistryFriendlyByteBuf data){var entity=inventory.player.level().getBlockEntity(data.readBlockPos());return new MicrocontrollerMenu(id,inventory,entity instanceof MicrocontrollerBlockEntity host?host:null);}
    public static void register(IEventBus bus){TYPES.register(bus);}
    public MicrocontrollerMenu(int id,Inventory player,MicrocontrollerBlockEntity host){super(TYPE.get(),id);this.host=host;
        eeprom=host==null||player.player.level().isClientSide?new SimpleContainer(1):new Container(){public int getContainerSize(){return 1;}public boolean isEmpty(){return getItem(0).isEmpty();}public ItemStack getItem(int slot){int actual=host.eepromSlot();return actual<0?ItemStack.EMPTY:host.getItem(actual);}public ItemStack removeItem(int slot,int count){return count>0?host.changeEEPROM(ItemStack.EMPTY):ItemStack.EMPTY;}public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,1);}public void setItem(int slot,ItemStack value){host.changeEEPROM(value);}public void setChanged(){host.setChanged();}public boolean stillValid(Player player){return host.canInteract(player);}public void clearContent(){host.changeEEPROM(ItemStack.EMPTY);}};
        addSlot(new Slot(eeprom,0,80,35){@Override public boolean mayPlace(ItemStack stack){return MicrocontrollerBlockEntity.isEEPROM(stack);}@Override public int getMaxStackSize(){return 1;}});for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,9+row*9+col,8+col*18,84+row*18));for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,142));
    }
    @Override public boolean stillValid(Player player){return host!=null&&host.canInteract(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;if(host!=null&&!player.level().isClientSide)host.runtime.saveComponents();Slot source=slots.get(index);if(!source.hasItem())return ItemStack.EMPTY;ItemStack stack=source.getItem(),copy=stack.copy();if(!(index==0?moveItemStackTo(stack,1,slots.size(),true):moveItemStackTo(stack,0,1,false)))return ItemStack.EMPTY;if(stack.isEmpty())source.setByPlayer(ItemStack.EMPTY);else source.setChanged();source.onTake(player,stack);return copy;}
}
