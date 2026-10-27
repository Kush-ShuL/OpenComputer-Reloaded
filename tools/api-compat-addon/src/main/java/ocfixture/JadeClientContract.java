package ocfixture;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ui.IBoxElement;
import snownee.jade.impl.config.PluginConfig;

/** Real Jade packets/HUD in the client's own normally loaded integrated-server world. */
@OnlyIn(Dist.CLIENT)
public final class JadeClientContract {
    private static final ResourceLocation[] OPTIONS={
        ResourceLocation.fromNamespaceAndPath("opencomputers","address"),
        ResourceLocation.fromNamespaceAndPath("opencomputers","energy"),
        ResourceLocation.fromNamespaceAndPath("opencomputers","component_name")};
    private static Run active;
    private static boolean observer,events;
    private JadeClientContract(){}
    public static void observerRegistered(){observer=true;}
    public static void start(Minecraft client,Path reportDirectory,List<String> results){
        require(active==null,"Jade client contract already active");
        require(observer,"Jade did not discover the real fixture observer plugin");
        require(client.player!=null&&client.level!=null&&client.hasSingleplayerServer(),"Jade contract requires the owned integrated world");
        require("zh_cn".equals(client.options.languageCode),"Jade client contract requires actual zh_cn language");
        if(!events){events=true;NeoForge.EVENT_BUS.addListener(JadeClientContract::serverTick);NeoForge.EVENT_BUS.addListener(JadeClientContract::afterFrame);}
        active=new Run(client,reportDirectory,results);
        try{active.start();}catch(Throwable error){active.fail(error);}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){
        Run run=active;if(run==null)return;
        try{run.tick();}catch(Throwable error){run.fail(error);}
        if(run.finishing&&run.cleanup!=null&&run.cleanup.isDone()){
            try{run.cleanup.join();}catch(Throwable error){run.results.add("FAIL I18.client cleanup: "+error);}
            active=null;
        }
    }
    public static void collected(IBoxElement box,Accessor<?> accessor){
        Run run=active;
        if(run==null||run.finishing||!(accessor instanceof BlockAccessor block)||accessor.getLevel()!=run.client.level)return;
        if(!block.getPosition().equals(run.casePos)&&!block.getPosition().equals(run.assemblerPos))return;
        run.seen=new Seen(++run.sequence,block.getPosition().immutable(),block.getSide(),
            accessor.getServerData().copy(),clean(box.getTooltip().getMessage()),mask(),box);
    }
    public static void rendered(IBoxElement box,Accessor<?> accessor){
        Run run=active;
        if(run!=null&&run.seen!=null&&run.seen.box==box)run.rendered=run.seen;
    }
    private static void serverTick(ServerTickEvent.Post event){
        Run run=active;if(run==null||run.finishing||event.getServer()!=run.server)return;
        try{run.serverTick();}catch(Throwable error){run.serverError=error;}
    }
    private static void afterFrame(RenderFrameEvent.Post event){
        Run run=active;if(run==null||run.finishing||run.shot==null||run.rendered==null)return;
        Seen seen=run.rendered;
        if(seen.sequence<=run.shotAfter||!seen.position.equals(run.target)||seen.side!=run.side||seen.mask!=mask())return;
        try(NativeImage image=Screenshot.takeScreenshot(run.client.getMainRenderTarget())){
            Path path=run.directory.resolve(run.shot);image.writeToFile(path);
            run.results.add("SCREENSHOT I18 "+path+" "+image.getWidth()+"x"+image.getHeight()+" actual rendered Jade HUD");
            run.shot=null;run.shotDone=true;
        }catch(Throwable error){run.fail(error);}
    }
    private static int mask(){int mask=0;for(int i=0;i<OPTIONS.length;i++)if(PluginConfig.INSTANCE.get(OPTIONS[i]))mask|=1<<i;return mask;}
    private static String clean(String text){return text.replaceAll("§[0-9a-fk-orA-FK-OR]","");}
    private static String translated(String key,Object... args){return clean(net.minecraft.network.chat.Component.translatable(key,args).getString());}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private record Seen(long sequence,BlockPos position,Direction side,CompoundTag data,String message,int mask,IBoxElement box){}
    private static final class Run {
        final Minecraft client;final MinecraftServer server;final ServerLevel level;final Path directory;final List<String> results;
        final BlockPos casePos,assemblerPos;final Map<BlockPos,BlockState> oldBlocks=new LinkedHashMap<>();final Map<Long,Boolean> forced=new LinkedHashMap<>();
        final boolean[] original=new boolean[3];final boolean hiddenGui;
        final long started=System.nanoTime();CompletableFuture<Void> placement,cleanup;
        Case host;Environment assembler;Direction front,side;BlockPos target;
        volatile boolean serverReady,requestAssembly,assemblyReady;volatile String outputName;volatile Throwable serverError;
        Seen seen,rendered;long sequence,after,shotAfter;int phase,combination;String shot;boolean shotDone,finishing;
        Run(Minecraft client,Path directory,List<String> results){
            this.client=client;this.server=client.getSingleplayerServer();this.level=server.overworld();this.directory=directory;this.results=results;
            casePos=client.player.blockPosition().east(12).above(4);assemblerPos=casePos.east();hiddenGui=client.options.hideGui;
        }
        ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing real Jade fixture material "+name);return descriptor.createItemStack(1);}
        void start(){
            for(int i=0;i<OPTIONS.length;i++){require(PluginConfig.INSTANCE.containsKey(OPTIONS[i]),"Actual Jade config did not register "+OPTIONS[i]);original[i]=PluginConfig.INSTANCE.get(OPTIONS[i]);}
            client.options.hideGui=false;
            placement=CompletableFuture.runAsync(()->{
                for(BlockPos pos:List.of(casePos,assemblerPos)){
                    long chunk=ChunkPos.asLong(pos);forced.putIfAbsent(chunk,level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true));
                    level.getChunkAt(pos);require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Jade fixture refuses occupied cell "+pos);oldBlocks.put(pos,level.getBlockState(pos));
                    var descriptor=li.cil.oc.api.Items.get(pos.equals(casePos)?"case3":"assembler");require(descriptor!=null&&descriptor.block()!=null,"Missing actual Jade fixture block");
                    require(level.setBlock(pos,descriptor.block().defaultBlockState(),3),"Could not place actual Jade fixture block");
                }
                require(level.getBlockEntity(casePos) instanceof Case&&level.getBlockEntity(assemblerPos) instanceof Environment,"Actual Jade devices lack SDK interfaces");
                host=(Case)level.getBlockEntity(casePos);assembler=(Environment)level.getBlockEntity(assemblerPos);front=host.facing();
                var player=server.getPlayerList().getPlayer(client.player.getUUID());require(player!=null,"Integrated server lacks the actual client player");
                player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();
            },server);
        }
        void serverTick()throws Exception{
            if(placement==null||!placement.isDone()||placement.isCompletedExceptionally()||host==null)return;
            if(!serverReady){
                Node node=host.machine().node(),work=assembler.node();
                if(node==null||work==null||node.network()==null||node.network()!=work.network())return;
                require(node instanceof Connector,"Actual Case node lacks its normal energy connector");
                ((Connector)node).changeBuffer(321.75);serverReady=true;
            }
            if(requestAssembly&&!assemblyReady){
                Container inventory=(Container)assembler;
                for(var entry:Map.of(0,item("case1"),16,item("cpu1"),17,item("ram1")).entrySet()){
                    require(inventory.canPlaceItem(entry.getKey(),entry.getValue()),"Actual assembler rejected real template material "+entry.getKey());inventory.setItem(entry.getKey(),entry.getValue());
                }
                require(assembler.node() instanceof Component,"Actual assembler node is not its real SDK component");
                Object[] result=((Component)assembler.node()).invoke("start",host.machine());
                require(result!=null&&result.length>0&&Boolean.TRUE.equals(result[0]),"Actual assembler.start did not consume its real template");
                ItemStack pending=(ItemStack)assembler.getClass().getMethod("pendingOutput").invoke(assembler);
                require(!pending.isEmpty()&&pending.get(DataComponents.CUSTOM_NAME)!=null,"Real robot assembly has no original custom output name");
                outputName=pending.getHoverName().getString();((Connector)assembler.node()).changeBuffer(1234.75);assemblyReady=true;
            }
        }
        void flags(int value){for(int i=0;i<OPTIONS.length;i++)PluginConfig.INSTANCE.set(OPTIONS[i],(value&(1<<i))!=0);after=sequence;}
        void aim(BlockPos pos,Direction direction){
            target=pos;side=direction;Vec3 center=Vec3.atCenterOf(pos),eye=center.add(Vec3.atLowerCornerOf(direction.getNormal()).scale(2.8));
            Vec3 delta=center.subtract(eye);float yaw=(float)Math.toDegrees(-Math.atan2(delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
            client.player.getAbilities().flying=true;client.player.setDeltaMovement(Vec3.ZERO);
            client.player.setPos(eye.x,eye.y-client.player.getEyeHeight(),eye.z);
            client.player.setYRot(yaw);client.player.yRotO=yaw;client.player.setYHeadRot(yaw);client.player.yHeadRotO=yaw;
            client.player.setXRot(pitch);client.player.xRotO=pitch;after=sequence;
        }
        Seen current(){
            Seen value=seen;
            return value!=null&&value.sequence>after&&value.position.equals(target)&&value.side==side&&value.mask==mask()&&value.data.contains("opencomputers:waila",Tag.TAG_COMPOUND)?value:null;
        }
        CompoundTag node(Seen value){
            CompoundTag data=value.data.getCompound("opencomputers:waila");
            return data.contains("nodes",Tag.TAG_LIST)?data.getList("nodes",Tag.TAG_COMPOUND).getCompound(value.side.get3DDataValue()):data;
        }
        void fields(Seen value,CompoundTag node,int flags){
            String address=node.getString("address"),name=node.getString("componentName");
            require(!address.isEmpty()&&!name.isEmpty()&&node.getInt("bufferSize")>0,"Actual Jade server response omitted its ordinary node facts: "+node);
            String[] lines={translated("oc:gui.Analyzer.Address",address),translated("oc:gui.Analyzer.StoredEnergy",node.getInt("buffer")+"/"+node.getInt("bufferSize")),translated("oc:gui.Analyzer.ComponentName",name)};
            for(int i=0;i<lines.length;i++)require(value.message.contains(lines[i])==((flags&(1<<i))!=0),"Actual Jade option "+OPTIONS[i]+" mask="+flags+" differs from its server data; expected line="+lines[i]+", HUD="+value.message);
        }
        void screenshot(String name){shot=name;shotAfter=sequence;shotDone=false;}
        void tick()throws Exception{
            require(System.nanoTime()-started<TimeUnit.MINUTES.toNanos(3),"Jade client timed out phase="+phase+", observation="+seen);
            if(serverError!=null)throw new IllegalStateException("Actual Jade server operation failed",serverError);
            if(finishing)return;
            switch(phase){
                case 0->{
                    if(!placement.isDone()||!serverReady)return;placement.join();
                    if(client.level.getBlockEntity(casePos)==null||client.level.getBlockEntity(assemblerPos)==null)return;
                    aim(casePos,front.getOpposite());flags(0);phase=1;
                }
                case 1->{
                    Seen value=current();if(value==null)return;fields(value,node(value),combination);
                    results.add("PASS I18.client Jade actual server-data/HUD independent config mask="+combination+" side="+value.side);
                    if(++combination<8)flags(combination);
                    else{screenshot("jade-case-node.png");phase=2;}
                }
                case 2->{if(!shotDone)return;aim(casePos,front);phase=3;}
                case 3->{
                    Seen value=current();if(value==null)return;require(node(value).isEmpty(),"Actual closed Case front leaked a sided node: "+node(value));
                    for(String key:List.of("oc:gui.Analyzer.Address","oc:gui.Analyzer.StoredEnergy","oc:gui.Analyzer.ComponentName")){
                        String prefix=translated(key,"").strip();require(!value.message.contains(prefix),"Closed Case front rendered ordinary node data: "+value.message);
                    }
                    results.add("PASS I18.client actual LocalPlayer crosshair selects Case closed front; real server sided data and rendered HUD suppress its node");
                    screenshot("jade-case-front-closed.png");phase=4;
                }
                case 4->{if(!shotDone)return;requestAssembly=true;phase=5;}
                case 5->{if(!assemblyReady)return;aim(assemblerPos,Direction.SOUTH);phase=6;}
                case 6->{
                    Seen value=current();if(value==null)return;CompoundTag data=value.data.getCompound("opencomputers:waila");
                    if(!data.contains("progress",Tag.TAG_ANY_NUMERIC)||!data.contains("outputName",Tag.TAG_STRING))return;
                    var output=net.minecraft.network.chat.Component.Serializer.fromJson(data.getString("outputName"),client.level.registryAccess());
                    require(output!=null&&output.getString().equals(outputName),"Actual Jade output-name packet differs from the real assembled robot: "+data);
                    int seconds=data.getInt("timeRemaining");String time=String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
                    String progress=translated("oc:gui.Assembler.Progress",Integer.toString((int)data.getDouble("progress")),time),building=translated("oc:gui.Assembler.Building",output);
                    require(value.message.contains(progress),"Actual Jade progress/time text differs from server facts: expected="+progress+", HUD="+value.message);
                    require(value.message.contains(building),"Actual Jade did not render its serialized custom output name: expected="+building+", HUD="+value.message);
                    fields(value,node(value),7);results.add("PASS I18.client actual assembler.start consumes real case/CPU/RAM; live Jade packets render truncated progress, m:ss time and custom robot output "+output.getString());
                    screenshot("jade-assembler-working.png");phase=7;
                }
                case 7->{if(shotDone)finish();}
                default->throw new AssertionError("Unknown Jade client phase "+phase);
            }
        }
        void fail(Throwable error){if(finishing)return;results.add("FAIL I18.client phase="+phase+": "+error);finish();}
        void finish(){
            if(finishing)return;finishing=true;client.options.hideGui=hiddenGui;for(int i=0;i<OPTIONS.length;i++)PluginConfig.INSTANCE.set(OPTIONS[i],original[i]);
            cleanup=CompletableFuture.runAsync(()->{
                if(host!=null)host.machine().stop();
                for(var entry:oldBlocks.entrySet()){if(level.getBlockEntity(entry.getKey()) instanceof Container inventory)inventory.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}
                for(var entry:forced.entrySet())if(entry.getValue())level.setChunkForced(ChunkPos.getX(entry.getKey()),ChunkPos.getZ(entry.getKey()),false);
            },server);
        }
    }
}
