package li.cil.oc.server.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import li.cil.oc.Constants;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.component.RackBusConnectable;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.ComponentHost;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.common.block.entity.RackBlockEntity;
import li.cil.oc.common.inventory.ServerInventory;
import li.cil.oc.common.menu.ServerMenu;
import li.cil.oc.common.redstone.RedstoneHost;
import li.cil.oc.common.redstone.RedstoneState;
import li.cil.oc.server.PacketSender.ActivityListener;
import li.cil.oc.server.machine.Machine;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** A rack blade is the machine host, distinct from the rack's isolated external buses. */
public final class Server extends ServerInventory implements li.cil.oc.api.internal.Server, Analyzable, ComponentHost, DeviceInfo, RedstoneHost, ActivityListener {
    private final RackBlockEntity rack;
    private final int rackSlot;
    private final ManagedEnvironment[] environments;
    private Machine machine;
    private boolean initializing,initialized,disposed,wasRunning,hadErrored;
    private long lastFileSystemAccess,lastNetworkActivity;
    public Server(RackBlockEntity rack,int slot){super(rack.getItem(slot),rack.world().registryAccess());this.rack=rack;rackSlot=slot;environments=new ManagedEnvironment[getContainerSize()];}
    @Override public RackBlockEntity rack(){return rack;}
    @Override public int slot(){return rackSlot;}
    @Override public Level world(){return rack.world();}
    @Override public double xPosition(){return rack.xPosition();}
    @Override public double yPosition(){return rack.yPosition();}
    @Override public double zPosition(){return rack.zPosition();}
    @Override public void markChanged(){rack.markChanged(rackSlot);}
    @Override public Direction facing(){return rack.facing();}
    @Override public Direction toGlobal(Direction side){return rack.toGlobal(side);}
    @Override public Direction toLocal(Direction side){return rack.toLocal(side);}
    @Override public RedstoneState redstoneState(){return rack.redstoneState();}
    @Override public Machine machine(){if(machine==null&&!world().isClientSide&&!disposed)machine=(Machine)li.cil.oc.api.Machine.create(this);return machine;}
    @Override public Node node(){Machine value=machine();return value==null?null:value.node();}
    @Override public Iterable<ItemStack> internalComponents(){return List.copyOf(items);}
    @Override public Iterable<Environment> getComponents(){return Arrays.stream(environments).filter(java.util.Objects::nonNull).map(value->(Environment)value).toList();}
    @Override public int componentSlot(String address){for(int i=0;i<environments.length;i++)if(environments[i]!=null&&environments[i].node()!=null&&address.equals(environments[i].node().address()))return i;return -1;}
    private CompoundTag data(li.cil.oc.api.driver.DriverItem driver,ItemStack stack){CompoundTag value=driver.dataTag(stack);return value!=null?value:stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data");}
    private void create(int slot){
        if(items.get(slot).isEmpty())return;var driver=Driver.driverFor(items.get(slot),getClass());if(driver==null)return;
        ManagedEnvironment environment=driver.createEnvironment(items.get(slot),rack);environments[slot]=environment;
        if(environment!=null){environment.load(data(driver,items.get(slot)),registries);if(environment.node()!=null&&node().network()!=null)node().connect(environment.node());}
    }
    private void flush(int slot){ManagedEnvironment environment=environments[slot];ItemStack stack=items.get(slot);if(environment==null||stack.isEmpty())return;var driver=Driver.driverFor(stack,getClass());if(driver!=null){CompoundTag tag=data(driver,stack);environment.save(tag,registries);driver.saveDataTag(stack,tag);}}
    private void removeEnvironment(int slot){flush(slot);ManagedEnvironment old=environments[slot];environments[slot]=null;if(old!=null&&old.node()!=null)old.node().remove();}
    private void initialize(){
        if(initialized||world().isClientSide||disposed)return;initialized=true;initializing=true;
        try{machine();for(int i=0;i<items.size();i++)create(i);machine.onHostChanged();}finally{initializing=false;}
    }
    @Override protected void beforeChange(int slot){if(initialized&&!initializing&&!world().isClientSide)removeEnvironment(slot);}
    @Override protected void afterChange(int slot){
        if(initialized&&!initializing&&!world().isClientSide){create(slot);machine.onHostChanged();if(layout(tier())[slot].slot().equals("cpu"))machine.stop();rack.reconnectMountable(rackSlot);}
        setChanged();
    }
    @Override public void setChanged(){if(rack==null)return;saveItems(container,items,registries);rack.markChanged(rackSlot);}
    @Override public boolean stillValid(Player player){return !disposed&&rack.getMountable(rackSlot)==this&&rack.stillValid(player)&&machine().canInteract(player.getGameProfile().getName());}
    @Override public void onMachineConnect(Node connected){if(machine!=null&&connected==machine.node())for(ManagedEnvironment environment:environments)if(environment!=null&&environment.node()!=null)machine.node().connect(environment.node());}
    @Override public void onMachineDisconnect(Node disconnected){if(machine!=null&&disconnected==machine.node())for(int i=0;i<environments.length;i++)flush(i);}
    @Override public void onConnect(Node node){onMachineConnect(node);}
    @Override public void onDisconnect(Node node){onMachineDisconnect(node);}
    @Override public void onMessage(Message message){}
    @Override public void onComponentActivity(String address,boolean network){if(network)lastNetworkActivity=System.currentTimeMillis();else lastFileSystemAccess=System.currentTimeMillis();rack.markChanged(rackSlot);}
    @Override public Map<String,String> getDeviceInfo(){return Map.of("class","system","description","Server","vendor",Constants.DeviceInfo.DefaultVendor,"product","Blader","capacity",Integer.toString(getContainerSize()));}
    @Override public CompoundTag getData(){CompoundTag tag=new CompoundTag();tag.putBoolean("isRunning",wasRunning);tag.putBoolean("hasErrored",hadErrored);tag.putLong("lastFileSystemAccess",lastFileSystemAccess);tag.putLong("lastNetworkActivity",lastNetworkActivity);net.minecraft.nbt.ListTag addresses=new net.minecraft.nbt.ListTag();for(ManagedEnvironment environment:environments)if(environment!=null&&environment.node()!=null&&environment.node().address()!=null)addresses.add(net.minecraft.nbt.StringTag.valueOf(environment.node().address()));tag.put("componentAddresses",addresses);return tag;}
    private List<RackBusConnectable> connectables(){List<RackBusConnectable> values=new ArrayList<>();for(ManagedEnvironment environment:environments)if(environment instanceof RackBusConnectable connectable)values.add(connectable);return values;}
    @Override public int getConnectableCount(){return connectables().size();}
    @Override public RackBusConnectable getConnectableAt(int index){List<RackBusConnectable> values=connectables();return index>=0&&index<values.size()?values.get(index):null;}
    @Override public boolean onActivate(Player player,InteractionHand hand,ItemStack held,float hitX,float hitY){
        if(player instanceof ServerPlayer server&&stillValid(player)){if(player.isShiftKeyDown()){if(!machine().isRunning())machine().start();}else ServerMenu.openMounted(server,this);}return true;
    }
    @Override public boolean canUpdate(){return true;}
    @Override public void update(){
        if(world().isClientSide||disposed)return;initialize();if(node().network()==null)Network.joinNewNetwork(node());
        if(tier()==3)machine.node().changeBuffer(Double.POSITIVE_INFINITY);
        machine.update();for(ManagedEnvironment environment:environments)if(environment!=null&&environment.canUpdate())environment.update();
        boolean running=machine.isRunning(),errored=machine.lastError()!=null;
        if(running!=wasRunning||errored!=hadErrored){wasRunning=running;hadErrored=errored;rack.markChanged(rackSlot);}
    }
    @Override public EnumSet<State> getCurrentState(){return machine!=null&&machine.isRunning()?EnumSet.of(State.IsWorking):EnumSet.noneOf(State.class);}
    @Override public Node[] onAnalyze(Player player,Direction side,float x,float y,float z){return new Node[]{node()};}
    @Override public void load(CompoundTag tag){load(tag,registries);}
    @Override public void load(CompoundTag tag,HolderLookup.Provider registries){
        if(world().isClientSide)return;initializing=true;
        try{if(initialized)for(int i=0;i<environments.length;i++)removeEnvironment(i);ServerInventory.loadItems(tag,items,registries);initialized=false;initialize();if(tag.contains("machine"))machine.load(tag.getCompound("machine"),registries);}
        finally{initializing=false;}
    }
    @Override public void save(CompoundTag tag){save(tag,registries);}
    @Override public void save(CompoundTag tag,HolderLookup.Provider registries){
        if(world().isClientSide)return;initialize();for(int i=0;i<environments.length;i++)flush(i);saveItems(tag,items,registries);
        CompoundTag machineTag=new CompoundTag();machine.save(machineTag,registries);tag.put("machine",machineTag);
    }
    public void dispose(){if(disposed)return;for(int i=0;i<environments.length;i++)flush(i);disposed=true;if(machine!=null){machine.stop();machine.node().remove();}for(ManagedEnvironment environment:environments)if(environment!=null&&environment.node()!=null)environment.node().remove();}
}
