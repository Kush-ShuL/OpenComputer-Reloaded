package li.cil.oc.common.block;

import li.cil.oc.common.block.entity.CarpetedCapacitorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.function.Supplier;

public final class CarpetedCapacitorBlock extends CapacitorBlock {
    public CarpetedCapacitorBlock(Properties properties, Supplier<BlockEntityType<CarpetedCapacitorBlockEntity>> type) { super(properties, type); }
}
