package li.cil.oc.common.block.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.component.RackBusConnectable;
import li.cil.oc.api.component.RackMountable;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Packet;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.util.Lifecycle;
import li.cil.oc.api.util.StateAware;
import li.cil.oc.common.block.RackBlock;
import li.cil.oc.common.capabilities.EnergyProvider;
import li.cil.oc.common.inventory.ServerInventory;
import li.cil.oc.common.item.RedstoneCardItem;
import li.cil.oc.common.menu.RackMenu;
import li.cil.oc.common.redstone.RedstoneHost;
import li.cil.oc.common.redstone.RedstoneState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Four mountables, five isolated primary buses, twelve secondary sniffers and a bounded relay. */
public final class RackBlockEntity extends BlockEntity implements li.cil.oc.api.internal.Rack, Environment, Analyzable, StateAware, MenuProvider, RedstoneHost, EnergyProvider, RotatableBlockEntity, li.cil.oc.server.PacketSender.ActivityListener {
    private final NonNullList<ItemStack> items=NonNullList.withSize(4,ItemStack.EMPTY);
    private final RackMountable[] mountables=new RackMountable[4];
    private final ItemStack[] clientStacks=new ItemStack[4];
    private final CompoundTag[] lastData=new CompoundTag[4];
    private final Plug[] plugs=new Plug[6];
    private final Node[][] sniffers=new Node[4][3];
    private final int[][] mapping=new int[4][4];
    private final ArrayDeque<QueuedPacket> queue=new ArrayDeque<>();
    private final AtomicBoolean dirty=new AtomicBoolean();
    private final AtomicInteger changed=new AtomicInteger(15);
    private ListTag savedPlugs;
    private boolean initialized,loading,detached,relayEnabled;
    private int relayCooldown=-1;
    private final RedstoneState redstone=new RedstoneState(this,change->{for(RackMountable mountable:this.mountables)if(mountable!=null&&mountable.node()!=null)mountable.node().sendToNeighbors("redstone.changed",change);});
    private record QueuedPacket(Direction side,Packet packet){}
    public RackBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);for(int[] row:mapping)Arrays.fill(row,-1);}
    @Override public Level world(){return level;}
    @Override public double xPosition(){return worldPosition.getX()+.5;}
    @Override public double yPosition(){return worldPosition.getY()+.5;}
    @Override public double zPosition(){return worldPosition.getZ()+.5;}
    @Override public Direction facing(){return getBlockState().getValue(RackBlock.FACING);}
    @Override public Direction toGlobal(Direction side){if(side==null||side.getAxis().isVertical())return side;for(int i=0;i<facing().get2DDataValue();i++)side=side.getClockWise();return side;}
    @Override public Direction toLocal(Direction side){if(side==null||side.getAxis().isVertical())return side;for(int i=0;i<facing().get2DDataValue();i++)side=side.getCounterClockWise();return side;}
    @Override public RedstoneState redstoneState(){return redstone;}
    @Override public boolean rotate(Direction axis){
        Direction target=li.cil.oc.util.RotationHelper.rotate(facing(),axis);if(target.getAxis().isVertical()||target==facing()||level==null)return false;if(level.isClientSide)return true;
        for(int slot=0;slot<4;slot++)for(int connection=0;connection<4;connection++)disconnect(slot,connection);
        for(Plug plug:plugs)if(plug!=null){List<Node> neighbors=new ArrayList<>();for(Node neighbor:plug.node.neighbors())neighbors.add(neighbor);for(Node neighbor:neighbors)plug.node.disconnect(neighbor);}
        boolean rotated=level.setBlock(worldPosition,getBlockState().setValue(RackBlock.FACING,target),3);
        level.invalidateCapabilities(worldPosition);Network.joinOrCreateNetwork(this);for(int slot=0;slot<4;slot++)for(int connection=0;connection<4;connection++)reconnect(slot,connection);if(rotated){redstone.rescan();markChanged();synchronize();}return rotated;
    }
    @Override public Node node(){return null;}
    @Override public boolean canConnect(Direction side){return side!=null&&side!=facing();}
    @Override public Node sidedNode(Direction side){ensurePlugs();return canConnect(side)&&plugs[side.get3DDataValue()]!=null?plugs[side.get3DDataValue()].node:null;}
    private void ensurePlugs(){
        if(level==null||level.isClientSide||detached||isRemoved())return;
        for(Direction side:Direction.values())if(plugs[side.get3DDataValue()]==null){Plug plug=new Plug(side);plugs[side.get3DDataValue()]=plug;if(savedPlugs!=null&&side.ordinal()<savedPlugs.size())plug.node.load(savedPlugs.getCompound(side.ordinal()));}
        savedPlugs=null;
    }
    @Override public void markChanged(){dirty.set(true);}
    @Override public void markChanged(int slot){if(slot>=0&&slot<4)changed.getAndUpdate(value->value|1<<slot);dirty.set(true);}
    @Override public void setChanged(){if(level instanceof ServerLevel server&&!server.getServer().isSameThread()){markChanged();return;}super.setChanged();}
    public int indexOfStack(ItemStack stack){for(int i=0;i<4;i++)if(items.get(i)==stack)return i;return -1;}
    @Override public int indexOfMountable(RackMountable mountable){for(int i=0;i<4;i++)if(mountables[i]==mountable)return i;return -1;}
    @Override public RackMountable getMountable(int slot){return slot>=0&&slot<4?mountables[slot]:null;}
    @Override public CompoundTag getMountableData(int slot){return slot>=0&&slot<4?lastData[slot]:null;}
    public int mapping(int slot,int connection){return slot>=0&&slot<4&&connection>=0&&connection<4?mapping[slot][connection]:-1;}
    public boolean relayEnabled(){return relayEnabled;}
    public void setRelayEnabled(boolean value){relayEnabled=value;markChanged();synchronize();}
    public int presenceMask(){int mask=0;for(int slot=0;slot<4;slot++){RackMountable value=mountables[slot];if(value==null)continue;mask|=1<<(slot*4);for(int bus=0;bus<Math.min(3,value.getConnectableCount());bus++)if(value.getConnectableAt(bus)!=null)mask|=1<<(slot*4+bus+1);}return mask;}
    public void connect(int slot,int connection,int localSide){
        if(slot<0||slot>=4||connection<0||connection>=4)return;
        int side=localSide>=0&&localSide<6&&localSide!=Direction.SOUTH.ordinal()?localSide:-1;
        if(mapping[slot][connection]==side)return;disconnect(slot,connection);mapping[slot][connection]=side;reconnect(slot,connection);markChanged();synchronize();
    }
    private void disconnect(int slot,int connection){
        RackMountable mountable=mountables[slot];int old=mapping[slot][connection];
        if(connection==0&&old>=0&&mountable!=null&&mountable.node()!=null){Node plug=sidedNode(toGlobal(Direction.from3DDataValue(old)));if(plug!=null)mountable.node().disconnect(plug);}
        if(connection>0&&sniffers[slot][connection-1]!=null)sniffers[slot][connection-1].remove();
    }
    private void reconnect(int slot,int connection){
        RackMountable mountable=mountables[slot];int side=mapping[slot][connection];if(mountable==null||side<0)return;
        if(connection==0){Node plug=sidedNode(toGlobal(Direction.from3DDataValue(side)));if(plug!=null&&mountable.node()!=null){if(mountable.node().network()==null)Network.joinNewNetwork(mountable.node());mountable.node().connect(plug);}}
        else if(connection-1<mountable.getConnectableCount()){
            RackBusConnectable connectable=mountable.getConnectableAt(connection-1);if(connectable==null||connectable.node()==null)return;
            if(sniffers[slot][connection-1]==null)sniffers[slot][connection-1]=Network.newNode(this,Visibility.Neighbors).create();
            if(connectable.node().network()==null)Network.joinNewNetwork(connectable.node());connectable.node().connect(sniffers[slot][connection-1]);
        }
    }
    public void reconnectMountable(int slot){for(int connection=0;connection<4;connection++){disconnect(slot,connection);reconnect(slot,connection);}markChanged(slot);}
    private void createMountable(int slot){
        ItemStack stack=items.get(slot);if(stack.isEmpty())return;var driver=Driver.driverFor(stack,getClass());if(driver==null)return;
        var environment=driver.createEnvironment(stack,this);if(!(environment instanceof RackMountable mountable))return;mountables[slot]=mountable;
        lifecycle(mountable,Lifecycle.LifecycleState.Constructing);CompoundTag data=driver.dataTag(stack);if(data==null)data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data");try{mountable.load(data,level.registryAccess());}catch(Throwable error){li.cil.oc.OpenComputers.LOGGER.warn("A rack item component failed while loading",error);}lifecycle(mountable,Lifecycle.LifecycleState.Initializing);
        if(mountable.node()!=null&&mountable.node().network()==null)Network.joinNewNetwork(mountable.node());for(int connection=0;connection<4;connection++)reconnect(slot,connection);lifecycle(mountable,Lifecycle.LifecycleState.Initialized);if(!loading)flush(slot);markChanged(slot);
    }
    private static void lifecycle(RackMountable mountable,Lifecycle.LifecycleState state){if(mountable instanceof Lifecycle value)value.onLifecycleStateChange(state);}
    private void flush(int slot){
        flush(slot,mountables[slot]);
    }
    private void flush(int slot,RackMountable mountable){
        ItemStack stack=items.get(slot);if(mountable==null||stack.isEmpty()||level==null||level.isClientSide)return;
        var driver=Driver.driverFor(stack,getClass());if(driver==null)return;CompoundTag data=driver.dataTag(stack);if(data==null)data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data");mountable.save(data,level.registryAccess());driver.saveDataTag(stack,data);
    }
    public void flushMountables(){for(int slot=0;slot<4;slot++)flush(slot);}
    public void prepareDrops(){for(int slot=0;slot<4;slot++)release(slot);}
    private void release(int slot){
        RackMountable mountable=mountables[slot];for(int connection=0;connection<4;connection++)disconnect(slot,connection);mountables[slot]=null;
        if(mountable!=null){if(mountable instanceof li.cil.oc.server.component.DiskDriveMountable disk)disk.dispose(false);lifecycle(mountable,Lifecycle.LifecycleState.Disposing);if(mountable instanceof li.cil.oc.server.component.Server server)server.disposeForRemoval();else if(mountable.node()!=null)mountable.node().remove();flush(slot,mountable);if(mountable.node()!=null)mountable.node().remove();lifecycle(mountable,Lifecycle.LifecycleState.Disposed);}
        Arrays.fill(mapping[slot],-1);lastData[slot]=null;
    }
    private void initialize(){if(initialized||level==null||level.isClientSide||detached)return;initialized=true;loading=true;try{ensurePlugs();for(int slot=0;slot<4;slot++)createMountable(slot);Network.joinOrCreateNetwork(this);}finally{loading=false;}}
    public void tick(){
        if(level==null||level.isClientSide||detached||isRemoved())return;initialize();redstone.tick();
        if(dirty.getAndSet(false))super.setChanged();
        for(Direction side:Direction.values())if(canConnect(side)&&plugs[side.ordinal()].node.network()==null){Network.joinOrCreateNetwork(this);break;}
        if(level.getGameTime()%Math.max(1,(int)Settings.get().tickFrequency)==0)balancePower();
        for(int slot=0;slot<4;slot++){RackMountable mountable=mountables[slot];if(mountable==null)continue;power(mountable);if(mountable.canUpdate())mountable.update();}
        redstone.setEnabled(hasRedstoneCard());relayTick();
        int bits=changed.getAndSet(0);if(bits!=0){for(int slot=0;slot<4;slot++)if((bits&1<<slot)!=0)lastData[slot]=mountables[slot]==null?null:mountables[slot].getData();synchronize();}
    }
    private boolean hasRedstoneCard(){for(RackMountable mountable:mountables)if(mountable instanceof Container inventory&&mountable.getCurrentState().contains(State.IsWorking))for(int slot=0;slot<inventory.getContainerSize();slot++)if(inventory.getItem(slot).getItem() instanceof RedstoneCardItem)return true;return false;}
    private List<Connector> primaryConnectors(){List<Connector> result=new ArrayList<>();Set<Object> networks=Collections.newSetFromMap(new IdentityHashMap<>());for(Direction side:Direction.values())if(canConnect(side)){Connector connector=plugs[side.ordinal()].node;Object key=connector.network()==null?connector:connector.network();if(networks.add(key))result.add(connector);}return result;}
    private void balancePower(){List<Connector> connectors=primaryConnectors();List<Object> locks=connectors.stream().map(value->value.network()==null?(Object)value:value.network()).toList();withLocks(locks,0,()->{double stored=0,capacity=0;for(Connector value:connectors){stored+=value.globalBuffer();capacity+=value.globalBufferSize();}if(capacity>0){double ratio=stored/capacity;for(Connector value:connectors)value.changeBuffer(value.globalBufferSize()*ratio-value.globalBuffer());}});}
    private static void withLocks(List<Object> locks,int index,Runnable action){if(index>=locks.size()){action.run();return;}synchronized(locks.get(index)){withLocks(locks,index+1,action);}}
    private void power(RackMountable mountable){if(!(mountable.node() instanceof Connector inside))return;double remaining=Settings.get().serverRackRate;for(Connector outside:primaryConnectors())if(remaining>0){double received=remaining+outside.changeBuffer(-remaining),rejected=inside.changeBuffer(received);outside.changeBuffer(rejected);remaining-=received-rejected;}}
    private void enqueue(Direction side,Packet packet){
        deliver(side,packet,null);
        if(!relayEnabled)return;synchronized(queue){if(packet.ttl()>0&&queue.size()<maxQueueSize()){queue.add(new QueuedPacket(side,packet.hop()));if(relayCooldown<0)relayCooldown=relayDelay()-1;markChanged();}}
    }
    private int relayDelay(){Settings settings=Settings.get();return Math.max(1,settings.switchDefaultRelayDelay-(int)((settings.serverRackSwitchTier+1)*settings.switchRelayDelayUpgrade));}
    private int relayAmount(){Settings settings=Settings.get();return Math.max(1,settings.switchDefaultRelayAmount+(settings.serverRackSwitchTier+1)*settings.switchRelayAmountUpgrade*2);}
    private int maxQueueSize(){Settings settings=Settings.get();return Math.max(1,settings.switchDefaultMaxQueueSize+(settings.serverRackSwitchTier+1)*settings.switchQueueSizeUpgrade);}
    private void deliver(Direction side,Packet packet,Node source){if(side==null)return;for(int slot=0;slot<4;slot++){RackMountable mountable=mountables[slot];if(mountable==null)continue;for(int connection=0;connection<Math.min(3,mountable.getConnectableCount());connection++){int local=mapping[slot][connection+1];if(local>=0&&toGlobal(Direction.from3DDataValue(local))==side){RackBusConnectable target=mountable.getConnectableAt(connection);if(target!=null&&target.node()!=source)target.receivePacket(packet);}}}}
    private void relayTick(){synchronized(queue){if(relayCooldown>0){relayCooldown--;return;}relayCooldown=-1;int count=Math.min(queue.size(),relayAmount());for(int i=0;i<count;i++){QueuedPacket next=queue.remove();if(relayEnabled)for(Direction side:Direction.values())if(canConnect(side)&&side!=next.side){Node plug=sidedNode(side);if(plug!=null)plug.sendToReachable("network.message",next.packet);}}if(!queue.isEmpty())relayCooldown=relayDelay()-1;}}
    @Override public void onConnect(Node node){}
    @Override public void onDisconnect(Node node){}
    @Override public void onMessage(Message message){
        if(!message.name().equals("network.message")||message.data().length!=1||!(message.data()[0] instanceof Packet packet))return;
        for(int slot=0;slot<4;slot++){RackMountable mountable=mountables[slot];if(mountable==null)continue;for(int connection=0;connection<Math.min(3,mountable.getConnectableCount());connection++){int local=mapping[slot][connection+1];RackBusConnectable connectable=mountable.getConnectableAt(connection);if(local>=0&&connectable!=null&&connectable.node()==message.source()){Direction side=toGlobal(Direction.from3DDataValue(local));Node plug=sidedNode(side);if(plug!=null)plug.sendToReachable("network.message",packet);deliver(side,packet,message.source());return;}}}
    }
    private final class Plug implements Environment {
        final Direction side;final Connector node;
        Plug(Direction side){this.side=side;node=Network.newNode(this,Visibility.Network).withConnector(Settings.get().bufferDistributor).create();}
        @Override public Node node(){return node;}
        private boolean primary(){for(Direction direction:Direction.values())if(canConnect(direction)&&plugs[direction.ordinal()]!=null&&plugs[direction.ordinal()].node.network()==node.network())return direction==side;return false;}
        @Override public void onConnect(Node value){if(!detached&&value==node&&initialized)for(int slot=0;slot<4;slot++)for(int connection=0;connection<4;connection++)if(mapping[slot][connection]>=0&&toGlobal(Direction.from3DDataValue(mapping[slot][connection]))==side)reconnect(slot,connection);}
        @Override public void onDisconnect(Node node){}
        @Override public void onMessage(Message message){if(primary()&&message.name().equals("network.message")&&message.data().length==1&&message.data()[0] instanceof Packet packet){for(Plug plug:plugs)if(plug!=null&&plug.node==message.source())return;enqueue(side,packet);}}
    }
    public void neighborChanged(){redstone.rescan();if(level!=null&&!level.isClientSide&&!detached)Network.joinOrCreateNetwork(this);}
    private void clearClientMountable(int slot){RackMountable old=mountables[slot];mountables[slot]=null;clientStacks[slot]=null;if(old!=null&&old.node()!=null)old.node().remove();}
    private void refreshClientMountables(){
        if(level==null||!level.isClientSide||detached||isRemoved())return;
        for(int slot=0;slot<4;slot++){
            ItemStack stack=items.get(slot),previous=clientStacks[slot];
            if(previous!=null&&ItemStack.isSameItemSameComponents(previous,stack)&&previous.getCount()==stack.getCount())continue;
            clearClientMountable(slot);clientStacks[slot]=stack.copy();if(stack.isEmpty())continue;
            var driver=Driver.driverFor(stack,getClass());if(driver!=null&&driver.createEnvironment(stack,this) instanceof RackMountable view)mountables[slot]=view;
        }
    }
    @Override public void onLoad(){super.onLoad();detached=false;if(level!=null){if(level.isClientSide)refreshClientMountables();else initialize();}}
    private void dispose(){if(detached)return;if(level!=null&&level.isClientSide){for(int slot=0;slot<4;slot++)clearClientMountable(slot);detached=true;return;}flushMountables();detached=true;for(int slot=0;slot<4;slot++){RackMountable value=mountables[slot];if(value!=null){lifecycle(value,Lifecycle.LifecycleState.Disposing);if(value instanceof li.cil.oc.server.component.Server server)server.dispose();else if(value.node()!=null)value.node().remove();lifecycle(value,Lifecycle.LifecycleState.Disposed);}mountables[slot]=null;}for(Node[] row:sniffers){for(Node node:row)if(node!=null)node.remove();Arrays.fill(row,null);}for(Plug plug:plugs)if(plug!=null)plug.node.remove();Arrays.fill(plugs,null);initialized=false;}
    @Override public void setRemoved(){dispose();super.setRemoved();}
    @Override public void onChunkUnloaded(){dispose();super.onChunkUnloaded();}
    @Override public int getContainerSize(){return 4;}
    @Override public int getMaxStackSize(){return 1;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<4?items.get(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int amount){if(slot<0||slot>=4||amount<=0||items.get(slot).isEmpty())return ItemStack.EMPTY;release(slot);ItemStack result=items.get(slot).split(amount);if(items.get(slot).isEmpty())items.set(slot,ItemStack.EMPTY);markChanged(slot);synchronize();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){if(slot<0||slot>=4||items.get(slot).isEmpty())return ItemStack.EMPTY;release(slot);ItemStack result=items.set(slot,ItemStack.EMPTY);markChanged(slot);synchronize();return result;}
    @Override public void setItem(int slot,ItemStack stack){if(slot<0||slot>=4||items.get(slot)==stack||items.get(slot).isEmpty()&&stack.isEmpty())return;ItemStack next=stack.copyWithCount(Math.min(1,stack.getCount()));release(slot);items.set(slot,next);if(initialized&&!loading)createMountable(slot);markChanged(slot);synchronize();}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){var driver=Driver.driverFor(stack,getClass());return slot>=0&&slot<4&&driver!=null&&driver.slot(stack).equals("rack_mountable");}
    @Override public void clearContent(){for(int slot=0;slot<4;slot++)release(slot);items.replaceAll(value->ItemStack.EMPTY);markChanged();synchronize();}
    @Override public boolean stillValid(Player player){return level!=null&&!isRemoved()&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(xPosition(),yPosition(),zPosition())<=64;}
    @Override public Component getDisplayName(){return Component.translatable("block.opencomputers.rack");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return stillValid(player)?new RackMenu(id,inventory,this):null;}
    public int slotAt(Direction side,double hitY){return side==facing()?Math.clamp(((15-(int)(hitY*16))-2)*4/12,0,3):-1;}
    @Override public Node[] onAnalyze(Player player,Direction side,float x,float y,float z){int slot=slotAt(side,y);if(slot>=0)return getMountable(slot) instanceof Analyzable analyzable?analyzable.onAnalyze(player,side,x,y,z):null;return new Node[]{sidedNode(side)};}
    @Override public EnumSet<State> getCurrentState(){EnumSet<State> result=EnumSet.noneOf(State.class);for(RackMountable value:mountables)if(value!=null)result.addAll(value.getCurrentState());return result;}
    public void synchronize(){if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    @Override public void onComponentActivity(String address,boolean network){for(CompoundTag data:lastData)if(data!=null){ListTag addresses=data.getList("componentAddresses",Tag.TAG_STRING);for(Tag candidate:addresses)if(candidate.getAsString().equals(address)){long now=System.currentTimeMillis();data.putLong(network?"lastNetworkActivity":"lastFileSystemAccess",now);if(!network&&data.contains("disk"))data.putLong("lastAccess",now);}}}
    /** Called on VM threads: only the mountable's volatile light timestamp is changed. */
    public void recordDiskActivity(String address){if(address==null)return;for(RackMountable mountable:mountables)if(mountable instanceof li.cil.oc.server.component.DiskDriveMountable disk){Node filesystem=disk.filesystemNode();if(filesystem!=null&&address.equals(filesystem.address()))disk.recordAccess();}}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);boolean rebuild=initialized&&level!=null&&!level.isClientSide;loading=true;try{if(rebuild){for(int slot=0;slot<4;slot++)release(slot);for(Plug plug:plugs)if(plug!=null)plug.node.remove();Arrays.fill(plugs,null);initialized=false;}ServerInventory.loadItems(tag,items,registries);relayEnabled=tag.getBoolean("oc:isRelayEnabled");ListTag rows=tag.getList("oc:nodeMapping",Tag.TAG_INT_ARRAY);for(int slot=0;slot<Math.min(4,rows.size());slot++){int[] values=rows.getIntArray(slot);for(int connection=0;connection<Math.min(4,values.length);connection++)mapping[slot][connection]=values[connection]>=0&&values[connection]<6&&values[connection]!=3?values[connection]:-1;}
            ListTag data=tag.getList("oc:lastData",Tag.TAG_COMPOUND);for(int slot=0;slot<Math.min(4,data.size());slot++)lastData[slot]=data.getCompound(slot).copy();
            if(level!=null&&level.isClientSide){redstone.loadClient(tag);refreshClientMountables();return;}
            redstone.load(tag);savedPlugs=tag.getList("oc:plugs",Tag.TAG_COMPOUND).copy();relayCooldown=tag.contains("oc:relayCooldown")?tag.getInt("oc:relayCooldown"):-1;synchronized(queue){queue.clear();for(Tag entry:tag.getList("oc:queue",Tag.TAG_COMPOUND)){CompoundTag value=(CompoundTag)entry;int side=value.getInt("side");queue.add(new QueuedPacket(side>=0&&side<6?Direction.from3DDataValue(side):null,Network.newPacket(value)));}}
        }finally{loading=false;}if(rebuild)initialize();
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);flushMountables();ServerInventory.saveItems(tag,items,registries);writeConfiguration(tag);redstone.save(tag);ensurePlugs();ListTag nodes=new ListTag();for(Plug plug:plugs){CompoundTag node=new CompoundTag();if(plug!=null)plug.node.save(node);nodes.add(node);}tag.put("oc:plugs",nodes);synchronized(queue){ListTag packets=new ListTag();for(QueuedPacket queued:queue){CompoundTag value=new CompoundTag();queued.packet.save(value);value.putInt("side",queued.side==null?-1:queued.side.ordinal());packets.add(value);}tag.put("oc:queue",packets);tag.putInt("oc:relayCooldown",relayCooldown);}}
    private void writeConfiguration(CompoundTag tag){tag.putBoolean("oc:isRelayEnabled",relayEnabled);ListTag rows=new ListTag();for(int[] row:mapping)rows.add(new IntArrayTag(row.clone()));tag.put("oc:nodeMapping",rows);ListTag data=new ListTag();for(CompoundTag value:lastData)data.add(value==null?new CompoundTag():value.copy());tag.put("oc:lastData",data);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();List<ItemStack> display=new ArrayList<>();for(ItemStack stack:items){ItemStack copy=stack.copy();copy.remove(DataComponents.CUSTOM_DATA);display.add(copy);}ServerInventory.saveItems(tag,display,registries);writeConfiguration(tag);redstone.saveClient(tag);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public IEnergyStorage energy(Direction side){if(!canConnect(side))return null;return new IEnergyStorage(){
        public int receiveEnergy(int maximum,boolean simulate){Node value=sidedNode(side);if(!(value instanceof Connector connector)||Settings.get().ratioForgeEnergy<=0)return 0;double ratio=Settings.get().ratioForgeEnergy;int accepted=(int)Math.max(0,Math.min(maximum,Math.min(Settings.get().serverRackRate,connector.globalBufferSize()-connector.globalBuffer())/ratio));if(!simulate){connector.changeBuffer(accepted*ratio);markChanged();}return accepted;}
        public int extractEnergy(int amount,boolean simulate){return 0;}public int getEnergyStored(){Node node=sidedNode(side);return node instanceof Connector connector?(int)Math.min(Integer.MAX_VALUE,connector.globalBuffer()/Settings.get().ratioForgeEnergy):0;}public int getMaxEnergyStored(){Node node=sidedNode(side);return node instanceof Connector connector?(int)Math.min(Integer.MAX_VALUE,connector.globalBufferSize()/Settings.get().ratioForgeEnergy):0;}public boolean canReceive(){return true;}public boolean canExtract(){return false;}
    };}
}
