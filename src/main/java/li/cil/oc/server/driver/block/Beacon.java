package li.cil.oc.server.driver.block;

import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.*;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;

public final class Beacon extends VanillaBlockDriver<BeaconBlockEntity> {
    public Beacon(){super(BeaconBlockEntity.class,Blocks.BEACON);}
    @Override protected ManagedEnvironment create(Level level,BlockPos pos,Direction side,BeaconBlockEntity tile){return new Environment(level,pos,side,tile);}
    @Override protected Class<?> environmentType(){return Environment.class;}
    public static final class Environment extends VanillaBlockEnvironment<BeaconBlockEntity> {
        Environment(Level level,BlockPos pos,Direction side,BeaconBlockEntity tile){super(level,pos,side,"beacon",tile);}
        private String effect(int index){var effect=BeaconMenu.decodeEffect(target().dataAccess.get(index));return effect==null?null:effect.value().getDescriptionId();}
        @Callback(doc="function():number -- Get the number of levels for this beacon.") public Object[] getLevels(Context context,Arguments args){return permitted()?new Object[]{target().dataAccess.get(0)}:denied();}
        @Callback(doc="function():string -- Get the name of the active primary effect.") public Object[] getPrimaryEffect(Context context,Arguments args){return permitted()?new Object[]{effect(1)}:denied();}
        @Callback(doc="function():string -- Get the name of the active secondary effect.") public Object[] getSecondaryEffect(Context context,Arguments args){return permitted()?new Object[]{effect(2)}:denied();}
    }
}
