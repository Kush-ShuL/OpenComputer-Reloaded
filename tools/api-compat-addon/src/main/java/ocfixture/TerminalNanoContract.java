package ocfixture;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import li.cil.oc.api.API;
import li.cil.oc.api.Nanomachines;
import li.cil.oc.api.Network;
import li.cil.oc.api.component.RackMountable;
import li.cil.oc.api.internal.Rack;
import li.cil.oc.api.internal.Server;
import li.cil.oc.api.internal.TextBuffer;
import li.cil.oc.api.nanomachines.Behavior;
import li.cil.oc.api.nanomachines.BehaviorProvider;
import li.cil.oc.api.nanomachines.Controller;
import li.cil.oc.api.nanomachines.DisableReason;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Packet;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.network.WirelessEndpoint;
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
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owned real player, rack mountables, registered payloads and public nanomachine integrations. */
public final class TerminalNanoContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-terminal-nano");
    private static final String PROVIDER="f1c379d5-236a-4fcb-b880-3b88548f89c6";
    private static final NanoProvider NANO_PROVIDER=new NanoProvider();
    private static Run active;
    private TerminalNanoContract(){}
    public static void register(){Nanomachines.addProvider(NANO_PROVIDER);}
    public static void start(ServerLevel level,BlockPos position,List<String> results){Run run=new Run(level,position.immutable(),results);active=run;try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}}
    public static boolean isRunning(){return active!=null;}
    public static void tick(){Run run=active;if(run==null)return;try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}active=null;run.cleanup();}
    public static void close(){Run run=active;active=null;if(run!=null)run.cleanup();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static String text(Object value){return value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):String.valueOf(value);}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("opencomputers",path);}
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing actual terminal/nano item "+name);return descriptor.createItemStack(1);}
    private static CompoundTag data(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static void near(double actual,double expected,String operation){require(Math.abs(actual-expected)<.000001,operation+": expected "+expected+", actual "+actual);}
    private record Input(Node source,Object[] data){}
    private record Open(UUID token,String address,CompoundTag data){}
    private static final class Observer extends AbstractManagedEnvironment {
        final List<Input> inputs=new ArrayList<>();
        Observer(){setNode(Network.newNode(this,Visibility.Network).withComponent("terminal_contract_observer",Visibility.Network).create());Network.joinNewNetwork(node());}
        @Override public void onMessage(Message message){if("computer.checked_signal".equals(message.name()))inputs.add(new Input(message.source(),message.data().clone()));}
        boolean saw(Node source,String event){return inputs.stream().anyMatch(input->input.source==source&&input.data.length>=2&&event.equals(text(input.data[1])));}
    }
    /** A genuine addon behavior; it observes production lifecycle callbacks and its public controller. */
    private static final class NanoBehavior implements Behavior {
        final Player player;int enabled,disabled,outOfEnergy,updates;boolean controllerVisible=true;Controller enabledController;
        NanoBehavior(Player player){this.player=player;}
        public String getNameHint(){return "ocfixture_observer";}
        public void onEnable(){enabled++;enabledController=Nanomachines.getController(player);}
        public void onDisable(DisableReason reason){disabled++;if(reason==DisableReason.OutOfEnergy)outOfEnergy++;controllerVisible&=enabledController!=null&&Nanomachines.getController(player)==enabledController;}
        public void update(){updates++;}
    }
    private static final class NanoProvider implements BehaviorProvider {
        boolean owns(Player player){Run run=active;return run!=null&&run.profile.getId().equals(player.getUUID());}
        NanoBehavior create(Player player){NanoBehavior behavior=new NanoBehavior(player);active.behavior=behavior;return behavior;}
        public Iterable<Behavior> createBehaviors(Player player){return owns(player)?List.of(create(player)):List.of();}
        public CompoundTag writeToNBT(Behavior behavior){require(behavior instanceof NanoBehavior,"Provider received a foreign behavior");CompoundTag tag=new CompoundTag();tag.putString("provider",PROVIDER);if(active!=null)active.providerWrites++;return tag;}
        public Behavior readFromNBT(Player player,CompoundTag tag){if(!owns(player)||!PROVIDER.equals(tag.getString("provider")))return null;active.providerReads++;return create(player);}
    }
    /** Public WirelessEndpoint registered at a real owned cell, with actual network packet delivery. */
    private static final class Radio implements WirelessEndpoint {
        final ServerLevel level;final BlockPos pos;final List<Packet> received=new ArrayList<>();
        Radio(ServerLevel level,BlockPos pos){this.level=level;this.pos=pos;Network.joinWirelessNetwork(this);}
        public int x(){return pos.getX();}public int y(){return pos.getY();}public int z(){return pos.getZ();}public Level world(){return level;}
        public void receivePacket(Packet packet,WirelessEndpoint sender){if(packet.data().length>1&&"nanomachines".equals(text(packet.data()[0])))received.add(packet);}
        void send(String command,Object... values){Object[] payload=new Object[values.length+2];payload[0]="nanomachines".getBytes(StandardCharsets.UTF_8);payload[1]=command.getBytes(StandardCharsets.UTF_8);System.arraycopy(values,0,payload,2,values.length);Network.sendWirelessPacket(this,8,Network.newPacket("ocfixture-terminal-nano",null,1,payload));}
        Packet take(String event){for(int i=0;i<received.size();i++){Packet packet=received.get(i);if(event.equals(text(packet.data()[1]))){received.remove(i);return packet;}}return null;}
    }
    private static final class Run {
        final ServerLevel level;final BlockPos position;final List<String> results;
        final GameProfile profile=new GameProfile(UUID.randomUUID(),"OC_Nano_"+UUID.randomUUID().toString().substring(0,6));
        final EmbeddedChannel channel=new EmbeddedChannel();final ConcurrentLinkedQueue<net.minecraft.network.protocol.Packet<?>> packets=new ConcurrentLinkedQueue<>();
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        ServerPlayer actor;Rack rack;BlockEntity rackEntity;RackMountable terminal;Server blade;TextBuffer screen;Node keyboard;Observer observer;Radio radio;
        Controller controller;NanoBehavior behavior,retiringBehavior;ItemStack paired,configured;Open opened;int ticks,phase,phaseTick,useTicks,observerIndex=-1,providerWrites,providerReads,oldEnabled,oldDisabled,oldUpdates,inputCount;
        double frequency,cost,energyBefore;long energyTime;String nanoAddress;boolean joined,operator,cleaned;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;}
        void remember(BlockPos pos){if(restored.containsKey(pos))return;if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Terminal/nano fixture cell is occupied: "+pos);restored.put(pos.immutable(),level.getBlockState(pos));}
        void start()throws Exception{
            require(API.nanomachines!=null&&API.config!=null,"Actual nanomachine API/config is unavailable");boolean registered=false;for(BehaviorProvider provider:Nanomachines.getProviders())registered|=provider==NANO_PROVIDER;require(registered,"TerminalNanoContract.register was not called after OC API initialization");
            require(!API.config.getBoolean("power.ignorePower"),"Actual energy proof requires the isolated powered configuration");near(API.config.getDouble("nanomachines.commandRange"),2,"Isolated command range");frequency=API.config.getDouble("power.tickFrequency");cost=API.config.getDouble("power.cost.nanomachineInput");require(cost>0&&frequency>=1,"Actual nanomachine tick cost must be positive");
            remember(position);require(level.setBlock(position,li.cil.oc.api.Items.get("rack").block().defaultBlockState(),3),"Cannot place actual rack");rackEntity=level.getBlockEntity(position);require(rackEntity instanceof Rack,"Actual terminal host is not a Rack");rack=(Rack)rackEntity;
            actor=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());Connection transport=new Connection(PacketFlow.SERVERBOUND){
                @Override public Channel channel(){return Run.this.channel;}
                @Override public boolean isConnected(){return Run.this.channel.isOpen();}
                @Override public SocketAddress getRemoteAddress(){return new InetSocketAddress("127.0.0.1",0);}
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet,PacketSendListener listener,boolean flush){packets.add(packet);}
            };
            channel.pipeline().addLast("packet_handler",transport);transport.channelActive(channel.pipeline().context(transport));NetworkRegistry.configureMockConnection(transport);actor.connection=new ServerGamePacketListenerImpl(level.getServer(),transport,actor,CommonListenerCookie.createInitial(profile,false));level.getServer().getPlayerList().placeNewPlayer(transport,actor,CommonListenerCookie.createInitial(profile,false));joined=true;
            require(level.getServer().getPlayerList().getPlayer(profile.getId())==actor,"Public login did not register the actual nano player");actor.setGameMode(GameType.SURVIVAL);actor.setNoGravity(true);nearRack();
            ItemStack server=item("server1"),terminalItem=item("terminalserver");require(rack.canPlaceItem(0,terminalItem)&&rack.canPlaceItem(1,server),"Actual Rack rejects terminal/server mountables");rack.setItem(0,terminalItem);rack.setItem(1,server);
        }
        void nearRack(){actor.teleportTo(position.getX()+.5,position.getY()+.5,position.getZ()+2.5);}
        void serverbound(String path,Consumer<RegistryFriendlyByteBuf> writer){var codec=NetworkRegistry.getCodec(id(path),ConnectionProtocol.PLAY,PacketFlow.SERVERBOUND);require(codec!=null,"Actual payload codec missing: "+path);RegistryFriendlyByteBuf bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());try{writer.accept(bytes);CustomPacketPayload payload=codec.decode(bytes);require(!bytes.isReadable(),"Actual payload codec left unread bytes: "+path);NetworkRegistry.handleModdedPayload(actor.connection,new ServerboundCustomPayloadPacket(payload));}finally{bytes.release();}}
        void connect(int slot){serverbound("rack_edit",bytes->{bytes.writeBlockPos(position);bytes.writeVarInt(slot);bytes.writeVarInt(0);bytes.writeVarInt(Direction.DOWN.get3DDataValue());bytes.writeVarInt(0);bytes.writeBoolean(false);});}
        @SuppressWarnings({"rawtypes","unchecked"}) RegistryFriendlyByteBuf encode(CustomPacketPayload payload){StreamCodec codec=NetworkRegistry.getCodec(payload.type().id(),ConnectionProtocol.PLAY,PacketFlow.CLIENTBOUND);require(codec!=null,"Actual outgoing codec missing: "+payload.type().id());RegistryFriendlyByteBuf bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());codec.encode(bytes,payload);return bytes;}
        Open takeOpen(){for(var packet:List.copyOf(packets))if(packet instanceof ClientboundCustomPayloadPacket custom&&custom.payload().type().id().equals(id("terminal_open"))){packets.remove(packet);RegistryFriendlyByteBuf bytes=encode(custom.payload());try{UUID token=bytes.readUUID();require(bytes.readResourceLocation().equals(level.dimension().location())&&bytes.readBlockPos().equals(position)&&bytes.readVarInt()==0,"Actual terminal snapshot identifies another rack");String address=bytes.readUtf(64);bytes.readVarLong();CompoundTag snapshot=bytes.readNbt();require(snapshot!=null&&!bytes.isReadable(),"Actual terminal snapshot is incomplete");return new Open(token,address,snapshot);}finally{bytes.release();}}return null;}
        boolean tookClose(UUID token){for(var packet:List.copyOf(packets))if(packet instanceof ClientboundCustomPayloadPacket custom&&custom.payload().type().id().equals(id("terminal_update"))){packets.remove(packet);RegistryFriendlyByteBuf bytes=encode(custom.payload());try{UUID actual=bytes.readUUID();bytes.readVarLong();boolean full=bytes.readBoolean();CompoundTag tag=bytes.readNbt();if(token.equals(actual)&&full&&tag!=null&&tag.getBoolean("closed"))return true;}finally{bytes.release();}}return false;}
        // Original wire enum ordinals: keyDown, keyUp, clipboard, touch, drag, drop, scroll, copyLine, close.
        void input(int action,int character,int code,double x,double y,int value,String text){serverbound("terminal_input",bytes->{bytes.writeUUID(opened.token);bytes.writeVarInt(action);bytes.writeVarInt(character);bytes.writeVarInt(code);bytes.writeDouble(x);bytes.writeDouble(y);bytes.writeVarInt(value);bytes.writeUtf(text,8192);});}
        void open(ItemStack stack,InteractionHand hand){actor.setItemInHand(hand,stack);require(stack.getItem().use(level,actor,hand).getResult()==InteractionResult.PASS,"Real terminal use no longer retains its original pass result");}
        void terminalSetup(){
            terminal=rack.getMountable(0);require(terminal instanceof Analyzable&&rack.getMountable(1) instanceof Server,"Actual terminal/server mountables are unavailable");blade=(Server)rack.getMountable(1);
            require(rackEntity instanceof MenuProvider&&actor.openMenu((MenuProvider)rackEntity,bytes->bytes.writeBlockPos(position)).isPresent(),"Cannot open the actual RackMenu");connect(0);connect(1);actor.closeContainer();require(terminal.node().network()==blade.node().network(),"Real rack edit did not connect terminal and server buses");
            Node[] nodes=((Analyzable)terminal).onAnalyze(actor,Direction.NORTH,.5F,.5F,.5F);for(Node node:nodes){if(node.host() instanceof TextBuffer buffer)screen=buffer;else if(node.host() instanceof li.cil.oc.api.internal.Keyboard)keyboard=node;}require(screen!=null&&keyboard!=null,"Real terminal analyzer did not expose its text buffer and keyboard");
            observer=new Observer();observer.node().connect(screen.node());require(screen.node() instanceof Connector,"Actual terminal buffer has no power connector");((Connector)screen.node()).changeBuffer(Double.POSITIVE_INFINITY);screen.set(0,0,"terminal fixture",false);
            ItemStack oldest=null;for(int i=0;i<5;i++){ItemStack key=item("terminal");actor.setItemInHand(InteractionHand.MAIN_HAND,key);require(terminal.onActivate(actor,InteractionHand.MAIN_HAND,key,.5F,.5F),"Actual terminal binding failed");if(i==0)oldest=key;paired=key;}
            require(terminal.getData().getList("keys",Tag.TAG_STRING).size()==4,"Actual terminal did not evict its oldest key at the original four-key limit");open(oldest,InteractionHand.MAIN_HAND);require(takeOpen()==null,"Evicted real terminal key opened a session");actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);open(paired,InteractionHand.OFF_HAND);require(takeOpen()==null,"Off-hand terminal opened a main-hand session");actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            open(paired,InteractionHand.MAIN_HAND);opened=takeOpen();require(opened!=null&&opened.address.equals(terminal.node().address()),"Actual paired terminal did not produce a real session snapshot");require(opened.data.getList("buffer",Tag.TAG_STRING).getString(0).startsWith("terminal fixture"),"Actual open snapshot lost real terminal text");
            input(0,'a',30,0,0,0,"");input(1,'a',30,0,0,0,"");input(2,0,0,0,0,0,"terminal clipboard");input(3,0,0,1,1,0,"");input(5,0,0,-1,-1,0,"");phase=1;phaseTick=ticks;
        }
        void terminalAuthenticated(){
            require(observer.saw(keyboard,"key_down")&&observer.saw(keyboard,"key_up")&&observer.saw(keyboard,"clipboard")&&observer.saw(screen.node(),"touch")&&observer.saw(screen.node(),"drop"),"Actual terminal payloads did not reach their real keyboard/buffer nodes");
            int count=observer.inputs.size();double range=API.config.getDoubleList("misc.maxWirelessRange").get(1);actor.teleportTo(position.getX()+.5+range,position.getY()+.5,position.getZ()+.5);input(0,'b',48,0,0,0,"");require(observer.inputs.size()==count&&tookClose(opened.token),"Actual terminal did not reject/close exactly at its range limit");nearRack();open(paired,InteractionHand.MAIN_HAND);opened=takeOpen();require(opened!=null,"Actual terminal did not reconnect after returning in range");
            input(0,'c',46,0,0,0,"");require(observer.saw(keyboard,"key_down"),"Actual terminal reconnect did not accept input");actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);input(0,'d',32,0,0,0,"");require(tookClose(opened.token),"Changing the actual held terminal did not close its session");
            results.add("PASS terminal.rack actual server/terminal mountables share the configured rack bus; real key eviction, main-hand authentication, text snapshots, keyboard/clipboard/touch/outside release and strict range closure pass through registered NeoForge codecs (local mock transport, no remote client claimed)");
            actor.getInventory().clearContent();ItemStack nanos=item("nanomachines");nanos.setCount(2);consume(nanos);phase=2;phaseTick=ticks;
        }
        void consume(ItemStack stack){actor.setItemInHand(InteractionHand.MAIN_HAND,stack);require(stack.getItem().use(level,actor,InteractionHand.MAIN_HAND).getResult().consumesAction()&&actor.isUsingItem(),"Actual material did not start the real player-use lifecycle");useTicks=0;}
        boolean consumed(){require(++useTicks<=32,"Actual material exceeded 32 public player lifecycle ticks; remaining="+actor.getUseItemRemainingTicks());if(actor.isUsingItem())return false;require(useTicks==32,"Actual material completed before 32 public player lifecycle ticks");return true;}
        void disableInputs(){for(int i=0;i<controller.getTotalInputCount();i++)controller.setInput(i,false);}
        int locateObserver(){int found=-1;disableInputs();for(int i=0;i<controller.getTotalInputCount();i++){require(controller.setInput(i,true),"Actual single input was rejected");for(Behavior effect:controller.getActiveBehaviors())if(effect==behavior)found=i;controller.setInput(i,false);controller.getActiveBehaviors();}require(found>=0,"Actual debug graph did not connect the registered addon behavior");return found;}
        void nanoInstalled()throws Exception{
            require(!actor.isUsingItem()&&actor.getMainHandItem().getCount()==1&&Nanomachines.hasController(actor),"Actual 32-tick item use did not consume/install nanomachines");controller=Nanomachines.getController(actor);require(controller!=null,"Installed real controller is unavailable");
            level.getServer().getPlayerList().op(profile);operator=true;require(level.getServer().getCommands().getDispatcher().execute("oc_debugNanomachines",actor.createCommandSourceStack())==1,"Actual player command did not create its supported debug graph");require(behavior!=null,"Actual provider was not consulted");observerIndex=locateObserver();inputCount=controller.getTotalInputCount();int maximum=controller.getMaxActiveInputs();require(inputCount>maximum,"Actual builtin/addon debug graph cannot exercise its input limit");
            for(int i=0;i<maximum;i++)require(controller.setInput(i,true),"Actual input quota rejected an allowed input");require(!controller.setInput(0,true)&&!controller.setInput(maximum,true),"Full real input quota accepted repeated/new enabling");disableInputs();controller.getActiveBehaviors();require(controller.setInput(observerIndex,true),"Actual observer input was rejected");controller.getActiveBehaviors();require(controller.getInputCount(behavior)==1,"Public input count lost the genuine behavior mapping");controller.changeBuffer(Double.POSITIVE_INFINITY);energyBefore=controller.getLocalBuffer();energyTime=level.getGameTime();oldUpdates=behavior.updates;phase=3;phaseTick=ticks;
        }
        boolean tick()throws Exception{
            require(++ticks<=500,"Terminal/nano proof exceeded 500 ticks; phase="+phase+", input="+observerIndex+", packets="+(radio==null?0:radio.received.size()));
            // This local transport is outside ServerConnectionListener's socket list.
            // Run the real player lifecycle normally driven by its packet listener;
            // this also delivers the genuine PlayerTickEvent used by nanomachines.
            actor.doTick();
            switch(phase){
                case 0->{if(ticks<3)return false;terminalSetup();}
                case 1->{if(ticks==phaseTick)return false;terminalAuthenticated();}
                case 2->{if(!consumed())return false;nanoInstalled();}
                case 3->{if(level.getGameTime()<=energyTime||level.getGameTime()%frequency!=0)return false;require(behavior.updates>oldUpdates,"Actual player ticker never updated its registered behavior");near(controller.getLocalBuffer(),energyBefore-cost*frequency*1.5,"Actual powered nanomachine interval");controller.changeBuffer(-controller.getLocalBuffer());oldDisabled=behavior.disabled;phase=4;}
                case 4->{if(behavior.outOfEnergy==0)return false;require(behavior.disabled==oldDisabled+1&&behavior.controllerVisible,"Real out-of-energy disable lost its controller or fired repeatedly");require(controller.setInput(observerIndex,false),"Actual powerless input change was rejected");oldDisabled=behavior.disabled;oldEnabled=behavior.enabled;phase=5;phaseTick=ticks;}
                case 5->{if(ticks-phaseTick<2)return false;require(behavior.disabled==oldDisabled&&behavior.enabled==oldEnabled,"Stable zero-energy player ticks eagerly reconciled behavior state");controller.setInput(observerIndex,true);controller.changeBuffer(Double.POSITIVE_INFINITY);oldUpdates=behavior.updates;phase=6;}
                case 6->{if(behavior.updates==oldUpdates)return false;require(behavior.enabled==oldEnabled+1,"Power restoration did not re-enable the genuine behavior once");results.add("PASS nanomachines.lifecycle actual 32-tick material installation, supported debug graph, addon-provider input mapping, original input limit, real player tick energy debit and out-of-energy/restoration callbacks retain the public controller");BlockPos pos=actor.blockPosition().east(3);remember(pos);level.setBlock(pos,Blocks.GLASS.defaultBlockState(),3);radio=new Radio(level,pos);require(actor.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>4,"Radio does not exercise the original squared range");radio.send("setResponsePort",24017);phase=7;}
                case 7->{Packet reply=radio.take("port");if(reply==null)return false;require(reply.port()==24017&&reply.data()[2] instanceof Number number&&number.intValue()==24017,"Actual wireless reply changed its response port");nanoAddress=reply.source();require(nanoAddress!=null&&!nanoAddress.isEmpty(),"Actual controller response has no source address");radio.send("getActiveEffects");phase=8;}
                case 8->{Packet reply=radio.take("effects");if(reply==null)return false;require(nanoAddress.equals(reply.source())&&text(reply.data()[2]).contains("ocfixture_observer"),"Real wireless query did not report the active addon behavior");providerWrites=0;radio.send("saveConfiguration");phase=9;}
                case 9->{Packet reply=radio.take("saved");if(reply==null)return false;require(Boolean.TRUE.equals(reply.data()[2])&&providerWrites>0,"Actual wireless save did not serialize the provider and consume a real configuration item");int configuredSlot=-1,count=0;for(int i=0;i<actor.getInventory().items.size();i++){ItemStack stack=actor.getInventory().getItem(i);if(stack.is(item("nanomachines").getItem())){count+=stack.getCount();if(data(stack).contains("oc:configuration"))configuredSlot=i;}}require(count==1&&configuredSlot>=0,"Real configuration save lost/duplicated its single remaining nanomachine item");configured=actor.getInventory().removeItem(configuredSlot,1);CompoundTag tag=data(configured);require(nanoAddress.equals(tag.getString("oc:uuid")),"Configured actual item lost the responding controller UUID");for(Tag raw:tag.getCompound("oc:configuration").getList("triggers",Tag.TAG_COMPOUND))require(!((CompoundTag)raw).getBoolean("isActive"),"Real configuration item preserved an enabled input");retiringBehavior=behavior;oldDisabled=behavior.disabled;providerReads=0;consume(configured);phase=10;phaseTick=ticks;}
                case 10->{if(!consumed())return false;require(!actor.isUsingItem()&&actor.getMainHandItem().isEmpty()&&Nanomachines.hasController(actor)&&retiringBehavior.controllerVisible&&retiringBehavior.disabled==oldDisabled+1,"Actual configured-item replacement failed or hid the old controller during disposal");Controller replacement=Nanomachines.getController(actor);require(replacement!=controller&&replacement.getTotalInputCount()==inputCount&&providerReads>0,"Configured real item did not restore its graph via the public BehaviorProvider reader");controller=replacement;for(int i=0;i<inputCount;i++)require(!controller.getInput(i),"Configured-item installation unexpectedly enabled a real input");observerIndex=locateObserver();controller.setInput(observerIndex,true);controller.getActiveBehaviors();controller.changeBuffer(Double.POSITIVE_INFINITY);radio.send("setResponsePort",24017);phase=11;}
                case 11->{Packet reply=radio.take("port");if(reply==null)return false;require(nanoAddress.equals(reply.source()),"Actual configured-item reinstall changed its wireless UUID");results.add("PASS nanomachines.protocol real SDK wireless packets from distance 3 reach commandRange=2 controllers using the original squared radius; actual port/effects/save replies preserve source UUID, consume one real item and restore the serialized addon graph with all inputs disabled");oldDisabled=behavior.disabled;consume(item("acid"));phase=12;phaseTick=ticks;}
                case 12->{if(!consumed())return false;require(!actor.isUsingItem()&&actor.getMainHandItem().isEmpty()&&!Nanomachines.hasController(actor)&&Nanomachines.getController(actor)==null,"Actual acid drinking did not consume/uninstall nanomachines");require(behavior.disabled==oldDisabled+1&&behavior.controllerVisible,"Actual acid teardown did not disable the genuine behavior while its controller remained available");int replies=radio.received.size();radio.send("getName");phase=13;phaseTick=ticks;oldUpdates=replies;}
                case 13->{if(ticks-phaseTick<(int)(API.config.getDouble("nanomachines.commandDelay")*20)+2)return false;require(radio.received.size()==oldUpdates,"Uninstalled actual controller remained a wireless receiver");results.add("PASS nanomachines.uninstall real 32-tick acid use consumes the material, invokes addon disable before detaching its controller and stops actual wireless replies; player cold-save/rendering/multiplayer acceptance remains with shared host/client coverage");return true;}
                default->throw new AssertionError("Unknown terminal/nano phase "+phase);
            }
            return false;
        }
        void fail(Throwable error){results.add("FAIL terminal.nanomachines phase="+phase+": "+error);LOGGER.error("TERMINAL_NANO failed at phase {}",phase,error);}
        void cleanup(){if(cleaned)return;cleaned=true;if(radio!=null)Network.leaveWirelessNetwork(radio);if(observer!=null)observer.node().remove();if(actor!=null){actor.stopUsingItem();Nanomachines.uninstallController(actor);actor.getInventory().clearContent();if(operator)level.getServer().getPlayerList().deop(profile);if(joined)level.getServer().getPlayerList().remove(actor);}if(rack!=null)rack.clearContent();var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries){if(level.getBlockEntity(entry.getKey()) instanceof Container container)container.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}channel.finishAndReleaseAll();packets.clear();for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);}
    }
}
