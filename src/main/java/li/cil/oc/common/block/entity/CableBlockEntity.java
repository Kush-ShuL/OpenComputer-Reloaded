package li.cil.oc.common.block.entity;

import li.cil.oc.api.network.*;
import li.cil.oc.api.internal.Colored;
import li.cil.oc.common.block.CableBlock;
import li.cil.oc.common.capabilities.Capabilities;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** A colored, non-component network junction with the original wildcard gray. */
public final class CableBlockEntity extends BlockEntity implements Environment,Colored {
    private static final int DEFAULT_COLOR=0xABABAB;
    private static final int[] LEGACY_COLORS={0xF0F0F0,0xEB8844,0xC354CD,0xAAAAFF,0xFFFF66,0x66FF66,0xD88198,0x666666,0xABABAB,0x66FFFF,0x7B2FBE,0x6666FF,0x51301A,0x339911,0xB3312C,0x444444};
    private Node node;
    private int color=DEFAULT_COLOR;
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
    public static int colorForDye(DyeColor dye){return LEGACY_COLORS[dye.getId()];}
    public void fromItemStack(ItemStack stack){
        CompoundTag data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(data.contains("oc:color"))setColor(data.getInt("oc:color"));
        else if(data.getCompound("display").contains("color"))setColor(data.getCompound("display").getInt("color"));
    }
    public ItemStack createItemStack(){
        ItemStack stack=li.cil.oc.api.Items.get("cable").createItemStack(1);
        if(color!=DEFAULT_COLOR)CustomData.update(DataComponents.CUSTOM_DATA,stack,data->data.putInt("oc:color",color));
        return stack;
    }
    public void rebuildConnections(){
        if(level==null||level.isClientSide||isRemoved())return;li.cil.oc.api.Network.joinOrCreateNetwork(this);BlockState state=getBlockState(),next=state;
        for(Direction side:Direction.values()){
            BlockPos pos=worldPosition.relative(side);boolean connected=false;
            if(level.hasChunkAt(pos)){
                var neighbor=level.getBlockEntity(pos);Node other=li.cil.oc.server.network.Network.getNetworkNode(neighbor,side.getOpposite());
                Colored colored=level.getCapability(Capabilities.ColoredCapability,pos,null);
                connected=other!=null&&!(neighbor instanceof li.cil.oc.api.internal.Robot)
                        &&(!(neighbor instanceof li.cil.oc.server.network.Network.SideConnectivity geometry)||geometry.isNetworkSideOpen(side.getOpposite()))
                        &&(colored==null||!colored.controlsConnectivity()||color==DEFAULT_COLOR||colored.getColor()==DEFAULT_COLOR||colored.getColor()==color);
            }
            next=next.setValue(CableBlock.CONNECTIONS[side.get3DDataValue()],connected).setValue(CableBlock.CABLE_NEIGHBORS[side.get3DDataValue()],connected&&level.getBlockEntity(pos) instanceof CableBlockEntity);
        }
        if(!next.equals(state))level.setBlock(worldPosition,next,3);
    }
    @Override public void onLoad(){super.onLoad();rebuildConnections();}
    @Override public void setRemoved(){if(node!=null)node.remove();super.setRemoved();}
    @Override public void onChunkUnloaded(){if(node!=null)node.remove();super.onChunkUnloaded();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(tag.contains("oc:renderColorRGB"))color=tag.getInt("oc:renderColorRGB");
        else if(tag.contains("oc:renderColor")){int index=tag.getInt("oc:renderColor");color=LEGACY_COLORS[index<0||index>=LEGACY_COLORS.length?0:index];}
        else color=tag.contains("oc:color")?tag.getInt("oc:color"):DEFAULT_COLOR;
        savedNode=tag.contains("oc:node")?tag.getCompound("oc:node"):null;
        if(node!=null&&savedNode!=null){node.load(savedNode);savedNode=null;}
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putInt("oc:renderColorRGB",color);CompoundTag n=new CompoundTag();if(node()!=null)node().save(n);else if(savedNode!=null)n=savedNode.copy();tag.put("oc:node",n);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){CompoundTag tag=new CompoundTag();tag.putInt("oc:renderColorRGB",color);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
