package li.cil.oc.common.block;

import java.util.List;
import java.util.function.Supplier;
import li.cil.oc.Settings;
import li.cil.oc.common.block.entity.WorkstationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class WorkstationBlock extends Block implements EntityBlock {
    private final Supplier<? extends BlockEntityType<? extends WorkstationBlockEntity>> type;
    private final boolean assembler;
    private static final VoxelShape ASSEMBLER = Shapes.or(box(0, 0, 0, 16, 7, 16), box(2, 7, 2, 14, 9, 14), box(0, 9, 0, 16, 16, 16));
    public WorkstationBlock(Properties properties, boolean assembler, Supplier<? extends BlockEntityType<? extends WorkstationBlockEntity>> type) { super(properties.isValidSpawn((state, level, pos, entity) -> false)); this.assembler = assembler; this.type = type; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return type.get().create(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> requested) { return !level.isClientSide && requested == type.get() ? (world, pos, value, entity) -> ((WorkstationBlockEntity) entity).tick() : null; }
    @Override protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) { return assembler ? ASSEMBLER : Shapes.block(); }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof li.cil.oc.api.util.StateAware machine)) return 0;
        var states = machine.getCurrentState();
        return states.contains(li.cil.oc.api.util.StateAware.State.IsWorking) ? 15
                : states.contains(li.cil.oc.api.util.StateAware.State.CanWork) ? 10 : 0;
    }
    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        Component text = assembler ? Component.translatable("oc:tooltip.assembler")
                : Component.translatable("oc:tooltip.disassembler", Integer.toString((int) (Settings.get().disassemblerBreakChance * 100)));
        for (String line : text.getString().split("\\[nl\\]")) tooltip.add(Component.literal(line));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof WorkstationBlockEntity entity) || !entity.stillValid(player)) return InteractionResult.PASS;
        if (!level.isClientSide) player.openMenu(entity, data -> { data.writeBlockPos(pos); data.writeBoolean(assembler); });
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) { if (level.getBlockEntity(pos) instanceof WorkstationBlockEntity entity) entity.neighborsChanged(); super.neighborChanged(state, level, pos, block, from, moving); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) { if (state.getBlock() != next.getBlock() && level.getBlockEntity(pos) instanceof WorkstationBlockEntity entity) Containers.dropContents(level, pos, entity); super.onRemove(state, level, pos, next, moving); }
}
