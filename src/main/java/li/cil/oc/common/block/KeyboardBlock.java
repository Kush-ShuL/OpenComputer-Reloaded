package li.cil.oc.common.block;

import com.mojang.serialization.MapCodec;
import li.cil.oc.api.Network;
import li.cil.oc.common.block.entity.KeyboardBlockEntity;
import li.cil.oc.common.block.entity.ScreenBlockEntity;
import li.cil.oc.common.network.ScreenNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.function.Supplier;

public class KeyboardBlock extends BaseEntityBlock {
    private final Supplier<BlockEntityType<KeyboardBlockEntity>> type;
    public KeyboardBlock(Properties properties, Supplier<BlockEntityType<KeyboardBlockEntity>> type) {
        super(properties); this.type = type;
        registerDefaultState(stateDefinition.any().setValue(ScreenBlock.PITCH, Direction.UP).setValue(ScreenBlock.YAW, Direction.NORTH));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(ScreenBlock.PITCH, ScreenBlock.YAW); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState state = defaultBlockState().setValue(ScreenBlock.PITCH, face.getAxis().isVertical() ? face : Direction.NORTH)
                .setValue(ScreenBlock.YAW, face.getAxis().isHorizontal() ? face : context.getHorizontalDirection());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction face = ScreenBlock.facing(state); BlockPos support = pos.relative(face.getOpposite());
        BlockState block = level.getBlockState(support);
        return block.isFaceSturdy(level, support, face) && (!(block.getBlock() instanceof ScreenBlock) || ScreenBlock.facing(block) != face);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction front = ScreenBlock.facing(state), up = front.getAxis().isVertical() ? state.getValue(ScreenBlock.YAW) : Direction.UP;
        Direction right = Direction.fromDelta(up.getStepY() * front.getStepZ() - up.getStepZ() * front.getStepY(),
                up.getStepZ() * front.getStepX() - up.getStepX() * front.getStepZ(), up.getStepX() * front.getStepY() - up.getStepY() * front.getStepX());
        double cx = 0.5 - front.getStepX() * 0.46875, cy = 0.5 - front.getStepY() * 0.46875, cz = 0.5 - front.getStepZ() * 0.46875;
        double dx = Math.abs(up.getStepX()) * 0.25 + Math.abs(right.getStepX()) * 0.4375 + Math.abs(front.getStepX()) * 0.03125;
        double dy = Math.abs(up.getStepY()) * 0.25 + Math.abs(right.getStepY()) * 0.4375 + Math.abs(front.getStepY()) * 0.03125;
        double dz = Math.abs(up.getStepZ()) * 0.25 + Math.abs(right.getStepZ()) * 0.4375 + Math.abs(front.getStepZ()) * 0.03125;
        return Shapes.box(cx - dx, cy - dy, cz - dz, cx + dx, cy + dy, cz + dz);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return type.get().create(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> actual) {
        return !level.isClientSide && actual == type.get() ? (world, pos, blockState, entity) -> ((KeyboardBlockEntity) entity).tick() : null;
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide && !state.canSurvive(level, pos)) { level.destroyBlock(pos, true); return; }
        if (level.getBlockEntity(pos) instanceof KeyboardBlockEntity keyboard && !level.isClientSide) {
            Network.joinOrCreateNetwork(keyboard);
            for (Direction side : Direction.values()) if (level.getBlockEntity(pos.relative(side)) instanceof ScreenBlockEntity screen) screen.invalidateGroup();
        }
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof KeyboardBlockEntity keyboard && keyboard.connectedScreen() != null) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) ScreenNetworking.open(serverPlayer, keyboard.connectedScreen(), pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
