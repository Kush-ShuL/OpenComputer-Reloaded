package li.cil.oc.server.fs;
import li.cil.oc.api.fs.*;
import net.minecraft.nbt.CompoundTag;
import java.io.FileNotFoundException;
public final class ReadOnlyWrapper implements li.cil.oc.api.fs.FileSystem {
    private final li.cil.oc.api.fs.FileSystem inner;
    public ReadOnlyWrapper(li.cil.oc.api.fs.FileSystem inner){this.inner=java.util.Objects.requireNonNull(inner);}
    public boolean isReadOnly(){return true;}
    public long spaceTotal(){return inner.spaceUsed();}
    public long spaceUsed(){return inner.spaceUsed();}
    public boolean exists(String path){return inner.exists(path);}
    public boolean isDirectory(String path){return inner.isDirectory(path);}
    public long size(String path){return inner.size(path);}
    public long lastModified(String path){return inner.lastModified(path);}
    public String[] list(String path){return inner.list(path);}
    public boolean delete(String path){return false;}
    public boolean makeDirectory(String path){return false;}
    public boolean rename(String from,String to){return false;}
    public boolean setLastModified(String path,long time){return false;}
    public int open(String path,Mode mode)throws FileNotFoundException{if(mode!=Mode.Read)throw new FileNotFoundException("file system is read only");return inner.open(path,mode);}
    public Handle getHandle(int id){return inner.getHandle(id);}
    public void close(){inner.close();}
    public void load(CompoundTag nbt){inner.load(nbt);}
    public void save(CompoundTag nbt){inner.save(nbt);}
}
