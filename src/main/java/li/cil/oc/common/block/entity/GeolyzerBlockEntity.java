package li.cil.oc.common.block.entity;

import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.server.component.Geolyzer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The fixed-world-direction terrain analyzer and its persisted component address. */
public final class GeolyzerBlockEntity extends AutomationBlockEntity {
    public GeolyzerBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override protected ManagedEnvironment createEnvironment(){return new Geolyzer(this);}
}
