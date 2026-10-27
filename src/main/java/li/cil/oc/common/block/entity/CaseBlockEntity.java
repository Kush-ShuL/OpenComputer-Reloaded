package li.cil.oc.common.block.entity;

import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.util.Lifecycle;
import li.cil.oc.api.machine.MachineHost;
import li.cil.oc.api.network.*;
import li.cil.oc.common.InventorySlots;
import li.cil.oc.common.block.CaseBlock;
import li.cil.oc.common.menu.CaseMenu;
import li.cil.oc.server.machine.Machine;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import java.util.*;

/** Tiered OC case: inventory components, machine lifecycle, power and persistence. */
public final class CaseBlockEntity extends BlockEntity implements Case,MenuProvider,SidedEnvironment,li.cil.oc.server.PacketSender.ActivityListener,li.cil.oc.common.capabilities.EnergyProvider,li.cil.oc.common.redstone.RedstoneHost,Analyzable,DeviceInfo {
    public long lastFileSystemAccess, lastNetworkActivity;
    public boolean hasErrored;
    @Override public void onComponentActivity(String address,boolean network){if(network)lastNetworkActivity=System.currentTimeMillis();else lastFileSystemAccess=System.currentTimeMillis();}
    public final int tier;
    private final NonNullList<ItemStack> items;
    private final ManagedEnvironment[] environments;
    private Machine machine;
    private CompoundTag pendingMachine;
    private int color;
    private String[] users=new String[0];
    private boolean initialized,loading;
    private final java.util.concurrent.atomic.AtomicBoolean changeScheduled=new java.util.concurrent.atomic.AtomicBoolean();
    private final li.cil.oc.common.redstone.RedstoneState redstone=new li.cil.oc.common.redstone.RedstoneState(this,change->{
        if(machine!=null&&machine.node().network()!=null)machine.node().sendToNeighbors("redstone.changed",change);
    });
    @Override public li.cil.oc.common.redstone.RedstoneState redstoneState(){return redstone;}
    public CaseBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,int tier){
        super(type,pos,state);this.tier=tier;items=NonNullList.withSize(InventorySlots.computer[tier].length,ItemStack.EMPTY);environments=new ManagedEnvironment[items.size()];color=new int[]{0xABABAB,0xFFFF66,0x66FFFF,0xC354CD}[tier];
    }
    @Override public int tier(){return tier;}
    @Override public Map<String,String> getDeviceInfo(){return Map.of("class","system","description","Computer","vendor",li.cil.oc.Constants.DeviceInfo.DefaultVendor,"product","Blocker","capacity",Integer.toString(items.size()));}
    @Override public Level world(){return level;}
    @Override public double xPosition(){return worldPosition.getX()+.5;}
    @Override public double yPosition(){return worldPosition.getY()+.5;}
    @Override public double zPosition(){return worldPosition.getZ()+.5;}
    @Override public void markChanged(){changeScheduled.set(true);}
    @Override public Machine machine(){
        if(machine==null&&level!=null&&!level.isClientSide)machine=(Machine)li.cil.oc.api.Machine.create(this);
        return machine;
    }
    @Override public Node node(){Machine m=machine();return m==null?null:m.node();}
    @Override public Node sidedNode(Direction side){return canConnect(side)?node():null;}
    @Override public Node[] onAnalyze(Player player,Direction side,float x,float y,float z){return new Node[]{node()};}
    @Override public boolean canConnect(Direction side){return side!=facing();}
    @Override public Iterable<ItemStack> internalComponents(){return Collections.unmodifiableList(items);}
    @Override public int componentSlot(String address){for(int i=0;i<environments.length;i++)if(environments[i]!=null&&environments[i].node()!=null&&Objects.equals(address,environments[i].node().address()))return i;return -1;}
    @Override public Direction facing(){return getBlockState().getValue(CaseBlock.FACING);}
    @Override public Direction toGlobal(Direction side){if(side==null||side.getAxis()==Direction.Axis.Y)return side;int turns=facing().get2DDataValue();for(int i=0;i<turns;i++)side=side.getClockWise();return side;}
    @Override public Direction toLocal(Direction side){if(side==null||side.getAxis()==Direction.Axis.Y)return side;int turns=facing().get2DDataValue();for(int i=0;i<turns;i++)side=side.getCounterClockWise();return side;}
    @Override public int getColor(){return color;}
    @Override public void setColor(int value){color=value;setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override public boolean controlsConnectivity(){return false;}
    public boolean canInteract(Player player){String name=player instanceof li.cil.oc.server.agent.AgentPlayer agent?agent.agent().ownerName():player.getGameProfile().getName();if(level!=null&&level.isClientSide)return !Settings.get().canComputersBeOwned||users.length==0||Arrays.asList(users).contains(name);return machine()==null||machine().canInteract(name);}
    @Override public void onConnect(Node node){}
    @Override public void onDisconnect(Node node){}
    @Override public void onMessage(Message message){}
    @Override public void onMachineConnect(Node node){if(machine!=null&&node==machine.node())for(ManagedEnvironment environment:environments)if(environment!=null&&environment.node()!=null)machine.node().connect(environment.node());}
    @Override public void onMachineDisconnect(Node node){if(machine!=null&&node==machine.node())flushComponents();}
    private void initialize(){
        if(initialized||level==null||level.isClientSide)return;initialized=true;loading=true;
        try{Machine m=machine();for(int i=0;i<items.size();i++)createEnvironment(i);if(pendingMachine!=null){m.load(pendingMachine,level.registryAccess());pendingMachine=null;}li.cil.oc.api.Network.joinOrCreateNetwork(this);m.onHostChanged();}
        finally{loading=false;}
    }
    private void createEnvironment(int slot){
        ItemStack stack=items.get(slot);if(stack.isEmpty())return;var driver=Driver.driverFor(stack,getClass());if(driver==null)return;
        ManagedEnvironment environment=driver.createEnvironment(stack,this);environments[slot]=environment;
        if(environment!=null){
            lifecycle(environment,Lifecycle.LifecycleState.Constructing);
            try{environment.load(componentData(driver,stack),level.registryAccess());}catch(Throwable error){li.cil.oc.OpenComputers.LOGGER.warn("A computer item component failed while loading",error);}
            lifecycle(environment,Lifecycle.LifecycleState.Initializing);
            if(machine!=null&&machine.node().network()!=null&&environment.node()!=null)machine.node().connect(environment.node());
            lifecycle(environment,Lifecycle.LifecycleState.Initialized);if(!loading)flushComponent(slot);
        }
    }
    private void flushComponent(int slot){
        flushComponent(slot,environments[slot]);
    }
    private void flushComponent(int slot,ManagedEnvironment environment){
        ItemStack stack=items.get(slot);if(environment==null||stack.isEmpty()||level==null||level.isClientSide)return;
        var driver=Driver.driverFor(stack,getClass());if(driver!=null){CompoundTag data=componentData(driver,stack);environment.save(data,level.registryAccess());driver.saveDataTag(stack,data);}
    }
    private static CompoundTag componentData(li.cil.oc.api.driver.DriverItem driver,ItemStack stack){
        CompoundTag data=driver.dataTag(stack);
        return data!=null?data:stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("oc:data");
    }
    public void flushComponents(){for(int i=0;i<items.size();i++)flushComponent(i);}
    public void prepareDrops(){for(int i=0;i<items.size();i++)releaseComponent(i);}
    private static void lifecycle(ManagedEnvironment environment,Lifecycle.LifecycleState state){if(environment instanceof Lifecycle listener)listener.onLifecycleStateChange(state);}
    private void diskSound(String name){if(level!=null&&!level.isClientSide&&Settings.get().soundVolume>0)level.playSound(null,worldPosition,net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("opencomputers",name)),net.minecraft.sounds.SoundSource.BLOCKS,Settings.get().soundVolume,1);}
    private void releaseComponent(int slot){ManagedEnvironment old=environments[slot];environments[slot]=null;if(old!=null){lifecycle(old,Lifecycle.LifecycleState.Disposing);if(old.node()!=null)old.node().remove();flushComponent(slot,old);if(old.node()!=null)old.node().remove();lifecycle(old,Lifecycle.LifecycleState.Disposed);}if(level!=null&&!level.isClientSide&&initialized&&!loading&&!items.get(slot).isEmpty()){String kind=InventorySlots.computer[tier][slot].slot();if(kind.equals("floppy"))diskSound("floppy_eject");if(kind.equals("cpu"))machine().stop();}}
    private void inventoryChanged(int slot){if(level!=null&&!level.isClientSide&&initialized&&!loading){createEnvironment(slot);machine().onHostChanged();if(InventorySlots.computer[tier][slot].slot().equals("floppy")&&!items.get(slot).isEmpty())diskSound("floppy_insert");}setChanged();}
    public static void tick(Level level,BlockPos pos,BlockState state,CaseBlockEntity entity){
        if(level.isClientSide)return;entity.initialize();Machine machine=entity.machine();
        entity.redstone.tick();
        if(entity.changeScheduled.getAndSet(false))entity.setChanged();
        if(tierCreative(entity.tier)&&level.getGameTime()%Settings.get().tickFrequency==0)machine.node().changeBuffer(Double.POSITIVE_INFINITY);
        machine.update();for(ManagedEnvironment env:entity.environments)if(env!=null&&env.canUpdate())env.update();
        if(entity.isRemoved()||level.getBlockEntity(pos)!=entity)return;
        boolean running=machine.isRunning();
        entity.redstone.setEnabled(running&&entity.items.stream().anyMatch(stack->Driver.driverFor(stack,entity.getClass()) instanceof li.cil.oc.server.driver.item.RedstoneCard));
        BlockState current=level.getBlockState(pos);if(current.getBlock()==state.getBlock()&&current.getValue(CaseBlock.RUNNING)!=running)level.setBlock(pos,current.setValue(CaseBlock.RUNNING,running),3);
        boolean errored=machine.lastError()!=null;
        String[] users=machine.users();if(entity.hasErrored!=errored||!Arrays.equals(entity.users,users)){entity.hasErrored=errored;entity.users=users;entity.setChanged();level.sendBlockUpdated(pos,entity.getBlockState(),entity.getBlockState(),3);}
    }
    private static boolean tierCreative(int tier){return tier==3;}
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide)initialize();}
    private void dispose(){if(machine!=null){flushComponents();machine.node().remove();}for(ManagedEnvironment env:environments)if(env!=null&&env.node()!=null)env.node().remove();initialized=false;}
    @Override public void setRemoved(){dispose();super.setRemoved();}
    @Override public void onChunkUnloaded(){dispose();super.onChunkUnloaded();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(tag.contains("oc:color"))color=tag.getInt("oc:color");
        hasErrored=tag.getBoolean("oc:hasErrored");
        if(level!=null&&level.isClientSide){users=tag.getList("oc:users",net.minecraft.nbt.Tag.TAG_STRING).stream().map(net.minecraft.nbt.Tag::getAsString).toArray(String[]::new);redstone.loadClient(tag);return;}
        redstone.load(tag);
        loading=true;try{ContainerHelper.loadAllItems(tag,items,registries);pendingMachine=tag.contains("oc:machine")?tag.getCompound("oc:machine"):null;}
        finally{loading=false;}
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);flushComponents();ContainerHelper.saveAllItems(tag,items,registries);tag.putByte("oc:tier",(byte)tier);tag.putInt("oc:color",color);
        redstone.save(tag);
        if(machine!=null){CompoundTag data=new CompoundTag();machine.save(data,registries);tag.put("oc:machine",data);}else if(pendingMachine!=null)tag.put("oc:machine",pendingMachine.copy());
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();tag.putInt("oc:color",color);tag.putBoolean("oc:hasErrored",hasErrored);net.minecraft.nbt.ListTag names=new net.minecraft.nbt.ListTag();for(String name:machine==null?users:machine.users())names.add(net.minecraft.nbt.StringTag.valueOf(name));tag.put("oc:users",names);redstone.saveClient(tag);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public int getContainerSize(){return items.size();}
    @Override public int getMaxStackSize(){return 1;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<items.size()?items.get(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int amount){if(slot<0||slot>=items.size()||amount<=0||items.get(slot).isEmpty())return ItemStack.EMPTY;releaseComponent(slot);ItemStack result=ContainerHelper.removeItem(items,slot,amount);inventoryChanged(slot);return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){if(slot<0||slot>=items.size()||items.get(slot).isEmpty())return ItemStack.EMPTY;releaseComponent(slot);ItemStack result=ContainerHelper.takeItem(items,slot);inventoryChanged(slot);return result;}
    @Override public void setItem(int slot,ItemStack stack){if(slot<0||slot>=items.size()||items.get(slot)==stack||items.get(slot).isEmpty()&&stack.isEmpty())return;ItemStack next=stack.copyWithCount(Math.min(1,stack.getCount()));releaseComponent(slot);items.set(slot,next);inventoryChanged(slot);}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){if(slot<0||slot>=items.size())return false;var driver=Driver.driverFor(stack,getClass());var allowed=InventorySlots.computer[tier][slot];return driver!=null&&allowed.slot().equals(driver.slot(stack))&&driver.tier(stack)<=allowed.tier();}
    @Override public boolean stillValid(Player player){return level!=null&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(xPosition(),yPosition(),zPosition())<=64&&canInteract(player)&&(tier!=3||player.getAbilities().instabuild);}
    @Override public void clearContent(){for(int i=0;i<items.size();i++){releaseComponent(i);items.set(i,ItemStack.EMPTY);}if(machine!=null)machine.onHostChanged();setChanged();}
    @Override public Component getDisplayName(){return Component.translatable("block.opencomputers.case"+(tier==3?"creative":tier+1));}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return stillValid(player)?new CaseMenu(id,inventory,this):null;}
    public IEnergyStorage energy(Direction side){if(side==facing()||Settings.get().ignorePower)return null;return new IEnergyStorage(){
        private Machine computer(){return side==facing()||Settings.get().ignorePower?null:machine();}
        public int receiveEnergy(int maximum,boolean simulate){Machine m=computer();if(m==null)return 0;double ratio=Settings.get().ratioForgeEnergy;if(ratio<=0)return 0;double room=m.node().globalBufferSize()-m.node().globalBuffer();int accepted=(int)Math.max(0,Math.min(maximum,Math.min(room,Settings.get().caseRate[tier])/ratio));if(!simulate)m.node().changeBuffer(accepted*ratio);return accepted;}
        public int extractEnergy(int amount,boolean simulate){return 0;}
        public int getEnergyStored(){Machine m=computer();return m==null||Settings.get().ratioForgeEnergy<=0?0:(int)Math.min(Integer.MAX_VALUE,m.node().globalBuffer()/Settings.get().ratioForgeEnergy);}
        public int getMaxEnergyStored(){Machine m=computer();return m==null||Settings.get().ratioForgeEnergy<=0?0:(int)Math.min(Integer.MAX_VALUE,m.node().globalBufferSize()/Settings.get().ratioForgeEnergy);}
        public boolean canExtract(){return false;}public boolean canReceive(){return computer()!=null&&Settings.get().ratioForgeEnergy>0;}
    };}
}
