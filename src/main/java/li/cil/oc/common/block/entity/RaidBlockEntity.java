package li.cil.oc.common.block.entity;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.fs.Label;
import li.cil.oc.api.network.*;
import li.cil.oc.common.block.RaidBlock;
import li.cil.oc.common.item.data.RaidData;
import li.cil.oc.common.menu.RaidMenu;
import li.cil.oc.common.network.RaidNetworking;
import li.cil.oc.server.PacketSender;
import li.cil.oc.server.fs.RaidVolume;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** The original RAID cabinet, exposing one fast filesystem only while all three disks exist. */
public final class RaidBlockEntity extends BlockEntity implements Environment,EnvironmentHost,Container,MenuProvider,Analyzable,li.cil.oc.api.internal.Rotatable,PacketSender.ActivityListener {
    private final ItemStack[] disks={ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
    public final boolean[] presence={false,false,false};
    public final RaidLabel label=new RaidLabel();
    public volatile long lastAccess;
    public boolean playerDropGenerated;
    private Node node;
    private RaidVolume volume;
    private CompoundTag pendingNode,pendingFileSystem;
    private boolean initialized,disposed,loading;
    private final AtomicBoolean dirty=new AtomicBoolean(),syncDirty=new AtomicBoolean();
    public RaidBlockEntity(BlockEntityType<?> type,BlockPos position,BlockState state){super(type,position,state);}
    @Override public Level world(){return level;}
    @Override public double xPosition(){return worldPosition.getX()+.5;}
    @Override public double yPosition(){return worldPosition.getY()+.5;}
    @Override public double zPosition(){return worldPosition.getZ()+.5;}
    @Override public void markChanged(){dirty.set(true);}
    @Override public void setChanged(){if(level instanceof ServerLevel server&&!server.getServer().isSameThread()){dirty.set(true);return;}updatePresence();super.setChanged();}
    @Override public Direction facing(){return getBlockState().getValue(RaidBlock.FACING);}
    @Override public Direction toGlobal(Direction side){if(side.getAxis()==Direction.Axis.Y)return side;for(int index=0;index<facing().get2DDataValue();index++)side=side.getClockWise();return side;}
    @Override public Direction toLocal(Direction side){if(side.getAxis()==Direction.Axis.Y)return side;for(int index=0;index<facing().get2DDataValue();index++)side=side.getCounterClockWise();return side;}
    @Override public Node node(){if(node==null&&level!=null&&!level.isClientSide&&!disposed){node=Network.newNode(this,Visibility.None).create();if(pendingNode!=null){node.load(pendingNode);pendingNode=null;}}return node;}
    public Node filesystemNode(){return volume==null?null:volume.environment().node();}
    public boolean complete(){return presence[0]&&presence[1]&&presence[2];}
    public void joinNetwork(){initialize();if(node()!=null)Network.joinOrCreateNetwork(this);}
    private void initialize(){if(initialized||disposed||level==null||level.isClientSide)return;initialized=true;node();updatePresence();if(complete()){CompoundTag state=pendingFileSystem;String id=state==null?UUID.randomUUID().toString():state.getCompound("node").getString("address");createVolume(id,state);pendingFileSystem=null;}Network.joinOrCreateNetwork(this);}
    private void createVolume(String id,CompoundTag state){if(!complete()||volume!=null)return;volume=RaidVolume.create(this,disks,id,label,state);Network.joinNewNetwork(node());node.connect(volume.environment().node());setChanged();}
    private void destroyVolume(){if(volume==null)return;RaidVolume removed=volume;volume=null;removed.destroy(level.registryAccess());pendingFileSystem=null;}
    private void updatePresence(){for(int slot=0;slot<3;slot++)presence[slot]=!disks[slot].isEmpty();}
    private void inventoryChanged(){updatePresence();if(level!=null&&!level.isClientSide&&!loading){if(initialized&&complete())createVolume(UUID.randomUUID().toString(),null);RaidNetworking.send(this);level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());}setChanged();}
    public void tick(){initialize();if(node()!=null&&node.network()==null)Network.joinOrCreateNetwork(this);if(volume!=null&&volume.environment().canUpdate())volume.environment().update();if(dirty.getAndSet(false))setChanged();if(syncDirty.getAndSet(false))RaidNetworking.send(this);}
    @Override public void onConnect(Node connected){if(connected==node&&volume!=null)node.connect(volume.environment().node());}
    @Override public void onDisconnect(Node disconnected){if(disconnected==node&&volume!=null)volume.environment().node().remove();}
    @Override public void onMessage(Message message){}
    @Override public Node[] onAnalyze(Player player,Direction side,float x,float y,float z){return new Node[]{filesystemNode()};}
    @Override public void onComponentActivity(String address,boolean network){if(!network)lastAccess=System.currentTimeMillis();}
    @Override public void onLoad(){super.onLoad();initialize();}
    private void dispose(){if(disposed)return;if(volume!=null){pendingFileSystem=volume.close(level.registryAccess());volume=null;}if(node!=null){pendingNode=new CompoundTag();node.save(pendingNode);node.remove();}disposed=true;initialized=false;}
    @Override public void setRemoved(){dispose();super.setRemoved();}
    @Override public void onChunkUnloaded(){dispose();super.onChunkUnloaded();}
    public void initialize(ItemStack stack){if(level==null||level.isClientSide)return;RaidData data=new RaidData(stack,level.registryAccess());if(volume!=null){volume.close(level.registryAccess());volume=null;}loading=true;try{Arrays.fill(disks,ItemStack.EMPTY);for(int slot=0;slot<Math.min(3,data.disks.length);slot++)disks[slot]=data.disks[slot].copy();data.label.ifPresent(label::setLabel);pendingFileSystem=data.filesystem.isEmpty()?null:data.filesystem.copy();updatePresence();}finally{loading=false;}if(complete()){String id=pendingFileSystem==null?UUID.randomUUID().toString():pendingFileSystem.getCompound("node").getString("address");createVolume(id,pendingFileSystem);pendingFileSystem=null;}inventoryChanged();}
    public ItemStack dropStack(){if(isEmpty())return new ItemStack(li.cil.oc.common.init.RaidDevices.ITEM.get());RaidData data=new RaidData();data.disks=Arrays.stream(disks).map(ItemStack::copy).toArray(ItemStack[]::new);data.filesystem=volume==null?pendingFileSystem==null?new CompoundTag():pendingFileSystem.copy():volume.save(level.registryAccess(),true);data.label=Optional.ofNullable(label.getLabel());return data.createItemStack(level.registryAccess());}
    @Override public int getContainerSize(){return 3;}
    @Override public int getMaxStackSize(){return 1;}
    @Override public boolean isEmpty(){return Arrays.stream(disks).allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<3?disks[slot]:ItemStack.EMPTY;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){if(slot<0||slot>=3||stack.isEmpty())return false;var driver=Driver.driverFor(stack,getClass());return driver!=null&&li.cil.oc.api.driver.item.Slot.HDD.equals(driver.slot(stack));}
    @Override public ItemStack removeItem(int slot,int count){if(slot<0||slot>=3||count<=0||disks[slot].isEmpty())return ItemStack.EMPTY;if(level!=null&&!level.isClientSide)destroyVolume();ItemStack result=disks[slot].split(count);inventoryChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,1);}
    @Override public void setItem(int slot,ItemStack stack){if(slot<0||slot>=3||(!stack.isEmpty()&&!canPlaceItem(slot,stack)))return;if(volume!=null&&level!=null&&!level.isClientSide)destroyVolume();disks[slot]=stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(1);inventoryChanged();}
    @Override public void clearContent(){if(level!=null&&!level.isClientSide)destroyVolume();Arrays.fill(disks,ItemStack.EMPTY);inventoryChanged();}
    @Override public boolean stillValid(Player player){return level!=null&&player.level()==level&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(xPosition(),yPosition(),zPosition())<=64;}
    @Override public Component getDisplayName(){return Component.translatable("oc:container.raid");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return stillValid(player)?new RaidMenu(id,inventory,this):null;}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(level!=null&&level.isClientSide){applyClient(tag.getByteArray("oc:presence"),tag.contains("oc:label")?tag.getString("oc:label"):null);return;}
        if(volume!=null){volume.close(registries);volume=null;}
        initialized=false;disposed=false;loading=true;
        try{
            Arrays.fill(disks,ItemStack.EMPTY);
            for(Tag value:tag.getList("oc:items",Tag.TAG_COMPOUND)){CompoundTag entry=(CompoundTag)value;int slot=entry.getInt("slot");if(slot>=0&&slot<3)disks[slot]=ItemStack.parseOptional(registries,entry.getCompound("item"));}
            pendingNode=tag.contains("oc:node")?tag.getCompound("oc:node").copy():null;
            if(node!=null&&pendingNode!=null){node.load(pendingNode);pendingNode=null;}
            pendingFileSystem=tag.contains("oc:fs")?tag.getCompound("oc:fs").copy():null;label.load(tag);updatePresence();
        }finally{loading=false;}
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);ListTag items=new ListTag();for(int slot=0;slot<3;slot++)if(!disks[slot].isEmpty()){CompoundTag entry=new CompoundTag();entry.putInt("slot",slot);entry.put("item",disks[slot].save(registries));items.add(entry);}tag.put("oc:items",items);CompoundTag network=new CompoundTag();if(node!=null)node.save(network);else if(pendingNode!=null)network=pendingNode.copy();tag.put("oc:node",network);if(volume!=null)tag.put("oc:fs",volume.save(registries,false));else if(pendingFileSystem!=null)tag.put("oc:fs",pendingFileSystem.copy());label.save(tag);}
    public byte[] presenceData(){byte[] result=new byte[3];for(int slot=0;slot<3;slot++)result[slot]=(byte)(presence[slot]?1:0);return result;}
    public void applyClient(byte[] values,String name){for(int slot=0;slot<3;slot++)presence[slot]=slot<values.length&&values[slot]!=0;label.value=name;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();tag.putByteArray("oc:presence",presenceData());label.save(tag);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    public final class RaidLabel implements Label {
        private volatile String value="raid";
        @Override public String getLabel(){return value;}
        @Override public void setLabel(String value){this.value=value==null?null:value.substring(0,Math.min(16,value.length()));markChanged();syncDirty.set(true);}
        @Override public void load(CompoundTag tag){if(tag.contains("oc:label")){String saved=tag.getString("oc:label");value=saved.substring(0,Math.min(16,saved.length()));}}
        @Override public void save(CompoundTag tag){if(value==null)tag.remove("oc:label");else tag.putString("oc:label",value);}
    }
}
