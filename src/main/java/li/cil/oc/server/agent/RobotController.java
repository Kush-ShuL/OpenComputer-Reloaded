package li.cil.oc.server.agent;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.event.RobotMoveEvent;
import li.cil.oc.api.internal.MultiTank;
import li.cil.oc.api.network.*;
import li.cil.oc.common.block.entity.RobotBlockEntity;
import li.cil.oc.common.init.Agents;
import li.cil.oc.common.item.data.RobotData;
import li.cil.oc.common.redstone.*;
import li.cil.oc.server.machine.Machine;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;

/** Persistent robot state retained while Minecraft replaces the movable proxy block entity. */
public final class RobotController implements li.cil.oc.api.internal.Robot,AgentHost,RedstoneHost {
    public RobotBlockEntity proxy;
    public final RobotData info=new RobotData();
    private final AgentRuntime runtime=new AgentRuntime(this);
    public li.cil.oc.common.component.TextBuffer clientScreen;
    public boolean clientHasKeyboard,clientHasErrored;
    private String[] users=new String[0];
    private final AtomicBoolean dirty=new AtomicBoolean();
    public final AgentStorage equipment=new AgentStorage(4,()->4,this::equipmentChanged,this::beforeEquipmentChange);
    public final AgentStorage inventory=new AgentStorage(96,this::inventoryCapacity,this::inventoryChanged);
    private final RedstoneState redstone=new RedstoneState(this,change->{if(machine()!=null&&machine().node().network()!=null)machine().node().sendToNeighbors("redstone.changed",change);});
    private String owner=Settings.get().fakePlayerName;
    private UUID ownerId=UUID.fromString(Settings.get().fakePlayerUuid);
    private int selected,tank;
    private boolean loading;
    private boolean equipmentMutation;
    private final ItemStack[] installedEquipment={ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
    public int animationTicks,animationTotal,animationKind;
    public BlockPos moveFrom;
    public RobotController(RobotBlockEntity proxy){this.proxy=proxy;}
    public void setProxy(RobotBlockEntity proxy){this.proxy=proxy;}
    @Override public AgentRuntime runtime(){return runtime;}
    @Override public boolean robot(){return true;}
    @Override public Level world(){return proxy.getLevel();}
    @Override public BlockPos blockPosition(){return proxy.getBlockPos();}
    @Override public double xPosition(){return blockPosition().getX()+.5;}
    @Override public double yPosition(){return blockPosition().getY()+.5;}
    @Override public double zPosition(){return blockPosition().getZ()+.5;}
    @Override public void markChanged(){dirty.set(true);}
    @Override public Machine machine(){return runtime.machine();}
    @Override public Node node(){return machine()==null?null:machine().node();}
    @Override public void onConnect(Node node){}
    @Override public void onDisconnect(Node node){}
    @Override public void onMessage(Message message){}
    @Override public void onMachineConnect(Node node){if(machine()!=null&&node==machine().node())runtime.connect();}
    @Override public void onMachineDisconnect(Node node){}
    @Override public int tier(){return info.tier;}
    @Override public Direction facing(){return proxy.getBlockState().getValue(li.cil.oc.common.block.RobotBlock.FACING);}
    @Override public Direction toGlobal(Direction side){if(side.getAxis().isVertical())return side;for(int i=0;i<facing().get2DDataValue();i++)side=side.getClockWise();return side;}
    @Override public Direction toLocal(Direction side){if(side.getAxis().isVertical())return side;for(int i=0;i<facing().get2DDataValue();i++)side=side.getCounterClockWise();return side;}
    @Override public Container equipmentInventory(){return equipment;}
    @Override public Container mainInventory(){return inventory;}
    @Override public MultiTank tank(){return runtime;}
    @Override public int selectedSlot(){return selected;}
    @Override public void setSelectedSlot(int slot){selected=Math.min(Math.max(0,slot),inventory.getContainerSize()-1);synchronize();}
    @Override public int selectedTank(){return tank;}
    @Override public void setSelectedTank(int index){tank=index;markChanged();}
    @Override public Player player(){return runtime.player().orient(facing(),false);}
    @Override public String name(){return info.name;}
    @Override public void setName(String name){info.name=name;synchronize();}
    @Override public String ownerName(){return owner;}
    @Override public UUID ownerUUID(){return ownerId;}
    @Override public int componentCount(){return info.components.length;}
    @Override public Environment getComponentInSlot(int index){return runtime.environment(index);}
    @Override public void synchronizeSlot(int slot){runtime.saveComponents();synchronize();}
    @Override public boolean shouldAnimate(){return proxy.clientRunning;}
    @Override public RedstoneState redstoneState(){return redstone;}
    public void initialize(ItemStack stack,Player player){loading=true;try{info.load(stack,world().registryAccess());if(info.name.isEmpty())info.name=RobotData.randomName();owner=player instanceof AgentPlayer agent?agent.agent().ownerName():player.getGameProfile().getName();ownerId=player instanceof AgentPlayer agent?agent.agent().ownerUUID():AgentPlayer.determineUUID(player.getUUID());}finally{for(int slot=1;slot<4;slot++)installedEquipment[slot-1]=equipment.items.get(slot);loading=false;}if(runtime.initialized())runtime.rebuild();else runtime.initialize();if(runtime.control()!=null)runtime.control().node().changeBuffer(info.robotEnergy-runtime.control().node().localBuffer());synchronize();}
    public int inventoryCapacity(){return inventoryCapacityRaw();}
    @Override public List<ComponentSlot> componentStacks(){List<ComponentSlot> stacks=new ArrayList<>();for(int i=1;i<=Math.min(3,info.containers.length);i++)stacks.add(new ComponentSlot(i,equipment.items.get(i)));int componentOffset=4+inventoryCapacityRaw();for(int i=0;i<info.components.length;i++)stacks.add(new ComponentSlot(componentOffset+i,info.components[i]));return stacks;}
    private int inventoryCapacityRaw(){int capacity=0;for(ItemStack stack:info.components){var driver=Driver.driverFor(stack,getClass());if(driver instanceof li.cil.oc.api.driver.item.Inventory inventory)capacity+=inventory.inventoryCapacity(stack);}for(int i=1;i<=Math.min(3,info.containers.length);i++){var driver=Driver.driverFor(equipment.items.get(i),getClass());if(driver instanceof li.cil.oc.api.driver.item.Inventory inventory)capacity+=inventory.inventoryCapacity(equipment.items.get(i));}return Math.min(Math.max(0,96-info.components.length),capacity);}
    @Override public Iterable<ItemStack> internalComponents(){return componentStacks().stream().map(ComponentSlot::stack).toList();}
    @Override public int componentSlot(String address){return runtime.componentSlot(address);}
    @Override public void inventoryChanged(){markChanged();if(machine()!=null)machine().signal("inventory_changed",selected+1);}
    private void beforeEquipmentChange(int slot){if(slot>0&&!loading&&runtime.initialized()){if(isFloppySlot(slot,equipment.items.get(slot)))li.cil.oc.common.DiskSounds.play(this,"floppy_eject");equipmentMutation=true;runtime.removeComponent(slot);}}
    private boolean isFloppySlot(int slot,ItemStack stack){if(slot<=0||slot>info.containers.length||stack.isEmpty())return false;var driver=Driver.driverFor(stack,getClass());return driver!=null&&li.cil.oc.api.driver.item.Slot.Floppy.equals(driver.slot(stack));}
    private void equipmentChanged(){boolean componentsChanged=equipmentMutation;equipmentMutation=false;for(int slot=1;slot<4;slot++){ItemStack current=equipment.items.get(slot);if(installedEquipment[slot-1]!=current){componentsChanged=true;installedEquipment[slot-1]=current;if(!loading&&runtime.initialized()&&isFloppySlot(slot,current))li.cil.oc.common.DiskSounds.play(this,"floppy_insert");}}if(componentsChanged&&!loading&&runtime.initialized())runtime.rebuild();InventoryUpgradeCapacity.reconcileRobot(this);inventoryChanged();synchronize();}
    @Override public void synchronize(){markChanged();if(world()!=null&&!world().isClientSide)proxy.synchronize();}
    @Override public void animate(double seconds,int kind){animationTotal=animationTicks=Math.max(1,(int)(seconds*20));animationKind=kind;synchronize();}
    public void tick(){
        if(animationTicks>0&&--animationTicks==0){animationKind=0;moveFrom=null;}
        if(world()==null)return;
        if(world().isClientSide){tickItems();return;}
        if(dirty.getAndSet(false))proxy.setChanged();runtime.tick();redstone.tick();
        InventoryUpgradeCapacity.reconcileRobot(this);
        boolean running=machine().isRunning(),errored=machine().lastError()!=null;String[] nextUsers=machine().users();
        boolean stateChanged=proxy.clientRunning!=running||clientHasErrored!=errored||!Arrays.equals(users,nextUsers);
        proxy.clientRunning=running;clientHasErrored=errored;users=nextUsers;
        redstone.setEnabled(running&&componentStacks().stream().anyMatch(slot->Driver.driverFor(slot.stack(),getClass()) instanceof li.cil.oc.server.driver.item.RedstoneCard));
        if(tier()==3&&runtime.control()!=null)runtime.control().node().changeBuffer(Double.POSITIVE_INFINITY);
        if(runtime.control()!=null){info.totalEnergy=(int)runtime.control().node().globalBuffer();info.robotEnergy=(int)runtime.control().node().localBuffer();}
        if(stateChanged||world().getGameTime()%20==0)synchronize();
        tickItems();
    }
    private void tickItems(){
        for(int slot=0;slot<4+inventory.getContainerSize();slot++){
            ItemStack stack=slot<4?equipment.getItem(slot):inventory.getItem(slot-4);
            if(!stack.isEmpty())try{stack.inventoryTick(world(),world().isClientSide?null:runtime.player(),slot,slot==0);}catch(NullPointerException error){if(!world().isClientSide)throw error;}
        }
    }
    public boolean move(Direction direction){
        var level=(ServerLevel)world();BlockPos old=blockPosition(),target=old.relative(direction);
        if(!level.hasChunkAt(target)||!level.isInWorldBounds(target)||!level.getWorldBorder().isWithinBounds(target))return false;
        var event=new RobotMoveEvent.Pre(this,direction);NeoForge.EVENT_BUS.post(event);if(event.isCanceled())return false;
        var before=level.getBlockState(target);if(!before.canBeReplaced())return false;
        RobotBlockEntity previous=proxy;
        try{
            li.cil.oc.common.block.RobotBlock.MOVING.set(this);
            if(!level.setBlock(target,previous.getBlockState(),3)||!(level.getBlockEntity(target) instanceof RobotBlockEntity next)||next.controller()!=this){proxy=previous;level.setBlock(target,before,3);return false;}
            previous.movingAway=true;level.setBlock(old,Agents.AFTERIMAGE.get().defaultBlockState(),3);level.scheduleTick(old,Agents.AFTERIMAGE.get(),Math.max(1,(int)(delay("move")*20)+2));
            moveFrom=old;animate(delay("move"),2);redstone.rescan();li.cil.oc.api.Network.joinOrCreateNetwork(next);NeoForge.EVENT_BUS.post(new RobotMoveEvent.Post(this,direction));return true;
        }finally{li.cil.oc.common.block.RobotBlock.MOVING.remove();}
    }
    public boolean turn(boolean clockwise){var state=proxy.getBlockState();Direction facing=clockwise?facing().getClockWise():facing().getCounterClockWise();world().setBlock(blockPosition(),state.setValue(li.cil.oc.common.block.RobotBlock.FACING,facing),3);animate(delay("turn"),clockwise?3:4);redstone.rescan();return true;}
    public void prepareDrop(){if(runtime.control()!=null){info.totalEnergy=(int)runtime.control().node().globalBuffer();info.robotEnergy=(int)runtime.control().node().localBuffer();}runtime.close();}
    public ItemStack dropStack(){runtime.saveComponents();if(runtime.control()!=null){if(runtime.control().node().network()!=null)info.totalEnergy=(int)runtime.control().node().globalBuffer();info.robotEnergy=(int)runtime.control().node().localBuffer();}return info.createItemStack(world().registryAccess());}
    public void load(CompoundTag tag,HolderLookup.Provider registries){loading=true;try{info.load(tag,registries);CompoundTag equipmentTag=tag.getCompound("oc:equipment"),inventoryTag=tag.getCompound("oc:inventory");equipment.load(equipmentTag,registries);inventory.load(inventoryTag,registries);owner=tag.contains("oc:owner")?tag.getString("oc:owner"):owner;if(tag.hasUUID("oc:ownerUuid"))ownerId=tag.getUUID("oc:ownerUuid");selected=tag.getInt("oc:selectedSlot");tank=tag.getInt("oc:selectedTank");animationTicks=tag.getInt("oc:animationTicksLeft");animationTotal=tag.getInt("oc:animationTicksTotal");animationKind=tag.getInt("oc:animationKind");moveFrom=tag.contains("oc:moveFrom")?BlockPos.of(tag.getLong("oc:moveFrom")):null;clientHasErrored=tag.getBoolean("oc:hasErrored");users=tag.getList("oc:users",net.minecraft.nbt.Tag.TAG_STRING).stream().map(net.minecraft.nbt.Tag::getAsString).toArray(String[]::new);if(tag.contains("output",net.minecraft.nbt.Tag.TAG_INT_ARRAY))redstone.loadClient(tag);else redstone.load(tag);runtime.load(tag);}finally{for(int slot=1;slot<4;slot++)installedEquipment[slot-1]=equipment.items.get(slot);loading=false;}}
    public void save(CompoundTag tag,HolderLookup.Provider registries){runtime.save(tag,registries);info.save(tag,registries);CompoundTag equip=new CompoundTag(),main=new CompoundTag();equipment.save(equip,registries);inventory.save(main,registries);tag.put("oc:equipment",equip);tag.put("oc:inventory",main);tag.putString("oc:owner",owner);tag.putUUID("oc:ownerUuid",ownerId);tag.putInt("oc:selectedSlot",selected);tag.putInt("oc:selectedTank",tank);tag.putInt("oc:animationTicksLeft",animationTicks);tag.putInt("oc:animationTicksTotal",animationTotal);tag.putInt("oc:animationKind",animationKind);if(moveFrom!=null)tag.putLong("oc:moveFrom",moveFrom.asLong());redstone.save(tag);}
    public void writeClient(CompoundTag tag,HolderLookup.Provider registries){info.save(tag,registries);CompoundTag equip=new CompoundTag(),main=new CompoundTag();equipment.save(equip,registries);inventory.save(main,registries);tag.put("oc:equipment",equip);tag.put("oc:inventory",main);tag.putString("oc:owner",owner);tag.putUUID("oc:ownerUuid",ownerId);tag.putInt("oc:selectedSlot",selected);tag.putInt("oc:animationTicksLeft",animationTicks);tag.putInt("oc:animationTicksTotal",animationTotal);tag.putInt("oc:animationKind",animationKind);if(moveFrom!=null)tag.putLong("oc:moveFrom",moveFrom.asLong());tag.putBoolean("oc:isRunning",machine()!=null&&machine().isRunning());tag.putBoolean("oc:hasErrored",machine()!=null&&machine().lastError()!=null);net.minecraft.nbt.ListTag names=new net.minecraft.nbt.ListTag();if(machine()!=null)for(String user:machine().users())names.add(net.minecraft.nbt.StringTag.valueOf(user));tag.put("oc:users",names);if(node()!=null)tag.putString("oc:address",node().address());redstone.saveClient(tag);li.cil.oc.common.network.AgentNetworking.writeScreen(runtime.screen(),tag);}
    @Override public int getContainerSize(){return 4+inventoryCapacityRaw()+componentCount();}
    @Override public boolean isEmpty(){return equipment.isEmpty()&&inventory.isEmpty()&&Arrays.stream(info.components).allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){if(slot<4)return equipment.getItem(slot);int inv=inventoryCapacityRaw();return slot<4+inv?inventory.getItem(slot-4):slot-4-inv<info.components.length?info.components[slot-4-inv]:ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){if(slot<4)return equipment.removeItem(slot,count);return slot<4+inventoryCapacityRaw()?inventory.removeItem(slot-4,count):ItemStack.EMPTY;}
    @Override public ItemStack removeItemNoUpdate(int slot){if(slot<4)return equipment.removeItemNoUpdate(slot);return slot<4+inventoryCapacityRaw()?inventory.removeItemNoUpdate(slot-4):ItemStack.EMPTY;}
    @Override public void setItem(int slot,ItemStack stack){if(slot<4)equipment.setItem(slot,stack);else if(slot<4+inventoryCapacityRaw())inventory.setItem(slot-4,stack);}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){if(slot==0)return true;if(slot>=4)return slot<4+inventoryCapacityRaw();if(slot>info.containers.length)return false;var bay=Driver.driverFor(info.containers[slot-1],getClass());var driver=Driver.driverFor(stack,getClass());return bay instanceof li.cil.oc.api.driver.item.Container container&&driver!=null&&container.providedSlot(info.containers[slot-1]).equals(driver.slot(stack))&&driver.tier(stack)<=container.providedTier(info.containers[slot-1]);}
    @Override public void setChanged(){inventoryChanged();}
    public boolean canInteract(String name){return world().isClientSide?!Settings.get().canComputersBeOwned||users.length==0||Arrays.asList(users).contains(name):machine()==null||machine().canInteract(name);}
    @Override public boolean stillValid(Player player){return world()!=null&&!proxy.isRemoved()&&world().getBlockEntity(blockPosition())==proxy&&player.level()==world()&&player.distanceToSqr(xPosition(),yPosition(),zPosition())<=64&&canInteract(player instanceof AgentPlayer agent?agent.agent().ownerName():player.getGameProfile().getName())&&(tier()!=3||player.getAbilities().instabuild);}
    @Override public void clearContent(){equipment.clearContent();inventory.clearContent();}
    @Override public int[] getSlotsForFace(Direction side){return java.util.stream.IntStream.range(4,4+inventoryCapacityRaw()).toArray();}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return slot>=4&&slot<4+inventoryCapacityRaw();}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return slot>=4&&slot<4+inventoryCapacityRaw();}
}
