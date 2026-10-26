package li.cil.oc.common.block.entity;

import java.util.concurrent.atomic.AtomicBoolean;
import li.cil.oc.api.Network;
import li.cil.oc.api.network.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** Server-only network lifetime shared by the original network world devices. */
public abstract class NetworkDeviceBlockEntity extends BlockEntity implements Environment,EnvironmentHost {
    private Node node;
    private CompoundTag savedNode;
    protected boolean initialized,detached;
    private final AtomicBoolean dirty=new AtomicBoolean();
    protected NetworkDeviceBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    protected abstract Node createNode();
    protected final boolean server(){return level!=null&&!level.isClientSide;}
    @Override public Node node(){if(node==null&&server()&&!detached&&!isRemoved()){node=createNode();if(node!=null&&savedNode!=null){node.load(savedNode);savedNode=null;}}return node;}
    @Override public Level world(){return level;}
    @Override public double xPosition(){return worldPosition.getX()+.5;}
    @Override public double yPosition(){return worldPosition.getY()+.5;}
    @Override public double zPosition(){return worldPosition.getZ()+.5;}
    @Override public void markChanged(){setChanged();}
    @Override public void setChanged(){if(level instanceof ServerLevel world&&!world.getServer().isSameThread()){dirty.set(true);return;}super.setChanged();}
    protected void onInitialize(){}
    protected void onDetach(){}
    protected final void initialize(){if(!server()||detached||isRemoved())return;if(!initialized){initialized=true;onInitialize();}Node value=node();if(value!=null&&value.network()==null)Network.joinOrCreateNetwork(this);}
    public void serverTick(){initialize();if(dirty.getAndSet(false))super.setChanged();}
    public void neighborChanged(){if(server()&&!detached&&!isRemoved()){initialize();Network.joinOrCreateNetwork(this);}}
    @Override public void onLoad(){super.onLoad();detached=false;initialize();}
    public void detach(){if(detached)return;detached=true;onDetach();if(node!=null)node.remove();initialized=false;}
    @Override public void setRemoved(){detach();super.setRemoved();}
    @Override public void onChunkUnloaded(){detach();super.onChunkUnloaded();}
    @Override public void onConnect(Node node){}
    @Override public void onDisconnect(Node node){}
    @Override public void onMessage(Message message){}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);if(tag.contains("oc:node")){savedNode=tag.getCompound("oc:node").copy();if(node!=null&&server()){node.load(savedNode);savedNode=null;}}}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);CompoundTag data=savedNode==null?new CompoundTag():savedNode.copy();if(node!=null)node.save(data);if(node!=null||savedNode!=null)tag.put("oc:node",data);}
}
