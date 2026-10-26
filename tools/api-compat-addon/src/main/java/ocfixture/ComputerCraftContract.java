package ocfixture;

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.MountConstants;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.media.IMedia;
import dan200.computercraft.api.media.MediaCapability;
import dan200.computercraft.api.peripheral.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.SeekableByteChannel;
import java.util.*;
import dan200.computercraft.api.ComputerCraftAPI;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Context;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** External SDK consumer: public CC APIs exercise the actual placed blocks and media. */
public final class ComputerCraftContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-api-computercraft");
    private static final List<String> RESULTS=new ArrayList<>();
    private static Run current;
    private ComputerCraftContract(){}
    public static void register(){}
    private static boolean configured(){return API.config.hasPath("integration.computerCraft.enabled")&&API.config.getBoolean("integration.computerCraft.enabled")&&(!API.config.hasPath("integration.modBlacklist")||API.config.getStringList("integration.modBlacklist").stream().noneMatch(name->name.trim().equalsIgnoreCase("computercraft")));}
    public static String run(ServerLevel level)throws Exception{return start(level,false);}
    public static String runDisabled(ServerLevel level)throws Exception{return start(level,true);}
    private static String start(ServerLevel level,boolean disabled)throws Exception{
        require(current==null,"A ComputerCraft contract is already running");RESULTS.clear();
        require(configured()!=disabled,disabled?"Disabled CC contract requires a false/missing flag or a mod blacklist entry":"Enabled CC contract requires an explicit true flag and no CC mod blacklist entry");
        if(!Boolean.getBoolean("ocfixture.worldFixture")){RESULTS.add("SKIP ComputerCraft: temporary world fixtures are disabled");return "Skipped ComputerCraft outside the isolated fixture";}
        Run run=new Run(level,disabled);current=run;
        try{run.start();}catch(Exception|Error error){current=null;run.cleanup();throw error;}
        return disabled?"Started CC-present explicit-disabled registration contract":"Started real Relay, Adapter/computer and media ComputerCraft data-flow contracts";
    }
    public static void tick(){
        Run run=current;if(run==null)return;
        try{if(!run.tick())return;LOGGER.info(run.disabled?"API_COMPUTERCRAFT PASS installed CC remains isolated while compatibility is disabled":"API_COMPUTERCRAFT PASS real relay packet round-trip, Adapter computer calls and disk mount I/O");}
        catch(Throwable error){RESULTS.add("FAIL ComputerCraft: "+error);LOGGER.error("API_COMPUTERCRAFT FAIL",error);}
        current=null;run.cleanup();
    }
    public static boolean isRunning(){return current!=null;}
    public static List<String> results(){return List.copyOf(RESULTS);}
    public static void close(){Run run=current;current=null;if(run!=null)run.cleanup();}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static Block block(String namespace,String id){ResourceLocation key=ResourceLocation.fromNamespaceAndPath(namespace,id);require(BuiltInRegistries.BLOCK.containsKey(key),"Missing real fixture block "+key);return BuiltInRegistries.BLOCK.get(key);}
    private static boolean payload(Object value,String expected){return value instanceof byte[] bytes?Arrays.equals(bytes,expected.getBytes(StandardCharsets.UTF_8)):expected.equals(value);}

    private static final class Run {
        final ServerLevel level;
        final boolean disabled;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();
        final List<Node> ownNodes=new ArrayList<>();
        Probe wire,adapterComputer;
        BlockEntity adapter;
        IDynamicPeripheral relay;
        Recorder sender,receiver;
        BlockPos computerPos,redstonePos;
        Component ccComputer;
        int ticks,phase;
        Run(ServerLevel level,boolean disabled){this.level=level;this.disabled=disabled;}
        private void place(BlockPos pos,BlockState state){require(level.isEmptyBlock(pos),"ComputerCraft fixture will not overwrite nonempty cell "+pos);restored.put(pos,level.getBlockState(pos));level.setBlock(pos,state,3);}
        void start()throws Exception{
            BlockPos spawn=level.getSharedSpawnPos();int y=Math.min(level.getMaxBuildHeight()-8,Math.max(level.getMinBuildHeight()+12,spawn.getY()+28));
            BlockPos relayPos=new BlockPos(spawn.getX()+14,y,spawn.getZ()+14);computerPos=relayPos.east(8);BlockPos adapterPos=computerPos.west();
            for(BlockPos pos:List.of(relayPos,computerPos,adapterPos))level.getChunkAt(pos);
            place(relayPos,block("opencomputers","relay").defaultBlockState());
            place(computerPos,block("computercraft","computer_normal").defaultBlockState());
            place(adapterPos,block("opencomputers","adapter").defaultBlockState());
            adapter=level.getBlockEntity(adapterPos);require(adapter instanceof Environment,"Placed Adapter has no public OC Environment");Network.joinOrCreateNetwork(adapter);
            adapterComputer=new Probe(level,adapterPos);ownNodes.add(adapterComputer.node());Network.joinNewNetwork(adapterComputer.node());adapterComputer.node().connect(((Environment)adapter).node());((Connector)adapterComputer.node()).changeBuffer(100000);
            IPeripheral actual=level.getCapability(PeripheralCapability.get(),relayPos,Direction.WEST);
            if(disabled){
                require(actual==null,"Disabled CC compatibility still registered the Relay peripheral");
                ItemStack disk=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("computercraft","disk")));
                require(!disk.isEmpty()&&disk.getCapability(MediaCapability.get())!=null,"CC is not actually installed with its real media capability");
                require(Driver.driverFor(disk,Probe.class)==null,"Disabled CC compatibility still registered its media driver");
                SentinelEnvironment sentinel=new SentinelEnvironment();ownNodes.add(sentinel.node());Network.joinNewNetwork(sentinel.node());
                Object[] converted=((Component)sentinel.node()).invoke("sentinel",adapterComputer);
                require(converted!=null&&converted.length==1&&!(converted[0] instanceof Map<?,?> map&&map.get("value") instanceof li.cil.oc.api.machine.Value),"Disabled CC compatibility still registered its Lua object converter");
                redstonePos=relayPos.south(4);level.getChunkAt(redstonePos);place(redstonePos,block("opencomputers","redstone").defaultBlockState());
                return;
            }
            require(actual instanceof IDynamicPeripheral,"Placed relay did not expose a real dynamic CC modem peripheral");relay=(IDynamicPeripheral)actual;
            require("modem".equals(relay.getType()),"Relay peripheral has the wrong type");
            sender=new Recorder(9301,"fixture_left",relay);receiver=new Recorder(9302,"fixture_right",relay);relay.attach(sender);relay.attach(receiver);
            BlockEntity entity=level.getBlockEntity(relayPos);require(entity instanceof SidedEnvironment,"Relay has no public sided network interface");
            wire=new Probe(level,relayPos.west());ownNodes.add(wire.node());Network.joinNewNetwork(wire.node());wire.node().connect(((SidedEnvironment)entity).sidedNode(Direction.WEST));((Connector)wire.node()).changeBuffer(100000);
            Object[] opened=call(receiver,"open",43123);require(opened!=null&&Boolean.TRUE.equals(opened[0]),"CC receiver channel did not open");call(sender,"open",43124);
            Object[] transmitted=call(sender,"transmit",43123,43124,"cc-to-oc");require(transmitted!=null&&Boolean.TRUE.equals(transmitted[0]),"Real relay rejected CC transmission");
            require(receiver.hasEvent(43123,43124,"cc-to-oc"),"CC relay event did not preserve channel, reply channel and payload");
        }
        private Object[] call(Recorder computer,String method,Object...args)throws LuaException{
            int index=Arrays.asList(relay.getMethodNames()).indexOf(method);require(index>=0,"Relay does not expose CC method "+method);
            ILuaContext context=new ILuaContext(){@Override public long issueMainThreadTask(LuaTask task)throws LuaException{throw new LuaException("Unexpected asynchronous task in main-thread fixture");}@Override public MethodResult executeMainThreadTask(LuaTask task)throws LuaException{require(level.getServer().isSameThread(),"CC contract left the server thread");return MethodResult.of(task.execute());}};
            MethodResult result=relay.callMethod(computer,context,index,new ObjectArguments(args));require(result.getCallback()==null,"Inline CC modem operation unexpectedly yielded");return result.getResult();
        }
        boolean tick()throws Exception{
            require(++ticks<=160,"ComputerCraft contract did not finish its real data flow within 160 ticks");
            if(disabled){
                Node node=((Environment)adapter).node();
                if(node!=null&&node.network()!=null)for(Node candidate:node.reachableNodes())if(candidate instanceof Component component)require(!(component.methods().contains("getID")&&component.methods().contains("isOn")&&component.methods().contains("turnOn")),"Disabled CC compatibility still adapts the adjacent real CC computer");
                require(ComputerCraftAPI.getBundledRedstoneOutput(level,redstonePos,Direction.NORTH)==-1,"Disabled CC compatibility still registered OC bundled-redstone output with CC");
                if(ticks<30)return false;
                RESULTS.add("PASS ComputerCraft disabled: installed CC registers no Relay peripheral, Adapter computer bridge, media driver, Lua converter or bundled-redstone bridge");return true;
            }
            if(phase==0){
                Packet transmitted=wire.packets.stream().filter(packet->packet.port()==43123).findFirst().orElse(null);if(transmitted==null)return false;
                Object[] data=transmitted.data();require(data.length==2&&data[0] instanceof Number reply&&reply.intValue()==43124&&payload(data[1],"cc-to-oc"),"Actual wired Relay packet did not put replyPort before the unchanged CC payload");
                wire.node().sendToReachable("network.message",Network.newPacket(wire.node().address(),null,43124,new Object[]{43123.0,"oc-to-cc"}));
                require(sender.hasEvent(43124,43123,"oc-to-cc"),"Actual OC network packet did not return a correct CC modem_message event");
                RESULTS.add("PASS ComputerCraft: real Relay attach/open/transmit, queued wired packet and modem_message return preserve replyPort/payload");phase=1;
            }
            if(phase==1){
                Component computer=null;Node adapterNode=((Environment)adapter).node();
                if(adapterNode!=null&&adapterNode.network()!=null)for(Node candidate:adapterNode.reachableNodes())if(candidate instanceof Component component&&component.methods().contains("getID")&&component.methods().contains("isOn")&&component.methods().contains("turnOn")){computer=component;break;}
                if(computer==null)return false;
                computer.invoke("turnOn",adapterComputer);ccComputer=computer;phase=2;return false;
            }
            if(phase==2){
                Object[] on=ccComputer.invoke("isOn",adapterComputer);if(on==null||!Boolean.TRUE.equals(on[0]))return false;
                Object[] id=ccComputer.invoke("getID",adapterComputer);require(id!=null&&id[0] instanceof Number number&&number.intValue()>=0,"Adapter did not return a real CC computer ID");
                require(level.getCapability(PeripheralCapability.get(),computerPos,Direction.WEST)!=null,"Physical CC computer peripheral disappeared during Adapter invocation");
                RESULTS.add("PASS ComputerCraft: real Adapter discovers a placed CC computer and executes turnOn/isOn/getID through SDK components");
                media();return true;
            }
            return false;
        }
        private void media()throws Exception{
            ResourceLocation key=ResourceLocation.fromNamespaceAndPath("computercraft","disk");require(BuiltInRegistries.ITEM.containsKey(key),"Actual CC disk item is unavailable");ItemStack disk=new ItemStack(BuiltInRegistries.ITEM.get(key));
            IMedia media=disk.getCapability(MediaCapability.get());require(media!=null,"Actual CC disk has no public media capability");var driver=Driver.driverFor(disk,Probe.class);require(driver!=null&&li.cil.oc.api.driver.item.Slot.Floppy.equals(driver.slot(disk)),"Public SDK did not select the CC media floppy driver");
            ManagedEnvironment filesystem=driver.createEnvironment(disk,adapterComputer);require(filesystem!=null&&filesystem.node() instanceof Component,"CC media did not mount as a real OC filesystem component");ownNodes.add(filesystem.node());adapterComputer.node().connect(filesystem.node());Component component=(Component)filesystem.node();String address=component.address();
            Mount actualMount=media.createDataMount(disk,level);require(actualMount instanceof WritableMount,"Actual CC data disk did not produce a writable mount");WritableMount writable=(WritableMount)actualMount;
            String outgoing="oc_fixture_to_cc.txt",incoming="cc_fixture_to_oc.txt";byte[] fromOC="OC writes real CC media\n".getBytes(StandardCharsets.UTF_8),fromCC="CC writes real OC media\n".getBytes(StandardCharsets.UTF_8);
            try{
                Object handle=component.invoke("open",adapterComputer,outgoing,"w")[0];component.invoke("write",adapterComputer,handle,fromOC);component.invoke("close",adapterComputer,handle);
                ByteBuffer read=ByteBuffer.allocate(fromOC.length+16);try(SeekableByteChannel channel=actualMount.openForRead(outgoing)){while(read.hasRemaining()&&channel.read(read)>0){}}
                require(Arrays.equals(fromOC,Arrays.copyOf(read.array(),read.position())),"SDK filesystem write did not reach the real CC mount");
                try(SeekableByteChannel channel=writable.openFile(incoming,MountConstants.WRITE_OPTIONS)){ByteBuffer bytes=ByteBuffer.wrap(fromCC);while(bytes.hasRemaining())channel.write(bytes);}
                handle=component.invoke("open",adapterComputer,incoming,"r")[0];Object[] value=component.invoke("read",adapterComputer,handle,fromCC.length+16);component.invoke("close",adapterComputer,handle);
                require(value!=null&&payload(value[0],new String(fromCC,StandardCharsets.UTF_8)),"Real CC mount write did not reach SDK filesystem read");
                component.invoke("setLabel",adapterComputer,"oc-fixture-cc");require("oc-fixture-cc".equals(media.getLabel(level.registryAccess(),disk)),"SDK label mutation did not update the actual CC item");
                CompoundTag saved=driver.dataTag(disk);filesystem.save(saved,level.registryAccess());driver.saveDataTag(disk,saved);filesystem.node().remove();
                ManagedEnvironment remounted=driver.createEnvironment(disk,adapterComputer);require(remounted!=null&&remounted.node()!=null,"CC media did not remount");ownNodes.add(remounted.node());require(Objects.equals(address,remounted.node().address()),"CC disk filesystem address changed after save/remount");
                RESULTS.add("PASS ComputerCraft: public media mount transfers real bytes both ways, updates the actual disk label and preserves node identity on remount");
            }finally{if(writable.exists(outgoing))writable.delete(outgoing);if(writable.exists(incoming))writable.delete(incoming);}
        }
        void cleanup(){
            if(relay!=null){if(sender!=null)relay.detach(sender);if(receiver!=null)relay.detach(receiver);}
            for(Node node:ownNodes)node.remove();ownNodes.clear();
            var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries)level.setBlock(entry.getKey(),entry.getValue(),3);restored.clear();
        }
    }
    private static final class SentinelEnvironment extends AbstractManagedEnvironment {
        SentinelEnvironment(){setNode(Network.newNode(this,Visibility.Network).withComponent("oc_fixture_cc_gate").create());}
        @li.cil.oc.api.machine.Callback(direct=true)
        public Object[] sentinel(Context context,li.cil.oc.api.machine.Arguments args){return new Object[]{new IDynamicLuaObject(){@Override public String[] getMethodNames(){return new String[]{"ping"};}@Override public MethodResult callMethod(ILuaContext context,int method,IArguments arguments){return MethodResult.of("cc-sentinel");}}};}
    }
    private static final class Probe extends AbstractManagedEnvironment implements Context,EnvironmentHost {
        private final ServerLevel level;private final BlockPos pos;final List<Packet> packets=new ArrayList<>();
        Probe(ServerLevel level,BlockPos pos){this.level=level;this.pos=pos;setNode(Network.newNode(this,Visibility.Network).withConnector(100000).create());}
        @Override public void onMessage(Message message){if(message.name().equals("network.message")&&message.data().length==1&&message.data()[0] instanceof Packet packet)packets.add(packet);}
        @Override public ServerLevel world(){return level;}
        @Override public double xPosition(){return pos.getX()+.5;}
        @Override public double yPosition(){return pos.getY()+.5;}
        @Override public double zPosition(){return pos.getZ()+.5;}
        @Override public void markChanged(){}
        @Override public boolean canInteract(String player){return true;}
        @Override public boolean isRunning(){return true;}
        @Override public boolean isPaused(){return false;}
        @Override public boolean start(){return false;}
        @Override public boolean pause(double seconds){return false;}
        @Override public boolean stop(){return false;}
        @Override public void consumeCallBudget(double cost){}
        @Override public boolean signal(String name,Object...args){return true;}
    }
    private static final class Recorder implements IComputerAccess {
        private record Event(String name,Object[] args){}
        private final int id;private final String name;private final IPeripheral peripheral;private final List<Event> events=new ArrayList<>();
        Recorder(int id,String name,IPeripheral peripheral){this.id=id;this.name=name;this.peripheral=peripheral;}
        boolean hasEvent(int channel,int reply,String message){return events.stream().anyMatch(event->event.name.equals("modem_message")&&event.args.length==4&&name.equals(event.args[0])&&event.args[1] instanceof Number c&&c.intValue()==channel&&event.args[2] instanceof Number r&&r.intValue()==reply&&payload(event.args[3],message));}
        @Override public String mount(String path,Mount mount,String drive){return null;}
        @Override public String mountWritable(String path,WritableMount mount,String drive){return null;}
        @Override public void unmount(String path){}
        @Override public int getID(){return id;}
        @Override public void queueEvent(String event,Object...args){events.add(new Event(event,args==null?new Object[0]:args.clone()));}
        @Override public String getAttachmentName(){return name;}
        @Override public Map<String,IPeripheral> getAvailablePeripherals(){return Map.of(name,peripheral);}
        @Override public IPeripheral getAvailablePeripheral(String name){return this.name.equals(name)?peripheral:null;}
        @Override public WorkMonitor getMainThreadMonitor(){return null;}
    }
}
