package ocfixture;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Colored;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Packet;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real cable dyeing, vanilla-powered splitter topology and wired modem delivery using only public SDK hosts. */
public final class NetworkDevicesContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-network-devices-contract");
    private static Run active;
    private NetworkDevicesContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){
        require(active==null,"A network device contract is already active");Run run=new Run(level,position.immutable(),results);active=run;
        try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){
        Run run=active;if(run==null)return;
        try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}
        active=null;run.cleanup();
    }
    public static void close(){Run run=active;active=null;if(run!=null){run.results.add("FAIL network.devices: server stopped before the world contract completed");run.cleanup();}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object first(Object[] values,String operation){require(values!=null&&values.length==1,operation+" returned no single result");return values[0];}
    private record Signal(Node source,Object[] values){}
    private static final class Probe extends AbstractManagedEnvironment {
        final List<Packet> packets=new ArrayList<>();final List<Signal> signals=new ArrayList<>();
        Probe(){setNode(Network.newNode(this,Visibility.Network).create());}
        @Override public void onMessage(Message message){
            if("network.message".equals(message.name())&&message.data().length==1&&message.data()[0] instanceof Packet packet)packets.add(packet);
            if("computer.signal".equals(message.name()))signals.add(new Signal(message.source(),message.data().clone()));
        }
        void clear(){packets.clear();signals.clear();}
    }
    private static final class Run {
        final ServerLevel level;final BlockPos position,splitterPos,powerPos,relayPos;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        final List<Case> cases=new ArrayList<>();final List<ManagedEnvironment> cards=new ArrayList<>();
        Case left,right,splitLeft,splitRight,relayCase;SidedEnvironment splitter;Component splitterComponent;Container relay;FakePlayer player;Probe probe;
        int ticks,phase;boolean cleaned;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;splitterPos=position.south(8);powerPos=splitterPos.above();relayPos=position.east(8);this.results=results;}
        void remember(BlockPos pos){
            if(restored.containsKey(pos))return;
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
            require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Network fixture refuses an occupied cell "+pos);restored.put(pos.immutable(),level.getBlockState(pos));
        }
        void place(String name,BlockPos pos){
            remember(pos);var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null&&descriptor.block()!=null,"Missing real network block "+name);
            require(level.setBlock(pos,descriptor.block().defaultBlockState(),3),"Could not place actual "+name);
        }
        Case placedCase(BlockPos pos){place("case3",pos);require(level.getBlockEntity(pos) instanceof Case,"Actual network host is not a public Case");Case host=(Case)level.getBlockEntity(pos);cases.add(host);return host;}
        Node node(BlockPos pos){require(level.getBlockEntity(pos) instanceof Environment,"Network block has no public Environment at "+pos);return ((Environment)level.getBlockEntity(pos)).node();}
        void start(){
            left=placedCase(position.west());place("cable",position);place("cable",position.east());place("cable",position.east(2));right=placedCase(position.east(3));
            splitLeft=placedCase(splitterPos.west());place("netsplitter",splitterPos);splitRight=placedCase(splitterPos.east());remember(powerPos);
            require(level.getBlockEntity(splitterPos) instanceof SidedEnvironment,"Actual splitter has no published sided environment");splitter=(SidedEnvironment)level.getBlockEntity(splitterPos);
            require(node(splitterPos) instanceof Component,"Actual splitter has no public component");splitterComponent=(Component)node(splitterPos);
            relayCase=placedCase(relayPos.west());place("relay",relayPos);
            require(level.getBlockEntity(relayPos) instanceof Container&&level.getBlockEntity(relayPos) instanceof SidedEnvironment,"Actual relay has no public inventory/sided environment");relay=(Container)level.getBlockEntity(relayPos);
            player=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes("oc-network-device-dye-operator".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"OCNetDevices"));
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);player.setPos(position.getX()+.5,position.getY(),position.getZ()-2);
        }
        void sameNetwork(Case a,Case b,boolean expected,String message){
            require(a.machine().node().network()!=null&&b.machine().node().network()!=null,"Real Case network has not naturally initialized");
            require((a.machine().node().network()==b.machine().node().network())==expected,message);
        }
        void dye(BlockPos pos,Item dye){
            ItemStack stack=new ItemStack(dye,2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
            require(player.gameMode.useItemOn(player,level,stack,InteractionHand.MAIN_HAND,hit).consumesAction(),"Actual dye right-click was not accepted");
            require(stack.getCount()==1,"Survival cable dyeing did not consume exactly one dye");
        }
        void cables(){
            sameNetwork(left,right,true,"Three default cables did not naturally connect the actual Cases");
            dye(position,Items.RED_DYE);dye(position.east(),Items.RED_DYE);dye(position.east(),Items.RED_DYE);
            dye(position.east(2),Items.BLUE_DYE);sameNetwork(left,right,false,"Different real cable colors did not split the actual Case graphs");
            require(level.getBlockEntity(position) instanceof Colored&&level.getBlockEntity(position.east(2)) instanceof Colored,"Real cables expose no public color interface");
            require(((Colored)level.getBlockEntity(position)).getColor()!=((Colored)level.getBlockEntity(position.east(2))).getColor(),"Different dyes produced the same cable color");
            dye(position.east(),Items.LIGHT_GRAY_DYE);sameNetwork(left,right,true,"Restoring the middle cable's wildcard color did not reconnect both colored networks");
            List<ItemStack> drops=Block.getDrops(level.getBlockState(position.east()),level,position.east(),level.getBlockEntity(position.east()));
            require(drops.size()==1&&drops.getFirst().is(li.cil.oc.api.Items.get("cable").createItemStack(1).getItem()),"Actual cable drop path returned no normal cable");
            CompoundTag data=drops.getFirst().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            require(!data.contains("oc:color")&&!data.getCompound("display").contains("color"),"Default wildcard cable drop retained a color tag");
            results.add("PASS cable.world three actual cables connect real Cases; survival dyeing consumes one item including repeated color, unequal colors isolate graphs, wildcard dye reconnects and actual default drops contain no color tag");
        }
        Component card(Case host,CompoundTag saved){
            ItemStack stack=li.cil.oc.api.Items.get("lancard").createItemStack(1);var driver=Driver.driverFor(stack,host.getClass());require(driver!=null,"Real Case-context driver rejected a wired network card");
            ManagedEnvironment environment=driver.createEnvironment(stack,host);require(environment!=null&&environment.node() instanceof Component,"Actual card driver created no component");cards.add(environment);
            environment.load(saved,level.registryAccess());host.machine().node().connect(environment.node());return (Component)environment.node();
        }
        void wired()throws Exception{
            int maximum=API.config.getIntList("misc.maxOpenPorts").getFirst();require(maximum>0&&maximum<1024,"Wired saved-port proof requires the normal finite port limit");
            CompoundTag saved=new CompoundTag();int[] ports=new int[maximum+1];for(int i=0;i<ports.length;i++)ports[i]=i+1;saved.putIntArray("openPorts",ports);
            Component sender=card(left,new CompoundTag()),receiver=card(right,saved);
            for(int port:ports)require(Boolean.TRUE.equals(first(receiver.invoke("isOpen",right.machine(),port),"isOpen")),"Loading saved ports discarded a port above the current opening limit");
            receiver.invoke("close",right.machine());require(Boolean.TRUE.equals(first(receiver.invoke("open",right.machine(),321),"open")),"Actual receiver could not open its modem port");
            probe=new Probe();right.machine().node().connect(probe.node());byte[] bytes={0,1,(byte)255};
            sender.invoke("send",left.machine(),receiver.address(),321,bytes,true);require(probe.packets.size()==1,"Public packet probe did not observe exactly one actual wired packet");
            Packet packet=probe.packets.getFirst();require(packet.port()==321&&sender.address().equals(packet.source())&&receiver.address().equals(packet.destination())&&packet.data()[0] instanceof byte[] payload&&Arrays.equals(payload,bytes),"Actual wired packet changed its addressing, port or binary payload");
            require(probe.signals.size()==1&&probe.signals.getFirst().source()==receiver,"Receiver did not emit exactly one real computer.signal");Object[] signal=probe.signals.getFirst().values();
            require(signal.length==6&&"modem_message".equals(signal[0])&&sender.address().equals(signal[1])&&((Number)signal[2]).intValue()==321&&((Number)signal[3]).doubleValue()==0&&signal[4] instanceof byte[] payload&&Arrays.equals(payload,bytes)&&Boolean.TRUE.equals(signal[5]),"Actual modem signal changed its source, port, wired distance or binary arguments");
            probe.clear();receiver.invoke("close",right.machine(),321);sender.invoke("send",left.machine(),receiver.address(),321,bytes);
            require(probe.packets.size()==1&&probe.signals.isEmpty(),"Closed receiver port still emitted a modem signal");
            receiver.invoke("open",right.machine(),321);receiver.remove();require(receiver.network()==null&&!Boolean.TRUE.equals(first(receiver.invoke("isOpen",right.machine(),321),"isOpen after removal")),"Removing actual card node retained its open port");
            results.add("PASS modem.wired real Case-context cards preserve saved ports above a reduced limit, deliver an addressed binary Packet and matching modem signal, reject closed-port delivery and clear ports on node removal");
        }
        void wireless()throws Exception{
            sameNetwork(splitLeft,splitRight,false,"Wireless proof requires two independent actual Case graphs");
            double distance=new Vec3(splitLeft.xPosition(),splitLeft.yPosition(),splitLeft.zPosition())
                    .distanceTo(new Vec3(splitRight.xPosition(),splitRight.yPosition(),splitRight.zPosition()));
            require(distance==2,"The bounded wireless fixture no longer places its actual Cases two blocks apart");
            probe.node().remove();splitRight.machine().node().connect(probe.node());
            for(int tier=1;tier<=2;tier++){
                Component[] modems=new Component[2];
                for(int index=0;index<2;index++){
                    Case host=index==0?splitLeft:splitRight;
                    ItemStack stack=li.cil.oc.api.Items.get("wlancard"+tier).createItemStack(1);
                    var driver=Driver.driverFor(stack,host.getClass());require(driver!=null,"Real Case rejected wireless tier "+tier);
                    ManagedEnvironment environment=driver.createEnvironment(stack,host);require(environment!=null,"Real wireless driver created no environment");cards.add(environment);
                    environment.load(driver.dataTag(stack),level.registryAccess());host.machine().node().connect(environment.node());
                    require(environment.node() instanceof Component&&environment.node() instanceof Connector,"Actual wireless card has no component/energy connector");
                    modems[index]=(Component)environment.node();((Connector)environment.node()).changeBuffer(Double.POSITIVE_INFINITY);
                }
                Component sender=modems[0],receiver=modems[1];Connector power=(Connector)sender;
                double strength=((Number)first(sender.invoke("setStrength",splitLeft.machine(),Double.POSITIVE_INFINITY),"wireless.setStrength")).doubleValue();
                require(Double.isFinite(strength)&&strength>=distance,"Actual wireless tier "+tier+" could not cover the two-block fixture");
                require(Boolean.TRUE.equals(first(sender.invoke("isWireless",splitLeft.machine()),"isWireless"))
                        &&Boolean.valueOf(tier==2).equals(first(sender.invoke("isWired",splitLeft.machine()),"isWired")),"Actual wireless tier lost its original networking capabilities");
                int port=8000+tier;require(Boolean.TRUE.equals(first(receiver.invoke("open",splitRight.machine(),port),"wireless.open")),"Actual wireless receiver refused its port");
                double cost=API.config.getBoolean("power.ignorePower")?0:strength*Math.max(0,API.config.getDoubleList("power.cost.wirelessCostPerRange").get(tier-1));
                double before=power.globalBuffer();require(before>=cost,"Actual Case energy capacity cannot cover one configured wireless send");
                byte[] bytes={0,1,(byte)128,(byte)255};probe.clear();
                require(Boolean.TRUE.equals(first(sender.invoke("send",splitLeft.machine(),receiver.address(),port,bytes),"wireless.send")),"Actual wireless send failed");
                require(Math.abs(before-power.globalBuffer()-cost)<1e-8,"Actual wireless send did not debit its configured strength/range energy cost");
                require(probe.packets.isEmpty()&&probe.signals.size()==1&&probe.signals.getFirst().source()==receiver,"Independent receiver Case did not receive exactly one wireless modem signal");
                Object[] signal=probe.signals.getFirst().values();
                require(signal.length==5&&"modem_message".equals(signal[0])&&sender.address().equals(signal[1])
                        &&((Number)signal[2]).intValue()==port&&((Number)signal[3]).doubleValue()==distance
                        &&signal[4] instanceof byte[] payload&&Arrays.equals(payload,bytes),"Actual wireless modem signal changed its address, port, distance or binary payload");
                sender.remove();receiver.remove();require(sender.network()==null&&receiver.network()==null,"Removing actual wireless cards retained their node networks");
                results.add("PASS modem.wireless.tier"+tier+" real Driver cards on independent Cases transmit addressed binary data at distance two, report the real wireless modem signal and debit configured energy before node cleanup");
            }
            probe.node().remove();
        }
        void openSplitter()throws Exception{
            splitterComponent.invoke("open",splitLeft.machine(),Direction.WEST.get3DDataValue());splitterComponent.invoke("open",splitLeft.machine(),Direction.EAST.get3DDataValue());
            sameNetwork(splitLeft,splitRight,true,"Opening both actual splitter faces did not join its Case graphs");
            require(splitter.sidedNode(Direction.WEST)!=null&&splitter.sidedNode(Direction.EAST)!=null,"Opened splitter faces expose no nodes");
        }
        double relayStrength(Component component)throws Exception{
            Object value=first(component.invoke("getStrength",relayCase.machine()),"relay.getStrength");require(value instanceof Number,"Actual relay strength is not numeric");return ((Number)value).doubleValue();
        }
        void relay()throws Exception{
            var entity=level.getBlockEntity(relayPos);require(entity instanceof EnvironmentHost,"Real relay is not a public component host");EnvironmentHost host=(EnvironmentHost)entity;
            Node plug=((SidedEnvironment)entity).sidedNode(Direction.WEST);require(plug!=null&&plug.network()==relayCase.machine().node().network(),"Actual relay plug did not naturally join its neighboring Case");
            Component component=null;
            for(Node node:relayCase.machine().node().network().nodes())if(node instanceof Component candidate&&"relay".equals(candidate.name())){require(component==null,"Neighbor Case unexpectedly sees multiple relay components");component=candidate;}
            require(component!=null,"Neighbor Case cannot access the actual relay component");
            ItemStack wireless=li.cil.oc.api.Items.get("wlancard1").createItemStack(1);var driver=Driver.driverFor(wireless,host.getClass());require(driver!=null&&relay.canPlaceItem(3,wireless),"Relay rejected its actual wireless card");
            CompoundTag initial=driver.dataTag(wireless).copy();initial.putDouble("strength",5);initial.putString("wakeMessage","relay-hardware-contract");driver.saveDataTag(wireless,initial);relay.setItem(3,wireless);
            require(((Number)first(component.invoke("setStrength",relayCase.machine(),7),"relay.setStrength")).doubleValue()==7,"Actual relay refused strength seven");
            ItemStack cpu=li.cil.oc.api.Items.get("cpu1").createItemStack(1);require(relay.canPlaceItem(0,cpu),"Relay rejected its actual CPU slot");relay.setItem(0,cpu);require(relayStrength(component)==7,"Inserting CPU reset the configured relay strength");
            relay.setItem(0,li.cil.oc.api.Items.get("cpu2").createItemStack(1));require(relayStrength(component)==7,"Replacing CPU reset the configured relay strength");
            ItemStack used=relay.removeItem(3,1);require(!used.isEmpty()&&relayStrength(component)==7,"Removing a wireless card reset the relay's own configured strength");
            CompoundTag saved=driver.dataTag(used);require(saved.getDouble("strength")==5&&"relay-hardware-contract".equals(saved.getString("wakeMessage")),"Removing the actual wireless card lost its independent driver state");
            ItemStack replacement=li.cil.oc.api.Items.get("wlancard2").createItemStack(1);require(relay.canPlaceItem(3,replacement),"Relay rejected its actual replacement wireless tier");relay.setItem(3,replacement);require(relayStrength(component)==7,"Replacing the wireless card changed the relay's own configured strength");
            relay.removeItem(3,1);relay.setItem(3,used);require(relayStrength(component)==7,"Reinserting the used wireless card reset configured relay strength");
            saved=driver.dataTag(relay.getItem(3));require(saved.getDouble("strength")==5&&"relay-hardware-contract".equals(saved.getString("wakeMessage")),"Remounting the used wireless card changed its independent persisted state");
            require(entity.saveWithoutMetadata(level.registryAccess()).getDouble("oc:strength")==7,"Saving the actual relay changed its configured strength");
            relay.clearContent();require(level.setBlock(relayPos,Blocks.AIR.defaultBlockState(),3),"Could not remove fixture relay");require(component.network()==null&&plug.network()==null,"Actual relay removal retained a component or plug network");
            results.add("PASS relay.hardware real Case-context strength seven survives CPU and wireless-card insertion/replacement/removal; the used card retains its independent strength/wake state across remount, and block save/removal completes with detached plug/component nodes");
        }
        boolean tick()throws Exception{
            if(++ticks>100)throw new AssertionError("Network devices contract exceeded 100 actual world ticks: phase="+phase+", splitter inputs="+Arrays.toString(level.getBlockEntity(splitterPos).saveWithoutMetadata(level.registryAccess()).getIntArray("oc:rs.input")));
            if(phase==0){if(ticks<3)return false;cables();wired();relay();wireless();openSplitter();phase=1;return false;}
            CompoundTag state=level.getBlockEntity(splitterPos).saveWithoutMetadata(level.registryAccess());int[] inputs=state.getIntArray("oc:rs.input");
            require(inputs.length==6,"Actual splitter save did not expose its original six redstone input samples");
            if(phase==1){
                // The original first input sample establishes its cache without emitting a change event.
                if(Arrays.stream(inputs).anyMatch(value->value<0))return false;
                require(Arrays.stream(inputs).allMatch(value->value==0)&&!state.getBoolean("oc:isInverted"),"Actual splitter did not establish an unpowered input baseline");
                require(level.setBlock(powerPos,Blocks.REDSTONE_BLOCK.defaultBlockState(),3),"Could not power the actual splitter using a real redstone block");phase=2;return false;
            }
            if(phase==2){
                if(inputs[Direction.UP.get3DDataValue()]!=15)return false;
                require(state.getBoolean("oc:isInverted"),"Actual sampled redstone transition to fifteen did not invert the splitter");
                require(splitter.sidedNode(Direction.WEST)==null&&splitter.sidedNode(Direction.EAST)==null,"Real ordinary redstone did not invert both open splitter faces");
                sameNetwork(splitLeft,splitRight,false,"Powered splitter retained a data path between its closed faces");
                require(level.setBlock(powerPos,Blocks.AIR.defaultBlockState(),3),"Could not remove fixture redstone source");phase=3;return false;
            }
            if(!Arrays.stream(inputs).allMatch(value->value==0))return false;
            require(!state.getBoolean("oc:isInverted"),"Actual sampled redstone transition to zero retained splitter inversion");
            require(splitter.sidedNode(Direction.WEST)!=null&&splitter.sidedNode(Direction.EAST)!=null,"Removing real redstone did not restore the configured splitter faces");
            sameNetwork(splitLeft,splitRight,true,"Unpowered splitter did not restore the real Case graph");
            results.add("PASS splitter.redstone real saved input cache establishes six zero samples, an actual redstone block produces sampled fifteen and inverted/isolated Case graphs, and removing it restores six zero samples and the configured connected graph");return true;
        }
        void fail(Throwable error){results.add("FAIL network.devices: "+error);LOGGER.error("NETWORK_DEVICES_CONTRACT FAIL",error);}
        void cleanup(){
            if(cleaned)return;cleaned=true;
            if(relay!=null)relay.clearContent();
            for(ManagedEnvironment card:cards)card.node().remove();if(probe!=null)probe.node().remove();for(Case host:cases)host.machine().stop();
            if(player!=null)player.getInventory().clearContent();var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);
            for(var entry:entries)level.setBlock(entry.getKey(),entry.getValue(),3);
            for(long packed:forced){ChunkPos chunk=new ChunkPos(packed);level.setChunkForced(chunk.x,chunk.z,false);}forced.clear();
        }
    }
}
