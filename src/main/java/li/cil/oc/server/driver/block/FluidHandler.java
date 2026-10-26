package li.cil.oc.server.driver.block;

import java.util.LinkedHashMap;
import java.util.Map;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Modern replacement for the original FluidHandler/FluidTank inspection APIs. */
public final class FluidHandler implements DriverBlock {
    @Override public boolean worksWith(Level world, BlockPos pos, Direction side) { return world.hasChunkAt(pos) && world.getCapability(Capabilities.FluidHandler.BLOCK, pos, side) != null; }
    @Override public ManagedEnvironment createEnvironment(Level world, BlockPos pos, Direction side) { return !world.isClientSide && worksWith(world, pos, side) ? new Environment(world, pos, side) : null; }
    public static final class Environment extends BlockEnvironment {
        Environment(Level level, BlockPos pos, Direction side) { super(level, pos, side, "fluid_handler"); }
        private Object[] tanks() {
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, position, side);
            if (handler == null) return new Object[]{null, "no tank"};
            Object[] tanks = new Object[handler.getTanks()];
            for (int i = 0; i < tanks.length; i++) { Map<String, Object> info = new LinkedHashMap<>(); var fluid = handler.getFluidInTank(i); info.put("capacity", handler.getTankCapacity(i)); info.put("amount", fluid.getAmount()); if (!fluid.isEmpty()) { info.put("name", BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()); info.put("label", fluid.getHoverName().getString()); info.put("hasTag", !fluid.getComponentsPatch().isEmpty()); } tanks[i] = info; }
            return new Object[]{tanks};
        }
        @Callback(doc = "function():table -- Get information about every accessible tank.") public Object[] getTankInfo(Context context, Arguments args) { return tanks(); }
        @Callback(doc = "function():table -- Get information about this fluid tank.") public Object[] getInfo(Context context, Arguments args) { return tanks(); }
    }
}
