package ocfixture;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Microcontroller;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Packet;
import li.cil.oc.api.network.SidedEnvironment;
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
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real placed microcontroller VM, EEPROM access, private graph and saved output-side routing. */
public final class MicrocontrollerPersistenceContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-microcontroller-persistence");
    private static final String PROCESS=UUID.randomUUID().toString(),MARKER="ocfixture:microcontroller_probe";
    private static final AtomicInteger SAVED_VALUES=new AtomicInteger(),LOADED_VALUES=new AtomicInteger();
    private static Run active;
    private MicrocontrollerPersistenceContract(){}
    public static void register(){Driver.add(new ProbeDriver());}
    public static void start(MinecraftServer server){try{active=new Run(server);active.start();}catch(Throwable error){if(active!=null)active.fail(error);else throw new IllegalStateException("Microcontroller persistence startup failed",error);}}
    public static void tick(){Run run=active;if(run==null||run.finished)return;try{run.tick();}catch(Throwable error){run.fail(error);}}
    public static void stopped(){Run run=active;if(run==null)return;if(run.pendingSuccess){run.report("PASS");LOGGER.info("HOST_PERSISTENCE_COMPLETE host=microcontroller stage={} architecture={} status=PASS",run.stage,run.architecture);}else if(!run.finished)run.fail(new AssertionError("Server stopped before microcontroller proof completed"));if(run.actor!=null)run.actor.channel.finishAndReleaseAll();active=null;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static ItemStack item(String key){var info=li.cil.oc.api.Items.get(key);require(info!=null,"Missing real microcontroller hardware "+key);return info.createItemStack(1);}
    private static CompoundTag data(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static List<ItemStack> hardware(Microcontroller host){List<ItemStack> result=new ArrayList<>();host.internalComponents().forEach(result::add);return result;}
    private static String signature(ItemStack stack){return stack.isEmpty()?"empty":BuiltInRegistries.ITEM.getKey(stack.getItem())+"*"+stack.getCount()+"|"+stack.getHoverName().getString()+"|"+data(stack).getString("mcu_component_fixture");}
    private static final class Run {
        final MinecraftServer server;
        final ServerLevel level;
        final String stage=System.getProperty("ocfixture.hostStage"),runId=System.getProperty("ocfixture.hostRunId"),architecture=System.getProperty("ocfixture.hostArchitecture","Lua 5.3");
        final boolean luaJ=architecture.equals("LuaJ");
        final Path directory=Path.of(System.getProperty("ocfixture.hostDirectory")).toAbsolutePath().normalize();
        final Properties manifest=new Properties();
        final List<String> evidence=new ArrayList<>();
        final BlockPos position=new BlockPos(4096,96,4096);
        TestPlayer actor;
        BlockEntity entity;
        Microcontroller host;
        SidedEnvironment sides;
        Probe probe;
        int ticks;
        boolean ready,finished,pendingSuccess;
        Run(MinecraftServer server){this.server=server;level=server.overworld();}
        void start()throws Exception{
            require(stage.equals("prepare")||stage.equals("resume"),"Microcontroller stage must be prepare or resume");require(directory.equals(Path.of("").toAbsolutePath().normalize())&&directory.getParent().getFileName().toString().equals("microcontroller")&&directory.getParent().getParent().getFileName().toString().equals("hosts")&&directory.getFileName().toString().equals(runId),"Refusing a non-fixture microcontroller directory");require(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(directory.resolve("ocfixture-host-world")),"Refusing a different Minecraft world");level.setChunkForced(position.getX()>>4,position.getZ()>>4,true);level.getChunkAt(position);actor=new TestPlayer(level);actor.getAbilities().instabuild=true;actor.setPos(position.getX()+.5,position.getY(),position.getZ()-2);actor.setYRot(0);
            if(stage.equals("prepare"))prepare();else resume();evidence.add("process.pid="+ProcessHandle.current().pid());evidence.add("process.current="+PROCESS);evidence.add("hdd=not allowed by the original microcontroller templates; no HDD or synthetic storage added");LOGGER.info("HOST_PERSISTENCE host=microcontroller stage={} architecture={} run={}",stage,architecture,runId);
        }
        void prepare()throws Exception{
            require(!Files.exists(directory.resolve("checkpoint.properties")),"Prepare cannot overwrite a microcontroller checkpoint");require(level.isEmptyBlock(position),"Isolated microcontroller cell is occupied");require(CreativeTab.instance!=null,"Real configured creative microcontroller is unavailable");CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(),true,level.registryAccess()));ItemStack configured=ItemStack.EMPTY;
            for(ItemStack stack:CreativeTab.instance.getDisplayItems())if(stack.is(item("microcontroller").getItem())&&data(stack).getByte("oc:tier")==3){configured=stack.copy();break;}require(!configured.isEmpty()&&configured.getItem() instanceof BlockItem,"Creative tab did not provide the original configured microcontroller BlockItem");
            CompoundTag root=data(configured);ListTag parts=new ListTag();boolean cpu=false,marked=false;int upgrades=0;var selected=li.cil.oc.api.Machine.architectures().stream().filter(type->li.cil.oc.api.Machine.getArchitectureName(type).equals(architecture)).findFirst().orElseThrow(()->new AssertionError("Requested microcontroller architecture unavailable: "+architecture));
            for(Tag raw:root.getList("oc:components",Tag.TAG_COMPOUND)){ItemStack part=ItemStack.parseOptional(level.registryAccess(),(CompoundTag)raw);if(part.isEmpty())continue;var driver=Driver.driverFor(part);if(driver!=null){String kind=driver.slot(part);require(!kind.equals(li.cil.oc.api.driver.item.Slot.HDD),"Configured original microcontroller contains a forbidden HDD");if(kind.equals(li.cil.oc.api.driver.item.Slot.Upgrade)){upgrades++;if(!marked){part.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("microcontroller-hardware"));CustomData.update(DataComponents.CUSTOM_DATA,part,tag->tag.putString("mcu_component_fixture","kept"));marked=true;}}if(kind.equals(li.cil.oc.api.driver.item.Slot.CPU)&&driver instanceof MutableProcessor processor){processor.setArchitecture(part,selected);cpu=true;}}parts.add(part.saveOptional(level.registryAccess()));}
            require(cpu&&marked&&upgrades<9,"Original creative microcontroller has no CPU or permitted free upgrade slot");ItemStack observation=new ItemStack(Items.PAPER);CustomData.update(DataComponents.CUSTOM_DATA,observation,tag->tag.putBoolean(MARKER,true));parts.add(observation.saveOptional(level.registryAccess()));root.put("oc:components",parts);configured.set(DataComponents.CUSTOM_DATA,CustomData.of(root));actor.setItemInHand(InteractionHand.MAIN_HAND,configured);
            require(((BlockItem)configured.getItem()).place(new BlockPlaceContext(actor,InteractionHand.MAIN_HAND,configured,new BlockHitResult(Vec3.atCenterOf(position),Direction.UP,position,false))).consumesAction(),"Actual assembled microcontroller BlockItem.place failed");attach();actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);use(true);AbstractContainerMenu menu=actor.containerMenu;require(menu!=actor.inventoryMenu&&!menu.slots.isEmpty()&&menu.stillValid(actor),"Actual sneaking use did not open the real authorized EEPROM menu");if(menu.slots.getFirst().hasItem())menu.slots.getFirst().remove(1);ItemStack bios=li.cil.oc.api.Items.registerEEPROM("Microcontroller cold fixture",BIOS.getBytes(StandardCharsets.UTF_8),new byte[0],false);require(menu.slots.getFirst().mayPlace(bios),"Actual microcontroller menu rejected a real EEPROM");menu.slots.getFirst().setByPlayer(bios);require(!menu.slots.getFirst().getItem().isEmpty(),"Real EEPROM menu did not install the program");actor.closeFixtureMenu();evidence.add("hardware=original creative microcontroller placed through BlockItem.place; allowed SDK observation upgrade; EEPROM installed through its actual sneaking-use inventory menu");
        }
        void attach(){entity=level.getBlockEntity(position);require(entity instanceof Microcontroller&&entity instanceof SidedEnvironment&&entity instanceof Analyzable,"World did not load the actual public microcontroller host");host=(Microcontroller)entity;sides=(SidedEnvironment)entity;require(host.machine()!=null&&host.machine().host()==host,"Actual microcontroller BE does not own its VM");}
        void use(boolean inventory){actor.setShiftKeyDown(inventory);require(level.getBlockState(position).useWithoutItem(level,actor,new BlockHitResult(Vec3.atCenterOf(position),host.facing(),position,false)).consumesAction(),"Actual microcontroller block interaction failed");actor.setShiftKeyDown(false);}
        void resume()throws Exception{
            Path prepared=directory.resolve("prepare-results.txt");require(Files.isRegularFile(prepared)&&Files.readAllLines(prepared,StandardCharsets.UTF_8).getFirst().equals("status=PASS"),"Microcontroller prepare did not finish normal shutdown");try(InputStream input=Files.newInputStream(directory.resolve("checkpoint.properties"))){manifest.load(input);}require(runId.equals(manifest.getProperty("run_id"))&&"microcontroller".equals(manifest.getProperty("host"))&&architecture.equals(manifest.getProperty("architecture"))&&"prepared".equals(manifest.getProperty("phase")),"Microcontroller checkpoint selection differs or was consumed");require(!PROCESS.equals(manifest.getProperty("process")),"Microcontroller resume must run in another Java process");require(directory.resolve("ocfixture-host-world").toString().equals(manifest.getProperty("world")),"Checkpoint belongs to a different Minecraft world");attach();evidence.add("process.prepare="+manifest.getProperty("process"));
        }
        boolean connected(){if(host==null||host.machine().node().network()==null)return false;for(Node node:host.machine().node().network().nodes())if(node.host() instanceof Probe observation){probe=observation;return host.componentSlot(node.address())>=0;}return false;}
        void tick()throws Exception{
            require(++ticks<=1000,"Microcontroller cold stage exceeded 1000 ticks; ready="+ready+", error="+(host==null?"no host":host.machine().lastError()));
            if(!ready){if(!connected()){require(ticks<100,"Actual microcontroller component lifecycle did not initialize");return;}require(host.machine().componentCount()<=host.machine().maxComponents(),"Actual microcontroller exceeds its real CPU component budget");
                if(stage.equals("prepare")){use(false);require(host.machine().isRunning(),"Actual ordinary block use did not boot the microcontroller: "+host.machine().lastError());}
                else{verifyIdentities();verifyHardwareAndSides();require(probe.boots.get()==1||luaJ&&probe.boots.get()==2,"Microcontroller observation upgrade lost its saved BIOS count");require(host.machine().isRunning()||luaJ&&probe.completed.get(),"World-loaded microcontroller did not restore an active VM: "+host.machine().lastError());if(!probe.completed.get()&&host.machine().isPaused())require(host.machine().start(),"Cannot release the intentionally paused saved microcontroller checkpoint");}
                ready=true;return;
            }
            require(host.machine().lastError()==null,"Actual microcontroller VM failed: "+host.machine().lastError());require(host.machine().isRunning()||probe.completed.get(),"Microcontroller stopped before proof callbacks");
            if(stage.equals("prepare")&&probe.requests.get()==1&&host.machine().isPaused()){
                require(probe.boots.get()==1,"Microcontroller BIOS restarted before checkpoint");captureIdentities();captureHardwareAndSides();verifyRouting();require(host.machine().signal("microcontroller_cold_resume",2718,"microcontroller-queued-signal"),"Cannot queue the actual microcontroller cold-resume signal");saveWorld();require(luaJ||SAVED_VALUES.get()>0,"Actual microcontroller world save did not serialize native userdata");manifest.setProperty("run_id",runId);manifest.setProperty("host","microcontroller");manifest.setProperty("architecture",architecture);manifest.setProperty("phase","prepared");manifest.setProperty("process",PROCESS);manifest.setProperty("world",directory.resolve("ocfixture-host-world").toString());saveManifest();evidence.add("checkpoint=actual placed microcontroller private VM and six-side routing saved in world NBT");evidence.add("value.save_calls="+SAVED_VALUES.get());success();
            }else if(stage.equals("resume")&&probe.completed.get()){
                require(probe.boots.get()==(luaJ?2:1),"Microcontroller BIOS boot count differs from the selected architecture contract");require(luaJ?LOADED_VALUES.get()==0:LOADED_VALUES.get()>0,"Microcontroller userdata load differs from native/LuaJ semantics");verifyIdentities();verifyHardwareAndSides();verifyRouting();evidence.add("proof="+(luaJ?"actual world-loaded microcontroller genuinely rebooted LuaJ BIOS once; EEPROM, actual hardware/owner, nodes and six-side routing persisted":"actual world-loaded microcontroller resumed native locals, nested coroutine, ItemStack/iterator userdata, open tmpfs descriptor offset, EEPROM, queued signal, private/outer node identities and six-side routing"));evidence.add("boots="+probe.boots.get());evidence.add("value.load_calls="+LOADED_VALUES.get());manifest.setProperty("phase","complete");saveWorld();saveManifest();success();
            }
        }
        Node outerControl(Direction side){Node[] analyzed=((Analyzable)host).onAnalyze(actor,side,.5F,.5F,.5F);require(analyzed!=null&&analyzed.length==1,"Actual microcontroller analyzer did not return one side node");return analyzed[0];}
        void captureIdentities(){Machine machine=host.machine();manifest.setProperty("machine",machine.node().address());manifest.setProperty("tmp",machine.tmpAddress());manifest.setProperty("probe",probe.node().address());manifest.setProperty("eeprom",probe.flash);manifest.setProperty("control",probe.control);machine.components().forEach((address,type)->manifest.setProperty("component."+address,type));require(host.componentSlot(probe.flash)>=0,"Microcontroller EEPROM is not real installed hardware");require(outerControl(host.facing())==machine.node(),"Front analyzer did not expose the actual private machine node");for(Direction side:Direction.values())if(side!=host.facing()){Node port=sides.sidedNode(side);require(port!=null,"Actual non-front microcontroller side has no port");manifest.setProperty("port."+side.get3DDataValue(),port.address());manifest.setProperty("outer_control."+side.get3DDataValue(),outerControl(side).address());}}
        void verifyIdentities(){require(host.machine().host()==entity&&manifest.getProperty("machine").equals(host.machine().node().address())&&manifest.getProperty("tmp").equals(host.machine().tmpAddress())&&manifest.getProperty("probe").equals(probe.node().address()),"Microcontroller host/private VM/tmpfs/upgrade identity changed");require(host.componentSlot(manifest.getProperty("eeprom"))>=0,"Saved EEPROM is no longer a real installed microcontroller component");for(String key:manifest.stringPropertyNames())if(key.startsWith("component."))require(manifest.getProperty(key).equals(host.machine().components().get(key.substring(10))),"Microcontroller lost an actual saved internal component: "+key);for(Direction side:Direction.values())if(side!=host.facing()){Node port=sides.sidedNode(side);require(port!=null&&port.address().equals(manifest.getProperty("port."+side.get3DDataValue()))&&outerControl(side).address().equals(manifest.getProperty("outer_control."+side.get3DDataValue())),"Microcontroller lost an actual saved outer port/component identity: "+side);}}
        void captureHardwareAndSides()throws Exception{List<ItemStack> parts=hardware(host);manifest.setProperty("hardware.count",Integer.toString(parts.size()));for(int index=0;index<parts.size();index++)manifest.setProperty("hardware."+index,signature(parts.get(index)));CompoundTag tag=entity.saveWithFullMetadata(level.registryAccess());manifest.setProperty("block",BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).toString());manifest.setProperty("facing",host.facing().getName());manifest.setProperty("owner",tag.getString("oc:owner"));require(tag.hasUUID("oc:ownerUuid"),"Placed microcontroller did not save its real owner UUID");manifest.setProperty("owner_uuid",tag.getUUID("oc:ownerUuid").toString());byte[] outputs=tag.getByteArray("oc:outputs");require(outputs.length==6,"Actual microcontroller did not save all six output states");for(int side=0;side<6;side++)manifest.setProperty("output."+side,Boolean.toString(outputs[side]!=0));verifyHardwareAndSides();}
        void verifyHardwareAndSides()throws Exception{
            List<ItemStack> parts=hardware(host);require(parts.size()==Integer.parseInt(manifest.getProperty("hardware.count")),"Actual microcontroller hardware inventory changed size");boolean marked=false;for(int index=0;index<parts.size();index++){ItemStack part=parts.get(index);require(signature(part).equals(manifest.getProperty("hardware."+index)),"Actual microcontroller ItemStack data changed in slot "+index);marked|=data(part).getString("mcu_component_fixture").equals("kept");}require(marked,"Microcontroller lost its actual custom-named/component-data hardware item");require(BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).toString().equals(manifest.getProperty("block"))&&host.facing().getName().equals(manifest.getProperty("facing")),"Actual microcontroller block or facing changed");CompoundTag tag=entity.saveWithFullMetadata(level.registryAccess());require(tag.getString("oc:owner").equals(manifest.getProperty("owner"))&&tag.hasUUID("oc:ownerUuid")&&tag.getUUID("oc:ownerUuid").toString().equals(manifest.getProperty("owner_uuid")),"Microcontroller owner world NBT did not persist");byte[] outputs=tag.getByteArray("oc:outputs");require(outputs.length==6,"Actual microcontroller world NBT lost six output flags");Node node=host.machine().node().network().node(manifest.getProperty("control"));require(node instanceof Component,"Actual internal microcontroller callback component is missing");Component control=(Component)node;
            for(Direction side:Direction.values()){int index=side.get3DDataValue();boolean expected=side==host.facing()||index%2==1;require((outputs[index]!=0)==expected&&Boolean.parseBoolean(manifest.getProperty("output."+index))==expected,"Actual saved microcontroller output state differs: "+side);if(side==host.facing())require(sides.sidedNode(side)==null,"Original front side incorrectly exposes an outer network");else require(Boolean.valueOf(expected).equals(control.invoke("isSideOpen",host.machine(),index)[0]),"Actual component callback lost its configured side state: "+side);}
        }
        void verifyRouting(){Endpoint[] endpoints=new Endpoint[6];try{require(sides.sidedNode(host.facing())==null,"Original microcontroller front is not isolated");for(Direction side:Direction.values())if(side!=host.facing()){int index=side.get3DDataValue();Node port=sides.sidedNode(side);require(port!=null&&port.network()!=host.machine().node().network(),"Actual microcontroller outer side merged into the private VM graph: "+side);for(int previous=0;previous<index;previous++)if(endpoints[previous]!=null)require(port.network()!=endpoints[previous].node().network(),"Separate physical microcontroller sides were merged");Endpoint endpoint=new Endpoint();endpoints[index]=endpoint;port.connect(endpoint.node());require(endpoint.node().network()==port.network(),"Actual external endpoint did not join its microcontroller side graph");require(!probe.node().canBeReachedFrom(endpoint.node()),"Private SDK probe became directly reachable through an outside port");String marker="mcu-in-"+stage+"-"+index;endpoint.node().sendToReachable("network.message",Network.newPacket(endpoint.node().address(),null,20173,new Object[]{marker}));require(probe.received.contains(marker),"Actual microcontroller did not bridge an outside message into its private graph: "+side);}
                String outgoing="mcu-out-"+stage;probe.node().sendToReachable("network.message",Network.newPacket(probe.node().address(),null,20173,new Object[]{outgoing}));for(int index=0;index<6;index++)if(endpoints[index]!=null)require(endpoints[index].received.contains(outgoing)==Boolean.parseBoolean(manifest.getProperty("output."+index)),"Actual output-side flag did not control outgoing network messages: "+Direction.from3DDataValue(index));evidence.add("routing."+stage+"=five isolated outer graphs remain separate from the private VM; all non-front inputs arrive, only explicitly open outputs forward real SDK packets");
            }finally{for(Endpoint endpoint:endpoints)if(endpoint!=null)endpoint.node().remove();}}
        void saveWorld(){entity.setChanged();require(server.saveEverything(false,true,true),"Minecraft did not save the actual microcontroller world");}
        void saveManifest()throws Exception{try(OutputStream output=Files.newOutputStream(directory.resolve("checkpoint.properties"))){manifest.store(output,"Actual microcontroller cold-process world checkpoint");}}
        void success(){finished=true;pendingSuccess=true;report("PENDING_STOP");server.halt(false);}
        void fail(Throwable error){if(finished&&!pendingSuccess)return;finished=true;pendingSuccess=false;evidence.add("failure="+error);LOGGER.error("HOST_PERSISTENCE FAIL host=microcontroller stage={}",stage,error);report("FAIL");server.halt(false);}
        void report(String status){List<String> lines=new ArrayList<>();lines.add("status="+status);lines.add("host=microcontroller");lines.add("stage="+stage);lines.add("architecture="+architecture);lines.add("run_id="+runId);lines.addAll(evidence);try{Files.write(directory.resolve(stage+"-results.txt"),lines,StandardCharsets.UTF_8);}catch(Exception error){throw new IllegalStateException("Cannot write microcontroller report",error);}}
    }
    private static String packetMarker(Message message){if(!message.name().equals("network.message")||message.data().length!=1||!(message.data()[0] instanceof Packet packet)||packet.port()!=20173||packet.data().length!=1||!(packet.data()[0] instanceof String marker))return null;return marker;}
    private static final class Endpoint extends AbstractManagedEnvironment {
        final java.util.Set<String> received=ConcurrentHashMap.newKeySet();
        Endpoint(){setNode(Network.newNode(this,Visibility.Network).create());}
        @Override public void onMessage(Message message){String marker=packetMarker(message);if(marker!=null)received.add(marker);}
    }
    /** Public mock transport for the real block/menu path; no remote-player round trip is claimed. */
    private static final class TestPlayer extends FakePlayer {
        final EmbeddedChannel channel=new EmbeddedChannel();
        private int menuId;
        TestPlayer(ServerLevel level){super(level,new GameProfile(UUID.fromString("a32319c9-4cf3-4d49-8de4-851e417dad18"),"OC_McuFixture"));Connection transport=new Connection(PacketFlow.SERVERBOUND){@Override public Channel channel(){return TestPlayer.this.channel;}@Override public boolean isConnected(){return TestPlayer.this.channel.isOpen();}@Override public void send(net.minecraft.network.protocol.Packet<?> packet,PacketSendListener listener,boolean flush){}};NetworkRegistry.configureMockConnection(transport);connection=new ServerGamePacketListenerImpl(level.getServer(),transport,this,CommonListenerCookie.createInitial(getGameProfile(),false));}
        @Override public OptionalInt openMenu(MenuProvider provider,Consumer<RegistryFriendlyByteBuf> extraDataWriter){if(provider==null)return OptionalInt.empty();closeFixtureMenu();AbstractContainerMenu opened=provider.createMenu(++menuId,getInventory(),this);if(opened==null)return OptionalInt.empty();containerMenu=opened;return OptionalInt.of(opened.containerId);}
        @Override public boolean hasDisconnected(){return false;}
        void closeFixtureMenu(){if(containerMenu!=inventoryMenu){containerMenu.removed(this);containerMenu=inventoryMenu;}}
    }
    private static final class ProbeDriver extends li.cil.oc.api.prefab.DriverItem implements HostAware {
        @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.is(Items.PAPER)&&data(stack).getBoolean(MARKER);}
        @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&Microcontroller.class.isAssignableFrom(host);}
        @Override public String slot(ItemStack stack){return li.cil.oc.api.driver.item.Slot.Upgrade;}
        @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){return host instanceof Microcontroller microcontroller?new Probe(microcontroller):null;}
    }
    public static final class Probe extends AbstractManagedEnvironment {
        final Microcontroller host;
        final AtomicInteger boots=new AtomicInteger(),requests=new AtomicInteger();
        final AtomicBoolean completed=new AtomicBoolean();
        final java.util.Set<String> received=ConcurrentHashMap.newKeySet();
        volatile String flash,control;
        Probe(Microcontroller host){this.host=host;setNode(Network.newNode(this,Visibility.Network).withComponent("microcontroller_persistence_probe",Visibility.Neighbors).create());}
        @Override public void onMessage(Message message){String marker=packetMarker(message);if(marker!=null)received.add(marker);}
        @Callback(direct=true,limit=64)public Object[] booted(Context context,Arguments args){host.markChanged();return new Object[]{boots.incrementAndGet()};}
        @Callback(direct=true,limit=64)public Object[] value(Context context,Arguments args){MicrocontrollerValue value=new MicrocontrollerValue();value.seed(context,args);return new Object[]{value};}
        @Callback(direct=true,limit=64)public Object[] front(Context context,Arguments args){return new Object[]{host.facing().get3DDataValue()};}
        @Callback(direct=true,limit=64)public Object[] checkpoint(Context context,Arguments args){require(context instanceof Machine machine&&machine.host()==host&&host==active.host,"Checkpoint did not originate in the actual placed microcontroller VM");require(boots.get()==1&&requests.get()==0,"Microcontroller reached an unexpected initial checkpoint");flash=args.checkString(0);control=args.checkString(1);require(context.pause(600),"Cannot pause actual microcontroller at a serializable Lua yield");requests.set(1);host.markChanged();return new Object[]{true};}
        void proof(Context context,Arguments args){Run run=java.util.Objects.requireNonNull(active);require(run.stage.equals("resume")&&context instanceof Machine machine&&machine.host()==host&&host==run.entity,"Proof did not originate in the world-loaded real microcontroller BE");require(args.checkString(0).equals(run.manifest.getProperty("machine"))&&args.checkString(1).equals(run.manifest.getProperty("tmp"))&&args.checkString(2).equals(run.manifest.getProperty("eeprom"))&&args.checkString(3).equals(run.manifest.getProperty("control")),"Microcontroller Lua locals lost saved node identities");}
        @Callback(direct=true,limit=64)public Object[] verified(Context context,Arguments args){proof(context,args);require(!active.luaJ&&boots.get()==1&&args.checkInteger(4)==314159,"Native microcontroller BIOS restarted or lost its retained local");completed.set(true);host.markChanged();return new Object[]{true};}
        @Callback(direct=true,limit=64)public Object[] rebooted(Context context,Arguments args){proof(context,args);require(active.luaJ&&boots.get()==2,"LuaJ microcontroller did not genuinely reboot BIOS exactly once");completed.set(true);host.markChanged();return new Object[]{true};}
        @Override public void load(CompoundTag tag){super.load(tag);boots.set(tag.getInt("boots"));requests.set(tag.getInt("requests"));completed.set(tag.getBoolean("complete"));flash=tag.getString("flash");control=tag.getString("control");}
        @Override public void save(CompoundTag tag){super.save(tag);tag.putInt("boots",boots.get());tag.putInt("requests",requests.get());tag.putBoolean("complete",completed.get());if(flash!=null)tag.putString("flash",flash);if(control!=null)tag.putString("control",control);}
    }
    public static final class MicrocontrollerValue extends AbstractValue {
        private int counter=-1;
        private ItemStack stack=ItemStack.EMPTY;
        private ItemStackArrayValue iterator;
        private HolderLookup.Provider loadedWith;
        public MicrocontrollerValue(){}
        void seed(Context context,Arguments args){require(context instanceof Machine machine&&machine.host() instanceof Microcontroller,"Value did not originate in an actual microcontroller");HolderLookup.Provider registries=((Machine)context).host().world().registryAccess();counter=43;stack=new ItemStack(Items.EMERALD,7);stack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("microcontroller-cold-value"));CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putString("microcontroller_payload","kept"));iterator=new ItemStackArrayValue(new ItemStack[]{ItemStack.EMPTY,new ItemStack(Items.DIAMOND,3)},registries);iterator.call(context,args);}
        @Override public void save(CompoundTag tag){throw new AssertionError("Microcontroller Value lost registry provider on save");}
        @Override public void load(CompoundTag tag){throw new AssertionError("Microcontroller Value lost registry provider on load");}
        @Override public void save(CompoundTag tag,HolderLookup.Provider registries){require(registries!=null&&iterator!=null&&!stack.isEmpty(),"Microcontroller Value was not initialized through its real Lua callback");tag.putInt("counter",counter);tag.put("stack",stack.save(registries));CompoundTag state=new CompoundTag();iterator.save(state,registries);tag.put("iterator",state);SAVED_VALUES.incrementAndGet();}
        @Override public void load(CompoundTag tag,HolderLookup.Provider registries){require(registries!=null,"Microcontroller Value restore has no world registry");counter=tag.getInt("counter");stack=ItemStack.parseOptional(registries,tag.getCompound("stack"));iterator=new ItemStackArrayValue();iterator.load(tag.getCompound("iterator"),registries);loadedWith=registries;LOADED_VALUES.incrementAndGet();}
        @Callback(direct=true,limit=64)public Object[] snapshot(Context context,Arguments args){require(context instanceof Machine machine&&machine.host() instanceof Microcontroller&&loadedWith==machine.host().world().registryAccess(),"Microcontroller Value was not reconstructed with the invoking world's registry");return new Object[]{counter,stack.getCount(),stack.getHoverName().getString(),data(stack).getString("microcontroller_payload")};}
        @Callback(direct=true,limit=64)public Object[] nextCount(Context context,Arguments args){Object[] next=iterator.call(context,args);return new Object[]{next==null?0:((ItemStack)next[0]).getCount()};}
    }
    private static final String BIOS="""
        local p=component.proxy(assert(component.list("microcontroller_persistence_probe",true)()))
        local boot=p.booted()
        local machine,tmp=computer.address(),computer.tmpAddress()
        local flash=assert(component.list("eeprom",true)())
        local control=assert(component.list("microcontroller",true)())
        local mcu=component.proxy(control)
        if boot>1 then
          assert(computer.getArchitecture()=="LuaJ","native microcontroller rebooted BIOS")
          assert(component.invoke(flash,"getData")=="microcontroller-eeprom-data")
          for side=0,5 do if side~=p.front() then assert(mcu.isSideOpen(side)==(side%2==1)) end end
          assert(p.rebooted(machine,tmp,flash,control))
          while true do computer.pullSignal() end
        end
        local retained=314159
        local co=coroutine.create(function() local n=271828 coroutine.yield() return n+1 end)
        assert(coroutine.resume(co))
        local value=p.value()
        local fs=component.proxy(tmp)
        local w=assert(fs.open("microcontroller-tmp","w")) assert(fs.write(w,"abcdefghi")) fs.close(w)
        local reader=assert(fs.open("microcontroller-tmp","r")) assert(fs.read(reader,3)=="abc")
        component.invoke(flash,"setData","microcontroller-eeprom-data")
        for side=0,5 do if side~=p.front() then mcu.setSideOpen(side,side%2==1) end end
        assert(p.checkpoint(flash,control))
        local event,number,text
        repeat event,number,text=computer.pullSignal() until event=="microcontroller_cold_resume"
        assert(number==2718 and text=="microcontroller-queued-signal","microcontroller queued signal lost")
        assert(retained==314159,"microcontroller Lua local lost")
        local ok,n=coroutine.resume(co) assert(ok and n==271829,"microcontroller nested coroutine lost")
        local count,size,label,tag=value.snapshot()
        assert(count==43 and size==7 and label=="microcontroller-cold-value" and tag=="kept","microcontroller userdata ItemStack lost")
        assert(value.nextCount()==3,"microcontroller iterator cursor lost")
        assert(fs.read(reader,6)=="defghi","microcontroller tmpfs open handle/offset lost")
        assert(component.invoke(flash,"getData")=="microcontroller-eeprom-data")
        for side=0,5 do if side~=p.front() then assert(mcu.isSideOpen(side)==(side%2==1)) end end
        fs.close(reader) assert(p.verified(machine,tmp,flash,control,retained))
        while true do computer.pullSignal() end
        """;
}
