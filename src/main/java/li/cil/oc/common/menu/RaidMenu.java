package li.cil.oc.common.menu;

import java.util.function.Supplier;
import li.cil.oc.api.Driver;
import li.cil.oc.common.block.entity.RaidBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Original sockets at 60/80/100,23 and the original player inventory coordinates. */
public final class RaidMenu extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> TYPES=DeferredRegister.create(Registries.MENU,"opencomputers");
    public static final Supplier<MenuType<RaidMenu>> TYPE=TYPES.register("raid",()->IMenuTypeExtension.create((id,player,data)->new RaidMenu(id,player,new SimpleContainer(3),data.readBlockPos())));
    public final BlockPos position;
    private final Container inventory;
    public static void register(IEventBus bus){TYPES.register(bus);}
    public RaidMenu(int id,Inventory player,RaidBlockEntity raid){this(id,player,raid,raid.getBlockPos());}
    private RaidMenu(int id,Inventory player,Container inventory,BlockPos position){super(TYPE.get(),id);this.inventory=inventory;this.position=position;inventory.startOpen(player.player);
        for(int slot=0;slot<3;slot++)addSlot(new Slot(inventory,slot,60+slot*20,23){@Override public boolean mayPlace(ItemStack stack){var driver=Driver.driverFor(stack,RaidBlockEntity.class);return driver!=null&&li.cil.oc.api.driver.item.Slot.HDD.equals(driver.slot(stack));}@Override public int getMaxStackSize(){return 1;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,9+row*9+col,8+col*18,84+row*18));for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,142));
    }
    @Override public boolean stillValid(Player player){return inventory.stillValid(player);}
    @Override public void removed(Player player){super.removed(player);inventory.stopOpen(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot source=slots.get(index);if(!source.hasItem())return ItemStack.EMPTY;ItemStack stack=source.getItem(),copy=stack.copy();boolean moved=index<3?moveItemStackTo(stack,3,slots.size(),true):moveItemStackTo(stack,0,3,false);if(!moved)return ItemStack.EMPTY;if(stack.isEmpty())source.setByPlayer(ItemStack.EMPTY);else source.setChanged();source.onTake(player,stack);return copy;}
}
