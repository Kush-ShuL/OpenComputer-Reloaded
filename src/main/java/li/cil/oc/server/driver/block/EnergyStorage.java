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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class EnergyStorage implements DriverBlock {
    @Override public boolean worksWith(Level world, BlockPos pos, Direction side) { return world.hasChunkAt(pos) && world.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) != null; }
    @Override public ManagedEnvironment createEnvironment(Level world, BlockPos pos, Direction side) { return !world.isClientSide && worksWith(world, pos, side) ? new Environment(world, pos, side) : null; }
    public static final class Environment extends BlockEnvironment implements NamedBlock {
        Environment(Level level, BlockPos pos, Direction side) { super(level, pos, side, "energy_device"); }
        private IEnergyStorage storage() { IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, position, side); if (storage == null) throw new IllegalStateException("energy storage is no longer present"); return storage; }
        @Override public String preferredName() { return "energy_device"; }
        @Override public int priority() { return 0; }
        @Callback(doc = "function():number -- Get the energy stored on this side.") public Object[] getEnergyStored(Context context, Arguments args) { return new Object[]{storage().getEnergyStored()}; }
        @Callback(doc = "function():number -- Get the energy capacity on this side.") public Object[] getMaxEnergyStored(Context context, Arguments args) { return new Object[]{storage().getMaxEnergyStored()}; }
        @Callback(doc = "function():boolean -- Whether energy may be extracted on this side.") public Object[] canExtract(Context context, Arguments args) { return new Object[]{storage().canExtract()}; }
        @Callback(doc = "function():boolean -- Whether energy may be received on this side.") public Object[] canReceive(Context context, Arguments args) { return new Object[]{storage().canReceive()}; }
    }
}
