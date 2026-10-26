package li.cil.oc.server.driver.block;

import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** Exact furnace timings, including smoker and blast-furnace subclasses. */
public final class Furnace implements DriverBlock {
    @Override public boolean worksWith(Level world, BlockPos pos, Direction side) { return world.hasChunkAt(pos) && world.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity; }
    @Override public ManagedEnvironment createEnvironment(Level world, BlockPos pos, Direction side) { return !world.isClientSide && worksWith(world, pos, side) ? new Environment(world, pos, side) : null; }
    public static final class Environment extends BlockEnvironment implements NamedBlock {
        Environment(Level level, BlockPos pos, Direction side) { super(level, pos, side, "furnace"); }
        private AbstractFurnaceBlockEntity furnace() { if (level.getBlockEntity(position) instanceof AbstractFurnaceBlockEntity furnace) return furnace; throw new IllegalStateException("furnace is no longer present"); }
        @Override public String preferredName() { return "furnace"; }
        @Override public int priority() { return 0; }
        @Callback(doc = "function():number -- The remaining burn time, in ticks.") public Object[] getBurnTime(Context context, Arguments args) { return new Object[]{furnace().litTime}; }
        @Callback(doc = "function():number -- The total burn time of the currently consumed fuel.") public Object[] getCurrentItemBurnTime(Context context, Arguments args) { return new Object[]{furnace().litDuration}; }
        @Callback(doc = "function():number -- The current item's cooking progress, in ticks.") public Object[] getCookTime(Context context, Arguments args) { return new Object[]{furnace().cookingProgress}; }
        @Callback(doc = "function():number -- The current item's required cooking time, in ticks.") public Object[] getTotalCookTime(Context context, Arguments args) { return new Object[]{furnace().cookingTotalTime}; }
        @Callback(doc = "function():boolean -- Whether the furnace is currently burning.") public Object[] isBurning(Context context, Arguments args) { return new Object[]{furnace().litTime > 0}; }
    }
}
