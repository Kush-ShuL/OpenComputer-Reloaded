package li.cil.oc.server.fs;

import li.cil.oc.OpenComputers;
import li.cil.oc.api.fs.Mode;
import net.minecraft.nbt.CompoundTag;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Archive assets are materialized once and thereafter exposed through read-only descriptors. */
public final class ZipFileInputStreamFileSystem extends VirtualFileSystem {
    private ZipFileInputStreamFileSystem(){super(-1);}
    public static ZipFileInputStreamFileSystem fromFile(File file,String innerPath){
        String prefix=FileSystem.validatePath(innerPath);if(!prefix.isEmpty())prefix+="/";
        ZipFileInputStreamFileSystem fs=new ZipFileInputStreamFileSystem();boolean found=prefix.isEmpty();
        try(ZipFile zip=new ZipFile(file)){
            Enumeration<? extends ZipEntry> iterator=zip.entries();
            while(iterator.hasMoreElements()){
                ZipEntry entry=iterator.nextElement();if(!entry.getName().startsWith(prefix))continue;found=true;
                String path=entry.getName().substring(prefix.length());if(path.isEmpty())continue;if(path.endsWith("/"))path=path.substring(0,path.length()-1);path=FileSystem.validatePath(path);
                int slash=path.indexOf('/');while(slash>=0){fs.entries.putIfAbsent(path.substring(0,slash),new Entry(true));slash=path.indexOf('/',slash+1);}
                Entry data=new Entry(entry.isDirectory());data.modified=entry.getTime();if(!data.directory)try(InputStream stream=zip.getInputStream(entry)){data.bytes=stream.readAllBytes();}fs.entries.put(path,data);
            }
            return found?fs:null;
        }catch(IOException error){OpenComputers.LOGGER.warn("Failed opening asset archive {}",file,error);return null;}
    }
    public boolean isReadOnly(){return true;}
    public long spaceTotal(){return spaceUsed();}
    public synchronized long spaceUsed(){return entries.values().stream().filter(entry->!entry.directory).mapToLong(entry->entry.bytes.length).sum();}
    public boolean makeDirectory(String path){return false;}
    public boolean delete(String path){return false;}
    public boolean rename(String from,String to){return false;}
    public boolean setLastModified(String path,long time){return false;}
    protected OpenHandle createHandle(int id,String path,Mode mode)throws IOException{if(mode!=Mode.Read)throw new FileNotFoundException("file system is read only");return super.createHandle(id,path,mode);}
    public synchronized void load(CompoundTag nbt){super.closeHandles();restoreDescriptors(nbt);}
    private void restoreDescriptors(CompoundTag nbt){for(var raw:nbt.getList("input",net.minecraft.nbt.Tag.TAG_COMPOUND)){CompoundTag tag=(CompoundTag)raw;try{int id=tag.getInt("handle");OpenHandle handle=createHandle(id,clean(tag.getString("path")),Mode.Read);handle.seek(tag.getLong("position"));handles.put(id,handle);}catch(IOException ignored){}}}
    public synchronized void save(CompoundTag nbt){net.minecraft.nbt.ListTag list=new net.minecraft.nbt.ListTag();for(OpenHandle handle:handles.values()){CompoundTag tag=new CompoundTag();tag.putInt("handle",handle.id);tag.putString("path",handle.path);tag.putLong("position",handle.position());list.add(tag);}nbt.put("input",list);}
}
