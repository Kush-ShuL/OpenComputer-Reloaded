package ocfixture;

import com.mojang.authlib.GameProfile;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.fs.Handle;
import li.cil.oc.api.fs.Mode;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded reproduction of native FS activity versus main-thread keyboard/network dispatch. */
public final class DiskConcurrencyContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-disk-concurrency");
    private static final byte[] BYTES="native external-drive bytes\n".getBytes(StandardCharsets.UTF_8);
    private static Run active;
    private DiskConcurrencyContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results)throws Exception{Run run=new Run(level,position,results);active=run;run.start();}
    public static boolean isRunning(){return active!=null;}
    public static void tick(){Run run=active;if(run==null)return;try{if(!run.tick())return;}catch(Throwable error){run.fail(error);return;}active=null;run.cleanup();}
    public static void close(){Run run=active;active=null;if(run!=null)run.cleanup();}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static ItemStack item(String key){var descriptor=li.cil.oc.api.Items.get(key);require(descriptor!=null,"Missing actual concurrency device "+key);return descriptor.createItemStack(1);}
    private static final class Run {
        final ServerLevel level;
        final BlockPos position;
        final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();
        final Probe probe=new Probe();
        GateFileSystem gate;
        Case hardware;
        Container drive;
        Environment driveEnvironment,keyboard;
        FakePlayer actor;
        int ticks,phase,phaseStart,inputs;
        final AtomicInteger factoryCalls=new AtomicInteger();
        String mediaState="not queried";
        boolean failed;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;}
        void place(BlockPos pos,BlockState state){require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Concurrency fixture will not overwrite occupied cell "+pos);restored.put(pos,level.getBlockState(pos));require(level.setBlock(pos,state,3),"Could not place concurrency fixture block");}
        void start()throws Exception{
            place(position,item("case3").getItem() instanceof net.minecraft.world.item.BlockItem block?block.getBlock().defaultBlockState():Blocks.AIR.defaultBlockState());
            place(position.east(),li.cil.oc.api.Items.get("diskdrive").block().defaultBlockState());place(position.east(2).below(),Blocks.STONE.defaultBlockState());place(position.east(2),li.cil.oc.api.Items.get("keyboard").block().defaultBlockState());
            require(level.getBlockEntity(position) instanceof Case,"Actual concurrency case has no SDK host");hardware=(Case)level.getBlockEntity(position);
            BlockEntity disk=level.getBlockEntity(position.east());require(disk instanceof Container&&disk instanceof Environment,"Actual external disk drive has no inventory/network");drive=(Container)disk;driveEnvironment=(Environment)disk;
            require(level.getBlockEntity(position.east(2)) instanceof Environment,"Actual keyboard did not create its environment");keyboard=(Environment)level.getBlockEntity(position.east(2));
            ItemStack cpu=item("cpu3");var cpuDriver=Driver.driverFor(cpu,hardware.getClass());require(cpuDriver instanceof MutableProcessor,"Real CPU is not configurable");var architecture=li.cil.oc.api.Machine.architectures().stream().filter(type->li.cil.oc.api.Machine.getArchitectureName(type).equals("Lua 5.3")).findFirst().orElseThrow(()->new AssertionError("Native Lua 5.3 unavailable"));((MutableProcessor)cpuDriver).setArchitecture(cpu,architecture);install(cpu);install(item("ram6"));install(li.cil.oc.api.Items.registerEEPROM("External drive concurrency",BIOS.getBytes(StandardCharsets.UTF_8),new byte[0],false));
            var actual=li.cil.oc.api.FileSystem.fromSaveDirectory(UUID.randomUUID().toString(),65536,false);require(actual!=null,"Real unbuffered save-directory filesystem was unavailable");int file=actual.open("bytes",Mode.Write);actual.getHandle(file).write(BYTES);actual.getHandle(file).close();gate=new GateFileSystem(this,actual);
            ItemStack floppy=li.cil.oc.api.Items.registerFloppy("fixture-drive-concurrency",DyeColor.WHITE,()->{factoryCalls.incrementAndGet();return gate;},false);require(drive.canPlaceItem(0,floppy),"Actual external drive rejected the SDK program disk");drive.setItem(0,floppy);
            Network.joinOrCreateNetwork((BlockEntity)hardware);Network.joinOrCreateNetwork(disk);Network.joinOrCreateNetwork((BlockEntity)keyboard);Network.joinNewNetwork(probe.node());probe.node().connect(hardware.node());hardware.node().connect(driveEnvironment.node());hardware.node().connect(keyboard.node());probe.node().changeBuffer(1_000_000);
            actor=new FakePlayer(level,new GameProfile(UUID.fromString("a32319c9-4cf3-4d49-8de4-851e417dad19"),"OC_DiskFixture"));actor.setPos(position.getX()+2.5,position.getY(),position.getZ()+.5);phaseStart=0;
        }
        void install(ItemStack stack){for(int slot=0;slot<hardware.getContainerSize();slot++)if(hardware.getItem(slot).isEmpty()&&hardware.canPlaceItem(slot,stack)){hardware.setItem(slot,stack);return;}throw new AssertionError("Actual case has no slot for "+stack);}
        void keyInput(){require(level.getServer().isSameThread(),"Keyboard input left the actual server thread");probe.node().sendToAddress(keyboard.node().address(),"keyboard.keyDown",actor,'x',45);probe.node().sendToAddress(keyboard.node().address(),"keyboard.keyUp",actor,'x',45);inputs++;}
        boolean tick()throws Exception{
            ticks++;
            if(failed){if(ticks-phaseStart<3)return false;return true;}
            require(ticks<400,"External drive native concurrency did not complete in 400 ticks; "+status());
            probe.node().changeBuffer(1_000_000);
            if(phase==0){Object[] media=((Component)driveEnvironment.node()).invoke("media",probe);mediaState=java.util.Arrays.deepToString(media);if(ticks==1||ticks==20)LOGGER.info("DRIVE_CONCURRENCY waiting-media {}",status());if(media==null||media.length!=1||!(media[0] instanceof String)){require(ticks<80,"Actual external drive did not mount its medium; "+status());return false;}DiskMediaSyncContract.verify((BlockEntity)drive,level.registryAccess(),results);require(hardware.machine().start(),"Actual concurrency VM did not boot; "+status());phase=1;phaseStart=ticks;LOGGER.info("DRIVE_CONCURRENCY started-native {}",status());return false;}
            require(hardware.machine().lastError()==null,"Native concurrency BIOS failed; "+status());
            if(phase==1){
                if(gate.entered.get()!=1){if(ticks-phaseStart==40)LOGGER.info("DRIVE_CONCURRENCY waiting-worker {}",status());require(ticks-phaseStart<100,"Native firmware did not reach a direct worker read; "+status());return false;}int before=gate.entryTicket;gate.release[0].countDown();long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(500);
                // Deliberately do not run server tasks: a correct direct callback needs no chunk future.
                while(probe.reads.get()<=before&&System.nanoTime()<deadline)LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                if(probe.reads.get()<=before){Thread worker=gate.worker;StringBuilder trace=new StringBuilder();if(worker!=null)for(StackTraceElement frame:worker.getStackTrace())trace.append(frame).append(" > ");throw new AssertionError("Direct external-drive read did not finish in 500ms without pumping server tasks; actual worker="+trace);}
                require(gate.workerReads.get()>0,"Actual Lua filesystem read never executed on a VM worker");results.add("PASS drive.concurrent.direct native external-drive read completes while main thread waits without processing chunk tasks");probe.next.set(2);phase=2;phaseStart=ticks;return false;
            }
            if(phase==2){
                if(gate.entered.get()!=2)return false;require(gate.holding,"Main thread missed the bounded filesystem-lock window");long before=System.nanoTime();try{keyInput();}finally{gate.release[1].countDown();}long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before);
                require(elapsed<100,"Keyboard/network messages waited "+elapsed+"ms for an unrelated filesystem lock");results.add("PASS drive.concurrent.keyboard real keyboard keyDown/keyUp bypass unrelated held FS lock in "+elapsed+"ms");probe.next.set(3);phase=3;phaseStart=ticks;return false;
            }
            keyInput();
            if(probe.finished.get()==0)return false;
            require(probe.finished.get()==80&&probe.keys.get()>0&&gate.workerReads.get()>=2&&gate.workerWrites.get()>0,"Actual VM I/O or keyboard signals did not advance together");Object[] media=((Component)driveEnvironment.node()).invoke("media",probe);Node fs=driveEnvironment.node().network().node((String)media[0]);Object[] size=((Component)fs).invoke("size",probe,"writes");require(size!=null&&((Number)size[0]).intValue()==10,"Concurrent Lua writes did not reach the actual external disk");results.add("PASS drive.concurrent.progress native Lua completed 80 real external-drive reads and 10 writes with "+probe.keys.get()+" key_up signals; worker reads="+gate.workerReads.get()+", writes="+gate.workerWrites.get()+", main inputs="+inputs);return true;
        }
        String status(){var machine=hardware.machine();return "phase="+phase+", phaseTicks="+(ticks-phaseStart)+", ticks="+ticks+", running="+machine.isRunning()+", paused="+machine.isPaused()+", error="+machine.lastError()+", architecture="+(machine.architecture()==null?"none":li.cil.oc.api.Machine.getArchitectureName(machine.architecture().getClass()))+", uptime="+machine.upTime()+", cpu="+machine.cpuTime()+", components="+machine.components()+", media="+mediaState+", factory="+factoryCalls.get()+", probeCalls="+probe.phaseCalls.get()+", afterRead="+probe.reads.get()+", gate="+(gate==null?"none":gate.entered.get()+"/holding="+gate.holding+"/open="+gate.opens.get()+"/mainRead="+gate.mainReads.get()+"/workerRead="+gate.workerReads.get());}
        void fail(Throwable error){if(failed)return;failed=true;phaseStart=ticks;probe.next.set(-1);if(gate!=null)for(CountDownLatch latch:gate.release)latch.countDown();if(hardware!=null)hardware.machine().stop();results.add("FAIL drive.concurrent: "+error+"; "+status());LOGGER.error("DRIVE_CONCURRENCY FAIL {}; returning to the main loop before deferred cleanup",status(),error);}
        void cleanup(){if(gate!=null)for(CountDownLatch latch:gate.release)latch.countDown();if(hardware!=null)hardware.machine().stop();probe.node().remove();var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries){if(level.getBlockEntity(entry.getKey()) instanceof Container inventory)inventory.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}restored.clear();}
    }
    public static final class Probe extends AbstractManagedEnvironment implements Context {
        final AtomicInteger next=new AtomicInteger(1),reads=new AtomicInteger(),finished=new AtomicInteger(),keys=new AtomicInteger(),phaseCalls=new AtomicInteger();
        Probe(){setNode(Network.newNode(this,Visibility.Network).withComponent("drive_concurrency_probe").withConnector(1_000_000).create());}
        @Override public Connector node(){return (Connector)super.node();}
        @Callback(direct=true,limit=64)public Object[] phase(Context context,Arguments args){phaseCalls.incrementAndGet();return new Object[]{next.get()};}
        @Callback(direct=true,limit=64)public Object[] readDone(Context context,Arguments args){reads.incrementAndGet();return new Object[]{true};}
        @Callback(direct=true,limit=64)public Object[] done(Context context,Arguments args){keys.set(args.checkInteger(1));finished.set(args.checkInteger(0));return new Object[]{true};}
        @Override public boolean canInteract(String name){return true;}
        @Override public boolean isRunning(){return true;}
        @Override public boolean isPaused(){return false;}
        @Override public boolean start(){return false;}
        @Override public boolean stop(){return false;}
        @Override public boolean pause(double seconds){return false;}
        @Override public void consumeCallBudget(double cost){}
        @Override public boolean signal(String name,Object...args){return false;}
    }
    /** SDK filesystem decorator controls a bounded read window over real unbuffered world storage. */
    private static final class GateFileSystem implements li.cil.oc.api.fs.FileSystem {
        final Run run;
        final li.cil.oc.api.fs.FileSystem inner;
        final CountDownLatch[] release={new CountDownLatch(1),new CountDownLatch(1)};
        final AtomicInteger entered=new AtomicInteger(),workerReads=new AtomicInteger(),workerWrites=new AtomicInteger(),mainReads=new AtomicInteger(),opens=new AtomicInteger();
        volatile Thread worker;
        volatile int entryTicket;
        volatile boolean holding;
        GateFileSystem(Run run,li.cil.oc.api.fs.FileSystem inner){this.run=run;this.inner=inner;}
        public boolean isReadOnly(){return inner.isReadOnly();}public long spaceTotal(){return inner.spaceTotal();}public long spaceUsed(){return inner.spaceUsed();}public boolean exists(String path){return inner.exists(path);}public long size(String path){return inner.size(path);}public boolean isDirectory(String path){return inner.isDirectory(path);}public long lastModified(String path){return inner.lastModified(path);}public String[] list(String path){return inner.list(path);}public boolean delete(String path){return inner.delete(path);}public boolean makeDirectory(String path){return inner.makeDirectory(path);}public boolean rename(String from,String to)throws FileNotFoundException{return inner.rename(from,to);}public boolean setLastModified(String path,long time){return inner.setLastModified(path,time);}public int open(String path,Mode mode)throws FileNotFoundException{opens.incrementAndGet();return inner.open(path,mode);}
        public Handle getHandle(int id){Handle handle=inner.getHandle(id);if(handle==null)return null;return new Handle(){public long position(){return handle.position();}public long length(){return handle.length();}public void close(){handle.close();}public long seek(long offset)throws IOException{return handle.seek(offset);}public void write(byte[] bytes)throws IOException{if(!run.level.getServer().isSameThread())workerWrites.incrementAndGet();handle.write(bytes);}public int read(byte[] bytes)throws IOException{
            if(!run.level.getServer().isSameThread()){workerReads.incrementAndGet();int stage=run.probe.next.get();if(stage>0&&stage<3&&entered.get()==stage-1){worker=Thread.currentThread();entryTicket=run.probe.reads.get();holding=true;entered.set(stage);try{require(release[stage-1].await(stage==1?5:300,stage==1?TimeUnit.SECONDS:TimeUnit.MILLISECONDS),stage==1?"Main thread never released direct-read window":"Keyboard/network message held the main thread until the FS read window expired");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException(error);}finally{holding=false;}}}else mainReads.incrementAndGet();return handle.read(bytes);
        }};}
        public void close(){inner.close();}public void load(CompoundTag tag){inner.load(tag);}public void save(CompoundTag tag){inner.save(tag);}@Override public void load(CompoundTag tag,HolderLookup.Provider registries){inner.load(tag,registries);}@Override public void save(CompoundTag tag,HolderLookup.Provider registries){inner.save(tag,registries);}
    }
    private static final String BIOS="""
        local p=component.proxy(assert(component.list("drive_concurrency_probe",true)()))
        local d=component.proxy(assert(component.list("disk_drive",true)()))
        local fs=component.proxy(assert(d.media()))
        local reader=assert(fs.open("bytes","r"))
        local keys=0
        local function signal(timeout)
          local event=computer.pullSignal(timeout)
          if event=="key_up" then keys=keys+1 end
        end
        local function freshBudget()
          local deadline=computer.uptime()+0.05
          repeat signal(0.05) until computer.uptime()>=deadline
        end
        while true do
          local phase=p.phase()
          if phase<0 then computer.shutdown() return end
          if phase>=3 then break end
          assert(fs.seek(reader,"set",0)==0)
          -- seek and read each cost one direct-call budget unit; T3 provides 1.5.
          -- Crossing an actual world tick keeps the read on the native executor.
          freshBudget()
          assert(fs.read(reader,100)=="native external-drive bytes\\n")
          assert(p.readDone()) signal(0.01)
        end
        for i=1,80 do
          assert(fs.seek(reader,"set",0)==0)
          freshBudget()
          assert(fs.read(reader,100)=="native external-drive bytes\\n")
          if i%8==0 then freshBudget() local w=assert(fs.open("writes","a")) assert(fs.write(w,"x")) fs.close(w) end
          signal(0.01)
        end
        fs.close(reader) assert(p.done(80,keys)) computer.shutdown()
        """;
}
