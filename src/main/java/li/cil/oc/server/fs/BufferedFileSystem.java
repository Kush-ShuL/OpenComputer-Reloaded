package li.cil.oc.server.fs;

import li.cil.oc.OpenComputers;
import net.minecraft.nbt.CompoundTag;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;

/** Buffered disks keep world data in RAM and serialize ordered snapshots when the world saves. */
@net.neoforged.fml.common.EventBusSubscriber(modid=li.cil.oc.OpenComputers.ID)
public final class BufferedFileSystem extends VirtualFileSystem {
    private static final ExecutorService SAVER=Executors.newSingleThreadExecutor(task->li.cil.oc.util.ThreadPoolFactory.daemon(task,"OpenComputers-FileSystem"));
    private static final ConcurrentMap<Path,Future<?>> PENDING=new ConcurrentHashMap<>();
    private final Path root;
    private final Map<String,Long> deletions=new LinkedHashMap<>();
    private Future<?> saving;
    public BufferedFileSystem(Path root,long capacity){super(capacity);this.root=root.toAbsolutePath().normalize();awaitSave();loadFiles();}
    @Override protected String clean(String path){String normalized=super.clean(path);if(!FileSystem.isCaseInsensitive())return normalized;String current="";for(String segment:normalized.split("/")){if(segment.isEmpty())continue;String prefix=current.isEmpty()?"":current+"/";String wanted=prefix+segment;String found=null;for(String entry:entries.keySet())if(entry.equalsIgnoreCase(wanted)){found=entry;break;}current=found==null?wanted:found;}return current;}
    @Override public synchronized boolean delete(String path){String key=clean(path);if(!super.delete(key))return false;deletions.put(key,System.currentTimeMillis());return true;}
    @Override public synchronized boolean rename(String from,String to)throws FileNotFoundException{String source=clean(from);if(!super.rename(source,to))return false;deletions.put(source,System.currentTimeMillis());return true;}
    private void awaitSave(){Future<?> previous=PENDING.get(root);if(previous==null)previous=saving;if(previous!=null)try{previous.get(120,TimeUnit.SECONDS);}catch(TimeoutException error){OpenComputers.LOGGER.warn("Waiting for file system to save took two minutes",error);}catch(CancellationException ignored){}catch(InterruptedException error){Thread.currentThread().interrupt();throw FileSystem.failure(new IOException("interrupted waiting for disk save",error));}catch(ExecutionException error){throw FileSystem.failure(new IOException("disk save failed",error.getCause()));}}
    private synchronized void loadFiles(){
        entries.clear();entries.put("",new Entry(true));
        if(!Files.isDirectory(root)){recountUsage();return;}
        try(var stream=Files.walk(root)){
            for(Path path:stream.sorted(Comparator.comparingInt(Path::getNameCount)).toList()){
                String relative=root.relativize(path).toString().replace('\\','/');if(!FileSystem.isValidFilename(relative))continue;
                Entry entry=new Entry(Files.isDirectory(path));entry.modified=Files.getLastModifiedTime(path).toMillis();if(!entry.directory)entry.bytes=Files.readAllBytes(path);entries.put(relative,entry);
            }
            recountUsage();
        }catch(IOException error){throw FileSystem.failure(error);}
    }
    @Override public void load(CompoundTag nbt){awaitSave();synchronized(this){ignoreCapacity=true;try{loadFiles();restoreHandles(nbt);}finally{ignoreCapacity=false;recountUsage();}}}
    @Override public synchronized void save(CompoundTag nbt){
        saveHandles(nbt);
        LinkedHashMap<String,Entry> snapshot=new LinkedHashMap<>();entries.forEach((path,entry)->snapshot.put(path,entry.copy()));
        Map<String,Long> removed=new LinkedHashMap<>(deletions);deletions.clear();
        // A freshly mounted instance must not read this root before its previous instance's snapshot.
        // Only detached snapshots run on SAVER: waiting never needs a filesystem or VM monitor.
        FutureTask<Void> task=new FutureTask<>(()->{try{writeSnapshot(snapshot,removed);return null;}catch(IOException error){OpenComputers.LOGGER.error("Could not save buffered file system {}",root,error);throw error;}}){
            @Override protected void done(){PENDING.remove(root,this);}
        };
        saving=task;PENDING.put(root,task);SAVER.execute(task);
    }
    public void flush(){awaitSave();}
    @net.neoforged.bus.api.SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event){try{SAVER.submit(()->{}).get(120,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}catch(ExecutionException|TimeoutException error){OpenComputers.LOGGER.error("Buffered disk writes did not finish while stopping the server",error);}}
    private Path diskPath(String path)throws IOException{Path result=root.resolve(path).normalize();if(!result.startsWith(root))throw new IOException("path escapes file system root");return result;}
    private void writeSnapshot(Map<String,Entry> snapshot,Map<String,Long> removed)throws IOException{
        for(var deletion:removed.entrySet()){
            Path path=diskPath(deletion.getKey());if(Files.exists(path)&&Files.getLastModifiedTime(path).toMillis()<deletion.getValue()){
                if(Files.isDirectory(path))try(var stream=Files.walk(path)){for(Path child:stream.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(child);}else Files.deleteIfExists(path);
            }
        }
        Files.createDirectories(root);
        for(var item:snapshot.entrySet()){
            Path path=diskPath(item.getKey());Entry entry=item.getValue();
            if(entry.directory){Files.createDirectories(path);continue;}
            if(entry.modified==0||!Files.exists(path)||Files.getLastModifiedTime(path).toMillis()<entry.modified){Files.createDirectories(path.getParent());Files.write(path,entry.bytes,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);Files.setLastModifiedTime(path,FileTime.fromMillis(entry.modified));}
        }
        List<String> directories=snapshot.entrySet().stream().filter(entry->entry.getValue().directory).map(Map.Entry::getKey).sorted(Comparator.comparingInt(String::length).reversed()).toList();
        for(String directory:directories)Files.setLastModifiedTime(diskPath(directory),FileTime.fromMillis(snapshot.get(directory).modified));
    }
}
