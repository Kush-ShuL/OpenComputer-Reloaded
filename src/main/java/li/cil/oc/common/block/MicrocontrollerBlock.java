package li.cil.oc.common.block;

import java.util.List;
import li.cil.oc.common.block.entity.MicrocontrollerBlockEntity;
import li.cil.oc.common.init.PortableDevices;
import li.cil.oc.common.menu.MicrocontrollerMenu;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
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

public final class MicrocontrollerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty RUNNING=BooleanProperty.create("running"),ERROR=BooleanProperty.create("error");
    public MicrocontrollerBlock(Properties properties){super(properties.isValidSpawn((state,level,pos,entity)->false));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH).setValue(RUNNING,false).setValue(ERROR,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,RUNNING,ERROR);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new MicrocontrollerBlockEntity(PortableDevices.MICROCONTROLLER_TYPE.get(),pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T>getTicker(Level level,BlockState state,BlockEntityType<T> type){return !level.isClientSide&&type==PortableDevices.MICROCONTROLLER_TYPE.get()?(world,pos,value,entity)->((MicrocontrollerBlockEntity)entity).tick():null;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(!(level.getBlockEntity(pos) instanceof MicrocontrollerBlockEntity host)||!host.canInteract(player))return InteractionResult.FAIL;if(!level.isClientSide){if(player.isShiftKeyDown())player.openMenu(new SimpleMenuProvider((id,inventory,viewer)->new MicrocontrollerMenu(id,inventory,host),Component.translatable("block.opencomputers.microcontroller")),buffer->buffer.writeBlockPos(pos));else host.toggle(player);}return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(player.isShiftKeyDown()&&MicrocontrollerBlockEntity.isEEPROM(stack)&&level.getBlockEntity(pos) instanceof MicrocontrollerBlockEntity host){if(!host.canInteract(player))return ItemInteractionResult.FAIL;if(!level.isClientSide){ItemStack old=host.changeEEPROM(stack.copyWithCount(1));stack.shrink(1);if(!old.isEmpty()&&!player.getInventory().add(old))player.drop(old,false);}return ItemInteractionResult.sidedSuccess(level.isClientSide);}return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide&&placer instanceof Player player&&level.getBlockEntity(pos) instanceof MicrocontrollerBlockEntity host)host.initialize(stack,player);}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MicrocontrollerBlockEntity host?List.of(host.dropStack()):List.of();}
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state){return level.getBlockEntity(pos) instanceof MicrocontrollerBlockEntity host?host.info.copyItemStack(level.registryAccess()):ItemStack.EMPTY;}
    @Override public boolean onDestroyedByPlayer(BlockState state,Level level,BlockPos pos,Player player,boolean harvest,net.minecraft.world.level.material.FluidState fluid){if(level.getBlockEntity(pos) instanceof MicrocontrollerBlockEntity host&&!host.canInteract(player))return false;return super.onDestroyedByPlayer(state,level,pos,player,harvest,fluid);}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return level.getBlockEntity(pos) instanceof MicrocontrollerBlockEntity host?Math.max(0,host.redstoneState().output(side.getOpposite())):0;}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return getSignal(state,level,pos,side);}
    @Override public boolean canConnectRedstone(BlockState state,BlockGetter level,BlockPos pos,Direction side){return level.getBlockEntity(pos) instanceof MicrocontrollerBlockEntity host&&host.redstoneState().enabled();}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){if(level.getBlockEntity(pos) instanceof MicrocontrollerBlockEntity host){host.redstoneState().rescan();host.onConnect(null);}super.neighborChanged(state,level,pos,block,from,moving);}
}
