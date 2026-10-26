package li.cil.oc.common.block;

import com.mojang.serialization.MapCodec;
import li.cil.oc.common.block.entity.NetworkPowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Supplier;

/** Full-cube power devices retain the original block models and expose their real environments. */
public abstract class PowerDeviceBlock extends BaseEntityBlock {
    private final Supplier<? extends BlockEntityType<? extends NetworkPowerBlockEntity>> type;
    protected PowerDeviceBlock(Properties properties, Supplier<? extends BlockEntityType<? extends NetworkPowerBlockEntity>> type) { super(properties); this.type = type; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return type.get().create(position, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> actualType) {
        return !level.isClientSide && actualType == type.get() ? (world, position, blockState, entity) -> ((NetworkPowerBlockEntity) entity).serverTick() : null;
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos position, Block neighbor, BlockPos from, boolean moving) {
        if (!level.isClientSide && level.getBlockEntity(position) instanceof NetworkPowerBlockEntity entity) li.cil.oc.api.Network.joinOrCreateNetwork(entity);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moving) {
        if (state.getBlock() != replacement.getBlock() && level.getBlockEntity(position) instanceof NetworkPowerBlockEntity entity) entity.onBlockDestroyed();
        super.onRemove(state, level, position, replacement, moving);
    }
}
