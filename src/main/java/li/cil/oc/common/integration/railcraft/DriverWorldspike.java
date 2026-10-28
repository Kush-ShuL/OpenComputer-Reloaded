package li.cil.oc.common.integration.railcraft;

import li.cil.oc.api.Network;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.prefab.DriverSidedTileEntity;
import mods.railcraft.world.level.block.entity.worldspike.WorldSpikeBlockEntity;
import mods.railcraft.world.level.block.worldspike.PersonalWorldSpikeBlock;
import mods.railcraft.world.level.block.worldspike.WorldSpikeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** OC's original five Worldspike callbacks (258943363ea8fc4ef14948e6bc6f67375061580c, MIT). */
public final class DriverWorldspike extends DriverSidedTileEntity {
    @Override public Class<?> getTileEntityClass() { return WorldSpikeBlockEntity.class; }

    @Override public ManagedEnvironment createEnvironment(Level level, BlockPos position, Direction side) {
        return new Environment((WorldSpikeBlockEntity) level.getBlockEntity(position));
    }

    public static final class Environment extends AbstractManagedEnvironment implements NamedBlock {
        private final WorldSpikeBlockEntity tile;

        Environment(WorldSpikeBlockEntity tile) {
            this.tile = tile;
            setNode(Network.newNode(this, Visibility.Network).withComponent("worldspike").create());
        }

        @Override public String preferredName() { return "worldspike"; }
        @Override public int priority() { return 5; }

        @Callback(doc = "function():int -- Get the amount of fuel.")
        public Object[] getFuel(Context context, Arguments args) {
            // Official Reborn Worldspikes have no fuel store or fuel consumption.
            return new Object[]{0};
        }

        @Callback(doc = "function():string -- Get the anchor owner name.")
        public Object[] getOwner(Context context, Arguments args) {
            String name = tile.getOwner().map(owner -> owner.getName()).orElse(null);
            return name == null || name.equals("[unknown]") ? new Object[0] : new Object[]{name};
        }

        @Callback(doc = "function():string -- Get the anchor type.")
        public Object[] getType(Context context, Arguments args) {
            var block = tile.getBlockState().getBlock();
            return new Object[]{block instanceof PersonalWorldSpikeBlock ? "personal" : block instanceof WorldSpikeBlock ? "world" : "missing"};
        }

        @Callback(doc = "function():table -- Get the anchor fuel slot's contents.")
        public Object[] getFuelSlotContents(Context context, Arguments args) {
            // This is the original needsFuel=false branch, not a fabricated slot.
            return new Object[0];
        }

        @Callback(doc = "function():boolean -- If the anchor is disabled (powered by redstone).")
        public Object[] isDisabled(Context context, Arguments args) {
            // Official Reborn places its tickets independently of redstone input.
            return new Object[]{false};
        }
    }
}
