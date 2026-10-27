package li.cil.oc.server.fs;

import li.cil.oc.Settings;
import li.cil.oc.api.fs.Mode;
import net.minecraft.nbt.*;
import java.io.*;
import java.util.*;

/** Hierarchical in-memory files, capacity accounting, sparse seeks, and persistent descriptors. */
public class VirtualFileSystem extends AbstractFileSystem {
    protected static final class Entry {
        final boolean directory; byte[] bytes = new byte[0]; long modified=System.currentTimeMillis();
        Entry(boolean directory){this.directory=directory;}
        Entry copy(){Entry copy=new Entry(directory);copy.bytes=bytes.clone();copy.modified=modified;return copy;}
    }
    protected final LinkedHashMap<String,Entry> entries = new LinkedHashMap<>();
    private final Map<Entry,OpenHandle> writers=new IdentityHashMap<>();
    protected final long capacity;
    protected boolean ignoreCapacity;
    private long used;
    public VirtualFileSystem(long capacity){this.capacity=capacity;entries.put("",new Entry(true));recountUsage();}
    public boolean isReadOnly(){return false;}
    public long spaceTotal(){return capacity;}
    public synchronized long spaceUsed(){return used;}
    protected synchronized void recountUsage(){used=0;for(Entry entry:entries.values())used+=Settings.get().fileCost+(entry.directory?0:entry.bytes.length);}
    protected void ensureCapacity(long growth)throws IOException{if(!ignoreCapacity&&capacity>=0&&growth>Math.max(0,capacity-spaceUsed()))throw new IOException("not enough space");}
    public synchronized boolean exists(String path){try{return entries.containsKey(clean(path));}catch(RuntimeException error){return false;}}
    public synchronized boolean isDirectory(String path){try{Entry entry=entries.get(clean(path));return entry!=null&&entry.directory;}catch(RuntimeException error){return false;}}
    public synchronized long size(String path){try{Entry entry=entries.get(clean(path));return entry==null||entry.directory?0:entry.bytes.length;}catch(RuntimeException error){return 0;}}
    public synchronized long lastModified(String path){try{Entry entry=entries.get(clean(path));return entry==null?0:entry.modified;}catch(RuntimeException error){return 0;}}
    private static String parent(String path){int index=path.lastIndexOf('/');return index<0?"":path.substring(0,index);}
    public synchronized String[] list(String path){String key=clean(path);if(!isDirectory(key))return null;String prefix=key.isEmpty()?"":key+"/";List<String> result=new ArrayList<>();for(var item:entries.entrySet()){String name=item.getKey();if(!name.equals(key)&&name.startsWith(prefix)&&name.indexOf('/',prefix.length())<0)result.add(name.substring(prefix.length())+(item.getValue().directory?"/":""));}return result.toArray(String[]::new);}
    public synchronized boolean makeDirectory(String path){String key=clean(path);try{ensureCapacity(Settings.get().fileCost);}catch(IOException error){throw FileSystem.failure(error);}if(key.isEmpty()||entries.containsKey(key)||!isDirectory(parent(key)))return false;entries.put(key,new Entry(true));used+=Settings.get().fileCost;entries.get(parent(key)).modified=System.currentTimeMillis();return true;}
    public synchronized boolean delete(String path){
        String key=clean(path);Entry entry=entries.get(key);if(entry==null)return false;
        if(key.isEmpty()){used=Math.max(0,used-Settings.get().fileCost);return true;}
        if(writers.containsKey(entry))return false;
        if(entry.directory)for(var child:entries.entrySet())if(child.getKey().startsWith(key+"/")&&writers.containsKey(child.getValue()))return false;
        entries.keySet().removeIf(name->name.equals(key)||entry.directory&&name.startsWith(key+"/"));
        used=Math.max(0,used-Settings.get().fileCost-(entry.directory?0:entry.bytes.length));entries.get(parent(key)).modified=System.currentTimeMillis();return true;
    }
    public synchronized boolean rename(String from,String to)throws FileNotFoundException{
        String source=clean(from),target=clean(to);if(source.isEmpty()||!entries.containsKey(source))throw new FileNotFoundException(from);if(source.equals(target))return true;if(target.isEmpty()||target.startsWith(source+"/")||!isDirectory(parent(target)))return false;
        Entry sourceParent=entries.get(parent(source)),targetParent=entries.get(parent(target));
        LinkedHashMap<String,Entry> moved=new LinkedHashMap<>();for(var item:entries.entrySet())if(item.getKey().equals(source)||item.getKey().startsWith(source+"/"))moved.put(target+item.getKey().substring(source.length()),item.getValue());
        Entry replaced=entries.get(target);if(replaced!=null){used=Math.max(0,used-Settings.get().fileCost-(replaced.directory?0:replaced.bytes.length));entries.keySet().removeIf(key->key.equals(target)||replaced.directory&&key.startsWith(target+"/"));}
        entries.keySet().removeIf(key->key.equals(source)||key.startsWith(source+"/"));entries.putAll(moved);for(OpenHandle handle:handles.values())if(handle.path.equals(source)||handle.path.startsWith(source+"/"))handle.path=target+handle.path.substring(source.length());long modified=System.currentTimeMillis();sourceParent.modified=modified;targetParent.modified=modified;moved.get(target).modified=modified;return true;
    }
    public synchronized boolean setLastModified(String path,long time){Entry entry=entries.get(clean(path));if(entry==null||time<0)return false;entry.modified=time;return true;}
    protected synchronized OpenHandle createHandle(int id,String path,Mode mode)throws IOException{
        Entry entry=entries.get(path);
        if(mode==Mode.Read){if(entry==null||entry.directory)throw new FileNotFoundException(path);}
        else{
            if(!isDirectory(parent(path))||path.isEmpty()||(entry!=null&&entry.directory))throw new FileNotFoundException(path);
            if(entry!=null&&writers.containsKey(entry))throw new FileNotFoundException(path);
            if(entry==null){ensureCapacity(Settings.get().fileCost);entry=new Entry(false);entries.put(path,entry);used+=Settings.get().fileCost;}
            if(mode==Mode.Write){used=Math.max(0,used-entry.bytes.length);entry.bytes=new byte[0];entry.modified=System.currentTimeMillis();}
        }
        Entry file=entry;OpenHandle result=new OpenHandle(id,path,mode){long position=mode==Mode.Append?file.bytes.length:0;
            public long position(){return position;}
            public long length(){synchronized(VirtualFileSystem.this){return file.bytes.length;}}
            public int read(byte[] into)throws IOException{synchronized(VirtualFileSystem.this){readable();if(into.length==0)return 0;if(position>=file.bytes.length)return -1;int count=(int)Math.min(into.length,file.bytes.length-position);System.arraycopy(file.bytes,(int)position,into,0,count);position+=count;return count;}}
            public long seek(long to)throws IOException{checkOpen();if(to<0)throw new IOException("invalid offset");position=to;return position;}
            public void write(byte[] value)throws IOException{synchronized(VirtualFileSystem.this){writable();long end=position+value.length;if(end>Integer.MAX_VALUE||end<position)throw new IOException("not enough space");ensureCapacity(value.length);long growth=Math.max(0,end-file.bytes.length);if(growth>0)file.bytes=Arrays.copyOf(file.bytes,(int)end);System.arraycopy(value,0,file.bytes,(int)position,value.length);used+=value.length;position=end;file.modified=System.currentTimeMillis();}}
            @Override protected void release(){if(mode!=Mode.Read)writers.remove(file,this);}
        };
        if(mode!=Mode.Read)writers.put(file,result);return result;
    }
    protected synchronized void loadEntries(CompoundTag nbt){entries.clear();readEntry("",nbt);if(!entries.containsKey(""))entries.put("",new Entry(true));recountUsage();}
    private void readEntry(String path,CompoundTag tag){boolean directory=path.isEmpty()||tag.getBoolean("isDirectory");Entry entry=new Entry(directory);if(tag.contains("lastModified"))entry.modified=tag.getLong("lastModified");if(!directory)entry.bytes=tag.getByteArray("data");entries.put(path,entry);if(directory)for(Tag raw:tag.getList("children",Tag.TAG_COMPOUND)){CompoundTag child=(CompoundTag)raw;String name=child.getString("name");if(!name.isEmpty()&&!name.contains("/")&&FileSystem.isValidFilename(name))readEntry(path.isEmpty()?name:path+"/"+name,child);}}
    private CompoundTag writeEntry(String path){Entry entry=entries.get(path);CompoundTag tag=new CompoundTag();tag.putLong("lastModified",entry.modified);tag.putBoolean("isDirectory",entry.directory);if(!entry.directory)tag.putByteArray("data",entry.bytes);else{ListTag children=new ListTag();for(String name:list(path)){String cleanName=name.endsWith("/")?name.substring(0,name.length()-1):name;CompoundTag child=writeEntry(path.isEmpty()?cleanName:path+"/"+cleanName);child.putString("name",cleanName);children.add(child);}tag.put("children",children);}return tag;}
    public synchronized void load(CompoundTag nbt){ignoreCapacity=true;try{closeHandles();loadEntries(nbt);super.load(nbt);}finally{ignoreCapacity=false;recountUsage();}}
    public synchronized void save(CompoundTag nbt){super.save(nbt);nbt.merge(writeEntry(""));}
    @Override public synchronized void close(){super.close();recountUsage();}
}
