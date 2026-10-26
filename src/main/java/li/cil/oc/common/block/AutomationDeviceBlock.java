package li.cil.oc.common.block;

import com.mojang.serialization.MapCodec;
import li.cil.oc.common.block.entity.AutomationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Supplier;

/** Original non-rotating world devices with block-model rendering and real OC environments. */
public abstract class AutomationDeviceBlock extends BaseEntityBlock {
    private final Supplier<? extends BlockEntityType<? extends AutomationBlockEntity>> type;
    protected AutomationDeviceBlock(Properties properties,Supplier<? extends BlockEntityType<? extends AutomationBlockEntity>> type){super(properties);this.type=type;}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return type.get().create(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> actual){return !level.isClientSide&&actual==type.get()?(world,pos,blockState,entity)->((AutomationBlockEntity)entity).serverTick():null;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof AutomationBlockEntity entity)entity.neighborChanged();}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&level.getBlockEntity(pos) instanceof AutomationBlockEntity entity)entity.detach();super.onRemove(state,level,pos,next,moving);}
}
