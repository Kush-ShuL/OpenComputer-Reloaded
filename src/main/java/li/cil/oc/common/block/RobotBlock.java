package li.cil.oc.common.block;

import li.cil.oc.common.block.entity.RobotBlockEntity;
import li.cil.oc.common.init.Agents;
import li.cil.oc.server.agent.RobotController;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import java.util.List;

/** Movable proxy block; the controller/VM is transferred, never serialized for a move. */
public final class RobotBlock extends Block implements EntityBlock {
    public static final ThreadLocal<RobotController> MOVING=new ThreadLocal<>();
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public RobotBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new RobotBlockEntity(Agents.ROBOT_TYPE.get(),pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T>getTicker(Level level,BlockState state,BlockEntityType<T>type){return type==Agents.ROBOT_TYPE.get()?(world,pos,value,entity)->((RobotBlockEntity)entity).tick():null;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.ENTITYBLOCK_ANIMATED;}
    static VoxelShape shape(RobotController controller){VoxelShape shape=box(1.6,1.6,1.6,14.4,14.4,14.4);if(controller.moveFrom!=null&&controller.animationTicks>0&&controller.animationTotal>0){double remaining=(double)controller.animationTicks/controller.animationTotal;BlockPos offset=controller.moveFrom.subtract(controller.blockPosition());shape=shape.move(offset.getX()*remaining,offset.getY()*remaining,offset.getZ()*remaining);}return shape;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return level.getBlockEntity(pos) instanceof RobotBlockEntity robot?shape(robot.controller()):box(1.6,1.6,1.6,14.4,14.4,14.4);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!(level.getBlockEntity(pos) instanceof RobotBlockEntity robot)||!robot.stillValid(player))return InteractionResult.FAIL;
        if(player.isShiftKeyDown()&&!player.getMainHandItem().isEmpty())return InteractionResult.PASS;
        if(!level.isClientSide){robot.controller().runtime().initialize();if(player.isShiftKeyDown()){if(!robot.machine().isRunning())robot.machine().start();}else player.openMenu(robot,buffer->li.cil.oc.common.menu.AgentMenu.writeOpen(robot.controller(),buffer));}
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack){super.setPlacedBy(level,pos,state,entity,stack);if(!level.isClientSide&&entity instanceof Player player&&level.getBlockEntity(pos) instanceof RobotBlockEntity robot){ItemStack placed=player.getAbilities().instabuild?new li.cil.oc.common.item.data.RobotData(stack,level.registryAccess()).copyItemStack(level.registryAccess()):stack;robot.controller().initialize(placed,player);}}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){var entity=params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);if(entity instanceof RobotBlockEntity robot&&!robot.movingAway&&!robot.playerDropGenerated){robot.controller().prepareDrop();return List.of(robot.controller().dropStack());}return List.of();}
    @Override public boolean onDestroyedByPlayer(BlockState state,Level level,BlockPos pos,Player player,boolean harvest,net.minecraft.world.level.material.FluidState fluid){
        if(level.getBlockEntity(pos) instanceof RobotBlockEntity robot){RobotController host=robot.controller();if(player instanceof li.cil.oc.server.agent.AgentPlayer agent&&agent.agent()==host)return false;if(host.tier()==3&&(!player.getAbilities().instabuild||!host.stillValid(player)))return false;if(!level.isClientSide){host.prepareDrop();popResource(level,pos,host.dropStack());robot.playerDropGenerated=true;}if(host.moveFrom!=null&&level.getBlockState(host.moveFrom).is(Agents.AFTERIMAGE.get()))level.removeBlock(host.moveFrom,false);}
        return level.removeBlock(pos,false);
    }
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state){return level.getBlockEntity(pos) instanceof RobotBlockEntity robot?robot.controller().info.copyItemStack(level.registryAccess()):ItemStack.EMPTY;}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&level.getBlockEntity(pos) instanceof RobotBlockEntity robot&&!robot.movingAway&&MOVING.get()!=robot.controller()){
        robot.controller().runtime().close();Containers.dropContents(level,pos,robot.controller().equipment);Containers.dropContents(level,pos,robot.controller().inventory);
    }super.onRemove(state,level,pos,next,moving);}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return level.getBlockEntity(pos) instanceof RobotBlockEntity robot?Math.max(0,robot.redstoneState().output(side.getOpposite())):0;}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return getSignal(state,level,pos,side);}
    @Override public boolean canConnectRedstone(BlockState state,BlockGetter level,BlockPos pos,Direction side){return level.getBlockEntity(pos) instanceof RobotBlockEntity robot&&robot.redstoneState().enabled();}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){if(level.getBlockEntity(pos) instanceof RobotBlockEntity robot)robot.redstoneState().rescan();super.neighborChanged(state,level,pos,block,from,moving);}
}
