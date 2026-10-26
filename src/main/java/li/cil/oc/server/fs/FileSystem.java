package li.cil.oc.server.fs;

import li.cil.oc.Settings;
import li.cil.oc.api.detail.FileSystemAPI;
import li.cil.oc.api.fs.Label;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Original file-system factories, backed by Java 21 and registry-aware modern components. */
public final class FileSystem implements FileSystemAPI {
    public static final FileSystem INSTANCE=new FileSystem();
    private static volatile Boolean insensitive;
    public static boolean isValidFilename(String path){return path!=null&&path.chars().noneMatch(value->"\\:*?\"<>|".indexOf(value)>=0);}
    public static String validatePath(String path){
        if(!isValidFilename(path))throw failure(new IOException("path contains invalid characters"));
        Deque<String> segments=new ArrayDeque<>();for(String segment:path.split("/")){if(segment.isEmpty()||segment.equals("."))continue;if(segment.equals("..")){if(segments.isEmpty())throw failure(new IOException("path escapes file system root"));segments.removeLast();}else segments.addLast(segment);}return String.join("/",segments);
    }
    @SuppressWarnings("unchecked") public static <E extends Throwable> RuntimeException failure(Throwable error)throws E{throw(E)error;}
    public static Path saveRoot(){var server=ServerLifecycleHooks.getCurrentServer();if(server==null)throw new IllegalStateException("File systems require a running Minecraft server");return server.getWorldPath(LevelResource.ROOT);}
    public static boolean isCaseInsensitive(){
        if(Settings.get().forceCaseInsensitive)return true;Boolean known=insensitive;if(known!=null)return known;
        synchronized(FileSystem.class){if(insensitive!=null)return insensitive;Path probe=saveRoot().resolve(UUID.randomUUID()+"oc_rox");try{Files.createFile(probe);insensitive=Files.exists(probe.resolveSibling(probe.getFileName().toString().toUpperCase(Locale.ROOT)));}catch(IOException error){insensitive=true;}finally{try{Files.deleteIfExists(probe);}catch(IOException ignored){}}return insensitive;}
    }
    public li.cil.oc.api.fs.FileSystem fromClass(Class<?> clazz,String domain,String root){
        String resource="assets/"+domain+"/"+validatePath(root.trim());
        try{
            var url=clazz.getResource("/"+resource+"/");if(url!=null){try{Path path=Paths.get(url.toURI());if(Files.isDirectory(path))return new PathFileSystem(path,true,-1);}catch(Exception ignored){}}
            ModList mods=ModList.get();if(mods!=null){var file=mods.getModFileById(domain);if(file!=null){Path path=file.getFile().findResource(resource);if(Files.isDirectory(path))return new PathFileSystem(path,true,-1);}}
            var source=clazz.getProtectionDomain().getCodeSource();if(source==null)return null;Path path=Paths.get(source.getLocation().toURI());
            if(Files.isDirectory(path)){Path directory=path.resolve(resource);return Files.isDirectory(directory)?new PathFileSystem(directory,true,-1):null;}
            if(Files.isRegularFile(path))return ZipFileInputStreamFileSystem.fromFile(path.toFile(),resource);
        }catch(Exception error){li.cil.oc.OpenComputers.LOGGER.warn("Failed locating read-only file system {}",resource,error);}
        return null;
    }
    public li.cil.oc.api.fs.FileSystem fromSaveDirectory(String root,long capacity,boolean buffered){
        Path base=saveRoot().resolve(Settings.savePath).normalize(),path=base.resolve(validatePath(root)).normalize();if(!path.startsWith(base))throw new IllegalArgumentException("invalid file system root");
        try{if(Files.exists(path)&&!Files.isDirectory(path))Files.delete(path);Files.createDirectories(path);return buffered?new BufferedFileSystem(path,capacity):new PathFileSystem(path,false,capacity);}catch(IOException error){throw failure(error);}
    }
    public li.cil.oc.api.fs.FileSystem fromMemory(long capacity){return new VolatileFileSystem(capacity);}
    public li.cil.oc.api.fs.FileSystem asReadOnly(li.cil.oc.api.fs.FileSystem fs){return fs.isReadOnly()?fs:new ReadOnlyWrapper(fs);}
    public ManagedEnvironment asManagedEnvironment(li.cil.oc.api.fs.FileSystem fs,Label label,EnvironmentHost host,String sound,int speed){return fs==null?null:new li.cil.oc.server.component.FileSystem(fs,label,host,sound,Math.max(0,Math.min(5,speed-1)));}
    public ManagedEnvironment asManagedEnvironment(li.cil.oc.api.fs.FileSystem fs,String label,EnvironmentHost host,String sound,int speed){return asManagedEnvironment(fs,new ReadOnlyLabel(label),host,sound,speed);}
    public ManagedEnvironment asManagedEnvironment(li.cil.oc.api.fs.FileSystem fs,Label label,EnvironmentHost host,String sound){return asManagedEnvironment(fs,label,host,sound,1);}
    public ManagedEnvironment asManagedEnvironment(li.cil.oc.api.fs.FileSystem fs,String label,EnvironmentHost host,String sound){return asManagedEnvironment(fs,label,host,sound,1);}
    public ManagedEnvironment asManagedEnvironment(li.cil.oc.api.fs.FileSystem fs,Label label){return asManagedEnvironment(fs,label,null,null,1);}
    public ManagedEnvironment asManagedEnvironment(li.cil.oc.api.fs.FileSystem fs,String label){return asManagedEnvironment(fs,label,null,null,1);}
    public ManagedEnvironment asManagedEnvironment(li.cil.oc.api.fs.FileSystem fs){return asManagedEnvironment(fs,(Label)null,null,null,1);}
    public static boolean removeAddress(ItemStack stack){var driver=li.cil.oc.api.Driver.driverFor(stack);if(driver==null)return false;CompoundTag data=driver.dataTag(stack),node=data.getCompound("node");if(!node.contains("address"))return false;node.remove("address");data.put("node",node);driver.saveDataTag(stack,data);return true;}
    public abstract static class ItemLabel implements Label {
        public final ItemStack stack;
        protected ItemLabel(ItemStack stack){this.stack=Objects.requireNonNull(stack);}
    }
    public static class ReadOnlyLabel implements Label {
        private final String label;public ReadOnlyLabel(String label){this.label=label;}
        public String getLabel(){return label;}
        public void setLabel(String value){throw new IllegalArgumentException("label is read only");}
        public void load(CompoundTag nbt){}
        public void save(CompoundTag nbt){if(label!=null)nbt.putString(Settings.namespace+"fs.label",label);}
    }
}
