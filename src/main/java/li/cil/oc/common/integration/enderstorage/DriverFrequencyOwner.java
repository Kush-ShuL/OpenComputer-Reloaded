package li.cil.oc.common.integration.enderstorage;

import java.util.LinkedHashMap;
import java.util.Map;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.prefab.DriverSidedTileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Java migration of the original EnderStorage Adapter driver from OpenComputers
 * commit 258943363ea8fc4ef14948e6bc6f67375061580c. Copyright (c) 2013-2015
 * Florian "Sangar" Nücke and contributors; MIT, see LICENSE.
 */
public final class DriverFrequencyOwner extends DriverSidedTileEntity {
    private final EnderStorageAccess access;

    DriverFrequencyOwner(EnderStorageAccess access) { this.access = access; }

    @Override public Class<?> getTileEntityClass() { return access.tileType; }

    @Override
    public boolean worksWith(Level level, BlockPos position, Direction side) {
        return level != null && !level.isClientSide && level.hasChunkAt(position)
            && access.tileType.isInstance(level.getBlockEntity(position));
    }

    @Override
    public ManagedEnvironment createEnvironment(Level level, BlockPos position, Direction side) {
        return worksWith(level, position, side) ? new Environment(level, position, level.getBlockEntity(position), access) : null;
    }

    public static final class Environment extends AbstractManagedEnvironment implements NamedBlock {
        private final Level level;
        private final BlockPos position;
        private final BlockEntity tile;
        private final EnderStorageAccess access;
        private final String name;

        Environment(Level level, BlockPos position, BlockEntity tile, EnderStorageAccess access) {
            this.level = level;
            this.position = position.immutable();
            this.tile = tile;
            this.access = access;
            name = access.tankType.isInstance(tile) ? "ender_tank" : "ender_chest";
            setNode(Network.newNode(this, Visibility.Network).withComponent(name).create());
        }

        @Override public String preferredName() { return name; }
        @Override public int priority() { return 0; }

        private BlockEntity target() {
            if (tile.isRemoved() || !level.hasChunkAt(position) || level.getBlockEntity(position) != tile) {
                throw new IllegalStateException(name + " is no longer present");
            }
            return tile;
        }

        @Callback(doc = "function():table -- Get the currently set frequency as three color numbers: {left, middle, right}.")
        public Object[] getFrequency(Context context, Arguments args) {
            return new Object[]{access.numbers(access.frequency(target()))};
        }

        @Callback(doc = "function(frequency:number) or function(left:number, middle:number, right:number) -- Set a packed 0-4095 frequency or three 0-15 colors.")
        public Object[] setFrequency(Context context, Arguments args) {
            final int left, middle, right;
            if (args.count() == 1) {
                int frequency = args.checkInteger(0);
                if ((frequency & 0xFFF) != frequency) throw new IllegalArgumentException("invalid frequency");
                left = (frequency >> 8) & 0xF;
                middle = (frequency >> 4) & 0xF;
                right = frequency & 0xF;
            } else {
                left = args.checkInteger(0);
                middle = args.checkInteger(1);
                right = args.checkInteger(2);
                if ((left & 0xF) != left || (middle & 0xF) != middle || (right & 0xF) != right) {
                    throw new IllegalArgumentException("invalid frequency");
                }
            }
            access.setFrequency(target(), left, middle, right);
            return null;
        }

        @Callback(doc = "function():string -- Get the stored owner's display name, its UUID if no name is stored, or 'global'.")
        public Object[] getOwner(Context context, Arguments args) {
            return new Object[]{access.owner(access.frequency(target()))};
        }

        @Callback(doc = "function():table -- Get the current left, middle and right color names.")
        public Object[] getFrequencyColors(Context context, Arguments args) {
            return new Object[]{access.frequencyColours(access.frequency(target()))};
        }

        @Callback(doc = "function():table -- Map zero-based color numbers to color names.")
        public Object[] getColors(Context context, Arguments args) {
            Object[] colours = access.colours();
            Map<Integer, Object> values = new LinkedHashMap<>();
            for (int index = 0; index < colours.length; index++) values.put(index, colours[index]);
            return new Object[]{values};
        }
    }
}
