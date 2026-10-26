package li.cil.oc.api.prefab;

import li.cil.oc.api.driver.DriverBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * If you wish to create a block component for a third-party block, i.e. a block
 * for which you do not control the tile entity, such as vanilla blocks, you
 * will need a block driver.
 * <br>
 * This prefab allows creating a driver that works for a specified list of item
 * stacks. To support only particular block states, override worksWith(BlockState).
 * <br>
 * You still have to provide the implementation for creating its environment, if
 * any.
 * <br>
 * To limit sidedness, I recommend overriding {@link #worksWith(Level, BlockPos, Direction)}
 * and calling <code>super.worksWith</code> in addition to the side check.
 *
 * @see li.cil.oc.api.network.ManagedEnvironment
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class DriverSidedBlock implements DriverBlock {
    protected final ItemStack[] blocks;

    protected DriverSidedBlock(final ItemStack... blocks) {
        this.blocks = blocks.clone();
    }

    @Override
    public boolean worksWith(final Level world, final BlockPos pos, final Direction side) {
        return worksWith(world.getBlockState(pos));
    }

    /**
     * Metadata variants are separate blocks or block states in modern Minecraft.
     * Override this overload when a driver only supports particular properties.
     */
    protected boolean worksWith(final BlockState state) {
        return worksWith(state.getBlock());
    }

    protected boolean worksWith(final Block referenceBlock) {
        for (ItemStack stack : blocks) {
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem item &&
                    referenceBlock == item.getBlock()) {
                return true;
            }
        }
        return false;
    }
}
