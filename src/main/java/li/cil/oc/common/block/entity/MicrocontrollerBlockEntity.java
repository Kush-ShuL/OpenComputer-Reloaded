package li.cil.oc.common.block.entity;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import li.cil.oc.Constants;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.common.block.MicrocontrollerBlock;
import li.cil.oc.common.capabilities.EnergyProvider;
import li.cil.oc.common.item.data.MicrocontrollerData;
import li.cil.oc.common.redstone.*;
import li.cil.oc.server.portable.PortableRuntime;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Each outer face has a separate graph; only network messages cross the private VM boundary. */
public final class MicrocontrollerBlockEntity extends BlockEntity implements li.cil.oc.api.internal.Microcontroller,SidedEnvironment,Analyzable,WorldlyContainer,EnergyProvider,DeviceInfo,RedstoneHost {
    public final MicrocontrollerData info=new MicrocontrollerData();
    public final boolean[] outputSides={true,true,true,true,true,true};
    public final PortableRuntime runtime=new PortableRuntime(this,()->info.components);
    private final Plug[] plugs=new Plug[6];
    private final li.cil.oc.api.network.Component[] componentNodes=new li.cil.oc.api.network.Component[6];
    private ComponentConnector snooper;
    private CompoundTag pending;
    private final AtomicBoolean dirty=new AtomicBoolean(),topologyDirty=new AtomicBoolean(true);
    private boolean initialized,closed;
    public boolean clientRunning,clientErrored;
    private String owner="";
    private UUID ownerId;
    private String[] users=new String[0];
    private final RedstoneState redstone=new RedstoneState(this,change->{if(machine()!=null)machine().node().sendToReachable("redstone.changed",change);});
    public MicrocontrollerBlockEntity(BlockEntityType<?> type,BlockPos position,BlockState state){super(type,position,state);}
    @Override public Level world(){return level;}
    @Override public double xPosition(){return worldPosition.getX()+.5;}
    @Override public double yPosition(){return worldPosition.getY()+.5;}
    @Override public double zPosition(){return worldPosition.getZ()+.5;}
    @Override public int tier(){return info.tier;}
    @Override public Direction facing(){return getBlockState().getValue(MicrocontrollerBlock.FACING);}
    @Override public Direction toGlobal(Direction side){if(side==null||side.getAxis()==Direction.Axis.Y)return side;for(int i=0;i<facing().get2DDataValue();i++)side=side.getClockWise();return side;}
    @Override public Direction toLocal(Direction side){if(side==null||side.getAxis()==Direction.Axis.Y)return side;for(int i=0;i<facing().get2DDataValue();i++)side=side.getCounterClockWise();return side;}
    @Override public RedstoneState redstoneState(){return redstone;}
    @Override public li.cil.oc.server.machine.Machine machine(){return runtime.machine();}
    @Override public Node node(){return null;}
    @Override public boolean canConnect(Direction side){return side!=null&&side!=facing();}
    @Override public Node sidedNode(Direction side){if(!canConnect(side)||level==null||level.isClientSide||closed)return null;ensureNodes();return plugs[side.get3DDataValue()].node;}
    @Override public Iterable<ItemStack> internalComponents(){return Arrays.asList(info.components);}
    @Override public int componentSlot(String address){return runtime.componentSlot(address);}
    @Override public void onMachineConnect(Node node){if(node==machine().node())runtime.connect();}
    @Override public void onMachineDisconnect(Node node){if(node==machine().node())runtime.saveComponents();}
    @Override public void onConnect(Node node){topologyDirty.set(true);}
    @Override public void onDisconnect(Node node){topologyDirty.set(true);markChanged();}
    @Override public void onMessage(Message message){if(snooper!=null&&message.name().equals("network.message")&&message.source().network()==snooper.network())for(Direction side:Direction.values())if(canConnect(side)&&outputSides[side.get3DDataValue()])plugs[side.get3DDataValue()].node.sendToReachable(message.name(),message.data());}
    @Override public void markChanged(){dirty.set(true);}
    @Override public void setChanged(){if(level instanceof ServerLevel server&&!server.getServer().isSameThread()){dirty.set(true);return;}super.setChanged();}
    private void ensureNodes(){if(snooper!=null||level==null||level.isClientSide||closed)return;snooper=Network.newNode(this,Visibility.Network).withComponent("microcontroller").withConnector(Settings.get().bufferMicrocontroller).create();
        for(Direction side:Direction.values()){int index=side.get3DDataValue();plugs[index]=new Plug(side);componentNodes[index]=Network.newNode(this,Visibility.Network).withComponent("microcontroller").create();}
        if(pending!=null){snooper.load(pending.getCompound("oc:snooper"));ListTag nodes=pending.getList("oc:componentNodes",Tag.TAG_COMPOUND),outer=pending.getList("oc:plugs",Tag.TAG_COMPOUND);for(int index=0;index<6;index++){if(index<nodes.size())componentNodes[index].load(nodes.getCompound(index));if(index<outer.size())plugs[index].node.load(outer.getCompound(index));}}
    }
    private void initialize(){if(initialized||closed||level==null||level.isClientSide)return;ensureNodes();initialized=true;runtime.initialize(snooper);machine().setCostPerTick(Settings.get().microcontrollerCost);if(pending==null||!pending.contains("oc:snooper"))snooper.changeBuffer(info.storedEnergy-snooper.localBuffer());pending=null;Network.joinOrCreateNetwork(this);refreshPlugs();redstone.rescan();}
    private void refreshPlugs(){if(snooper==null||closed)return;for(Direction side:Direction.values()){int index=side.get3DDataValue();Plug plug=plugs[index];if(!canConnect(side)){componentNodes[index].remove();plug.node.remove();continue;}if(plug.node.network()==null)Network.joinNewNetwork(plug.node);if(plug.primary()){if(componentNodes[index].network()!=plug.node.network())plug.node.connect(componentNodes[index]);}else if(componentNodes[index].network()!=null)componentNodes[index].remove();}topologyDirty.set(false);}
    public void tick(){if(level==null||level.isClientSide||closed)return;initialize();if(topologyDirty.getAndSet(false)){Network.joinOrCreateNetwork(this);refreshPlugs();}redstone.tick();
        if(level.getGameTime()%Math.max(1,(int)Settings.get().tickFrequency)==0){if(info.tier==3)snooper.changeBuffer(Double.POSITIVE_INFINITY);for(Direction side:Direction.values())if(canConnect(side)){Connector outer=plugs[side.get3DDataValue()].node;double demand=Math.max(0,snooper.globalBufferSize()-snooper.globalBuffer());if(demand>0)snooper.changeBuffer(demand+outer.changeBuffer(-demand));}}
        runtime.tick();if(isRemoved()||level.getBlockEntity(worldPosition)!=this)return;boolean running=machine().isRunning(),error=machine().lastError()!=null;redstone.setEnabled(running&&Arrays.stream(info.components).anyMatch(stack->Driver.driverFor(stack,getClass()) instanceof li.cil.oc.server.driver.item.RedstoneCard));
        String[] nextUsers=machine().users();boolean usersChanged=!Arrays.equals(users,nextUsers);if(usersChanged)users=nextUsers;
        if(clientRunning!=running||clientErrored!=error){clientRunning=running;clientErrored=error;level.setBlock(worldPosition,getBlockState().setValue(MicrocontrollerBlock.RUNNING,running).setValue(MicrocontrollerBlock.ERROR,error),3);markChanged();}if(usersChanged)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);if(dirty.getAndSet(false))super.setChanged();
    }
    public void initialize(ItemStack stack,Player player){runtime.saveComponents();info.load(stack,level.registryAccess());owner=player.getGameProfile().getName();ownerId=player.getUUID();if(initialized)runtime.rebuild();else initialize();snooper.changeBuffer(info.storedEnergy-snooper.localBuffer());setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    public ItemStack dropStack(){runtime.saveComponents();if(snooper!=null)info.storedEnergy=(int)snooper.localBuffer();return info.createItemStack(level.registryAccess());}
    public boolean canInteract(Player player){String name=player instanceof li.cil.oc.server.agent.AgentPlayer agent?agent.agent().ownerName():player.getGameProfile().getName();boolean permission=level!=null&&level.isClientSide?!Settings.get().canComputersBeOwned||users.length==0||Arrays.asList(users).contains(name):machine()==null||machine().canInteract(name);return level==player.level()&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(xPosition(),yPosition(),zPosition())<=64&&permission&&(info.tier!=3||player.getAbilities().instabuild);}
    public void toggle(Player player){initialize();if(!canInteract(player))return;if(machine().isRunning())machine().stop();else if(!machine().start()&&machine().lastError()!=null)player.displayClientMessage(net.minecraft.network.chat.Component.literal(machine().lastError()),true);markChanged();}
    public int eepromSlot(){for(int index=0;index<info.components.length;index++)if(isEEPROM(info.components[index]))return index;int last=info.components.length-1;return last>=0&&info.components[last].isEmpty()?last:-1;}
    public static boolean isEEPROM(ItemStack stack){return !stack.isEmpty()&&li.cil.oc.api.Items.get(stack)==li.cil.oc.api.Items.get(Constants.ItemName.EEPROM);}
    public ItemStack changeEEPROM(ItemStack replacement){if(!replacement.isEmpty()&&!isEEPROM(replacement))return replacement;initialize();int slot=eepromSlot();if(slot<0)return replacement;runtime.remove(slot);ItemStack old=info.components[slot];info.components[slot]=replacement.isEmpty()?ItemStack.EMPTY:replacement.copyWithCount(1);runtime.install(slot);setChanged();return old;}
    @Override public Node[] onAnalyze(Player player,Direction side,float x,float y,float z){initialize();return side==facing()?new Node[]{machine().node()}:new Node[]{componentNodes[side.get3DDataValue()]};}
    private int side(Arguments args){int side=args.checkInteger(0);if(side<0||side>=6)throw new IllegalArgumentException("invalid side");if(Direction.from3DDataValue(side)==facing())throw new IllegalArgumentException("unsupported side");return side;}
    @Callback(doc="function():boolean -- Starts the microcontroller. Returns true if the state changed.") public Object[] start(Context context,Arguments args){return new Object[]{!machine().isPaused()&&machine().start()};}
    @Callback(doc="function():boolean -- Stops the microcontroller. Returns true if the state changed.") public Object[] stop(Context context,Arguments args){return new Object[]{machine().stop()};}
    @Callback(direct=true,doc="function():boolean -- Returns whether the microcontroller is running.") public Object[] isRunning(Context context,Arguments args){return new Object[]{machine().isRunning()};}
    @Callback(direct=true,doc="function():string -- Returns the reason the microcontroller crashed, if applicable.") public Object[] lastError(Context context,Arguments args){return new Object[]{machine().lastError()};}
    @Callback(direct=true,doc="function(side:number):boolean -- Get whether network messages are sent via the specified side.") public Object[] isSideOpen(Context context,Arguments args){return new Object[]{outputSides[side(args)]};}
    @Callback(doc="function(side:number, open:boolean):boolean -- Set whether network messages are sent via the specified side.") public Object[] setSideOpen(Context context,Arguments args){int side=side(args);boolean old=outputSides[side];outputSides[side]=args.checkBoolean(1);markChanged();return new Object[]{old};}
    @Override public Map<String,String> getDeviceInfo(){return Map.of(DeviceAttribute.Class,DeviceClass.System,DeviceAttribute.Description,"Microcontroller",DeviceAttribute.Vendor,Constants.DeviceInfo.DefaultVendor,DeviceAttribute.Product,"Cubicle",DeviceAttribute.Capacity,Integer.toString(info.components.length));}
    @Override public void onLoad(){super.onLoad();initialize();}
    private void close(){if(closed)return;runtime.saveComponents();closed=true;runtime.close();for(Plug plug:plugs)if(plug!=null)plug.node.remove();for(Node node:componentNodes)if(node!=null)node.remove();}
    @Override public void setRemoved(){close();super.setRemoved();}
    @Override public void onChunkUnloaded(){close();super.onChunkUnloaded();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);info.load(tag.getCompound("oc:info"),registries);clientRunning=tag.getBoolean("oc:isRunning");clientErrored=tag.getBoolean("oc:hasErrored");if(level!=null&&level.isClientSide){users=tag.getList("oc:users",Tag.TAG_STRING).stream().map(Tag::getAsString).toArray(String[]::new);redstone.loadClient(tag);return;}pending=tag.copy();runtime.load(tag.getCompound("oc:computer"));redstone.load(tag);byte[] sides=tag.getByteArray("oc:outputs");for(int index=0;index<Math.min(6,sides.length);index++)outputSides[index]=sides[index]!=0;owner=tag.getString("oc:owner");if(tag.hasUUID("oc:ownerUuid"))ownerId=tag.getUUID("oc:ownerUuid");}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);runtime.saveComponents();CompoundTag data=new CompoundTag();info.save(data,registries);tag.put("oc:info",data);tag.put("oc:computer",runtime.save(registries));byte[] sides=new byte[6];for(int index=0;index<6;index++)sides[index]=(byte)(outputSides[index]?1:0);tag.putByteArray("oc:outputs",sides);redstone.save(tag);tag.putString("oc:owner",owner);if(ownerId!=null)tag.putUUID("oc:ownerUuid",ownerId);
        if(snooper!=null){CompoundTag control=new CompoundTag();snooper.save(control);tag.put("oc:snooper",control);ListTag outer=new ListTag(),nodes=new ListTag();for(int index=0;index<6;index++){CompoundTag plug=new CompoundTag(),node=new CompoundTag();plugs[index].node.save(plug);componentNodes[index].save(node);outer.add(plug);nodes.add(node);}tag.put("oc:plugs",outer);tag.put("oc:componentNodes",nodes);}else if(pending!=null){for(String key:new String[]{"oc:snooper","oc:plugs","oc:componentNodes"})if(pending.contains(key))tag.put(key,pending.get(key).copy());}}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag(),data=new CompoundTag();info.save(data,registries);tag.put("oc:info",data);tag.putBoolean("oc:isRunning",clientRunning);tag.putBoolean("oc:hasErrored",clientErrored);ListTag names=new ListTag();for(String name:level!=null&&!level.isClientSide&&machine()!=null?machine().users():users)names.add(StringTag.valueOf(name));tag.put("oc:users",names);redstone.saveClient(tag);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public IEnergyStorage energy(Direction side){if(side==facing()||Settings.get().ignorePower)return null;return new IEnergyStorage(){private double ratio(){return Settings.get().ratioForgeEnergy;}private boolean valid(){return level!=null&&!level.isClientSide&&!closed&&side!=facing()&&!Settings.get().ignorePower&&ratio()>0;}public int receiveEnergy(int max,boolean simulate){if(!valid()||max<=0)return 0;initialize();int accepted=(int)Math.max(0,Math.min(max,Math.min(Settings.get().caseRate[0],snooper.globalBufferSize()-snooper.globalBuffer())/ratio()));if(!simulate&&accepted>0){snooper.changeBuffer(accepted*ratio());markChanged();}return accepted;}public int extractEnergy(int max,boolean simulate){return 0;}public int getEnergyStored(){return !valid()||snooper==null?0:(int)Math.min(Integer.MAX_VALUE,snooper.globalBuffer()/ratio());}public int getMaxEnergyStored(){return !valid()||snooper==null?0:(int)Math.min(Integer.MAX_VALUE,snooper.globalBufferSize()/ratio());}public boolean canExtract(){return false;}public boolean canReceive(){return valid();}};}
    @Override public int getContainerSize(){return info.components.length;}
    @Override public boolean isEmpty(){return Arrays.stream(info.components).allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<info.components.length?info.components[slot]:ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){return ItemStack.EMPTY;}
    @Override public ItemStack removeItemNoUpdate(int slot){return ItemStack.EMPTY;}
    @Override public void setItem(int slot,ItemStack stack){}
    @Override public void clearContent(){}
    @Override public boolean stillValid(Player player){return canInteract(player);}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return false;}
    @Override public int[] getSlotsForFace(Direction side){return new int[0];}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return false;}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return false;}
    private final class Plug implements Environment {
        private final Direction side;
        private final Connector node=Network.newNode(this,Visibility.Network).withConnector().create();
        private Plug(Direction side){this.side=side;}
        @Override public Connector node(){return node;}
        private boolean primary(){if(node.network()==null)return false;for(Plug plug:plugs)if(plug!=null&&plug!=this&&plug.side.get3DDataValue()<side.get3DDataValue()&&plug.node.network()==node.network())return false;return true;}
        @Override public void onConnect(Node connected){topologyDirty.set(true);}
        @Override public void onDisconnect(Node connected){topologyDirty.set(true);}
        @Override public void onMessage(Message message){if(primary()&&snooper!=null&&message.name().equals("network.message")&&message.source().network()!=snooper.network()){for(Plug plug:plugs)if(plug!=null&&plug.node==message.source())return;snooper.sendToReachable(message.name(),message.data());}}
    }
}
