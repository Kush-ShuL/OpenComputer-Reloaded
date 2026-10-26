package li.cil.oc.common.block;

import java.util.function.Supplier;
import li.cil.oc.common.block.entity.PrinterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

public final class PrinterBlock extends Block implements EntityBlock {
    private final Supplier<BlockEntityType<PrinterBlockEntity>> type;
    private static final VoxelShape BASE = Shapes.box(0, 0, 0, 1, 3 / 16d, 1);
    public PrinterBlock(Properties properties, Supplier<BlockEntityType<PrinterBlockEntity>> type) { super(properties); this.type = type; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return type.get().create(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> requested) { return !level.isClientSide && requested == type.get() ? (world, pos, value, entity) -> ((PrinterBlockEntity) entity).serverTick() : null; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }
    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return BASE; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PrinterBlockEntity printer) || !printer.stillValid(player)) return InteractionResult.PASS;
        if (!level.isClientSide) player.openMenu(printer, data -> data.writeBlockPos(pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos changed, boolean moving) { if (level.getBlockEntity(pos) instanceof PrinterBlockEntity printer) printer.neighborChanged(); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && level.getBlockEntity(pos) instanceof PrinterBlockEntity printer) Containers.dropContents(level, pos, printer);
        super.onRemove(state, level, pos, next, moving);
    }
}
