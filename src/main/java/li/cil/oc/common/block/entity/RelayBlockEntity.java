package li.cil.oc.common.block.entity;

import java.util.*;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.common.capabilities.EnergyProvider;
import li.cil.oc.common.item.ComponentItem;
import li.cil.oc.common.menu.RelayMenu;
import li.cil.oc.common.network.NetworkBlockNetworking;
import li.cil.oc.server.network.QuantumNetwork;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Original switch/access point: six isolated networks and a bounded, hop-limited relay queue. */
public final class RelayBlockEntity extends NetworkDeviceBlockEntity implements SidedEnvironment,Container,MenuProvider,WirelessEndpoint,QuantumNetwork.QuantumNode,EnergyProvider,Analyzable {
    /** Optional integration hook without linking any ComputerCraft classes into this block. */
    public interface PacketListener { void receive(Packet packet); default void removed(){} }
    private final java.util.concurrent.CopyOnWriteArrayList<PacketListener> packetListeners=new java.util.concurrent.CopyOnWriteArrayList<>();
    public void addPacketListener(PacketListener listener){packetListeners.addIfAbsent(listener);}
    public void removePacketListener(PacketListener listener){packetListeners.remove(listener);}
    public boolean wirelessEnabled(){return wirelessTier>=0;}
    public boolean linkedEnabled(){return linked;}
    private record Queued(Direction source,Packet packet){}
    private final ArrayDeque<Queued> queue=new ArrayDeque<>();
    private final NonNullList<ItemStack> items=NonNullList.withSize(4,ItemStack.EMPTY);
    private final Plug[] plugs=new Plug[6];
    private final Node[] componentNodes=new Node[6];
    /** Six world-side addresses without creating or reconnecting component nodes. */
    public String[] componentAddresses(){String[] addresses=new String[6];for(int i=0;i<addresses.length;i++){Node node=componentNodes[i];String address=node==null?null:node.address();addresses[i]=address==null?"":address;}return addresses;}
    private ListTag savedPlugs=new ListTag(),savedComponents=new ListTag();
    private boolean updatingComponents;
    private volatile int maxQueueSize=Math.max(1,Settings.get().switchDefaultMaxQueueSize),relayDelay=Math.max(1,Settings.get().switchDefaultRelayDelay),relayAmount=Math.max(1,Settings.get().switchDefaultRelayAmount);
    private int relayCooldown=-1;
    private final int[] history=new int[20];
    private int historyIndex;
    private volatile int packetsPerCycle;
    private volatile int wirelessTier=-1;
    private volatile double strength;
    private volatile boolean repeater=true,linked;
    private String channel="creative";
    public long lastMessage;
    public RelayBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override protected Node createNode(){return null;}
    @Override public Node node(){return null;}
    private void ensurePlugs(){
        if(!server()||detached||isRemoved())return;
        for(Direction side:Direction.values()) {
            int index=side.get3DDataValue();
            if(plugs[index]==null){plugs[index]=new Plug(side);if(index<savedPlugs.size())plugs[index].node.load(savedPlugs.getCompound(index));}
            if(componentNodes[index]==null){componentNodes[index]=Network.newNode(this,Visibility.Network).withComponent("relay").create();if(index<savedComponents.size())componentNodes[index].load(savedComponents.getCompound(index));}
        }
    }
    @Override protected void onInitialize(){ensurePlugs();updateHardware();Network.joinOrCreateNetwork(this);updateComponents();}
    @Override protected void onDetach(){for(var listener:packetListeners)listener.removed();packetListeners.clear();Network.leaveWirelessNetwork(this);QuantumNetwork.remove(this);for(Node node:componentNodes)if(node!=null)node.remove();for(Plug plug:plugs)if(plug!=null)plug.node.remove();}
    @Override public Node sidedNode(Direction side){if(side==null)return null;ensurePlugs();return plugs[side.get3DDataValue()]==null?null:plugs[side.get3DDataValue()].node;}
    @Override public boolean canConnect(Direction side){return side!=null;}
    private final class Plug implements Environment {
        final Direction side;
        final Connector node;
        Plug(Direction side){this.side=side;node=Network.newNode(this,Visibility.Network).withConnector(Math.round(Settings.get().bufferAccessPoint)).create();}
        boolean primary(){if(node.network()==null)return false;for(Plug plug:plugs)if(plug!=null&&plug.node.network()==node.network())return plug==this;return false;}
        @Override public Node node(){return node;}
        @Override public void onConnect(Node connected){updateComponents();}
        @Override public void onDisconnect(Node disconnected){updateComponents();}
        @Override public void onMessage(Message message){
            if(!primary()||!message.name().equals("network.message")||message.data().length!=1||!(message.data()[0] instanceof Packet packet))return;
            for(Plug plug:plugs)if(plug!=null&&plug.node==message.source())return;
            tryEnqueuePacket(side,packet);
        }
    }
    private void updateComponents(){
        if(updatingComponents||!server()||detached||isRemoved())return;updatingComponents=true;
        try{
            boolean connected=false;
            for(int i=0;i<6;i++)if(plugs[i]!=null&&componentNodes[i]!=null){
                Plug plug=plugs[i];connected|=plug.node.network()!=null;
                if(plug.primary()){if(!plug.node.isNeighborOf(componentNodes[i]))plug.node.connect(componentNodes[i]);}
                else componentNodes[i].remove();
            }
            if(connected&&wirelessTier>=0)Network.joinWirelessNetwork(this);else Network.leaveWirelessNetwork(this);
        }finally{updatingComponents=false;}
    }
    public boolean tryEnqueuePacket(Direction source,Packet packet){for(var listener:packetListeners)listener.receive(packet);synchronized(queue){if(packet.ttl()<=0||queue.size()>=maxQueueSize)return false;queue.addLast(new Queued(source,packet.hop()));if(relayCooldown<0)relayCooldown=relayDelay-1;setChanged();return true;}}
    private boolean consume(Direction source,double cost){if(source!=null){Plug plug=plugs[source.get3DDataValue()];return plug!=null&&plug.node.tryChangeBuffer(-cost);}for(Plug plug:plugs)if(plug!=null&&plug.node.tryChangeBuffer(-cost))return true;return false;}
    private void relay(Queued entry){
        Packet packet=entry.packet;
        for(Direction side:Direction.values())if(side!=entry.source){Node node=sidedNode(side);if(node!=null)node.sendToReachable("network.message",packet);}
        if(wirelessTier>=0&&strength>0&&(entry.source!=null||repeater)&&consume(entry.source,strength*wirelessCost()))Network.sendWirelessPacket(this,strength,packet);
        if(linked&&entry.source!=null&&consume(entry.source,packet.size()/32.0+wirelessCost()*maxWirelessRange()*5))for(var endpoint:QuantumNetwork.getEndpoints(channel))if(endpoint!=this)endpoint.receivePacket(packet);
        long now=System.currentTimeMillis();if(now-lastMessage>=Math.max(0,relayDelay-1)*50L){lastMessage=now;NetworkBlockNetworking.relayActivity(this);}
    }
    private void average(int value){history[historyIndex++%history.length]=value;int sum=0;for(int sample:history)sum+=sample;packetsPerCycle=sum/history.length;}
    @Override public void serverTick(){
        super.serverTick();if(!server()||detached||isRemoved())return;
        if(level.getGameTime()%20==0){Network.joinOrCreateNetwork(this);updateComponents();}
        List<Queued> outgoing=new ArrayList<>();
        synchronized(queue){
            if(relayCooldown>0){relayCooldown--;return;}relayCooldown=-1;
            if(!queue.isEmpty()){int count=Math.min(queue.size(),relayAmount);average(count);for(int i=0;i<count;i++)outgoing.add(queue.removeFirst());if(!queue.isEmpty())relayCooldown=relayDelay-1;setChanged();}
            else if(level.getGameTime()%relayDelay==0)average(0);
        }
        for(var entry:outgoing)relay(entry);
    }
    @Override public void receivePacket(Packet packet,WirelessEndpoint source){if(wirelessTier>=0)tryEnqueuePacket(null,packet);}
    @Override public void receivePacket(Packet packet){if(linked)tryEnqueuePacket(null,packet);}
    @Override public int x(){return worldPosition.getX();}
    @Override public int y(){return worldPosition.getY();}
    @Override public int z(){return worldPosition.getZ();}
    @Override public String tunnel(){return channel;}
    public double maxWirelessRange(){return wirelessTier>=0?Settings.get().maxWirelessRange[wirelessTier]:0;}
    private double wirelessCost(){return wirelessTier>=0?Settings.get().wirelessCostPerRange[wirelessTier]:0;}
    public int relayDelay(){return relayDelay;}
    public int relayAmount(){return relayAmount;}
    public int maxQueueSize(){return maxQueueSize;}
    public int packetsPerCycle(){return packetsPerCycle;}
    public int queueSize(){synchronized(queue){return queue.size();}}
    @Callback(direct=true,doc="function():number -- Wireless relay signal strength.")
    public Object[] getStrength(Context context,Arguments args){return new Object[]{strength};}
    @Callback(doc="function(strength:number):number -- Set the wireless relay range.")
    public Object[] setStrength(Context context,Arguments args){double value=args.checkDouble(0);strength=Double.isNaN(value)?0:Math.max(0,Math.min(value,maxWirelessRange()));setChanged();return new Object[]{strength};}
    @Callback(direct=true,doc="function():boolean -- Whether wireless packets are repeated wirelessly.")
    public Object[] isRepeater(Context context,Arguments args){return new Object[]{repeater};}
    @Callback(doc="function(enabled:boolean):boolean -- Enable or disable wireless repeating.")
    public Object[] setRepeater(Context context,Arguments args){repeater=args.checkBoolean(0);setChanged();return new Object[]{repeater};}
    private void updateHardware(){
        QuantumNetwork.remove(this);wirelessTier=-1;linked=false;
        relayDelay=Math.max(1,Settings.get().switchDefaultRelayDelay);relayAmount=Math.max(1,Settings.get().switchDefaultRelayAmount);maxQueueSize=Math.max(1,Settings.get().switchDefaultMaxQueueSize);
        for(int slot=0;slot<items.size();slot++){
            ItemStack stack=items.get(slot);if(stack.isEmpty())continue;var driver=Driver.driverFor(stack,getClass());if(driver==null)continue;
            switch(slot){
                case 0 -> relayDelay=Math.max(1,relayDelay-(int)((driver.tier(stack)+1)*Settings.get().switchRelayDelayUpgrade));
                case 1 -> {int amount=stack.getItem() instanceof ComponentItem item&&item.kind()==ComponentItem.Kind.MEMORY?(item.level()+1)*Settings.get().switchRelayAmountUpgrade:(driver.tier(stack)+1)*Settings.get().switchRelayAmountUpgrade*2;relayAmount=Math.max(1,relayAmount+amount);}
                case 2 -> maxQueueSize=Math.max(1,maxQueueSize+(driver.tier(stack)+1)*Settings.get().switchQueueSizeUpgrade);
                case 3 -> {var descriptor=li.cil.oc.api.Items.get(stack);if(descriptor!=null){if(descriptor.name().equals("wlancard1"))wirelessTier=0;else if(descriptor.name().equals("wlancard2"))wirelessTier=1;else if(descriptor.name().equals("linkedcard")){var data=driver.dataTag(stack);if(data!=null&&data.contains("oc:tunnel")){channel=data.getString("oc:tunnel");linked=true;}}}}
            }
        }
        // A newly inserted wireless card starts at its usable maximum; an existing setting remains bounded.
        strength=Math.max(0,Math.min(strength,maxWirelessRange()));
        if(linked&&server()&&!detached&&!isRemoved())QuantumNetwork.add(this);
        if(initialized)updateComponents();
    }
    private void inventoryChanged(int slot,boolean hadWireless){updateHardware();if(slot==3&&wirelessTier>=0&&!hadWireless)strength=maxWirelessRange();setChanged();if(server())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override public int getContainerSize(){return 4;}
    @Override public int getMaxStackSize(){return 1;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<4?items.get(slot):ItemStack.EMPTY;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){
        if(slot<0||slot>=4||stack.isEmpty())return false;var driver=Driver.driverFor(stack,getClass());if(driver==null||driver.tier(stack)>2)return false;
        String[] kinds={li.cil.oc.api.driver.item.Slot.CPU,li.cil.oc.api.driver.item.Slot.Memory,li.cil.oc.api.driver.item.Slot.HDD,li.cil.oc.api.driver.item.Slot.Card};
        if(!kinds[slot].equals(driver.slot(stack)))return false;
        if(slot!=3)return true;var descriptor=li.cil.oc.api.Items.get(stack);return descriptor!=null&&Set.of("wlancard1","wlancard2","linkedcard").contains(descriptor.name());
    }
    @Override public ItemStack removeItem(int slot,int count){if(slot<0||slot>=4||count<=0)return ItemStack.EMPTY;boolean had=wirelessTier>=0;ItemStack removed=ContainerHelper.removeItem(items,slot,count);inventoryChanged(slot,had);return removed;}
    @Override public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,1);}
    @Override public void setItem(int slot,ItemStack stack){if(slot<0||slot>=4)return;boolean had=wirelessTier>=0;items.set(slot,stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(1));inventoryChanged(slot,had);}
    @Override public void clearContent(){items.replaceAll(stack->ItemStack.EMPTY);updateHardware();setChanged();}
    @Override public boolean stillValid(Player player){return level!=null&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(xPosition(),yPosition(),zPosition())<=64;}
    @Override public Component getDisplayName(){return Component.translatable("block.opencomputers.relay");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return stillValid(player)?new RelayMenu(id,inventory,this):null;}
    @Override public Node[] onAnalyze(Player player,Direction side,float hitX,float hitY,float hitZ){if(wirelessTier<0||side==null)return null;player.displayClientMessage(Component.translatable("oc:gui.Analyzer.WirelessStrength",strength),false);return new Node[]{componentNodes[side.get3DDataValue()]};}
    @Override public IEnergyStorage energy(Direction side){
        if(side==null)return null;return new IEnergyStorage(){
            private Connector connector(){Node value=sidedNode(side);return value instanceof Connector connector?connector:null;}
            private double ratio(){return Math.max(0,Settings.get().ratioForgeEnergy);}
            private int fe(double oc){double ratio=ratio();return ratio<=0?0:(int)Math.clamp(Math.floor(oc/ratio),0,Integer.MAX_VALUE);}
            @Override public int receiveEnergy(int maximum,boolean simulate){Connector value=connector();if(value==null||maximum<=0||ratio()<=0||Settings.get().ignorePower)return 0;Object lock=value.network()==null?value:value.network();synchronized(lock){int accepted=Math.min(maximum,fe(Math.max(0,Math.min(Settings.get().accessPointRate,value.globalBufferSize()-value.globalBuffer()))));if(!simulate&&accepted>0){value.changeBuffer(accepted*ratio());setChanged();}return accepted;}}
            @Override public int extractEnergy(int maximum,boolean simulate){return 0;}
            @Override public int getEnergyStored(){Connector value=connector();return value==null?0:fe(value.globalBuffer());}
            @Override public int getMaxEnergyStored(){Connector value=connector();return value==null?0:fe(value.globalBufferSize());}
            @Override public boolean canExtract(){return false;}
            @Override public boolean canReceive(){return !Settings.get().ignorePower&&!detached&&ratio()>0;}
        };
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(tag.contains("oc:items")){items.replaceAll(stack->ItemStack.EMPTY);for(Tag raw:tag.getList("oc:items",Tag.TAG_COMPOUND)){var entry=(CompoundTag)raw;int slot=entry.getInt("slot");if(slot>=0&&slot<4)items.set(slot,ItemStack.parseOptional(registries,entry.getCompound("item")));}}
        savedPlugs=tag.getList("oc:plugs",Tag.TAG_COMPOUND).copy();savedComponents=tag.getList("oc:componentNodes",Tag.TAG_COMPOUND).copy();
        updateHardware();
        if(server()){ensurePlugs();for(int i=0;i<6;i++){if(i<savedPlugs.size())plugs[i].node.load(savedPlugs.getCompound(i));if(i<savedComponents.size())componentNodes[i].load(savedComponents.getCompound(i));}}
        if(tag.contains("oc:strength"))strength=Math.max(0,Math.min(tag.getDouble("oc:strength"),maxWirelessRange()));
        if(tag.contains("oc:isRepeater"))repeater=tag.getBoolean("oc:isRepeater");
        relayCooldown=tag.contains("oc:relayCooldown")?tag.getInt("oc:relayCooldown"):-1;
        synchronized(queue){queue.clear();for(Tag raw:tag.getList("oc:queue",Tag.TAG_COMPOUND)){var entry=(CompoundTag)raw;int side=entry.getInt("side");Packet packet=Network.newPacket(entry);if(packet!=null&&queue.size()<maxQueueSize)queue.addLast(new Queued(side>=0&&side<6?Direction.from3DDataValue(side):null,packet));}}
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);var inventory=new ListTag();for(int i=0;i<4;i++)if(!items.get(i).isEmpty()){var entry=new CompoundTag();entry.putInt("slot",i);entry.put("item",items.get(i).save(registries));inventory.add(entry);}tag.put("oc:items",inventory);
        var plugTags=new ListTag();var componentTags=new ListTag();for(int i=0;i<6;i++){var p=new CompoundTag();var c=new CompoundTag();if(plugs[i]!=null)plugs[i].node.save(p);else if(i<savedPlugs.size())p=savedPlugs.getCompound(i).copy();if(componentNodes[i]!=null)componentNodes[i].save(c);else if(i<savedComponents.size())c=savedComponents.getCompound(i).copy();plugTags.add(p);componentTags.add(c);}tag.put("oc:plugs",plugTags);tag.put("oc:componentNodes",componentTags);
        tag.putDouble("oc:strength",strength);tag.putBoolean("oc:isRepeater",repeater);
        synchronized(queue){var saved=new ListTag();for(var queued:queue){var entry=new CompoundTag();queued.packet.save(entry);entry.putInt("side",queued.source==null?-1:queued.source.get3DDataValue());saved.add(entry);}tag.put("oc:queue",saved);if(relayCooldown>0)tag.putInt("oc:relayCooldown",relayCooldown);}
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();tag.putDouble("oc:strength",strength);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
