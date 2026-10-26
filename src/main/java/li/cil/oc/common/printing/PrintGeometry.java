package li.cil.oc.common.printing;

import java.util.List;
import li.cil.oc.common.item.data.PrintData;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Immutable model input shared by chunk tessellation and the printed block's collision shape. */
public final class PrintGeometry {
    private PrintGeometry() {}
    public record View(List<PrintData.Shape> shapes, boolean item) { public View { shapes = List.copyOf(shapes); } }
    public static AABB rotate(AABB box, Direction facing) {
        return switch (facing) {
            case EAST -> new AABB(box.minZ, box.minY, 1 - box.maxX, box.maxZ, box.maxY, 1 - box.minX);
            case NORTH -> new AABB(1 - box.maxX, box.minY, 1 - box.maxZ, 1 - box.minX, box.maxY, 1 - box.minZ);
            case WEST -> new AABB(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX);
            default -> box;
        };
    }
    public static View view(PrintData data, boolean active, Direction facing, boolean item) {
        var shapes = active ? data.stateOn : data.stateOff;
        return new View(shapes.stream().map(shape -> new PrintData.Shape(rotate(shape.bounds, facing), shape.texture, shape.tint)).toList(), item);
    }
}
