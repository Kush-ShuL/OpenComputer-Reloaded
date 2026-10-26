package li.cil.oc.common.block.entity;

import java.util.Arrays;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.common.block.WaypointBlock;
import li.cil.oc.common.redstone.*;
import li.cil.oc.server.network.Waypoints;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Named navigation beacon with the original adjacent-facing target and portal particles. */
public final class WaypointBlockEntity extends NetworkDeviceBlockEntity implements RedstoneHost {
    private volatile String label="";
    private final RedstoneState redstone=new RedstoneState(this,change->{});
    public WaypointBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override protected Node createNode(){return Network.newNode(this,Visibility.Network).withComponent("waypoint").create();}
    @Override public Direction facing(){return WaypointBlock.facing(getBlockState());}
    @Override public RedstoneState redstoneState(){return redstone;}
    public String label(){return label;}
    public int maxInput(){return Arrays.stream(redstone.inputs()).max().orElse(0);}
    public void label(String value){label=value.substring(0,Math.min(32,value.length()));setChanged();if(server())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Callback(doc="function():string -- Get this waypoint's label.")
    public Object[] getLabel(Context context,Arguments args){return new Object[]{label};}
    @Callback(doc="function(value:string) -- Set this waypoint's label, up to 32 characters.")
    public Object[] setLabel(Context context,Arguments args){label(args.checkString(0));context.pause(.5);return null;}
    @Override protected void onInitialize(){node();Waypoints.add(this);redstone.rescan();}
    @Override protected void onDetach(){Waypoints.remove(this);}
    @Override public void neighborChanged(){redstone.rescan();super.neighborChanged();}
    @Override public void serverTick(){super.serverTick();redstone.tick();}
    public void clientTick(){
        if(level==null||!level.isClientSide)return;Direction face=facing();var random=level.random;
        double dx=(random.nextFloat()-.5)*.8,dy=(random.nextFloat()-.5)*.8,dz=(random.nextFloat()-.5)*.8;
        level.addParticle(ParticleTypes.PORTAL,xPosition()+face.getStepX()*.5+dx,yPosition()+face.getStepY()*.5+dy,zPosition()+face.getStepZ()*.5+dz,(random.nextFloat()-.5)*.2+face.getStepX()*.3,(random.nextFloat()-.5)*.2+face.getStepY()*.3-.5,(random.nextFloat()-.5)*.2+face.getStepZ()*.3);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);label=tag.getString("oc:label");redstone.load(tag);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putString("oc:label",label);redstone.save(tag);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();tag.putString("oc:label",label);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
