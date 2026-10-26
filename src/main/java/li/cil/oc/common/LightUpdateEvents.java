package li.cil.oc.common;

import li.cil.oc.Settings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** Original optional 40-tick workaround, limited to already loaded OC block entities. */
public final class LightUpdateEvents {
    private static final Map<Level,Map<Long,LevelChunk>> LOADED=new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean registered;
    private LightUpdateEvents(){}
    public static void register(){if(registered)return;registered=true;NeoForge.EVENT_BUS.addListener(LightUpdateEvents::load);NeoForge.EVENT_BUS.addListener(LightUpdateEvents::unload);NeoForge.EVENT_BUS.addListener(LightUpdateEvents::unloadLevel);NeoForge.EVENT_BUS.addListener(LightUpdateEvents::tick);}
    private static void load(ChunkEvent.Load event){if(!Settings.get().periodicallyForceLightUpdate)return;if(event.getLevel() instanceof Level level&&event.getChunk() instanceof LevelChunk chunk)LOADED.computeIfAbsent(level,key->new java.util.concurrent.ConcurrentHashMap<>()).put(chunk.getPos().toLong(),chunk);}
    private static void unload(ChunkEvent.Unload event){if(event.getLevel() instanceof Level level){Map<Long,LevelChunk> chunks=LOADED.get(level);if(chunks!=null)chunks.remove(event.getChunk().getPos().toLong(),event.getChunk());}}
    private static void unloadLevel(LevelEvent.Unload event){if(event.getLevel() instanceof Level level)LOADED.remove(level);}
    private static void tick(LevelTickEvent.Post event){if(!Settings.get().periodicallyForceLightUpdate)return;Level level=event.getLevel();if(level.getGameTime()%40!=0)return;Map<Long,LevelChunk> chunks=LOADED.get(level);if(chunks==null)return;for(LevelChunk chunk:List.copyOf(chunks.values()))for(var entity:List.copyOf(chunk.getBlockEntities().values())){if(entity.isRemoved()||entity.getLevel()!=level)continue;var type=BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType());if(type==null||!type.getNamespace().equals("opencomputers"))continue;var pos=entity.getBlockPos();var state=entity.getBlockState();if(state.getLightEmission(level,pos)<=0)continue;level.getChunkSource().getLightEngine().checkBlock(pos);level.sendBlockUpdated(pos,state,state,3);}}
}
