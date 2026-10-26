package li.cil.oc.common.block;

import java.util.function.Supplier;
import li.cil.oc.common.integration.WrenchTools;
import li.cil.oc.common.block.entity.AdapterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class AdapterBlock extends Block implements EntityBlock {
    private final Supplier<BlockEntityType<AdapterBlockEntity>> type;
    public AdapterBlock(Properties properties, Supplier<BlockEntityType<AdapterBlockEntity>> type) { super(properties); this.type = type; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return type.get().create(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> requested) { return !level.isClientSide && requested == type.get() ? (world, pos, blockState, entity) -> AdapterBlockEntity.tick((AdapterBlockEntity) entity) : null; }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (WrenchTools.holdsApplicableWrench(player, pos) && level.getBlockEntity(pos) instanceof AdapterBlockEntity adapter) {
            if (!level.isClientSide) { Direction side = player.isShiftKeyDown() ? hit.getDirection().getOpposite() : hit.getDirection(); adapter.setSideOpen(side, !adapter.isSideOpen(side)); WrenchTools.wrenchUsed(player, pos); }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AdapterBlockEntity adapter) || !adapter.stillValid(player)) return InteractionResult.PASS;
        if (!level.isClientSide) player.openMenu(adapter, buffer -> buffer.writeBlockPos(pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos changed, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, changed, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AdapterBlockEntity adapter) adapter.refreshNeighbors();
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && level.getBlockEntity(pos) instanceof AdapterBlockEntity adapter) { adapter.flushUpgrade(); Containers.dropContents(level, pos, adapter); }
        super.onRemove(state, level, pos, next, moving);
    }
}
