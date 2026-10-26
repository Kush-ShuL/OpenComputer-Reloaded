package ocfixture;

import com.mojang.serialization.MapCodec;
import java.io.IOException;
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
import java.util.function.Supplier;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractValue;
import li.cil.oc.api.prefab.ItemStackArrayValue;
import li.cil.oc.api.prefab.TileEntityEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Two separate server processes exercise an actual persisted Case block using only the SDK. */
public final class WorldPersistenceContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-world-persistence");
    private static final String PROCESS_TOKEN=UUID.randomUUID().toString();
    private static final String WORLD_NAME="ocfixture-persistence-world";
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,"oc_api_compat");
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"oc_api_compat");
    private static final Supplier<Block> PROBE=BLOCKS.register("world_persistence_probe",ProbeBlock::new);
    private static final Supplier<BlockEntityType<Probe>> PROBE_TYPE=TYPES.register("world_persistence_probe",()->BlockEntityType.Builder.of(Probe::new,PROBE.get()).build(null));
    private static final AtomicInteger SAVED_VALUES=new AtomicInteger(),LOADED_VALUES=new AtomicInteger();
    private static Run active;
    private WorldPersistenceContract(){}
    public static boolean enabled(){return System.getProperty("ocfixture.persistenceStage")!=null;}
    public static void registerBlocks(IEventBus bus){BLOCKS.register(bus);TYPES.register(bus);NeoForge.EVENT_BUS.addListener(WorldPersistenceContract::unloaded);}
    public static void start(MinecraftServer server){try{active=new Run(server);active.start();}catch(Throwable error){if(active!=null)active.fail(error);else{LOGGER.error("API_WORLD_PERSISTENCE startup failed",error);throw new IllegalStateException("World persistence fixture startup failed",error);}}}
    public static void tick(MinecraftServer server){Run run=active;if(run==null||run.finished)return;try{run.tick();}catch(Throwable error){run.fail(error);}}
    public static void stopped(){Run run=active;if(run==null)return;if(run.pendingSuccess){run.writeReport("PASS");LOGGER.info("API_WORLD_PERSISTENCE_COMPLETE stage={} architecture={} status=PASS",run.stage,run.architecture);}else if(!run.finished)run.fail(new AssertionError("Server stopped before the world persistence contract completed"));active=null;}
    private static void unloaded(ChunkEvent.Unload event){Run run=active;if(run!=null&&run.phase==Phase.UNLOAD&&event.getLevel()==run.level&&event.getChunk().getPos().equals(new net.minecraft.world.level.ChunkPos(run.position)))run.unloadObserved=true;}
    private enum Phase{SETUP,PREPARE,RESTORE,COLD,UNLOAD,CHUNK_RESTORE,CHUNK}
    private static final class Run {
        final MinecraftServer server;
        final ServerLevel level;
        final String stage=System.getProperty("ocfixture.persistenceStage"),runId=System.getProperty("ocfixture.persistenceRunId"),architecture=System.getProperty("ocfixture.persistenceArchitecture","Lua 5.3");
        final boolean luaJ=architecture.equals("LuaJ"),chunks=!luaJ&&Boolean.parseBoolean(System.getProperty("ocfixture.persistenceChunks","true"));
        final Path directory=Path.of(System.getProperty("ocfixture.persistenceDirectory")).toAbsolutePath().normalize();
        final Properties manifest=new Properties();
        final List<String> evidence=new ArrayList<>();
        BlockPos position=new BlockPos(4096,96,4096);
        Case hardware;
        BlockEntity caseEntity,previousCase;
        Probe probe;
        Node oldNode;
        Phase phase;
        int ticks,phaseTicks;
        boolean finished,pendingSuccess,unloadObserved;
        Run(MinecraftServer server){this.server=server;level=server.overworld();}
        void start()throws Exception{
            require(stage.equals("prepare")||stage.equals("resume"),"Unknown persistence stage");
            require(directory.equals(Path.of("").toAbsolutePath().normalize()),"Persistence work directory differs from the isolated Gradle directory");
            require(directory.getParent().getFileName().toString().equals("persistence")&&directory.getFileName().toString().equals(runId),"Refusing a non-fixture persistence directory");
            require(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(directory.resolve(WORLD_NAME)),"Refusing a different Minecraft world");
            if(stage.equals("prepare"))prepare();else resume();
            LOGGER.info("API_WORLD_PERSISTENCE stage={} architecture={} run={} world={}",stage,architecture,runId,server.getWorldPath(LevelResource.ROOT));
        }
        void prepare(){
            require(!Files.exists(directory.resolve("checkpoint.properties")),"Prepare may not replace an existing checkpoint");
            force(true);level.getChunkAt(position);
            require(level.isEmptyBlock(position)&&level.isEmptyBlock(position.east()),"The isolated test cells are occupied; nothing was replaced");
            Block caseBlock=java.util.Objects.requireNonNull(li.cil.oc.api.Items.get("case3"),"Real case3 is unavailable").block();
            require(caseBlock!=null,"The SDK case descriptor did not supply a real block");
            level.setBlock(position,caseBlock.defaultBlockState(),3);level.setBlock(position.east(),PROBE.get().defaultBlockState(),3);attach();
            ItemStack cpu=item("cpu3");var driver=Driver.driverFor(cpu,hardware.getClass());
            require(driver instanceof MutableProcessor,"The real CPU is not a configurable processor");
            Class<? extends Architecture> selected=li.cil.oc.api.Machine.architectures().stream().filter(type->li.cil.oc.api.Machine.getArchitectureName(type).equals(architecture)).findFirst().orElseThrow(()->new AssertionError("Required architecture is unavailable: "+architecture));
            ((MutableProcessor)driver).setArchitecture(cpu,selected);
            install(cpu);install(item("ram6"));install(item("hdd3"));
            ItemStack eeprom=li.cil.oc.api.Items.registerEEPROM("World persistence fixture",BIOS.getBytes(StandardCharsets.UTF_8),new byte[0],false);
            require(!eeprom.isEmpty(),"The public EEPROM factory returned no hardware");install(eeprom);
            phase=Phase.SETUP;
            evidence.add("world.case="+position.toShortString());evidence.add("hardware=real case3,cpu3,ram6,hdd3,eeprom");
        }
        void resume()throws IOException{
            Path preparation=directory.resolve("prepare-results.txt");
            require(Files.isRegularFile(preparation)&&Files.readAllLines(preparation,StandardCharsets.UTF_8).getFirst().equals("status=PASS"),"Prepare did not finish its normal server shutdown successfully");
            try(InputStream input=Files.newInputStream(directory.resolve("checkpoint.properties"))){manifest.load(input);}
            require(runId.equals(manifest.getProperty("run_id"))&&architecture.equals(manifest.getProperty("architecture")),"Checkpoint run id or architecture differs");
            require("prepared".equals(manifest.getProperty("phase")),"Checkpoint was not prepared or has already been consumed");
            require(!PROCESS_TOKEN.equals(manifest.getProperty("process_token")),"Resume must run in a new JVM process");
            require(directory.resolve(WORLD_NAME).toString().equals(manifest.getProperty("world")),"Checkpoint belongs to another world");
            position=BlockPos.of(Long.parseLong(manifest.getProperty("position")));force(true);level.getChunkAt(position);attach();
            phase=Phase.RESTORE;
            evidence.add("process.prepare="+manifest.getProperty("process_token"));evidence.add("process.resume="+PROCESS_TOKEN);evidence.add("process.pid="+ProcessHandle.current().pid());
        }
        void attach(){caseEntity=level.getBlockEntity(position);require(caseEntity instanceof Case,"Loaded block entity is not the real public Case host");hardware=(Case)caseEntity;BlockEntity adjacent=level.getBlockEntity(position.east());require(adjacent instanceof Probe,"Persisted external probe block is absent");probe=(Probe)adjacent;require(hardware.machine()!=null,"The real case did not create its machine");}
        void install(ItemStack stack){for(int slot=0;slot<hardware.getContainerSize();slot++)if(hardware.getItem(slot).isEmpty()&&hardware.canPlaceItem(slot,stack)){hardware.setItem(slot,stack);return;}throw new AssertionError("No real case slot accepted "+stack.getHoverName().getString());}
        void force(boolean enabled){level.setChunkForced(position.getX()>>4,position.getZ()>>4,enabled);}
        void power(){if(probe!=null&&probe.node() instanceof Connector connector)connector.changeBuffer(Double.MAX_VALUE);}
        void releasePause(){if(hardware.machine().isPaused())require(hardware.machine().start(),"Could not release the intentionally paused saved checkpoint");}
        boolean connectedHardware(){Machine machine=hardware.machine();if(machine.node().network()==null||machine.node().address()==null)return false;for(Node node:machine.node().network().nodes())if(node instanceof Component component&&component.name().equals("eeprom")&&hardware.componentSlot(node.address())>=0)return true;return false;}
        void tick()throws Exception{
            if(++ticks>2400)throw new AssertionError("World persistence stage exceeded 2400 ticks; phase="+phase);
            phaseTicks++;
            if(phase==Phase.UNLOAD){
                if(unloadObserved&&!level.getChunkSource().hasChunk(position.getX()>>4,position.getZ()>>4)){
                    require(oldNode.network()==null,"Unloaded real case retained its network node");force(true);level.getChunkAt(position);attach();require(caseEntity!=previousCase,"Chunk reload reused the old block entity instead of loading world data");phase=Phase.CHUNK_RESTORE;phaseTicks=0;evidence.add("chunk.unload=observed real ChunkEvent.Unload and detached machine node");
                }else if(phaseTicks>1200)throw new AssertionError("The isolated forced chunk did not unload after releasing its ticket");
                return;
            }
            power();
            // Real chunks enqueue fresh block entities for their normal onLoad/ticker lifecycle.
            // Wait for that lifecycle; do not synthesize or manually load the machine state.
            if(phase==Phase.SETUP||phase==Phase.RESTORE||phase==Phase.CHUNK_RESTORE){
                if(!connectedHardware()){require(phaseTicks<100,"Real case component lifecycle did not initialize within 100 ticks");return;}
                if(phase==Phase.SETUP){require(hardware.machine().start(),"Real case failed to boot: "+hardware.machine().lastError());phase=Phase.PREPARE;}
                else{verifyAddresses();require(probe.boots.get()==1||luaJ&&probe.boots.get()==2,"Probe boot count was not restored correctly");require(hardware.machine().isRunning()||luaJ&&probe.completed.get(),"Persisted real case did not restore an active VM: "+hardware.machine().lastError());if(!probe.completed.get())releasePause();phase=phase==Phase.CHUNK_RESTORE?Phase.CHUNK:Phase.COLD;}
                phaseTicks=0;return;
            }
            if(!hardware.machine().isRunning())require(probe.completed.get(),"Real case stopped before proof callbacks: "+hardware.machine().lastError());
            if(phase==Phase.PREPARE&&probe.requests.get()==1&&hardware.machine().isPaused()){
                require(probe.boots.get()==1,"BIOS restarted before preparation");
                require(hardware.machine().signal("world_cold_resume",2718,"cold-queued-signal"),"Cannot queue the persisted cold-start signal");captureAddresses();saveWorld();
                if(!luaJ)require(SAVED_VALUES.get()>0,"Native world save did not serialize its live addon Value");
                manifest.setProperty("run_id",runId);manifest.setProperty("architecture",architecture);manifest.setProperty("phase","prepared");manifest.setProperty("process_token",PROCESS_TOKEN);manifest.setProperty("position",Long.toString(position.asLong()));manifest.setProperty("world",directory.resolve(WORLD_NAME).toString());saveManifest();
                evidence.add("checkpoint.coroutine=paused in actual case world NBT");evidence.add("checkpoint.queued_signal=world_cold_resume,2718,cold-queued-signal");evidence.add("value.save_calls="+SAVED_VALUES.get());success();
            }else if(phase==Phase.COLD&&chunks&&probe.requests.get()==2&&hardware.machine().isPaused()){
                require(hardware.machine().signal("world_chunk_resume",31415,"chunk-queued-signal"),"Cannot queue the persisted chunk signal");saveWorld();previousCase=caseEntity;oldNode=hardware.machine().node();hardware=null;probe=null;phase=Phase.UNLOAD;phaseTicks=0;force(false);
            }else if(probe.completed.get()){
                require(probe.boots.get()==(luaJ?2:1),"Unexpected persisted BIOS boot count");
                if(!luaJ)require(LOADED_VALUES.get()>=(chunks?2:1),"Native Value was not reconstructed from real cold/chunk world loads");
                else require(LOADED_VALUES.get()==0,"LuaJ must reboot; this fixture must not substitute a persisted native Value");
                evidence.add("proof="+(luaJ?"LuaJ BIOS rebooted exactly once; persistent HDD and EEPROM survived":"native coroutine locals, nested coroutine, Value ItemStack/iterator, tmpfs handle position, component addresses and queued signals resumed"));evidence.add("boots="+probe.boots.get());evidence.add("value.load_calls="+LOADED_VALUES.get());evidence.add("chunk.contract="+(chunks?"PASS":"not requested for "+architecture));manifest.setProperty("phase","complete");saveWorld();saveManifest();success();
            }
        }
        void captureAddresses(){manifest.setProperty("machine",hardware.machine().node().address());manifest.setProperty("tmp",hardware.machine().tmpAddress());manifest.setProperty("probe",probe.node().address());for(Node node:hardware.machine().node().network().nodes())if(node instanceof Component component){if(component.name().equals("filesystem")&&!node.address().equals(hardware.machine().tmpAddress()))manifest.setProperty("hdd",node.address());if(component.name().equals("eeprom"))manifest.setProperty("eeprom",node.address());}require(manifest.getProperty("hdd")!=null&&manifest.getProperty("eeprom")!=null,"Real HDD/EEPROM components are absent");}
        void verifyAddresses(){require(manifest.getProperty("machine").equals(hardware.machine().node().address()),"Machine address changed");require(manifest.getProperty("tmp").equals(hardware.machine().tmpAddress()),"tmpfs address changed");require(manifest.getProperty("probe").equals(probe.node().address()),"Probe node address changed");require(hardware.componentSlot(manifest.getProperty("hdd"))>=0,"Persisted HDD address is not an internal real case component");require(hardware.componentSlot(manifest.getProperty("eeprom"))>=0,"Persisted EEPROM address is not an internal real case component");}
        void saveWorld(){caseEntity.setChanged();probe.setChanged();require(server.saveEverything(false,true,true),"Minecraft did not save the isolated world");}
        void saveManifest()throws IOException{try(OutputStream output=Files.newOutputStream(directory.resolve("checkpoint.properties"))){manifest.store(output,"Real-world external SDK persistence checkpoint");}}
        void success(){pendingSuccess=true;finished=true;writeReport("PENDING_STOP");LOGGER.info("API_WORLD_PERSISTENCE awaiting normal server shutdown stage={}",stage);server.halt(false);}
        void fail(Throwable error){if(finished&&!pendingSuccess)return;pendingSuccess=false;finished=true;evidence.add("failure="+error);LOGGER.error("API_WORLD_PERSISTENCE FAIL stage={} architecture={}",stage,architecture,error);writeReport("FAIL");server.halt(false);}
        void writeReport(String status){List<String> lines=new ArrayList<>();lines.add("status="+status);lines.add("stage="+stage);lines.add("architecture="+architecture);lines.add("run_id="+runId);lines.addAll(evidence);try{Files.createDirectories(directory);Files.write(directory.resolve(stage+"-results.txt"),lines,StandardCharsets.UTF_8);}catch(IOException error){throw new java.io.UncheckedIOException(error);}}
    }
    public static final class Probe extends TileEntityEnvironment {
        final AtomicInteger boots=new AtomicInteger(),requests=new AtomicInteger();
        final AtomicBoolean completed=new AtomicBoolean(),dirty=new AtomicBoolean();
        Probe(BlockPos position,BlockState state){super(PROBE_TYPE.get(),position,state);node=Network.newNode(this,Visibility.Network).withComponent("world_persistence_probe",Visibility.Network).withConnector(10_000_000).create();}
        @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide)Network.joinOrCreateNetwork(this);}
        void tick(){if(dirty.getAndSet(false))setChanged();}
        @Callback(direct=true,limit=64)public Object[] booted(Context context,Arguments args){dirty.set(true);return new Object[]{boots.incrementAndGet()};}
        @Callback(direct=true,limit=64)public Object[] value(Context context,Arguments args){PersistentValue value=new PersistentValue();value.seed(context,args);return new Object[]{value};}
        @Callback(direct=true,limit=64)public Object[] checkpoint(Context context,Arguments args){require(boots.get()==1&&requests.get()==0,"Unexpected initial BIOS checkpoint");require(context.pause(600),"Cannot pause real case at a serializable yield boundary");requests.set(1);dirty.set(true);return new Object[]{true};}
        private void proof(Context context,Arguments args){Run run=java.util.Objects.requireNonNull(active);require(run.stage.equals("resume"),"Proof callback executed in the prepare process");require(context instanceof Machine machine&&machine.host() instanceof Case,"Proof did not originate from a real Case machine");require(args.checkString(0).equals(run.manifest.getProperty("machine"))&&args.checkString(1).equals(run.manifest.getProperty("tmp"))&&args.checkString(2).equals(run.manifest.getProperty("hdd"))&&args.checkString(3).equals(run.manifest.getProperty("eeprom")),"Lua locals lost their saved machine/component addresses");}
        @Callback(direct=true,limit=64)public Object[] coldVerified(Context context,Arguments args){proof(context,args);require(!active.luaJ&&boots.get()==1,"Native cold load restarted BIOS");require(args.checkInteger(4)==314159,"Native Lua local was lost");if(active.chunks){require(context.pause(600),"Cannot pause before real chunk unload");requests.set(2);dirty.set(true);return new Object[]{true};}completed.set(true);dirty.set(true);return new Object[]{false};}
        @Callback(direct=true,limit=64)public Object[] chunkVerified(Context context,Arguments args){proof(context,args);require(boots.get()==1&&requests.get()==2,"Chunk restoration restarted the native BIOS");require(args.checkInteger(4)==314161,"Chunk restoration lost a native local");completed.set(true);dirty.set(true);return new Object[]{true};}
        @Callback(direct=true,limit=64)public Object[] rebooted(Context context,Arguments args){proof(context,args);require(active.luaJ&&boots.get()==2,"LuaJ did not perform exactly one genuine BIOS reboot");completed.set(true);dirty.set(true);return new Object[]{true};}
        @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);boots.set(tag.getInt("fixtureBoots"));requests.set(tag.getInt("fixtureRequests"));completed.set(tag.getBoolean("fixtureComplete"));}
        @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putInt("fixtureBoots",boots.get());tag.putInt("fixtureRequests",requests.get());tag.putBoolean("fixtureComplete",completed.get());}
    }
    /** Native Lua must reconstruct this actual serialized userdata, never a Java-side substitute. */
    public static final class PersistentValue extends AbstractValue {
        private int counter=-1;
        private ItemStack stack=ItemStack.EMPTY;
        private ItemStackArrayValue iterator;
        private HolderLookup.Provider loadedWith;
        public PersistentValue(){}
        private void seed(Context context,Arguments args){require(context instanceof Machine,"Value originated outside a real machine");HolderLookup.Provider registries=((Machine)context).host().world().registryAccess();counter=43;stack=new ItemStack(Items.EMERALD,7);stack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("cold-value"));CompoundTag data=new CompoundTag();data.putString("fixture_tag","kept");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));iterator=new ItemStackArrayValue(new ItemStack[]{ItemStack.EMPTY,new ItemStack(Items.DIAMOND,3)},registries);iterator.call(context,args);}
        @Override public void save(CompoundTag tag){throw new AssertionError("Real case Value.save lost its registry provider");}
        @Override public void load(CompoundTag tag){throw new AssertionError("Real case Value.load lost its registry provider");}
        @Override public void save(CompoundTag tag,HolderLookup.Provider registries){require(registries!=null,"Value save has no world registry provider");require(iterator!=null&&!stack.isEmpty(),"Live Value payload was not initialized through its real callback");tag.putInt("counter",counter);tag.put("stack",stack.save(registries));CompoundTag array=new CompoundTag();iterator.save(array,registries);tag.put("iterator",array);SAVED_VALUES.incrementAndGet();}
        @Override public void load(CompoundTag tag,HolderLookup.Provider registries){require(registries!=null,"Value load has no world registry provider");counter=tag.getInt("counter");stack=ItemStack.parseOptional(registries,tag.getCompound("stack"));iterator=new ItemStackArrayValue();iterator.load(tag.getCompound("iterator"),registries);loadedWith=registries;LOADED_VALUES.incrementAndGet();}
        @Callback(direct=true,limit=64)public Object[] snapshot(Context context,Arguments args){require(context instanceof Machine machine&&loadedWith==machine.host().world().registryAccess(),"Restored Value is missing the actual invoking world's registry provider");return new Object[]{counter,stack.getCount(),stack.getHoverName().getString(),stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("fixture_tag")};}
        @Callback(direct=true,limit=64)public Object[] nextCount(Context context,Arguments args){Object[] result=iterator.call(context,args);return new Object[]{result==null?0:((ItemStack)result[0]).getCount()};}
    }
    private static final class ProbeBlock extends BaseEntityBlock {
        ProbeBlock(){super(BlockBehaviour.Properties.of().strength(1));}
        @Override protected MapCodec<? extends BaseEntityBlock>codec(){return MapCodec.unit(this);}
        @Override public BlockEntity newBlockEntity(BlockPos position,BlockState state){return new Probe(position,state);}
        @Override public <T extends BlockEntity>BlockEntityTicker<T>getTicker(Level level,BlockState state,BlockEntityType<T>type){return !level.isClientSide&&type==PROBE_TYPE.get()?(world,pos,value,entity)->((Probe)entity).tick():null;}
    }
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing real hardware: "+name);return descriptor.createItemStack(1);}
    private static void require(boolean condition,String description){if(!condition)throw new AssertionError(description);}
    private static final String BIOS="""
        local p=component.proxy(assert(component.list("world_persistence_probe",true)()))
        local boot=p.booted()
        local machine,tmp=computer.address(),computer.tmpAddress()
        local disk
        for address in component.list("filesystem",true) do if address~=tmp then disk=address break end end
        assert(disk,"real HDD absent")
        local flash=assert(component.list("eeprom",true)())
        local persistent=component.proxy(disk)
        if boot>1 then
          assert(computer.getArchitecture()=="LuaJ","native VM rebooted its EEPROM")
          local f=assert(persistent.open("cold-marker","r"))
          assert(persistent.read(f,100)=="persistent-cold-marker")
          persistent.close(f)
          assert(component.invoke(flash,"getData")=="cold-eeprom-data")
          assert(p.rebooted(machine,tmp,disk,flash))
          computer.shutdown()
        end
        local retained=314159
        local co=coroutine.create(function() local x=271828 coroutine.yield() return x+1 end)
        assert(coroutine.resume(co))
        local value=p.value()
        local fs=component.proxy(tmp)
        local w=assert(fs.open("cold-tmp","w"))
        assert(fs.write(w,"abcdefghi")) fs.close(w)
        local reader=assert(fs.open("cold-tmp","r"))
        assert(fs.read(reader,3)=="abc")
        w=assert(persistent.open("cold-marker","w"))
        assert(persistent.write(w,"persistent-cold-marker")) persistent.close(w)
        component.invoke(flash,"setData","cold-eeprom-data")
        assert(p.checkpoint())
        local event,number,text
        repeat event,number,text=computer.pullSignal() until event=="world_cold_resume"
        assert(number==2718 and text=="cold-queued-signal","cold signal lost")
        assert(retained==314159,"coroutine local lost")
        local ok,n=coroutine.resume(co) assert(ok and n==271829,"nested coroutine lost")
        local count,size,label,tag=value.snapshot()
        assert(count==43 and size==7 and label=="cold-value" and tag=="kept","Value lost ItemStack data")
        assert(value.nextCount()==3,"Value iterator cursor lost")
        assert(fs.read(reader,6)=="defghi","tmpfs open handle/offset lost")
        assert(component.invoke(flash,"getData")=="cold-eeprom-data")
        w=assert(persistent.open("cold-marker","r"))
        assert(persistent.read(w,100)=="persistent-cold-marker") persistent.close(w)
        assert(fs.seek(reader,"set",3)==3)
        local chunkLocal=retained+2
        if p.coldVerified(machine,tmp,disk,flash,retained) then
          repeat event,number,text=computer.pullSignal() until event=="world_chunk_resume"
          assert(number==31415 and text=="chunk-queued-signal","chunk signal lost")
          assert(fs.read(reader,6)=="defghi","chunk tmpfs handle/offset lost")
          count,size,label,tag=value.snapshot()
          assert(count==43 and size==7 and label=="cold-value" and tag=="kept")
          assert(value.nextCount()==0,"chunk Value iterator cursor lost")
          assert(p.chunkVerified(machine,tmp,disk,flash,chunkLocal))
        end
        fs.close(reader)
        computer.shutdown()
        """;
}
