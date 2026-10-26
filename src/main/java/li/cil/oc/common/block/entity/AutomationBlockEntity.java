package li.cil.oc.common.block.entity;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.concurrent.atomic.AtomicBoolean;

/** World lifetime and original root-level component NBT for the three automation devices. */
public abstract class AutomationBlockEntity extends BlockEntity implements Environment,EnvironmentHost {
    private ManagedEnvironment environment;
    private CompoundTag pendingState;
    private boolean detached;
    private final AtomicBoolean pendingSave=new AtomicBoolean();
    protected AutomationBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    protected abstract ManagedEnvironment createEnvironment();
    protected final boolean server(){return level!=null&&!level.isClientSide;}
    public final ManagedEnvironment environment(){
        if(environment==null&&server()&&!detached&&!isRemoved()){
            environment=createEnvironment();
            if(pendingState!=null){environment.load(pendingState,level.registryAccess());pendingState=null;}
        }
        return environment;
    }
    @Override public final Node node(){ManagedEnvironment environment=environment();return environment==null?null:environment.node();}
    @Override public final Level world(){return level;}
    @Override public final double xPosition(){return worldPosition.getX()+.5;}
    @Override public final double yPosition(){return worldPosition.getY()+.5;}
    @Override public final double zPosition(){return worldPosition.getZ()+.5;}
    @Override public final void markChanged(){setChanged();}
    @Override public void setChanged(){
        if(level instanceof ServerLevel world&&!world.getServer().isSameThread()){pendingSave.set(true);return;}
        pendingSave.getAndSet(false);super.setChanged();
    }
    public void serverTick(){
        if(!server()||detached||isRemoved())return;
        if(pendingSave.getAndSet(false))super.setChanged();
        ManagedEnvironment environment=environment();
        if(environment.node()!=null&&environment.node().network()==null)Network.joinOrCreateNetwork(this);
        if(environment.canUpdate())environment.update();
    }
    public void neighborChanged(){if(server()&&!detached&&!isRemoved())Network.joinOrCreateNetwork(this);}
    @Override public final void onConnect(Node node){if(environment!=null)environment.onConnect(node);}
    @Override public final void onDisconnect(Node node){if(environment!=null)environment.onDisconnect(node);}
    @Override public final void onMessage(Message message){if(environment!=null)environment.onMessage(message);}
    @Override public void onLoad(){super.onLoad();detached=false;if(server())neighborChanged();}
    public final void detach(){if(detached)return;detached=true;if(environment!=null&&environment.node()!=null)environment.node().remove();}
    @Override public void setRemoved(){detach();super.setRemoved();}
    @Override public void onChunkUnloaded(){detach();super.onChunkUnloaded();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);pendingState=tag.copy();
        if(environment!=null&&server()){environment.load(pendingState,registries);pendingState=null;}
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);
        if(environment!=null)environment.save(tag,registries);
        else if(pendingState!=null)tag.merge(pendingState.copy());
    }
}
