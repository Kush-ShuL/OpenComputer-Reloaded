package ocfixture;

import com.mojang.authlib.GameProfile;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.prefab.AbstractValue;
import li.cil.oc.api.prefab.ItemStackArrayValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A real moving robot, its internal host and its addon card survive two separate server JVMs. */
public final class HostPersistenceContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-host-persistence");
    private static final String PROCESS=UUID.randomUUID().toString(),MARKER="ocfixture:host_probe";
    private static final AtomicInteger SAVED_VALUES=new AtomicInteger(),LOADED_VALUES=new AtomicInteger();
    private static Run active;
    private HostPersistenceContract(){}
    public static boolean enabled(){return System.getProperty("ocfixture.hostStage")!=null;}
    public static void register(){Driver.add(new ProbeDriver());}
    public static void start(MinecraftServer server){try{active=new Run(server);active.start();}catch(Throwable error){if(active!=null)active.fail(error);else throw new IllegalStateException("Host persistence setup failed",error);}}
    public static void tick(){Run run=active;if(run==null||run.finished)return;try{run.tick();}catch(Throwable error){run.fail(error);}}
    public static void stopped(){Run run=active;if(run==null)return;if(run.pendingSuccess){run.report("PASS");LOGGER.info("HOST_PERSISTENCE_COMPLETE host={} stage={} architecture={} status=PASS",run.hostName,run.stage,run.architecture);}else if(!run.finished)run.fail(new AssertionError("Server stopped before host proof completed"));active=null;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing real host hardware "+name);return descriptor.createItemStack(1);}
    private static final class Run {
        final MinecraftServer server;
        final ServerLevel level;
        final String hostName=System.getProperty("ocfixture.host","robot"),stage=System.getProperty("ocfixture.hostStage"),runId=System.getProperty("ocfixture.hostRunId"),architecture=System.getProperty("ocfixture.hostArchitecture","Lua 5.3");
        final boolean luaJ=architecture.equals("LuaJ");
        final Path directory=Path.of(System.getProperty("ocfixture.hostDirectory")).toAbsolutePath().normalize();
        final Properties manifest=new Properties();
        final List<String> evidence=new ArrayList<>();
        final BlockPos initialPosition=new BlockPos(4096,96,4096);
        BlockPos position=initialPosition;
        Robot robot;
        BlockEntity proxy;
        Probe probe;
        int ticks,phaseTicks,probeSlot=-1;
        boolean ready,finished,pendingSuccess;
        Run(MinecraftServer server){this.server=server;level=server.overworld();}
        void start()throws Exception{
            require(hostName.equals("robot"),"This first host scenario implements robot only; other hosts have no asserted coverage");
            require(stage.equals("prepare")||stage.equals("resume"),"Host stage must be prepare or resume");
            require(directory.equals(Path.of("").toAbsolutePath().normalize())&&directory.getParent().getFileName().toString().equals(hostName)&&directory.getParent().getParent().getFileName().toString().equals("hosts")&&directory.getFileName().toString().equals(runId),"Refusing a non-fixture host directory");
            require(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(directory.resolve("ocfixture-host-world")),"Refusing a different Minecraft world");
            if(stage.equals("prepare"))prepare();else resume();
            evidence.add("process.pid="+ProcessHandle.current().pid());evidence.add("process.current="+PROCESS);
            LOGGER.info("HOST_PERSISTENCE host={} stage={} architecture={} run={}",hostName,stage,architecture,runId);
        }
        void prepare()throws Exception{
            require(!Files.exists(directory.resolve("checkpoint.properties")),"Prepare cannot overwrite a checkpoint");
            force();level.getChunkAt(position);require(level.isEmptyBlock(position)&&level.isEmptyBlock(position.above()),"Robot fixture cells are occupied");
            require(CreativeTab.instance!=null,"Real configured creative devices are unavailable");
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(),true,level.registryAccess()));
            ItemStack configured=null;
            for(ItemStack stack:CreativeTab.instance.getDisplayItems())if(stack.is(item("robot").getItem())&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("oc:tier")==3){configured=stack.copy();break;}
            require(configured!=null&&configured.getItem() instanceof BlockItem,"Creative tab did not supply the configured robot");
            // Configure hardware in the assembled item before placement, using only SDK processors,
            // EEPROM creation and Minecraft's public item/NBT serialization. No running VM is edited.
            CompoundTag data=configured.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();ListTag parts=data.getList("oc:components",Tag.TAG_COMPOUND);ListTag updated=new ListTag();boolean cpu=false,flash=false;
            Class<? extends Architecture> selected=li.cil.oc.api.Machine.architectures().stream().filter(type->li.cil.oc.api.Machine.getArchitectureName(type).equals(architecture)).findFirst().orElseThrow(()->new AssertionError("Architecture unavailable: "+architecture));
            for(Tag value:parts){ItemStack part=ItemStack.parseOptional(level.registryAccess(),(CompoundTag)value);var driver=Driver.driverFor(part);
                if(driver instanceof MutableProcessor processor&&li.cil.oc.api.driver.item.Slot.CPU.equals(driver.slot(part))){processor.setArchitecture(part,selected);cpu=true;}
                if(driver!=null&&"eeprom".equals(driver.slot(part))){part=li.cil.oc.api.Items.registerEEPROM("Robot cold fixture",BIOS.getBytes(StandardCharsets.UTF_8),new byte[0],false);flash=true;}
                updated.add(part.saveOptional(level.registryAccess()));
            }
            require(cpu&&flash,"Configured robot has no real mutable CPU/EEPROM hardware");data.put("oc:components",updated);configured.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            FakePlayer actor=new FakePlayer(level,new GameProfile(UUID.fromString("a32319c9-4cf3-4d49-8de4-851e417dad18"),"OC_HostFixture"));actor.getAbilities().instabuild=true;actor.setPos(position.getX()+.5,position.getY(),position.getZ()-2);actor.setItemInHand(InteractionHand.MAIN_HAND,configured);
            require(((BlockItem)configured.getItem()).place(new BlockPlaceContext(actor,InteractionHand.MAIN_HAND,configured,new BlockHitResult(Vec3.atCenterOf(position),Direction.UP,position,false))).consumesAction(),"Configured robot failed its real BlockItem.place path");
            attach();ItemStack card=new ItemStack(Items.PAPER);CustomData.update(DataComponents.CUSTOM_DATA,card,tag->tag.putBoolean(MARKER,true));Container equipment=robot.equipmentInventory();
            for(int slot=1;slot<equipment.getContainerSize();slot++)if(robot.canPlaceItem(slot,card)){probeSlot=slot;break;}
            require(probeSlot>=0,"The configured real robot has no hot card bay for the external SDK probe");equipment.setItem(probeSlot,card);
            ItemStack inventory=new ItemStack(Items.DIAMOND,4);inventory.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("host-inventory"));CustomData.update(DataComponents.CUSTOM_DATA,inventory,tag->tag.putString("host_fixture","robot-real-state"));require(robot.mainInventory().getContainerSize()>2,"Configured robot has no real inventory");robot.mainInventory().setItem(2,inventory);robot.setSelectedSlot(2);
            manifest.setProperty("robot.name",robot.name());manifest.setProperty("robot.owner_name",robot.ownerName());manifest.setProperty("robot.owner_uuid",robot.ownerUUID().toString());manifest.setProperty("robot.selected_slot",Integer.toString(robot.selectedSlot()));manifest.setProperty("probe_slot",Integer.toString(probeSlot));
            evidence.add("hardware=real configured robot, original CPU/RAM/upgrades/HDD/containers, public SDK EEPROM program and hot-swapped addon card");
        }
        void resume()throws Exception{
            Path prepared=directory.resolve("prepare-results.txt");require(Files.isRegularFile(prepared)&&Files.readAllLines(prepared,StandardCharsets.UTF_8).getFirst().equals("status=PASS"),"Prepare did not finish normal shutdown");
            try(InputStream input=Files.newInputStream(directory.resolve("checkpoint.properties"))){manifest.load(input);}
            require(runId.equals(manifest.getProperty("run_id"))&&hostName.equals(manifest.getProperty("host"))&&architecture.equals(manifest.getProperty("architecture")),"Checkpoint selection differs");require("prepared".equals(manifest.getProperty("phase")),"Checkpoint is not prepared or has already been consumed");require(!PROCESS.equals(manifest.getProperty("process")),"Resume must execute in a different Java process");require(directory.resolve("ocfixture-host-world").toString().equals(manifest.getProperty("world")),"Checkpoint belongs to a different world");
            position=BlockPos.of(Long.parseLong(manifest.getProperty("position")));probeSlot=Integer.parseInt(manifest.getProperty("probe_slot"));force();level.getChunkAt(position);attach();evidence.add("process.prepare="+manifest.getProperty("process"));
        }
        void force(){level.setChunkForced(position.getX()>>4,position.getZ()>>4,true);}
        void attach(){proxy=level.getBlockEntity(position);require(proxy instanceof Robot,"World did not load the actual Robot proxy");robot=(Robot)proxy;require(robot.machine()!=null&&robot.machine().host() instanceof Robot,"Actual robot did not retain its internal SDK Robot machine host");}
        boolean connected(){Machine machine=robot.machine();if(machine.node().network()==null)return false;var environment=robot.getComponentInSlot(probeSlot);if(!(environment instanceof Probe actual))return false;probe=actual;return probe.node().network()==machine.node().network()&&robot.componentSlot(probe.node().address())==probeSlot;}
        void tick()throws Exception{
            require(++ticks<=1000,"Robot host stage exceeded 1000 ticks");phaseTicks++;
            if(!ready){
                if(!connected()){require(phaseTicks<100,"Real robot's restored component lifecycle did not initialize");return;}
                if(stage.equals("prepare")){require(robot.machine().componentCount()<=robot.machine().maxComponents(),"Robot CPU component budget exceeded");require(robot.machine().start(),"Actual robot failed boot: "+robot.machine().lastError());}
                else{verifyAddresses();verifyRobot();require(probe.boots.get()==1||luaJ&&probe.boots.get()==2,"Saved robot addon card lost its BIOS boot counter");require(robot.machine().isRunning()||luaJ&&probe.completed.get(),"World-loaded robot VM was not active: "+robot.machine().lastError());if(!probe.completed.get()&&robot.machine().isPaused())require(robot.machine().start(),"Could not release the intentionally paused saved robot checkpoint");}
                ready=true;phaseTicks=0;return;
            }
            require(robot.machine().lastError()==null,"Real robot VM failed: "+robot.machine().lastError());require(robot.machine().isRunning()||probe.completed.get(),"Robot stopped before completing its proof");
            if(stage.equals("prepare")&&probe.requests.get()==1&&robot.machine().isPaused()){
                Machine stable=robot.machine();position=BlockPos.containing(stable.host().xPosition(),stable.host().yPosition(),stable.host().zPosition());require(position.equals(initialPosition.above()),"Actual Lua robot.move did not transfer the proxy upward");attach();require(robot.machine()==stable,"Moving the real robot replaced its running VM");require(probe.boots.get()==1,"Robot BIOS restarted before checkpoint");
                require(stable.signal("host_cold_resume",2718,"robot-queued-signal"),"Could not queue the robot's cold-start signal");captureAddresses();saveWorld();if(!luaJ)require(SAVED_VALUES.get()>0,"Real robot world save did not serialize native live userdata");
                manifest.setProperty("run_id",runId);manifest.setProperty("host",hostName);manifest.setProperty("stage","prepared");manifest.setProperty("phase","prepared");manifest.setProperty("architecture",architecture);manifest.setProperty("process",PROCESS);manifest.setProperty("position",Long.toString(position.asLong()));manifest.setProperty("world",directory.resolve("ocfixture-host-world").toString());saveManifest();evidence.add("robot.move=real Lua move(1) transferred the same machine to "+position.toShortString());evidence.add("checkpoint=paused native VM in the moved robot's real world save");evidence.add("value.save_calls="+SAVED_VALUES.get());success();
            }else if(stage.equals("resume")&&probe.completed.get()){
                require(probe.boots.get()==(luaJ?2:1),"Robot BIOS boot count differs from the selected architecture contract");require(luaJ?LOADED_VALUES.get()==0:LOADED_VALUES.get()>0,"Robot Value loading did not match the selected native/reboot semantics");verifyRobot();evidence.add("proof="+(luaJ?"real LuaJ robot rebooted BIOS once; HDD/EEPROM and robot state persisted":"moved real robot resumed native locals, nested coroutine, userdata ItemStack/iterator, open tmpfs/HDD descriptor offsets, EEPROM, component identities and queued signal"));evidence.add("boots="+probe.boots.get());evidence.add("value.load_calls="+LOADED_VALUES.get());manifest.setProperty("phase","complete");saveWorld();saveManifest();success();
            }
        }
        void captureAddresses(){
            manifest.setProperty("machine",robot.machine().node().address());manifest.setProperty("tmp",robot.machine().tmpAddress());manifest.setProperty("probe",probe.node().address());manifest.setProperty("hdd",probe.disk);manifest.setProperty("eeprom",probe.flash);manifest.setProperty("control",probe.control);
            robot.machine().components().forEach((address,type)->manifest.setProperty("component."+address,type));require(probe.disk!=null&&probe.flash!=null&&probe.control!=null,"Robot checkpoint omitted its actual component addresses");
        }
        void verifyAddresses(){
            require(manifest.getProperty("machine").equals(robot.machine().node().address())&&manifest.getProperty("tmp").equals(robot.machine().tmpAddress())&&manifest.getProperty("probe").equals(probe.node().address()),"Robot machine/tmpfs/hot-card address changed");
            require(robot.componentSlot(manifest.getProperty("hdd"))>=0&&robot.componentSlot(manifest.getProperty("eeprom"))>=0,"Saved HDD/EEPROM are not actual internal robot components");
            for(String key:manifest.stringPropertyNames())if(key.startsWith("component."))require(manifest.getProperty(key).equals(robot.machine().components().get(key.substring("component.".length()))),"Robot lost a saved internal component identity: "+key);
        }
        void verifyRobot(){
            require(manifest.getProperty("robot.name").equals(robot.name())&&manifest.getProperty("robot.owner_name").equals(robot.ownerName())&&manifest.getProperty("robot.owner_uuid").equals(robot.ownerUUID().toString()),"Real robot identity/ownership changed after cold load");require(robot.selectedSlot()==Integer.parseInt(manifest.getProperty("robot.selected_slot")),"Real robot selected inventory slot was lost");ItemStack stack=robot.mainInventory().getItem(2);require(stack.is(Items.DIAMOND)&&stack.getCount()==4&&stack.getHoverName().getString().equals("host-inventory")&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("host_fixture").equals("robot-real-state"),"Real robot inventory ItemStack/components were not restored");
        }
        void saveWorld(){proxy.setChanged();require(server.saveEverything(false,true,true),"Minecraft did not save the actual moved robot world");}
        void saveManifest()throws Exception{try(OutputStream output=Files.newOutputStream(directory.resolve("checkpoint.properties"))){manifest.store(output,"Actual moving robot cold-process checkpoint");}}
        void success(){finished=true;pendingSuccess=true;report("PENDING_STOP");LOGGER.info("HOST_PERSISTENCE waiting for normal shutdown host={} stage={}",hostName,stage);server.halt(false);}
        void fail(Throwable error){if(finished&&!pendingSuccess)return;finished=true;pendingSuccess=false;evidence.add("failure="+error);LOGGER.error("HOST_PERSISTENCE FAIL host={} stage={}",hostName,stage,error);report("FAIL");server.halt(false);}
        void report(String status){List<String> lines=new ArrayList<>();lines.add("status="+status);lines.add("host="+hostName);lines.add("stage="+stage);lines.add("architecture="+architecture);lines.add("run_id="+runId);lines.addAll(evidence);try{Files.createDirectories(directory);Files.write(directory.resolve(stage+"-results.txt"),lines,StandardCharsets.UTF_8);}catch(Exception error){throw new IllegalStateException("Cannot write host persistence report",error);}}
    }
    /** A genuine third-party item component, saved by the robot's normal component inventory. */
    private static final class ProbeDriver extends li.cil.oc.api.prefab.DriverItem implements HostAware {
        @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.is(Items.PAPER)&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBoolean(MARKER);}
        @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&Robot.class.isAssignableFrom(host);}
        @Override public String slot(ItemStack stack){return li.cil.oc.api.driver.item.Slot.Card;}
        @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){return host instanceof Robot?new Probe(host):null;}
    }
    public static final class Probe extends AbstractManagedEnvironment {
        private final EnvironmentHost host;
        final AtomicInteger boots=new AtomicInteger(),requests=new AtomicInteger();
        final AtomicBoolean completed=new AtomicBoolean();
        volatile String disk,flash,control;
        Probe(EnvironmentHost host){this.host=host;setNode(Network.newNode(this,Visibility.Network).withComponent("host_persistence_probe",Visibility.Neighbors).create());}
        @Callback(direct=true,limit=64)public Object[] booted(Context context,Arguments args){host.markChanged();return new Object[]{boots.incrementAndGet()};}
        @Callback(direct=true,limit=64)public Object[] value(Context context,Arguments args){HostValue value=new HostValue();value.seed(context,args);return new Object[]{value};}
        @Callback(direct=true,limit=64)public Object[] checkpoint(Context context,Arguments args){require(context instanceof Machine machine&&machine.host() instanceof Robot,"Checkpoint did not originate in a real robot VM");require(boots.get()==1&&requests.get()==0,"Robot BIOS reached an unexpected initial checkpoint");disk=args.checkString(0);flash=args.checkString(1);control=args.checkString(2);require(context.pause(600),"Could not pause actual robot at a serializable Lua yield");requests.set(1);host.markChanged();return new Object[]{true};}
        void proof(Context context,Arguments args){Run run=java.util.Objects.requireNonNull(active);require(run.stage.equals("resume")&&context instanceof Machine machine&&machine.host() instanceof Robot,"Cold proof did not originate in the world-loaded real robot");require(args.checkString(0).equals(run.manifest.getProperty("machine"))&&args.checkString(1).equals(run.manifest.getProperty("tmp"))&&args.checkString(2).equals(run.manifest.getProperty("hdd"))&&args.checkString(3).equals(run.manifest.getProperty("eeprom"))&&args.checkString(4).equals(run.manifest.getProperty("control")),"Robot Lua locals lost their saved component identities");}
        @Callback(direct=true,limit=64)public Object[] verified(Context context,Arguments args){proof(context,args);require(!active.luaJ&&boots.get()==1&&args.checkInteger(5)==314159,"Native robot BIOS restarted or lost its retained local");completed.set(true);host.markChanged();return new Object[]{true};}
        @Callback(direct=true,limit=64)public Object[] rebooted(Context context,Arguments args){proof(context,args);require(active.luaJ&&boots.get()==2,"LuaJ robot did not execute exactly one genuine BIOS reboot");completed.set(true);host.markChanged();return new Object[]{true};}
        @Override public void load(CompoundTag tag){super.load(tag);boots.set(tag.getInt("boots"));requests.set(tag.getInt("requests"));completed.set(tag.getBoolean("complete"));disk=tag.getString("disk");flash=tag.getString("flash");control=tag.getString("control");}
        @Override public void save(CompoundTag tag){super.save(tag);tag.putInt("boots",boots.get());tag.putInt("requests",requests.get());tag.putBoolean("complete",completed.get());if(disk!=null)tag.putString("disk",disk);if(flash!=null)tag.putString("flash",flash);if(control!=null)tag.putString("control",control);}
    }
    public static final class HostValue extends AbstractValue {
        private int counter=-1;
        private ItemStack stack=ItemStack.EMPTY;
        private ItemStackArrayValue iterator;
        private HolderLookup.Provider loadedWith;
        public HostValue(){}
        void seed(Context context,Arguments args){require(context instanceof Machine machine&&machine.host() instanceof Robot,"Userdata was not created by a real robot");HolderLookup.Provider registries=((Machine)context).host().world().registryAccess();counter=43;stack=new ItemStack(Items.EMERALD,7);stack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("host-cold-value"));CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putString("host_payload","kept"));iterator=new ItemStackArrayValue(new ItemStack[]{ItemStack.EMPTY,new ItemStack(Items.DIAMOND,3)},registries);iterator.call(context,args);}
        @Override public void save(CompoundTag tag){throw new AssertionError("Robot userdata save lost the invoking registry provider");}
        @Override public void load(CompoundTag tag){throw new AssertionError("Robot userdata load lost the invoking registry provider");}
        @Override public void save(CompoundTag tag,HolderLookup.Provider registries){require(registries!=null&&iterator!=null&&!stack.isEmpty(),"Robot userdata payload was not initialized through its actual Lua callback");tag.putInt("counter",counter);tag.put("stack",stack.save(registries));CompoundTag state=new CompoundTag();iterator.save(state,registries);tag.put("iterator",state);SAVED_VALUES.incrementAndGet();}
        @Override public void load(CompoundTag tag,HolderLookup.Provider registries){require(registries!=null,"Robot userdata load has no registry provider");counter=tag.getInt("counter");stack=ItemStack.parseOptional(registries,tag.getCompound("stack"));iterator=new ItemStackArrayValue();iterator.load(tag.getCompound("iterator"),registries);loadedWith=registries;LOADED_VALUES.incrementAndGet();}
        @Callback(direct=true,limit=64)public Object[] snapshot(Context context,Arguments args){require(context instanceof Machine machine&&machine.host() instanceof Robot&&loadedWith==machine.host().world().registryAccess(),"Robot userdata was not reconstructed with its actual invoking world registry");return new Object[]{counter,stack.getCount(),stack.getHoverName().getString(),stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("host_payload")};}
        @Callback(direct=true,limit=64)public Object[] nextCount(Context context,Arguments args){Object[] next=iterator.call(context,args);return new Object[]{next==null?0:((ItemStack)next[0]).getCount()};}
    }
    private static final String BIOS="""
        local p=component.proxy(assert(component.list("host_persistence_probe",true)()))
        local boot=p.booted()
        local machine,tmp=computer.address(),computer.tmpAddress()
        local control=assert(component.list("robot",true)())
        local disk
        for address in component.list("filesystem",true) do
          if address~=tmp and not component.invoke(address,"isReadOnly") then disk=address break end
        end
        assert(disk,"real robot HDD absent")
        local flash=assert(component.list("eeprom",true)())
        local hdd=component.proxy(disk)
        if boot>1 then
          assert(computer.getArchitecture()=="LuaJ","native robot rebooted BIOS")
          local f=assert(hdd.open("host-marker","r"))
          assert(hdd.read(f,100)=="persistent-cold-marker") hdd.close(f)
          assert(component.invoke(flash,"getData")=="host-eeprom-data")
          assert(p.rebooted(machine,tmp,disk,flash,control))
          computer.shutdown()
        end
        local retained=314159
        local co=coroutine.create(function() local n=271828 coroutine.yield() return n+1 end)
        assert(coroutine.resume(co))
        local value=p.value()
        local fs=component.proxy(tmp)
        local w=assert(fs.open("host-tmp","w")) assert(fs.write(w,"abcdefghi")) fs.close(w)
        local reader=assert(fs.open("host-tmp","r")) assert(fs.read(reader,3)=="abc")
        w=assert(hdd.open("host-marker","w")) assert(hdd.write(w,"persistent-cold-marker")) hdd.close(w)
        local diskReader=assert(hdd.open("host-marker","r")) assert(hdd.read(diskReader,10)=="persistent")
        component.invoke(flash,"setData","host-eeprom-data")
        assert(component.invoke(control,"move",1),"real robot proxy move failed")
        assert(p.checkpoint(disk,flash,control))
        local event,number,text
        repeat event,number,text=computer.pullSignal() until event=="host_cold_resume"
        assert(number==2718 and text=="robot-queued-signal","robot queued signal lost")
        assert(retained==314159,"robot Lua local lost")
        local ok,n=coroutine.resume(co) assert(ok and n==271829,"robot nested coroutine lost")
        local count,size,label,tag=value.snapshot()
        assert(count==43 and size==7 and label=="host-cold-value" and tag=="kept","robot userdata ItemStack lost")
        assert(value.nextCount()==3,"robot userdata iterator cursor lost")
        assert(fs.read(reader,6)=="defghi","robot tmpfs open handle/offset lost")
        assert(hdd.read(diskReader,100)=="-cold-marker","robot HDD open handle/offset lost")
        assert(component.invoke(flash,"getData")=="host-eeprom-data")
        fs.close(reader) hdd.close(diskReader)
        assert(p.verified(machine,tmp,disk,flash,control,retained))
        computer.shutdown()
        """;
}
