package li.cil.oc.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Temporary invisible occupancy left by the original proxy move animation. */
public final class RobotAfterimageBlock extends Block {
    public RobotAfterimageBlock(Properties properties){super(properties);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    private li.cil.oc.common.block.entity.RobotBlockEntity findRobot(BlockGetter level,BlockPos pos){for(Direction side:Direction.values())if(level.getBlockEntity(pos.relative(side)) instanceof li.cil.oc.common.block.entity.RobotBlockEntity robot&&pos.equals(robot.controller().moveFrom))return robot;return null;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){var robot=findRobot(level,pos);if(robot==null)return Shapes.empty();BlockPos offset=robot.getBlockPos().subtract(pos);return RobotBlock.shape(robot.controller()).move(offset.getX(),offset.getY(),offset.getZ());}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){var robot=findRobot(level,pos);return robot!=null?((RobotBlock)robot.getBlockState().getBlock()).useWithoutItem(robot.getBlockState(),level,robot.getBlockPos(),player,hit):InteractionResult.PASS;}
    @Override public boolean onDestroyedByPlayer(BlockState state,Level level,BlockPos pos,Player player,boolean harvest,net.minecraft.world.level.material.FluidState fluid){var robot=findRobot(level,pos);return robot!=null?robot.getBlockState().getBlock().onDestroyedByPlayer(robot.getBlockState(),level,robot.getBlockPos(),player,false,level.getFluidState(robot.getBlockPos())):level.removeBlock(pos,false);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){level.removeBlock(pos,false);}
}
