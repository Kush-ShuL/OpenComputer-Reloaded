package li.cil.oc.common.block.entity;

import li.cil.oc.api.network.*;
import li.cil.oc.api.internal.Colored;
import li.cil.oc.common.block.CableBlock;
import li.cil.oc.common.capabilities.Capabilities;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** A colored, non-component network junction with the original wildcard gray. */
public final class CableBlockEntity extends BlockEntity implements Environment,Colored {
    private Node node;
    private int color=0xABABAB;
    private CompoundTag savedNode;
    public CableBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override public Node node(){
        if(node==null&&level!=null&&!level.isClientSide){node=li.cil.oc.api.Network.newNode(this,Visibility.None).create();if(savedNode!=null){node.load(savedNode);savedNode=null;}}
        return node;
    }
    @Override public void onConnect(Node node){}
    @Override public void onDisconnect(Node node){}
    @Override public void onMessage(Message message){}
    @Override public int getColor(){return color;}
    @Override public void setColor(int value){if(value==color)return;color=value;setChanged();if(level!=null&&!level.isClientSide){level.invalidateCapabilities(worldPosition);rebuildConnections();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}}
    @Override public boolean controlsConnectivity(){return true;}
    public void rebuildConnections(){
        if(level==null||level.isClientSide||isRemoved())return;li.cil.oc.api.Network.joinOrCreateNetwork(this);BlockState state=getBlockState(),next=state;
        for(Direction side:Direction.values()){
            BlockPos pos=worldPosition.relative(side);boolean connected=false;
            if(level.hasChunkAt(pos)){
                var neighbor=level.getBlockEntity(pos);Node other=li.cil.oc.server.network.Network.getNetworkNode(neighbor,side.getOpposite());
                Colored colored=level.getCapability(Capabilities.ColoredCapability,pos,side.getOpposite());
                connected=other!=null&&(colored==null||!colored.controlsConnectivity()||color==0xABABAB||colored.getColor()==0xABABAB||colored.getColor()==color);
            }
            next=next.setValue(CableBlock.CONNECTIONS[side.get3DDataValue()],connected).setValue(CableBlock.CABLE_NEIGHBORS[side.get3DDataValue()],connected&&level.getBlockEntity(pos) instanceof CableBlockEntity);
        }
        if(!next.equals(state))level.setBlock(worldPosition,next,3);
    }
    @Override public void onLoad(){super.onLoad();rebuildConnections();}
    @Override public void setRemoved(){if(node!=null)node.remove();super.setRemoved();}
    @Override public void onChunkUnloaded(){if(node!=null)node.remove();super.onChunkUnloaded();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);color=tag.contains("oc:color")?tag.getInt("oc:color"):0xABABAB;savedNode=tag.contains("oc:node")?tag.getCompound("oc:node"):null;if(node!=null&&savedNode!=null){node.load(savedNode);savedNode=null;}}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putInt("oc:color",color);CompoundTag n=new CompoundTag();if(node()!=null)node().save(n);else if(savedNode!=null)n=savedNode.copy();tag.put("oc:node",n);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();tag.putInt("oc:color",color);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
