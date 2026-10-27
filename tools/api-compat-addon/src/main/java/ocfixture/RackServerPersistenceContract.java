package ocfixture;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.component.RackBusConnectable;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Rack;
import li.cil.oc.api.internal.Server;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Packet;
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
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
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

/** A real rack blade, its owned VM and primary/secondary bus configuration across separate JVMs. */
public final class RackServerPersistenceContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-rack-persistence");
    private static final String PROCESS=UUID.randomUUID().toString(),MARKER="ocfixture:rack_probe";
    private static final AtomicInteger SAVED_VALUES=new AtomicInteger(),LOADED_VALUES=new AtomicInteger();
    private static Run active;
    private RackServerPersistenceContract(){}
    public static void register(){Driver.add(new ProbeDriver());}
    public static void start(MinecraftServer server){try{active=new Run(server);active.start();}catch(Throwable error){if(active!=null)active.fail(error);else throw new IllegalStateException("Rack persistence startup failed",error);}}
    public static void tick(){Run run=active;if(run==null||run.finished)return;try{run.tick();}catch(Throwable error){run.fail(error);}}
    public static void stopped(){Run run=active;if(run==null)return;if(run.pendingSuccess){run.report("PASS");LOGGER.info("HOST_PERSISTENCE_COMPLETE host=rack stage={} architecture={} status=PASS",run.stage,run.architecture);}else if(!run.finished)run.fail(new AssertionError("Server stopped before rack proof completed"));if(run.actor!=null)run.actor.channel.finishAndReleaseAll();active=null;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static ItemStack item(String key){var info=li.cil.oc.api.Items.get(key);require(info!=null,"Missing real rack hardware "+key);return info.createItemStack(1);}
    private static CompoundTag data(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static List<ItemStack> hardware(Server host){List<ItemStack> result=new ArrayList<>();host.internalComponents().forEach(result::add);return result;}
    private static String signature(ItemStack stack){return stack.isEmpty()?"empty":BuiltInRegistries.ITEM.getKey(stack.getItem())+"*"+stack.getCount()+"|"+stack.getHoverName().getString()+"|"+data(stack).getString("rack_hardware_fixture");}
    private static final class Run {
        final MinecraftServer minecraft;
        final ServerLevel level;
        final String stage=System.getProperty("ocfixture.hostStage"),runId=System.getProperty("ocfixture.hostRunId"),architecture=System.getProperty("ocfixture.hostArchitecture","Lua 5.3");
        final boolean luaJ=architecture.equals("LuaJ");
        final Path directory=Path.of(System.getProperty("ocfixture.hostDirectory")).toAbsolutePath().normalize();
        final Properties manifest=new Properties();
        final List<String> evidence=new ArrayList<>();
        final BlockPos position=new BlockPos(4096,96,4096);
        TestPlayer actor;
        BlockEntity entity;
        Rack rack;
        Server blade,peer;
        Probe probe,peerProbe;
        int ticks;
        boolean ready,finished,pendingSuccess;
        Run(MinecraftServer server){minecraft=server;level=server.overworld();}
        void start()throws Exception{
            require(stage.equals("prepare")||stage.equals("resume"),"Rack stage must be prepare or resume");require(directory.equals(Path.of("").toAbsolutePath().normalize())&&directory.getParent().getFileName().toString().equals("rack")&&directory.getParent().getParent().getFileName().toString().equals("hosts")&&directory.getFileName().toString().equals(runId),"Refusing a non-fixture rack directory");require(minecraft.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(directory.resolve("ocfixture-host-world")),"Refusing a different Minecraft world");level.setChunkForced(position.getX()>>4,position.getZ()>>4,true);level.getChunkAt(position);
            actor=new TestPlayer(level);minecraft.getPlayerList().placeNewPlayer(actor.transport,actor,CommonListenerCookie.createInitial(actor.getGameProfile(),false));require(minecraft.getPlayerList().getPlayer(actor.getUUID())==actor,"Public PlayerList login did not register the owner used by Machine.addUser");actor.getAbilities().instabuild=true;actor.setPos(position.getX()+.5,position.getY(),position.getZ()-2);actor.setYRot(0);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            if(stage.equals("prepare"))prepare();else resume();evidence.add("process.pid="+ProcessHandle.current().pid());evidence.add("process.current="+PROCESS);evidence.add("owner=Machine.users set through public addUser after public PlayerList.placeNewPlayer; local NeoForge mock transport, no remote client round trip claimed");evidence.add("peer=second actual stopped blade with its real addon card on the other primary bus; only the first blade's native VM is under cold-restoration proof");LOGGER.info("HOST_PERSISTENCE host=rack stage={} architecture={} run={}",stage,architecture,runId);
        }
        void prepare()throws Exception{
            require(!Files.exists(directory.resolve("checkpoint.properties")),"Prepare cannot overwrite a rack checkpoint");require(level.isEmptyBlock(position),"Isolated rack cell is occupied");ItemStack block=item("rack");require(block.getItem() instanceof BlockItem,"Real rack item is not placeable");actor.setItemInHand(InteractionHand.MAIN_HAND,block);require(((BlockItem)block.getItem()).place(new BlockPlaceContext(actor,InteractionHand.MAIN_HAND,block,new BlockHitResult(Vec3.atCenterOf(position),Direction.UP,position,false))).consumesAction(),"Actual rack BlockItem.place failed");attachRack();
            ItemStack main=configureBlade(true),other=configureBlade(false);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);openRack();require(actor.containerMenu.slots.get(0).mayPlace(main)&&actor.containerMenu.slots.get(1).mayPlace(other),"Real rack menu rejected original server blades");actor.containerMenu.slots.get(0).setByPlayer(main);actor.containerMenu.slots.get(1).setByPlayer(other);actor.closeFixtureMenu();evidence.add("hardware=real creative server blade filled through its public item menu with permitted CPU3, two RAM6, HDD3, redstonecard2, EEPROM BIOS and SDK card; physically installed through real RackMenu");
        }
        ItemStack configureBlade(boolean main)throws Exception{
            ItemStack stack=item("servercreative");stack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal(main?"rack-cold-blade":"rack-peer-blade"));CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putString("rack_blade_fixture",main?"main":"peer"));actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setShiftKeyDown(false);require(stack.getItem().use(level,actor,InteractionHand.MAIN_HAND).getResult().consumesAction(),"Actual server item did not open its inventory");AbstractContainerMenu menu=actor.containerMenu;require(menu!=actor.inventoryMenu&&menu.stillValid(actor),"Real held server inventory was not available");
            List<ItemStack> parts=new ArrayList<>();ItemStack card=new ItemStack(Items.PAPER);CustomData.update(DataComponents.CUSTOM_DATA,card,tag->{tag.putBoolean(MARKER,true);tag.putString("rack_probe_role",main?"main":"peer");});parts.add(card);
            if(main){ItemStack cpu=item("cpu3");var driver=Driver.driverFor(cpu);require(driver instanceof MutableProcessor,"Real blade CPU cannot select its public architecture");var selected=li.cil.oc.api.Machine.architectures().stream().filter(type->li.cil.oc.api.Machine.getArchitectureName(type).equals(architecture)).findFirst().orElseThrow(()->new AssertionError("Requested rack architecture unavailable: "+architecture));((MutableProcessor)driver).setArchitecture(cpu,selected);parts.add(cpu);parts.add(item("ram6"));parts.add(item("ram6"));ItemStack disk=item("hdd3");disk.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("rack-cold-hdd"));CustomData.update(DataComponents.CUSTOM_DATA,disk,tag->tag.putString("rack_hardware_fixture","kept"));parts.add(disk);parts.add(item("redstonecard2"));parts.add(li.cil.oc.api.Items.registerEEPROM("Rack cold fixture",BIOS.getBytes(StandardCharsets.UTF_8),new byte[0],false));}
            int count=menu.slots.size()-36;require(count>0,"Actual blade inventory has no component slots");for(ItemStack part:parts){boolean inserted=false;for(int slot=0;slot<count;slot++){Slot target=menu.slots.get(slot);if(!target.hasItem()&&target.mayPlace(part)){target.setByPlayer(part);inserted=true;break;}}require(inserted,"Real blade inventory rejected allowed hardware "+part.getHoverName().getString());}actor.closeFixtureMenu();return stack;
        }
        void attachRack(){entity=level.getBlockEntity(position);require(entity instanceof Rack,"World did not load the actual Rack block entity");rack=(Rack)entity;}
        void resume()throws Exception{
            Path prepared=directory.resolve("prepare-results.txt");require(Files.isRegularFile(prepared)&&Files.readAllLines(prepared,StandardCharsets.UTF_8).getFirst().equals("status=PASS"),"Rack prepare did not finish normal shutdown");try(InputStream input=Files.newInputStream(directory.resolve("checkpoint.properties"))){manifest.load(input);}require(runId.equals(manifest.getProperty("run_id"))&&"rack".equals(manifest.getProperty("host"))&&architecture.equals(manifest.getProperty("architecture"))&&"prepared".equals(manifest.getProperty("phase")),"Rack checkpoint selection differs or was consumed");require(!PROCESS.equals(manifest.getProperty("process")),"Rack resume must execute in another Java process");require(directory.resolve("ocfixture-host-world").toString().equals(manifest.getProperty("world")),"Rack checkpoint belongs to another world");attachRack();evidence.add("process.prepare="+manifest.getProperty("process"));
        }
        boolean connected(){if(!(rack.getMountable(0) instanceof Server main)||!(rack.getMountable(1) instanceof Server other))return false;blade=main;peer=other;probe=findProbe(main,"main");peerProbe=findProbe(other,"peer");return probe!=null&&peerProbe!=null;}
        Probe findProbe(Server host,String role){if(host.machine()==null||host.machine().node().network()==null)return null;for(Node node:host.machine().node().network().nodes())if(node.host() instanceof Probe actual&&actual.role.equals(role)&&host.componentSlot(node.address())>=0)return actual;return null;}
        void openRack(){actor.closeFixtureMenu();actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);actor.setShiftKeyDown(false);require(entity instanceof MenuProvider&&actor.openMenu((MenuProvider)entity,buffer->buffer.writeBlockPos(position)).isPresent(),"Actual rack did not open its authorized menu");require(actor.containerMenu.stillValid(actor),"Actual rack menu rejected its nearby owner");}
        void edit(int slot,int connection,int side,int operation,boolean enabled){var codec=NetworkRegistry.getCodec(ResourceLocation.fromNamespaceAndPath("opencomputers","rack_edit"),ConnectionProtocol.PLAY,PacketFlow.SERVERBOUND);require(codec!=null,"Actual rack edit payload codec was not registered");RegistryFriendlyByteBuf buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());try{buffer.writeBlockPos(position);buffer.writeVarInt(slot);buffer.writeVarInt(connection);buffer.writeVarInt(side);buffer.writeVarInt(operation);buffer.writeBoolean(enabled);var payload=codec.decode(buffer);require(!buffer.isReadable(),"Actual rack edit codec did not consume its wire fields");NetworkRegistry.handleModdedPayload(actor.connection,new ServerboundCustomPayloadPacket(payload));}finally{buffer.release();}}
        void power(){
            // The first real power click can precede the creative blade's first
            // production update. Supply its already mapped external rack bus.
            Node input=bus(Direction.DOWN);
            require(input instanceof Connector&&input.network()==blade.node().network(),"Actual blade has no mapped external power connector");
            Connector supply=(Connector)input;
            supply.changeBuffer(Double.MAX_VALUE);
            require(supply.globalBuffer()>0,"Actual rack power network did not accept initial energy");
            actor.closeFixtureMenu();actor.setShiftKeyDown(false);require(blade.onActivate(actor,InteractionHand.MAIN_HAND,ItemStack.EMPTY,.5F,.5F)&&actor.containerMenu!=actor.inventoryMenu&&actor.containerMenu.slots.getFirst().container==blade&&actor.containerMenu.stillValid(actor),"Actual mounted blade did not open its authorized ServerMenu");edit(0,0,-1,2,true);require(blade.machine().isRunning(),"Actual mounted server power codec/handler did not start/resume the VM: "+blade.machine().lastError());actor.closeFixtureMenu();
        }
        void tick()throws Exception{
            require(++ticks<=1000,"Rack cold stage exceeded 1000 ticks; ready="+ready+", error="+(blade==null?"no blade":blade.machine().lastError()));
            if(!ready){if(!connected()){require(ticks<100,"Actual rack/server component lifecycle did not initialize");return;}require(blade.rack()==rack&&blade.slot()==0&&blade.machine().host()==blade&&peer.rack()==rack&&peer.slot()==1,"Actual mounted servers do not own their distinct VMs");require(blade.machine().componentCount()<=blade.machine().maxComponents(),"Actual server blade exceeds its CPU component budget");
                if(stage.equals("prepare")){openRack();edit(0,0,Direction.DOWN.get3DDataValue(),0,false);edit(0,1,Direction.UP.get3DDataValue(),0,false);edit(1,0,Direction.UP.get3DDataValue(),0,false);edit(0,0,-1,1,true);edit(0,0,-1,1,false);actor.closeFixtureMenu();blade.machine().addUser(actor.getGameProfile().getName());power();}
                else{verifyIdentities();verifyHardwareAndRack();require(probe.boots.get()==1||luaJ&&probe.boots.get()==2,"Rack addon card lost its saved BIOS counter");require(blade.machine().isRunning()||luaJ&&probe.completed.get(),"World-loaded blade did not restore an active VM: "+blade.machine().lastError());if(!probe.completed.get()&&blade.machine().isPaused())power();}
                ready=true;return;
            }
            require(blade.machine().lastError()==null,"Actual rack VM failed: "+blade.machine().lastError());require(blade.machine().isRunning()||probe.completed.get(),"Actual rack VM stopped before proof callbacks");
            if(stage.equals("prepare")&&probe.requests.get()==1&&blade.machine().isPaused()){
                require(probe.boots.get()==1,"Rack BIOS restarted before checkpoint");captureIdentities();captureHardwareAndRack();verifyBuses();require(blade.machine().signal("rack_cold_resume",2718,"rack-queued-signal"),"Cannot queue actual blade cold-resume signal");saveWorld();require(luaJ||SAVED_VALUES.get()>0,"Actual rack world save did not serialize native userdata");manifest.setProperty("run_id",runId);manifest.setProperty("host","rack");manifest.setProperty("architecture",architecture);manifest.setProperty("phase","prepared");manifest.setProperty("process",PROCESS);manifest.setProperty("world",directory.resolve("ocfixture-host-world").toString());saveManifest();evidence.add("checkpoint=owned native VM in actual rack slot 0, permitted HDD open handle and real primary/secondary mappings saved in world NBT");evidence.add("value.save_calls="+SAVED_VALUES.get());success();
            }else if(stage.equals("resume")&&probe.completed.get()){
                require(probe.boots.get()==(luaJ?2:1),"Rack BIOS boot count differs from selected architecture semantics");require(luaJ?LOADED_VALUES.get()==0:LOADED_VALUES.get()>0,"Rack userdata load differs from native/LuaJ semantics");verifyIdentities();verifyHardwareAndRack();verifyBuses();evidence.add("proof="+(luaJ?"real world-loaded owned blade rebooted LuaJ BIOS once; HDD/EEPROM, owner, hardware, redstone output and rack mappings persisted":"real world-loaded owned blade resumed native locals, nested coroutine, ItemStack/iterator userdata, open tmpfs/HDD offsets, EEPROM, queued signal, all component identities, redstone output and isolated primary/secondary bus mapping"));evidence.add("boots="+probe.boots.get());evidence.add("value.load_calls="+LOADED_VALUES.get());manifest.setProperty("phase","complete");saveWorld();saveManifest();success();
            }
        }
        Node bus(Direction local){return rack.sidedNode(rack.toGlobal(local));}
        void captureIdentities(){manifest.setProperty("machine",blade.machine().node().address());manifest.setProperty("tmp",blade.machine().tmpAddress());manifest.setProperty("probe",probe.node().address());manifest.setProperty("hdd",probe.disk);manifest.setProperty("eeprom",probe.flash);manifest.setProperty("redstone",probe.redstone);manifest.setProperty("peer.machine",peer.machine().node().address());manifest.setProperty("peer.probe",peerProbe.node().address());blade.machine().components().forEach((address,type)->manifest.setProperty("component."+address,type));for(Direction side:Direction.values())if(side!=Direction.SOUTH){Node port=bus(side);require(port!=null,"Actual rack has no non-front bus "+side);manifest.setProperty("bus."+side.get3DDataValue(),port.address());}require(blade.componentSlot(probe.disk)>=0&&blade.componentSlot(probe.flash)>=0&&blade.componentSlot(probe.redstone)>=0,"Checkpoint HDD/EEPROM/redstone are not actual blade hardware");}
        void verifyIdentities(){require(blade.machine().host()==blade&&blade.rack()==rack&&rack.indexOfMountable(blade)==0&&peer.machine().host()==peer&&rack.indexOfMountable(peer)==1,"World-loaded rack/blade host relationship changed");require(manifest.getProperty("machine").equals(blade.machine().node().address())&&manifest.getProperty("tmp").equals(blade.machine().tmpAddress())&&manifest.getProperty("probe").equals(probe.node().address())&&manifest.getProperty("peer.machine").equals(peer.machine().node().address())&&manifest.getProperty("peer.probe").equals(peerProbe.node().address()),"Rack blade/tmpfs/card/peer identity changed");for(String key:manifest.stringPropertyNames())if(key.startsWith("component."))require(manifest.getProperty(key).equals(blade.machine().components().get(key.substring(10))),"Rack blade lost an actual saved component: "+key);for(Direction side:Direction.values())if(side!=Direction.SOUTH)require(bus(side)!=null&&bus(side).address().equals(manifest.getProperty("bus."+side.get3DDataValue())),"Actual rack external bus identity changed: "+side);}
        void captureHardwareAndRack()throws Exception{List<ItemStack> parts=hardware(blade);manifest.setProperty("hardware.count",Integer.toString(parts.size()));for(int index=0;index<parts.size();index++)manifest.setProperty("hardware."+index,signature(parts.get(index)));manifest.setProperty("facing",rack.facing().getName());CompoundTag state=entity.getUpdateTag(level.registryAccess());ListTag rows=state.getList("oc:nodeMapping",Tag.TAG_INT_ARRAY);require(rows.size()==4,"Actual rack did not expose all four mapping rows");for(int slot=0;slot<4;slot++){int[] row=rows.getIntArray(slot);require(row.length==4,"Actual rack mapping row is incomplete");for(int connection=0;connection<4;connection++)manifest.setProperty("mapping."+slot+"."+connection,Integer.toString(row[connection]));}manifest.setProperty("relay",Boolean.toString(state.getBoolean("oc:isRelayEnabled")));verifyHardwareAndRack();}
        void verifyHardwareAndRack()throws Exception{
            require(Arrays.asList(blade.machine().users()).contains(actor.getGameProfile().getName()),"Real blade Machine.users ownership did not persist");List<ItemStack> parts=hardware(blade);require(parts.size()==Integer.parseInt(manifest.getProperty("hardware.count")),"Actual blade hardware inventory changed size");boolean marked=false;for(int index=0;index<parts.size();index++){ItemStack part=parts.get(index);require(signature(part).equals(manifest.getProperty("hardware."+index)),"Actual blade hardware ItemStack data changed in slot "+index);marked|=data(part).getString("rack_hardware_fixture").equals("kept");}require(marked,"Blade lost its real custom-named HDD ItemStack components");require(rack.getItem(0).getHoverName().getString().equals("rack-cold-blade")&&data(rack.getItem(0)).getString("rack_blade_fixture").equals("main")&&rack.getItem(1).getHoverName().getString().equals("rack-peer-blade")&&data(rack.getItem(1)).getString("rack_blade_fixture").equals("peer"),"Physical mounted blade ItemStack metadata did not persist");require(rack.facing().getName().equals(manifest.getProperty("facing")),"Actual rack orientation changed");CompoundTag state=entity.getUpdateTag(level.registryAccess());ListTag rows=state.getList("oc:nodeMapping",Tag.TAG_INT_ARRAY);require(rows.size()==4,"World-loaded rack lost its mapping matrix");for(int slot=0;slot<4;slot++){int[] row=rows.getIntArray(slot);require(row.length==4,"World-loaded rack mapping row changed size");for(int connection=0;connection<4;connection++)require(row[connection]==Integer.parseInt(manifest.getProperty("mapping."+slot+"."+connection)),"Rack primary/secondary mapping changed: "+slot+"/"+connection);}require(!state.getBoolean("oc:isRelayEnabled")&&!Boolean.parseBoolean(manifest.getProperty("relay")),"Explicit rack relay-disable state was not preserved");require(rows.getIntArray(0)[0]==Direction.DOWN.get3DDataValue()&&rows.getIntArray(0)[1]==Direction.UP.get3DDataValue()&&rows.getIntArray(1)[0]==Direction.UP.get3DDataValue(),"Actual codec/handler did not retain selected primary/secondary mappings");Node redstone=blade.machine().node().network().node(manifest.getProperty("redstone"));require(redstone instanceof Component&&((Number)((Component)redstone).invoke("getOutput",blade.machine(),Direction.UP.get3DDataValue())[0]).intValue()==11,"Actual blade redstone component lost its saved output");require(level.getBlockState(position).getSignal(level,position,Direction.DOWN)==11,"Actual world rack redstone output does not match the saved blade callback state");
        }
        void verifyBuses(){Endpoint down=new Endpoint(),up=new Endpoint(),back=new Endpoint();try{Node bottom=bus(Direction.DOWN),top=bus(Direction.UP),rear=bus(Direction.NORTH);require(bottom!=null&&top!=null&&rear!=null&&bottom.network()!=top.network()&&top.network()!=rear.network()&&bottom.network()!=rear.network(),"Actual rack primary networks merged");bottom.connect(down.node());top.connect(up.node());rear.connect(back.node());require(down.node().network()==bottom.network()&&up.node().network()==top.network()&&back.node().network()==rear.network(),"New test endpoints did not join their actual rack bus graphs");require(blade.node().network()==bottom.network()&&peer.node().network()==top.network()&&probe.node().canBeSeenFrom(down.node())&&!probe.node().canBeSeenFrom(up.node())&&peerProbe.node().canBeSeenFrom(up.node())&&!peerProbe.node().canBeSeenFrom(down.node()),"Saved primary mappings did not preserve actual blade/component isolation");require(blade.getConnectableCount()==1&&blade.getConnectableAt(0)==probe,"Actual server did not expose its SDK card as the real secondary connectable");String incoming="rack-in-up-"+stage;up.node().sendToReachable("network.message",Network.newPacket(up.node().address(),null,20491,new Object[]{incoming}));require(probe.busPackets.contains(incoming),"Configured rack secondary bus did not deliver an actual packet to the real blade card");String wrong="rack-in-back-"+stage;back.node().sendToReachable("network.message",Network.newPacket(back.node().address(),null,20491,new Object[]{wrong}));require(!probe.busPackets.contains(wrong),"Unmapped rack bus delivered into the blade's secondary card");String outgoing="rack-out-up-"+stage;probe.node().sendToNeighbors("network.message",Network.newPacket(probe.node().address(),null,20491,new Object[]{outgoing}));require(up.received.contains(outgoing)&&!back.received.contains(outgoing),"Actual secondary sniffer did not route blade-card output only to its selected bus");evidence.add("buses."+stage+"=real codec/handler mappings preserve isolated primary graphs; secondary actual packets cross the selected UP bus in both directions without exposing private blade components");}finally{down.node().remove();up.node().remove();back.node().remove();}}
        void saveWorld(){entity.setChanged();require(minecraft.saveEverything(false,true,true),"Minecraft did not save the actual rack/blade world");}
        void saveManifest()throws Exception{try(OutputStream output=Files.newOutputStream(directory.resolve("checkpoint.properties"))){manifest.store(output,"Actual rack/server blade cold-process world checkpoint");}}
        void success(){finished=true;pendingSuccess=true;report("PENDING_STOP");minecraft.halt(false);}
        void fail(Throwable error){if(finished&&!pendingSuccess)return;finished=true;pendingSuccess=false;evidence.add("failure="+error);LOGGER.error("HOST_PERSISTENCE FAIL host=rack stage={}",stage,error);report("FAIL");minecraft.halt(false);}
        void report(String status){List<String> lines=new ArrayList<>();lines.add("status="+status);lines.add("host=rack");lines.add("stage="+stage);lines.add("architecture="+architecture);lines.add("run_id="+runId);lines.addAll(evidence);try{Files.write(directory.resolve(stage+"-results.txt"),lines,StandardCharsets.UTF_8);}catch(Exception error){throw new IllegalStateException("Cannot write rack report",error);}}
    }
    private static String packetMarker(Packet packet){return packet.port()==20491&&packet.data().length==1&&packet.data()[0] instanceof String text?text:null;}
    private static final class Endpoint extends AbstractManagedEnvironment {
        final java.util.Set<String> received=ConcurrentHashMap.newKeySet();
        Endpoint(){setNode(Network.newNode(this,Visibility.Network).create());}
        @Override public void onMessage(Message message){if(message.name().equals("network.message")&&message.data().length==1&&message.data()[0] instanceof Packet packet){String marker=packetMarker(packet);if(marker!=null)received.add(marker);}}
    }
    /** Public PlayerList lifecycle registers an online owner; the transport is a local test double. */
    private static final class TestPlayer extends FakePlayer {
        final EmbeddedChannel channel=new EmbeddedChannel();
        final Connection transport;
        private int menuId;
        TestPlayer(ServerLevel level)throws Exception{
            super(level,new GameProfile(UUID.fromString("a32319c9-4cf3-4d49-8de4-851e417dad18"),"OC_RackFixture"));
            transport=new Connection(PacketFlow.SERVERBOUND){
                @Override public Channel channel(){return TestPlayer.this.channel;}
                @Override public SocketAddress getRemoteAddress(){return new InetSocketAddress("127.0.0.1",0);}
                @Override public boolean isConnected(){return TestPlayer.this.channel.isOpen();}
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet,PacketSendListener listener,boolean flush){}
            };
            // PlayerList's protocol switch writes through Connection's own channel field.
            // Bind it through the real public Netty lifecycle with an actual pipeline context.
            channel.pipeline().addLast("packet_handler",transport);
            transport.channelActive(channel.pipeline().context(transport));
            NetworkRegistry.configureMockConnection(transport);
            connection=new ServerGamePacketListenerImpl(level.getServer(),transport,this,CommonListenerCookie.createInitial(getGameProfile(),false));
        }
        @Override public OptionalInt openMenu(MenuProvider provider,Consumer<RegistryFriendlyByteBuf> extraDataWriter){if(provider==null)return OptionalInt.empty();closeFixtureMenu();AbstractContainerMenu opened=provider.createMenu(++menuId,getInventory(),this);if(opened==null)return OptionalInt.empty();containerMenu=opened;return OptionalInt.of(opened.containerId);}
        @Override public boolean hasDisconnected(){return false;}
        void closeFixtureMenu(){if(containerMenu!=inventoryMenu){containerMenu.removed(this);containerMenu=inventoryMenu;}}
    }
    private static final class ProbeDriver extends li.cil.oc.api.prefab.DriverItem implements HostAware {
        @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.is(Items.PAPER)&&data(stack).getBoolean(MARKER);}
        @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&(Server.class.isAssignableFrom(host)||Rack.class.isAssignableFrom(host));}
        @Override public String slot(ItemStack stack){return li.cil.oc.api.driver.item.Slot.Card;}
        @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){return host instanceof Rack rack?new Probe(rack,data(stack).getString("rack_probe_role")):null;}
    }
    public static final class Probe extends AbstractManagedEnvironment implements RackBusConnectable {
        final Rack rack;
        final String role;
        final AtomicInteger boots=new AtomicInteger(),requests=new AtomicInteger();
        final AtomicBoolean completed=new AtomicBoolean();
        final java.util.Set<String> busPackets=ConcurrentHashMap.newKeySet();
        volatile String disk,flash,redstone;
        Probe(Rack rack,String role){this.rack=rack;this.role=role;setNode(Network.newNode(this,Visibility.Network).withComponent(role.equals("main")?"rack_persistence_probe":"rack_peer_probe",Visibility.Network).create());}
        @Override public Component node(){return (Component)super.node();}
        @Override public void receivePacket(Packet packet){String marker=packetMarker(packet);if(marker!=null)busPackets.add(marker);}
        @Callback(direct=true,limit=64)public Object[] booted(Context context,Arguments args){rack.markChanged();return new Object[]{boots.incrementAndGet()};}
        @Callback(direct=true,limit=64)public Object[] value(Context context,Arguments args){RackValue value=new RackValue();value.seed(context,args);return new Object[]{value};}
        @Callback(direct=true,limit=64)public Object[] checkpoint(Context context,Arguments args){require(context instanceof Machine machine&&machine.host()==active.blade&&active.blade.rack()==rack,"Checkpoint did not originate in the actual mounted server-owned VM");require(boots.get()==1&&requests.get()==0,"Rack reached an unexpected first checkpoint");disk=args.checkString(0);flash=args.checkString(1);redstone=args.checkString(2);require(context.pause(600),"Cannot pause real rack blade at a serializable Lua yield");requests.set(1);rack.markChanged();return new Object[]{true};}
        void proof(Context context,Arguments args){Run run=java.util.Objects.requireNonNull(active);require(run.stage.equals("resume")&&context instanceof Machine machine&&machine.host()==run.blade&&run.blade.rack()==rack&&run.rack==rack,"Proof did not originate in the world-loaded real server blade");require(args.checkString(0).equals(run.manifest.getProperty("machine"))&&args.checkString(1).equals(run.manifest.getProperty("tmp"))&&args.checkString(2).equals(run.manifest.getProperty("hdd"))&&args.checkString(3).equals(run.manifest.getProperty("eeprom"))&&args.checkString(4).equals(run.manifest.getProperty("redstone")),"Rack Lua locals lost saved component identities");}
        @Callback(direct=true,limit=64)public Object[] verified(Context context,Arguments args){proof(context,args);require(!active.luaJ&&boots.get()==1&&args.checkInteger(5)==314159,"Native rack BIOS restarted or lost its retained local");completed.set(true);rack.markChanged();return new Object[]{true};}
        @Callback(direct=true,limit=64)public Object[] rebooted(Context context,Arguments args){proof(context,args);require(active.luaJ&&boots.get()==2,"LuaJ rack blade did not genuinely reboot BIOS exactly once");completed.set(true);rack.markChanged();return new Object[]{true};}
        @Override public void load(CompoundTag tag){super.load(tag);boots.set(tag.getInt("boots"));requests.set(tag.getInt("requests"));completed.set(tag.getBoolean("complete"));disk=tag.getString("disk");flash=tag.getString("flash");redstone=tag.getString("redstone");}
        @Override public void save(CompoundTag tag){super.save(tag);tag.putInt("boots",boots.get());tag.putInt("requests",requests.get());tag.putBoolean("complete",completed.get());if(disk!=null)tag.putString("disk",disk);if(flash!=null)tag.putString("flash",flash);if(redstone!=null)tag.putString("redstone",redstone);}
    }
    public static final class RackValue extends AbstractValue {
        private int counter=-1;
        private ItemStack stack=ItemStack.EMPTY;
        private ItemStackArrayValue iterator;
        private HolderLookup.Provider loadedWith;
        public RackValue(){}
        void seed(Context context,Arguments args){require(context instanceof Machine machine&&machine.host() instanceof Server,"Value did not originate in an actual server blade");HolderLookup.Provider registries=((Machine)context).host().world().registryAccess();counter=43;stack=new ItemStack(Items.EMERALD,7);stack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("rack-cold-value"));CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putString("rack_payload","kept"));iterator=new ItemStackArrayValue(new ItemStack[]{ItemStack.EMPTY,new ItemStack(Items.DIAMOND,3)},registries);iterator.call(context,args);}
        @Override public void save(CompoundTag tag){throw new AssertionError("Rack Value lost registry provider on save");}
        @Override public void load(CompoundTag tag){throw new AssertionError("Rack Value lost registry provider on load");}
        @Override public void save(CompoundTag tag,HolderLookup.Provider registries){require(registries!=null&&iterator!=null&&!stack.isEmpty(),"Rack Value was not initialized through its real Lua callback");tag.putInt("counter",counter);tag.put("stack",stack.save(registries));CompoundTag state=new CompoundTag();iterator.save(state,registries);tag.put("iterator",state);SAVED_VALUES.incrementAndGet();}
        @Override public void load(CompoundTag tag,HolderLookup.Provider registries){require(registries!=null,"Rack Value restore has no invoking world registry");counter=tag.getInt("counter");stack=ItemStack.parseOptional(registries,tag.getCompound("stack"));iterator=new ItemStackArrayValue();iterator.load(tag.getCompound("iterator"),registries);loadedWith=registries;LOADED_VALUES.incrementAndGet();}
        @Callback(direct=true,limit=64)public Object[] snapshot(Context context,Arguments args){require(context instanceof Machine machine&&machine.host() instanceof Server&&loadedWith==machine.host().world().registryAccess(),"Rack Value was not reconstructed with the invoking world's registry");return new Object[]{counter,stack.getCount(),stack.getHoverName().getString(),data(stack).getString("rack_payload")};}
        @Callback(direct=true,limit=64)public Object[] nextCount(Context context,Arguments args){Object[] next=iterator.call(context,args);return new Object[]{next==null?0:((ItemStack)next[0]).getCount()};}
    }
    private static final String BIOS="""
        local p=component.proxy(assert(component.list("rack_persistence_probe",true)()))
        local boot=p.booted()
        local machine,tmp=computer.address(),computer.tmpAddress()
        local disk
        for address in component.list("filesystem",true) do
          if address~=tmp and not component.invoke(address,"isReadOnly") then disk=address break end
        end
        assert(disk,"actual server HDD absent")
        local flash=assert(component.list("eeprom",true)())
        local redstone=assert(component.list("redstone",true)())
        local hdd=component.proxy(disk)
        assert(computer.users()=="OC_RackFixture","real server owner lost")
        if boot>1 then
          assert(computer.getArchitecture()=="LuaJ","native rack blade rebooted BIOS")
          local f=assert(hdd.open("rack-marker","r"))
          assert(hdd.read(f,100)=="persistent-cold-marker") hdd.close(f)
          assert(component.invoke(flash,"getData")=="rack-eeprom-data")
          assert(component.invoke(redstone,"getOutput",1)==11)
          assert(p.rebooted(machine,tmp,disk,flash,redstone))
          while true do computer.pullSignal() end
        end
        local retained=314159
        local co=coroutine.create(function() local n=271828 coroutine.yield() return n+1 end)
        assert(coroutine.resume(co))
        local value=p.value()
        local fs=component.proxy(tmp)
        local w=assert(fs.open("rack-tmp","w")) assert(fs.write(w,"abcdefghi")) fs.close(w)
        local reader=assert(fs.open("rack-tmp","r")) assert(fs.read(reader,3)=="abc")
        w=assert(hdd.open("rack-marker","w")) assert(hdd.write(w,"persistent-cold-marker")) hdd.close(w)
        local diskReader=assert(hdd.open("rack-marker","r")) assert(hdd.read(diskReader,10)=="persistent")
        assert(hdd.setLabel("rack-hdd")=="rack-hdd")
        component.invoke(flash,"setData","rack-eeprom-data")
        component.invoke(redstone,"setOutput",1,11)
        assert(p.checkpoint(disk,flash,redstone))
        local event,number,text
        repeat event,number,text=computer.pullSignal() until event=="rack_cold_resume"
        assert(number==2718 and text=="rack-queued-signal","rack queued signal lost")
        assert(retained==314159,"rack Lua local lost")
        local ok,n=coroutine.resume(co) assert(ok and n==271829,"rack nested coroutine lost")
        local count,size,label,tag=value.snapshot()
        assert(count==43 and size==7 and label=="rack-cold-value" and tag=="kept","rack userdata ItemStack lost")
        assert(value.nextCount()==3,"rack iterator cursor lost")
        assert(fs.read(reader,6)=="defghi","rack tmpfs open handle/offset lost")
        assert(hdd.read(diskReader,100)=="-cold-marker","rack HDD open handle/offset lost")
        assert(hdd.getLabel()=="rack-hdd")
        assert(component.invoke(flash,"getData")=="rack-eeprom-data")
        assert(component.invoke(redstone,"getOutput",1)==11)
        fs.close(reader) hdd.close(diskReader)
        assert(p.verified(machine,tmp,disk,flash,redstone,retained))
        while true do computer.pullSignal() end
        """;
}
