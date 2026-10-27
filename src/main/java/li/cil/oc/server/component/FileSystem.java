package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.fs.*;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.prefab.AbstractValue;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import java.io.*;
import java.util.*;

/** Lua filesystem component, including per-computer descriptor ownership and I/O budgets. */
public final class FileSystem extends AbstractManagedEnvironment implements DeviceInfo {
    private final li.cil.oc.api.fs.FileSystem fileSystem;
    /** Original Scala val access, used by storage hosts when formatting or releasing a volume. */
    public li.cil.oc.api.fs.FileSystem fileSystem(){return fileSystem;}
    private final Label label;
    private final EnvironmentHost host;
    private final String sound;
    private final int speed;
    private final Map<String,Set<Integer>> owners=new LinkedHashMap<>();
    private static final double[] READ={1,1d/4,1d/7,1d/10,1d/13,1d/15},SEEK=READ,WRITE={1,1d/2,1d/3,1d/4,1d/5,1d/6};
    public FileSystem(li.cil.oc.api.fs.FileSystem fileSystem,Label label,EnvironmentHost host,String sound,int speed){this.fileSystem=Objects.requireNonNull(fileSystem);this.label=label;this.host=host;this.sound=sound;this.speed=Math.max(0,Math.min(5,speed));setNode(li.cil.oc.api.Network.newNode(this,Visibility.Network).withComponent("filesystem",Visibility.Neighbors).withConnector().create());}
    private ComponentConnector connector(){return(ComponentConnector)node();}
    private static Object[] result(Object... values){return values;}
    public Map<String,String> getDeviceInfo(){return Map.of("class","volume","description","Filesystem","vendor","MightyPirates GmbH & Co. KG","product","MPFS.21.6","capacity",Integer.toString((int)(fileSystem.spaceTotal()*1.024)),"size",Long.toString(fileSystem.spaceTotal()),"clock",((int)(2000/READ[speed])/100)+"/"+((int)(2000/SEEK[speed])/100)+"/"+((int)(2000/WRITE[speed])/100));}
    @Callback(direct=true,doc="function():string -- Get the current label of the drive.") public Object[] getLabel(Context context,Arguments args){synchronized(fileSystem){return label==null?null:result(label.getLabel());}}
    @Callback(doc="function(value:string):string -- Sets the label of the drive. Returns the new value, which may be truncated.") public Object[] setLabel(Context context,Arguments args)throws Exception{synchronized(fileSystem){if(label==null)throw new Exception("drive does not support labeling");label.setLabel(args.checkAny(0)==null?null:args.checkString(0));changed();return result(label.getLabel());}}
    @Callback(direct=true,doc="function():boolean -- Returns whether the file system is read-only.") public Object[] isReadOnly(Context context,Arguments args){return result(fileSystem.isReadOnly());}
    @Callback(direct=true,doc="function():number -- The overall capacity of the file system, in bytes.") public Object[] spaceTotal(Context context,Arguments args){long size=fileSystem.spaceTotal();return result(size<0?Double.POSITIVE_INFINITY:size);}
    @Callback(direct=true,doc="function():number -- The currently used capacity of the file system, in bytes.") public Object[] spaceUsed(Context context,Arguments args){return result(fileSystem.spaceUsed());}
    @Callback(direct=true,doc="function(path:string):boolean -- Returns whether an object exists at the specified absolute path in the file system.") public Object[] exists(Context context,Arguments args){synchronized(fileSystem){activity();return result(fileSystem.exists(clean(args.checkString(0))));}}
    @Callback(direct=true,doc="function(path:string):number -- Returns the size of the object at the specified absolute path in the file system.") public Object[] size(Context context,Arguments args){synchronized(fileSystem){activity();return result(fileSystem.size(clean(args.checkString(0))));}}
    @Callback(direct=true,doc="function(path:string):boolean -- Returns whether the object at the specified absolute path in the file system is a directory.") public Object[] isDirectory(Context context,Arguments args){synchronized(fileSystem){activity();return result(fileSystem.isDirectory(clean(args.checkString(0))));}}
    @Callback(direct=true,doc="function(path:string):number -- Returns the (real world) timestamp of when the object at the specified absolute path in the file system was modified.") public Object[] lastModified(Context context,Arguments args){synchronized(fileSystem){activity();return result(fileSystem.lastModified(clean(args.checkString(0))));}}
    @Callback(doc="function(path:string):table -- Returns a list of names of objects in the directory at the specified absolute path in the file system.") public Object[] list(Context context,Arguments args){synchronized(fileSystem){String[] entries=fileSystem.list(clean(args.checkString(0)));if(entries==null)return null;activity();return result((Object)entries);}}
    @Callback(doc="function(path:string):boolean -- Creates a directory at the specified absolute path in the file system. Creates parent directories, if necessary.") public Object[] makeDirectory(Context context,Arguments args){synchronized(fileSystem){String path=clean(args.checkString(0));boolean success=mkdir(path);activity();return result(success);}}
    private boolean mkdir(String path){if(path.isEmpty()||fileSystem.exists(path))return false;int split=path.lastIndexOf('/');String parent=split<0?"":path.substring(0,split);if(!fileSystem.exists(parent)&&!mkdir(parent))return false;return fileSystem.makeDirectory(path);}
    @Callback(doc="function(path:string):boolean -- Removes the object at the specified absolute path in the file system.") public Object[] remove(Context context,Arguments args){synchronized(fileSystem){boolean result=remove(clean(args.checkString(0)));activity();return result(result);}}
    private boolean remove(String path){if(fileSystem.isDirectory(path)){String[] children=fileSystem.list(path);if(children!=null)for(String child:children)if(!remove(path.isEmpty()?child:path+"/"+child))return false;}return fileSystem.delete(path);}
    @Callback(doc="function(from:string, to:string):boolean -- Renames/moves an object from the first specified absolute path in the file system to the second.") public Object[] rename(Context context,Arguments args)throws IOException{synchronized(fileSystem){boolean changed=fileSystem.rename(clean(args.checkString(0)),clean(args.checkString(1)));activity();return result(changed);}}
    @Callback(direct=true,doc="function(handle:userdata) -- Closes an open file descriptor with the specified handle.") public Object[] close(Context context,Arguments args)throws IOException{synchronized(fileSystem){close(context,handle(args,0));return null;}}
    @Callback(direct=true,limit=4,doc="function(path:string[, mode:string='r']):userdata -- Opens a new file descriptor and returns its handle.") public Object[] open(Context context,Arguments args)throws IOException{synchronized(fileSystem){String owner=context.node().address();Set<Integer> owned=owners.get(owner);if(owned!=null&&owned.size()>=Settings.get().maxHandles)throw new IOException("too many open handles");int id=fileSystem.open(clean(args.checkString(0)),mode(args.optString(1,"r")));if(id>0)owners.computeIfAbsent(owner,key->new LinkedHashSet<>()).add(id);activity();return result(new HandleValue(node().address(),id));}}
    @Callback(direct=true,limit=15,doc="function(handle:userdata, count:number):string or nil -- Reads up to the specified amount of data from an open file descriptor with the specified handle. Returns nil when EOF is reached.") public Object[] read(Context context,Arguments args)throws IOException{synchronized(fileSystem){context.consumeCallBudget(READ[speed]);int id=handle(args,0);int requested=Math.min(Settings.get().maxReadBuffer,Math.max(0,args.checkInteger(1)));checkOwner(context.node().address(),id);Handle file=require(id);byte[] bytes=new byte[requested];int count=file.read(bytes);if(count<0)return result((Object)null);if(!connector().tryChangeBuffer(-Settings.get().hddReadCost*count))throw new IOException("not enough energy");activity();return result((Object)(count==bytes.length?bytes:Arrays.copyOf(bytes,count)));}}
    @Callback(direct=true,doc="function(handle:userdata, whence:string, offset:number):number -- Seeks in an open file descriptor with the specified handle. Returns the new pointer position.") public Object[] seek(Context context,Arguments args)throws IOException{synchronized(fileSystem){context.consumeCallBudget(SEEK[speed]);int id=handle(args,0);String whence=args.checkString(1);long offset=args.checkInteger(2);checkOwner(context.node().address(),id);Handle file=require(id);long position=switch(whence){case "cur"->file.position()+offset;case "set"->offset;case "end"->file.length()+offset;default->throw new IllegalArgumentException("invalid mode");};file.seek(position);changed();return result(file.position());}}
    @Callback(direct=true,doc="function(handle:userdata, value:string):boolean -- Writes the specified data to an open file descriptor with the specified handle.") public Object[] write(Context context,Arguments args)throws IOException{synchronized(fileSystem){context.consumeCallBudget(WRITE[speed]);int id=handle(args,0);byte[] value=args.checkByteArray(1);if(!connector().tryChangeBuffer(-Settings.get().hddWriteCost*value.length))throw new IOException("not enough energy");checkOwner(context.node().address(),id);require(id).write(value);activity();return result(true);}}
    private Handle require(int id)throws IOException{Handle handle=fileSystem.getHandle(id);if(handle==null)throw new IOException("bad file descriptor");return handle;}
    private static int handle(Arguments args,int index)throws IOException{if(args.isInteger(index))return args.checkInteger(index);Object value=args.checkAny(index);if(value instanceof HandleValue handle)return handle.handle;if(value instanceof Map<?,?> table&&table.get("handle") instanceof Number number)return number.intValue();throw new IOException("bad file descriptor");}
    private void checkOwner(String owner,int id)throws IOException{Set<Integer> owned=owners.get(owner);if(owned==null||!owned.contains(id))throw new IOException("bad file descriptor");}
    public void close(Context context,int id)throws IOException{Handle handle=require(id);Set<Integer> owned=owners.get(context.node().address());if(owned==null||!owned.remove(id))throw new IOException("bad file descriptor");handle.close();changed();}
    private void closeOwner(String owner){Set<Integer> owned=owners.remove(owner);if(owned!=null)for(int id:owned){Handle handle=fileSystem.getHandle(id);if(handle!=null)handle.close();}}
    public void onMessage(Message message){if(!message.name().equals("computer.stopped")&&!message.name().equals("computer.started"))return;synchronized(fileSystem){closeOwner(message.source().address());}}
    public void onDisconnect(Node node){synchronized(fileSystem){if(node==node())fileSystem.close();else closeOwner(node.address());}}
    @Override public void load(CompoundTag nbt){load(nbt,null,false);}
    @Override public void load(CompoundTag nbt,HolderLookup.Provider registries){load(nbt,registries,true);}
    private void load(CompoundTag nbt,HolderLookup.Provider registries,boolean registryAware){
        synchronized(fileSystem){
            super.load(nbt);
            owners.clear();
            for(Tag raw:nbt.getList("owners",Tag.TAG_COMPOUND)){
                CompoundTag tag=(CompoundTag)raw;
                String address=tag.getString("address");
                if(!address.isEmpty()){
                    Set<Integer> ids=new LinkedHashSet<>();
                    for(int id:tag.getIntArray("handles"))ids.add(id);
                    owners.put(address,ids);
                }
            }
            if(registryAware){
                if(label!=null)label.load(nbt,registries);
                fileSystem.load(nbt.getCompound("fs"),registries);
            }else{
                if(label!=null)label.load(nbt);
                fileSystem.load(nbt.getCompound("fs"));
            }
        }
    }
    @Override public void save(CompoundTag nbt){save(nbt,null,false);}
    @Override public void save(CompoundTag nbt,HolderLookup.Provider registries){save(nbt,registries,true);}
    private void save(CompoundTag nbt,HolderLookup.Provider registries,boolean registryAware){
        synchronized(fileSystem){
            super.save(nbt);
            if(label!=null){if(registryAware)label.save(nbt,registries);else label.save(nbt);}
            if(li.cil.oc.common.SaveHandler.isSavingForClients())return;
            ListTag list=new ListTag();
            owners.forEach((address,ids)->{
                CompoundTag tag=new CompoundTag();
                tag.putString("address",address);
                tag.putIntArray("handles",ids.stream().mapToInt(Integer::intValue).toArray());
                list.add(tag);
            });
            nbt.put("owners",list);
            CompoundTag contents=new CompoundTag();
            if(registryAware)fileSystem.save(contents,registries);else fileSystem.save(contents);
            nbt.put("fs",contents);
        }
    }
    private static String clean(String path){return li.cil.oc.server.fs.FileSystem.validatePath(path);}
    private static Mode mode(String mode){return switch(mode){case "r","rb"->Mode.Read;case "w","wb"->Mode.Write;case "a","ab"->Mode.Append;default->throw new IllegalArgumentException("unsupported mode");};}
    private void changed(){if(host!=null)host.markChanged();}
    private void activity(){changed();if(host instanceof li.cil.oc.common.block.entity.RackBlockEntity rack)rack.recordDiskActivity(node().address());if(sound!=null&&host!=null)li.cil.oc.server.PacketSender.sendFileSystemActivity(node(),host,sound);}
}
