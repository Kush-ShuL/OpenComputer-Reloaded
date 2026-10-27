package li.cil.oc.server.fs;

import li.cil.oc.Settings;
import li.cil.oc.api.fs.Mode;
import net.minecraft.nbt.CompoundTag;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;

/** Native and SecureJar/union-backed streams; writes are immediate for unbuffered disks. */
public class PathFileSystem extends AbstractFileSystem {
    protected final Path root; private final long capacity; private final boolean readOnly;
    private long used;
    private boolean restoring;
    public PathFileSystem(Path root,boolean readOnly,long capacity){this.root=root.toAbsolutePath().normalize();this.readOnly=readOnly;this.capacity=capacity;used=actualSpaceUsed();}
    protected Path resolve(String path){Path result=root.resolve(clean(path)).normalize();if(!result.startsWith(root))throw FileSystem.failure(new IOException("path escapes file system root"));return result;}
    public boolean isReadOnly(){return readOnly;}
    public long spaceTotal(){return readOnly?spaceUsed():capacity;}
    public synchronized long spaceUsed(){return used;}
    private long actualSpaceUsed(){if(!Files.exists(root))return readOnly?0:Settings.get().fileCost;try(var paths=Files.walk(root)){long actual=0;for(Path path:paths.toList())actual+=(readOnly?0:Settings.get().fileCost)+(Files.isRegularFile(path)?Files.size(path):0);return actual;}catch(IOException error){throw FileSystem.failure(error);}}
    public boolean exists(String path){try{return Files.exists(resolve(path));}catch(RuntimeException error){return false;}}
    public boolean isDirectory(String path){try{return Files.isDirectory(resolve(path));}catch(RuntimeException error){return false;}}
    public long size(String path){try{Path file=resolve(path);return Files.isRegularFile(file)?Files.size(file):0;}catch(IOException|RuntimeException error){return 0;}}
    public long lastModified(String path){try{return Files.getLastModifiedTime(resolve(path)).toMillis();}catch(IOException|RuntimeException error){return 0;}}
    public String[] list(String path){Path file=resolve(path);if(Files.isRegularFile(file))return new String[]{file.getFileName().toString()};if(!Files.isDirectory(file))throw FileSystem.failure(new FileNotFoundException("no such file or directory: "+path));try(var stream=Files.list(file)){return stream.map(child->child.getFileName().toString()+(Files.isDirectory(child)?"/":"")).toArray(String[]::new);}catch(IOException error){throw FileSystem.failure(error);}}
    private void ensureCapacity(long growth)throws IOException{if(!restoring&&capacity>=0&&growth>Math.max(0,capacity-spaceUsed()))throw new IOException("not enough space");}
    public synchronized boolean makeDirectory(String path){if(readOnly)return false;Path file=resolve(path);try{ensureCapacity(Settings.get().fileCost);if(Files.exists(file))return false;Files.createDirectory(file);used+=Settings.get().fileCost;return true;}catch(NoSuchFileException error){return false;}catch(IOException error){throw FileSystem.failure(error);}}
    public synchronized boolean delete(String path){if(readOnly)return false;Path file=resolve(path);long freed=Settings.get().fileCost+size(path);if(file.equals(root)){used=Math.max(0,used-freed);return true;}try{boolean deleted=Files.deleteIfExists(file);if(deleted)used=Math.max(0,used-freed);return deleted;}catch(DirectoryNotEmptyException error){return false;}catch(IOException error){throw FileSystem.failure(error);}}
    public synchronized boolean rename(String from,String to)throws FileNotFoundException{if(readOnly)return false;Path source=resolve(from),target=resolve(to);if(!Files.exists(source))return false;if(source.equals(root)||target.equals(root))return false;long freed=Files.exists(target)&&!source.equals(target)?Settings.get().fileCost+size(to):0;try{Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);used=Math.max(0,used-freed);String old=clean(from),replacement=clean(to);for(OpenHandle handle:handles.values())if(handle.path.equals(old)||handle.path.startsWith(old+"/"))handle.path=replacement+handle.path.substring(old.length());return true;}catch(IOException error){return false;}}
    public boolean setLastModified(String path,long time){if(readOnly||time<0)return false;try{Files.setLastModifiedTime(resolve(path),FileTime.fromMillis(time));return true;}catch(IOException error){return false;}}
    protected OpenHandle createHandle(int id,String path,Mode mode)throws IOException{
        Path file=resolve(path);if(Files.isDirectory(file))throw new FileNotFoundException(path);
        boolean existed=Files.exists(file);long previous=existed?size(path):0;
        if(mode!=Mode.Read){if(readOnly)throw new FileNotFoundException("file system is read only");ensureCapacity(existed?mode==Mode.Write?-previous:0:Settings.get().fileCost);}
        SeekableByteChannel channel=mode==Mode.Read?Files.newByteChannel(file,StandardOpenOption.READ):Files.newByteChannel(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        if(mode==Mode.Write)channel.truncate(0);else if(mode==Mode.Append)channel.position(channel.size());
        if(mode!=Mode.Read)used=Math.max(0,used+(existed?mode==Mode.Write?-previous:0:Settings.get().fileCost));
        return new OpenHandle(id,path,mode){
            public long position(){try{return channel.position();}catch(IOException error){throw FileSystem.failure(error);}}
            public long length(){try{return channel.size();}catch(IOException error){throw FileSystem.failure(error);}}
            public int read(byte[] into)throws IOException{readable();return channel.read(ByteBuffer.wrap(into));}
            public long seek(long to)throws IOException{checkOpen();if(to<0)throw new IOException("invalid offset");channel.position(to);return channel.position();}
            public void write(byte[] value)throws IOException{synchronized(PathFileSystem.this){writable();ensureCapacity(value.length);ByteBuffer bytes=ByteBuffer.wrap(value);while(bytes.hasRemaining())channel.write(bytes);used+=value.length;}}
            protected void release(){try{channel.close();}catch(IOException error){throw FileSystem.failure(error);}}
        };
    }
    @Override public synchronized void close(){super.close();used=actualSpaceUsed();}
    @Override public synchronized void load(CompoundTag nbt){restoring=true;try{super.load(nbt);}finally{restoring=false;used=actualSpaceUsed();}}
}
