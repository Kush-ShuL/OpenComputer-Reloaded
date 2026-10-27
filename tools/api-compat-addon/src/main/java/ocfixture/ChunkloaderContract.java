package ocfixture;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real SDK chunkloader callbacks observed through Minecraft's public persistent-ticket save view. */
public final class ChunkloaderContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-chunkloader-contract");
    private static Run active;
    private ChunkloaderContract(){}

    public static void start(ServerLevel level,BlockPos position,List<String> results){
        require(active==null,"A chunkloader contract is already active");
        Run run=new Run(level,position.immutable(),results);active=run;
        try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){
        Run run=active;if(run==null)return;
        try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}
        active=null;run.cleanup();
    }
    public static void close(){
        Run run=active;active=null;
        if(run!=null){run.results.add("FAIL chunkloader: server stopped before the real ticket contract completed");run.cleanup();}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object first(Object[] values,String operation){require(values!=null&&values.length==1,operation+" returned no single value");return values[0];}

    private static final class Run {
        final ServerLevel level;final BlockPos position;final List<String> results;
        BlockState restored;Case host;ManagedEnvironment upgrade;Component component;Connector connector;
        UUID owner;boolean forced,cleaned;int ticks,phase,deadline;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;}

        void start(){
            require(API.config!=null&&!API.config.getBoolean("power.ignorePower")&&API.config.getDouble("power.cost.chunkloaderCost")>0,
                    "Chunkloader contract requires the normal positive energy cost");
            forced=level.setChunkForced(position.getX()>>4,position.getZ()>>4,true);level.getChunkAt(position);
            require(level.isEmptyBlock(position)&&level.getBlockEntity(position)==null,"Chunkloader fixture refuses an occupied Case cell");
            var descriptor=li.cil.oc.api.Items.get("case3");require(descriptor!=null&&descriptor.block()!=null,"Real tier-three Case block is unavailable");
            restored=level.getBlockState(position);require(level.setBlock(position,descriptor.block().defaultBlockState(),3),"Could not place real chunkloader Case");
            require(level.getBlockEntity(position) instanceof Case,"Placed block does not expose the published Case interface");host=(Case)level.getBlockEntity(position);
            require(host.machine()!=null&&host.machine().host()==host,"Callbacks require the placed Case's own machine Context");
        }
        boolean invoke(String method,Object... args)throws Exception{
            Object value=first(component.invoke(method,host.machine(),args),method);require(value instanceof Boolean,method+" returned no boolean");return (Boolean)value;
        }
        void create()throws Exception{
            require(host.machine().node().network()!=null,"Actual Case did not naturally initialize its network");
            ItemStack stack=li.cil.oc.api.Items.get("chunkloaderupgrade").createItemStack(1);
            var driver=Driver.driverFor(stack,host.getClass());require(driver!=null,"Real Case-context driver rejected its chunkloader upgrade");
            upgrade=driver.createEnvironment(stack,host);require(upgrade!=null&&upgrade.node() instanceof Component&&upgrade.node() instanceof Connector,
                    "Actual chunkloader driver did not expose a component and energy connector");
            upgrade.load(driver.dataTag(stack),level.registryAccess());component=(Component)upgrade.node();connector=(Connector)upgrade.node();
            host.machine().node().connect(upgrade.node());
            require(component.network()==host.machine().node().network()&&component.canBeSeenFrom(host.machine().node()),"Chunkloader did not join its actual Case network");
            CompoundTag saved=new CompoundTag();upgrade.save(saved,level.registryAccess());require(saved.hasUUID("ticketIdentity"),"Chunkloader did not persist its ticket owner identity");owner=saved.getUUID("ticketIdentity");
        }
        Set<Long> tickets(){
            ForcedChunksSavedData data=level.getDataStorage().get(ForcedChunksSavedData.factory(),ForcedChunksSavedData.FILE_ID);
            require(data!=null,"Actual chunk-forcing operation created no persistent saved data");
            // Public vanilla save method includes NeoForge's own ticket records; no production implementation or private fields are used.
            CompoundTag saved=data.save(new CompoundTag(),level.registryAccess());Set<Long> found=new HashSet<>();
            for(Tag value:saved.getList("ModForced",Tag.TAG_COMPOUND)){
                CompoundTag controller=(CompoundTag)value;if(!"opencomputers:chunkloader".equals(controller.getString("Controller")))continue;
                for(Tag entry:controller.getList("ModForced",Tag.TAG_COMPOUND)){
                    CompoundTag chunk=(CompoundTag)entry;
                    for(Tag nonTicking:chunk.getList("Entities",Tag.TAG_INT_ARRAY))require(!owner.equals(NbtUtils.loadUUID(nonTicking)),"Chunkloader installed a non-ticking ticket");
                    for(Tag ticking:chunk.getList("TickingEntities",Tag.TAG_INT_ARRAY))if(owner.equals(NbtUtils.loadUUID(ticking)))found.add(chunk.getLong("Chunk"));
                }
            }
            return found;
        }
        void nine(){
            ChunkPos center=new ChunkPos(position);Set<Long> expected=new HashSet<>();
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)expected.add(ChunkPos.asLong(center.x+x,center.z+z));
            Set<Long> actual=tickets();require(actual.equals(expected),"Chunkloader persistent ticking tickets differ from its actual Case's nine chunks: "+actual);
        }
        void charge(){
            connector.changeBuffer(Double.POSITIVE_INFINITY);require(connector.globalBuffer()>API.config.getDouble("power.cost.chunkloaderCost")*API.config.getDouble("power.tickFrequency"),"Real Case network has insufficient charged capacity");
        }
        boolean tick()throws Exception{
            require(++ticks<=250,"Chunkloader contract exceeded 250 actual world ticks");
            if(phase==0){
                if(ticks<3)return false;create();require(!invoke("isActive")&&tickets().isEmpty(),"Stopped Case unexpectedly activated its chunkloader");charge();
                require(invoke("setActive",true)&&invoke("isActive"),"Real setActive(true) did not activate the chunkloader");nine();
                require(!invoke("setActive",true),"Repeated enable reported a state change");nine();
                require(invoke("setActive",false)&&!invoke("isActive")&&tickets().isEmpty(),"Explicit disable left persistent tickets");
                results.add("PASS chunkloader.tickets real Case-context enable creates exactly nine fully ticking NeoForge persistent tickets, repeated enable is unchanged and disable removes them");
                require(invoke("setActive",true),"Could not reactivate for the real energy depletion case");nine();connector.changeBuffer(Double.NEGATIVE_INFINITY);
                require(connector.globalBuffer()==0,"Actual Case network did not drain to zero");deadline=ticks+(int)Math.ceil(API.config.getDouble("power.tickFrequency"))*2+5;phase=1;return false;
            }
            // This SDK-created environment is ticked exactly once per actual server tick, as a mounted host would tick it.
            if(upgrade.canUpdate())upgrade.update();
            if(invoke("isActive")){require(ticks<deadline,"Empty real energy network did not release tickets at the configured check frequency");return false;}
            require(tickets().isEmpty(),"Power exhaustion left persistent ticking tickets");
            results.add("PASS chunkloader.power draining the actual shared Connector releases all nine tickets on its configured update cycle");
            charge();require(invoke("setActive",true),"Could not reactivate for actual node removal");nine();upgrade.node().remove();
            require(!invoke("isActive")&&tickets().isEmpty(),"Removing the actual component node left active persistent tickets");
            results.add("PASS chunkloader.detach actual network-node removal disables the component and removes all owned NeoForge tickets");return true;
        }
        void fail(Throwable error){results.add("FAIL chunkloader: "+error);LOGGER.error("CHUNKLOADER_CONTRACT FAIL",error);}
        void cleanup(){
            if(cleaned)return;cleaned=true;
            if(upgrade!=null&&upgrade.node()!=null)upgrade.node().remove();
            if(host!=null)host.machine().stop();
            if(restored!=null)level.setBlock(position,restored,3);
            if(forced)level.setChunkForced(position.getX()>>4,position.getZ()>>4,false);
        }
    }
}
