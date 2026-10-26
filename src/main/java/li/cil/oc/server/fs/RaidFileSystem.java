package li.cil.oc.server.fs;

import java.io.FileNotFoundException;
import java.io.IOException;
import li.cil.oc.api.fs.Handle;
import li.cil.oc.api.fs.Mode;
import li.cil.oc.api.network.EnvironmentHost;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Persists the cabinet when its volume or descriptor positions change on a VM thread. */
final class RaidFileSystem implements li.cil.oc.api.fs.FileSystem {
    private final li.cil.oc.api.fs.FileSystem inner;
    private final EnvironmentHost host;
    RaidFileSystem(li.cil.oc.api.fs.FileSystem inner,EnvironmentHost host){this.inner=inner;this.host=host;}
    li.cil.oc.api.fs.FileSystem inner(){return inner;}
    private boolean changed(boolean changed){if(changed)host.markChanged();return changed;}
    public boolean isReadOnly(){return inner.isReadOnly();}
    public long spaceTotal(){return inner.spaceTotal();}
    public long spaceUsed(){return inner.spaceUsed();}
    public boolean exists(String path){return inner.exists(path);}
    public boolean isDirectory(String path){return inner.isDirectory(path);}
    public long size(String path){return inner.size(path);}
    public long lastModified(String path){return inner.lastModified(path);}
    public String[] list(String path){return inner.list(path);}
    public boolean delete(String path){return changed(inner.delete(path));}
    public boolean makeDirectory(String path){return changed(inner.makeDirectory(path));}
    public boolean rename(String from,String to)throws FileNotFoundException{return changed(inner.rename(from,to));}
    public boolean setLastModified(String path,long time){return changed(inner.setLastModified(path,time));}
    public int open(String path,Mode mode)throws FileNotFoundException{int handle=inner.open(path,mode);host.markChanged();return handle;}
    public Handle getHandle(int id){Handle handle=inner.getHandle(id);if(handle==null)return null;return new Handle(){public long position(){return handle.position();}public long length(){return handle.length();}public void close(){handle.close();host.markChanged();}public int read(byte[] into)throws IOException{int count=handle.read(into);host.markChanged();return count;}public long seek(long position)throws IOException{long result=handle.seek(position);host.markChanged();return result;}public void write(byte[] value)throws IOException{handle.write(value);host.markChanged();}};}
    public void close(){inner.close();}
    public void load(CompoundTag tag){inner.load(tag);}
    public void save(CompoundTag tag){inner.save(tag);}
    @Override public void load(CompoundTag tag,HolderLookup.Provider registries){inner.load(tag,registries);}
    @Override public void save(CompoundTag tag,HolderLookup.Provider registries){inner.save(tag,registries);}
}
