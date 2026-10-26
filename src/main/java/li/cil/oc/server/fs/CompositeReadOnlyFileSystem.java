package li.cil.oc.server.fs;
import li.cil.oc.api.fs.*;
import net.minecraft.nbt.CompoundTag;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.Callable;
public final class CompositeReadOnlyFileSystem implements li.cil.oc.api.fs.FileSystem {
    private final LinkedHashMap<String,li.cil.oc.api.fs.FileSystem> parts=new LinkedHashMap<>();
    public CompositeReadOnlyFileSystem(Map<String,? extends Callable<li.cil.oc.api.fs.FileSystem>> factories)throws Exception{for(var item:factories.entrySet()){var fs=item.getValue().call();if(fs!=null)parts.put(item.getKey(),fs);}}
    private li.cil.oc.api.fs.FileSystem find(String path){List<li.cil.oc.api.fs.FileSystem> values=new ArrayList<>(parts.values());for(int index=values.size()-1;index>=0;index--)if(values.get(index).exists(path))return values.get(index);return null;}
    public boolean isReadOnly(){return true;}
    public long spaceUsed(){return parts.values().stream().mapToLong(li.cil.oc.api.fs.FileSystem::spaceUsed).sum();}
    public long spaceTotal(){return Math.max(spaceUsed(),parts.values().stream().mapToLong(li.cil.oc.api.fs.FileSystem::spaceTotal).sum());}
    public boolean exists(String path){return find(path)!=null;}
    public boolean isDirectory(String path){var fs=find(path);return fs!=null&&fs.isDirectory(path);}
    public long size(String path){var fs=find(path);return fs==null?0:fs.size(path);}
    public long lastModified(String path){var fs=find(path);return fs==null?0:fs.lastModified(path);}
    public String[] list(String path){if(!isDirectory(path))return null;Set<String> result=new LinkedHashSet<>();for(var fs:parts.values()){String[] children=fs.exists(path)?fs.list(path):null;if(children!=null)for(String child:children){String name=child.endsWith("/")?child.substring(0,child.length()-1):child;result.remove(name);result.remove(name+"/");result.add(child);}}return result.toArray(String[]::new);}
    public boolean delete(String path){return false;}
    public boolean makeDirectory(String path){return false;}
    public boolean rename(String from,String to){return false;}
    public boolean setLastModified(String path,long time){return false;}
    public int open(String path,Mode mode)throws FileNotFoundException{if(mode!=Mode.Read)throw new FileNotFoundException("file system is read only");var fs=find(path);if(fs==null)throw new FileNotFoundException(path);return fs.open(path,mode);}
    public Handle getHandle(int id){for(var fs:parts.values()){Handle handle=fs.getHandle(id);if(handle!=null)return handle;}return null;}
    public void close(){parts.values().forEach(li.cil.oc.api.fs.FileSystem::close);}
    public void load(CompoundTag nbt){parts.forEach((name,fs)->fs.load(nbt.getCompound(name)));}
    public void save(CompoundTag nbt){parts.forEach((name,fs)->{CompoundTag tag=new CompoundTag();fs.save(tag);nbt.put(name,tag);});}
}
