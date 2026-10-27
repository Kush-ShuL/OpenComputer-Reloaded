package ocfixture;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.TextBuffer;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Original OS installers and commands driven through actual screen/keyboard input. */
public final class OperatingSystemContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-operating-systems");
    private static Run active;
    private OperatingSystemContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){Run run=new Run(level,position,results);active=run;try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}}
    public static boolean isRunning(){return active!=null;}
    public static void tick(){Run run=active;if(run==null)return;try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}active=null;run.cleanup();}
    public static void close(){Run run=active;active=null;if(run!=null)run.cleanup();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ItemStack item(String name){var value=li.cil.oc.api.Items.get(name);require(value!=null,"Missing actual OS hardware "+name);return value.createItemStack(1);}
    private static String text(Object value){return value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):String.valueOf(value);}
    private static final class Observer extends AbstractManagedEnvironment {
        final AtomicInteger starts=new AtomicInteger(),stops=new AtomicInteger();
        Observer(){setNode(li.cil.oc.api.Network.newNode(this,Visibility.Network).withComponent("os_contract_observer").create());}
        @Override public void onMessage(Message message){if(message.name().equals("computer.started"))starts.incrementAndGet();if(message.name().equals("computer.stopped"))stops.incrementAndGet();}
    }
    private static final class Run {
        final ServerLevel level;final BlockPos position;final List<String> results;final List<SystemRun> systems=new ArrayList<>();
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;}
        void start(){for(int index=0;index<2;index++){SystemRun system=new SystemRun(level,position.south(index*40),index==0?"openos":"plan9k",results);systems.add(system);system.place();}}
        boolean tick()throws Exception{boolean done=true;for(SystemRun system:systems)if(!system.done){system.tick();done&=system.done;}return done;}
        void fail(Throwable error){results.add("FAIL operating.systems "+error);LOGGER.error("OPERATING_SYSTEMS failed",error);for(SystemRun system:systems)system.capture();}
        void cleanup(){for(SystemRun system:systems)system.cleanup();}
    }
    private static final class SystemRun {
        final ServerLevel level;final BlockPos position;final String system,marker;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        final EmbeddedChannel channel=new EmbeddedChannel();final Observer observer=new Observer();
        final List<String> commands;Case host;TextBuffer screen;ServerPlayer actor;Component disk,source,eeprom;
        int ticks,phase,phaseTick,floppySlot,diskSlot,command;boolean done;String lastScreen="",diskAddress;
        SystemRun(ServerLevel level,BlockPos position,String system,List<String> results){this.level=level;this.position=position;this.system=system;this.results=results;marker=system+"-native-disk-proof";
            commands=List.of("mkdir /oc-migration","echo "+marker+" > /oc-migration/original","cp /oc-migration/original /oc-migration/copied","mv /oc-migration/copied /oc-migration/moved","cat /oc-migration/moved","ls -l /oc-migration","echo 'print(\"OS_LUA_\" .. tostring(2+3))' > /oc-migration/check.lua","lua /oc-migration/check.lua","rm /oc-migration/original");}
        void place(BlockPos pos,BlockState state){
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
            require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"OS fixture cell is occupied: "+pos);restored.put(pos,level.getBlockState(pos));require(level.setBlock(pos,state,3),"Could not place OS fixture block");
        }
        void place(){
            place(position,li.cil.oc.api.Items.get("case3").block().defaultBlockState());host=(Case)level.getBlockEntity(position);
            place(position.above(),li.cil.oc.api.Items.get("screen3").block().defaultBlockState());place(position.east(),Blocks.STONE.defaultBlockState());place(position.east().above(),li.cil.oc.api.Items.get("keyboard").block().defaultBlockState());
            ItemStack cpu=item("cpu3");var processor=Driver.driverFor(cpu);require(processor instanceof MutableProcessor,"Actual CPU does not expose public architecture selection");
            var architecture=li.cil.oc.api.Machine.architectures().stream().filter(type->li.cil.oc.api.Machine.getArchitectureName(type).equals("Lua 5.3")).findFirst().orElseThrow(()->new AssertionError("Original OS contract requires native Lua 5.3"));((MutableProcessor)processor).setArchitecture(cpu,architecture);
            insert(cpu);insert(item("ram6"));insert(item("ram6"));insert(item("graphicscard3"));diskSlot=insert(item("hdd3"));floppySlot=insert(item(system));insert(item("luabios"));
            actor=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),system.equals("openos")?"OC_OpenOS":"OC_Plan9k"),ClientInformation.createDefault());
            Connection transport=new Connection(PacketFlow.SERVERBOUND){@Override public Channel channel(){return SystemRun.this.channel;}@Override public boolean isConnected(){return SystemRun.this.channel.isOpen();}@Override public void send(Packet<?> packet,PacketSendListener listener,boolean flush){}};
            NetworkRegistry.configureMockConnection(transport);actor.connection=new ServerGamePacketListenerImpl(level.getServer(),transport,actor,CommonListenerCookie.createInitial(actor.getGameProfile(),false));actor.setNoGravity(true);actor.setPos(position.getX()+.5,position.getY()+1,position.getZ()-2);
        }
        int insert(ItemStack stack){for(int slot=0;slot<host.getContainerSize();slot++)if(host.getItem(slot).isEmpty()&&host.canPlaceItem(slot,stack)){host.setItem(slot,stack);return slot;}throw new AssertionError("Actual Case rejected OS hardware "+stack);}
        Component component(String name,int slot){for(Node node:host.machine().node().network().nodes())if(node instanceof Component value&&value.name().equals(name)&&(slot<0||host.componentSlot(value.address())==slot))return value;throw new AssertionError("Missing actual OS component "+name+" at slot "+slot);}
        Object[] invoke(Component component,String method,Object... args)throws Exception{return component.invoke(method,host.machine(),args);}
        boolean exists(String path)throws Exception{return Boolean.TRUE.equals(invoke(disk,"exists",path)[0]);}
        long size(String path)throws Exception{return ((Number)invoke(disk,"size",path)[0]).longValue();}
        void input(String value){require(value.indexOf('\n')<0&&value.indexOf('\r')<0,"Input must be a single normal terminal command");screen.clipboard(value,actor);screen.keyDown('\r',28,actor);screen.keyUp('\r',28,actor);phaseTick=ticks;LOGGER.info("OS_CONTRACT {} phase={} input={}",system,phase,value);}
        String screenText(){StringBuilder value=new StringBuilder();for(int y=0;y<screen.getHeight();y++){for(int x=0;x<screen.getWidth();x++)value.appendCodePoint(screen.getCodePoint(x,y));value.append('\n');}return value.toString();}
        boolean line(String value){return lastScreen.lines().anyMatch(row->row.strip().equals(value));}
        boolean ready(){return lastScreen.contains("#")&&(system.equals("openos")?lastScreen.contains("OpenOS"):lastScreen.contains("Starting Plan9k shell"));}
        boolean stopped(){return !host.machine().isRunning()&&!host.machine().architecture().isInitialized();}
        void advance(int next){phase=next;phaseTick=ticks;LOGGER.info("OS_CONTRACT {} phase={}",system,phase);}
        boolean commandDone()throws Exception{return switch(command){
            case 0->Boolean.TRUE.equals(invoke(disk,"isDirectory","oc-migration")[0]);
            case 1->size("oc-migration/original")==marker.length()+1;
            case 2->size("oc-migration/copied")==marker.length()+1;
            case 3->!exists("oc-migration/copied")&&size("oc-migration/moved")==marker.length()+1;
            case 4->line(marker);
            case 5->lastScreen.lines().filter(row->!row.contains("/")&&!row.contains("#")).anyMatch(row->row.contains("moved"))&&lastScreen.lines().filter(row->!row.contains("/")&&!row.contains("#")).anyMatch(row->row.contains("original"));
            case 6->size("oc-migration/check.lua")>20;
            case 7->line("OS_LUA_5");
            case 8->!exists("oc-migration/original");default->throw new AssertionError("Unknown OS command stage");};}
        void tick()throws Exception{
            require(++ticks<=3000,system+" exceeded 3000 live world ticks; phase="+phase+" command="+command);((Connector)host.machine().node()).changeBuffer(Double.POSITIVE_INFINITY);
            require(host.machine().lastError()==null,system+" VM failed: "+host.machine().lastError());if(ticks%5!=0)return;
            if(phase==0){
                screen=(TextBuffer)((Environment)level.getBlockEntity(position.above())).node().host();Node keyboard=((Environment)level.getBlockEntity(position.east().above())).node();
                Component screenNode=(Component)screen.node();Object[] keyboards=(Object[])invoke(screenNode,"getKeyboards")[0];require(java.util.Arrays.stream(keyboards).anyMatch(address->text(address).equals(keyboard.address())),"OS screen does not see its actual physically adjacent keyboard");
                require(host.machine().node().network()==screen.node().network()&&screen.node().network()==keyboard.network(),"OS physical input graph did not naturally connect");
                disk=component("filesystem",diskSlot);source=component("filesystem",floppySlot);eeprom=component("eeprom",-1);diskAddress=disk.address();
                require(Boolean.TRUE.equals(invoke(source,"isReadOnly")[0])&&!exists("init.lua"),"OS installation must begin with a real read-only program disk and blank real HDD");host.machine().node().connect(observer.node());
                require(host.machine().start(),"Actual standard Lua BIOS failed to start for "+system);advance(1);return;
            }
            lastScreen=screenText();
            if(phase==1&&ready()){require(text(invoke(eeprom,"getData")[0]).equals(source.address()),"Original BIOS did not select the actual OS installation disk");input(system.equals("openos")?"install --from="+source.address()+" --to="+disk.address()+" --noreboot":"install --noreboot");advance(2);return;}
            if(phase==2){
                if(system.equals("openos")&&lastScreen.contains("Install ")&&lastScreen.contains("[Y/n]")){input("y");advance(4);}
                else if(system.equals("plan9k")&&lastScreen.contains("number between 1 and")){input("1");advance(3);}return;
            }
            if(phase==3&&lastScreen.contains("Select distribution/action")){input("1");advance(4);return;}
            if(phase==4&&exists("init.lua")&&exists("bin/sh.lua")&&(lastScreen.contains("Returning to shell")||lastScreen.contains("Installation complete!"))){capture();input("shutdown");advance(5);return;}
            if(phase==5&&stopped()){
                for(String file:system.equals("openos")?List.of("init.lua","lib/core/boot.lua","bin/sh.lua","bin/install.lua"):List.of("init.lua","boot/kernel/pipes","lib/modules/base/25_init.lua","bin/sh.lua"))require(exists(file),system+" original installer omitted "+file);
                ItemStack removed=host.removeItem(floppySlot,1);require(!removed.isEmpty()&&source.network()==null,"Actual OS installation disk was not physically removed");
                results.add("PASS os."+system+".install original standard BIOS booted a read-only real program disk; actual installer ran through normal screen/keyboard input and populated the blank HDD before normal shutdown and disk removal");
                require(host.machine().start(),"Installed HDD could not start after removing its installation disk");advance(6);return;
            }
            if(phase==6&&ticks-phaseTick>=20&&ready()){
                require(host.getItem(floppySlot).isEmpty()&&diskAddress.equals(disk.address())&&text(invoke(eeprom,"getData")[0]).equals(disk.address()),"Installed OS did not boot independently from the original HDD identity");
                input(commands.get(0));advance(7);return;
            }
            if(phase==7&&ticks-phaseTick>=5&&commandDone()){
                if(++command<commands.size()){input(commands.get(command));return;}
                results.add("PASS os."+system+".programs actual HDD-booted shell executes mkdir, redirected echo, cp, mv, cat, ls, Lua script and rm; screen shows actual file contents and OS_LUA_5");capture();input("reboot");advance(8);return;
            }
            if(phase==8&&observer.starts.get()>=3&&ticks-phaseTick>=20&&ready()){
                require(text(invoke(eeprom,"getData")[0]).equals(diskAddress)&&exists("oc-migration/moved")&&!exists("oc-migration/original"),"Original reboot lost HDD boot selection or persistent command results");input("shutdown");advance(9);return;
            }
            if(phase==9&&stopped()){
                Object descriptor=invoke(disk,"open","oc-migration/moved","r")[0];String bytes;
                try{bytes=text(invoke(disk,"read",descriptor,128)[0]);}finally{invoke(disk,"close",descriptor);}
                require(bytes.equals(marker+"\n"),"Actual HDD bytes differ from the shell write/copy/move result: "+bytes);
                require(observer.starts.get()==3&&observer.stops.get()>=3,"OS startup/shutdown/reboot omitted actual machine lifecycle events");
                results.add("PASS os."+system+".reboot original reboot program restarts without installation media; native Lua 5.3 then shuts down cleanly and actual persistent HDD bytes match the shell commands");capture();done=true;LOGGER.info("OS_CONTRACT {} PASS ticks={}",system,ticks);
            }
        }
        void capture(){if(screen==null)return;try{Files.writeString(Path.of(System.getProperty("ocfixture.deviceDirectory")).resolve(system+"-screen-phase-"+phase+".txt"),"system="+system+" phase="+phase+" command="+command+" ticks="+ticks+"\n"+screenText(),StandardCharsets.UTF_8);}catch(Exception error){LOGGER.warn("Could not retain OS screen output",error);}}
        void cleanup(){observer.node().remove();if(host!=null)host.machine().stop();var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries){if(level.getBlockEntity(entry.getKey()) instanceof Container inventory)inventory.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}channel.finishAndReleaseAll();for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);}
    }
}
