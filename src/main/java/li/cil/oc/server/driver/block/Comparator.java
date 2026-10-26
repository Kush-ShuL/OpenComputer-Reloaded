package li.cil.oc.server.driver.block;

import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;

public final class Comparator extends VanillaBlockDriver<ComparatorBlockEntity> {
    public Comparator(){super(ComparatorBlockEntity.class,Blocks.COMPARATOR);}
    @Override protected ManagedEnvironment create(Level level,BlockPos pos,Direction side,ComparatorBlockEntity tile){return new Environment(level,pos,side,tile);}
    @Override protected Class<?> environmentType(){return Environment.class;}
    public static final class Environment extends VanillaBlockEnvironment<ComparatorBlockEntity> {
        Environment(Level level,BlockPos pos,Direction side,ComparatorBlockEntity tile){super(level,pos,side,"comparator",tile);}
        @Callback(doc="function():number -- Get the strength of the comparators output signal.") public Object[] getOutputSignal(Context context,Arguments args){return permitted()?new Object[]{target().getOutputSignal()}:denied();}
    }
}
