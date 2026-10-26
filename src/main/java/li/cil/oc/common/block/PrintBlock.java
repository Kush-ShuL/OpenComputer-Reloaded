package li.cil.oc.common.block;

import java.util.List;
import java.util.function.Supplier;
import li.cil.oc.Settings;
import li.cil.oc.common.block.entity.PrintBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Data-driven printed geometry; beacon-base membership is handled by the narrow printing mixin. */
public final class PrintBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final Supplier<BlockEntityType<PrintBlockEntity>> type;
    public PrintBlock(Properties properties, Supplier<BlockEntityType<PrintBlockEntity>> type) { super(properties); this.type = type; registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return type.get().create(pos, state); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity user, ItemStack stack) { super.setPlacedBy(level, pos, state, user, stack); if (level.getBlockEntity(pos) instanceof PrintBlockEntity print) print.setData(stack); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return level.getBlockEntity(pos) instanceof PrintBlockEntity print ? print.shape() : Shapes.block(); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return level.getBlockEntity(pos) instanceof PrintBlockEntity print ? print.collision() : Shapes.block(); }
    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return level.getBlockEntity(pos) instanceof PrintBlockEntity print ? print.support() : Shapes.empty(); }
    @Override public boolean hasDynamicLightEmission(BlockState state) { return true; }
    @Override public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) { return level.getBlockEntity(pos) instanceof PrintBlockEntity print ? print.data.lightLevel : 0; }
    @Override protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return level.getBlockEntity(pos) instanceof PrintBlockEntity print && Settings.get().printsHaveOpacity ? (int) (print.data.opacity() * 4) : 1; }
    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) { return level.getBlockEntity(pos) instanceof PrintBlockEntity print ? print.output() : 0; }
    @Override protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) { return getSignal(state, level, pos, side); }
    @Override public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction side) { return true; }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block source, BlockPos from, boolean moving) { if (level.getBlockEntity(pos) instanceof PrintBlockEntity print) print.neighborChanged(); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) { return level.getBlockEntity(pos) instanceof PrintBlockEntity print && print.activate() ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS; }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { if (level.getBlockEntity(pos) instanceof PrintBlockEntity print && print.active) { print.toggle(); if (print.active) level.scheduleTick(pos, this, 20); } }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder loot) { return loot.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof PrintBlockEntity print ? List.of(print.stack(loot.getLevel().registryAccess())) : List.of(); }
    @Override public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) { return level.getBlockEntity(pos) instanceof PrintBlockEntity print ? print.stack(level.registryAccess()) : ItemStack.EMPTY; }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && level.getBlockEntity(pos) instanceof PrintBlockEntity print && print.output() > 0) {
            level.updateNeighborsAt(pos, this); for (Direction side : Direction.values()) level.updateNeighborsAt(pos.relative(side), this);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
