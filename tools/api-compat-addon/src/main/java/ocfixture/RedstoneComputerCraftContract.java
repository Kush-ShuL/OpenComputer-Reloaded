package ocfixture;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.MountConstants;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.media.MediaCapability;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.network.*;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real Case Context, world I/O, generic CC inventory and a disk-booted CC Lua computer. */
public final class RedstoneComputerCraftContract {
    private static final Logger LOG=LoggerFactory.getLogger("oc-redstone-cc-contract");
    private static Run active;
    private RedstoneComputerCraftContract(){}
    public static void start(ServerLevel level,BlockPos origin,List<String> results){Run run=new Run(level,origin,results);active=run;try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}}
    public static boolean isRunning(){return active!=null;}
    public static void tick(){Run run=active;if(run==null)return;try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}active=null;run.cleanup();}
    public static void close(){Run run=active;active=null;if(run!=null){run.results.add("FAIL redstone.cc: interrupted");run.cleanup();}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ItemStack item(String name){var value=li.cil.oc.api.Items.get(name);require(value!=null,"Missing hardware "+name);return value.createItemStack(1);}
    private static Block block(String id){return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));}
    private static int integer(Object value){require(value instanceof Number,"Non-numeric result: "+value);return ((Number)value).intValue();}
    private static final class Observer extends AbstractManagedEnvironment {
        final List<Message> messages=new ArrayList<>();
        Observer(){setNode(Network.newNode(this,Visibility.Network).create());}
        @Override public void onMessage(Message message){messages.add(message);}
    }
    private static final class Run {
        final ServerLevel level;final BlockPos pos;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();final List<Node> owned=new ArrayList<>();
        final Observer observer=new Observer();Case host;Component io,computer,generic;WritableMount disk;int ticks,phase,phaseTick;
        final int[] masks={0,1,32768,0x5555,0xaaaa,65535,0};
        Run(ServerLevel level,BlockPos pos,List<String> results){this.level=level;this.pos=pos;this.results=results;}
        void place(BlockPos position,BlockState state){
            if(level.setChunkForced(position.getX()>>4,position.getZ()>>4,true))forced.add(ChunkPos.asLong(position.getX()>>4,position.getZ()>>4));level.getChunkAt(position);
            require(level.isEmptyBlock(position),"Occupied test cell "+position);restored.put(position,level.getBlockState(position));require(level.setBlock(position,state,3),"Placement failed "+position);
        }
        void start()throws Exception{
            require(API.config.getBoolean("integration.computerCraft.enabled"),"This contract needs the explicitly enabled CC fixture");
            place(pos,block("opencomputers:redstone").defaultBlockState());
            place(pos.south(),block("opencomputers:case3").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH));
            place(pos.east(),block("computercraft:computer_normal").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));
            place(pos.east().south(),block("opencomputers:adapter").defaultBlockState());
            place(pos.east(2),block("computercraft:disk_drive").defaultBlockState());
            place(pos.west(),Blocks.CHEST.defaultBlockState());
            place(pos.west().south(),block("opencomputers:adapter").defaultBlockState());
            host=(Case)level.getBlockEntity(pos.south());require(host.machine()!=null,"Real Case machine missing");
            ItemStack media=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("computercraft:disk")));
            var capability=media.getCapability(MediaCapability.get());require(capability!=null,"Real CC media capability missing");
            var mount=capability.createDataMount(media,level);require(mount instanceof WritableMount,"Real writable CC mount missing");disk=(WritableMount)mount;
            write("cc-target","0");
            // Normal CC computers name left/right from the viewer's side, unlike turtles.
            write("startup.lua","while true do\n local f=fs.open('/disk/cc-target','r')\n local n=f and tonumber(f.readAll())\n if f then f.close() end\n if n then redstone.setBundledOutput('right',n) end\n local o=fs.open('/disk/cc-observed','w')\n o.write(tostring(redstone.getBundledInput('right'))) o.close()\n local d=fs.open('/disk/cc-output','w') d.write(tostring(redstone.getBundledOutput('right'))) d.close()\n sleep(0.05)\nend\n");
            var drive=level.getCapability(Capabilities.ItemHandler.BLOCK,pos.east(2),Direction.WEST);require(drive!=null&&drive.insertItem(0,media,false).isEmpty(),"Real disk drive rejected prepared CC disk");
            ((Container)level.getBlockEntity(pos.west())).setItem(0,new ItemStack(net.minecraft.world.item.Items.DIAMOND,7));
        }
        Object[] call(Component component,String method,Object...args)throws Exception{return component.invoke(method,host.machine(),args);}
        Object value(Component component,String method,Object...args)throws Exception{Object[] values=call(component,method,args);require(values!=null&&values.length>0,method+" returned nothing");return values[0];}
        Component find(String method){
            Node node=host.machine().node();if(node.network()!=null)for(Node candidate:node.reachableNodes())if(candidate instanceof Component component&&component!=host.machine().node()&&component.methods().contains(method))return component;return null;
        }
        void write(String name,String text)throws Exception{try(var out=disk.openFile(name,MountConstants.WRITE_OPTIONS)){ByteBuffer data=ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));while(data.hasRemaining())out.write(data);}}
        String read(String name)throws Exception{if(!disk.exists(name))return null;try(var in=disk.openForRead(name)){ByteBuffer data=ByteBuffer.allocate(128);while(data.hasRemaining()&&in.read(data)>0){}return new String(data.array(),0,data.position(),StandardCharsets.UTF_8);}}
        boolean tick()throws Exception{
            require(++ticks<=600,"Real CC computer did not complete bounded disk/redstone exchange; phase="+phase);
            if(phase==0){
                if(ticks<5)return false;
                io=(Component)((Environment)level.getBlockEntity(pos)).node();computer=find("turnOn");generic=find("pushItems");if(computer==null||generic==null)return false;
                // An all-zero cached matrix proves this host has completed initial sampling.
                if(!(value(io,"getBundledOutput") instanceof Map<?,?> sampled&&sampled.get(0) instanceof Map<?,?> row&&integer(row.get(0))==0))return false;
                Network.joinNewNetwork(observer.node());observer.node().connect(host.machine().node());owned.add(observer.node());
                redstone();
                Object inventory=value(generic,"list");require(inventory instanceof Map<?,?> entries&&entries.get(1) instanceof Map<?,?> entry&&"minecraft:diamond".equals(entry.get("name"))&&integer(entry.get("count"))==7,"CC generic inventory list did not report the real chest contents: "+inventory);
                require(integer(value(generic,"size"))==27,"CC generic inventory main-thread size callback failed");
                results.add("PASS cc.generic: actual Adapter discovers CC generic chest inventory; list/size run through CC's synchronized Lua task with real item counts");
                place(pos.north(),Blocks.REDSTONE_BLOCK.defaultBlockState());
                call(computer,"turnOn");phase=1;phaseTick=ticks;return false;
            }
            int index=phase-1,mask=masks[index];
            if(ticks-phaseTick>120)throw new AssertionError("CC exchange stalled: phase="+phase+", target="+read("cc-target")+", observed="+read("cc-observed")+", luaOutput="+read("cc-output")+", ccWorldOutput="+ComputerCraftAPI.getBundledRedstoneOutput(level,pos.east(),Direction.WEST)+", ioInput="+value(io,"getBundledInput",5)+", ioOutput="+value(io,"getBundledOutput",5)+", computer="+level.getBlockState(pos.east()));
            String observed=read("cc-observed");if(observed==null||!observed.trim().equals(Integer.toString(mask)))return false;
            for(int color=0;color<16;color++)if(integer(value(io,"getBundledInput",Direction.EAST.get3DDataValue(),color))!=((mask&(1<<color))==0?0:255))return false;
            if(index==0){require(integer(value(io,"getInput",Direction.NORTH.get3DDataValue()))==15,"World redstone block did not update input cache");require(observer.messages.stream().anyMatch(message->message.name().equals("computer.start")),"External threshold crossing did not wake a reachable non-neighbor observer");}
            if(index==masks.length-1){
                require(observer.messages.stream().anyMatch(message->message.name().equals("computer.signal")&&message.data().length==5&&"redstone_changed".equals(message.data()[0])),"No actual per-channel redstone_changed signal reached the network");
                results.add("PASS cc.bundled: disk-booted CC Lua and actual OC I/O exchange all sixteen digital channels both ways, including masks 0/1/32768/21845/43690/65535, with real channel events");return true;
            }
            int next=masks[index+1];write("cc-target",Integer.toString(next));Map<Integer,Integer> outputs=new LinkedHashMap<>();for(int color=0;color<16;color++)outputs.put(color,(next&(1<<color))==0?0:color+1);
            call(io,"setBundledOutput",Direction.EAST.get3DDataValue(),outputs);phase++;phaseTick=ticks;return false;
        }
        void redstone()throws Exception{
            require(io.methods().contains("getBundledInput"),"External I/O lacks bundled callbacks");
            require(integer(value(io,"getWakeThreshold"))==0&&integer(value(io,"setWakeThreshold",10))==0,"Initial/previous wake threshold mismatch");
            require(integer(value(io,"setOutput",Direction.DOWN.get3DDataValue(),42))==0&&integer(value(io,"getOutput",Direction.DOWN.get3DDataValue()))==42,"Original unclamped output mismatch");
            require(level.getSignal(pos,Direction.UP)==42,"World weak output did not use the actual unbounded output");
            call(io,"setOutput",Map.of(0.0,0,1.0,7));require(integer(value(io,"getOutput",1))==7,"Lua numeric-key output table mismatch");
            require(integer(value(io,"getComparatorInput",Direction.WEST.get3DDataValue()))==1,"Actual chest comparator input mismatch");
            for(String method:List.of("setOutput","setBundledOutput"))try{call(io,method);throw new AssertionError("Accepted missing arguments: "+method);}catch(Exception expected){require(expected.getClass()==Exception.class,"Original argument-count exception changed: "+expected);}
            try{call(io,"getBundledInput",0,16);throw new AssertionError("Accepted invalid color");}catch(IllegalArgumentException expected){}
            // Pinned API quirk: the no-side output table exposes raw bundled INPUT rows.
            Object raw=value(io,"getBundledOutput");require(raw instanceof Map<?,?> table&&table.size()==6&&table.get(0) instanceof Map<?,?> row&&row.size()==16,"Pinned all-side matrix shape changed");
            call(io,"setBundledOutput",2,3,99);require(integer(value(io,"getBundledOutput",2,3))==99,"Side/color output did not preserve numeric value");
            require(integer(value(io,"getBundledOutput",2) instanceof Map<?,?> row?row.get(3):null)==99,"Side output table mismatch");
            for(String name:List.of("redstonecard1","redstonecard2")){
                ItemStack stack=item(name);var driver=Driver.driverFor(stack,host.getClass());require(driver!=null,"Redstone driver absent "+name);var environment=driver.createEnvironment(stack,host);require(environment!=null&&environment.node() instanceof Component,"Redstone driver environment missing");
                Component card=(Component)environment.node();owned.add(card);host.machine().node().connect(card);require(card.methods().contains("setBundledOutput")==name.endsWith("2"),"Wrong card tier callback surface");
                var caseState=level.getBlockState(pos.south());level.setBlock(pos.south(),caseState.setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST),3);
                call(card,"setOutput",Direction.SOUTH.get3DDataValue(),13);
                require(integer(value(card,"getOutput",Direction.SOUTH.get3DDataValue()))==13&&value(card,"getOutput") instanceof Map<?,?> outputs&&integer(outputs.get(Direction.EAST.get3DDataValue()))==13,"Rotated single-side local/global table axes changed");
                call(card,"setOutput",Direction.SOUTH.get3DDataValue(),0);level.setBlock(pos.south(),caseState,3);
                require(integer(value(card,"setWakeThreshold",23))==0,"Card threshold default mismatch");CompoundTag data=new CompoundTag();environment.save(data,level.registryAccess());String address=card.address();card.remove();
                var reloaded=driver.createEnvironment(stack,host);owned.add(reloaded.node());reloaded.load(data,level.registryAccess());host.machine().node().connect(reloaded.node());require(address.equals(reloaded.node().address())&&integer(value((Component)reloaded.node(),"getWakeThreshold"))==23,"Saved card identity/threshold lost");
            }
            call(io,"setOutput",Map.of(0,0,1,0));call(io,"setBundledOutput",2,3,0);
            results.add("PASS redstone: actual I/O, tier1/2 card callback surface, world signals, comparator chest, Lua table keys, error types, raw matrix shape, saved card identity and wake threshold");
        }
        void fail(Throwable error){results.add("FAIL redstone.cc: "+error);LOG.error("Redstone/CC contract failed",error);}
        void cleanup(){
            if(computer!=null)try{call(computer,"shutdown");}catch(Exception ignored){}
            for(Node node:owned)node.remove();owned.clear();var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries)level.setBlock(entry.getKey(),entry.getValue(),3);restored.clear();
            for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);forced.clear();
        }
    }
}
