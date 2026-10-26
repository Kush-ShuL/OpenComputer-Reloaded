package li.cil.oc.common.menu;

import li.cil.oc.common.item.FunctionalUpgradeItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

/** Ghost catalogue slots never transfer or consume the player's real items. */
public final class DatabaseMenu extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> TYPES=DeferredRegister.create(Registries.MENU,"opencomputers");
    public static final Supplier<MenuType<DatabaseMenu>> TYPE=TYPES.register("database",()->IMenuTypeExtension.create((id,inventory,data)->new DatabaseMenu(id,inventory,data.readVarInt())));
    public final int tier;private final Inventory playerInventory;private final int lockedIndex;private final ItemStack database;private final SimpleContainer entries;private boolean loading;
    public static void register(IEventBus bus){TYPES.register(bus);}
    public DatabaseMenu(int id,Inventory inventory,int index){super(TYPE.get(),id);playerInventory=inventory;lockedIndex=index;database=inventory.getItem(index);if(!(database.getItem() instanceof FunctionalUpgradeItem item)||item.kind!=FunctionalUpgradeItem.Kind.DATABASE)throw new IllegalArgumentException("not a database");tier=item.tier();int rows=new int[]{3,5,9}[tier],count=rows*rows;entries=new SimpleContainer(count){@Override public void setChanged(){super.setChanged();if(!loading)save();}};loading=true;CompoundTag data=database.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data");ListTag list=data.getList("oc:items",Tag.TAG_COMPOUND);for(int slot=0;slot<Math.min(count,list.size());slot++)entries.setItem(slot,ItemStack.parseOptional(inventory.player.level().registryAccess(),list.getCompound(slot)));loading=false;int offset=8+new int[]{3,2,0}[tier]*18;
        for(int row=0;row<rows;row++)for(int col=0;col<rows;col++)addSlot(new Slot(entries,row*rows+col,offset+col*18,offset+row*18){@Override public boolean mayPickup(Player player){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,8+col*18,174+row*18));for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+col*18,232));
    }
    private void save(){CompoundTag root=database.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag(),data=root.getCompound("oc:data");ListTag list=new ListTag();for(int i=0;i<entries.getContainerSize();i++)list.add(entries.getItem(i).saveOptional(playerInventory.player.level().registryAccess()));data.put("oc:items",list);root.put("oc:data",data);database.set(DataComponents.CUSTOM_DATA,CustomData.of(root));playerInventory.setChanged();}
    @Override public void clicked(int index,int button,ClickType type,Player player){if(type==ClickType.SWAP&&button==lockedIndex)return;if(index>=0&&index<entries.getContainerSize()){entries.setItem(index,getCarried().copy());return;}if(index>=0&&index<slots.size()){Slot slot=slots.get(index);if(slot.container==playerInventory&&slot.getContainerSlot()==lockedIndex)return;}super.clicked(index,button,type,player);}
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return slot.container!=entries&&super.canTakeItemForPickAll(stack,slot);}
    @Override public boolean stillValid(Player player){return player==playerInventory.player&&playerInventory.getItem(lockedIndex)==database;}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<entries.getContainerSize()||index<0||index>=slots.size())return ItemStack.EMPTY;Slot source=slots.get(index);if(source.container==playerInventory&&source.getContainerSlot()==lockedIndex)return ItemStack.EMPTY;if(source.hasItem())for(int i=0;i<entries.getContainerSize();i++)if(entries.getItem(i).isEmpty()){entries.setItem(i,source.getItem().copyWithCount(1));break;}return ItemStack.EMPTY;}
}
