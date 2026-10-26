package li.cil.oc.common.block;

import java.util.function.Supplier;
import li.cil.oc.common.block.entity.WaypointBlockEntity;
import li.cil.oc.common.integration.WrenchTools;
import li.cil.oc.common.network.NetworkBlockNetworking;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class WaypointBlock extends Block implements EntityBlock {
    public static final DirectionProperty PITCH=ScreenBlock.PITCH,YAW=ScreenBlock.YAW;
    private final Supplier<BlockEntityType<WaypointBlockEntity>> type;
    public WaypointBlock(Properties properties,Supplier<BlockEntityType<WaypointBlockEntity>> type){super(properties);this.type=type;registerDefaultState(stateDefinition.any().setValue(PITCH,Direction.NORTH).setValue(YAW,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(PITCH,YAW);}
    public static Direction facing(BlockState state){return state.getValue(PITCH)==Direction.NORTH?state.getValue(YAW):state.getValue(PITCH);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){Direction face=context.getNearestLookingDirection().getOpposite();return defaultBlockState().setValue(PITCH,face.getAxis().isVertical()?face:Direction.NORTH).setValue(YAW,face.getAxis().isHorizontal()?face:context.getHorizontalDirection().getOpposite());}
    @Override public BlockState rotate(BlockState state,Rotation rotation){return state.setValue(YAW,rotation.rotate(state.getValue(YAW)));}
    @Override public BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(YAW)));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return type.get().create(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> actual){return actual==type.get()?(world,pos,value,entity)->{if(world.isClientSide)((WaypointBlockEntity)entity).clientTick();else((WaypointBlockEntity)entity).serverTick();}:null;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(player.isShiftKeyDown()||!(level.getBlockEntity(pos) instanceof WaypointBlockEntity waypoint))return InteractionResult.PASS;if(player instanceof ServerPlayer server)NetworkBlockNetworking.openWaypoint(server,waypoint);return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!WrenchTools.holdsApplicableWrench(player,pos))return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(!level.isClientSide){Direction face=facing(state),axis=player.isShiftKeyDown()?hit.getDirection().getOpposite():hit.getDirection();if(axis.getAxis()!=face.getAxis()){Direction rotated=face.getClockWise(axis.getAxis());level.setBlock(pos,state.setValue(PITCH,rotated.getAxis().isVertical()?rotated:Direction.NORTH).setValue(YAW,rotated.getAxis().isHorizontal()?rotated:state.getValue(YAW)),3);WrenchTools.wrenchUsed(player,pos);}}return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos from,boolean moving){if(level.getBlockEntity(pos) instanceof WaypointBlockEntity waypoint)waypoint.neighborChanged();}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&level.getBlockEntity(pos) instanceof WaypointBlockEntity waypoint)waypoint.detach();super.onRemove(state,level,pos,next,moving);}
}
