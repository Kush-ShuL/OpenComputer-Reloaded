package ocfixture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import li.cil.oc.api.API;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Finite world operations for capacitor adjacency, six isolated power networks and a real tablet in a charger. */
public final class PowerDevicesContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-power-devices-contract");
    private static Run active;
    private PowerDevicesContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){
        require(active==null,"A power device contract is already active");Run run=new Run(level,position.immutable(),results);active=run;
        try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){
        Run run=active;if(run==null)return;
        try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}
        active=null;run.cleanup();
    }
    public static void close(){Run run=active;active=null;if(run!=null){run.results.add("FAIL power.devices: server stopped before the world contract completed");run.cleanup();}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void equal(double actual,double expected,String message){require(Math.abs(actual-expected)<=Math.max(1e-8,Math.abs(expected)*1e-10),message+": expected="+expected+", actual="+actual);}

    private static final class Run {
        final ServerLevel level;final BlockPos position,distributorPos,chargerPos;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        final List<Connector> networks=new ArrayList<>();final List<Case> cases=new ArrayList<>();
        Connector capacitor,carpeted,farCapacitor,chargerNode;Container charger;Node oldFilesystem;
        SidedEnvironment distributor;String filesystemAddress;int ticks,phase,startedAt;long chargedAt;double transferred;boolean cleaned;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;distributorPos=position.south(8);chargerPos=position.south(16);this.results=results;}
        void force(BlockPos pos){
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
        }
        void place(String name,BlockPos pos){
            force(pos);require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Power fixture refuses an occupied cell "+pos);
            var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null&&descriptor.block()!=null,"Missing real power block "+name);
            restored.put(pos.immutable(),level.getBlockState(pos));require(level.setBlock(pos,descriptor.block().defaultBlockState(),3),"Could not place actual "+name);
        }
        Connector connector(BlockPos pos){
            require(level.getBlockEntity(pos) instanceof Environment,"Real power block does not expose the published Environment interface at "+pos);
            Node node=((Environment)level.getBlockEntity(pos)).node();require(node instanceof Connector,"Real power block has no published Connector at "+pos);return (Connector)node;
        }
        void start(){
            require(API.config!=null&&!API.config.getBoolean("power.ignorePower")&&API.config.getDouble("power.buffer.capacitorAdjacencyBonus")>0,
                    "Power world contract requires the normal nonzero adjacency bonus and enabled energy accounting");
            place("capacitor",position);place("carpetedcapacitor",position.east());place("capacitor",position.east(2));
            place("powerdistributor",distributorPos);place("case3",distributorPos.west());place("case3",distributorPos.east());
            require(level.getBlockEntity(distributorPos) instanceof SidedEnvironment,"Actual distributor does not expose its published sided environment");distributor=(SidedEnvironment)level.getBlockEntity(distributorPos);
            for(BlockPos pos:List.of(distributorPos.west(),distributorPos.east())){
                require(level.getBlockEntity(pos) instanceof Case,"Power distribution requires actual placed Case hosts");cases.add((Case)level.getBlockEntity(pos));
            }
            place("charger",chargerPos);require(level.getBlockEntity(chargerPos) instanceof Container,"Real charger has no inventory");charger=(Container)level.getBlockEntity(chargerPos);
            require(CreativeTab.instance!=null,"Actual configured creative tablet is unavailable");
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(),true,level.registryAccess()));
            var tablet=li.cil.oc.api.Items.get("tablet");require(tablet!=null,"Real tablet descriptor is missing");ItemStack configured=ItemStack.EMPTY;
            for(ItemStack stack:CreativeTab.instance.getDisplayItems())if(stack.is(tablet.item())){
                CompoundTag data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
                if(data.getInt("oc:tier")==3&&data.contains("oc:items",Tag.TAG_LIST)){configured=stack.copy();break;}
            }
            require(!configured.isEmpty()&&charger.canPlaceItem(0,configured),"Charger rejected the actual configured tablet");charger.setItem(0,configured);
        }
        void capacitors(){
            capacitor=connector(position);carpeted=connector(position.east());farCapacitor=connector(position.east(2));
            double base=API.config.getDouble("power.buffer.capacitor"),bonus=API.config.getDouble("power.buffer.capacitorAdjacencyBonus");
            equal(capacitor.localBufferSize(),base+bonus*1.5,"First capacitor immediate and distance-two bonuses");
            equal(carpeted.localBufferSize(),base+bonus*2,"Carpeted capacitor must count both direct neighbors");
            equal(farCapacitor.localBufferSize(),base+bonus*1.5,"Last capacitor immediate and distance-two bonuses");
            require(level.setBlock(position.east(),Blocks.AIR.defaultBlockState(),3),"Could not remove fixture carpeted capacitor");
            equal(capacitor.localBufferSize(),base+bonus*.5,"Removing direct carpeted neighbor retained its immediate bonus");
            equal(farCapacitor.localBufferSize(),base+bonus*.5,"Removing shared direct neighbor retained its immediate bonus");
            require(carpeted.network()==null,"Removed carpeted capacitor kept its real network node");
            require(level.setBlock(position.east(2),Blocks.AIR.defaultBlockState(),3),"Could not remove fixture distant capacitor");
            equal(capacitor.localBufferSize(),base,"Removing the distance-two capacitor retained its half bonus");require(farCapacitor.network()==null,"Removed far capacitor kept its node");
            results.add("PASS capacitor.adjacency real ordinary/carpeted blocks receive full direct and half distance-two bonuses, remove both bonuses and detach removed nodes");
        }
        Node filesystem(){
            Node found=null;
            for(Node node:chargerNode.network().nodes())if(node instanceof Component component&&"filesystem".equals(component.name())){
                require(found==null,"Real charger unexpectedly connected multiple tablet filesystems");found=node;
            }
            return found;
        }
        void beginDistribution(){
            Set<Object> identities=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Direction direction:Direction.values()){
                Node node=distributor.sidedNode(direction);require(node instanceof Connector&&node.network()!=null,"Distributor face has no naturally initialized Connector/network");
                require(identities.add(node.network()),"Isolated distributor faces accidentally share an OC network");networks.add((Connector)node);
            }
            require(cases.get(0).machine().node().network()==distributor.sidedNode(Direction.WEST).network()
                            &&cases.get(1).machine().node().network()==distributor.sidedNode(Direction.EAST).network(),
                    "Actual opposite Case networks did not naturally connect to the matching distributor faces");
            for(Connector connector:networks)connector.changeBuffer(Double.NEGATIVE_INFINITY);
            Connector source=(Connector)distributor.sidedNode(Direction.WEST);transferred=Math.min(100,source.globalBufferSize()/4);require(transferred>0,"Source Case network has no real storage capacity");
            equal(source.changeBuffer(transferred),0,"Real source network could not store the supplied energy");chargedAt=level.getGameTime();
        }
        void distribution(){
            double sum=0,size=0;Set<Object> identities=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Connector connector:networks){require(identities.add(connector.network()),"Distributor merged independent networks while balancing");sum+=connector.globalBuffer();size+=connector.globalBufferSize();}
            equal(sum,transferred,"Balancing actual isolated networks created or destroyed energy");double ratio=sum/size;
            for(Connector connector:networks)equal(connector.globalBuffer(),connector.globalBufferSize()*ratio,"An isolated real network did not reach the shared fill ratio");
            results.add("PASS distributor.balance two real opposite Case networks and four isolated faces retain separate graphs, conserve "+transferred+" OC energy and reach the same fill ratio on actual world ticks");
        }
        boolean tick(){
            require(++ticks<=200,"Power devices contract exceeded 200 actual world ticks");
            if(phase==0){
                if(ticks<3)return false;capacitors();beginDistribution();chargerNode=connector(chargerPos);require(chargerNode.network()!=null,"Actual charger did not initialize its network");
                oldFilesystem=filesystem();require(oldFilesystem!=null,"Configured tablet did not mount its actual storage component in the charger");filesystemAddress=oldFilesystem.address();
                require(filesystemAddress!=null,"Mounted tablet filesystem has no real node address");chargerNode.remove();
                require(oldFilesystem.network()==null,"Disconnecting the actual charger left its tablet filesystem in an old network");startedAt=ticks;phase=1;return false;
            }
            if(ticks-startedAt<2||level.getGameTime()==chargedAt||level.getGameTime()%API.config.getDouble("power.tickFrequency")!=0)return false;
            distribution();require(chargerNode.network()!=null,"Actual charger did not naturally rejoin after node removal");Node remounted=filesystem();
            require(remounted!=null&&remounted!=oldFilesystem&&filesystemAddress.equals(remounted.address()),"Charger did not recreate and reconnect its actual tablet filesystem with the persisted address");
            require(oldFilesystem.network()==null,"Original tablet filesystem became attached after remount");
            results.add("PASS charger.lifecycle real configured tablet storage mounts in the charger, detaches on actual node removal and naturally remounts with its persisted address");return true;
        }
        void fail(Throwable error){results.add("FAIL power.devices: "+error);LOGGER.error("POWER_DEVICES_CONTRACT FAIL",error);}
        void cleanup(){
            if(cleaned)return;cleaned=true;
            if(charger!=null)charger.clearContent();for(Case host:cases)host.machine().stop();
            var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries)level.setBlock(entry.getKey(),entry.getValue(),3);
            for(long packed:forced){ChunkPos chunk=new ChunkPos(packed);level.setChunkForced(chunk.x,chunk.z,false);}forced.clear();
        }
    }
}
