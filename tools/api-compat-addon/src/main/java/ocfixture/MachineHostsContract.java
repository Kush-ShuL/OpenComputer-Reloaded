package ocfixture;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.internal.Adapter;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Rack;
import li.cil.oc.api.internal.Server;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.util.Lifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Actual world hosts perform every insertion, network join, lifecycle transition and item save. */
public final class MachineHostsContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-machine-hosts");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"oc_api_compat");
    private static final Supplier<Item> CARD=ITEMS.register("host_lifecycle_card",()->new Item(new Item.Properties().stacksTo(1)));
    private static final Supplier<Item> UPGRADE=ITEMS.register("host_lifecycle_upgrade",()->new Item(new Item.Properties().stacksTo(1)));
    private static final Supplier<Item> SERVER_CARD=ITEMS.register("server_marker_card",()->new Item(new Item.Properties().stacksTo(1)));
    private static final String CLOSED="ocfixture:closedBeforeSave",PHASE="ocfixture:savePhase";
    private static Run active;
    private MachineHostsContract(){}

    /** Called by the addon constructor only for the isolated host batch. */
    public static void register(IEventBus bus){ITEMS.register(bus);NeoForge.EVENT_BUS.addListener(MachineHostsContract::joined);}
    /** Called in common setup before the public driver registry is locked. */
    public static void registerDrivers(){Driver.add(new ProbeDriver());}
    public static void start(ServerLevel level,BlockPos position,List<String> results){
        require(active==null,"A machine-host contract is already active");
        Run run=new Run(level,position.immutable(),results);active=run;
        try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){
        Run run=active;if(run==null)return;
        try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}
        active=null;run.cleanup();
    }
    public static void close(){Run run=active;active=null;if(run!=null){run.results.add("FAIL machine.hosts: server stopped before the actual host contract completed");run.cleanup();}}
    private static void joined(EntityJoinLevelEvent event){
        Run run=active;if(run!=null&&run.recordingDrops&&event.getLevel()==run.level&&!event.loadedFromDisk()&&event.getEntity() instanceof ItemEntity item&&run.bounds().contains(item.position()))run.spawnedDrops.add(item);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void equal(double actual,double expected,String message){require(Math.abs(actual-expected)<=Math.max(1e-8,Math.abs(expected)*1e-10),message+": expected="+expected+", actual="+actual);}
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing actual machine-host item "+name);return descriptor.createItemStack(1);}
    private enum Kind{CaseCard,AdapterUpgrade,ServerCard}
    private static Kind kind(ItemStack stack){return stack.is(CARD.get())?Kind.CaseCard:stack.is(UPGRADE.get())?Kind.AdapterUpgrade:stack.is(SERVER_CARD.get())?Kind.ServerCard:null;}
    private static final class ProbeDriver implements HostAware {
        @Override public boolean worksWith(ItemStack stack){return kind(stack)!=null;}
        @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){
            Kind kind=kind(stack);return kind==Kind.CaseCard?Case.class.isAssignableFrom(host):kind==Kind.AdapterUpgrade?Adapter.class.isAssignableFrom(host):kind==Kind.ServerCard&&host==Server.class;
        }
        @Override public String slot(ItemStack stack){return kind(stack)==Kind.AdapterUpgrade?Slot.Upgrade:Slot.Card;}
        @Override public int tier(ItemStack stack){return 0;}
        @Override public CompoundTag dataTag(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data");}
        @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){
            Probe probe=new Probe(kind(stack),host);if(active!=null)active.probes.add(probe);return probe;
        }
    }
    /** A normal public driver observes host callbacks; it never supplies expected host state. */
    private static final class Probe extends AbstractManagedEnvironment implements Lifecycle {
        final Kind kind;final EnvironmentHost host;final List<LifecycleState> states=new ArrayList<>();
        int disconnects;boolean loaded;LifecycleState phase;
        Probe(Kind kind,EnvironmentHost host){this.kind=kind;this.host=host;setNode(Network.newNode(this,Visibility.Network).withComponent("host_lifecycle_probe").create());}
        @Override public void onLifecycleStateChange(LifecycleState state){phase=state;states.add(state);}
        @Override public void load(CompoundTag tag){require(phase==LifecycleState.Constructing,"Host loaded its real component before Constructing");loaded=true;super.load(tag);}
        @Override public void onDisconnect(Node node){if(node==node())disconnects++;}
        @Override public void save(CompoundTag tag){
            // Capture before AbstractManagedEnvironment may temporarily assign a missing address.
            boolean closed=disconnects>0&&node().network()==null;String state=phase==null?"":phase.name();
            super.save(tag);tag.putBoolean(CLOSED,closed);tag.putString(PHASE,state);
        }
    }
    private static final class Run {
        final ServerLevel level;final BlockPos position;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        final Set<Integer> oldItems=new LinkedHashSet<>();final List<Probe> probes=new ArrayList<>();
        final List<ItemEntity> spawnedDrops=new ArrayList<>();
        Case computer;Adapter adapter;Rack rack;Connector capacitor;FakePlayer actor;
        Probe brokenCaseProbe;int ticks;boolean cleaned,recordingDrops;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;}
        AABB bounds(){return new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(position.offset(-2,-2,-2)),net.minecraft.world.phys.Vec3.atLowerCornerOf(position.offset(16,4,4)));}
        void place(BlockPos pos,String name){
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
            require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Machine-host fixture refuses an occupied cell: "+pos);
            restored.put(pos.immutable(),level.getBlockState(pos));var descriptor=li.cil.oc.api.Items.get(name);
            require(descriptor!=null&&descriptor.block()!=null&&level.setBlock(pos,descriptor.block().defaultBlockState(),3),"Could not place actual "+name);
        }
        void start(){
            require(API.config!=null&&!API.config.getBoolean("power.ignorePower"),"Actual host energy proof requires enabled OC power");
            place(position,"case1");place(position.east(),"capacitor");place(position.east(6),"adapter");place(position.east(12),"rack");
            require(level.getBlockEntity(position) instanceof Case,"Placed machine host is not a public Case");computer=(Case)level.getBlockEntity(position);
            require(level.getBlockEntity(position.east(6)) instanceof Adapter,"Placed upgrade host is not a public Adapter");adapter=(Adapter)level.getBlockEntity(position.east(6));
            require(level.getBlockEntity(position.east(12)) instanceof Rack,"Placed blade host is not a public Rack");rack=(Rack)level.getBlockEntity(position.east(12));
            actor=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"OC_HostProof"));actor.getAbilities().instabuild=true;actor.setPos(position.getX()+12.5,position.getY(),position.getZ()+.5);
            for(ItemEntity entity:level.getEntitiesOfClass(ItemEntity.class,bounds()))oldItems.add(entity.getId());
        }
        Probe newest(Kind kind,EnvironmentHost host){
            for(int i=probes.size()-1;i>=0;i--){Probe probe=probes.get(i);if(probe.kind==kind&&probe.host==host)return probe;}
            throw new AssertionError("Actual host never called the registered driver for "+kind);
        }
        CompoundTag data(ItemStack stack){var driver=Driver.driverFor(stack);require(driver!=null,"Removed actual probe item lost its driver");return driver.dataTag(stack);}
        void mounted(Probe probe,ItemStack stack,Node hostNode){
            require(probe.loaded&&probe.states.equals(List.of(Lifecycle.LifecycleState.Constructing,Lifecycle.LifecycleState.Initializing,Lifecycle.LifecycleState.Initialized)),"Real host did not construct/load/initialize its component in order: "+probe.states);
            require(probe.node().network()!=null&&probe.node().network()==hostNode.network()&&probe.node().isNeighborOf(hostNode),"Real inserted component is not connected to its host");
            CompoundTag data=data(stack);require("Initialized".equals(data.getString(PHASE))&&!data.getBoolean(CLOSED),"Hot insertion did not save the real initialized component back to its actual stack");
        }
        void removed(Probe probe,ItemStack stack){
            require(probe.states.equals(List.of(Lifecycle.LifecycleState.Constructing,Lifecycle.LifecycleState.Initializing,Lifecycle.LifecycleState.Initialized,Lifecycle.LifecycleState.Disposing,Lifecycle.LifecycleState.Disposed)),"Real host missed or reordered lifecycle transitions: "+probe.states);
            require(probe.disconnects>0&&probe.node().network()==null,"Actual removed component remained attached to its host");
            CompoundTag data=data(stack);require(data.getBoolean(CLOSED),"Actual removed ItemStack was saved before its component node disconnected");
            require("Disposing".equals(data.getString(PHASE))||"Disposed".equals(data.getString(PHASE)),"Removed stack contains an active-phase save");
            require(probe.node().address()!=null&&probe.node().address().equals(data.getCompound("node").getString("address")),"Hot removal lost the actual component's assigned node address");
        }
        boolean tick()throws Exception{
            require(++ticks<=60,"Machine-host contract exceeded 60 actual world ticks");if(ticks<3)return false;
            if(ticks==3){
                require(!computer.machine().isRunning()&&computer.machine().node().network()!=null&&adapter.node().network()!=null,"Actual placed hosts did not initialize as stopped normal world machines");
                energy();caseAndAdapter();blade();disk();
                computer.setItem(0,new ItemStack(CARD.get()));brokenCaseProbe=newest(Kind.CaseCard,computer);mounted(brokenCaseProbe,computer.getItem(0),computer.node());
                recordingDrops=true;try{require(level.destroyBlock(position,true),"Actual populated Case block could not be broken");}finally{recordingDrops=false;}return false;
            }
            if(ticks<5)return false;
            ItemStack dropped=ItemStack.EMPTY;
            // New forced chunks can still have HIDDEN entity sections. The public join event
            // observes vanilla's actual drop independently of its later visibility-index status.
            // isAddedToLevel below also rejects an observed but subsequently canceled spawn.
            for(ItemEntity entity:spawnedDrops)if(entity.isAddedToLevel()&&!entity.isRemoved()&&entity.getItem().is(CARD.get())&&brokenCaseProbe.node().address().equals(data(entity.getItem()).getCompound("node").getString("address"))){dropped=entity.getItem();break;}
            require(!dropped.isEmpty(),"Breaking the real Case did not yield its actual inserted component; actual join-event candidates="+spawnedDrops.stream().map(entity->entity.getItem()+" added="+entity.isAddedToLevel()+" removed="+entity.isRemoved()).toList());removed(brokenCaseProbe,dropped);
            results.add("PASS machine.hosts.drop breaking a populated real Case closes/disposes its component before vanilla copies the actual dropped item data");
            results.add("LIMIT machine.hosts: reuses prior separate-JVM Case/Rack VM and identity persistence evidence; client mountable views/tooltip keys, multiplayer ownership and UI clicks are not exercised by this dedicated-server contract");
            LOGGER.info("MACHINE_HOSTS PASS real hot removal, lifecycle/Server marker, HDD descriptor closure and Case-to-capacitor FE");return true;
        }
        void energy(){
            require(level.getBlockEntity(position.east()) instanceof Environment,"Real adjacent capacitor exposes no public Environment");Node node=((Environment)level.getBlockEntity(position.east())).node();
            require(node instanceof Connector&&computer.machine().node() instanceof Connector,"Actual capacitor/Case has no power connector");capacitor=(Connector)node;Connector power=(Connector)computer.machine().node();
            require(power.network()==capacitor.network()&&power.isNeighborOf(capacitor),"Real Case and physically adjacent capacitor are not on one network");
            require(computer.facing()!=Direction.EAST&&capacitor.localBufferSize()>0&&power.localBufferSize()>0,"Host energy fixture requires a non-front capacitor and positive real capacities");
            power.changeBuffer(Double.POSITIVE_INFINITY);equal(power.localBuffer(),power.localBufferSize(),"Could not fill the real Case buffer");
            equal(capacitor.changeBuffer(-capacitor.localBuffer()),0,"Could not drain only the actual capacitor's stored energy");equal(capacitor.localBuffer(),0,"Real external capacitor did not become empty");equal(power.localBuffer(),power.localBufferSize(),"Draining the external capacitor also drained the Case");
            IEnergyStorage energy=level.getCapability(Capabilities.EnergyStorage.BLOCK,position,Direction.EAST);require(energy!=null&&energy.canReceive(),"Actual Case exposes no non-front FE input");
            double ratio=API.config.getDouble("power.value.ForgeEnergy")/1000d;require(Double.isFinite(ratio)&&ratio>0,"Host FE proof requires a positive conversion");
            double before=power.globalBuffer(),localBefore=power.localBuffer();int simulated=energy.receiveEnergy(4096,true);require(simulated>0,"Case rejected FE because its local buffer is full despite real external room");
            equal(power.globalBuffer(),before,"Case FE simulation changed actual network energy");equal(capacitor.localBuffer(),0,"Case FE simulation changed the external capacitor");
            int received=energy.receiveEnergy(4096,false);require(received==simulated,"Actual Case FE execution differs from its immediately preceding simulation");
            equal(power.globalBuffer()-before,received*ratio,"Case did not store its reported FE in the actual network");equal(power.localBuffer(),localBefore,"Case FE overfilled or drained its already full local buffer");equal(capacitor.localBuffer(),received*ratio,"Case FE did not reach the actual external capacitor");
            require(level.getCapability(Capabilities.EnergyStorage.BLOCK,position,computer.facing())==null,"Actual Case exposes energy on its closed front");
            results.add("PASS machine.hosts.energy real Case FE SIM is immutable and EXEC reaches its physically adjacent capacitor while the Case local buffer is full; front face stays closed");
        }
        void caseAndAdapter(){
            ItemStack card=new ItemStack(CARD.get());require(computer.canPlaceItem(0,card),"Real Case rejected its valid public card driver");computer.setItem(0,card);
            Probe caseProbe=newest(Kind.CaseCard,computer);mounted(caseProbe,computer.getItem(0),computer.node());removed(caseProbe,computer.removeItem(0,1));
            ItemStack upgrade=new ItemStack(UPGRADE.get());require(adapter.canPlaceItem(0,upgrade),"Real Adapter rejected its valid public upgrade driver");adapter.setItem(0,upgrade);
            require(adapter instanceof EnvironmentHost,"Actual Adapter does not expose its public EnvironmentHost");Probe adapterProbe=newest(Kind.AdapterUpgrade,(EnvironmentHost)adapter);
            mounted(adapterProbe,adapter.getItem(0),adapter.node());removed(adapterProbe,adapter.removeItem(0,1));
            results.add("PASS machine.hosts.lifecycle real Case card and Adapter upgrade hot insertion/removal deliver Constructing/load/Initializing/Initialized/Disposing/Disposed and persist closed nodes into returned ItemStacks");
        }
        IItemHandler bladeInventory(ItemStack blade){IItemHandler inventory=Driver.itemHandlerFor(blade,actor);require(inventory!=null&&inventory.getSlots()==9,"Actual tier-one held blade exposes no nine-slot public inventory provider");return inventory;}
        void blade(){
            ItemStack marker=new ItemStack(SERVER_CARD.get()),blade=item("server1");IItemHandler inventory=bladeInventory(blade);
            require(Driver.driverFor(marker,Server.class)!=null,"Public Server marker-only driver was not registered before registry lock");
            require(inventory.isItemValid(0,marker)&&inventory.insertItem(0,marker,false).isEmpty(),"Actual held blade inventory did not validate/insert using the public Server marker");
            require(rack.canPlaceItem(0,blade),"Actual Rack rejected its valid tier-one server blade");rack.setItem(0,blade);
            require(rack.getMountable(0) instanceof Server,"Actual Rack did not create a public Server mountable");Server server=(Server)rack.getMountable(0);
            require(server instanceof Container,"Actual Server has no Minecraft component inventory");Container hardware=(Container)server;
            require(Driver.driverFor(marker,server.getClass())==null,"Marker fixture unexpectedly accepts the implementation class, concealing the marker regression");
            require(server.machine()!=null&&!server.machine().isRunning()&&server.machine().host()==server,"Actual inserted blade did not provide its own stopped real Machine");
            Probe child=newest(Kind.ServerCard,rack);mounted(child,hardware.getItem(0),server.node());
            require(server.componentSlot(child.node().address())==0,"Actual blade failed to expose the marker-only component in its first slot");
            ItemStack detached=hardware.removeItem(0,1);removed(child,detached);require(hardware.canPlaceItem(0,detached),"Mounted blade does not validate via the public Server marker");hardware.setItem(0,detached);
            Probe whole=newest(Kind.ServerCard,rack);require(whole!=child,"Mounted blade did not recreate a component after actual hot insertion");mounted(whole,hardware.getItem(0),server.node());
            ItemStack removedBlade=rack.removeItem(0,1);require(!removedBlade.isEmpty()&&rack.getMountable(0)==null,"Actual Rack did not remove the mounted blade");
            removed(whole,bladeInventory(removedBlade).getStackInSlot(0));require(server.node()==null||server.node().network()==null,"Removed blade kept its real machine network attached");
            results.add("PASS machine.hosts.server public Server marker-only external driver works through actual held/mounted inventories and Rack-created child environments; child and whole-blade removal persist disconnected nested component data without booting firmware");
        }
        Component filesystem(){
            for(Node node:computer.machine().node().network().nodes())if(node instanceof Component component&&"filesystem".equals(component.name())&&computer.componentSlot(node.address())==3)return component;
            throw new AssertionError("Actual inserted HDD did not create its first Case disk-slot filesystem");
        }
        void disk()throws Exception{
            ItemStack disk=item("hdd1");require(computer.canPlaceItem(3,disk),"Actual Case rejected a tier-one HDD");computer.setItem(3,disk);Component first=filesystem();
            String path="host-hot-"+UUID.randomUUID();byte[] bytes="closed-before-item-save".getBytes(StandardCharsets.UTF_8);
            Object handle=first.invoke("open",computer.machine(),path,"w")[0];require(Boolean.TRUE.equals(first.invoke("write",computer.machine(),handle,bytes)[0]),"Actual installed HDD could not write its test file");
            String address=first.address();ItemStack removed=computer.removeItem(3,1);require(first.network()==null,"Actual removed HDD stayed connected to its Case");computer.setItem(3,removed);Component restored=filesystem();
            require(address.equals(restored.address()),"Hot HDD item save/remount lost its real filesystem node identity");boolean rejected=false;
            try{restored.invoke("close",computer.machine(),handle);}catch(Exception error){require(error.getMessage()!=null&&error.getMessage().contains("bad file descriptor"),"Restored old HDD handle failed for an unrelated reason: "+error);rejected=true;}
            require(rejected,"Physical HDD removal saved and restored a handle that should have been closed");
            require(((Number)restored.invoke("size",computer.machine(),path)[0]).longValue()==bytes.length,"Closing before save discarded real HDD file contents");
            require(Boolean.TRUE.equals(restored.invoke("remove",computer.machine(),path)[0]),"Could not remove actual host filesystem fixture content");computer.removeItem(3,1);
            results.add("PASS machine.hosts.filesystem actual installed HDD open/write, physical removal and item remount retain file bytes/node identity while rejecting the old userdata descriptor closed before saving");
        }
        void fail(Throwable error){results.add("FAIL machine.hosts: "+error);LOGGER.error("MACHINE_HOSTS FAIL",error);}
        void cleanup(){
            if(cleaned)return;cleaned=true;
            List<BlockPos> positions=new ArrayList<>(restored.keySet());Collections.reverse(positions);
            for(BlockPos pos:positions)try{if(level.getBlockEntity(pos) instanceof Container container)container.clearContent();level.setBlock(pos,restored.get(pos),3);}catch(Throwable error){LOGGER.warn("Could not restore machine-host fixture cell {}",pos,error);}
            for(Probe probe:probes)try{probe.node().remove();}catch(Throwable error){LOGGER.warn("Could not detach host fixture probe",error);}
            for(ItemEntity entity:spawnedDrops)if(!entity.isRemoved())entity.discard();
            for(ItemEntity entity:level.getEntitiesOfClass(ItemEntity.class,bounds()))if(!oldItems.contains(entity.getId()))entity.discard();
            for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);
        }
    }
}
