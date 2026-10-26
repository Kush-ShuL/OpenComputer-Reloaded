package li.cil.oc.server.driver.block;

import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.*;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;

public final class RecordPlayer extends VanillaBlockDriver<JukeboxBlockEntity> {
    public RecordPlayer(){super(JukeboxBlockEntity.class,Blocks.JUKEBOX);}
    @Override protected ManagedEnvironment create(Level level,BlockPos pos,Direction side,JukeboxBlockEntity tile){return new Environment(level,pos,side,tile);}
    @Override protected Class<?> environmentType(){return Environment.class;}
    public static final class Environment extends VanillaBlockEnvironment<JukeboxBlockEntity> {
        Environment(Level level,BlockPos pos,Direction side,JukeboxBlockEntity tile){super(level,pos,side,"jukebox",tile);}
        @Callback(doc="function():string -- Get the title of the record currently in the jukebox.") public Object[] getRecord(Context context,Arguments args){if(!permitted())return denied();return JukeboxSong.fromStack(level.registryAccess(),target().getTheItem()).map(song->new Object[]{song.value().description().getString()}).orElseGet(()->new Object[0]);}
        @Callback(doc="function() -- Start playing the record currently in the jukebox.") public Object[] play(Context context,Arguments args){if(!permitted())return denied();var tile=target();var song=JukeboxSong.fromStack(level.registryAccess(),tile.getTheItem());if(song.isEmpty())return new Object[0];tile.getSongPlayer().play(level,song.get());tile.setChanged();return new Object[]{true};}
        @Callback(doc="function() -- Stop playing the record currently in the jukebox.") public Object[] stop(Context context,Arguments args){if(!permitted())return denied();var tile=target();tile.getSongPlayer().stop(level,tile.getBlockState());tile.setChanged();return new Object[0];}
    }
}
