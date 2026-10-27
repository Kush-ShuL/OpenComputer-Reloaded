package li.cil.oc.common.integration.forestry;

import forestry.api.core.genetics.capability.IIndividualHandlerItem;
import forestry.core.content.analyzer.TileAnalyzer;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.DriverSidedTileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Original Forestry analyzer callbacks, migrated from OC commit 258943363ea8fc4ef14948e6bc6f67375061580c (MIT). */
public final class DriverAnalyzer extends DriverSidedTileEntity {
    @Override public Class<?> getTileEntityClass() { return TileAnalyzer.class; }

    @Override public ManagedEnvironment createEnvironment(Level level, BlockPos position, Direction side) {
        return new Environment((TileAnalyzer) level.getBlockEntity(position));
    }

    public static final class Environment extends ForestryEnvironment<TileAnalyzer> {
        Environment(TileAnalyzer tile) { super(tile, "forestry_analyzer"); }

        @Callback(doc = "function():boolean -- Get whether the analyzer can work.")
        public Object[] isWorking(Context context, Arguments args) { return new Object[]{target().hasWork()}; }

        @Callback(doc = "function():double -- Get the progress of the current operation.")
        public Object[] getProgress(Context context, Arguments args) {
            return new Object[]{1.0 - target().getProgressScaled(100) / 100.0};
        }

        @Callback(doc = "function():table -- Get info on the currently present bee.")
        public Object[] getIndividualOnDisplay(Context context, Arguments args) {
            return new Object[]{IIndividualHandlerItem.getIndividual(target().getIndividualOnDisplay())};
        }
    }
}
