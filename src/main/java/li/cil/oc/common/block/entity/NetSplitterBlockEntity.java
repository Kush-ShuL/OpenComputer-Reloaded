package li.cil.oc.common.block.entity;

import java.util.*;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.common.block.NetSplitterBlock;
import li.cil.oc.common.redstone.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** One shared component node with six independently switched, redstone-inverted edges. */
public final class NetSplitterBlockEntity extends NetworkDeviceBlockEntity implements SidedEnvironment,RedstoneHost,DeviceInfo {
    private volatile int openSides;
    private volatile boolean inverted;
    private final RedstoneState redstone=new RedstoneState(this,change->{if(change.color()<0)updateInversion(change.newValue()>0);});
    public NetSplitterBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);redstone.setEnabled(true);}
    @Override protected Node createNode(){return Network.newNode(this,Visibility.Network).withComponent("net_splitter",Visibility.Network).create();}
    @Override public RedstoneState redstoneState(){return redstone;}
    public boolean isInverted(){return inverted;}
    public int sideMask(){return inverted?openSides^63:openSides;}
    public boolean rawSideOpen(Direction side){return side!=null&&(openSides&(1<<side.get3DDataValue()))!=0;}
    public boolean isSideOpen(Direction side){return side!=null&&(rawSideOpen(side)^inverted);}
    @Override public Node sidedNode(Direction side){return isSideOpen(side)?node():null;}
    @Override public boolean canConnect(Direction side){return isSideOpen(side);}
    @Override public Map<String,String> getDeviceInfo(){return Map.of("class","network","description","Ethernet controller","vendor","MightyPirates GmbH & Co. KG","product","NetSplits","version","1.0","width","6");}
    public void setSideOpen(Direction side,boolean value){
        if(side==null||rawSideOpen(side)==value)return;
        if(value)openSides|=1<<side.get3DDataValue();else openSides&=~(1<<side.get3DDataValue());
        rebuild(false);
    }
    private void updateInversion(boolean value){if(inverted==value)return;inverted=value;rebuild(true);}
    private void rebuild(boolean inversion){
        setChanged();if(!server()||detached||isRemoved())return;
        Node value=node();if(value!=null)value.remove();Network.joinOrCreateNetwork(this);
        BlockState state=getBlockState(),next=state;
        for(Direction side:Direction.values())next=next.setValue(NetSplitterBlock.SIDES[side.get3DDataValue()],isSideOpen(side));
        level.setBlock(worldPosition,next,2);level.sendBlockUpdated(worldPosition,state,next,3);
        level.playSound(null,worldPosition,inversion?SoundEvents.PISTON_CONTRACT:SoundEvents.PISTON_EXTEND,SoundSource.BLOCKS,.5f,.7f+level.random.nextFloat()*.25f);
        if(!inversion)level.updateNeighborsAt(worldPosition,next.getBlock());
    }
    private Map<Integer,Boolean> currentStatus(){Map<Integer,Boolean> status=new LinkedHashMap<>();for(Direction side:Direction.values())status.put(side.get3DDataValue(),isSideOpen(side));return status;}
    private boolean setSide(Direction side,boolean value){boolean old=isSideOpen(side);setSideOpen(side,value^inverted);return old!=value;}
    @Callback(doc="function(settings:table):table -- Set effective open states indexed by directions 0 through 5; return previous states.")
    public Object[] setSides(Context context,Arguments args){Map<?,?> values=args.checkTable(0);var previous=currentStatus();for(Direction side:Direction.values()){int index=side.get3DDataValue();Object value=values.containsKey(index)?values.get(index):values.get((double)index);setSide(side,Boolean.TRUE.equals(value));}return new Object[]{previous};}
    @Callback(direct=true,doc="function():table -- Get effective open states indexed by direction.")
    public Object[] getSides(Context context,Arguments args){return new Object[]{currentStatus()};}
    private Object[] setSide(Arguments args,boolean value){int index=args.checkInteger(0);return index<0||index>5?new Object[]{null,"invalid direction"}:new Object[]{setSide(Direction.from3DDataValue(index),value)};}
    @Callback(doc="function(side:number):boolean -- Open a side; return whether its state changed.")
    public Object[] open(Context context,Arguments args){return setSide(args,true);}
    @Callback(doc="function(side:number):boolean -- Close a side; return whether its state changed.")
    public Object[] close(Context context,Arguments args){return setSide(args,false);}
    @Override public void neighborChanged(){redstone.rescan();super.neighborChanged();if(server()&&!detached&&!isRemoved())redstone.tick();}
    @Override public void serverTick(){super.serverTick();redstone.setEnabled(true);redstone.tick();}
    @Override protected void onInitialize(){redstone.rescan();redstone.tick();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);if(tag.contains("oc:openSides"))openSides=tag.getByte("oc:openSides")&63;inverted=tag.getBoolean("oc:isInverted");redstone.load(tag);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putByte("oc:openSides",(byte)openSides);tag.putBoolean("oc:isInverted",inverted);redstone.save(tag);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();tag.putByte("oc:openSides",(byte)openSides);tag.putBoolean("oc:isInverted",inverted);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
