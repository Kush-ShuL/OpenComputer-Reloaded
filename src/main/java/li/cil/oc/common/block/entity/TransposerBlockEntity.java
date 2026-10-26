package li.cil.oc.common.block.entity;

import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.common.network.AutomationNetworking;
import li.cil.oc.server.component.Transposer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** A powered OC transposer with the original one-second transfer activity indicator. */
public final class TransposerBlockEntity extends AutomationBlockEntity implements Transposer.ActivityHost {
    public volatile long lastOperation;
    public TransposerBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override protected ManagedEnvironment createEnvironment(){return new Transposer(this);}
    @Override public void onTransposerActivity(){lastOperation=System.currentTimeMillis();AutomationNetworking.sendTransposerActivity(this);}
}
