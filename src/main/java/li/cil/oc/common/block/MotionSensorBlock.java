package li.cil.oc.common.block;

import li.cil.oc.common.block.entity.MotionSensorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.function.Supplier;

public final class MotionSensorBlock extends AutomationDeviceBlock {
    public MotionSensorBlock(Properties properties,Supplier<BlockEntityType<MotionSensorBlockEntity>> type){super(properties,type);}
}
