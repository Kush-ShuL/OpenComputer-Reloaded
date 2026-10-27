package li.cil.oc.common.block;

import java.util.function.Supplier;
import li.cil.oc.common.block.entity.ChargerBlockEntity;
import li.cil.oc.common.integration.WrenchTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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

public final class ChargerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final Supplier<BlockEntityType<ChargerBlockEntity>> type;
    public ChargerBlock(Properties properties, Supplier<BlockEntityType<ChargerBlockEntity>> type) { super(properties); this.type = type; registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return type.get().create(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> requested) { return requested == type.get() ? (world, pos, value, entity) -> { if (world.isClientSide) ((ChargerBlockEntity) entity).clientTick(); else ((ChargerBlockEntity) entity).serverTick(); } : null; }
    @Override public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction side) { return true; }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (level.getBlockEntity(pos) instanceof ChargerBlockEntity charger) {
            charger.neighborChanged();
            if (!level.isClientSide) li.cil.oc.api.Network.joinOrCreateNetwork(charger);
        }
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (WrenchTools.holdsApplicableWrench(player, pos) && level.getBlockEntity(pos) instanceof ChargerBlockEntity charger) {
            if (!level.isClientSide) { charger.invert(); WrenchTools.wrenchUsed(player, pos); }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ChargerBlockEntity charger) || !charger.stillValid(player)) return InteractionResult.PASS;
        if (!level.isClientSide) player.openMenu(charger, data -> data.writeBlockPos(pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && level.getBlockEntity(pos) instanceof ChargerBlockEntity charger) { charger.flushItem(); Containers.dropContents(level, pos, charger); charger.onBlockDestroyed(); }
        super.onRemove(state, level, pos, next, moving);
    }
}
