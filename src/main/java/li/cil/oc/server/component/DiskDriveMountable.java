package li.cil.oc.server.component;

import java.util.EnumSet;
import java.util.Map;
import li.cil.oc.Constants;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.component.RackBusConnectable;
import li.cil.oc.api.component.RackMountable;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.util.Lifecycle;
import li.cil.oc.common.block.entity.RackBlockEntity;
import li.cil.oc.common.inventory.RackDiskInventory;
import li.cil.oc.common.inventory.ServerInventory;
import li.cil.oc.common.menu.RackDiskMenu;
import li.cil.oc.util.InventoryUtils;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Source-faithful rack drive: one filesystem on the selected primary bus, no secondary buses. */
public final class DiskDriveMountable extends RackDiskInventory implements RackMountable,Analyzable,DeviceInfo,Lifecycle {
    public final RackBlockEntity rack;
    public final int slot;
    private final li.cil.oc.api.network.Component node;
    private volatile ManagedEnvironment medium;
    private boolean disposed,loaded,removedDisk;
    private volatile long lastAccess;
    public DiskDriveMountable(RackBlockEntity rack,int slot){
        super(rack.getItem(slot),rack.world().registryAccess(),()->rack.markChanged(slot));this.rack=rack;this.slot=slot;
        node=Network.newNode(this,Visibility.Network).withComponent("disk_drive").create();
    }
    @Override public Node node(){return node;}
    public Node filesystemNode(){return medium==null?null:medium.node();}
    public void recordAccess(){lastAccess=System.currentTimeMillis();}
    private static void lifecycle(ManagedEnvironment environment,LifecycleState state){if(environment instanceof Lifecycle lifecycle)lifecycle.onLifecycleStateChange(state);}
    private void mountMedium(){
        if(disposed||!loaded||medium!=null||isEmpty()||rack.world().isClientSide)return;
        ItemStack disk=items.getFirst();var driver=Driver.driverFor(disk);if(driver==null||!li.cil.oc.api.driver.item.Slot.Floppy.equals(driver.slot(disk)))return;
        medium=driver.createEnvironment(disk,rack);if(medium==null)return;
        lifecycle(medium,LifecycleState.Constructing);CompoundTag data=driver.dataTag(disk);if(data==null)data=disk.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("oc:data");medium.load(data,registries);
        lifecycle(medium,LifecycleState.Initializing);
        if(node.network()==null)Network.joinNewNetwork(node);
        if(medium.node() instanceof li.cil.oc.api.network.Component component)component.setVisibility(Visibility.Network);
        if(medium.node()!=null)node.connect(medium.node());
        lifecycle(medium,LifecycleState.Initialized);
    }
    public synchronized void flushMedium(){
        if(medium==null||isEmpty())return;var driver=Driver.driverFor(items.getFirst());if(driver==null)return;
        CompoundTag tag=new CompoundTag();medium.save(tag,registries);driver.saveDataTag(items.getFirst(),tag);
    }
    private void unmountMedium(boolean preserveSavedState){
        if(medium==null)return;
        lifecycle(medium,LifecycleState.Disposing);
        if(medium.node()!=null)medium.node().remove();
        // Physically removing a disk closes descriptors before serializing, as in ComponentInventory.
        if(!preserveSavedState)flushMedium();
        if(medium.node()!=null)medium.node().remove();
        lifecycle(medium,LifecycleState.Disposed);medium=null;
    }
    @Override protected synchronized void beforeChange(){removedDisk=!isEmpty();unmountMedium(false);}
    @Override protected synchronized void afterChange(){
        if(loaded&&!disposed){mountMedium();if(removedDisk)play("floppy_eject");if(!isEmpty())play("floppy_insert");}
        setChanged();
    }
    @Override public synchronized void setChanged(){flushMedium();super.setChanged();}
    private void play(String sound){li.cil.oc.common.DiskSounds.play(rack,sound);}
    @Override public boolean stillValid(Player player){return !disposed&&rack.getMountable(slot)==this&&rack.stillValid(player);}
    @Override public void onConnect(Node connected){if(connected==node&&medium!=null&&medium.node()!=null)node.connect(medium.node());}
    @Override public void onDisconnect(Node disconnected){if(disconnected==node&&medium!=null&&medium.node()!=null)medium.node().remove();}
    @Override public void onMessage(li.cil.oc.api.network.Message message){}
    @Override public boolean canUpdate(){return false;}
    @Override public void update(){}
    @Override public Map<String,String> getDeviceInfo(){return Map.of("class","disk","description","Floppy disk drive","vendor",Constants.DeviceInfo.DefaultVendor,"product","RackDrive 100 Rev. 2");}
    @Override public Node[] onAnalyze(Player player,Direction side,float x,float y,float z){Node fs=filesystemNode();return fs==null?null:new Node[]{fs};}
    @Callback(doc="function():boolean -- Checks whether some medium is currently in the drive.")
    public Object[] isEmpty(Context context,Arguments args){return new Object[]{filesystemNode()==null};}
    @Callback(doc="function([velocity:number]):boolean -- Eject the currently present medium from the drive.")
    public Object[] eject(Context context,Arguments args){return new Object[]{ejectMedium(args.optDouble(0,0))};}
    @Callback(doc="function():string -- Return the internal floppy disk address.")
    public Object[] media(Context context,Arguments args){Node fs=filesystemNode();return fs==null?new Object[]{null,"drive is empty"}:new Object[]{fs.address()};}
    public synchronized boolean ejectMedium(double velocity){
        if(disposed)return false;ItemStack disk=removeItem(0,1);if(disk.isEmpty())return false;
        var entity=InventoryUtils.spawnStackInWorld(rack.world(),rack.getBlockPos(),disk,rack.facing());
        if(entity!=null){double speed=Double.isFinite(velocity)?Math.clamp(velocity,0,1):0;Direction side=rack.facing();entity.setDeltaMovement(entity.getDeltaMovement().add(side.getStepX()*speed,side.getStepY()*speed,side.getStepZ()*speed));}return true;
    }
    @Override public boolean onActivate(Player player,InteractionHand hand,ItemStack held,float hitX,float hitY){
        if(!stillValid(player)||player.isSpectator())return false;
        if(player.isShiftKeyDown()){
            boolean occupied=!isEmpty(),insert=canPlaceItem(0,held);if(!occupied&&!insert)return false;
            if(!rack.world().isClientSide){if(occupied)ejectMedium(0);if(insert){setItem(0,held.split(1));player.getInventory().setChanged();}}return true;
        }
        if(player instanceof ServerPlayer server)RackDiskMenu.openMounted(server,this);return true;
    }
    @Override public int getConnectableCount(){return 0;}
    @Override public RackBusConnectable getConnectableAt(int index){return null;}
    @Override public EnumSet<State> getCurrentState(){return EnumSet.noneOf(State.class);}
    @Override public synchronized CompoundTag getData(){
        CompoundTag tag=new CompoundTag();ItemStack disk=items.getFirst();if(!disk.isEmpty())tag.put("disk",disk.save(registries));
        ListTag addresses=new ListTag();Node fs=filesystemNode();if(fs!=null&&fs.address()!=null)addresses.add(StringTag.valueOf(fs.address()));tag.put("componentAddresses",addresses);tag.putLong("lastAccess",lastAccess);return tag;
    }
    @Override public void load(CompoundTag tag){load(tag,registries);}
    @Override public synchronized void load(CompoundTag tag,HolderLookup.Provider provider){
        unmountMedium(true);ServerInventory.loadItems(tag,items,provider);node.load(tag.getCompound("node"));loaded=true;mountMedium();
    }
    @Override public void save(CompoundTag tag){save(tag,registries);}
    @Override public synchronized void save(CompoundTag tag,HolderLookup.Provider provider){
        flushMedium();ServerInventory.saveItems(tag,items,provider);CompoundTag state=new CompoundTag();
        if(node.address()==null){Network.joinNewNetwork(node);node.save(state);node.remove();}else node.save(state);tag.put("node",state);
    }
    /** Unload keeps the already saved VM descriptors; removal saves the closed media in the item. */
    public synchronized void dispose(boolean preserveSavedState){if(disposed)return;disposed=true;unmountMedium(preserveSavedState);node.remove();if(!preserveSavedState)super.setChanged();}
    @Override public void onLifecycleStateChange(LifecycleState state){if(state==LifecycleState.Disposing)dispose(true);}
}
