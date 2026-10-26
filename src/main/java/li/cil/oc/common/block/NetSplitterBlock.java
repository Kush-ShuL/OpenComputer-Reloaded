package li.cil.oc.common.block;

import java.util.Arrays;
import java.util.function.Supplier;
import li.cil.oc.common.block.entity.NetSplitterBlockEntity;
import li.cil.oc.common.integration.WrenchTools;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class NetSplitterBlock extends Block implements EntityBlock {
    public static final BooleanProperty[] SIDES=Arrays.stream(Direction.values()).map(side->BooleanProperty.create(side.getName())).toArray(BooleanProperty[]::new);
    private final Supplier<BlockEntityType<NetSplitterBlockEntity>> type;
    public NetSplitterBlock(Properties properties,Supplier<BlockEntityType<NetSplitterBlockEntity>> type){super(properties);this.type=type;BlockState state=stateDefinition.any();for(var side:SIDES)state=state.setValue(side,false);registerDefaultState(state);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(SIDES);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return type.get().create(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> actual){return !level.isClientSide&&actual==type.get()?(world,pos,value,entity)->((NetSplitterBlockEntity)entity).serverTick():null;}
    @Override public VoxelShape getBlockSupportShape(BlockState state,BlockGetter world,BlockPos pos){return Shapes.empty();}
    @Override public boolean canConnectRedstone(BlockState state,BlockGetter world,BlockPos pos,Direction side){return true;}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!WrenchTools.holdsApplicableWrench(player,pos)||!(level.getBlockEntity(pos) instanceof NetSplitterBlockEntity splitter))return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(!level.isClientSide){Direction side=player.isShiftKeyDown()?hit.getDirection().getOpposite():hit.getDirection();splitter.setSideOpen(side,!splitter.rawSideOpen(side));WrenchTools.wrenchUsed(player,pos);}return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos from,boolean moving){if(level.getBlockEntity(pos) instanceof NetSplitterBlockEntity splitter)splitter.neighborChanged();}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&level.getBlockEntity(pos) instanceof NetSplitterBlockEntity splitter)splitter.detach();super.onRemove(state,level,pos,next,moving);}
}
