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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Tablet;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A real held tablet VM is saved as the actual ItemStack in a Minecraft chest, then held in a new JVM. */
public final class TabletPersistenceContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-tablet-persistence");
    private static final String PROCESS=UUID.randomUUID().toString(),MARKER="ocfixture:tablet_probe";
    private static final AtomicInteger SAVED_VALUES=new AtomicInteger(),LOADED_VALUES=new AtomicInteger();
    private static Run active;
    private TabletPersistenceContract(){}
    public static void register(){Driver.add(new ProbeDriver());}
    public static void start(MinecraftServer server){try{active=new Run(server);active.start();}catch(Throwable error){if(active!=null)active.fail(error);else throw new IllegalStateException("Tablet persistence startup failed",error);}}
    public static void tick(){Run run=active;if(run==null||run.finished)return;try{run.tick();}catch(Throwable error){run.fail(error);}}
    public static void stopped(){Run run=active;if(run==null)return;if(run.pendingSuccess){run.report("PASS");LOGGER.info("HOST_PERSISTENCE_COMPLETE host=tablet stage={} architecture={} status=PASS",run.stage,run.architecture);}else if(!run.finished)run.fail(new AssertionError("Server stopped before tablet proof completed"));if(run.actor!=null)run.actor.channel.finishAndReleaseAll();active=null;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static ItemStack item(String key){var info=li.cil.oc.api.Items.get(key);require(info!=null,"Missing real tablet hardware "+key);return info.createItemStack(1);}
    private static CompoundTag data(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static List<ItemStack> hardware(Tablet tablet){List<ItemStack> result=new ArrayList<>();tablet.internalComponents().forEach(result::add);return result;}
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
        ChestBlockEntity chest;
        ItemStack stack=ItemStack.EMPTY;
        Tablet tablet;
        Probe probe;
        int ticks;
        boolean ready,finished,pendingSuccess;
        Run(MinecraftServer server){this.server=server;level=server.overworld();}
        void start()throws Exception{
            require(stage.equals("prepare")||stage.equals("resume"),"Tablet stage must be prepare or resume");require(directory.equals(Path.of("").toAbsolutePath().normalize())&&directory.getParent().getFileName().toString().equals("tablet")&&directory.getParent().getParent().getFileName().toString().equals("hosts")&&directory.getFileName().toString().equals(runId),"Refusing a non-fixture tablet directory");require(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(directory.resolve("ocfixture-host-world")),"Refusing a different Minecraft world");
            level.setChunkForced(position.getX()>>4,position.getZ()>>4,true);level.getChunkAt(position);actor=new TestPlayer(level);actor.getAbilities().instabuild=true;actor.setPos(position.getX()+.5,position.getY(),position.getZ()-2);actor.setXRot(-15);actor.setYRot(90);
            if(stage.equals("prepare"))prepare();else resume();evidence.add("process.pid="+ProcessHandle.current().pid());evidence.add("process.current="+PROCESS);evidence.add("storage=actual Minecraft ChestBlockEntity slot 0 in world NBT; FakePlayer is not in PlayerList and no player-save claim is made");LOGGER.info("HOST_PERSISTENCE host=tablet stage={} architecture={} run={}",stage,architecture,runId);
        }
        void prepare()throws Exception{
            require(!Files.exists(directory.resolve("checkpoint.properties")),"Prepare cannot overwrite a tablet checkpoint");require(level.isEmptyBlock(position),"Isolated tablet chest cell is occupied");require(level.setBlock(position,Blocks.CHEST.defaultBlockState(),3),"Could not place the real storage chest");attachChest();require(chest.isEmpty(),"New fixture chest is not empty");
            require(CreativeTab.instance!=null,"Real configured creative tablet is unavailable");CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(),true,level.registryAccess()));
            for(ItemStack candidate:CreativeTab.instance.getDisplayItems())if(candidate.is(item("tablet").getItem())&&data(candidate).getInt("oc:tier")==3){stack=candidate.copy();break;}require(!stack.isEmpty(),"Creative tab did not provide the configured original tablet");
            CompoundTag root=data(stack);ItemStack[] slots=new ItemStack[32];java.util.Arrays.fill(slots,ItemStack.EMPTY);
            for(Tag raw:root.getList("oc:items",Tag.TAG_COMPOUND)){CompoundTag slot=(CompoundTag)raw;int index=slot.getByte("slot");require(index>=0&&index<32,"Configured tablet item has an out-of-range hardware slot");slots[index]=ItemStack.parseOptional(level.registryAccess(),slot.getCompound("item"));}
            var selected=li.cil.oc.api.Machine.architectures().stream().filter(type->li.cil.oc.api.Machine.getArchitectureName(type).equals(architecture)).findFirst().orElseThrow(()->new AssertionError("Requested tablet architecture unavailable: "+architecture));
            boolean cpu=false,flash=false,hdd=false;int upgrades=0,free=-1;
            for(int index=0;index<31;index++){ItemStack part=slots[index];if(part.isEmpty()){if(free<0)free=index;continue;}var driver=Driver.driverFor(part);if(driver==null)continue;String kind=driver.slot(part);if(kind.equals(li.cil.oc.api.driver.item.Slot.Upgrade))upgrades++;if(kind.equals(li.cil.oc.api.driver.item.Slot.CPU)&&driver instanceof MutableProcessor processor){processor.setArchitecture(part,selected);cpu=true;}if(kind.equals("eeprom")){slots[index]=li.cil.oc.api.Items.registerEEPROM("Tablet cold fixture",BIOS.getBytes(StandardCharsets.UTF_8),new byte[0],false);flash=true;}if(kind.equals(li.cil.oc.api.driver.item.Slot.HDD))hdd=true;}
            require(cpu&&flash&&hdd&&upgrades<9&&free>=0,"Original creative tablet lacks CPU/EEPROM/HDD or a permitted free upgrade slot");ItemStack observation=new ItemStack(Items.PAPER);CustomData.update(DataComponents.CUSTOM_DATA,observation,tag->tag.putBoolean(MARKER,true));slots[free]=observation;
            ListTag serialized=new ListTag();for(int index=0;index<32;index++)if(!slots[index].isEmpty()){CompoundTag slot=new CompoundTag();slot.putByte("slot",(byte)index);slot.put("item",slots[index].saveOptional(level.registryAccess()));serialized.add(slot);}root.put("oc:items",serialized);root.putString("tablet_fixture","kept");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));stack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("tablet-cold-stack"));actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
            tickItem();useTablet(true);AbstractContainerMenu menu=actor.containerMenu;require(menu!=actor.inventoryMenu&&menu.slots.getFirst().container instanceof Tablet,"Actual shift-use did not create the real tablet inventory menu");require(menu.slots.getFirst().container==tablet,"Menu did not expose the SDK Tablet constructed by the actual item update");require(menu.stillValid(actor),"Actual tablet menu rejected its current inventory holder");
            ItemStack bay=ItemStack.parseOptional(level.registryAccess(),data(stack).getCompound("oc:container"));var driver=Driver.driverFor(bay,tablet.getClass());require(bay.is(item("diskdrive").getItem())&&driver instanceof li.cil.oc.api.driver.item.Container container&&li.cil.oc.api.driver.item.Slot.Floppy.equals(container.providedSlot(bay)),"Configured original tablet does not have its real diskdrive floppy bay");
            menu.slots.getFirst().remove(1);ItemStack floppy=item("floppy");floppy.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("tablet-bay-medium"));CustomData.update(DataComponents.CUSTOM_DATA,floppy,tag->tag.putString("tablet_bay_fixture","kept"));require(menu.slots.getFirst().mayPlace(floppy),"Real tablet menu rejected the allowed physical floppy");menu.slots.getFirst().setByPlayer(floppy);actor.closeFixtureMenu();require(hardware(tablet).size()==32,"Actual tablet wrapper does not expose its original 32 hardware slots");
            manifest.setProperty("container",bay.saveOptional(level.registryAccess()).toString());manifest.setProperty("probe_slot",Integer.toString(free));evidence.add("hardware=original configured tablet with permitted real HDD, ordinary diskdrive container, real menu-inserted floppy in slot 31, public EEPROM BIOS and SDK observation upgrade");
        }
        void attachChest(){require(level.getBlockState(position).is(Blocks.CHEST)&&level.getBlockEntity(position) instanceof ChestBlockEntity,"World did not load the actual Minecraft chest");chest=(ChestBlockEntity)level.getBlockEntity(position);}
        void resume()throws Exception{
            Path prepared=directory.resolve("prepare-results.txt");require(Files.isRegularFile(prepared)&&Files.readAllLines(prepared,StandardCharsets.UTF_8).getFirst().equals("status=PASS"),"Tablet prepare did not finish normal shutdown");try(InputStream input=Files.newInputStream(directory.resolve("checkpoint.properties"))){manifest.load(input);}require(runId.equals(manifest.getProperty("run_id"))&&"tablet".equals(manifest.getProperty("host"))&&architecture.equals(manifest.getProperty("architecture"))&&"prepared".equals(manifest.getProperty("phase")),"Tablet checkpoint selection differs or was consumed");require(!PROCESS.equals(manifest.getProperty("process")),"Tablet resume must execute in another Java process");require(directory.resolve("ocfixture-host-world").toString().equals(manifest.getProperty("world")),"Tablet checkpoint belongs to a different world");attachChest();
            stack=chest.removeItemNoUpdate(0);chest.setChanged();require(!stack.isEmpty()&&stack.is(item("tablet").getItem()),"Real chest NBT did not restore the physical tablet");require(data(stack).getString("oc:tablet").equals(manifest.getProperty("tablet.id"))&&data(stack).contains("oc:data"),"Chest-loaded tablet lost its runtime save or stable physical item UUID");require(stack.getHoverName().getString().equals("tablet-cold-stack")&&data(stack).getString("tablet_fixture").equals("kept"),"Physical tablet ItemStack components did not persist in chest NBT");actor.setItemInHand(InteractionHand.MAIN_HAND,stack);tickItem();require(tablet!=null&&probe!=null&&tablet.machine().host()==tablet,"Actual inventoryTick did not recreate the real item-owned tablet VM");evidence.add("process.prepare="+manifest.getProperty("process"));evidence.add("item.restore=read from actual chest world NBT, returned to a new holder, recreated through Item.inventoryTick");
        }
        void tickItem(){require(actor.getMainHandItem()==stack,"Tablet is not the real physical stack in its current holder's inventory");stack.getItem().inventoryTick(stack,level,actor,actor.getInventory().selected,true);}
        void useTablet(boolean inventory){actor.setShiftKeyDown(inventory);require(stack.getItem().use(level,actor,InteractionHand.MAIN_HAND).getResult().consumesAction(),"Actual tablet use path did not start item use");stack.getItem().releaseUsing(stack,level,actor,stack.getItem().getUseDuration(stack,actor)-1);actor.stopUsingItem();actor.setShiftKeyDown(false);}
        boolean connected(){return tablet!=null&&probe!=null&&tablet.machine()!=null&&tablet.machine().node().network()!=null&&probe.node().network()==tablet.machine().node().network()&&tablet.componentSlot(probe.node().address())>=0;}
        void tick()throws Exception{
            require(++ticks<=1000,"Tablet cold stage exceeded 1000 ticks; ready="+ready+", probe="+probe+", error="+(tablet==null?"no wrapper":tablet.machine().lastError()));tickItem();
            if(!ready){if(!connected()){require(ticks<100,"Actual tablet item component lifecycle did not initialize");return;}require(tablet.player()==actor&&tablet.machine().host()==tablet,"Tablet VM does not belong to the actual current stack holder");require(tablet.machine().componentCount()<=tablet.machine().maxComponents(),"Actual tablet exceeded its real CPU component budget");
                if(stage.equals("prepare")){useTablet(false);require(tablet.machine().isRunning(),"Actual tablet short-use failed to boot: "+tablet.machine().lastError());}
                else{verifyIdentities();verifyHardware();useTablet(false);require(probe.boots.get()==1||luaJ&&probe.boots.get()==2,"Tablet observation upgrade lost its saved BIOS count");require(tablet.machine().isRunning()||luaJ&&probe.completed.get(),"Chest-loaded tablet did not restore an active VM: "+tablet.machine().lastError());if(!probe.completed.get()&&tablet.machine().isPaused())require(tablet.machine().start(),"Cannot release the intentionally paused saved tablet checkpoint");}
                ready=true;return;
            }
            require(tablet.machine().lastError()==null,"Actual tablet VM failed: "+tablet.machine().lastError());require(tablet.machine().isRunning()||probe.completed.get(),"Tablet stopped before its proof callbacks");
            if(stage.equals("prepare")&&probe.requests.get()==1&&tablet.machine().isPaused()){
                require(probe.boots.get()==1,"Tablet BIOS restarted before the checkpoint");captureIdentities();require(tablet.machine().signal("tablet_cold_resume",2718,"tablet-queued-signal"),"Cannot queue the real tablet cold-resume signal");storeAndSave();require(luaJ||SAVED_VALUES.get()>0,"Actual tablet world-save path did not serialize native userdata");captureHardware();
                manifest.setProperty("run_id",runId);manifest.setProperty("host","tablet");manifest.setProperty("architecture",architecture);manifest.setProperty("phase","prepared");manifest.setProperty("process",PROCESS);manifest.setProperty("world",directory.resolve("ocfixture-host-world").toString());manifest.setProperty("tablet.id",data(stack).getString("oc:tablet"));saveManifest();evidence.add("tablet.id="+manifest.getProperty("tablet.id"));evidence.add("checkpoint=actual item-owned paused VM saved by normal world-save and server-stop lifecycle into the chest's physical ItemStack");evidence.add("value.save_calls="+SAVED_VALUES.get());success();
            }else if(stage.equals("resume")&&probe.completed.get()){
                require(probe.boots.get()==(luaJ?2:1),"Tablet BIOS boot count differs from the selected architecture contract");require(luaJ?LOADED_VALUES.get()==0:LOADED_VALUES.get()>0,"Tablet userdata load differs from native/LuaJ semantics");verifyIdentities();verifyHardware();verifyMedia();evidence.add("proof="+(luaJ?"actual chest-loaded tablet genuinely rebooted LuaJ BIOS once; physical item, 32-slot hardware/bay, HDD/floppy bytes, EEPROM and identities persisted":"actual chest-loaded tablet resumed native locals, nested coroutine, ItemStack/iterator userdata, open tmpfs/HDD/bay-floppy descriptor offsets, EEPROM, queued signal and all node identities"));evidence.add("boots="+probe.boots.get());evidence.add("value.load_calls="+LOADED_VALUES.get());manifest.setProperty("phase","complete");storeAndSave();saveManifest();success();
            }
        }
        void captureIdentities(){Machine machine=tablet.machine();manifest.setProperty("machine",machine.node().address());manifest.setProperty("tmp",machine.tmpAddress());manifest.setProperty("probe",probe.node().address());manifest.setProperty("hdd",probe.disk);manifest.setProperty("eeprom",probe.flash);manifest.setProperty("control",probe.control);manifest.setProperty("bay",probe.bay);machine.components().forEach((address,type)->manifest.setProperty("component."+address,type));require(tablet.componentSlot(probe.disk)>=0&&tablet.componentSlot(probe.flash)>=0&&tablet.componentSlot(probe.bay)==31,"Checkpoint media are not actual installed tablet hardware");}
        void verifyIdentities(){require(tablet.machine().host()==tablet&&tablet.player()==actor&&manifest.getProperty("machine").equals(tablet.machine().node().address())&&manifest.getProperty("tmp").equals(tablet.machine().tmpAddress())&&manifest.getProperty("probe").equals(probe.node().address()),"Tablet host/machine/tmpfs/upgrade identity changed");require(tablet.componentSlot(manifest.getProperty("hdd"))>=0&&tablet.componentSlot(manifest.getProperty("eeprom"))>=0&&tablet.componentSlot(manifest.getProperty("bay"))==31,"Chest-loaded tablet lost its actual HDD/EEPROM/floppy slot identity");for(String key:manifest.stringPropertyNames())if(key.startsWith("component."))require(manifest.getProperty(key).equals(tablet.machine().components().get(key.substring(10))),"Tablet lost an actual saved component identity: "+key);}
        void captureHardware(){List<ItemStack> parts=hardware(tablet);require(parts.size()==32,"Saved tablet hardware is not the original 32-slot inventory");for(int index=0;index<32;index++){ItemStack part=parts.get(index);manifest.setProperty("slot."+index,part.isEmpty()?"empty":BuiltInRegistries.ITEM.getKey(part.getItem())+"*"+part.getCount());}verifyMediaUnchecked();}
        void verifyHardware(){List<ItemStack> parts=hardware(tablet);require(parts.size()==32,"Chest load did not reconstruct the 32-slot tablet hardware array");for(int index=0;index<32;index++){ItemStack part=parts.get(index);String actual=part.isEmpty()?"empty":BuiltInRegistries.ITEM.getKey(part.getItem())+"*"+part.getCount();require(actual.equals(manifest.getProperty("slot."+index)),"Physical tablet hardware changed in slot "+index);}ItemStack media=parts.get(31);require(media.getHoverName().getString().equals("tablet-bay-medium")&&data(media).getString("tablet_bay_fixture").equals("kept"),"Actual slot-31 floppy ItemStack components did not persist");ItemStack bay=ItemStack.parseOptional(level.registryAccess(),data(stack).getCompound("oc:container"));require(bay.saveOptional(level.registryAccess()).toString().equals(manifest.getProperty("container")),"Original tablet bay container did not persist");require(data(stack).getString("oc:tablet").equals(manifest.getProperty("tablet.id")),"Real tablet item UUID changed on use/inventoryTick");}
        void verifyMedia()throws Exception{for(String key:new String[]{"hdd","bay"}){Node node=tablet.machine().node().network().node(manifest.getProperty(key));require(node instanceof Component,"Tablet saved medium is not connected: "+key);require(("tablet-"+key).equals(((Component)node).invoke("getLabel",tablet.machine())[0]),"Physical tablet medium label was lost: "+key);}}
        void verifyMediaUnchecked(){try{verifyMedia();}catch(Exception error){throw new AssertionError("Cannot inspect actual tablet media",error);}}
        void storeAndSave(){actor.closeFixtureMenu();require(actor.getMainHandItem()==stack&&chest.isEmpty(),"Cannot store a different physical tablet");actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);chest.setItem(0,stack);chest.setChanged();require(server.saveEverything(false,true,true),"Minecraft did not save the real tablet chest world");require(chest.getItem(0)==stack&&data(stack).contains("oc:data"),"Normal world save did not update the physical tablet runtime data");
            // The item world-save hook may run after this chunk's first serialization.
            // Mark and save again so the chest definitely writes the just-updated physical stack.
            chest.setChanged();require(server.saveEverything(false,true,true),"Minecraft did not flush the updated physical tablet chest");}
        void saveManifest()throws Exception{try(OutputStream output=Files.newOutputStream(directory.resolve("checkpoint.properties"))){manifest.store(output,"Actual tablet ItemStack cold-process checkpoint stored in a real chest");}}
        void success(){finished=true;pendingSuccess=true;report("PENDING_STOP");server.halt(false);}
        void fail(Throwable error){if(finished&&!pendingSuccess)return;finished=true;pendingSuccess=false;evidence.add("failure="+error);LOGGER.error("HOST_PERSISTENCE FAIL host=tablet stage={}",stage,error);report("FAIL");server.halt(false);}
        void report(String status){List<String> lines=new ArrayList<>();lines.add("status="+status);lines.add("host=tablet");lines.add("stage="+stage);lines.add("architecture="+architecture);lines.add("run_id="+runId);lines.addAll(evidence);try{Files.write(directory.resolve(stage+"-results.txt"),lines,StandardCharsets.UTF_8);}catch(Exception error){throw new IllegalStateException("Cannot write tablet report",error);}}
    }
    /** A public mock transport permits actual item/menu calls; this is not a remote-player round trip. */
    private static final class TestPlayer extends FakePlayer {
        final EmbeddedChannel channel=new EmbeddedChannel();
        private int menuId;
        TestPlayer(ServerLevel level){super(level,new GameProfile(UUID.fromString("a32319c9-4cf3-4d49-8de4-851e417dad18"),"OC_TabletFixture"));Connection transport=new Connection(PacketFlow.SERVERBOUND){@Override public Channel channel(){return TestPlayer.this.channel;}@Override public boolean isConnected(){return TestPlayer.this.channel.isOpen();}@Override public void send(Packet<?> packet,PacketSendListener listener,boolean flush){}};NetworkRegistry.configureMockConnection(transport);connection=new ServerGamePacketListenerImpl(level.getServer(),transport,this,CommonListenerCookie.createInitial(getGameProfile(),false));}
        @Override public OptionalInt openMenu(MenuProvider provider,Consumer<RegistryFriendlyByteBuf> extraDataWriter){if(provider==null)return OptionalInt.empty();closeFixtureMenu();AbstractContainerMenu opened=provider.createMenu(++menuId,getInventory(),this);if(opened==null)return OptionalInt.empty();containerMenu=opened;return OptionalInt.of(opened.containerId);}
        @Override public boolean hasDisconnected(){return false;}
        void closeFixtureMenu(){if(containerMenu!=inventoryMenu){containerMenu.removed(this);containerMenu=inventoryMenu;}}
    }
    private static final class ProbeDriver extends li.cil.oc.api.prefab.DriverItem implements HostAware {
        @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.is(Items.PAPER)&&data(stack).getBoolean(MARKER);}
        @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&Tablet.class.isAssignableFrom(host);}
        @Override public String slot(ItemStack stack){return li.cil.oc.api.driver.item.Slot.Upgrade;}
        @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){if(!(host instanceof Tablet tablet))return null;Probe probe=new Probe(tablet);Run run=active;if(run!=null){run.tablet=tablet;run.probe=probe;}return probe;}
    }
    public static final class Probe extends AbstractManagedEnvironment {
        final Tablet host;
        final AtomicInteger boots=new AtomicInteger(),requests=new AtomicInteger();
        final AtomicBoolean completed=new AtomicBoolean();
        volatile String disk,flash,control,bay;
        Probe(Tablet host){this.host=host;setNode(Network.newNode(this,Visibility.Network).withComponent("tablet_persistence_probe",Visibility.Neighbors).create());}
        @Callback(direct=true,limit=64)public Object[] booted(Context context,Arguments args){host.markChanged();return new Object[]{boots.incrementAndGet()};}
        @Callback(direct=true,limit=64)public Object[] value(Context context,Arguments args){TabletValue value=new TabletValue();value.seed(context,args);return new Object[]{value};}
        @Callback(direct=true,limit=64)public Object[] hardware(Context context,Arguments args){List<ItemStack> parts=TabletPersistenceContract.hardware(host);String disk=null,flash=null,control=null,bay=null;for(Node node:host.machine().node().network().nodes())if(node instanceof Component component){int slot=host.componentSlot(node.address());if(slot>=0&&slot<parts.size()){ItemStack part=parts.get(slot);var driver=Driver.driverFor(part,host.getClass());if(slot==31)bay=node.address();if(driver!=null&&driver.slot(part).equals(li.cil.oc.api.driver.item.Slot.HDD))disk=node.address();if(driver!=null&&driver.slot(part).equals("eeprom"))flash=node.address();}if(component.name().equals("tablet"))control=node.address();}require(disk!=null&&flash!=null&&control!=null&&bay!=null,"Real tablet hardware/slot-31 medium is not connected");return new Object[]{disk,flash,control,bay};}
        @Callback(direct=true,limit=64)public Object[] checkpoint(Context context,Arguments args){require(context instanceof Machine machine&&machine.host()==host&&host.player()==active.actor,"Checkpoint did not originate in the real held tablet VM");require(boots.get()==1&&requests.get()==0,"Tablet reached an unexpected initial checkpoint");disk=args.checkString(0);flash=args.checkString(1);control=args.checkString(2);bay=args.checkString(3);require(context.pause(600),"Cannot pause actual tablet at a serializable Lua yield");requests.set(1);host.markChanged();return new Object[]{true};}
        void proof(Context context,Arguments args){Run run=java.util.Objects.requireNonNull(active);require(run.stage.equals("resume")&&context instanceof Machine machine&&machine.host()==host&&host==run.tablet&&host.player()==run.actor,"Proof did not originate in the chest-loaded real tablet wrapper");require(args.checkString(0).equals(run.manifest.getProperty("machine"))&&args.checkString(1).equals(run.manifest.getProperty("tmp"))&&args.checkString(2).equals(run.manifest.getProperty("hdd"))&&args.checkString(3).equals(run.manifest.getProperty("eeprom"))&&args.checkString(4).equals(run.manifest.getProperty("control"))&&args.checkString(5).equals(run.manifest.getProperty("bay")),"Tablet Lua locals lost saved node identities");}
        @Callback(direct=true,limit=64)public Object[] verified(Context context,Arguments args){proof(context,args);require(!active.luaJ&&boots.get()==1&&args.checkInteger(6)==314159,"Native tablet BIOS restarted or lost its retained local");completed.set(true);host.markChanged();return new Object[]{true};}
        @Callback(direct=true,limit=64)public Object[] rebooted(Context context,Arguments args){proof(context,args);require(active.luaJ&&boots.get()==2,"LuaJ tablet did not genuinely reboot BIOS exactly once");completed.set(true);host.markChanged();return new Object[]{true};}
        @Override public void load(CompoundTag tag){super.load(tag);boots.set(tag.getInt("boots"));requests.set(tag.getInt("requests"));completed.set(tag.getBoolean("complete"));disk=tag.getString("disk");flash=tag.getString("flash");control=tag.getString("control");bay=tag.getString("bay");}
        @Override public void save(CompoundTag tag){super.save(tag);tag.putInt("boots",boots.get());tag.putInt("requests",requests.get());tag.putBoolean("complete",completed.get());if(disk!=null)tag.putString("disk",disk);if(flash!=null)tag.putString("flash",flash);if(control!=null)tag.putString("control",control);if(bay!=null)tag.putString("bay",bay);}
    }
    public static final class TabletValue extends AbstractValue {
        private int counter=-1;
        private ItemStack stack=ItemStack.EMPTY;
        private ItemStackArrayValue iterator;
        private HolderLookup.Provider loadedWith;
        public TabletValue(){}
        void seed(Context context,Arguments args){require(context instanceof Machine machine&&machine.host() instanceof Tablet,"Value did not originate in an actual tablet");HolderLookup.Provider registries=((Machine)context).host().world().registryAccess();counter=43;stack=new ItemStack(Items.EMERALD,7);stack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("tablet-cold-value"));CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putString("tablet_payload","kept"));iterator=new ItemStackArrayValue(new ItemStack[]{ItemStack.EMPTY,new ItemStack(Items.DIAMOND,3)},registries);iterator.call(context,args);}
        @Override public void save(CompoundTag tag){throw new AssertionError("Tablet Value lost registry provider on save");}
        @Override public void load(CompoundTag tag){throw new AssertionError("Tablet Value lost registry provider on load");}
        @Override public void save(CompoundTag tag,HolderLookup.Provider registries){require(registries!=null&&iterator!=null&&!stack.isEmpty(),"Tablet Value was not initialized through its real Lua callback");tag.putInt("counter",counter);tag.put("stack",stack.save(registries));CompoundTag state=new CompoundTag();iterator.save(state,registries);tag.put("iterator",state);SAVED_VALUES.incrementAndGet();}
        @Override public void load(CompoundTag tag,HolderLookup.Provider registries){require(registries!=null,"Tablet Value restore has no world registry");counter=tag.getInt("counter");stack=ItemStack.parseOptional(registries,tag.getCompound("stack"));iterator=new ItemStackArrayValue();iterator.load(tag.getCompound("iterator"),registries);loadedWith=registries;LOADED_VALUES.incrementAndGet();}
        @Callback(direct=true,limit=64)public Object[] snapshot(Context context,Arguments args){require(context instanceof Machine machine&&machine.host() instanceof Tablet&&loadedWith==machine.host().world().registryAccess(),"Tablet Value was not reconstructed with the invoking world's registry");return new Object[]{counter,stack.getCount(),stack.getHoverName().getString(),data(stack).getString("tablet_payload")};}
        @Callback(direct=true,limit=64)public Object[] nextCount(Context context,Arguments args){Object[] next=iterator.call(context,args);return new Object[]{next==null?0:((ItemStack)next[0]).getCount()};}
    }
    private static final String BIOS="""
        local p=component.proxy(assert(component.list("tablet_persistence_probe",true)()))
        local boot=p.booted()
        local machine,tmp=computer.address(),computer.tmpAddress()
        local disk,flash,control,bay=p.hardware()
        local hdd,floppy=component.proxy(disk),component.proxy(bay)
        if boot>1 then
          assert(computer.getArchitecture()=="LuaJ","native tablet rebooted BIOS")
          local f=assert(hdd.open("tablet-marker","r"))
          assert(hdd.read(f,100)=="persistent-cold-marker") hdd.close(f)
          f=assert(floppy.open("tablet-bay-marker","r"))
          assert(floppy.read(f,100)=="tablet-bay-marker") floppy.close(f)
          assert(component.invoke(flash,"getData")=="tablet-eeprom-data")
          assert(p.rebooted(machine,tmp,disk,flash,control,bay))
          while true do computer.pullSignal() end
        end
        local retained=314159
        local co=coroutine.create(function() local n=271828 coroutine.yield() return n+1 end)
        assert(coroutine.resume(co))
        local value=p.value()
        local fs=component.proxy(tmp)
        local w=assert(fs.open("tablet-tmp","w")) assert(fs.write(w,"abcdefghi")) fs.close(w)
        local reader=assert(fs.open("tablet-tmp","r")) assert(fs.read(reader,3)=="abc")
        w=assert(hdd.open("tablet-marker","w")) assert(hdd.write(w,"persistent-cold-marker")) hdd.close(w)
        local diskReader=assert(hdd.open("tablet-marker","r")) assert(hdd.read(diskReader,10)=="persistent")
        w=assert(floppy.open("tablet-bay-marker","w")) assert(floppy.write(w,"tablet-bay-marker")) floppy.close(w)
        local bayReader=assert(floppy.open("tablet-bay-marker","r")) assert(floppy.read(bayReader,7)=="tablet-")
        assert(hdd.setLabel("tablet-hdd")=="tablet-hdd")
        assert(floppy.setLabel("tablet-bay")=="tablet-bay")
        assert(component.invoke(control,"getPitch")==-15 and component.invoke(control,"getYaw")==90)
        component.invoke(flash,"setData","tablet-eeprom-data")
        assert(p.checkpoint(disk,flash,control,bay))
        local event,number,text
        repeat event,number,text=computer.pullSignal() until event=="tablet_cold_resume"
        assert(number==2718 and text=="tablet-queued-signal","tablet queued signal lost")
        assert(retained==314159,"tablet Lua local lost")
        local ok,n=coroutine.resume(co) assert(ok and n==271829,"tablet nested coroutine lost")
        local count,size,label,tag=value.snapshot()
        assert(count==43 and size==7 and label=="tablet-cold-value" and tag=="kept","tablet userdata ItemStack lost")
        assert(value.nextCount()==3,"tablet iterator cursor lost")
        assert(fs.read(reader,6)=="defghi","tablet tmpfs open handle/offset lost")
        assert(hdd.read(diskReader,100)=="-cold-marker","tablet HDD open handle/offset lost")
        assert(floppy.read(bayReader,100)=="bay-marker","tablet bay floppy open handle/offset lost")
        assert(component.invoke(flash,"getData")=="tablet-eeprom-data")
        fs.close(reader) hdd.close(diskReader) floppy.close(bayReader)
        assert(p.verified(machine,tmp,disk,flash,control,bay,retained))
        while true do computer.pullSignal() end
        """;
}
