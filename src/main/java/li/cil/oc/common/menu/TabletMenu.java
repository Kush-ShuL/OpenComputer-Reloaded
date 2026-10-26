package li.cil.oc.common.menu;

import java.util.function.Supplier;
import li.cil.oc.api.Driver;
import li.cil.oc.server.portable.TabletHost;
import li.cil.oc.server.portable.TabletManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TabletMenu extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> TYPES=DeferredRegister.create(Registries.MENU,"opencomputers");
    public static final Supplier<MenuType<TabletMenu>> TYPE=TYPES.register("tablet",()->IMenuTypeExtension.create(TabletMenu::client));
    public final String tabletId;
    public final ItemStack bay;
    private final TabletHost host;
    private final Container inventory;
    private static TabletMenu client(int id,Inventory player,RegistryFriendlyByteBuf data){return new TabletMenu(id,player,null,data.readUtf(),ItemStack.OPTIONAL_STREAM_CODEC.decode(data));}
    public static void register(IEventBus bus){TYPES.register(bus);}
    public TabletMenu(int id,Inventory player,TabletHost host){this(id,player,host,host.id,host.data.container);}
    private TabletMenu(int id,Inventory player,TabletHost host,String tabletId,ItemStack bay){super(TYPE.get(),id);this.host=host;this.tabletId=tabletId;this.bay=bay;inventory=host==null?new SimpleContainer(1):host;
        addSlot(new Slot(inventory,0,80,35){@Override public boolean mayPlace(ItemStack stack){if(host!=null)return host.canPlaceItem(0,stack);var driver=Driver.driverFor(stack,TabletHost.class);var container=Driver.driverFor(bay,TabletHost.class);return driver!=null&&container instanceof li.cil.oc.api.driver.item.Container slot&&driver.slot(stack).equals(slot.providedSlot(bay))&&driver.tier(stack)<=slot.providedTier(bay);}@Override public int getMaxStackSize(){return 1;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,9+row*9+col,8+col*18,84+row*18));for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,142));
    }
    @Override public boolean stillValid(Player player){return host==null||host.stillValid(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot source=slots.get(index);if(!source.hasItem())return ItemStack.EMPTY;ItemStack value=source.getItem();if(TabletManager.id(value).equals(tabletId))return ItemStack.EMPTY;ItemStack copy=value.copy();if(!(index==0?moveItemStackTo(value,1,slots.size(),true):moveItemStackTo(value,0,1,false)))return ItemStack.EMPTY;if(value.isEmpty())source.setByPlayer(ItemStack.EMPTY);else source.setChanged();source.onTake(player,value);return copy;}
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(slot>=0&&slot<slots.size()&&TabletManager.id(slots.get(slot).getItem()).equals(tabletId))return;if(type==ClickType.SWAP){ItemStack target=button==40?player.getOffhandItem():button>=0&&button<9?player.getInventory().getItem(button):ItemStack.EMPTY;if(TabletManager.id(target).equals(tabletId))return;}super.clicked(slot,button,type,player);}
}
