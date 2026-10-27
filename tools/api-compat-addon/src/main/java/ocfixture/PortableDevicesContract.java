package ocfixture;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.item.Chargeable;
import li.cil.oc.api.internal.Microcontroller;
import li.cil.oc.api.internal.Tablet;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.machine.MachineHost;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Only real assembled hosts, normal item/menu interactions and published SDK contexts. */
public final class PortableDevicesContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-portable-devices");
    private static Run active;
    private PortableDevicesContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){require(active==null,"A portable-device contract is already active");Run run=new Run(level,position.immutable(),results);active=run;try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}}
    public static boolean isRunning(){return active!=null;}
    public static void tick(){Run run=active;if(run==null)return;try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}active=null;run.cleanup();}
    public static void close(){Run run=active;active=null;if(run!=null){run.results.add("FAIL portable.devices: server stopped before the actual host contract completed");run.cleanup();}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void equal(double actual,double expected,String message){require(Math.abs(actual-expected)<=1e-7,message+": expected="+expected+", actual="+actual);}
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing portable-device item "+name);return descriptor.createItemStack(1);}
    private static CompoundTag tag(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static String text(Object value){return value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):String.valueOf(value);}
    @FunctionalInterface private interface Operation{void run()throws Exception;}
    private static void rejected(Operation operation,String expected)throws Exception{try{operation.run();}catch(Exception error){require(error.getMessage()!=null&&error.getMessage().contains(expected),"Unexpected portable callback error "+error);return;}throw new AssertionError("Portable callback accepted instead of "+expected);}
    private static final class Run {
        final ServerLevel level;final BlockPos position;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        final TestPlayer first,second;final String file="portable-hot-"+UUID.randomUUID(),expiredFile="portable-expire-"+UUID.randomUUID();
        ItemStack stack=ItemStack.EMPTY;Tablet tablet,expired;Container bay;Component hdd;Object expiredHandle;Microcontroller micro;int ticks,phase;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;first=new TestPlayer(level,"OC_PortableA");second=new TestPlayer(level,"OC_PortableB");}
        void remember(BlockPos pos){if(restored.containsKey(pos))return;if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Portable fixture refuses an occupied cell "+pos);restored.put(pos.immutable(),level.getBlockState(pos));}
        ItemStack preset(String name){for(ItemStack candidate:CreativeTab.instance.getDisplayItems())if(candidate.is(item(name).getItem())&&tag(candidate).getInt("oc:tier")==3)return candidate.copy();throw new AssertionError("No actual configured creative "+name);}
        void start(){
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(),true,level.registryAccess()));
            first.getAbilities().instabuild=true;second.getAbilities().instabuild=true;first.setPos(position.getX()+.5,position.getY(),position.getZ()-2);second.setPos(position.getX()+1.5,position.getY(),position.getZ()-2);
            remember(position.below());require(level.setBlock(position.below(),Blocks.STONE.defaultBlockState(),3),"Could not place portable fixture support");remember(position);
            ItemStack assembled=preset("microcontroller");first.setItemInHand(InteractionHand.MAIN_HAND,assembled);
            require(assembled.getItem() instanceof BlockItem&&((BlockItem)assembled.getItem()).place(new BlockPlaceContext(first,InteractionHand.MAIN_HAND,assembled,new BlockHitResult(Vec3.atCenterOf(position.below()).add(0,.5,0),Direction.UP,position.below(),false))).consumesAction(),"Actual assembled microcontroller placement failed");
            require(level.getBlockEntity(position) instanceof Microcontroller,"Placed world block is not the actual SDK Microcontroller");micro=(Microcontroller)level.getBlockEntity(position);
            stack=preset("tablet");first.setItemInHand(InteractionHand.MAIN_HAND,stack);tickItem(first);tablet=openBay(first);bay=(Container)tablet;
        }
        void tickItem(TestPlayer player){stack.getItem().inventoryTick(stack,level,player,player.getInventory().selected,true);}
        Tablet openBay(TestPlayer player){
            player.setShiftKeyDown(true);stack.getItem().use(level,player,InteractionHand.MAIN_HAND);stack.getItem().releaseUsing(stack,level,player,stack.getItem().getUseDuration(stack,player)-1);player.stopUsingItem();player.setShiftKeyDown(false);
            require(player.containerMenu!=player.inventoryMenu&&!player.containerMenu.slots.isEmpty()&&player.containerMenu.slots.getFirst().container instanceof Tablet,"Actual tablet short sneak-use did not expose its real host through the actual menu");return(Tablet)player.containerMenu.slots.getFirst().container;
        }
        Component component(MachineHost host,String name,int slot){for(Node node:host.machine().node().network().nodes())if(node instanceof Component component&&component.name().equals(name)&&(slot<0||host.componentSlot(node.address())==slot))return component;throw new AssertionError("Missing actual "+name+" at component slot "+slot);}
        Map<String,Node> hardware(MachineHost host,int excluded){Map<String,Node> values=new LinkedHashMap<>();for(Node node:host.machine().node().network().nodes()){int slot=host.componentSlot(node.address());if(slot>=0&&slot!=excluded)values.put(node.address(),node);}require(!values.isEmpty(),"Actual configured host has no unchanged hardware to observe");return values;}
        void unchanged(MachineHost host,Map<String,Node> previous){for(var entry:previous.entrySet()){Node found=null;for(Node node:host.machine().node().network().nodes())if(entry.getKey().equals(node.address())){found=node;break;}require(found==entry.getValue(),"Unrelated hot swap reconstructed actual component "+entry.getKey());}}
        boolean tick()throws Exception{
            require(++ticks<80,"Portable-device contract exceeded its finite hot-swap/holder/expiration sequence");if(ticks<3){tickItem(first);return false;}
            if(phase==0){microcontroller();tablet();expired=tablet;expiredHandle=hdd.invoke("open",tablet.machine(),expiredFile,"w")[0];hdd.invoke("write",tablet.machine(),expiredHandle,"closed-on-expiration".getBytes(StandardCharsets.UTF_8));second.closeFixtureMenu();second.discard();phase=1;return false;}
            if(phase==1){
                if(expired.machine().node().network()!=null)return false;second.getInventory().clearContent();first.setItemInHand(InteractionHand.MAIN_HAND,stack);tickItem(first);Tablet recreated=openBay(first);require(recreated!=expired&&recreated.machine()!=expired.machine()&&!recreated.machine().isRunning(),"Natural removed-holder expiration did not recreate a stopped real tablet host");
                Component restored=component(recreated,"filesystem",slotOfHdd(recreated));rejected(()->restored.invoke("close",recreated.machine(),expiredHandle),"bad file descriptor");
                require(((Number)restored.invoke("size",recreated.machine(),expiredFile)[0]).longValue()=="closed-on-expiration".length(),"Actual expiry disconnect/save lost HDD contents");restored.invoke("remove",recreated.machine(),file);restored.invoke("remove",recreated.machine(),expiredFile);tablet=recreated;first.closeFixtureMenu();first.discard();phase=2;return false;
            }
            if(tablet.machine().node().network()!=null)return false;
            results.add("PASS portable.expiry natural holder removal expires the actual tablet, disconnects and saves closed components, and recreates a stopped host with retained HDD bytes but no revived old descriptor");
            results.add("LIMIT portable.devices: reuses prior native Lua 5.3 separate-JVM Tablet/Microcontroller and charger-remount evidence; this finite SDK run does not boot firmware or remeasure all routing/architecture combinations, remote input, local tone or Windows visuals");LOGGER.info("PORTABLE_DEVICES PASS finite actual microcontroller/tablet hot-swap, holder and expiry paths");return true;
        }
        int slotOfHdd(Tablet host){for(ItemStack part:host.internalComponents())if(!part.isEmpty()){var driver=Driver.driverFor(part,host.getClass());if(driver!=null&&driver.slot(part).equals("hdd")){CompoundTag data=driver.dataTag(part);String address=data.getCompound("node").getString("address");int slot=host.componentSlot(address);if(slot>=0)return slot;}}throw new AssertionError("Actual configured tablet has no installed HDD");}
        void microcontroller()throws Exception{
            require(!micro.machine().isRunning()&&micro.machine().node().network()!=null,"Actual microcontroller was not initialized as a stopped physical host");((Connector)micro.machine().node()).changeBuffer(Double.POSITIVE_INFINITY);
            Direction front=micro.facing(),side=front.getClockWise();Node[] analyzed=((Analyzable)micro).onAnalyze(first,side,.5f,.5f,.5f);require(analyzed.length==1&&analyzed[0] instanceof Component,"Actual microcontroller face analysis did not expose its real controller");Component control=(Component)analyzed[0];
            rejected(()->control.invoke("isSideOpen",micro.machine(),front.get3DDataValue()),"unsupported side");rejected(()->control.invoke("isSideOpen",micro.machine(),6),"invalid side");require(Boolean.TRUE.equals(control.invoke("setSideOpen",micro.machine(),side.get3DDataValue(),false)[0])&&Boolean.FALSE.equals(control.invoke("isSideOpen",micro.machine(),side.get3DDataValue())[0]),"Real side callback did not return its old state");control.invoke("setSideOpen",micro.machine(),side.get3DDataValue(),true);
            Map<String,Node> original=hardware(micro,-1);swapEeprom(li.cil.oc.api.Items.registerEEPROM("Portable A","return".getBytes(StandardCharsets.UTF_8),new byte[0],false));unchanged(micro,original);
            Component firstEeprom=component(micro,"eeprom",-1);byte[] bytes="portable-eeprom-data".getBytes(StandardCharsets.UTF_8);firstEeprom.invoke("setData",micro.machine(),bytes);original=hardware(micro,micro.componentSlot(firstEeprom.address()));String address=firstEeprom.address();
            swapEeprom(li.cil.oc.api.Items.registerEEPROM("Portable B","return".getBytes(StandardCharsets.UTF_8),new byte[0],false));unchanged(micro,original);require(firstEeprom.network()==null,"Real swapped EEPROM remained on the microcontroller graph");boolean returned=false;
            for(int slot=0;slot<first.getInventory().getContainerSize();slot++){ItemStack part=first.getInventory().getItem(slot);var driver=Driver.driverFor(part);if(driver==null)continue;CompoundTag data=driver.dataTag(part);if(data!=null&&address.equals(data.getCompound("node").getString("address"))){require(java.util.Arrays.equals(bytes,data.getByteArray("oc:userdata")),"Actual returned EEPROM did not receive its live data before removal");returned=true;}}
            require(returned,"Normal microcontroller EEPROM interaction did not return the real old component");
            IEnergyStorage energy=level.getCapability(Capabilities.EnergyStorage.BLOCK,position,side);require(energy!=null,"Actual non-front microcontroller FE capability is missing");((Connector)micro.machine().node()).changeBuffer(Double.NEGATIVE_INFINITY);require(energy.receiveEnergy(100,true)>0,"Actual open microcontroller FE side rejected simulated input");
            ItemStack wrench=item("wrench");first.setItemInHand(InteractionHand.MAIN_HAND,wrench);require(wrench.getItem().onItemUseFirst(wrench,new UseOnContext(first,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(position),Direction.UP,position,false))).consumesAction()&&micro.facing()==side,"Normal scrench did not make the retained FE side the new front");
            require(!energy.canReceive()&&energy.receiveEnergy(100,true)==0&&energy.receiveEnergy(100,false)==0,"Retained FE capability still charged the newly closed microcontroller front");
            results.add("PASS portable.microcontroller real face callbacks preserve invalid/unsupported errors, normal two-stage EEPROM exchange saves returned data and retains unrelated nodes, and a retained FE capability rejects the actual rotated front");
            first.setItemInHand(InteractionHand.MAIN_HAND,stack);
        }
        void swapEeprom(ItemStack replacement){first.setShiftKeyDown(true);first.setItemInHand(InteractionHand.MAIN_HAND,replacement);require(level.getBlockState(position).useItemOn(replacement,level,first,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(position),Direction.UP,position,false)).consumesAction(),"Normal sneak-use microcontroller EEPROM swap failed");first.setShiftKeyDown(false);}
        void tablet()throws Exception{
            require(tablet.player()==first&&!tablet.machine().isRunning(),"Actual tablet menu returned the wrong holder/Context");Machine machine=tablet.machine();((Connector)machine.node()).changeBuffer(Double.POSITIVE_INFINITY);hdd=component(tablet,"filesystem",slotOfHdd(tablet));Map<String,Node> original=hardware(tablet,31);
            Object handle=hdd.invoke("open",machine,file,"w")[0];hdd.invoke("write",machine,handle,"before|".getBytes(StandardCharsets.UTF_8));
            ItemStack previous=bay.removeItem(0,1),floppy=item("floppy");require(bay.canPlaceItem(0,floppy),"Actual configured diskdrive bay rejected floppy");bay.setItem(0,floppy);unchanged(tablet,original);
            Component removable=component(tablet,"filesystem",31);Object diskHandle=removable.invoke("open",machine,"bay-hot","w")[0];removable.invoke("write",machine,diskHandle,new byte[]{1,2,3});ItemStack removed=bay.removeItem(0,1);require(removable.network()==null,"Removed real tablet bay disk remained connected");bay.setItem(0,removed);Component reinserted=component(tablet,"filesystem",31);rejected(()->reinserted.invoke("close",machine,diskHandle),"bad file descriptor");reinserted.invoke("remove",machine,"bay-hot");bay.removeItem(0,1);bay.setItem(0,previous);unchanged(tablet,original);
            hdd.invoke("write",machine,handle,"after".getBytes(StandardCharsets.UTF_8));hdd.invoke("close",machine,handle);Object reader=hdd.invoke("open",machine,file,"r")[0];require("before|after".equals(text(hdd.invoke("read",machine,reader,128)[0])),"Unrelated bay changes closed or replaced the actual HDD's live descriptor");hdd.invoke("close",machine,reader);
            first.closeFixtureMenu();int old=first.getInventory().selected;first.getInventory().selected=(old+1)%9;stack.getItem().inventoryTick(stack,level,first,old,false);require(tablet.machine()==machine,"Changing selected hotbar slot recreated the actual tablet VM");first.getInventory().selected=old;
            first.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);second.setItemInHand(InteractionHand.MAIN_HAND,stack);second.setXRot(17);second.setYRot(73);tickItem(second);Tablet transferred=openBay(second);require(transferred==tablet&&transferred.machine()==machine&&transferred.player()==second,"Same-world holder transfer recreated the actual tablet host or retained its old holder");
            Component control=component(transferred,"tablet",-1);equal(((Number)control.invoke("getPitch",machine)[0]).doubleValue(),17,"Actual current-holder pitch callback");equal(((Number)control.invoke("getYaw",machine)[0]).doubleValue(),73,"Actual current-holder yaw callback");
            Chargeable chargeable=(Chargeable)stack.getItem();Connector energy=(Connector)machine.node();energy.changeBuffer(-9);double before=energy.globalBuffer();ItemStack snapshot=stack.copy();equal(chargeable.charge(stack,6,true),0,"Actual tablet charge SIM remainder");equal(energy.globalBuffer(),before,"Actual tablet charge SIM changed energy");require(ItemStack.isSameItemSameComponents(stack,snapshot),"Actual tablet charge SIM changed stack components");equal(chargeable.charge(stack,6,false),0,"Actual tablet charge EXEC remainder");equal(energy.globalBuffer(),before+6,"Actual tablet charge EXEC did not reach the real host connector");
            results.add("PASS portable.tablet actual menu bay swaps retain unchanged screen/keyboard/HDD objects and an open HDD writer, close/save only removed disk descriptors, preserve machine identity across hotbar selection/same-world holder transfer, read current pitch/yaw and honor charge SIM/EXEC");
        }
        void fail(Throwable error){results.add("FAIL portable.devices: "+error);LOGGER.error("PORTABLE_DEVICES FAIL",error);}
        void cleanup(){
            first.closeFixtureMenu();second.closeFixtureMenu();first.discard();second.discard();List<BlockPos> positions=new ArrayList<>(restored.keySet());Collections.reverse(positions);for(BlockPos pos:positions)try{level.setBlock(pos,restored.get(pos),3);}catch(Throwable error){LOGGER.warn("Could not restore portable fixture cell {}",pos,error);}first.channel.finishAndReleaseAll();second.channel.finishAndReleaseAll();for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);
        }
    }
    private static final class TestPlayer extends FakePlayer {
        final EmbeddedChannel channel=new EmbeddedChannel();private int menuId;
        TestPlayer(ServerLevel level,String name){super(level,new GameProfile(UUID.randomUUID(),name));Connection connection=new Connection(PacketFlow.SERVERBOUND){@Override public Channel channel(){return TestPlayer.this.channel;}@Override public boolean isConnected(){return TestPlayer.this.channel.isOpen();}@Override public void send(Packet<?> packet,PacketSendListener listener,boolean flush){}};NetworkRegistry.configureMockConnection(connection);this.connection=new ServerGamePacketListenerImpl(level.getServer(),connection,this,CommonListenerCookie.createInitial(getGameProfile(),false));}
        @Override public OptionalInt openMenu(MenuProvider provider,Consumer<RegistryFriendlyByteBuf> extraDataWriter){if(provider==null)return OptionalInt.empty();closeFixtureMenu();AbstractContainerMenu menu=provider.createMenu(++menuId,getInventory(),this);if(menu==null)return OptionalInt.empty();containerMenu=menu;return OptionalInt.of(menu.containerId);}
        @Override public boolean hasDisconnected(){return false;}
        void closeFixtureMenu(){if(containerMenu!=inventoryMenu){containerMenu.removed(this);containerMenu=inventoryMenu;}}
    }
}
