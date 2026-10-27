package ocfixture;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;
import li.cil.oc.api.API;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.component.RackMountable;
import li.cil.oc.api.internal.Rack;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.internal.Tablet;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Actual placed devices and native menus; compilation sees only the published OC SDK. */
public final class DeviceContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-real-device-fixture");
    private static Run current;
    private DeviceContract(){}
    public static boolean enabled(){return System.getProperty("ocfixture.deviceRunId")!=null;}
    public static void start(MinecraftServer server){current=new Run(server);try{current.start();}catch(Throwable error){current.results.add("FAIL setup: "+error);LOGGER.error("DEVICE_FIXTURE setup failed",error);current.finish();}}
    public static void tick(){Run run=current;if(run==null||run.finished)return;try{run.tick();}catch(Throwable error){run.results.add("FAIL scenario: "+error);LOGGER.error("DEVICE_FIXTURE failed",error);run.finish();}}
    public static void stopped(){Run run=current;if(run!=null&&!run.finished){run.results.add("FAIL server stopped before device contracts completed");run.finish();}current=null;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static ItemStack item(String key){var descriptor=li.cil.oc.api.Items.get(key);require(descriptor!=null,"Device hardware descriptor is absent: "+key);return descriptor.createItemStack(1);}
    private static String text(Object value){return value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):String.valueOf(value);}

    private static final class Run {
        final MinecraftServer server;
        final ServerLevel level;
        final Path directory=Path.of(System.getProperty("ocfixture.deviceDirectory")).toAbsolutePath().normalize();
        final String runId=System.getProperty("ocfixture.deviceRunId");
        final List<String> results=new ArrayList<>();
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();
        final List<Probe> probes=new ArrayList<>();
        final BlockPos origin=new BlockPos(4096,96,4096),rackPos=origin.east(5);
        TestPlayer player;
        Robot robot;
        Tablet tablet;
        ItemStack heldTablet=ItemStack.EMPTY;
        ItemStack generatedProgram=ItemStack.EMPTY;
        Rack rack;
        BlockEntity rackEntity;
        Block rackBlock;
        Probe lower,upper;
        CompoundTag rackSnapshot;
        BlockState rackSnapshotState;
        String firstAddress,secondAddress,driveAddress;
        Component firstFilesystem;
        int ticks,robotStartTick,rackPhase,rackPhaseTick;
        boolean robotDone,rackDone,finished;
        Run(MinecraftServer server){this.server=server;level=server.overworld();}
        void start()throws Exception{
            require(directory.equals(Path.of("").toAbsolutePath().normalize())&&directory.getParent().getFileName().toString().equals("devices")&&directory.getFileName().toString().equals(runId),"Refusing a non-fixture device directory");
            require(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(directory.resolve("ocfixture-device-world")),"Refusing a different Minecraft world");
            if(Boolean.getBoolean("ocfixture.driveConcurrencyOnly")){level.setChunkForced(origin.getX()>>4,origin.getZ()>>4,true);level.getChunkAt(origin);robotDone=rackDone=true;DiskConcurrencyContract.start(level,origin.south(6),results);return;}
            if(Boolean.getBoolean("ocfixture.fluidOnly")){level.setChunkForced(origin.getX()>>4,origin.getZ()>>4,true);level.getChunkAt(origin);robotDone=rackDone=true;AgentFluidContract.start(level,origin.south(12),results);return;}
            if(Boolean.getBoolean("ocfixture.experienceOnly")){level.setChunkForced(origin.getX()>>4,origin.getZ()>>4,true);level.getChunkAt(origin);robotDone=rackDone=true;ExperienceContract.start(level,origin.south(12),results);return;}
            if(Boolean.getBoolean("ocfixture.generatorOnly")){level.setChunkForced(origin.getX()>>4,origin.getZ()>>4,true);level.getChunkAt(origin);robotDone=rackDone=true;GeneratorContract.start(level,origin.south(12),results);return;}
            if(Boolean.getBoolean("ocfixture.recipeConfigOnly")){robotDone=rackDone=true;RecipeConfigContract.start(server,results);return;}
            if(Boolean.getBoolean("ocfixture.enderIOOnly")){robotDone=rackDone=true;EnderIOContract.start(level,origin.south(12),results);return;}
            if(Boolean.getBoolean("ocfixture.tis3dOnly")){robotDone=rackDone=true;Tis3dContract.start(level,origin.south(12),results);return;}
            if(Boolean.getBoolean("ocfixture.upgradeStorageOnly")){robotDone=rackDone=true;UpgradeStorageContract.start(level,origin.south(12),results);return;}
            if(Boolean.getBoolean("ocfixture.vanillaOnly")){level.setChunkForced(origin.getX()>>4,origin.getZ()>>4,true);level.getChunkAt(origin);robotDone=rackDone=true;VanillaContract.start(level,origin.south(12),results);return;}
            if(Boolean.getBoolean("ocfixture.forgeEnergyOnly")){level.setChunkForced(origin.getX()>>4,origin.getZ()>>4,true);level.getChunkAt(origin);robotDone=rackDone=true;ForgeEnergyContract.start(level,origin.south(12),results);return;}
            require(CreativeTab.instance!=null,"OC did not publish its creative tab");
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(),true,level.registryAccess()));
            generatedProgram=LootContract.generate(level,results);
            ItemStack creativeRobot=creative("robot"),creativeTablet=creative("tablet");
            level.setChunkForced(origin.getX()>>4,origin.getZ()>>4,true);level.getChunkAt(origin);
            require(level.isEmptyBlock(origin)&&level.isEmptyBlock(rackPos),"Isolated device cells are occupied; no blocks were replaced");
            player=new TestPlayer(level);player.getAbilities().instabuild=true;player.setPos(origin.getX()+.5,origin.getY(),origin.getZ()-2);
            try{startRobot(creativeRobot);}catch(Throwable error){robotDone=true;results.add("FAIL creative robot: "+error);LOGGER.error("DEVICE_FIXTURE robot setup",error);}
            try{testTablet(creativeTablet);}catch(Throwable error){results.add("FAIL creative tablet: "+error);LOGGER.error("DEVICE_FIXTURE tablet bay",error);}
            try{startRack();}catch(Throwable error){rackDone=true;results.add("FAIL rack setup: "+error);LOGGER.error("DEVICE_FIXTURE rack setup",error);}
            try{DiskConcurrencyContract.start(level,origin.south(6),results);}catch(Throwable error){results.add("FAIL external drive concurrency setup: "+error);LOGGER.error("DEVICE_FIXTURE drive concurrency setup",error);DiskConcurrencyContract.close();}
        }
        ItemStack creative(String key){
            ItemStack base=item(key);
            for(ItemStack stack:CreativeTab.instance.getDisplayItems()){
                if(!stack.is(base.getItem()))continue;CompoundTag data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
                if(data.getInt("oc:tier")==3&&(data.contains("oc:components",Tag.TAG_LIST)||data.contains("oc:items",Tag.TAG_LIST)))return stack.copy();
            }
            throw new AssertionError("Creative tab did not produce the original configured "+key);
        }
        void startRobot(ItemStack configured)throws Exception{
            ItemStack drive=item("diskdrive");boolean ordinaryBay=false;
            for(Tag value:configured.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getList("oc:containers",Tag.TAG_COMPOUND))if(ItemStack.parseOptional(level.registryAccess(),(CompoundTag)value).is(drive.getItem()))ordinaryBay=true;
            require(ordinaryBay,"Configured creative robot did not use the ordinary disk-drive block as its floppy container");
            require(configured.getItem() instanceof BlockItem,"Configured robot is not a placeable block item");
            restored.put(origin,level.getBlockState(origin));player.setItemInHand(InteractionHand.MAIN_HAND,configured);
            var hit=new BlockHitResult(Vec3.atCenterOf(origin),Direction.UP,origin,false);
            require(((BlockItem)configured.getItem()).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,configured,hit)).consumesAction(),"Real configured robot BlockItem.place failed");
            require(level.getBlockEntity(origin) instanceof Robot,"Placed configured robot did not expose its public Robot host");robot=(Robot)level.getBlockEntity(origin);
            var bayDriver=Driver.driverFor(drive,robot.machine().host().getClass());
            require(bayDriver instanceof li.cil.oc.api.driver.item.Container bay&&li.cil.oc.api.driver.item.Slot.Floppy.equals(bay.providedSlot(drive)),"Ordinary disk-drive driver is not an actual floppy container for the real robot host");
            Container equipment=robot.equipmentInventory();int slot=-1;ItemStack floppy=item("floppy");
            for(int index=1;index<equipment.getContainerSize();index++)if(robot.canPlaceItem(index,floppy)){slot=index;break;}
            require(slot>=0,"The original configured robot has no functioning hot-swappable floppy slot");
            equipment.setItem(slot,floppy);var environment=robot.getComponentInSlot(slot);
            require(environment!=null&&environment.node() instanceof Component component&&component.name().equals("filesystem")&&component.canBeSeenFrom(robot.machine().node()),"Real robot bay insertion did not create a machine-visible filesystem");
            Node old=environment.node();String address=old.address();((Component)old).invoke("setLabel",robot.machine(),"fixture-robot");ItemStack removed=equipment.removeItem(slot,1);
            require(!removed.isEmpty()&&old.network()==null,"Removing the actual robot floppy did not detach its old filesystem node");
            equipment.setItem(slot,removed);var remounted=robot.getComponentInSlot(slot);require(remounted!=null&&address.equals(remounted.node().address())&&"fixture-robot".equals(((Component)remounted.node()).invoke("getLabel",robot.machine())[0]),"Robot physical removal/reinsertion lost the disk address or its latest unsaved label");results.add("PASS robot.bay ordinary diskdrive accepts a real floppy, detaches it on removal and remounts it with its address/latest label preserved");
        }
        void testTablet(ItemStack configured){
            ItemStack drive=item("diskdrive");ItemStack bay=ItemStack.parseOptional(level.registryAccess(),configured.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:container"));
            require(bay.is(drive.getItem()),"Original configured tablet did not use the ordinary disk-drive block as its bay");
            heldTablet=configured;player.setItemInHand(InteractionHand.MAIN_HAND,heldTablet);
            heldTablet.getItem().inventoryTick(heldTablet,level,player,player.getInventory().selected,true);
            player.setShiftKeyDown(true);heldTablet.getItem().use(level,player,InteractionHand.MAIN_HAND);
            heldTablet.getItem().releaseUsing(heldTablet,level,player,heldTablet.getItem().getUseDuration(heldTablet,player)-1);player.setShiftKeyDown(false);
            AbstractContainerMenu menu=player.containerMenu;require(menu!=player.inventoryMenu&&!menu.slots.isEmpty()&&menu.slots.getFirst().container instanceof Tablet,"Actual tablet interaction did not open its real host inventory");
            tablet=(Tablet)menu.slots.getFirst().container;Container inventory=menu.slots.getFirst().container;
            var driver=Driver.driverFor(drive,tablet.getClass());require(driver instanceof li.cil.oc.api.driver.item.Container container&&li.cil.oc.api.driver.item.Slot.Floppy.equals(container.providedSlot(drive)),"Ordinary disk drive is not the real tablet floppy-bay driver");
            ItemStack original=menu.slots.getFirst().remove(1),floppy=item("floppy");require(menu.slots.getFirst().mayPlace(floppy),"Actual tablet menu rejected a regular floppy in the original diskdrive bay");
            menu.slots.getFirst().setByPlayer(floppy);Node old=null;
            for(Node node:tablet.machine().node().network().nodes())if(node instanceof Component component&&component.name().equals("filesystem")&&tablet.componentSlot(node.address())==31){old=node;break;}
            require(old!=null&&old.canBeReachedFrom(tablet.machine().node()),"Actual tablet hot-swap did not connect its bay filesystem");
            ItemStack removed=inventory.removeItem(0,1);require(!removed.isEmpty()&&old.network()==null,"Actual tablet hot-removal left the old filesystem connected");
            inventory.setItem(0,original);player.closeFixtureMenu();
            results.add("PASS tablet.bay real item interaction/menu accepts a floppy through ordinary diskdrive, connects slot 31 and detaches it when removed");
        }
        void startRack(){
            rackBlock=li.cil.oc.api.Items.get("rack").block();require(rackBlock!=null,"Real rack block is unavailable");
            restored.put(rackPos,level.getBlockState(rackPos));require(level.setBlock(rackPos,rackBlock.defaultBlockState(),3),"Could not place real rack");
            rackEntity=level.getBlockEntity(rackPos);require(rackEntity instanceof Rack,"Placed rack does not expose the public Rack API");rack=(Rack)rackEntity;
            ItemStack mountable=item("diskdrivemountable");require(rack.canPlaceItem(0,mountable)&&rack.canPlaceItem(1,mountable),"Real rack rejected its original disk-drive mountable");rack.setItem(0,mountable);rack.setItem(1,mountable);
            lower=new Probe();upper=new Probe();probes.add(lower);probes.add(upper);rackPhase=0;rackPhaseTick=ticks;
        }
        void tick(){
            require(++ticks<=1000,"Real device contracts exceeded 1000 ticks; rack phase="+rackPhase);
            if(!robotDone)try{tickRobot();}catch(Throwable error){robotDone=true;results.add("FAIL robot.vm: "+error);LOGGER.error("DEVICE_FIXTURE robot VM",error);}
            if(!rackDone)try{tickRack();}catch(Throwable error){rackDone=true;results.add("FAIL rack: "+error);LOGGER.error("DEVICE_FIXTURE rack behavior",error);}
            DiskConcurrencyContract.tick();
            AgentFluidContract.tick();
            ForgeEnergyContract.tick();
            ExperienceContract.tick();
            GeneratorContract.tick();
            VanillaContract.tick();
            RecipeConfigContract.tick();
            EnderIOContract.tick();
            Tis3dContract.tick();
            UpgradeStorageContract.tick();
            if(robotDone&&rackDone&&!DiskConcurrencyContract.isRunning()&&!AgentFluidContract.isRunning()&&!ForgeEnergyContract.isRunning()&&!ExperienceContract.isRunning()&&!GeneratorContract.isRunning()&&!VanillaContract.isRunning()&&!RecipeConfigContract.isRunning()&&!EnderIOContract.isRunning()&&!Tis3dContract.isRunning()&&!UpgradeStorageContract.isRunning())finish();
        }
        void tickRobot(){
            Machine machine=robot.machine();
            if(robotStartTick==0){if(ticks<3)return;require(machine.architecture()!=null,"Configured robot has no usable architecture");
                require(machine.componentCount()<=machine.maxComponents(),"Configured robot exceeds CPU capacity: "+machine.componentCount()+"/"+machine.maxComponents());
                require(machine.start(),"Configured robot failed actual VM start: "+machine.lastError());robotStartTick=ticks;return;}
            require(machine.lastError()==null,"Original configured robot VM failed: "+machine.lastError()+" components="+machine.componentCount()+"/"+machine.maxComponents());
            require(machine.isRunning(),"Original configured robot stopped before boot acceptance: "+machine.lastError());
            if(ticks-robotStartTick<60||machine.upTime()<2||machine.cpuTime()<=0){require(ticks-robotStartTick<600,"Configured robot did not execute its original native BIOS within 600 ticks");return;}
            results.add("PASS robot.vm original configured BIOS executed: architecture="+li.cil.oc.api.Machine.getArchitectureName(machine.architecture().getClass())+" components="+machine.componentCount()+"/"+machine.maxComponents()+" uptime="+machine.upTime()+" cpu_seconds="+machine.cpuTime());robotDone=true;
        }
        void openRack(){player.closeFixtureMenu();player.setShiftKeyDown(false);player.setPos(rackPos.getX()+.5,rackPos.getY()+.5,rackPos.getZ()-2);require(rackEntity instanceof MenuProvider,"Actual rack has no menu provider");require(player.openMenu((MenuProvider)rackEntity,buffer->buffer.writeBlockPos(rackPos)).isPresent(),"Could not open real rack menu");}
        void connect(int slot,Direction side){
            ResourceLocation id=ResourceLocation.fromNamespaceAndPath("opencomputers","rack_edit");var codec=NetworkRegistry.getCodec(id,ConnectionProtocol.PLAY,PacketFlow.SERVERBOUND);require(codec!=null,"Rack edit payload has no registered server codec");
            RegistryFriendlyByteBuf buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());
            try{buffer.writeBlockPos(rackPos);buffer.writeVarInt(slot);buffer.writeVarInt(0);buffer.writeVarInt(side.get3DDataValue());buffer.writeVarInt(0);buffer.writeBoolean(false);var payload=codec.decode(buffer);require(!buffer.isReadable(),"Rack edit codec did not consume the expected wire fields");NetworkRegistry.handleModdedPayload(player.connection,new ServerboundCustomPayloadPacket(payload));}finally{buffer.release();}
            require(rack.getMountable(slot).node().network()==rack.sidedNode(rack.toGlobal(side)).network(),"Real rack edit handler did not connect its selected primary bus");
        }
        void attachProbes(){lower.node().connect(rack.sidedNode(rack.toGlobal(Direction.DOWN)));upper.node().connect(rack.sidedNode(rack.toGlobal(Direction.UP)));lower.node().changeBuffer(1_000_000);upper.node().changeBuffer(1_000_000);}
        RackMountable drive(int slot){RackMountable result=rack.getMountable(slot);require(result!=null&&result instanceof Container&&result.node() instanceof Component component&&component.name().equals("disk_drive"),"Rack slot "+slot+" did not create its real disk_drive mountable/inventory");return result;}
        Component filesystem(RackMountable drive,Probe context)throws Exception{
            Object[] media=((Component)drive.node()).invoke("media",context);require(media!=null&&media.length==1&&media[0] instanceof String,"Mounted disk_drive did not expose its actual medium address");
            Node fs=drive.node().network().node((String)media[0]);require(fs instanceof Component component&&component.name().equals("filesystem"),"Medium address does not resolve to its real filesystem node");return (Component)fs;
        }
        void write(Component filesystem,Probe context,String content,String label)throws Exception{
            require(filesystem.canBeSeenFrom(context.node()),"Filesystem is not visible on this actual rack bus");Object handle=filesystem.invoke("open",context,"device-contract.txt","w")[0];filesystem.invoke("write",context,handle,content.getBytes(StandardCharsets.UTF_8));filesystem.invoke("close",context,handle);filesystem.invoke("setLabel",context,label);
        }
        void read(Component filesystem,Probe context,String content,String label)throws Exception{
            require(filesystem.canBeSeenFrom(context.node()),"Restored filesystem is not visible on the selected rack bus");Object handle=filesystem.invoke("open",context,"device-contract.txt","r")[0];Object[] value=filesystem.invoke("read",context,handle,4096);filesystem.invoke("close",context,handle);
            require(value!=null&&value.length==1&&content.equals(text(value[0])),"Actual mounted floppy returned different persistent bytes");Object[] savedLabel=filesystem.invoke("getLabel",context);require(savedLabel!=null&&label.equals(savedLabel[0]),"Actual mounted floppy lost its label");
        }
        void tickRack()throws Exception{
            require(ticks-rackPhaseTick<120,"Rack real block lifecycle exceeded 120 ticks in phase "+rackPhase);
            if(rackPhase==0){
                if(rack.getMountable(0)==null||rack.getMountable(1)==null)return;
                RackMountable first=drive(0),second=drive(1);((Container)first).setItem(0,item("floppy"));((Container)second).setItem(0,item("floppy"));
                openRack();connect(0,Direction.DOWN);connect(1,Direction.UP);attachProbes();firstFilesystem=filesystem(first,lower);Component independent=filesystem(second,upper);
                require(firstFilesystem.canBeSeenFrom(lower.node())&&!firstFilesystem.canBeSeenFrom(upper.node())&&independent.canBeSeenFrom(upper.node())&&!independent.canBeSeenFrom(lower.node()),"Two selected rack buses leaked filesystem visibility into each other");
                double before=lower.node().globalBuffer();write(firstFilesystem,lower,"first rack medium\n","fixture-first");read(firstFilesystem,lower,"first rack medium\n","fixture-first");double expected=(API.config.getDouble("power.cost.hddWrite")+API.config.getDouble("power.cost.hddRead"))*"first rack medium\n".getBytes(StandardCharsets.UTF_8).length/1024;
                require(Math.abs(before-lower.node().globalBuffer()-expected)<.000001,"Real floppy I/O did not charge configured read/write energy");
                firstAddress=firstFilesystem.address();secondAddress=independent.address();driveAddress=first.node().address();
                results.add("PASS rack.bus real menu + registered rack_edit codec/handler connects two isolated primary buses; actual filesystem write/read/label consumes "+expected+" OC energy");
                ItemStack original=((Container)first).removeItem(0,1);require(firstFilesystem.network()==null&&lower.node().network().node(firstAddress)==null,"Removing a floppy retained its filesystem on the old rack bus");
                ((Container)first).setItem(0,item("floppy"));Component replacement=filesystem(first,lower);require(!replacement.address().equals(firstAddress),"A different blank floppy reused the previous medium address");Object[] absent=replacement.invoke("exists",lower,"device-contract.txt");require(absent!=null&&Boolean.FALSE.equals(absent[0]),"A replacement blank floppy inherited the old medium's files");
                write(replacement,lower,"second rack medium\n","fixture-second");Node replacedNode=replacement;((Container)first).removeItem(0,1);require(replacedNode.network()==null,"Replacing the second floppy did not detach its filesystem");((Container)first).setItem(0,original);
                firstFilesystem=filesystem(first,lower);require(firstAddress.equals(firstFilesystem.address()),"Reinserting the same floppy changed its persistent node address");read(firstFilesystem,lower,"first rack medium\n","fixture-first");
                Node oldDrive=first.node(),oldMedium=firstFilesystem;ItemStack blade=rack.removeItem(0,1);require(oldDrive.network()==null&&oldMedium.network()==null,"Uninstalling the whole rack drive retained a component node");rack.setItem(0,blade);first=drive(0);require(driveAddress.equals(first.node().address()),"Reinstalling the rack drive changed its component address");connect(0,Direction.DOWN);firstFilesystem=filesystem(first,lower);read(firstFilesystem,lower,"first rack medium\n","fixture-first");
                results.add("PASS rack.media swapping real floppies separates files/identities; reinsertion and whole-drive removal/reinstallation preserve data, label and addresses while removed nodes detach");
                rackSnapshotState=level.getBlockState(rackPos);rackSnapshot=rackEntity.saveWithFullMetadata(level.registryAccess());rack.clearContent();level.removeBlock(rackPos,false);require(firstFilesystem.network()==null,"Removing the actual rack retained the old medium node");rackPhase=1;rackPhaseTick=ticks;return;
            }
            if(rackPhase==1){if(ticks-rackPhaseTick<10)return;require(level.setBlock(rackPos,rackSnapshotState,3),"Could not reconstruct the saved rack block");rackEntity=level.getBlockEntity(rackPos);require(rackEntity instanceof Rack,"Reconstructed rack has no public host");rackEntity.loadWithComponents(rackSnapshot,level.registryAccess());rack=(Rack)rackEntity;rackPhase=2;rackPhaseTick=ticks;return;}
            if(rackPhase==2){
                if(rack.getMountable(0)==null||rack.getMountable(1)==null)return;RackMountable first=drive(0),second=drive(1);
                require(first.node().network()==rack.sidedNode(rack.toGlobal(Direction.DOWN)).network()&&second.node().network()==rack.sidedNode(rack.toGlobal(Direction.UP)).network(),"Saved actual rack primary-bus wiring did not restore");attachProbes();Component fs=filesystem(first,lower),other=filesystem(second,upper);
                require(firstAddress.equals(fs.address())&&secondAddress.equals(other.address())&&driveAddress.equals(first.node().address()),"Public world block-entity serialization lost a drive/media component identity");require(!fs.canBeSeenFrom(upper.node())&&!other.canBeSeenFrom(lower.node()),"Restored rack merged isolated primary networks");read(fs,lower,"first rack medium\n","fixture-first");
                results.add("PASS rack.world_save real BlockEntity save/remove/load rebuild preserves mounted media bytes, labels, addresses and isolated primary wiring (same process; cold-process restart remains outside this scenario)");
                ItemStack original=((Container)first).removeItem(0,1);((Container)first).setItem(0,generatedProgram);
                LootContract.readProgram(filesystem(first,lower),lower,results);
                ((Container)first).removeItem(0,1);((Container)first).setItem(0,original);rackDone=true;
            }
        }
        void finish(){
            if(finished)return;finished=true;
            try{cleanup();}catch(Throwable error){results.add("FAIL cleanup: "+error);LOGGER.error("DEVICE_FIXTURE cleanup",error);}
            boolean failure=results.stream().anyMatch(value->value.startsWith("FAIL"));List<String> report=new ArrayList<>();report.add("status="+(failure?"FAIL":"PASS"));report.add("scenario=real-devices");report.add("run_id="+runId);report.add("ticks="+ticks);report.addAll(results);
            try{Files.createDirectories(directory);Files.write(directory.resolve("device-results.txt"),report,StandardCharsets.UTF_8);}catch(Exception error){throw new IllegalStateException("Could not write device report",error);}
            for(String result:results)LOGGER.info("DEVICE_FIXTURE {}",result);LOGGER.info("DEVICE_FIXTURE_COMPLETE status={}",failure?"FAIL":"PASS");server.halt(false);
        }
        void cleanup(){
            DiskConcurrencyContract.close();
            AgentFluidContract.close();
            ForgeEnergyContract.close();
            ExperienceContract.close();
            GeneratorContract.close();
            VanillaContract.close();
            RecipeConfigContract.close();
            EnderIOContract.close();
            Tis3dContract.close();
            UpgradeStorageContract.close();
            if(player!=null)player.closeFixtureMenu();if(robot!=null){robot.machine().stop();robot.equipmentInventory().clearContent();robot.mainInventory().clearContent();}if(tablet!=null){tablet.machine().stop();tablet.machine().node().remove();}
            for(Probe probe:probes)probe.node().remove();probes.clear();
            var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries){if(level.getBlockEntity(entry.getKey()) instanceof Rack existing)existing.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}restored.clear();
            if(player!=null){player.getInventory().clearContent();player.channel.finishAndReleaseAll();}level.setChunkForced(origin.getX()>>4,origin.getZ()>>4,false);
        }
    }
    /** Test-only in-memory caller; all menu creation and payload validation remain the real implementation. */
    private static final class TestPlayer extends FakePlayer {
        final EmbeddedChannel channel=new EmbeddedChannel();
        private int menuId;
        TestPlayer(ServerLevel level){
            super(level,new GameProfile(UUID.fromString("a32319c9-4cf3-4d49-8de4-851e417dad18"),"OC_DeviceFixture"));
            Connection transport=new Connection(PacketFlow.SERVERBOUND){@Override public Channel channel(){return TestPlayer.this.channel;}@Override public boolean isConnected(){return TestPlayer.this.channel.isOpen();}@Override public void send(Packet<?> packet,PacketSendListener listener,boolean flush){}};
            NetworkRegistry.configureMockConnection(transport);connection=new ServerGamePacketListenerImpl(level.getServer(),transport,this,CommonListenerCookie.createInitial(getGameProfile(),false));
        }
        @Override public OptionalInt openMenu(MenuProvider provider,Consumer<RegistryFriendlyByteBuf> extraDataWriter){if(provider==null)return OptionalInt.empty();closeFixtureMenu();AbstractContainerMenu opened=provider.createMenu(++menuId,getInventory(),this);if(opened==null)return OptionalInt.empty();containerMenu=opened;return OptionalInt.of(opened.containerId);}
        @Override public boolean hasDisconnected(){return false;}
        void closeFixtureMenu(){if(containerMenu!=inventoryMenu){containerMenu.removed(this);containerMenu=inventoryMenu;}}
    }
    private static final class Probe extends AbstractManagedEnvironment implements Context {
        Probe(){setNode(Network.newNode(this,Visibility.Network).withConnector(1_000_000).create());Network.joinNewNetwork(node());}
        @Override public Connector node(){return (Connector)super.node();}
        @Override public boolean canInteract(String player){return true;}
        @Override public boolean isRunning(){return true;}
        @Override public boolean isPaused(){return false;}
        @Override public boolean start(){return false;}
        @Override public boolean stop(){return false;}
        @Override public boolean pause(double seconds){return false;}
        @Override public void consumeCallBudget(double amount){}
        @Override public boolean signal(String name,Object...args){return false;}
    }
}
