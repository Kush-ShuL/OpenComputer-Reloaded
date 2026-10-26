package li.cil.oc.server.network;

import java.util.*;
import li.cil.oc.common.block.entity.WaypointBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Loaded waypoints are scoped to the actual server level, never a global server lookup. */
public final class Waypoints {
    private static final Map<Level,Set<WaypointBlockEntity>> LEVELS=new WeakHashMap<>();
    private Waypoints(){}
    public static synchronized void add(WaypointBlockEntity waypoint){Level level=waypoint.getLevel();if(level!=null&&!level.isClientSide&&!waypoint.isRemoved())LEVELS.computeIfAbsent(level,key->Collections.newSetFromMap(new WeakHashMap<>())).add(waypoint);}
    public static synchronized void remove(WaypointBlockEntity waypoint){var set=LEVELS.get(waypoint.getLevel());if(set!=null){set.remove(waypoint);if(set.isEmpty())LEVELS.remove(waypoint.getLevel());}}
    public static synchronized List<WaypointBlockEntity> findWaypoints(Level level,BlockPos origin,double range){
        Set<WaypointBlockEntity> set=LEVELS.get(level);if(set==null||!Double.isFinite(range)||range<0)return List.of();
        double half=range*.5;List<WaypointBlockEntity> result=new ArrayList<>();
        for(var waypoint:set)if(!waypoint.isRemoved()&&waypoint.getLevel()==level&&level.hasChunkAt(waypoint.getBlockPos())&&waypoint.xPosition()>=origin.getX()-half&&waypoint.xPosition()<=origin.getX()+1+half&&waypoint.yPosition()>=origin.getY()-half&&waypoint.yPosition()<=origin.getY()+1+half&&waypoint.zPosition()>=origin.getZ()-half&&waypoint.zPosition()<=origin.getZ()+1+half)result.add(waypoint);
        return result;
    }
    @SubscribeEvent public static void onLevelUnload(LevelEvent.Unload event){if(event.getLevel() instanceof Level level)synchronized(Waypoints.class){LEVELS.remove(level);}}
    @SubscribeEvent public static void onLevelLoad(LevelEvent.Load event){if(event.getLevel() instanceof Level level)synchronized(Waypoints.class){LEVELS.remove(level);}}
}
