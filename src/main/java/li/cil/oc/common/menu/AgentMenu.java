package li.cil.oc.common.menu;

import li.cil.oc.server.agent.*;
import li.cil.oc.common.block.entity.RobotBlockEntity;
import li.cil.oc.common.entity.DroneEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

/** Original robot 4x4 scrolling inventory and drone 4x2 inventory layouts. */
public final class AgentMenu extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> TYPES=DeferredRegister.create(Registries.MENU,"opencomputers");
    public static final Supplier<MenuType<AgentMenu>> TYPE=TYPES.register("agent",()->IMenuTypeExtension.create(AgentMenu::client));
    public final AgentHost host;
    public final boolean robot,hasScreen;
    public final int deltaY;
    private int scroll;
    private final int agentSlots;
    private static AgentMenu client(int id,Inventory inventory,RegistryFriendlyByteBuf data){boolean robot=data.readBoolean();AgentHost host;if(robot){var entity=inventory.player.level().getBlockEntity(data.readBlockPos());if(!(entity instanceof RobotBlockEntity proxy))throw new IllegalStateException("Robot disappeared before opening its inventory");host=proxy.controller();}else{var entity=inventory.player.level().getEntity(data.readVarInt());if(!(entity instanceof DroneEntity drone))throw new IllegalStateException("Drone disappeared before opening its inventory");host=drone;}boolean screen=data.readBoolean();CompoundTag tag=data.readNbt();if(robot&&tag!=null){RobotController controller=(RobotController)host;controller.load(tag,inventory.player.level().registryAccess());li.cil.oc.client.AgentClient.applyScreen(controller,tag);host=controller.proxy.controller();}return new AgentMenu(id,inventory,host,screen);}
    public static void writeOpen(AgentHost host,RegistryFriendlyByteBuf data){data.writeBoolean(host.robot());if(host.robot())data.writeBlockPos(host.blockPosition());else data.writeVarInt(((DroneEntity)host).getId());data.writeBoolean(host.runtime().screen()!=null);CompoundTag tag=new CompoundTag();if(host instanceof RobotController robot)robot.writeClient(tag,host.world().registryAccess());data.writeNbt(tag);}
    public static void register(IEventBus bus){TYPES.register(bus);}
    public AgentMenu(int id,Inventory inventory,AgentHost host){this(id,inventory,host,host.runtime().screen()!=null);}
    private AgentMenu(int id,Inventory inventory,AgentHost host,boolean hasScreen){super(TYPE.get(),id);this.host=host;robot=host.robot();this.hasScreen=hasScreen;deltaY=robot&&!hasScreen?148:0;
        if(robot)for(int index=0;index<4;index++)addSlot(new Slot(host.equipmentInventory(),index,170+index*18,232-deltaY){@Override public boolean mayPlace(ItemStack stack){return host instanceof RobotController controller&&controller.canPlaceItem(getContainerSlot(),stack);}@Override public int getMaxStackSize(){return getContainerSlot()==0?64:1;}});
        Container view=new Container(){public int getContainerSize(){return robot?16:8;}public boolean isEmpty(){return host.mainInventory().isEmpty();}private int actual(int slot){return scroll+slot;}public ItemStack getItem(int slot){return actual(slot)<host.mainInventory().getContainerSize()?host.mainInventory().getItem(actual(slot)):ItemStack.EMPTY;}public ItemStack removeItem(int slot,int count){return host.mainInventory().removeItem(actual(slot),count);}public ItemStack removeItemNoUpdate(int slot){return host.mainInventory().removeItemNoUpdate(actual(slot));}public void setItem(int slot,ItemStack stack){if(canPlaceItem(slot,stack))host.mainInventory().setItem(actual(slot),stack);}public void setChanged(){host.inventoryChanged();}public boolean stillValid(Player player){return AgentMenu.this.stillValid(player);}public boolean canPlaceItem(int slot,ItemStack stack){return actual(slot)<host.mainInventory().getContainerSize();}public void clearContent(){host.mainInventory().clearContent();}};
        for(int index=0;index<(robot?16:8);index++){int x=(robot?170:98)+(index%4)*18,y=(robot?156-deltaY:8)+(index/4)*18;addSlot(new Slot(view,index,x,y){@Override public boolean isActive(){return scroll+getContainerSlot()<host.mainInventory().getContainerSize();}@Override public boolean mayPlace(ItemStack stack){return isActive();}});}
        agentSlots=slots.size();int x=robot?6:8,y=robot?174-deltaY:66;
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,x+18*col,y+18*row));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,x+18*col,y+58));
    }
    public int scroll(){return scroll;}
    public void scroll(int rows){scroll=Math.max(0,Math.min(Math.max(0,(host.mainInventory().getContainerSize()-16+3)/4)*4,rows*4));broadcastChanges();}
    @Override public boolean stillValid(Player player){return host.world()==player.level()&&player.distanceToSqr(host.xPosition(),host.yPosition(),host.zPosition())<=64&&(!robot||((RobotController)host).stillValid(player))&&(!(host instanceof DroneEntity drone)||drone.canInteract(player));}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot source=slots.get(index);if(!source.hasItem())return ItemStack.EMPTY;ItemStack stack=source.getItem(),copy=stack.copy();if(!(index<agentSlots?moveItemStackTo(stack,agentSlots,slots.size(),true):moveItemStackTo(stack,0,agentSlots,false)))return ItemStack.EMPTY;if(stack.isEmpty())source.setByPlayer(ItemStack.EMPTY);else source.setChanged();source.onTake(player,stack);return copy;}
    @Override public void removed(Player player){super.removed(player);if(!player.level().isClientSide)for(var keyboard:host.runtime().keyboards())keyboard.releasePressedKeys(player);}
}
