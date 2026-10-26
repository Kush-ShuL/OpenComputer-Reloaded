package li.cil.oc.server.fs;

import li.cil.oc.Settings;
import li.cil.oc.api.fs.Mode;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;

/** Native and SecureJar/union-backed streams; writes are immediate for unbuffered disks. */
public class PathFileSystem extends AbstractFileSystem {
    protected final Path root; private final long capacity; private final boolean readOnly;
    public PathFileSystem(Path root,boolean readOnly,long capacity){this.root=root.toAbsolutePath().normalize();this.readOnly=readOnly;this.capacity=capacity;}
    protected Path resolve(String path){Path result=root.resolve(clean(path)).normalize();if(!result.startsWith(root))throw FileSystem.failure(new IOException("path escapes file system root"));return result;}
    public boolean isReadOnly(){return readOnly;}
    public long spaceTotal(){return readOnly?spaceUsed():capacity;}
    public synchronized long spaceUsed(){try(var paths=Files.walk(root)){long used=0;for(Path path:paths.toList())used+=(readOnly?0:Settings.get().fileCost)+(Files.isRegularFile(path)?Files.size(path):0);return used;}catch(IOException error){throw FileSystem.failure(error);}}
    public boolean exists(String path){try{return Files.exists(resolve(path));}catch(RuntimeException error){return false;}}
    public boolean isDirectory(String path){try{return Files.isDirectory(resolve(path));}catch(RuntimeException error){return false;}}
    public long size(String path){try{Path file=resolve(path);return Files.isRegularFile(file)?Files.size(file):0;}catch(IOException|RuntimeException error){return 0;}}
    public long lastModified(String path){try{return Files.getLastModifiedTime(resolve(path)).toMillis();}catch(IOException|RuntimeException error){return 0;}}
    public String[] list(String path){Path file=resolve(path);if(!Files.isDirectory(file))return null;try(var stream=Files.list(file)){return stream.map(child->child.getFileName().toString()+(Files.isDirectory(child)?"/":"")).toArray(String[]::new);}catch(IOException error){throw FileSystem.failure(error);}}
    private void ensureCapacity(long growth)throws IOException{if(capacity>=0&&growth>Math.max(0,capacity-spaceUsed()))throw new IOException("not enough space");}
    public synchronized boolean makeDirectory(String path){if(readOnly)return false;Path file=resolve(path);if(Files.exists(file))return false;try{ensureCapacity(Settings.get().fileCost);Files.createDirectory(file);return true;}catch(NoSuchFileException error){return false;}catch(IOException error){throw FileSystem.failure(error);}}
    public synchronized boolean delete(String path){if(readOnly||clean(path).isEmpty())return false;try{return Files.deleteIfExists(resolve(path));}catch(DirectoryNotEmptyException error){return false;}catch(IOException error){throw FileSystem.failure(error);}}
    public synchronized boolean rename(String from,String to)throws FileNotFoundException{if(readOnly)return false;Path source=resolve(from),target=resolve(to);if(!Files.exists(source))throw new FileNotFoundException(from);if(source.equals(root)||target.equals(root))return false;try{Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);String old=clean(from),replacement=clean(to);for(OpenHandle handle:handles.values())if(handle.path.equals(old)||handle.path.startsWith(old+"/"))handle.path=replacement+handle.path.substring(old.length());return true;}catch(IOException error){return false;}}
    public boolean setLastModified(String path,long time){if(readOnly||time<0)return false;try{Files.setLastModifiedTime(resolve(path),FileTime.fromMillis(time));return true;}catch(IOException error){return false;}}
    protected OpenHandle createHandle(int id,String path,Mode mode)throws IOException{
        Path file=resolve(path);if(Files.isDirectory(file))throw new FileNotFoundException(path);
        if(mode!=Mode.Read){if(readOnly)throw new FileNotFoundException("file system is read only");if(!Files.exists(file))ensureCapacity(Settings.get().fileCost);}
        SeekableByteChannel channel=mode==Mode.Read?Files.newByteChannel(file,StandardOpenOption.READ):Files.newByteChannel(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        if(mode==Mode.Write)channel.truncate(0);else if(mode==Mode.Append)channel.position(channel.size());
        return new OpenHandle(id,path,mode){
            public long position(){try{return channel.position();}catch(IOException error){throw FileSystem.failure(error);}}
            public long length(){try{return channel.size();}catch(IOException error){throw FileSystem.failure(error);}}
            public int read(byte[] into)throws IOException{readable();return channel.read(ByteBuffer.wrap(into));}
            public long seek(long to)throws IOException{checkOpen();if(to<0)throw new IOException("invalid offset");channel.position(to);return channel.position();}
            public void write(byte[] value)throws IOException{synchronized(PathFileSystem.this){writable();ensureCapacity(Math.max(0,channel.position()+value.length-channel.size()));ByteBuffer bytes=ByteBuffer.wrap(value);while(bytes.hasRemaining())channel.write(bytes);}}
            protected void release(){try{channel.close();}catch(IOException error){throw FileSystem.failure(error);}}
        };
    }
}
