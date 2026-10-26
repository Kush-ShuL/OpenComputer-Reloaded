package li.cil.oc.common.block;

import com.mojang.serialization.MapCodec;
import li.cil.oc.common.block.entity.ScreenBlockEntity;
import li.cil.oc.common.network.ScreenNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import java.util.function.Supplier;

/** The original pitch/yaw scheme keeps all twelve wall/floor/ceiling orientations. */
public class ScreenBlock extends BaseEntityBlock {
    public static final DirectionProperty PITCH = DirectionProperty.create("pitch", Direction.NORTH, Direction.UP, Direction.DOWN);
    public static final DirectionProperty YAW = DirectionProperty.create("yaw", Direction.Plane.HORIZONTAL);
    public final int tier;
    private final Supplier<BlockEntityType<ScreenBlockEntity>> type;
    public ScreenBlock(int tier, Properties properties, Supplier<BlockEntityType<ScreenBlockEntity>> type) {
        super(properties); this.tier = tier; this.type = type;
        registerDefaultState(stateDefinition.any().setValue(PITCH, Direction.NORTH).setValue(YAW, Direction.NORTH));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(PITCH, YAW); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction nearest = context.getNearestLookingDirection().getOpposite();
        return defaultBlockState().setValue(PITCH, nearest.getAxis().isVertical() ? nearest : Direction.NORTH)
                .setValue(YAW, nearest.getAxis().isHorizontal() ? nearest : context.getHorizontalDirection().getOpposite());
    }
    public static Direction facing(BlockState state) { return state.getValue(PITCH) == Direction.NORTH ? state.getValue(YAW) : state.getValue(PITCH); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(YAW, rotation.rotate(state.getValue(YAW))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(YAW))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return type.get().create(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> actualType) {
        return actualType == type.get() ? (world, pos, blockState, entity) -> ((ScreenBlockEntity) entity).tick() : null;
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (level.getBlockEntity(pos) instanceof ScreenBlockEntity screen) { screen.invalidateGroup(); if (!level.isClientSide) li.cil.oc.api.Network.joinOrCreateNetwork(screen); }
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ScreenBlockEntity screen) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (player.isShiftKeyDown() == screen.origin().invertTouchMode()) ScreenNetworking.open(serverPlayer, screen);
                else screen.touch(hit.getLocation(), player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof DyeItem dye && level.getBlockEntity(pos) instanceof ScreenBlockEntity screen) {
            if (!level.isClientSide) { screen.setColor(dye.getDyeColor().getTextureDiffuseColor()); if (!player.getAbilities().instabuild) stack.shrink(1); }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide && projectile instanceof AbstractArrow && hit.getDirection() == facing(state) && level.getBlockEntity(hit.getBlockPos()) instanceof ScreenBlockEntity screen)
            screen.touch(hit.getLocation(), null);
    }
    @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (!level.isClientSide && facing(state) == Direction.UP && level.getBlockEntity(pos) instanceof ScreenBlockEntity screen) screen.walk(entity);
    }
}
