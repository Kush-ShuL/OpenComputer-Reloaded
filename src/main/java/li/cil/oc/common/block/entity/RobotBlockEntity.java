package li.cil.oc.common.block.entity;

import li.cil.oc.api.Network;
import li.cil.oc.api.internal.MultiTank;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.common.block.RobotBlock;
import li.cil.oc.common.menu.AgentMenu;
import li.cil.oc.common.redstone.*;
import li.cil.oc.server.agent.RobotController;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/** Public world proxy for the robot's stable internal machine host. */
public final class RobotBlockEntity extends BlockEntity implements li.cil.oc.api.internal.Robot,MenuProvider,Analyzable,RedstoneHost,RotatableBlockEntity {
    private RobotController controller;
    private li.cil.oc.api.network.Component outside;
    public boolean movingAway,clientRunning,playerDropGenerated;
    public RobotBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);controller=RobotBlock.MOVING.get();if(controller==null)controller=new RobotController(this);else controller.setProxy(this);}
    public RobotController controller(){return controller;}
    public void attach(RobotController controller){this.controller=controller;controller.setProxy(this);}
    public Node externalNode(){return node();}
    @Override public Node node(){
        if(level==null||level.isClientSide)return null;
        if(outside==null&&RobotBlock.MOVING.get()==controller)return null;
        controller.runtime().initialize();
        if(outside==null){outside=Network.newNode(this,Visibility.Network).withComponent("robot",Visibility.Neighbors).create();CompoundTag address=new CompoundTag();address.putString("address",controller.node().address());outside.load(address);}
        return outside;
    }
    public void tick(){controller.tick();if(level!=null&&!level.isClientSide&&node().network()==null)Network.joinOrCreateNetwork(this);}
    public void synchronize(){if(level!=null&&!level.isClientSide){setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}}
    @Override public void onLoad(){super.onLoad();if(level!=null&&level.isClientSide)li.cil.oc.client.ComputerSounds.robotChanged(controller,controller,clientRunning,true);}
    @Override public void setRemoved(){if(level!=null&&level.isClientSide)li.cil.oc.client.ComputerSounds.robotRemoved(this,false);if(outside!=null)outside.remove();if(!movingAway&&controller.proxy==this)controller.runtime().close();super.setRemoved();}
    @Override public void onChunkUnloaded(){if(level!=null&&level.isClientSide)li.cil.oc.client.ComputerSounds.robotRemoved(this,true);if(outside!=null)outside.remove();if(!movingAway&&controller.proxy==this)controller.runtime().close();super.onChunkUnloaded();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);controller.load(tag,registries);clientRunning=tag.getBoolean("oc:isRunning");if(level!=null&&level.isClientSide)li.cil.oc.client.AgentClient.applyScreen(controller,tag);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);controller.save(tag,registries);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();controller.writeClient(tag,registries);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void onConnect(Node node){}
    @Override public void onDisconnect(Node node){}
    @Override public void onMessage(Message message){if(message.name().equals("network.message")&&message.data().length==1&&message.data()[0] instanceof Packet packet&&controller.node()!=null)controller.node().sendToReachable("network.message",packet);}
    @Callback public Object[] start(Context context,Arguments args){return new Object[]{!machine().isPaused()&&machine().start()};}
    @Callback public Object[] stop(Context context,Arguments args){return new Object[]{machine().stop()};}
    @Callback(direct=true)public Object[] isRunning(Context context,Arguments args){return new Object[]{machine().isRunning()};}
    @Callback public Object[] getName(Context context,Arguments args){return new Object[]{name()};}
    @Callback public Object[] setName(Context context,Arguments args){if(machine().isRunning())return new Object[]{null,"is running"};String old=name();setName(args.checkString(0));return new Object[]{old};}
    @Override public Node[] onAnalyze(Player player,Direction side,float x,float y,float z){return new Node[]{controller.node()};}
    @Override public boolean rotate(Direction axis){if(axis.getAxis()!=Direction.Axis.Y||level==null||level.isClientSide||machine().isRunning())return false;return controller.turn(axis==Direction.UP);}
    @Override public li.cil.oc.server.machine.Machine machine(){return controller.machine();}
    @Override public Iterable<ItemStack> internalComponents(){return controller.internalComponents();}
    @Override public int componentSlot(String address){return controller.componentSlot(address);}
    @Override public void onMachineConnect(Node node){controller.onMachineConnect(node);}
    @Override public void onMachineDisconnect(Node node){controller.onMachineDisconnect(node);}
    @Override public net.minecraft.world.level.Level world(){return level;}
    @Override public double xPosition(){return worldPosition.getX()+.5;}
    @Override public double yPosition(){return worldPosition.getY()+.5;}
    @Override public double zPosition(){return worldPosition.getZ()+.5;}
    @Override public void markChanged(){controller.markChanged();}
    @Override public int tier(){return controller.tier();}
    @Override public Direction facing(){return controller.facing();}
    @Override public Direction toGlobal(Direction side){return controller.toGlobal(side);}
    @Override public Direction toLocal(Direction side){return controller.toLocal(side);}
    @Override public RedstoneState redstoneState(){return controller.redstoneState();}
    @Override public int componentCount(){return controller.componentCount();}
    @Override public Environment getComponentInSlot(int slot){return controller.getComponentInSlot(slot);}
    @Override public void synchronizeSlot(int slot){controller.synchronizeSlot(slot);}
    @Override public boolean shouldAnimate(){return clientRunning;}
    @Override public Container equipmentInventory(){return controller.equipmentInventory();}
    @Override public Container mainInventory(){return controller.mainInventory();}
    @Override public MultiTank tank(){return controller.tank();}
    @Override public int selectedSlot(){return controller.selectedSlot();}
    @Override public void setSelectedSlot(int slot){controller.setSelectedSlot(slot);}
    @Override public int selectedTank(){return controller.selectedTank();}
    @Override public void setSelectedTank(int tank){controller.setSelectedTank(tank);}
    @Override public Player player(){return controller.player();}
    @Override public String name(){return controller.name();}
    @Override public void setName(String name){controller.setName(name);}
    @Override public String ownerName(){return controller.ownerName();}
    @Override public UUID ownerUUID(){return controller.ownerUUID();}
    @Override public int getContainerSize(){return controller.getContainerSize();}
    @Override public boolean isEmpty(){return controller.isEmpty();}
    @Override public ItemStack getItem(int slot){return controller.getItem(slot);}
    @Override public ItemStack removeItem(int slot,int count){return controller.removeItem(slot,count);}
    @Override public ItemStack removeItemNoUpdate(int slot){return controller.removeItemNoUpdate(slot);}
    @Override public void setItem(int slot,ItemStack stack){controller.setItem(slot,stack);}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return controller.canPlaceItem(slot,stack);}
    @Override public boolean stillValid(Player player){return controller.stillValid(player);}
    @Override public void clearContent(){controller.clearContent();}
    @Override public int[] getSlotsForFace(Direction side){return controller.getSlotsForFace(side);}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return controller.canPlaceItemThroughFace(slot,stack,side);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return controller.canTakeItemThroughFace(slot,stack,side);}
    @Override public Component getDisplayName(){return Component.literal(name());}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return stillValid(player)?new AgentMenu(id,inventory,controller):null;}
}
