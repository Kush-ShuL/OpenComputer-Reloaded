package li.cil.oc.util;

import net.minecraft.core.Direction;

/** The upstream ForgeDirection matrix and twelve-orientation pitch/yaw transition. */
public final class RotationHelper {
    private static final int[][] ROTATION_MATRIX={
        {0,1,4,5,3,2}, {0,1,5,4,2,3}, {5,4,2,3,0,1},
        {4,5,2,3,1,0}, {2,3,1,0,4,5}, {3,2,0,1,4,5}
    };
    private RotationHelper() {}
    public record PitchYaw(Direction pitch,Direction yaw) {
        public Direction facing(){return pitch.getAxis().isVertical()?pitch:yaw;}
    }
    public static Direction rotate(Direction facing,Direction axis) {
        return Direction.from3DDataValue(ROTATION_MATRIX[axis.get3DDataValue()][facing.get3DDataValue()]);
    }
    public static PitchYaw rotate(Direction pitch,Direction yaw,Direction axis) {
        Direction facing=rotate(pitch.getAxis().isVertical()?pitch:yaw,axis);
        return facing.getAxis().isVertical()
                ?new PitchYaw(facing,facing==pitch?rotate(yaw,axis):yaw)
                :new PitchYaw(Direction.NORTH,facing);
    }
}
