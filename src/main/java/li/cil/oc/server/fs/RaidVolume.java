package li.cil.oc.server.fs;

import java.io.IOException;
import java.util.UUID;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.fs.Label;
import li.cil.oc.common.item.data.DriveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Source-derived three-disk concatenation: removing a member destroys the array. */
public final class RaidVolume {
    private final li.cil.oc.server.component.FileSystem environment;
    private RaidVolume(li.cil.oc.server.component.FileSystem environment){this.environment=environment;}
    public li.cil.oc.server.component.FileSystem environment(){return environment;}
    public static RaidVolume create(EnvironmentHost host,ItemStack[] disks,String requested,Label label,CompoundTag saved){
        String address=validAddress(requested);long capacity=0;
        for(ItemStack disk:disks){DriveData data=new DriveData(disk);data.lockInfo="";data.isUnmanaged=false;data.save(disk,host.world().registryAccess());capacity=Math.addExact(capacity,formatDisk(disk,host));}
        var raw=li.cil.oc.api.FileSystem.fromSaveDirectory(address,capacity,Settings.get().bufferChanges);
        var component=(li.cil.oc.server.component.FileSystem)li.cil.oc.api.FileSystem.asManagedEnvironment(new RaidFileSystem(raw,host),label,host,"opencomputers:hdd_access",6);
        if(component==null)throw new IllegalStateException("Filesystem factory unavailable while creating RAID");
        if(saved!=null&&!saved.isEmpty())component.load(saved,host.world().registryAccess());
        CompoundTag node=new CompoundTag();node.putString("address",address);component.node().load(node);((li.cil.oc.api.network.Component)component.node()).setVisibility(Visibility.Network);
        return new RaidVolume(component);
    }
    private static String validAddress(String requested){try{return UUID.fromString(requested).toString();}catch(RuntimeException invalid){return UUID.randomUUID().toString();}}
    private static long formatDisk(ItemStack stack,EnvironmentHost host){var driver=Driver.driverFor(stack,host.getClass());if(driver==null)return 0;var managed=driver.createEnvironment(stack,host);if(!(managed instanceof li.cil.oc.server.component.FileSystem component)){if(managed!=null&&managed.node()!=null)managed.node().remove();return 0;}
        CompoundTag data=driver.dataTag(stack);if(data==null)data=new CompoundTag();component.load(data,host.world().registryAccess());var raw=component.fileSystem();long capacity;
        synchronized(raw){capacity=raw.spaceTotal();raw.close();clear(raw,"");component.save(data,host.world().registryAccess());flush(raw);}
        driver.saveDataTag(stack,data);if(component.node()!=null)component.node().remove();return Math.max(0,capacity);
    }
    private static void clear(li.cil.oc.api.fs.FileSystem fs,String directory){String[] children=fs.list(directory);if(children==null)return;for(String entry:children){String path=directory.isEmpty()?entry:directory+"/"+entry;while(path.endsWith("/"))path=path.substring(0,path.length()-1);if(fs.isDirectory(path))clear(fs,path);if(!fs.delete(path))throw FileSystem.failure(new IOException("Could not format RAID path: "+path));}}
    private static void flush(li.cil.oc.api.fs.FileSystem fs){if(fs instanceof RaidFileSystem raid)fs=raid.inner();if(fs instanceof BufferedFileSystem buffered)buffered.flush();}
    public CompoundTag save(net.minecraft.core.HolderLookup.Provider registries,boolean flush){CompoundTag data=new CompoundTag();environment.save(data,registries);if(flush)flush(environment.fileSystem());return data;}
    public void destroy(net.minecraft.core.HolderLookup.Provider registries){var raw=environment.fileSystem();synchronized(raw){raw.close();clear(raw,"");environment.save(new CompoundTag(),registries);flush(raw);}environment.node().remove();}
    public CompoundTag close(net.minecraft.core.HolderLookup.Provider registries){CompoundTag data=save(registries,true);environment.node().remove();return data;}
}
