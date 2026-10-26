package li.cil.oc.common.redstone;

import li.cil.oc.api.internal.Rotatable;
import li.cil.oc.api.network.EnvironmentHost;
import net.minecraft.core.Direction;

/** Shared host state for internal redstone cards and the external I/O block. */
public interface RedstoneHost extends EnvironmentHost, Rotatable {
    RedstoneState redstoneState();
    @Override default Direction facing() { return Direction.SOUTH; }
    @Override default Direction toGlobal(Direction side) { return side; }
    @Override default Direction toLocal(Direction side) { return side; }
}
