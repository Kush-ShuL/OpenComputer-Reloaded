package li.cil.oc.server.driver.block;

import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CommandBlockEntity;

/** Registered only when the original enableCommandBlockDriver option is enabled. */
public final class CommandBlock extends VanillaBlockDriver<CommandBlockEntity> {
    public CommandBlock(){super(CommandBlockEntity.class,Blocks.COMMAND_BLOCK,Blocks.CHAIN_COMMAND_BLOCK,Blocks.REPEATING_COMMAND_BLOCK);}
    @Override protected ManagedEnvironment create(Level level,BlockPos pos,Direction side,CommandBlockEntity tile){return new Environment(level,pos,side,tile);}
    @Override protected Class<?> environmentType(){return Environment.class;}
    public static final class Environment extends VanillaBlockEnvironment<CommandBlockEntity> {
        Environment(Level level,BlockPos pos,Direction side,CommandBlockEntity tile){super(level,pos,side,"command_block",tile);}
        @Callback(direct=true,doc="function():string -- Get the command currently set in this command block.") public Object[] getCommand(Context context,Arguments args){return new Object[]{target().getCommandBlock().getCommand()};}
        @Callback(doc="function(value:string) -- Set the specified command for the command block.") public Object[] setCommand(Context context,Arguments args){if(!permitted())return denied();var tile=target();tile.getCommandBlock().setCommand(args.checkString(0));tile.setChanged();level.sendBlockUpdated(position,tile.getBlockState(),tile.getBlockState(),3);return new Object[]{true};}
        @Callback(doc="function():number -- Execute the currently set command. This has a slight delay to allow the command block to properly update.") public Object[] executeCommand(Context context,Arguments args){context.pause(.1);if(!((ServerLevel)level).getServer().isCommandBlockEnabled())return new Object[]{null,"command blocks are disabled"};if(!permitted())return denied();var tile=target();var logic=tile.getCommandBlock();logic.performCommand(level);tile.setChanged();return new Object[]{logic.getSuccessCount(),logic.getLastOutput().getString()};}
    }
}
