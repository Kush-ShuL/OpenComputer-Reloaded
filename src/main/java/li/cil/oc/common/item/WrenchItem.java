package li.cil.oc.common.item;

import java.util.EnumMap;
import java.util.List;
import li.cil.oc.api.network.Node;
import li.cil.oc.common.block.KeyboardBlock;
import li.cil.oc.common.block.ScreenBlock;
import li.cil.oc.common.block.entity.RotatableBlockEntity;
import li.cil.oc.common.block.entity.ScreenBlockEntity;
import li.cil.oc.util.RotationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** The original undamageable scrench, including axis-specific block rotation. */
public final class WrenchItem extends Item implements li.cil.oc.api.internal.Wrench {
    public WrenchItem(Properties properties) { super(properties.stacksTo(1)); }
    @Override public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) { return true; }
    @Override public boolean useWrenchOnBlock(Player player, Level level, BlockPos pos, boolean simulate) {
        if (!simulate) player.swing(InteractionHand.MAIN_HAND);
        return true;
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer(); Level level = context.getLevel(); BlockPos pos = context.getClickedPos();
        if (player == null || !level.hasChunkAt(pos) || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), stack)) return InteractionResult.PASS;
        BlockEntity entity = level.getBlockEntity(pos);
        if (level.getBlockState(pos).getBlock() instanceof li.cil.oc.common.block.ChargerBlock) return InteractionResult.PASS;
        if (entity instanceof RotatableBlockEntity rotatable) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!rotatable.rotate(context.getClickedFace())) return InteractionResult.PASS;
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            player.swing(context.getHand());
            return InteractionResult.CONSUME;
        }
        BlockState before = level.getBlockState(pos), after = rotatedState(before, context.getClickedFace());
        if (after == before || !after.canSurvive(level, pos)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        EnumMap<Direction, Node> oldNodes = new EnumMap<>(Direction.class), neighbors = new EnumMap<>(Direction.class);
        if (entity != null) for (Direction side : Direction.values()) {
            oldNodes.put(side, li.cil.oc.server.network.Network.getNetworkNode(entity, side));
            BlockPos neighbor = pos.relative(side);
            if (level.hasChunkAt(neighbor)) neighbors.put(side, li.cil.oc.server.network.Network.getNetworkNode(level.getBlockEntity(neighbor), side.getOpposite()));
        }
        if (entity instanceof ScreenBlockEntity screen) screen.invalidateGroup();
        if (!level.setBlock(pos, after, 3)) return InteractionResult.PASS;
        level.invalidateCapabilities(pos);
        if (entity != null) {
            // A newly closed face must not leave its old network edge behind.
            for (Direction side : Direction.values()) {
                Node old = oldNodes.get(side), remote = neighbors.get(side);
                if (old != null && remote != null && old != li.cil.oc.server.network.Network.getNetworkNode(entity, side)) old.disconnect(remote);
            }
            li.cil.oc.api.Network.joinOrCreateNetwork(entity);
        }
        if (entity instanceof ScreenBlockEntity screen) screen.invalidateGroup();
        level.updateNeighborsAt(pos, after.getBlock());
        player.swing(context.getHand());
        return InteractionResult.CONSUME;
    }
    private static BlockState rotatedState(BlockState state, Direction axis) {
        // Upstream keyboards explicitly refuse wrench rotation.
        if (state.getBlock() instanceof KeyboardBlock) return state;
        if (state.getBlock() instanceof ScreenBlock) {
            Direction facing = ScreenBlock.facing(state);
            if (!facing.getAxis().isVertical() && (axis == facing || axis == facing.getOpposite())) return state;
            var orientation = RotationHelper.rotate(state.getValue(ScreenBlock.PITCH), state.getValue(ScreenBlock.YAW), axis);
            return state.setValue(ScreenBlock.PITCH, orientation.pitch()).setValue(ScreenBlock.YAW, orientation.yaw());
        }
        for (var property : state.getProperties()) if (property instanceof DirectionProperty direction && property.getName().equals("facing")) {
            Direction facing = RotationHelper.rotate(state.getValue(direction), axis);
            return direction.getPossibleValues().contains(facing) ? state.setValue(direction, facing) : state;
        }
        return axis.getAxis().isVertical() ? state.rotate(axis == Direction.UP
                ? net.minecraft.world.level.block.Rotation.CLOCKWISE_90 : net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90) : state;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("oc:tooltip.Wrench"));
    }
}
