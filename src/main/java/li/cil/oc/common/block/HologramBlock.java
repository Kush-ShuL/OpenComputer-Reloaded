package li.cil.oc.common.block;

import com.mojang.serialization.MapCodec;
import li.cil.oc.common.block.entity.HologramBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.function.Supplier;

public final class HologramBlock extends BaseEntityBlock {
    private static final VoxelShape BOUNDS = Block.box(0, 0, 0, 16, 8, 16);
    public final int tier;
    private final Supplier<BlockEntityType<HologramBlockEntity>> type;
    public HologramBlock(int tier, Properties properties, Supplier<BlockEntityType<HologramBlockEntity>> type) { super(properties.isValidSpawn((state,level,pos,entity)->false)); this.tier = tier; this.type = type; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return BOUNDS; }
    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return type.get().create(position, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> actual) { return actual == type.get() ? (world, position, blockState, entity) -> ((HologramBlockEntity) entity).tick() : null; }
    @Override public void setPlacedBy(Level level, BlockPos position, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (!level.isClientSide && placer != null && level.getBlockEntity(position) instanceof HologramBlockEntity hologram) {
            // Original ItemBlock applies the default horizontal validFacings and
            // then invertRotation, so looking up/down does not tilt a placed projector.
            hologram.setOrientation(Direction.NORTH, Direction.fromYRot(placer.getYRot()).getOpposite());
        }
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos position, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide && level.getBlockEntity(position) instanceof HologramBlockEntity hologram) li.cil.oc.api.Network.joinOrCreateNetwork(hologram);
    }
}
