package li.cil.oc.common.block.entity;

import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.server.component.MotionSensor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Server-ticked living-entity motion sensor, including persistent sensitivity. */
public final class MotionSensorBlockEntity extends AutomationBlockEntity {
    public MotionSensorBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override protected ManagedEnvironment createEnvironment(){return new MotionSensor(this);}
}
