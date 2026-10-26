package li.cil.oc.common.block;

import li.cil.oc.common.block.entity.TransposerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import java.util.function.Supplier;

public final class TransposerBlock extends AutomationDeviceBlock {
    public TransposerBlock(Properties properties,Supplier<BlockEntityType<TransposerBlockEntity>> type){super(properties,type);}
    @Override protected VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
}
