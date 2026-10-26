package li.cil.oc.common.block.entity;

import net.minecraft.core.Direction;

/** Original wrench rotation for hosts whose orientation is stored outside block state. */
public interface RotatableBlockEntity {
    boolean rotate(Direction axis);
}
