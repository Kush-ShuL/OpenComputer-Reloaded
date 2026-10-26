package li.cil.oc.common.block;

import java.util.function.Supplier;
import li.cil.oc.common.block.entity.RackBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RackBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    private final Supplier<BlockEntityType<RackBlockEntity>> type;
    private static final VoxelShape[] INTERACTION=new VoxelShape[6];
    static{VoxelShape[] walls={Block.box(0,0,0,16,1,16),Block.box(0,15,0,16,16,16),Block.box(0,0,0,16,16,1),Block.box(0,0,15,16,16,16),Block.box(0,0,0,1,16,16),Block.box(15,0,0,16,16,16)};for(Direction front:Direction.values()){VoxelShape shape=Block.box(.5,.5,.5,15.5,15.5,15.5);for(Direction side:Direction.values())if(side!=front)shape=Shapes.or(shape,walls[side.ordinal()]);INTERACTION[front.ordinal()]=shape;}}
    public RackBlock(Properties properties,Supplier<BlockEntityType<RackBlockEntity>> type){super(properties);this.type=type;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return type.get().create(pos,state);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return INTERACTION[state.getValue(FACING).ordinal()];}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.block();}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> requested){return !level.isClientSide&&requested==type.get()?(world,pos,value,entity)->((RackBlockEntity)entity).tick():null;}
    @Override protected ItemInteractionResult useItemOn(ItemStack held,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        InteractionResult result=interact(level,pos,player,hand,hit);
        return result.consumesAction()?ItemInteractionResult.sidedSuccess(level.isClientSide):ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return interact(level,pos,player,InteractionHand.MAIN_HAND,hit);}
    private static InteractionResult interact(Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!(level.getBlockEntity(pos) instanceof RackBlockEntity rack)||!rack.stillValid(player))return InteractionResult.PASS;
        if(!level.isClientSide){
            var vector=hit.getLocation().subtract(pos.getX(),pos.getY(),pos.getZ());int slot=rack.slotAt(hit.getDirection(),vector.y);var mountable=rack.getMountable(slot);
            if(mountable!=null){double angle=switch(hit.getDirection()){case WEST->Math.PI/2;case NORTH->Math.PI;case EAST->Math.PI*1.5;default->0;};double x=((int)(vector.x*16))/16d-.5+1/32d,z=((int)(vector.z*16))/16d-.5+1/32d;double local=x*Math.cos(angle)-z*Math.sin(angle)+.5-1/32d;int pixelX=(int)(local*16.05);if(hit.getDirection().getAxis()!=Direction.Axis.Z)pixelX=15-pixelX;pixelX--;int pixelY=15-(int)(vector.y*16)-2-3*slot;
                if(pixelX>=0&&pixelX<14&&pixelY>=0&&pixelY<3&&mountable.onActivate(player,hand,player.getItemInHand(hand),pixelX/14f,pixelY/3f))return InteractionResult.CONSUME;
            }
            player.openMenu(rack,buffer->buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&level.getBlockEntity(pos) instanceof RackBlockEntity rack){rack.flushMountables();for(int slot=0;slot<rack.getContainerSize();slot++)if(rack.getMountable(slot) instanceof li.cil.oc.server.component.DiskDriveMountable disk)disk.dispose(false);Containers.dropContents(level,pos,rack);}super.onRemove(state,level,pos,next,moving);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){if(level.getBlockEntity(pos) instanceof RackBlockEntity rack)rack.neighborChanged();super.neighborChanged(state,level,pos,block,from,moving);}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int getSignal(BlockState state,BlockGetter world,BlockPos pos,Direction side){return world.getBlockEntity(pos) instanceof RackBlockEntity rack?Math.max(0,rack.redstoneState().output(side.getOpposite())):0;}
    @Override protected int getDirectSignal(BlockState state,BlockGetter world,BlockPos pos,Direction side){return getSignal(state,world,pos,side);}
    @Override public boolean canConnectRedstone(BlockState state,BlockGetter world,BlockPos pos,Direction side){return world.getBlockEntity(pos) instanceof RackBlockEntity rack&&rack.redstoneState().enabled();}
}
