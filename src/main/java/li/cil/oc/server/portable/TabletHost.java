package li.cil.oc.server.portable;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.network.*;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.common.item.data.TabletData;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** Original tablet wrapper: its computer belongs to this physical stack and its holder. */
public final class TabletHost implements li.cil.oc.api.internal.Tablet, Container {
    public final String id;
    public final TabletData data;
    public final PortableRuntime runtime;
    private ItemStack stack;
    private final Level world;
    private Player holder;
    private final TabletControl control;
    private final AtomicBoolean dirty=new AtomicBoolean(),displayDirty=new AtomicBoolean(true);
    private boolean closed,lastRunning;
    private long lastTick=Long.MIN_VALUE;
    public long lastAccess;
    public TabletHost(String id,ItemStack stack,Player holder){this.id=id;this.stack=stack;this.holder=holder;world=holder.level();data=new TabletData(stack,world.registryAccess());runtime=new PortableRuntime(this,()->data.items);
        control=new TabletControl();CompoundTag root=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();control.load(root.getCompound("oc:component"),world.registryAccess());runtime.load(root.getCompound("oc:data"));runtime.initialize(control.node());
        control.node().changeBuffer(Math.max(0,data.energy-control.node().globalBuffer()));lastRunning=machine().isRunning();touch(stack,holder);save(false);
    }
    public void touch(ItemStack stack,Player holder){if(this.holder!=holder){li.cil.oc.common.network.PortableNetworking.close(this);displayDirty.set(true);}this.stack=stack;this.holder=holder;lastAccess=holder.getServer()==null?0:holder.getServer().getTickCount();}
    public ItemStack stack(){return stack;}
    public boolean closed(){return closed;}
    @Override public Player player(){return holder;}
    @Override public Level world(){return world;}
    @Override public double xPosition(){return holder.getX();}
    @Override public double yPosition(){return holder.getEyeY();}
    @Override public double zPosition(){return holder.getZ();}
    @Override public void markChanged(){dirty.set(true);}
    @Override public li.cil.oc.server.machine.Machine machine(){return runtime.machine();}
    @Override public Iterable<ItemStack> internalComponents(){return Arrays.asList(data.items);}
    @Override public int componentSlot(String address){return runtime.componentSlot(address);}
    @Override public void onMachineConnect(Node node){if(node==machine().node())runtime.connect();}
    @Override public void onMachineDisconnect(Node node){if(node==machine().node())runtime.saveComponents();}
    @Override public Direction facing(){return holder.getDirection();}
    @Override public Direction toGlobal(Direction side){if(side==null||side.getAxis()==Direction.Axis.Y)return side;for(int i=0;i<facing().get2DDataValue();i++)side=side.getClockWise();return side;}
    @Override public Direction toLocal(Direction side){if(side==null||side.getAxis()==Direction.Axis.Y)return side;for(int i=0;i<facing().get2DDataValue();i++)side=side.getCounterClockWise();return side;}
    public Connector connector(){return control.node();}
    public void displayChanged(){displayDirty.set(true);markChanged();}
    public void tick(){if(closed||lastTick==world.getGameTime())return;lastTick=world.getGameTime();if(data.tier==3&&world.getGameTime()%Math.max(1,(int)Settings.get().tickFrequency)==0)connector().changeBuffer(Double.POSITIVE_INFINITY);runtime.tick();
        boolean running=machine().isRunning(),runningChanged=lastRunning!=running;if(runningChanged){lastRunning=running;if(running&&runtime.screen()!=null)runtime.screen().setPowerState(true);displayDirty.set(true);dirty.set(true);}
        data.isRunning=running;data.energy=connector().globalBuffer();data.maxEnergy=connector().globalBufferSize();
        if(world.getGameTime()%100==0)runtime.saveComponents();
        if(runningChanged||world.getGameTime()%20==0){dirty.set(false);data.save(stack,world.registryAccess());holder.getInventory().setChanged();}
        if(displayDirty.getAndSet(false))li.cil.oc.common.network.PortableNetworking.publish(this);
    }
    public void save(boolean stopState){if(closed)return;CompoundTag root=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        CompoundTag state=runtime.save(world.registryAccess());if(stopState)state.remove("state");root.put("oc:data",state);CompoundTag node=new CompoundTag();control.save(node,world.registryAccess());root.put("oc:component",node);
        data.energy=connector().globalBuffer();data.maxEnergy=connector().globalBufferSize();data.isRunning=!stopState&&machine().isRunning();data.save(root,world.registryAccess());root.putString("oc:tablet",id);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));data.applyItemProperties(stack);
    }
    public void close(boolean preserveState){if(closed)return;li.cil.oc.common.network.PortableNetworking.close(this);save(!preserveState);closed=true;runtime.close();if(!preserveState){runtime.saveComponents();data.save(stack,world.registryAccess());}}
    public double charge(double amount,boolean simulate){if(amount<=0)return amount;double accepted=Math.min(amount,Math.max(0,connector().globalBufferSize()-connector().globalBuffer()));if(!simulate&&accepted>0){connector().changeBuffer(accepted);markChanged();displayDirty.set(true);}return amount-accepted;}
    @Override public int getContainerSize(){return 1;}
    @Override public boolean isEmpty(){return data.items[31].isEmpty();}
    @Override public ItemStack getItem(int slot){return slot==0?data.items[31]:ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){if(slot!=0||count<=0||data.items[31].isEmpty())return ItemStack.EMPTY;runtime.remove(31);ItemStack removed=data.items[31].split(count);if(!removed.isEmpty())changedInventory();return removed;}
    @Override public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,1);}
    @Override public void setItem(int slot,ItemStack value){if(slot!=0||data.items[31]==value||data.items[31].isEmpty()&&value.isEmpty()||(!value.isEmpty()&&!canPlaceItem(slot,value)))return;runtime.remove(31);data.items[31]=value.isEmpty()?ItemStack.EMPTY:value.copyWithCount(1);changedInventory();}
    private void changedInventory(){runtime.install(31);save(false);holder.getInventory().setChanged();displayDirty.set(true);}
    @Override public void setChanged(){markChanged();}
    @Override public boolean stillValid(Player player){return !closed&&player==holder&&player.level()==world&&TabletManager.isInInventory(player,stack)&&machine().canInteract(player.getGameProfile().getName());}
    @Override public boolean canPlaceItem(int slot,ItemStack candidate){if(slot!=0||data.tier==0||candidate.isEmpty()||data.container.isEmpty())return false;var bay=Driver.driverFor(data.container,getClass());var driver=Driver.driverFor(candidate,getClass());return bay instanceof li.cil.oc.api.driver.item.Container container&&driver!=null&&!(candidate.getItem() instanceof net.minecraft.world.item.BlockItem block&&block.getBlock() instanceof li.cil.oc.common.block.ScreenBlock)&&driver.slot(candidate).equals(container.providedSlot(data.container))&&driver.tier(candidate)<=container.providedTier(data.container);}
    @Override public void clearContent(){setItem(0,ItemStack.EMPTY);}
    public void analyze(net.minecraft.core.BlockPos position,Direction side,double x,double y,double z){if(!machine().isRunning()||!world.hasChunkAt(position))return;CompoundTag result=new CompoundTag();machine().node().sendToReachable("tablet.use",result,stack,holder,position,side,x,y,z);if(!result.isEmpty())machine().signal("tablet_use",result);}
    public final class TabletControl extends AbstractManagedEnvironment implements DeviceInfo {
        private final ComponentConnector node=li.cil.oc.api.Network.newNode(this,Visibility.Network).withComponent("tablet").withConnector(Settings.get().bufferTablet).create();
        private TabletControl(){setNode(node);}
        @Override public ComponentConnector node(){return node;}
        @Callback(doc="function():number -- Gets the pitch of the player holding the tablet.") public Object[] getPitch(Context context,Arguments args){return new Object[]{holder.getXRot()};}
        @Callback(doc="function():number -- Gets the yaw of the player holding the tablet.") public Object[] getYaw(Context context,Arguments args){return new Object[]{holder.getYRot()};}
        @Override public java.util.Map<String,String> getDeviceInfo(){return java.util.Map.of(DeviceAttribute.Class,DeviceClass.System,DeviceAttribute.Description,"Tablet",DeviceAttribute.Vendor,li.cil.oc.Constants.DeviceInfo.DefaultVendor,DeviceAttribute.Product,"Jogger",DeviceAttribute.Capacity,Integer.toString(data.items.length));}
    }
}
