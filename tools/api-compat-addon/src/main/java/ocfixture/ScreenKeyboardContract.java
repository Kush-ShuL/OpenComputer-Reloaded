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
import li.cil.oc.api.Network;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Colored;
import li.cil.oc.api.internal.Keyboard;
import li.cil.oc.api.internal.Rotatable;
import li.cil.oc.api.internal.TextBuffer;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Physical screen grouping, keyboard routing, original input events and real world persistence. */
public final class ScreenKeyboardContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-screen-keyboard-contract");
    private static Run active;
    private static boolean registered;
    private ScreenKeyboardContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){
        require(active==null,"A screen/keyboard contract is already active");Run run=new Run(level,position.immutable(),results);active=run;
        if(!registered){registered=true;NeoForge.EVENT_BUS.addListener(ScreenKeyboardContract::beforeTick);NeoForge.EVENT_BUS.addListener(ScreenKeyboardContract::joined);}
        try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}
    }
    private static void beforeTick(ServerTickEvent.Pre event){
        Run run=active;
        if(run!=null)run.tickerOpportunity=List.of(run.mainPos,run.memberPos,run.keyboardPos).stream().allMatch(pos->
            run.level.getChunkAt(pos).getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)&&run.level.areEntitiesLoaded(ChunkPos.asLong(pos)));
    }
    private static void joined(EntityJoinLevelEvent event){
        Run run=active;
        if(run!=null&&run.recordingDrops&&event.getLevel()==run.level&&!event.loadedFromDisk()&&event.getEntity() instanceof ItemEntity item
                &&new AABB(run.keyboardPos).inflate(2).contains(item.position()))run.spawnedDrops.add(item);
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){Run run=active;if(run==null)return;try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}active=null;run.cleanup();}
    public static void close(){Run run=active;active=null;if(run!=null){run.results.add("FAIL screen.keyboard: server stopped before the world contract completed");run.cleanup();}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double actual,double expected,String message){require(Math.abs(actual-expected)<1e-7,message+": "+actual+" != "+expected);}
    private record Signal(Node source,String type,Object[] values){}
    private static final class Probe extends AbstractManagedEnvironment{
        final List<Signal> signals=new ArrayList<>();
        Probe(){setNode(Network.newNode(this,Visibility.Network).create());}
        @Override public void onMessage(Message message){if(message.name().equals("computer.checked_signal")||message.name().equals("computer.signal"))signals.add(new Signal(message.source(),message.name(),message.data().clone()));}
        List<Signal> named(String name){return signals.stream().filter(signal->signal.values.length>1&&name.equals(signal.values[signal.type.equals("computer.checked_signal")?1:0])).toList();}
        void clear(){signals.clear();}
    }
    private static final class Run{
        final ServerLevel level;final BlockPos position,mainPos,memberPos,keyboardPos,supportPos,forwardPos,powerPos;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();final List<ItemEntity> spawnedDrops=new ArrayList<>();
        Case host;Environment main,member,keyEnvironment;TextBuffer buffer;Keyboard keyboard;Node keyboardNode;Probe probe;FakePlayer player;
        int phase,ticks,frequency;double energyBefore,expectedEnergy;Connector energy;int originalColor;boolean cleaned,tickerOpportunity,recordingDrops;
        Run(ServerLevel level,BlockPos position,List<String> results){
            this.level=level;this.position=position;this.results=results;mainPos=position.above();memberPos=mainPos.west();keyboardPos=mainPos.east();supportPos=position.east();forwardPos=keyboardPos.north();powerPos=mainPos.above();
        }
        void remember(BlockPos pos){
            if(restored.containsKey(pos))return;
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
            require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Screen fixture refuses an occupied cell "+pos);restored.put(pos,level.getBlockState(pos));
        }
        void place(String name,BlockPos pos){remember(pos);var info=li.cil.oc.api.Items.get(name);require(info!=null&&info.block()!=null&&level.setBlock(pos,info.block().defaultBlockState(),3),"Could not place actual "+name);}
        Environment environment(BlockPos pos){require(level.getBlockEntity(pos) instanceof Environment,"Real world block exposes no SDK Environment at "+pos);return (Environment)level.getBlockEntity(pos);}
        TextBuffer text(BlockPos pos){Node node=environment(pos).node();require(node!=null&&node.host() instanceof TextBuffer,"Screen node exposes no public TextBuffer");return (TextBuffer)node.host();}
        Object[] call(Environment environment,String method,Object... args)throws Exception{
            require(environment.node() instanceof Component,"Actual screen has no SDK component");return ((Component)environment.node()).invoke(method,host.machine(),args);
        }
        Object value(Environment environment,String method,Object... args)throws Exception{Object[] values=call(environment,method,args);require(values!=null&&values.length>0,method+" returned no result");return values[0];}
        void aspect(Environment environment,int width,int height)throws Exception{Object[] values=call(environment,"getAspectRatio");require(values.length==2,"Screen aspect omitted dimensions");near(((Number)values[0]).doubleValue(),width,"Physical screen width");near(((Number)values[1]).doubleValue(),height,"Physical screen height");}
        void unsupported(Environment environment)throws Exception{Object[] values=call(environment,"setPrecise",true);require(values.length==2&&values[0]==null&&"unsupported operation".equals(values[1]),"Lower-tier screen accepted precise coordinates");}
        CompoundTag snapshot(BlockPos pos){return level.getBlockEntity(pos).getUpdateTag(level.registryAccess()).getCompound("screenSync");}
        Object adjacentScreen()throws Exception{return level.getBlockEntity(keyboardPos).getClass().getMethod("connectedScreen").invoke(level.getBlockEntity(keyboardPos));}
        void nearKeyboard(){player.setPos(keyboardPos.getX()+.5,keyboardPos.getY(),keyboardPos.getZ()+2);}
        void start(){
            require(API.config!=null&&!API.config.getBoolean("power.ignorePower"),"Screen contract requires normal power accounting");
            frequency=(int)Math.max(1,API.config.getDouble("power.tickFrequency"));
            place("case3",position);place("screen3",mainPos);place("screen3",memberPos);remember(supportPos);level.setBlock(supportPos,Blocks.STONE.defaultBlockState(),3);place("keyboard",keyboardPos);remember(forwardPos);remember(powerPos);
            require(level.getBlockEntity(position) instanceof Case,"Screen callbacks require an actual placed Case");host=(Case)level.getBlockEntity(position);require(host.machine()!=null&&host.machine().host()==host,"Screen callback Context is not the actual Case machine");
            player=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes("oc-screen-keyboard-operator".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"OCScreenKeys"));player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);nearKeyboard();
        }
        void inputs()throws Exception{
            probe.clear();buffer.keyDown('a',30,player);buffer.keyDown('a',30,player);buffer.keyUp('a',30,player);buffer.keyUp('a',30,player);
            require(probe.named("key_down").size()==2&&probe.named("key_up").size()==1,"Real physical keyboard lost repeat/paired key-up behavior");
            boolean username=API.config.getBoolean("misc.inputUsername");
            for(Signal signal:probe.named("key_down")){
                require(signal.source==keyboardNode&&signal.values[0]==player&&signal.values[2].equals('a')&&signal.values[3].equals(30),"Keyboard event lost its real node/player/character/code");
                require(signal.values.length==(username?5:4)&&(!username||player.getGameProfile().getName().equals(signal.values[4])),"Keyboard event ignored the configured username policy");
            }
            probe.clear();buffer.clipboard("one\rtwo\r\nthree\nfour",player);
            require(probe.named("clipboard").stream().map(signal->signal.values[2]).toList().equals(List.of("one\r","two\r\n","three\n","four")),"Clipboard did not preserve original CR/LF/CRLF line splitting");
            keyboard.setUsableOverride((component,operator)->false);probe.clear();buffer.keyDown('x',45,player);require(probe.named("key_down").isEmpty(),"Keyboard ignored its public usability override");keyboard.setUsableOverride(null);
            buffer.keyDown('b',48,player);probe.clear();player.setPos(position.getX()+20,position.getY(),position.getZ());
        }
        void precisionAndEnergy()throws Exception{
            TextBuffer first=text(supportPos),second=text(forwardPos);unsupported(environment(supportPos));unsupported(environment(forwardPos));
            require(first.getMaximumWidth()==50&&first.getMaximumHeight()==16&&first.getMaximumColorDepth()==TextBuffer.ColorDepth.OneBit,"Real tier-one screen limits differ");
            require(second.getMaximumWidth()==80&&second.getMaximumHeight()==25&&second.getMaximumColorDepth()==TextBuffer.ColorDepth.FourBit,"Real tier-two screen limits differ");
            first.setPowerState(false);second.setPowerState(false);
            require(Boolean.FALSE.equals(value(main,"setPrecise",true))&&Boolean.TRUE.equals(value(main,"isPrecise")),"Tier-three screen precise mode transition failed");
            require(snapshot(mainPos).getBoolean("precise"),"Actual complete screen snapshot omitted precise mode");
            probe.clear();buffer.mouseDown(2.25,1.5,0,player);buffer.mouseUp(-1,-1,0,player);
            Signal touch=probe.named("touch").getFirst(),drop=probe.named("drop").getFirst();
            require(touch.values[2].equals(2.25)&&touch.values[3].equals(1.5)&&drop.values[2].equals(-1d)&&drop.values[3].equals(-1d),"Screen changed precise coordinates or outside-drop sentinel");
            buffer.setColorDepth(TextBuffer.ColorDepth.EightBit);buffer.setResolution(16,4);buffer.setViewport(8,4);buffer.setForegroundColor(0xFFFFFF);buffer.setBackgroundColor(0);buffer.fill(0,0,16,4,' ');buffer.fill(0,0,4,4,'X');
            near(buffer.getEnergyCostPerTick(),API.config.getDouble("power.cost.screen")*2,"Merged screen base energy cost");
            energy=(Connector)host.machine().node();energy.changeBuffer(energy.globalBufferSize());energyBefore=energy.globalBuffer();
            expectedEnergy=.5*buffer.getEnergyCostPerTick()*(160*50d/(50*16))*frequency;
        }
        boolean tick()throws Exception{
            require(++ticks<=150,"Screen/keyboard contract exceeded 150 actual world ticks");
            switch(phase){
                case 0->{
                    // onLoad can create graph edges before vanilla permits this chunk's BE tickers.
                    if(!tickerOpportunity)return false;
                    if(host.machine().node().network()==null)return false;main=environment(mainPos);member=environment(memberPos);keyEnvironment=environment(keyboardPos);
                    if(main.node().network()!=host.machine().node().network()||member.node().network()!=main.node().network()||keyEnvironment.node().network()!=main.node().network())return false;
                    aspect(main,2,1);require(((Component)main.node()).visibility()==Visibility.Network&&((Component)member.node()).visibility()==Visibility.None,"Merged screen origin/member component visibility differs");
                    buffer=text(mainPos);keyboardNode=keyEnvironment.node();require(keyboardNode.host() instanceof Keyboard,"Physical keyboard exposes no SDK Keyboard");keyboard=(Keyboard)keyboardNode.host();
                    require(adjacentScreen()==null,"Floor keyboard incorrectly selected a sideways screen outside its original adjacency rules");
                    for(BlockPos pos:List.of(mainPos,keyboardPos)){
                        require(level.getBlockEntity(pos) instanceof Rotatable,"Screen or keyboard omitted the original SDK orientation interface");Rotatable orientation=(Rotatable)level.getBlockEntity(pos);
                        for(Direction side:Direction.values())require(orientation.toLocal(orientation.toGlobal(side))==side,"SDK local/global orientation is not invertible");require(orientation.toGlobal(Direction.SOUTH)==orientation.facing(),"SDK facing disagrees with its direction conversion");
                    }
                    require(((SidedEnvironment)keyEnvironment).sidedNode(Direction.UP)==null&&((SidedEnvironment)keyEnvironment).sidedNode(Direction.SOUTH)==null,"Floor keyboard exposes nodes on its face/back");
                    probe=new Probe();host.machine().node().connect(probe.node());place("screen2",forwardPos);phase=1;return false;
                }
                case 1->{
                    if(environment(forwardPos).node().network()!=main.node().network())return false;
                    require(adjacentScreen()==level.getBlockEntity(forwardPos),"Keyboard did not choose its actual forward screen");place("screen1",supportPos);phase=2;return false;
                }
                case 2->{
                    if(environment(supportPos).node().network()!=main.node().network())return false;
                    require(adjacentScreen()==level.getBlockEntity(supportPos),"Keyboard did not prefer its actual supporting screen");
                    require(snapshot(mainPos).getBoolean("keyboard"),"Real merged screen does not recognize its adjacent physical keyboard");
                    Object attached=value(main,"getKeyboards");require(attached instanceof String[]&&Arrays.equals((String[])attached,new String[]{keyboardNode.address()}),"Merged screen did not address its actual adjacent keyboard exactly once");
                    results.add("PASS screen.topology actual rectangular two-screen origin/hidden member, physical keyboard sides, SDK orientation and side/forward/support adjacency priority; real getKeyboards address");
                    inputs();phase=3;return false;
                }
                case 3->{
                    require(probe.named("key_up").size()==1&&probe.named("key_up").getFirst().values[3].equals(48),"Natural keyboard tick did not release a key after its player left range");
                    probe.clear();buffer.keyDown('c',46,player);require(probe.named("key_down").isEmpty(),"Out-of-range physical keyboard accepted input");
                    keyboard.setUsableOverride((component,operator)->true);buffer.keyDown('d',32,player);buffer.keyUp('d',32,player);require(probe.named("key_down").size()==1&&probe.named("key_up").size()==1,"Public remote usability override or far key release failed");keyboard.setUsableOverride(null);nearKeyboard();
                    CompoundTag savedKeyboard=level.getBlockEntity(keyboardPos).saveWithoutMetadata(level.registryAccess());String address=keyboardNode.address();level.getBlockEntity(keyboardPos).loadWithComponents(savedKeyboard,level.registryAccess());require(address.equals(keyEnvironment.node().address()),"Keyboard node identity did not survive actual BE save/load");
                    results.add("PASS keyboard.input real screen-to-keyboard signals preserve player/node/username, repeat and paired key-up, CR/LF/CRLF clipboard, usability override, range rejection/release and keyboard BE persistence");
                    precisionAndEnergy();phase=4;return false;
                }
                case 4->{
                    if(level.getGameTime()%frequency!=0)return false;
                    near(energyBefore-energy.globalBuffer(),expectedEnergy,"Natural tick half-lit merged tier-three screen energy");
                    level.setBlock(powerPos,Blocks.REDSTONE_BLOCK.defaultBlockState(),3);phase=5;return false;
                }
                case 5->{
                    require(!buffer.getPowerState(),"Actual redstone rising edge did not switch the merged screen off");
                    CompoundTag saved=level.getBlockEntity(mainPos).saveWithoutMetadata(level.registryAccess());require(saved.getBoolean("oc:hadRedstoneInput"),"Real screen save lost its sampled high redstone level");
                    level.getBlockEntity(mainPos).loadWithComponents(saved,level.registryAccess());phase=6;return false;
                }
                case 6->{
                    require(!buffer.getPowerState()&&Boolean.TRUE.equals(value(main,"isPrecise"))&&buffer.getViewportWidth()==8&&buffer.getViewportHeight()==4&&buffer.getCodePoint(0,0)=='X',"Powered screen save/load lost its off/precise/viewport/text state or spuriously toggled again");
                    level.setBlock(powerPos,Blocks.AIR.defaultBlockState(),3);phase=7;return false;
                }
                case 7->{require(!buffer.getPowerState(),"Falling redstone edge switched the screen");level.setBlock(powerPos,Blocks.REDSTONE_BLOCK.defaultBlockState(),3);phase=8;return false;}
                case 8->{
                    require(buffer.getPowerState(),"Second real rising edge did not switch the screen on");level.setBlock(powerPos,Blocks.AIR.defaultBlockState(),3);
                    originalColor=((Colored)level.getBlockEntity(mainPos)).getColor();value(main,"setTouchModeInverted",true);
                    ItemStack dye=new ItemStack(Items.RED_DYE,2);player.setItemInHand(InteractionHand.MAIN_HAND,dye);BlockState state=level.getBlockState(memberPos);
                    require(state.useItemOn(dye,level,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(memberPos),Direction.NORTH,memberPos,false)).consumesAction(),"Actual screen dye interaction was not handled");require(dye.getCount()==2,"Screen coloring consumed dye contrary to the original screen behavior");phase=9;return false;
                }
                case 9->{
                    aspect(main,1,1);aspect(member,1,1);require(((Component)member.node()).visibility()==Visibility.Network&&!Boolean.TRUE.equals(value(main,"isTouchModeInverted")),"Recoloring did not split the rectangle/reset original touch mode");
                    ((Colored)level.getBlockEntity(memberPos)).setColor(originalColor);phase=10;return false;
                }
                case 10->{
                    aspect(main,2,1);require(((Component)member.node()).visibility()==Visibility.None,"Recoloring back did not restore the original rectangle");
                    results.add("PASS screen.state real three-tier precision limits/snapshot, precise touch/drop signal coordinates, half-lit merged energy, powered BE persistence without a phantom toggle, subsequent real redstone edges, free dye/split/remerge and touch-mode reset");
                    BlockState state=level.getBlockState(forwardPos);for(var property:state.getProperties())if(property instanceof DirectionProperty direction&&property.getName().equals("pitch"))state=state.setValue(direction,Direction.UP);level.setBlock(forwardPos,state,3);phase=11;return false;
                }
                case 11->{
                    require(((Rotatable)level.getBlockEntity(forwardPos)).facing()==Direction.UP,"Real floor-screen rotation was not exposed through the SDK");
                    probe.clear();BlockState state=level.getBlockState(forwardPos);state.getBlock().stepOn(level,forwardPos,state,player);state.getBlock().stepOn(level,forwardPos,state,player);
                    require(probe.named("walk").size()==1,"Real floor screen did not deduplicate repeated walk events in the same screen cell");
                    recordingDrops=true;
                    try{level.setBlock(supportPos,Blocks.AIR.defaultBlockState(),3);}finally{recordingDrops=false;}
                    phase=12;return false;
                }
                default->{
                    require(level.isEmptyBlock(keyboardPos),"Removing the real support left a floating keyboard");
                    require(spawnedDrops.stream().anyMatch(item->item.isAddedToLevel()&&!item.isRemoved()&&item.getItem().is(li.cil.oc.api.Items.get("keyboard").createItemStack(1).getItem())),
                        "Unsupported keyboard did not drop its actual item; join-event candidates="+spawnedDrops.stream().map(item->item.getItem()+" added="+item.isAddedToLevel()+" removed="+item.isRemoved()).toList());
                    require(!snapshot(mainPos).getBoolean("keyboard")&&((String[])value(main,"getKeyboards")).length==0,"Removing physical keyboard left stale screen addressing");
                    require(!host.machine().isRunning(),"Screen contract unexpectedly started a VM");
                    results.add("PASS screen.lifecycle real floor rotation/walk event and actual support removal/drop clear physical keyboard presence/addressing; all callbacks used the stopped real Case Context");
                    return true;
                }
            }
        }
        void fail(Throwable error){results.add("FAIL screen.keyboard: "+error);LOGGER.error("SCREEN_KEYBOARD_CONTRACT FAIL phase={}",phase,error);}
        void cleanup(){
            if(cleaned)return;cleaned=true;if(keyboard!=null)keyboard.setUsableOverride(null);if(probe!=null)probe.node().remove();if(host!=null)host.machine().stop();if(player!=null)player.getInventory().clearContent();
            var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries)level.setBlock(entry.getKey(),entry.getValue(),3);
            for(ItemEntity item:spawnedDrops)if(!item.isRemoved())item.discard();
            for(long packed:forced){ChunkPos chunk=new ChunkPos(packed);level.setChunkForced(chunk.x,chunk.z,false);}forced.clear();
        }
    }
}
