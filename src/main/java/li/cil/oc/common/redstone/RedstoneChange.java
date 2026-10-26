package li.cil.oc.common.redstone;

import net.minecraft.core.Direction;

/** The original redstone.changed message payload; color -1 denotes vanilla redstone. */
public record RedstoneChange(Direction side, int oldValue, int newValue, int color) {
    public RedstoneChange(Direction side, int oldValue, int newValue) { this(side, oldValue, newValue, -1); }
}
