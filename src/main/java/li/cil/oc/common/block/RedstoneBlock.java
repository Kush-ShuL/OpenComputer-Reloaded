package li.cil.oc.common.block;

import java.util.function.Supplier;
import li.cil.oc.common.block.entity.RedstoneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class RedstoneBlock extends Block implements EntityBlock {
    private final Supplier<BlockEntityType<RedstoneBlockEntity>> type;
    public RedstoneBlock(Properties properties, Supplier<BlockEntityType<RedstoneBlockEntity>> type) { super(properties); this.type = type; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return type.get().create(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> requested) {
        return !level.isClientSide && requested == type.get() ? (world, pos, value, entity) -> ((RedstoneBlockEntity) entity).tick() : null;
    }
    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof RedstoneBlockEntity entity ? Math.max(0, entity.redstoneState().output(side.getOpposite())) : 0;
    }
    @Override protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) { return getSignal(state, level, pos, side); }
    @Override public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof RedstoneBlockEntity entity && entity.redstoneState().enabled();
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (level.getBlockEntity(pos) instanceof RedstoneBlockEntity entity) entity.neighborChanged();
        super.neighborChanged(state, level, pos, block, from, moving);
    }
}
