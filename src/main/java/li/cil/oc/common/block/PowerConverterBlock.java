package li.cil.oc.common.block;

import li.cil.oc.common.block.entity.PowerConverterBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.function.Supplier;

public final class PowerConverterBlock extends PowerDeviceBlock {
    public PowerConverterBlock(Properties properties, Supplier<BlockEntityType<PowerConverterBlockEntity>> type) { super(properties, type); }
}
