package li.cil.oc.server.driver.block;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** Exact furnace timings, including smoker and blast-furnace subclasses. */
public final class Furnace extends VanillaBlockDriver<AbstractFurnaceBlockEntity> {
    public Furnace() { super(AbstractFurnaceBlockEntity.class, Blocks.FURNACE, Blocks.SMOKER, Blocks.BLAST_FURNACE); }
    @Override protected ManagedEnvironment create(Level level, BlockPos pos, Direction side, AbstractFurnaceBlockEntity tile) { return new Environment(level, pos, side, tile); }
    @Override protected Class<?> environmentType() { return Environment.class; }
    public static final class Environment extends VanillaBlockEnvironment<AbstractFurnaceBlockEntity> {
        Environment(Level level, BlockPos pos, Direction side, AbstractFurnaceBlockEntity tile) { super(level, pos, side, "furnace", tile); }
        @Callback(doc = "function():number -- The number of ticks that the furnace will keep burning from the last consumed fuel.") public Object[] getBurnTime(Context context, Arguments args) { return new Object[]{target().litTime}; }
        @Callback(doc = "function():number -- The number of ticks that the currently burning fuel lasts in total.") public Object[] getCurrentItemBurnTime(Context context, Arguments args) { return new Object[]{target().litDuration}; }
        @Callback(doc = "function():number -- The number of ticks that the current item has been cooking for.") public Object[] getCookTime(Context context, Arguments args) { return new Object[]{target().cookingProgress}; }
        @Callback(doc = "function():number -- The number of ticks that the current item needs to cook.") public Object[] getTotalCookTime(Context context, Arguments args) { return new Object[]{target().cookingTotalTime}; }
        @Callback(doc = "function():boolean -- Get whether the furnace is currently active.") public Object[] isBurning(Context context, Arguments args) { return new Object[]{target().litTime > 0}; }
    }
}
