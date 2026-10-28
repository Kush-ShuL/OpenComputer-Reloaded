package ocfixture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValueFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import li.cil.oc.api.API;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Nanomachines;
import li.cil.oc.api.Network;
import li.cil.oc.api.event.RobotRenderEvent;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.internal.Tablet;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.client.CameraType;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.PortalParticle;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Remaining C22 effects observed in an actual owned client, native engine and normal connection. */
@OnlyIn(Dist.CLIENT)
public final class EffectsClientContract {
    private static final UUID SUPPORTER=UUID.fromString("9f1f262f-0d68-4e13-9161-9eeaf4a0a1a8");
    private static final String DEBUG_CLIPBOARD="OC effects actual debug clipboard";
    private static final String ROBOT_LABEL="C22 Robot Label";
    private static final String FOCUSED_LABEL="Creatix";
    private static volatile Run active;
    private static boolean registered;
    private EffectsClientContract(){}
    public static void start(Minecraft client,Path directory,List<String> results){
        require(active==null,"Effects contract already active");
        require(client.player!=null&&client.level!=null&&client.hasSingleplayerServer(),"Effects require the owned integrated world");
        require(SUPPORTER.equals(client.player.getUUID()),"Launch this owned test client with the original entitled UUID");
        if(!registered){registered=true;NeoForge.EVENT_BUS.addListener(EffectsClientContract::sound);NeoForge.EVENT_BUS.addListener(EffectsClientContract::robot);NeoForge.EVENT_BUS.addListener(EffectsClientContract::rendered);}
        Run run=new Run(client,directory,results);active=run;
        try{run.start();}catch(Throwable error){run.fail(error);}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){
        Run run=active;if(run==null)return;
        try{run.tick();}catch(Throwable error){run.fail(error);}
        if(run.finishing&&run.cleanup!=null&&run.cleanup.isDone()){
            try{run.cleanup.join();}catch(Throwable error){run.results.add("FAIL C22.effects cleanup: "+error);}
            active=null;
        }
    }
    public static void close(){Run run=active;if(run!=null&&!run.finishing)run.fail(new AssertionError("Effects contract closed before completion"));}
    private static void sound(PlaySoundEvent event){
        Run run=active;SoundInstance sound=event.getSound();if(run==null||run.finishing||sound==null)return;
        if(sound.getLocation().equals(id("floppy_insert"))||sound.getLocation().equals(id("floppy_eject")))run.diskSounds.add(sound);
        if(sound.getLocation().equals(ResourceLocation.withDefaultNamespace("block.note_block.harp")))run.clipboardBeeps++;
    }
    private static void robot(RobotRenderEvent event){
        Run run=active;if(run==null||run.finishing)return;
        try{
            if(event.agent==null){
                Object tint=invoke("li.cil.oc.client.renderer.PetRenderer","chassisTint",new Class<?>[0]);
                if(tint!=null){require(((Number)tint).intValue()==0xff4de699,"Real supporter pet lost its original tint");run.petFrames++;}
            }else if(event.agent instanceof Robot robot&&BlockPos.containing(robot.xPosition(),robot.yPosition(),robot.zPosition()).equals(run.robotPos)){run.robotFrames++;run.robotLight=event.packedLight;}
        }catch(Throwable error){run.fail(error);}
    }
    private static void rendered(RenderFrameEvent.Post event){
        Run run=active;if(run==null||run.finishing)return;run.frame++;if(run.screenshot==null||run.frame<run.shotAfter)return;
        try(NativeImage pixels=Screenshot.takeScreenshot(run.client.getMainRenderTarget())){
            Path path=run.directory.resolve(run.screenshot);pixels.writeToFile(path);run.results.add("SCREENSHOT C22 "+path+" actual "+pixels.getWidth()+"x"+pixels.getHeight());
            if(run.labelsOnly)try{run.labelPixels(pixels);}catch(AssertionError error){
                // Preserve both views even if a pixel assertion fails, so an actual
                // rendering regression can be distinguished from an oracle problem.
                run.labelErrors.add(error.toString());if(run.labelOnImage!=null){run.labelOnImage.close();run.labelOnImage=null;}
            }
            run.screenshot=null;run.shotDone=true;
        }catch(Throwable error){run.fail(error);}
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("opencomputers",path);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object invoke(String owner,String method,Class<?>[] types,Object... args)throws Exception{return Class.forName(owner).getMethod(method,types).invoke(null,args);}
    private static Object read(Class<?> owner,Object target,String name)throws Exception{var field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static final class SignalObserver extends AbstractManagedEnvironment {
        volatile Object[] debug,down,up;
        SignalObserver(){setNode(Network.newNode(this,Visibility.Network).create());}
        @Override public void onMessage(Message message){
            if(message.name().equals("computer.signal")&&message.data().length>0&&message.data()[0].equals("debug_message"))debug=message.data().clone();
            if(message.name().equals("computer.checked_signal")&&message.source().host() instanceof li.cil.oc.api.internal.TextBuffer&&message.data().length>=5){
                if(message.data()[1].equals("touch"))down=message.data().clone();
                if(message.data()[1].equals("drop"))up=message.data().clone();
            }
        }
    }
    private static final class Run {
        final Minecraft client;final MinecraftServer server;final ServerLevel level;final Path directory,settingsPath;final List<String> results;
        final BlockPos base,drivePos,waypointPos,robotPos,anchor,computerPos;
        final Map<BlockPos,BlockState> oldBlocks=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        final List<SoundInstance> diskSounds=new ArrayList<>();final CameraType oldCamera;final String oldClipboard;
        final long started=System.nanoTime();final double range;final float volume;final boolean labelsOnly;
        byte[] oldSettings;Container drive;Case host;Component debug;SignalObserver observer,robotObserver,tabletObserver;
        Robot realRobot;Tablet realTablet;ItemStack savedHand;double robotOutside,tabletOutside;
        CompletableFuture<Void> action,cleanup;int phase,clipboardBeeps,petFrames,robotFrames,hiddenStart,renderStart;
        long nextDiskAt;String screenshot;boolean shotDone,finishing;volatile boolean serverHidden;
        int frame,shotAfter,labelView,robotLight;Vec3 oldPosition,labelEye;float oldYaw,oldPitch;boolean oldMayfly,oldFlying,oldNoGravity,oldOnGround;
        long oldDayTime;boolean oldDaylight,timeCaptured;NativeImage labelOnImage;final List<String> labelErrors=new ArrayList<>();
        Run(Minecraft client,Path directory,List<String> results){
            this.client=client;this.server=client.getSingleplayerServer();this.level=server.getLevel(client.level.dimension());this.directory=directory;
            this.settingsPath=client.gameDirectory.toPath().resolve("config/opencomputers/settings.conf");this.results=results;
            base=client.player.blockPosition().east(8).above(4);drivePos=base;waypointPos=base.south(5);robotPos=waypointPos.east(2);anchor=base.west(6);computerPos=anchor.above();
            oldCamera=client.options.getCameraType();oldClipboard=client.keyboardHandler.getClipboard();range=15*API.config.getDouble("client.soundVolume");volume=(float)(range/15+.5);
            labelsOnly=System.getProperty("ocfixture.effectsFocus","all").equals("labels");
        }
        ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing actual effects item "+name);return descriptor.createItemStack(1);}
        void remember(BlockPos pos){
            if(oldBlocks.containsKey(pos))return;
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos));level.getChunkAt(pos);
            require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Effects fixture refuses occupied cell "+pos);oldBlocks.put(pos.immutable(),level.getBlockState(pos));
        }
        void place(BlockPos pos,String name){remember(pos);var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null&&descriptor.block()!=null&&level.setBlock(pos,descriptor.block().defaultBlockState(),3),"Actual effects placement failed "+name);}
        ServerPlayer player(){return java.util.Objects.requireNonNull(server.getPlayerList().getPlayer(SUPPORTER),"Original entitled owned player is not connected");}
        int command(String text)throws Exception{return server.getCommands().getDispatcher().execute(text,player().createCommandSourceStack());}
        void task(ThrowingTask task){action=CompletableFuture.runAsync(()->{try{task.run();}catch(Exception error){throw new IllegalStateException(error);}},server);}
        boolean completed(){if(action==null||!action.isDone())return false;action.join();return true;}
        void start()throws Exception{
            require(range>1,"Effects sound check requires normal enabled volume");require(Files.isRegularFile(settingsPath),"Actual owned settings file is missing");
            oldSettings=Files.readAllBytes(settingsPath);flags(false,true);client.setScreen(null);
            if(labelsOnly){client.options.setCameraType(CameraType.FIRST_PERSON);task(()->{
                savePlayer();oldDayTime=level.getDayTime();oldDaylight=level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);timeCaptured=true;
                level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){BlockPos floor=robotPos.below().offset(x,0,z);remember(floor);level.setBlock(floor,Blocks.WHITE_CONCRETE.defaultBlockState(),3);}
                placeRobot();player().setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);labelCamera();
            });phase=100;return;}
            task(()->{
                place(drivePos,"diskdrive");place(waypointPos,"waypoint");drive=(Container)level.getBlockEntity(drivePos);
                remember(anchor);require(level.setBlock(anchor,Blocks.STONE.defaultBlockState(),3),"Could not place actual command target");
                for(int y=1;y<=3;y++)remember(anchor.above(y));
                ServerPlayer player=player();savePlayer();player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();
                player.connection.teleport(anchor.getX()+.5,anchor.getY()+4,anchor.getZ()+.5,0,90);
                require(command("oc_spawnComputer")==1,"Actual spawnComputer command failed its stone raycast");
                require(level.getBlockEntity(computerPos) instanceof Case,"Actual spawnComputer did not create the physical Case");
                host=(Case)level.getBlockEntity(computerPos);boolean cpu=false,hdd=false;int ram=0;
                for(int i=0;i<host.getContainerSize();i++){ItemStack stack=host.getItem(i);cpu|=stack.is(item("apucreative").getItem());if(stack.is(item("ram6").getItem()))ram+=stack.getCount();hdd|=stack.is(item("hdd3").getItem());}
                require(cpu&&ram==2&&hdd,"Spawn command lost its real configured hardware");
                install(item("debugcard"));install(item("wlancard1"));
                ItemStack hand=player.getMainHandItem().copy();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
                require(command("oc_prevdis true")==1&&player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBoolean("oc:undisassemblable"),"Actual disassembly command failed to mark held item");
                require(command("oc_prevdis false")==1&&!player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().contains("oc:undisassemblable"),"Actual disassembly command failed to clear held item");
                player.setItemInHand(InteractionHand.MAIN_HAND,hand);
                require(command("oc_dn")==1&&Nanomachines.getController(player)!=null&&Nanomachines.getController(player).getTotalInputCount()>0,"Actual nanomachine debug command did not install its real debug configuration");
                require(command("oc_nm")==1,"Actual nanomachine client-log command failed");
                require(command("oc_wlan true")==1&&Boolean.TRUE.equals(invoke("li.cil.oc.common.command.OriginalCommands","wirelessRendering",new Class<?>[0])),"Actual wireless debug command did not enable its renderer state");
                require(command("oc_wlan false")==1&&Boolean.FALSE.equals(invoke("li.cil.oc.common.command.OriginalCommands","wirelessRendering",new Class<?>[0])),"Actual wireless debug command did not disable its renderer state");
                placeRobot();
                robotObserver=new SignalObserver();Network.joinNewNetwork(robotObserver.node());robotObserver.node().connect(realRobot.machine().node());robotOutside=outside(realRobot);
                observer=new SignalObserver();Network.joinNewNetwork(observer.node());observer.node().connect(host.machine().node());
            });
        }
        void savePlayer(){var player=player();savedHand=player.getMainHandItem().copy();oldPosition=player.position();oldYaw=player.getYRot();oldPitch=player.getXRot();oldMayfly=player.getAbilities().mayfly;oldFlying=player.getAbilities().flying;oldNoGravity=player.isNoGravity();oldOnGround=player.onGround();}
        void placeRobot(){
            require(CreativeTab.instance!=null,"Configured robot preset unavailable");CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(),true,level.registryAccess()));
            ItemStack robot=null;for(ItemStack stack:CreativeTab.instance.getDisplayItems())if(stack.is(item("robot").getItem())&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("oc:tier")==3){robot=stack.copy();break;}
            require(robot!=null&&robot.getItem() instanceof BlockItem,"Original creative robot is unavailable");remember(robotPos);var player=player();ItemStack hand=player.getMainHandItem().copy();player.setItemInHand(InteractionHand.MAIN_HAND,robot);
            require(((BlockItem)robot.getItem()).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,robot,new BlockHitResult(Vec3.atCenterOf(robotPos),Direction.UP,robotPos,false))).consumesAction(),"Actual robot label fixture did not place");player.setItemInHand(InteractionHand.MAIN_HAND,hand);
            require(level.getBlockEntity(robotPos) instanceof Robot,"Actual label host is not a Robot");realRobot=(Robot)level.getBlockEntity(robotPos);realRobot.setName(labelsOnly?FOCUSED_LABEL:ROBOT_LABEL);
        }
        void labelCamera(){
            var player=player();labelEye=new Vec3(robotPos.getX()+.5,robotPos.getY()+(labelView==0?1.3:2.2),robotPos.getZ()+(labelView==0?3.7:.9));
            Vec3 delta=labelAnchor().subtract(labelEye);float yaw=(float)Math.toDegrees(-Math.atan2(delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
            player.connection.teleport(labelEye.x,labelEye.y-player.getEyeHeight(),labelEye.z,yaw,pitch);player.setOnGround(false);player.setNoGravity(true);player.setDeltaMovement(Vec3.ZERO);player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();
        }
        Vec3 labelAnchor(){return new Vec3(robotPos.getX()+.5,robotPos.getY()+1.3,robotPos.getZ()+.5);}
        boolean labelReady(boolean enabled)throws Exception{
            if(!(client.level.getBlockEntity(robotPos) instanceof Robot robot)||!FOCUSED_LABEL.equals(robot.name()))return false;
            Object settings=invoke("li.cil.oc.Settings","get",new Class<?>[0]);require(settings.getClass().getField("robotLabels").getBoolean(settings)==enabled,"Actual loaded robotLabels differs from requested value");
            return labelEye!=null&&client.gameRenderer.getMainCamera().getPosition().distanceToSqr(labelEye)<.0001&&robotFrames>renderStart&&!client.options.hideGui&&client.level.getDayTime()%24000==6000;
        }
        void labelPixels(NativeImage pixels)throws Exception{
            String view=labelView==0?"front":"overhead";
            results.add("OBS C22.label-frame view="+view+", enabled="+screenshot.endsWith("-on.png")+", actual_name="+FOCUSED_LABEL+", packed_light="+robotLight+", day_time="+client.level.getDayTime()+", fps="+client.getFps()+", camera_distance="+client.gameRenderer.getMainCamera().getPosition().distanceTo(labelAnchor()));
            if(screenshot.endsWith("-on.png")){
                if(labelOnImage!=null)labelOnImage.close();labelOnImage=new NativeImage(pixels.getWidth(),pixels.getHeight(),false);labelOnImage.copyFrom(pixels);return;
            }
            require(labelOnImage!=null&&labelOnImage.getWidth()==pixels.getWidth()&&labelOnImage.getHeight()==pixels.getHeight(),"Actual same-camera label frames have different framebuffer sizes");
            var fovMethod=client.gameRenderer.getClass().getDeclaredMethod("getFov",net.minecraft.client.Camera.class,float.class,boolean.class);fovMethod.setAccessible(true);
            double fov=((Number)fovMethod.invoke(client.gameRenderer,client.gameRenderer.getMainCamera(),1f,true)).doubleValue();
            double scale=pixels.getHeight()*(1.6/60)/(2*Math.tan(Math.toRadians(fov)/2)*client.gameRenderer.getMainCamera().getPosition().distanceTo(labelAnchor()));
            int width=client.font.width(FOCUSED_LABEL),changed=0,candidates=0,retained=0;var glyphs=new ArrayList<Integer>();
            for(int letter=0;letter<FOCUSED_LABEL.length();letter++){
                int left=(int)Math.ceil(pixels.getWidth()/2d+(client.font.width(FOCUSED_LABEL.substring(0,letter))-width/2d)*scale);
                int right=(int)Math.floor(pixels.getWidth()/2d+(client.font.width(FOCUSED_LABEL.substring(0,letter+1))-width/2d)*scale);
                int ink=0;
                for(int y=Math.max(0,pixels.getHeight()/2);y<Math.min(pixels.getHeight(),pixels.getHeight()/2+(int)(9*scale));y++)for(int x=Math.max(0,left);x<Math.min(pixels.getWidth(),right);x++){
                    if(Math.abs(x-pixels.getWidth()/2)<12&&Math.abs(y-pixels.getHeight()/2)<12)continue;
                    int on=labelOnImage.getPixelRGBA(x,y),off=pixels.getPixelRGBA(x,y),error=0,delta=0,lo=255,hi=0;
                    for(int shift:new int[]{0,8,16}){int a=on>>>shift&255,b=off>>>shift&255;error=Math.max(error,Math.abs(a-(b*127+127)/255));delta=Math.max(delta,Math.abs(a-b));lo=Math.min(lo,b);hi=Math.max(hi,b);}
                    if(error>18)ink++;if(delta>8)changed++;
                    // On dark neutral chassis texels, the translucent black plate must blend
                    // with that same body, rather than expose the bright floor behind it.
                    if(labelView==1&&lo>8&&hi<110&&hi-lo<35){candidates++;if(error<=9)retained++;}
                }
                glyphs.add(ink);require(ink>=Math.max(3,(int)(scale*scale*.5)),"Actual "+view+" label glyph is missing/clipped: "+FOCUSED_LABEL.charAt(letter)+", contrasting_pixels="+ink);
            }
            require(changed>200,"Actual label on/off pair has no visible nameplate change");
            if(labelView==1)require(candidates>100&&retained>candidates*.4,"Actual overhead nameplate did not retain the underlying chassis alpha blend: retained="+retained+", sampled_body="+candidates);
            results.add("PASS C22.label-pair view="+view+", contrasting_pixels_per_glyph="+glyphs+", changed_pixels="+changed+", body_alpha_matches="+retained+"/"+candidates+"; actual on/off PNGs require visual review");
            labelOnImage.close();labelOnImage=null;
        }
        void install(ItemStack stack){for(int slot=0;slot<host.getContainerSize();slot++)if(host.getItem(slot).isEmpty()&&host.canPlaceItem(slot,stack)){host.setItem(slot,stack);return;}throw new AssertionError("Actual Case rejected effects hardware "+stack);}
        void flags(boolean hidden,boolean labels)throws Exception{
            var config=ConfigFactory.parseString(new String(oldSettings,StandardCharsets.UTF_8))
                .withValue("opencomputers.misc.hideOwnSpecial",ConfigValueFactory.fromAnyRef(hidden))
                .withValue("opencomputers.client.robotLabels",ConfigValueFactory.fromAnyRef(labels));
            Files.writeString(settingsPath,config.root().render(ConfigRenderOptions.defaults().setJson(false)),StandardCharsets.UTF_8);
            invoke("li.cil.oc.Settings","load",new Class<?>[]{Path.class},settingsPath);
        }
        void clipboardTests()throws Exception{
            client.player.connection.sendCommand("oc_setclipboard effects-client ignored");
            require(client.keyboardHandler.getClipboard().equals("effects-client"),"Actual client command did not retain its first argument");
            String text="C".repeat(1000);client.keyboardHandler.setClipboard(text);
            require(text.equals(invoke("li.cil.oc.client.ClipboardInput","read",new Class<?>[0])),"Real clipboard input rejected its valid whole paste");
            require(invoke("li.cil.oc.client.ClipboardInput","read",new Class<?>[0])==null,"Real shared clipboard cooldown allowed immediate second paste");
            int limit=(int)API.config.getDouble("misc.maxClipboard");client.keyboardHandler.setClipboard("C".repeat(limit+1));
            require(invoke("li.cil.oc.client.ClipboardInput","read",new Class<?>[0])==null,"Real clipboard accepted an over-limit paste");
            require(clipboardBeeps>=2,"Real clipboard rejection did not use native harp sound events");
            ItemStack picker=item("texturepicker");client.player.setItemInHand(InteractionHand.MAIN_HAND,picker);
            require(picker.getItem().onItemUseFirst(picker,new UseOnContext(client.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(anchor),Direction.UP,anchor,false))).consumesAction(),"Actual texture picker rejected the real stone model");
            require(chatHas("oc:gui.Chat.TextureName"),"Actual texture picker did not print the model texture through native player chat");
            results.add("PASS C22.clipboard actual client command, whole-paste limit/shared cooldown/native feedback and real texture-picker model/chat entry");
        }
        Component debug(){
            for(Node node:host.machine().node().reachableNodes())if(node instanceof Component component&&component.name().equals("debug"))return component;return null;
        }
        double outside(li.cil.oc.api.machine.MachineHost host)throws Exception{
            for(Node node:host.machine().node().network().nodes())if(node.host() instanceof li.cil.oc.api.internal.TextBuffer&&node instanceof Component screen)
                return Boolean.TRUE.equals(screen.invoke("isPrecise",host.machine())[0])?-1:0;
            throw new AssertionError("Actual GUI host has no installed screen");
        }
        void released(SignalObserver observer,double outside,String host){
            require(observer.down!=null,host+" actual GUI press did not reach its real TextBuffer touch signal");
            require(observer.up!=null,host+" actual GUI outside release did not reach its real TextBuffer drop signal");
            require(((Number)observer.up[2]).doubleValue()==outside,host+" outside mouse-up X differs: "+java.util.Arrays.toString(observer.up));
            require(((Number)observer.up[3]).doubleValue()==outside,host+" outside mouse-up Y differs: "+java.util.Arrays.toString(observer.up));
            require(((Number)observer.up[4]).intValue()==0,host+" outside mouse-up lost its actual left button");
        }
        double glyph(String method)throws Exception{return ((Number)invoke("li.cil.oc.client.renderer.ScreenTextRenderer",method,new Class<?>[0])).doubleValue();}
        void barrier(String text)throws Exception{Object[] result=debug.invoke("sendToClipboard",host.machine(),player().getGameProfile().getName(),text);require(result!=null&&Boolean.TRUE.equals(result[0]),"Actual debug card failed its normal clipboard payload");}
        void disk(String barrier,boolean far)throws Exception{
            Vec3 center=Vec3.atCenterOf(drivePos);player().connection.teleport(center.x+(far?range+1:range-1),center.y,center.z,90,0);
            if(drive.getItem(0).isEmpty())drive.setItem(0,item("floppy"));else{drive.removeItem(0,1);drive.setItem(0,item("floppy"));}
            if(barrier.equals("effects-disk-first")){drive.removeItem(0,1);drive.setItem(0,item("floppy"));}
            nextDiskAt=System.currentTimeMillis()+550;barrier(barrier);
        }
        int sounds(String name){return (int)diskSounds.stream().filter(sound->sound.getLocation().equals(id(name))).count();}
        void diskFacts(){for(SoundInstance sound:diskSounds)require(sound.getSource()==SoundSource.BLOCKS&&Math.abs(sound.getVolume()-volume)<1e-6,"Actual disk sound category/range-derived volume differs: "+sound.getVolume());}
        void aim(Vec3 eye,Vec3 point){
            Vec3 delta=point.subtract(eye);float yaw=(float)Math.toDegrees(-Math.atan2(delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
            client.player.getAbilities().flying=true;client.player.setDeltaMovement(Vec3.ZERO);client.player.setPos(eye.x,eye.y-client.player.getEyeHeight(),eye.z);
            client.player.setYRot(yaw);client.player.yRotO=yaw;client.player.setYHeadRot(yaw);client.player.yHeadRotO=yaw;client.player.setXRot(pitch);client.player.xRotO=pitch;
        }
        int portalParticles()throws Exception{
            Map<?,?> particles=(Map<?,?>)read(ParticleEngine.class,client.particleEngine,"particles");int count=0;
            for(Object group:particles.values())for(Object value:(Collection<?>)group)if(value instanceof PortalParticle particle&&particle.isAlive()&&particle.getBoundingBox().intersects(new AABB(waypointPos).inflate(1.5)))count++;
            return count;
        }
        boolean hiddenClient()throws Exception{return ((Set<?>)read(Class.forName("li.cil.oc.client.renderer.PetRenderer"),null,"HIDDEN")).contains(SUPPORTER);}
        void screenshot(String name){screenshot=name;shotDone=false;shotAfter=frame+(labelsOnly?24:4);}
        boolean chatHas(String key)throws Exception{
            for(Object message:(List<?>)read(ChatComponent.class,client.gui.getChat(),"allMessages"))if(message instanceof GuiMessage line&&contains(line.content(),key))return true;return false;
        }
        boolean contains(net.minecraft.network.chat.Component text,String key){ComponentContents value=text.getContents();if(value instanceof TranslatableContents translation&&translation.getKey().equals(key))return true;return text.getSiblings().stream().anyMatch(child->contains(child,key));}
        void updateCheck()throws Exception{
            Path path=client.gameDirectory.toPath().resolve("cache/opencomputers/update-check.json");require(Files.isRegularFile(path),"Normal login's actual GitHub request produced no cache; inspect the update-check network log");
            JsonObject cache=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            require(cache.get("schema").getAsInt()==1&&cache.get("repository").getAsString().equals("Kush-ShuL/OpenComputer-Reloaded"),"Actual update cache belongs to another protocol/repository");
            long processStart=ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli();
            require(cache.get("checkedAt").getAsLong()>=processStart,"Update check reused data from before this game JVM");
            require(cache.get("release").isJsonNull(),"Official release selection changed; inspect the actual eligible full-mod release before changing this representative expectation");
            require(!chatHas("oc:gui.Chat.NewVersion"),"An actual SDK-only release caused a playable-mod update hint");
            results.add("PASS C22.update normal real-player login fetched official GitHub releases and wrote a fresh actual cache; no eligible playable-mod release or SDK update hint, existing release filtering retained");
        }
        void tick()throws Exception{
            require(System.nanoTime()-started<TimeUnit.MINUTES.toNanos(4),"Effects contract timed out phase="+phase+", insert="+sounds("floppy_insert")+", eject="+sounds("floppy_eject"));
            if(finishing)return;
            switch(phase){
                case 0->{
                    if(!completed()||!client.level.getBlockState(anchor).is(Blocks.STONE)||client.level.getBlockEntity(robotPos)==null)return;
                    clipboardTests();task(()->{
                        debug=debug();require(debug!=null,"Actual spawned Case lost its installed Debug Card");
                        require(command("oc_sdbg "+debug.address()+" effects 21")==1,"Actual debug-message command found no real endpoint");
                        require(observer.debug!=null&&observer.debug.length==6&&observer.debug[4].equals("effects")&&observer.debug[5].equals("21"),"Actual Debug Card did not forward the command packet into its real OC graph");
                        barrier(DEBUG_CLIPBOARD);
                    });phase=1;
                }
                case 1->{
                    if(!completed()||!client.keyboardHandler.getClipboard().equals(DEBUG_CLIPBOARD))return;
                    require(Nanomachines.getController(client.player)!=null,"Actual nanomachine command configuration never reached the client");
                    results.add("PASS C22.commands actual server spawnComputer/nodis/wireless/nanomachine commands and Debug Card message/clipboard payloads reach the connected native client");
                    task(()->disk("effects-disk-first",false));phase=2;
                }
                case 2->{
                    if(!completed()||!client.keyboardHandler.getClipboard().equals("effects-disk-first"))return;
                    require(sounds("floppy_insert")==1&&sounds("floppy_eject")==1,"Same-host 500ms cooldown or independent disk sound names failed: insert="+sounds("floppy_insert")+", eject="+sounds("floppy_eject"));diskFacts();
                    if(System.currentTimeMillis()<nextDiskAt)return;task(()->disk("effects-disk-second",false));phase=3;
                }
                case 3->{
                    if(!completed()||!client.keyboardHandler.getClipboard().equals("effects-disk-second"))return;
                    require(sounds("floppy_insert")==2&&sounds("floppy_eject")==2,"Actual disk sounds did not resume after the real 500ms cooldown");diskFacts();
                    if(System.currentTimeMillis()<nextDiskAt)return;task(()->disk("effects-disk-far",true));phase=4;
                }
                case 4->{
                    if(!completed()||!client.keyboardHandler.getClipboard().equals("effects-disk-far"))return;
                    require(sounds("floppy_insert")==2&&sounds("floppy_eject")==2,"Actual disk sound crossed the configured server broadcast range");
                    results.add("PASS C22.disk real insertion/ejection and native PlaySound events preserve independent 500ms host cooldown, range "+range+", category and volume "+volume+"; FIFO debug payload confirms out-of-range delivery exclusion");
                    client.options.setCameraType(CameraType.FIRST_PERSON);aim(Vec3.atCenterOf(waypointPos).add(0,1,4),Vec3.atCenterOf(robotPos));renderStart=robotFrames;phase=5;
                }
                case 5->{
                    if(portalParticles()==0||robotFrames<=renderStart)return;
                    results.add("PASS C22.particles actual waypoint client ticker creates live native PortalParticle instances at its real world position; actual named Robot enters the native renderer");
                    screenshot("effects-particles-label-on.png");phase=6;
                }
                case 6->{
                    if(!shotDone)return;client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);renderStart=petFrames;phase=7;
                }
                case 7->{
                    if(petFrames<=renderStart||hiddenClient())return;
                    results.add("PASS C22.pet original entitled real player reaches the actual RenderPlayer/PetRenderer/RobotRenderEvent path with original RGB tint");
                    screenshot("effects-pet-visible.png");phase=8;
                }
                case 8->{
                    if(!shotDone)return;flags(true,false);hiddenStart=petFrames;
                    task(()->serverHidden=((Set<?>)read(Class.forName("li.cil.oc.common.network.PetNetworking"),null,"HIDDEN")).contains(SUPPORTER));phase=9;
                }
                case 9->{
                    if(!completed()||!hiddenClient())return;
                    if(!serverHidden){task(()->serverHidden=((Set<?>)read(Class.forName("li.cil.oc.common.network.PetNetworking"),null,"HIDDEN")).contains(SUPPORTER));return;}
                    require(petFrames==hiddenStart,"Actual hideOwnSpecial setting left the real pet rendering");
                    results.add("PASS C22.pet actual HOCON reload hides own supporter pet; normal visibility payload updates both client and server state without replacing renderer lists");
                    screenshot("effects-pet-hidden.png");phase=10;
                }
                case 10->{
                    if(!shotDone)return;client.options.setCameraType(CameraType.FIRST_PERSON);aim(Vec3.atCenterOf(waypointPos).add(0,1,4),Vec3.atCenterOf(robotPos));renderStart=robotFrames;phase=11;
                }
                case 11->{
                    if(robotFrames<=renderStart)return;screenshot("effects-label-off.png");phase=12;
                }
                case 12->{
                    if(!shotDone)return;
                    results.add("PASS C22.display real robotLabels setting reload and visible/hidden rendered frames; original configuration/clipboard/camera restored on cleanup");
                    task(()->{
                        ServerPlayer player=player();player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                        Vec3 center=Vec3.atCenterOf(robotPos);player.connection.teleport(center.x,center.y,center.z+2,180,0);
                        require(player.gameMode.useItemOn(player,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,new BlockHitResult(center,Direction.SOUTH,robotPos,false)).consumesAction(),"Actual robot block interaction did not open its real menu");
                    });phase=13;
                }
                case 13->{
                    if(!completed()||client.screen==null||!client.screen.getClass().getName().equals("li.cil.oc.client.gui.AgentScreen"))return;
                    var screen=client.screen;double scale=((Number)read(screen.getClass(),screen,"textScale")).doubleValue();if(scale<=0)return;
                    double x=((Number)read(screen.getClass(),screen,"textX")).doubleValue()+scale*glyph("characterWidth")*.5;
                    double y=((Number)read(screen.getClass(),screen,"textY")).doubleValue()+scale*glyph("characterHeight")*.5;
                    require(screen.mouseClicked(x,y,0),"Actual robot GUI did not accept an inside-buffer press");
                    require(screen.mouseReleased(-4,-4,0),"Actual robot GUI did not handle outside release");phase=14;
                }
                case 14->{
                    if(robotObserver.up==null)return;released(robotObserver,robotOutside,"Robot");
                    results.add("PASS G3.robot actual block menu opens AgentScreen; inside press then outside release traverses the real payload and server authorization into TextBuffer drop with original outside coordinates/button");
                    client.screen.onClose();task(()->{
                        ItemStack stack=null;
                        for(ItemStack preset:CreativeTab.instance.getDisplayItems())if(preset.is(item("tablet").getItem())&&preset.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("oc:tier")==3){stack=preset.copy();break;}
                        require(stack!=null,"Actual configured tablet preset unavailable");ServerPlayer player=player();player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                        Object actual=invoke("li.cil.oc.server.portable.TabletManager","get",new Class<?>[]{ItemStack.class,net.minecraft.world.entity.player.Player.class},stack,player);
                        require(actual instanceof Tablet,"Actual held tablet did not create its SDK host");realTablet=(Tablet)actual;
                        tabletObserver=new SignalObserver();Network.joinNewNetwork(tabletObserver.node());tabletObserver.node().connect(realTablet.machine().node());tabletOutside=outside(realTablet);
                        require(stack.getItem().use(level,player,InteractionHand.MAIN_HAND).getResult().consumesAction(),"Actual held tablet use failed");player.releaseUsingItem();
                    });phase=15;
                }
                case 15->{
                    if(!completed()||client.screen==null||!client.screen.getClass().getName().equals("li.cil.oc.client.gui.TabletScreen"))return;
                    var screen=client.screen;double scale=((Number)read(screen.getClass(),screen,"scale")).doubleValue();if(scale<=0)return;
                    double x=((Number)read(screen.getClass(),screen,"left")).doubleValue()+scale*(8+glyph("characterWidth")*.5);
                    double y=((Number)read(screen.getClass(),screen,"top")).doubleValue()+scale*(8+glyph("characterHeight")*.5);
                    require(screen.mouseClicked(x,y,0),"Actual held-tablet GUI did not accept inside press");
                    require(screen.mouseReleased(-4,-4,0),"Actual held-tablet GUI did not handle outside release");phase=16;
                }
                case 16->{
                    if(tabletObserver.up==null)return;released(tabletObserver,tabletOutside,"Tablet");
                    results.add("PASS G3.tablet actual held item short-use opens its normal TabletScreen session; outside GUI release traverses the real payload/session into TextBuffer drop with original outside coordinates/button");
                    client.screen.onClose();phase=17;
                }
                case 17->{
                    Object future=read(Class.forName("li.cil.oc.common.integration.UpdateChecker"),null,"result");
                    if(future instanceof CompletableFuture<?> request&&!request.isDone())return;
                    updateCheck();finish();
                }
                case 100->{if(!completed()||!labelReady(true))return;results.add("PASS C22.label-state real loaded robotLabels=true, actual ClientLevel Robot.name and frozen noon match before native world frames");screenshot("effects-label-front-on.png");phase=101;}
                case 101->{if(!shotDone)return;flags(false,false);renderStart=robotFrames;task(this::labelCamera);phase=102;}
                case 102->{if(!completed()||!labelReady(false))return;screenshot("effects-label-front-off.png");phase=103;}
                case 103->{if(!shotDone)return;labelView=1;flags(false,true);renderStart=robotFrames;task(this::labelCamera);phase=104;}
                case 104->{if(!completed()||!labelReady(true))return;screenshot("effects-label-overhead-on.png");phase=105;}
                case 105->{if(!shotDone)return;flags(false,false);renderStart=robotFrames;task(this::labelCamera);phase=106;}
                case 106->{if(!completed()||!labelReady(false))return;screenshot("effects-label-overhead-off.png");phase=107;}
                case 107->{if(!shotDone)return;if(labelErrors.isEmpty())results.add("PASS C22.labels actual front and close-overhead label on/off pairs preserve glyph contrast and underlying chassis; prior sound/command/pet/update results reused");else for(String error:labelErrors)results.add("FAIL C22.label-pixels "+error);results.add("LIMIT C22.labels: four native PNGs still require visual review for full text and opaque model faces");finish();}
                default->throw new AssertionError("Unknown effects client phase "+phase);
            }
        }
        void fail(Throwable error){if(finishing)return;results.add("FAIL C22.effects phase="+phase+": "+error);finish();}
        void finish(){
            if(finishing)return;finishing=true;
            if(labelOnImage!=null){labelOnImage.close();labelOnImage=null;}
            try{if(oldSettings!=null){Files.write(settingsPath,oldSettings);invoke("li.cil.oc.Settings","load",new Class<?>[]{Path.class},settingsPath);}}catch(Exception error){results.add("FAIL C22.effects settings cleanup: "+error);}
            client.keyboardHandler.setClipboard(oldClipboard);client.options.setCameraType(oldCamera);
            cleanup=CompletableFuture.runAsync(()->{
                if(observer!=null)observer.node().remove();if(robotObserver!=null)robotObserver.node().remove();if(tabletObserver!=null)tabletObserver.node().remove();if(realTablet!=null)realTablet.machine().stop();if(host!=null)host.machine().stop();
                if(savedHand!=null&&server.getPlayerList().getPlayer(SUPPORTER)!=null)player().setItemInHand(InteractionHand.MAIN_HAND,savedHand);
                for(var entry:oldBlocks.entrySet()){if(level.getBlockEntity(entry.getKey()) instanceof Container inventory)inventory.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}
                for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);
                if(timeCaptured){level.setDayTime(oldDayTime);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(oldDaylight,server);}
                if(oldPosition!=null){var player=player();player.connection.teleport(oldPosition.x,oldPosition.y,oldPosition.z,oldYaw,oldPitch);player.setDeltaMovement(Vec3.ZERO);player.setNoGravity(oldNoGravity);player.setOnGround(oldOnGround);player.getAbilities().mayfly=oldMayfly;player.getAbilities().flying=oldFlying;player.onUpdateAbilities();}
            },server);
        }
    }
    @FunctionalInterface private interface ThrowingTask{void run()throws Exception;}
}
