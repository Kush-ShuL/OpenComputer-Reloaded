package li.cil.oc.common.block;

import java.util.List;
import li.cil.oc.common.block.entity.RaidBlockEntity;
import li.cil.oc.common.init.RaidDevices;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

/** Breaking the cabinet preserves the complete array instead of ejecting and formatting its disks. */
public final class RaidBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public RaidBlock(Properties properties){super(properties.isValidSpawn((state,level,pos,entity)->false));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new RaidBlockEntity(RaidDevices.TYPE.get(),pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T>getTicker(Level level,BlockState state,BlockEntityType<T> type){return !level.isClientSide&&type==RaidDevices.TYPE.get()?(world,pos,value,entity)->((RaidBlockEntity)entity).tick():null;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(!(level.getBlockEntity(pos) instanceof RaidBlockEntity raid)||!raid.stillValid(player))return InteractionResult.FAIL;if(!level.isClientSide)player.openMenu(raid,buffer->buffer.writeBlockPos(pos));return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos){return level.getBlockEntity(pos) instanceof RaidBlockEntity raid&&raid.complete()?15:0;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide&&level.getBlockEntity(pos) instanceof RaidBlockEntity raid)raid.initialize(stack);}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RaidBlockEntity raid&&!raid.playerDropGenerated?List.of(raid.dropStack()):List.of();}
    @Override public boolean onDestroyedByPlayer(BlockState state,Level level,BlockPos pos,Player player,boolean harvest,net.minecraft.world.level.material.FluidState fluid){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof RaidBlockEntity raid){popResource(level,pos,raid.dropStack());raid.playerDropGenerated=true;}return super.onDestroyedByPlayer(state,level,pos,player,harvest,fluid);}
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state){return level.getBlockEntity(pos) instanceof RaidBlockEntity raid?raid.dropStack():new ItemStack(this);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos neighbor,boolean moving){super.neighborChanged(state,level,pos,block,neighbor,moving);if(!level.isClientSide&&level.getBlockEntity(pos) instanceof RaidBlockEntity raid)raid.joinNetwork();}
}
