package li.cil.oc.common.block;

import java.util.function.Supplier;
import li.cil.oc.common.block.entity.RelayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class RelayBlock extends Block implements EntityBlock {
    private final Supplier<BlockEntityType<RelayBlockEntity>> type;
    public RelayBlock(Properties properties,Supplier<BlockEntityType<RelayBlockEntity>> type){super(properties);this.type=type;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return type.get().create(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> actual){return !level.isClientSide&&actual==type.get()?(world,pos,value,entity)->((RelayBlockEntity)entity).serverTick():null;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(!(level.getBlockEntity(pos) instanceof RelayBlockEntity relay))return InteractionResult.PASS;if(!level.isClientSide)player.openMenu(relay,buffer->buffer.writeBlockPos(pos));return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos from,boolean moving){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof RelayBlockEntity relay)relay.neighborChanged();}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&level.getBlockEntity(pos) instanceof RelayBlockEntity relay){Containers.dropContents(level,pos,relay);relay.detach();}super.onRemove(state,level,pos,next,moving);}
}
