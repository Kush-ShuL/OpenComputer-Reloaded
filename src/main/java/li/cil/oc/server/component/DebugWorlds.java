package li.cil.oc.server.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Keeps the original numeric debug API stable while modern dimensions use resource keys. */
final class DebugWorlds extends SavedData {
    private final Map<String,Integer> ids=new LinkedHashMap<>();
    private DebugWorlds(){ids.put("minecraft:overworld",0);ids.put("minecraft:the_nether",-1);ids.put("minecraft:the_end",1);}
    private static DebugWorlds load(CompoundTag tag,HolderLookup.Provider registries){var data=new DebugWorlds();CompoundTag ids=tag.getCompound("dimensions");for(String key:ids.getAllKeys())data.ids.put(key,ids.getInt(key));return data;}
    private static DebugWorlds get(MinecraftServer server){var data=server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(DebugWorlds::new,DebugWorlds::load),"opencomputers_debug_dimensions");server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM).keySet().stream().map(ResourceLocation::toString).sorted().forEach(key->{if(!data.ids.containsKey(key)){int next=data.ids.values().stream().mapToInt(Integer::intValue).max().orElse(1)+1;data.ids.put(key,next);data.setDirty();}});return data;}
    static int id(ServerLevel level){return get(level.getServer()).ids.get(level.dimension().location().toString());}
    static int[] ids(MinecraftServer server){var data=get(server);return server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM).keySet().stream().mapToInt(key->data.ids.get(key.toString())).sorted().toArray();}
    static ServerLevel level(MinecraftServer server,int id){String key=get(server).ids.entrySet().stream().filter(entry->entry.getValue()==id).map(Map.Entry::getKey).findFirst().orElseThrow(()->new IllegalArgumentException("invalid dimension id"));return level(server,key);}
    static ServerLevel level(MinecraftServer server,String key){ServerLevel level=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(key)));if(level==null)throw new IllegalArgumentException("dimension is not loaded");return level;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){CompoundTag dimensions=new CompoundTag();ids.forEach(dimensions::putInt);tag.put("dimensions",dimensions);return tag;}
}
