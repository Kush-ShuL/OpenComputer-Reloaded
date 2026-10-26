package li.cil.oc.common.block;

import li.cil.oc.common.block.entity.GeolyzerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.function.Supplier;

public final class GeolyzerBlock extends AutomationDeviceBlock {
    public GeolyzerBlock(Properties properties,Supplier<BlockEntityType<GeolyzerBlockEntity>> type){super(properties,type);}
}
