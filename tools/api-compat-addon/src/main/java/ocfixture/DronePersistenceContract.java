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
import li.cil.oc.api.internal.Drone;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Component;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Actual entity storage, flight and embedded drone VM across separate server processes. */
public final class DronePersistenceContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-drone-persistence");
    private static final String PROCESS=UUID.randomUUID().toString(),MARKER="ocfixture:drone_probe";
    private static final AtomicInteger SAVED_VALUES=new AtomicInteger(),LOADED_VALUES=new AtomicInteger();
    private static Run active;
    private DronePersistenceContract(){}
    public static void register(){Driver.add(new ProbeDriver());NeoForge.EVENT_BUS.addListener(DronePersistenceContract::joined);}
    public static void start(MinecraftServer server){try{active=new Run(server);active.start();}catch(Throwable error){if(active!=null)active.fail(error);else throw new IllegalStateException("Drone persistence startup failed",error);}}
    public static void tick(){Run run=active;if(run==null||run.finished)return;try{run.tick();}catch(Throwable error){run.fail(error);}}
    public static void stopped(){Run run=active;if(run==null)return;if(run.pendingSuccess){run.report("PASS");LOGGER.info("HOST_PERSISTENCE_COMPLETE host=drone stage={} architecture={} status=PASS",run.stage,run.architecture);}else if(!run.finished)run.fail(new AssertionError("Server stopped before drone proof completed"));active=null;}
    private static void joined(EntityJoinLevelEvent event){
        Run run=active;if(run==null||event.getLevel()!=run.level||!(event.getEntity() instanceof Drone drone))return;
        if(run.stage.equals("prepare")&&event.getEntity().position().distanceToSqr(Vec3.atCenterOf(run.origin))<4){run.spawnCount++;run.entity=event.getEntity();run.drone=drone;run.entityId=event.getEntity().getUUID();run.spawnWasNew=!event.loadedFromDisk();return;}
        if(!run.stage.equals("resume")||run.entityId==null||!event.getEntity().getUUID().equals(run.entityId))return;
        // This hook may precede FULL chunk status. Read only already-deserialized entity fields.
        run.loadedPosition=event.getEntity().position();run.loadedVelocity=event.getEntity().getDeltaMovement();run.loadedTarget=drone.getTarget();run.loadedFromDisk=event.loadedFromDisk();run.joinObserved=true;
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static ItemStack item(String key){var info=li.cil.oc.api.Items.get(key);require(info!=null,"Missing real drone hardware "+key);return info.createItemStack(1);}
    private static void vector(Properties into,String key,Vec3 value){into.setProperty(key+".x",Double.toString(value.x));into.setProperty(key+".y",Double.toString(value.y));into.setProperty(key+".z",Double.toString(value.z));}
    private static Vec3 vector(Properties from,String key){return new Vec3(Double.parseDouble(from.getProperty(key+".x")),Double.parseDouble(from.getProperty(key+".y")),Double.parseDouble(from.getProperty(key+".z")));}
    private static final class Run {
        final MinecraftServer server;
        final ServerLevel level;
        final String stage=System.getProperty("ocfixture.hostStage"),runId=System.getProperty("ocfixture.hostRunId"),architecture=System.getProperty("ocfixture.hostArchitecture","Lua 5.3");
        final boolean luaJ=architecture.equals("LuaJ");
        final Path directory=Path.of(System.getProperty("ocfixture.hostDirectory")).toAbsolutePath().normalize();
        final Properties manifest=new Properties();
        final List<String> evidence=new ArrayList<>();
        final BlockPos origin=new BlockPos(4096,96,4096);
        UUID entityId;
        Entity entity;
        Drone drone;
        Probe probe;
        volatile Vec3 loadedPosition,loadedVelocity,loadedTarget;
        volatile boolean loadedFromDisk,joinObserved;
        int ticks,spawnCount;
        boolean ready,finished,pendingSuccess,spawnWasNew;
        Run(MinecraftServer server){this.server=server;level=server.overworld();}
        void start()throws Exception{
            require(stage.equals("prepare")||stage.equals("resume"),"Drone stage must be prepare or resume");require(directory.equals(Path.of("").toAbsolutePath().normalize())&&directory.getParent().getFileName().toString().equals("drone")&&directory.getParent().getParent().getFileName().toString().equals("hosts")&&directory.getFileName().toString().equals(runId),"Refusing a non-fixture drone directory");require(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(directory.resolve("ocfixture-host-world")),"Refusing a different Minecraft world");
            if(stage.equals("prepare"))prepare();else resume();evidence.add("process.pid="+ProcessHandle.current().pid());evidence.add("process.current="+PROCESS);evidence.add("hdd=not supported by original drone hardware; no HDD added by this fixture");
            LOGGER.info("HOST_PERSISTENCE host=drone stage={} architecture={} run={}",stage,architecture,runId);
        }
        void force(){level.setChunkForced(origin.getX()>>4,origin.getZ()>>4,true);level.getChunkAt(origin);}
        void prepare()throws Exception{
            require(!Files.exists(directory.resolve("checkpoint.properties")),"Prepare cannot overwrite a drone checkpoint");force();require(level.isEmptyBlock(origin.below())&&level.isEmptyBlock(origin),"Isolated drone cells are occupied");level.setBlock(origin.below(),Blocks.STONE.defaultBlockState(),3);
            require(CreativeTab.instance!=null,"Real configured creative drone is unavailable");CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(),true,level.registryAccess()));ItemStack configured=null;
            for(ItemStack stack:CreativeTab.instance.getDisplayItems())if(stack.is(item("drone").getItem())&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("oc:tier")==3){configured=stack.copy();break;}require(configured!=null,"Creative tab did not provide the original configured drone");
            CompoundTag data=configured.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();ListTag parts=new ListTag();boolean cpu=false,flash=false;int upgrades=0;
            var selected=li.cil.oc.api.Machine.architectures().stream().filter(type->li.cil.oc.api.Machine.getArchitectureName(type).equals(architecture)).findFirst().orElseThrow(()->new AssertionError("Requested drone architecture unavailable: "+architecture));
            for(Tag raw:data.getList("oc:components",Tag.TAG_COMPOUND)){ItemStack part=ItemStack.parseOptional(level.registryAccess(),(CompoundTag)raw);var driver=Driver.driverFor(part);if(driver!=null){String slot=driver.slot(part);require(!slot.equals(li.cil.oc.api.driver.item.Slot.HDD),"Configured original drone unexpectedly contains a forbidden HDD");if(slot.equals(li.cil.oc.api.driver.item.Slot.Upgrade))upgrades++;if(slot.equals(li.cil.oc.api.driver.item.Slot.CPU)&&driver instanceof MutableProcessor processor){processor.setArchitecture(part,selected);cpu=true;}if(slot.equals("eeprom")){part=eeprom();flash=true;}}parts.add(part.saveOptional(level.registryAccess()));}
            require(cpu&&upgrades<9,"Original creative drone has no CPU or free allowed upgrade slot");if(!flash)parts.add(eeprom().saveOptional(level.registryAccess()));ItemStack observation=new ItemStack(Items.PAPER);CustomData.update(DataComponents.CUSTOM_DATA,observation,tag->tag.putBoolean(MARKER,true));parts.add(observation.saveOptional(level.registryAccess()));data.put("oc:components",parts);configured.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            FakePlayer actor=new FakePlayer(level,new GameProfile(UUID.fromString("a32319c9-4cf3-4d49-8de4-851e417dad18"),"OC_HostFixture"));actor.getAbilities().instabuild=true;actor.setPos(origin.getX()+.5,origin.getY(),origin.getZ()-2);actor.setItemInHand(InteractionHand.MAIN_HAND,configured);require(configured.getItem().useOn(new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(origin.below()),Direction.UP,origin.below(),false))).consumesAction(),"Actual drone item placement failed");
            require(spawnCount==1&&spawnWasNew&&entity!=null,"Actual drone item did not produce exactly one fresh EntityJoinLevelEvent");require(drone.machine().host()==drone,"Drone machine is not owned by the actual entity");
            require(drone.mainInventory().getContainerSize()>3,"Configured real drone has no expected inventory capacity");ItemStack inventory=new ItemStack(Items.DIAMOND,4);inventory.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("drone-inventory"));CustomData.update(DataComponents.CUSTOM_DATA,inventory,tag->tag.putString("drone_fixture","kept"));drone.mainInventory().setItem(3,inventory);drone.setSelectedSlot(3);
            manifest.setProperty("entity.uuid",entityId.toString());manifest.setProperty("entity.type",BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());manifest.setProperty("drone.name",drone.name());manifest.setProperty("drone.owner_name",drone.ownerName());manifest.setProperty("drone.owner_uuid",drone.ownerUUID().toString());evidence.add("hardware=original configured flying drone; allowed EEPROM plus external SDK observation upgrade; no storage upgrade");
        }
        ItemStack eeprom(){return li.cil.oc.api.Items.registerEEPROM("Drone cold fixture",BIOS.getBytes(StandardCharsets.UTF_8),new byte[0],false);}
        void resume()throws Exception{
            Path prepared=directory.resolve("prepare-results.txt");require(Files.isRegularFile(prepared)&&Files.readAllLines(prepared,StandardCharsets.UTF_8).getFirst().equals("status=PASS"),"Drone prepare did not finish normal shutdown");try(InputStream input=Files.newInputStream(directory.resolve("checkpoint.properties"))){manifest.load(input);}
            require(runId.equals(manifest.getProperty("run_id"))&&"drone".equals(manifest.getProperty("host"))&&architecture.equals(manifest.getProperty("architecture"))&&"prepared".equals(manifest.getProperty("phase")),"Drone checkpoint selection differs or was consumed");require(!PROCESS.equals(manifest.getProperty("process")),"Drone resume must execute in another Java process");require(directory.resolve("ocfixture-host-world").toString().equals(manifest.getProperty("world")),"Drone checkpoint belongs to a different world");entityId=UUID.fromString(manifest.getProperty("entity.uuid"));force();evidence.add("process.prepare="+manifest.getProperty("process"));
        }
        boolean connected(){
            if(entity==null){Entity actual=level.getEntity(entityId);if(actual==null)return false;require(actual instanceof Drone,"Saved UUID loaded as a different entity type");entity=actual;drone=(Drone)actual;}
            if(drone.machine()==null||drone.machine().node().network()==null)return false;
            for(Node node:drone.machine().node().network().nodes())if(node.host() instanceof Probe observation){probe=observation;return drone.componentSlot(node.address())>=0;}
            return false;
        }
        void tick()throws Exception{
            require(++ticks<=1000,"Drone cold stage exceeded 1000 ticks");
            if(!ready){if(!connected()){require(ticks<100,"Actual drone entity/component loading did not finish; uuid="+entityId+", entity="+entity+", join="+joinObserved);return;}
                if(stage.equals("prepare")){require(drone.machine().componentCount()<=drone.machine().maxComponents(),"Actual drone exceeded its real CPU component budget");require(drone.machine().start(),"Actual drone VM failed boot: "+drone.machine().lastError());}
                else{verifyLoad();verifyIdentities();require(probe.boots.get()==1||luaJ&&probe.boots.get()==2,"Drone observation upgrade lost its saved boot count");require(drone.machine().isRunning()||luaJ&&probe.completed.get(),"World-loaded drone did not restore an active VM: "+drone.machine().lastError());if(!probe.completed.get()&&drone.machine().isPaused())require(drone.machine().start(),"Could not release drone's saved paused checkpoint");}
                ready=true;return;
            }
            require(drone.machine().lastError()==null,"Actual drone VM failed: "+drone.machine().lastError());require(drone.machine().isRunning()||probe.completed.get(),"Drone stopped before proof callbacks");
            if(stage.equals("prepare")&&probe.requests.get()==1&&drone.machine().isPaused()){
                Vec3 expected=Vec3.atCenterOf(origin).add(1.25,1,.75);require(drone.getTarget().distanceToSqr(expected)<1e-12,"Actual Lua drone.move did not set the expected quarter-block flight target");require(entity.position().distanceTo(drone.getTarget())>.05&&drone.getVelocity().lengthSqr()>1e-8,"Drone checkpoint did not capture actual flight toward its target");
                vector(manifest,"position",entity.position());vector(manifest,"velocity",entity.getDeltaMovement());vector(manifest,"target",drone.getTarget());captureIdentities();require(drone.machine().signal("drone_cold_resume",2718,"drone-queued-signal"),"Could not queue the drone cold signal");saveWorld();if(!luaJ)require(SAVED_VALUES.get()>0,"Actual entity save did not serialize native drone userdata");
                manifest.setProperty("run_id",runId);manifest.setProperty("host","drone");manifest.setProperty("architecture",architecture);manifest.setProperty("phase","prepared");manifest.setProperty("process",PROCESS);manifest.setProperty("world",directory.resolve("ocfixture-host-world").toString());saveManifest();evidence.add("entity.uuid="+entityId);evidence.add("flight=real Lua move(1.25,1,0.75), target="+drone.getTarget()+", position="+entity.position()+", velocity="+entity.getDeltaMovement());evidence.add("value.save_calls="+SAVED_VALUES.get());success();
            }else if(stage.equals("resume")&&probe.completed.get()){
                require(probe.boots.get()==(luaJ?2:1),"Drone BIOS reboot count differs from the architecture contract");require(luaJ?LOADED_VALUES.get()==0:LOADED_VALUES.get()>0,"Drone userdata loading differs from native/LuaJ semantics");verifyIdentities();verifyFlightAndInventory();evidence.add("proof="+(luaJ?"world-loaded drone rebooted LuaJ BIOS once; EEPROM, entity/flight state and actual components persisted":"world-loaded flying drone resumed native locals, nested coroutine, ItemStack/iterator userdata, open tmpfs handle offset, EEPROM bytes and queued signal"));evidence.add("entity.loaded_from_disk="+loadedFromDisk);evidence.add("entity.join_observed="+joinObserved);evidence.add("boots="+probe.boots.get());evidence.add("value.load_calls="+LOADED_VALUES.get());manifest.setProperty("phase","complete");saveWorld();saveManifest();success();
            }
        }
        void captureIdentities(){manifest.setProperty("machine",drone.machine().node().address());manifest.setProperty("tmp",drone.machine().tmpAddress());manifest.setProperty("probe",probe.node().address());manifest.setProperty("eeprom",probe.flash);manifest.setProperty("control",probe.control);drone.machine().components().forEach((address,type)->manifest.setProperty("component."+address,type));require(drone.componentSlot(probe.flash)>=0,"Drone EEPROM is not real installed hardware");}
        void verifyIdentities(){require(entity.getUUID().equals(entityId)&&manifest.getProperty("entity.type").equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())&&drone.machine().host()==entity,"World-loaded drone entity identity or VM ownership changed");require(manifest.getProperty("machine").equals(drone.machine().node().address())&&manifest.getProperty("tmp").equals(drone.machine().tmpAddress())&&manifest.getProperty("probe").equals(probe.node().address()),"Drone machine/tmpfs/upgrade identity changed");for(String key:manifest.stringPropertyNames())if(key.startsWith("component."))require(manifest.getProperty(key).equals(drone.machine().components().get(key.substring(10))),"Drone lost an actual saved component: "+key);}
        void verifyLoad()throws Exception{require(joinObserved&&loadedFromDisk,"Resume did not observe the actual EntityJoinLevelEvent loaded from disk");require(loadedPosition.distanceToSqr(vector(manifest,"position"))<1e-12&&loadedVelocity.distanceToSqr(vector(manifest,"velocity"))<1e-12&&loadedTarget.distanceToSqr(vector(manifest,"target"))<1e-12,"Entity NBT did not restore the exact pre-tick drone position/velocity/target");verifyFlightAndInventory();}
        void verifyFlightAndInventory()throws Exception{
            require(manifest.getProperty("drone.name").equals(drone.name())&&manifest.getProperty("drone.owner_name").equals(drone.ownerName())&&manifest.getProperty("drone.owner_uuid").equals(drone.ownerUUID().toString()),"Drone owner/name changed");require(drone.selectedSlot()==3&&drone.getTarget().distanceToSqr(vector(manifest,"target"))<1e-12,"Drone selected slot or flight target was lost");ItemStack inventory=drone.mainInventory().getItem(3);require(inventory.is(Items.DIAMOND)&&inventory.getCount()==4&&inventory.getHoverName().getString().equals("drone-inventory")&&inventory.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("drone_fixture").equals("kept"),"Actual drone inventory components did not persist");Component control=(Component)drone.machine().node().network().node(manifest.getProperty("control"));require("cold drone".equals(control.invoke("getStatusText",drone.machine())[0])&&((Number)control.invoke("getLightColor",drone.machine())[0]).intValue()==0x2468AC&&Math.abs(((Number)control.invoke("getAcceleration",drone.machine())[0]).doubleValue()-1.2)<.001,"Drone status/light/acceleration did not persist");
        }
        void saveWorld(){require(server.saveEverything(false,true,true),"Minecraft did not save the real drone entity world");}
        void saveManifest()throws Exception{try(OutputStream output=Files.newOutputStream(directory.resolve("checkpoint.properties"))){manifest.store(output,"Actual flying drone cold-process checkpoint");}}
        void success(){finished=true;pendingSuccess=true;report("PENDING_STOP");server.halt(false);}
        void fail(Throwable error){if(finished&&!pendingSuccess)return;finished=true;pendingSuccess=false;evidence.add("failure="+error);LOGGER.error("HOST_PERSISTENCE FAIL host=drone stage={}",stage,error);report("FAIL");server.halt(false);}
        void report(String status){List<String> lines=new ArrayList<>();lines.add("status="+status);lines.add("host=drone");lines.add("stage="+stage);lines.add("architecture="+architecture);lines.add("run_id="+runId);lines.addAll(evidence);try{Files.createDirectories(directory);Files.write(directory.resolve(stage+"-results.txt"),lines,StandardCharsets.UTF_8);}catch(Exception error){throw new IllegalStateException("Cannot write drone report",error);}}
    }
    private static final class ProbeDriver extends li.cil.oc.api.prefab.DriverItem implements HostAware {
        @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.is(Items.PAPER)&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBoolean(MARKER);}
        @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&Drone.class.isAssignableFrom(host);}
        @Override public String slot(ItemStack stack){return li.cil.oc.api.driver.item.Slot.Upgrade;}
        @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){return host instanceof Drone?new Probe(host):null;}
    }
    public static final class Probe extends AbstractManagedEnvironment {
        final EnvironmentHost host;
        final AtomicInteger boots=new AtomicInteger(),requests=new AtomicInteger();
        final AtomicBoolean completed=new AtomicBoolean();
        volatile String flash,control;
        Probe(EnvironmentHost host){this.host=host;setNode(Network.newNode(this,Visibility.Network).withComponent("drone_persistence_probe",Visibility.Neighbors).create());}
        @Callback(direct=true,limit=64)public Object[] booted(Context context,Arguments args){host.markChanged();return new Object[]{boots.incrementAndGet()};}
        @Callback(direct=true,limit=64)public Object[] value(Context context,Arguments args){DroneValue value=new DroneValue();value.seed(context,args);return new Object[]{value};}
        @Callback(direct=true,limit=64)public Object[] checkpoint(Context context,Arguments args){require(context instanceof Machine machine&&machine.host() instanceof Drone,"Checkpoint did not originate in a real entity-owned drone VM");require(boots.get()==1&&requests.get()==0,"Drone reached an unexpected first checkpoint");flash=args.checkString(0);control=args.checkString(1);require(context.pause(600),"Cannot pause actual drone at serializable Lua yield");requests.set(1);host.markChanged();return new Object[]{true};}
        void proof(Context context,Arguments args){Run run=java.util.Objects.requireNonNull(active);require(run.stage.equals("resume")&&context instanceof Machine machine&&machine.host() instanceof Drone&&machine.host()==run.entity,"Proof did not originate in the disk-loaded real drone entity");require(args.checkString(0).equals(run.manifest.getProperty("machine"))&&args.checkString(1).equals(run.manifest.getProperty("tmp"))&&args.checkString(2).equals(run.manifest.getProperty("eeprom"))&&args.checkString(3).equals(run.manifest.getProperty("control")),"Drone Lua locals lost saved component identities");}
        @Callback(direct=true,limit=64)public Object[] verified(Context context,Arguments args){proof(context,args);require(!active.luaJ&&boots.get()==1&&args.checkInteger(4)==314159,"Native drone BIOS restarted or lost a retained local");completed.set(true);host.markChanged();return new Object[]{true};}
        @Callback(direct=true,limit=64)public Object[] rebooted(Context context,Arguments args){proof(context,args);require(active.luaJ&&boots.get()==2,"LuaJ drone did not genuinely reboot BIOS exactly once");completed.set(true);host.markChanged();return new Object[]{true};}
        @Override public void load(CompoundTag tag){super.load(tag);boots.set(tag.getInt("boots"));requests.set(tag.getInt("requests"));completed.set(tag.getBoolean("complete"));flash=tag.getString("flash");control=tag.getString("control");}
        @Override public void save(CompoundTag tag){super.save(tag);tag.putInt("boots",boots.get());tag.putInt("requests",requests.get());tag.putBoolean("complete",completed.get());if(flash!=null)tag.putString("flash",flash);if(control!=null)tag.putString("control",control);}
    }
    public static final class DroneValue extends AbstractValue {
        private int counter=-1;
        private ItemStack stack=ItemStack.EMPTY;
        private ItemStackArrayValue iterator;
        private HolderLookup.Provider loadedWith;
        public DroneValue(){}
        void seed(Context context,Arguments args){require(context instanceof Machine machine&&machine.host() instanceof Drone,"Value did not originate in an actual drone");HolderLookup.Provider registries=((Machine)context).host().world().registryAccess();counter=43;stack=new ItemStack(Items.EMERALD,7);stack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("drone-cold-value"));CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putString("drone_payload","kept"));iterator=new ItemStackArrayValue(new ItemStack[]{ItemStack.EMPTY,new ItemStack(Items.DIAMOND,3)},registries);iterator.call(context,args);}
        @Override public void save(CompoundTag tag){throw new AssertionError("Drone Value lost registry provider on save");}
        @Override public void load(CompoundTag tag){throw new AssertionError("Drone Value lost registry provider on load");}
        @Override public void save(CompoundTag tag,HolderLookup.Provider registries){require(registries!=null&&iterator!=null&&!stack.isEmpty(),"Drone Value was not initialized by its real callback");tag.putInt("counter",counter);tag.put("stack",stack.save(registries));CompoundTag state=new CompoundTag();iterator.save(state,registries);tag.put("iterator",state);SAVED_VALUES.incrementAndGet();}
        @Override public void load(CompoundTag tag,HolderLookup.Provider registries){require(registries!=null,"Drone Value restore has no registry provider");counter=tag.getInt("counter");stack=ItemStack.parseOptional(registries,tag.getCompound("stack"));iterator=new ItemStackArrayValue();iterator.load(tag.getCompound("iterator"),registries);loadedWith=registries;LOADED_VALUES.incrementAndGet();}
        @Callback(direct=true,limit=64)public Object[] snapshot(Context context,Arguments args){require(context instanceof Machine machine&&machine.host() instanceof Drone&&loadedWith==machine.host().world().registryAccess(),"Drone Value was not restored with its actual world's registry");return new Object[]{counter,stack.getCount(),stack.getHoverName().getString(),stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("drone_payload")};}
        @Callback(direct=true,limit=64)public Object[] nextCount(Context context,Arguments args){Object[] next=iterator.call(context,args);return new Object[]{next==null?0:((ItemStack)next[0]).getCount()};}
    }
    private static final String BIOS="""
        local p=component.proxy(assert(component.list("drone_persistence_probe",true)()))
        local boot=p.booted()
        local machine,tmp=computer.address(),computer.tmpAddress()
        local flash=assert(component.list("eeprom",true)())
        local control=assert(component.list("drone",true)())
        if boot>1 then
          assert(computer.getArchitecture()=="LuaJ","native drone rebooted BIOS")
          assert(component.invoke(flash,"getData")=="drone-eeprom-data")
          assert(p.rebooted(machine,tmp,flash,control))
          while true do computer.pullSignal() end
        end
        local retained=314159
        local co=coroutine.create(function() local n=271828 coroutine.yield() return n+1 end)
        assert(coroutine.resume(co))
        local value=p.value()
        local fs=component.proxy(tmp)
        local w=assert(fs.open("drone-tmp","w")) assert(fs.write(w,"abcdefghi")) fs.close(w)
        local reader=assert(fs.open("drone-tmp","r")) assert(fs.read(reader,3)=="abc")
        component.invoke(flash,"setData","drone-eeprom-data")
        assert(math.abs(component.invoke(control,"setAcceleration",1.2)-1.2)<0.001)
        assert(component.invoke(control,"setStatusText","cold drone")=="cold drone")
        assert(component.invoke(control,"setLightColor",0x2468AC)==0x2468AC)
        component.invoke(control,"move",1.25,1,0.75)
        assert(p.checkpoint(flash,control))
        local event,number,text
        repeat event,number,text=computer.pullSignal() until event=="drone_cold_resume"
        assert(number==2718 and text=="drone-queued-signal","drone queued signal lost")
        assert(retained==314159,"drone coroutine local lost")
        local ok,n=coroutine.resume(co) assert(ok and n==271829,"drone nested coroutine lost")
        local count,size,label,tag=value.snapshot()
        assert(count==43 and size==7 and label=="drone-cold-value" and tag=="kept","drone userdata ItemStack lost")
        assert(value.nextCount()==3,"drone iterator cursor lost")
        assert(fs.read(reader,6)=="defghi","drone tmpfs handle offset lost")
        assert(component.invoke(flash,"getData")=="drone-eeprom-data")
        fs.close(reader) assert(p.verified(machine,tmp,flash,control,retained))
        while true do computer.pullSignal() end
        """;
}
