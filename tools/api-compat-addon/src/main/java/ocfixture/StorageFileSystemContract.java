package ocfixture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.fs.FileSystem;
import li.cil.oc.api.fs.Handle;
import li.cil.oc.api.fs.Mode;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.machine.Value;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Independent published-SDK checks of real storage, ownership and raw media. */
public final class StorageFileSystemContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-storage-filesystem");
    private static Run active;
    private StorageFileSystemContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){
        Run run=new Run(level,position,results);active=run;
        try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){Run run=active;if(run==null)return;try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}active=null;run.cleanup();}
    public static void close(){Run run=active;active=null;if(run!=null)run.cleanup();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ItemStack item(String name){var value=li.cil.oc.api.Items.get(name);require(value!=null,"Missing real storage item "+name);return value.createItemStack(1);}
    @FunctionalInterface private interface Operation{void run()throws Exception;}
    private static void rejected(Operation action,String expected)throws Exception{
        try{action.run();}catch(Exception error){require(error.getMessage()!=null&&error.getMessage().contains(expected),"Unexpected error: "+error);return;}
        throw new AssertionError("Operation accepted instead of reporting "+expected);
    }
    private static void rejected(Operation action,Class<? extends Exception> expected)throws Exception{
        try{action.run();}catch(Exception error){require(expected.isInstance(error),"Unexpected error: "+error);return;}
        throw new AssertionError("Operation accepted instead of "+expected.getSimpleName());
    }
    private static final class Run {
        final ServerLevel level;final BlockPos position;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        final List<Node> nodes=new ArrayList<>();final List<FileSystem> volumes=new ArrayList<>();
        Case owner,other;int ticks;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;}
        Case place(BlockPos pos){
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
            require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Storage fixture cell is occupied: "+pos);restored.put(pos,level.getBlockState(pos));
            require(level.setBlock(pos,li.cil.oc.api.Items.get("case3").block().defaultBlockState(),3),"Could not place real storage Case");
            require(level.getBlockEntity(pos) instanceof Case,"Storage host is not an actual SDK Case");return(Case)level.getBlockEntity(pos);
        }
        void start(){owner=place(position);other=place(position.east(4));}
        boolean tick()throws Exception{
            if(++ticks<3)return false;
            require(!owner.machine().isRunning()&&!other.machine().isRunning(),"Storage contract must use stopped real machine contexts");
            for(int kind=0;kind<3;kind++)quota(kind);
            replacementHandles();archive();callbacks();rawMedia();return true;
        }
        FileSystem volume(int kind,long capacity){
            FileSystem fs=kind==0?li.cil.oc.api.FileSystem.fromMemory(capacity):li.cil.oc.api.FileSystem.fromSaveDirectory("ocfixture-storage-"+UUID.randomUUID(),capacity,kind==2);
            require(fs!=null,"Published filesystem factory returned null");volumes.add(fs);return fs;
        }
        void quota(int kind)throws Exception{
            int cost=API.config.getInt("filesystem.fileCost");long capacity=4L*cost+20;
            FileSystem fs=volume(kind,capacity);require(fs.spaceUsed()==cost,"Initial root metadata charge differs from the original");
            require(fs.makeDirectory("dir"),"Could not create original quota test directory");int descriptor=fs.open("dir/file",Mode.Write);Handle output=fs.getHandle(descriptor);
            byte[] initial={1,2,3,4,5,6,7,8};output.write(initial);require(fs.spaceUsed()==3L*cost+8,"File metadata and write charge are wrong");
            output.seek(0);output.write(new byte[]{9,10,11,12});require(fs.size("dir/file")==8&&fs.spaceUsed()==3L*cost+12,"Overwrite must charge bytes written without extending file length");
            int reader=fs.open("dir/file",Mode.Read);byte[] bytes=new byte[8];require(fs.getHandle(reader).read(bytes)==8&&Arrays.equals(bytes,new byte[]{9,10,11,12,5,6,7,8}),"Reader cannot coexist with the original active writer");fs.getHandle(reader).close();
            if(kind!=1)rejected(()->fs.open("dir/file",Mode.Append),java.io.FileNotFoundException.class);
            else{int second=fs.open("dir/file",Mode.Append);fs.getHandle(second).close();}
            int tooMany=(int)(capacity-fs.spaceUsed()+1);long before=output.position();rejected(()->output.write(new byte[tooMany]),"not enough space");require(output.position()==before&&fs.size("dir/file")==8,"Rejected over-capacity write changed content or pointer");
            CompoundTag saved=new CompoundTag();fs.save(saved,level.registryAccess());fs.load(saved,level.registryAccess());
            require(fs.spaceUsed()==3L*cost+8,"Reload must recount real bytes instead of retaining transient overwrite charges");Handle resumed=fs.getHandle(descriptor);require(resumed!=null&&resumed.position()==before,"Saved writer identity/pointer did not restore");resumed.close();
            require(fs.rename("dir/file","dir/renamed"),"Real storage rename failed");
            FileSystem readOnly=li.cil.oc.api.FileSystem.asReadOnly(fs);require(readOnly.isReadOnly()&&!readOnly.delete("dir/renamed")&&!readOnly.makeDirectory("blocked")&&!readOnly.rename("dir/renamed","blocked"),"Read-only wrapper allowed a mutation");
            rejected(()->readOnly.open("dir/renamed",Mode.Write),"read-only filesystem");
            require(fs.exists("/../dir/renamed"),"Absolute parent traversal did not clamp at the original filesystem root");rejected(()->fs.open("../outside",Mode.Read),"../outside");
            require(fs.delete("dir/renamed")&&fs.delete("dir"),"Quota cleanup did not remove actual file/directory");
            results.add("PASS filesystem.quota backend="+new String[]{"memory","native","buffered"}[kind]+" real metadata/overwrite charges, reader-writer coexistence, original writer exclusivity, rejected writes, descriptor reload, read-only operations and normalized root paths");
        }
        ManagedEnvironment attach(FileSystem fs)throws Exception{
            ManagedEnvironment environment=li.cil.oc.api.FileSystem.asManagedEnvironment(fs,(li.cil.oc.api.fs.Label)null,owner,null,1);
            require(environment!=null&&environment.node() instanceof Component&&environment.node() instanceof Connector,"Published filesystem component factory failed");
            nodes.add(environment.node());owner.machine().node().connect(environment.node());other.machine().node().connect(environment.node());((Connector)environment.node()).changeBuffer(Double.POSITIVE_INFINITY);return environment;
        }
        void archive()throws Exception{
            FileSystem fs=li.cil.oc.api.FileSystem.fromClass(li.cil.oc.api.FileSystem.class,"opencomputers","loot/openos");
            require(fs!=null&&fs.isReadOnly()&&fs.spaceUsed()>0&&fs.spaceTotal()==fs.spaceUsed(),"Actual mod archive did not expose its read-only OpenOS filesystem");volumes.add(fs);
            int id=fs.open("init.lua",Mode.Read);require(fs.getHandle(id).read(new byte[32])>0,"Actual archive input descriptor returned no OpenOS bytes");fs.getHandle(id).close();
            rejected(()->fs.open("init.lua",Mode.Write),java.io.FileNotFoundException.class);
            results.add("PASS filesystem.archive the public class-resource factory opens actual packaged OpenOS bytes through a read-only descriptor and reports its real capacity");
        }
        void replacementHandles()throws Exception{
            FileSystem fs=volume(0,10L*API.config.getInt("filesystem.fileCost")+32);
            int source=fs.open("source",Mode.Write);fs.getHandle(source).write(new byte[]{1});fs.getHandle(source).close();
            int oldTarget=fs.open("target",Mode.Write);Handle orphan=fs.getHandle(oldTarget);orphan.write(new byte[]{2});
            require(fs.rename("source","target"),"RAM filesystem did not replace the target name while its old writer remains open");
            orphan.write(new byte[]{3});require(orphan.length()==2,"Replacing a name destroyed its original open stream");
            int reader=fs.open("target",Mode.Read);byte[] first=new byte[1];require(fs.getHandle(reader).read(first)==1&&first[0]==1,"Old target writer was retargeted to the replacement file");
            int next=fs.open("target",Mode.Append);fs.getHandle(next).write(new byte[]{4});
            require(fs.getHandle(reader).read(first)==1&&first[0]==4,"Replacement file did not keep its own independent output stream");
            fs.getHandle(next).close();fs.getHandle(reader).close();orphan.close();
            results.add("PASS filesystem.rename replacing an open target preserves the old stream's file identity while new readers/writers access the replacement");
        }
        static int handle(Object value){require(value instanceof Value,"Filesystem did not return its real userdata");CompoundTag tag=new CompoundTag();((Value)value).save(tag);return tag.getInt("handle");}
        void callbacks()throws Exception{
            FileSystem fs=volume(0,16384);ManagedEnvironment environment=attach(fs);Component component=(Component)environment.node();
            boolean labelRejected=false;
            try{component.invoke("setLabel",owner.machine(),"unsupported");}catch(IllegalArgumentException wrongKind){throw new AssertionError("Missing label support became an argument error",wrongKind);}catch(Exception expected){require("drive does not support labeling".equals(expected.getMessage()),"Unexpected label error: "+expected);labelRejected=true;}
            require(labelRejected,"Filesystem without a Label accepted a label change");
            Object descriptor=component.invoke("open",owner.machine(),"/shared","wb")[0];int id=handle(descriptor);byte[] bytes={0,1,(byte)255,3,4};
            require(Boolean.TRUE.equals(component.invoke("write",owner.machine(),descriptor,bytes)[0]),"Actual filesystem write failed");
            Object reader=component.invoke("open",other.machine(),"shared","rb")[0];
            rejected(()->component.invoke("read",other.machine(),descriptor,1),"bad file descriptor");
            require(Arrays.equals((byte[])component.invoke("read",other.machine(),reader,2)[0],new byte[]{0,1}),"Real owner reader lost binary bytes");
            CompoundTag saved=new CompoundTag();environment.save(saved,level.registryAccess());environment.load(saved,level.registryAccess());
            require(Arrays.equals((byte[])component.invoke("read",other.machine(),reader,3)[0],new byte[]{(byte)255,3,4}),"Registered descriptor owner/pointer did not resume through component save/load");
            require(component.invoke("read",other.machine(),reader,1)[0]==null,"Real EOF did not return nil");
            owner.machine().node().sendToReachable("computer.stopped");require(fs.getHandle(id)==null&&fs.getHandle(handle(reader))!=null,"Computer stop did not close only its own real handles");
            int maximum=API.config.getInt("filesystem.maxHandles");require(maximum>0,"Fixture requires normal positive handle limit");
            for(int index=0;index<maximum;index++)component.invoke("open",owner.machine(),"shared","r");
            rejected(()->component.invoke("open",owner.machine(),"shared","r"),"too many open handles");owner.machine().node().sendToReachable("computer.started");
            Object disposable=component.invoke("open",owner.machine(),"shared","r")[0];int disposableId=handle(disposable);((Value)disposable).dispose(owner.machine());require(fs.getHandle(disposableId)==null,"Actual persisted HandleValue did not dispose its owned descriptor");
            results.add("PASS filesystem.callbacks real Case contexts preserve binary data and registered owner/pointer state, reject foreign handles, close only the stopped owner, enforce per-computer limits, return EOF nil and dispose real handle userdata");
        }
        ManagedEnvironment mount(ItemStack stack)throws Exception{
            var driver=Driver.driverFor(stack,owner.getClass());require(driver!=null,"Actual Case rejected its disk driver");ManagedEnvironment environment=driver.createEnvironment(stack,owner);
            require(environment!=null&&environment.node() instanceof Component,"Actual disk driver created no component");environment.load(driver.dataTag(stack),level.registryAccess());nodes.add(environment.node());owner.machine().node().connect(environment.node());return environment;
        }
        void rawMedia()throws Exception{
            ItemStack disk=item("hdd1");CustomData.update(DataComponents.CUSTOM_DATA,disk,tag->tag.putBoolean("oc:unmanaged",true));ManagedEnvironment environment=mount(disk);Component drive=(Component)environment.node();
            require("drive".equals(drive.name()),"Unmanaged HDD did not mount its original drive component");require(((Number)drive.invoke("getSectorSize",owner.machine())[0]).intValue()==512,"Raw sector size changed");
            drive.invoke("writeByte",owner.machine(),512,85);drive.invoke("writeSector",owner.machine(),1,new byte[]{10,20});drive.invoke("writeByte",owner.machine(),3,255);
            require(((Number)drive.invoke("readByte",owner.machine(),3)[0]).intValue()==-1,"Raw bytes must retain their original signed return");
            byte[] sector=(byte[])drive.invoke("readSector",owner.machine(),1)[0];require(sector.length==512&&sector[0]==10&&sector[1]==20&&sector[2]==(byte)255&&sector[511]==85,"Raw sector partial writes changed unrelated bytes");
            String label=(String)drive.invoke("setLabel",owner.machine(),"0123456789abcdefghijkl")[0];require("0123456789abcdef".equals(label),"Mutable raw label did not truncate to sixteen characters");
            CompoundTag saved=new CompoundTag();environment.save(saved,level.registryAccess());Driver.driverFor(disk).saveDataTag(disk,saved);String address=drive.address();drive.remove();
            ManagedEnvironment remounted=mount(disk);Component restoredDrive=(Component)remounted.node();require(address.equals(restoredDrive.address())&&Arrays.equals(sector,(byte[])restoredDrive.invoke("readSector",owner.machine(),1)[0])&&label.equals(restoredDrive.invoke("getLabel",owner.machine())[0]),"Actual binary disk save/remount lost data, address or label");restoredDrive.remove();
            CustomData.update(DataComponents.CUSTOM_DATA,disk,tag->tag.putString("oc:lock","fixture-owner"));Component locked=(Component)mount(disk).node();
            rejected(()->locked.invoke("writeByte",owner.machine(),1,1),"drive is read only");rejected(()->locked.invoke("setLabel",owner.machine(),"changed"),"drive is read only");
            require(Arrays.equals(sector,(byte[])locked.invoke("readSector",owner.machine(),1)[0]),"Locked raw drive changed readable contents");
            results.add("PASS drive.raw actual HDD driver preserves partial 512-byte sectors, signed bytes, sixteen-character label and node identity through binary save/remount; locked media remains readable and rejects writes/labels");
        }
        void fail(Throwable error){results.add("FAIL storage.filesystem "+error);LOGGER.error("STORAGE_FILESYSTEM failed",error);}
        void cleanup(){for(Node node:nodes)node.remove();nodes.clear();for(FileSystem fs:volumes)fs.close();volumes.clear();if(owner!=null)owner.machine().stop();if(other!=null)other.machine().stop();var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries){if(level.getBlockEntity(entry.getKey()) instanceof Container inventory)inventory.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}restored.clear();for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);forced.clear();}
    }
}
