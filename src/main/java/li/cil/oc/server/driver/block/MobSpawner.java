package li.cil.oc.server.driver.block;

import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

public final class MobSpawner extends VanillaBlockDriver<SpawnerBlockEntity> {
    public MobSpawner(){super(SpawnerBlockEntity.class,Blocks.SPAWNER);}
    @Override protected ManagedEnvironment create(Level level,BlockPos pos,Direction side,SpawnerBlockEntity tile){return new Environment(level,pos,side,tile);}
    @Override protected Class<?> environmentType(){return Environment.class;}
    public static final class Environment extends VanillaBlockEnvironment<SpawnerBlockEntity> {
        Environment(Level level,BlockPos pos,Direction side,SpawnerBlockEntity tile){super(level,pos,side,"mob_spawner",tile);}
        @Callback(doc="function():string -- Get the name of the entity that is being spawned by this spawner.") public Object[] getSpawningMobName(Context context,Arguments args){String id=target().getSpawner().getOrCreateNextSpawnData(level,level.random,position).getEntityToSpawn().getString("id");return new Object[]{id.isEmpty()?null:id};}
    }
}
