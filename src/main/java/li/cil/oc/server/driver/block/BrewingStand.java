package li.cil.oc.server.driver.block;

import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

public final class BrewingStand extends VanillaBlockDriver<BrewingStandBlockEntity> {
    public BrewingStand(){super(BrewingStandBlockEntity.class,Blocks.BREWING_STAND);}
    @Override protected ManagedEnvironment create(Level level,BlockPos pos,Direction side,BrewingStandBlockEntity tile){return new Environment(level,pos,side,tile);}
    @Override protected Class<?> environmentType(){return Environment.class;}
    public static final class Environment extends VanillaBlockEnvironment<BrewingStandBlockEntity> {
        Environment(Level level,BlockPos pos,Direction side,BrewingStandBlockEntity tile){super(level,pos,side,"brewing_stand",tile);}
        @Callback(doc="function():number -- Get the number of ticks remaining of the current brewing operation.") public Object[] getBrewTime(Context context,Arguments args){return new Object[]{target().brewTime};}
    }
}
