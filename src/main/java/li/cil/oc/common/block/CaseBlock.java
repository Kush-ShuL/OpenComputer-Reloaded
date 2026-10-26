package li.cil.oc.common.block;

import li.cil.oc.common.block.entity.CaseBlockEntity;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import java.util.function.Supplier;

public final class CaseBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty RUNNING=BooleanProperty.create("running");
    private final Supplier<BlockEntityType<CaseBlockEntity>> type;
    public final int tier;
    public CaseBlock(int tier,Properties properties,Supplier<BlockEntityType<CaseBlockEntity>> type){super(properties);this.tier=tier;this.type=type;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH).setValue(RUNNING,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,RUNNING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return type.get().create(pos,state);}
    @Override @SuppressWarnings("unchecked") public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> requested){return !level.isClientSide&&requested==type.get()?(l,p,s,e)->CaseBlockEntity.tick(l,p,s,(CaseBlockEntity)e):null;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!(level.getBlockEntity(pos) instanceof CaseBlockEntity entity))return InteractionResult.PASS;
        if(!entity.stillValid(player))return InteractionResult.FAIL;
        if(!level.isClientSide){if(player.isShiftKeyDown())entity.machine().start();else player.openMenu(entity,buf->{buf.writeBlockPos(pos);buf.writeVarInt(tier);});}
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&level.getBlockEntity(pos) instanceof CaseBlockEntity entity){entity.flushComponents();Containers.dropContents(level,pos,entity);}super.onRemove(state,level,pos,next,moving);}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return level.getBlockEntity(pos) instanceof CaseBlockEntity host?Math.max(0,host.redstoneState().output(side.getOpposite())):0;}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return getSignal(state,level,pos,side);}
    @Override public boolean canConnectRedstone(BlockState state,BlockGetter level,BlockPos pos,Direction side){return level.getBlockEntity(pos) instanceof CaseBlockEntity host&&host.redstoneState().enabled();}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){if(level.getBlockEntity(pos) instanceof CaseBlockEntity host)host.redstoneState().rescan();super.neighborChanged(state,level,pos,block,from,moving);}
}
