package li.cil.oc.server.driver.block;

import li.cil.oc.api.driver.NamedBlock;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Captures the real block entity, allowing original direct read callbacks without world lookups. */
abstract class VanillaBlockEnvironment<T extends BlockEntity> extends BlockEnvironment implements NamedBlock {
    private final T tile;private final String name;
    VanillaBlockEnvironment(Level level,BlockPos pos,Direction side,String name,T tile){super(level,pos,side,name);this.tile=tile;this.name=name;}
    protected T target(){if(tile.isRemoved())throw new IllegalStateException(name+" is no longer present");return tile;}
    protected static Object[] denied(){return new Object[]{null,"permission denied"};}
    @Override public final String preferredName(){return name;}
    @Override public final int priority(){return 0;}
}
