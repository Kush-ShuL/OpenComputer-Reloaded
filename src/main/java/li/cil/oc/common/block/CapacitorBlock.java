package li.cil.oc.common.block;

import li.cil.oc.common.block.entity.CapacitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Supplier;

public class CapacitorBlock extends PowerDeviceBlock {
    public CapacitorBlock(Properties properties, Supplier<? extends BlockEntityType<? extends CapacitorBlockEntity>> type) { super(properties.randomTicks(), type); }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos position) {
        return !level.isClientSide && level.getBlockEntity(position) instanceof CapacitorBlockEntity capacitor ? capacitor.comparatorValue() : 0;
    }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        level.updateNeighbourForOutputSignal(position, this);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos position, Block neighbor, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, position, neighbor, from, moving);
        if (!level.isClientSide && level.getBlockEntity(position) instanceof CapacitorBlockEntity capacitor) capacitor.recomputeCapacity();
    }
}
