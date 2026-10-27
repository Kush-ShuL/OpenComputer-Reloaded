package li.cil.oc.server.driver.block;

import li.cil.oc.api.driver.*;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Modern note blocks keep their note in a block state instead of a block entity. */
public final class NoteBlock implements DriverBlock,EnvironmentProvider {
    @Override public boolean worksWith(Level level,BlockPos pos,Direction side){return li.cil.oc.common.integration.VanillaDrivers.integrationEnabled("minecraft")&&level.hasChunkAt(pos)&&level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.NoteBlock;}
    @Override public ManagedEnvironment createEnvironment(Level level,BlockPos pos,Direction side){return !level.isClientSide&&worksWith(level,pos,side)?new Environment(level,pos,side):null;}
    @Override public Class<?> getEnvironment(ItemStack stack){return stack.getItem() instanceof BlockItem item&&item.getBlock()==Blocks.NOTE_BLOCK?Environment.class:null;}
    public static final class Environment extends BlockEnvironment implements NamedBlock {
        Environment(Level level,BlockPos pos,Direction side){super(level,pos,side,"note_block");}
        private BlockState state(){BlockState state=level.getBlockState(position);if(!(state.getBlock() instanceof net.minecraft.world.level.block.NoteBlock))throw new IllegalStateException("note block is no longer present");return state;}
        @Override public String preferredName(){return "note_block";}
        @Override public int priority(){return 0;}
        @Callback(direct=true,doc="function():number -- Get the currently set pitch on this note block.") public Object[] getPitch(Context context,Arguments args){return new Object[]{state().getValue(net.minecraft.world.level.block.NoteBlock.NOTE)+1};}
        private void pitch(int value){if(value<1||value>25)throw new IllegalArgumentException("invalid pitch");level.setBlock(position,state().setValue(net.minecraft.world.level.block.NoteBlock.NOTE,value-1),3);}
        @Callback(doc="function(value:number) -- Set the pitch for this note block. Must be in the interval [1, 25].") public Object[] setPitch(Context context,Arguments args){pitch(args.checkInteger(0));return new Object[]{true};}
        @Callback(doc="function([pitch:number]):boolean -- Triggers the note block if possible. Allows setting the pitch for to save a tick.") public Object[] trigger(Context context,Arguments args){if(args.count()>0&&args.checkAny(0)!=null)pitch(args.checkInteger(0));BlockState state=state();boolean canTrigger=level.getBlockState(position.above()).isAir();if(canTrigger)((net.minecraft.world.level.block.NoteBlock)state.getBlock()).playNote(null,state,level,position);return new Object[]{canTrigger};}
    }
}
