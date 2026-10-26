package li.cil.oc.common.block;

import li.cil.oc.common.block.entity.PowerDistributorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.function.Supplier;

public final class PowerDistributorBlock extends PowerDeviceBlock {
    public PowerDistributorBlock(Properties properties, Supplier<BlockEntityType<PowerDistributorBlockEntity>> type) { super(properties, type); }
}
