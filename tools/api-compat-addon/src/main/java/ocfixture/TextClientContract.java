package ocfixture;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValueFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import li.cil.oc.api.API;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Rotatable;
import li.cil.oc.api.internal.TextBuffer;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.opengl.GL11;

/** Real GPU/state packets and normal GUI/world text rendered with the original bundled fonts. */
@OnlyIn(Dist.CLIENT)
public final class TextClientContract {
    private static Run active;
    private static boolean registered;
    private TextClientContract(){}
    public static void start(Minecraft client,Path directory,List<String> results){
        require(active==null,"Text client contract already running");
        require(client.player!=null&&client.level!=null&&client.hasSingleplayerServer(),"Text contract requires its owned integrated world");
        if(!registered){registered=true;NeoForge.EVENT_BUS.addListener(TextClientContract::serverTick);NeoForge.EVENT_BUS.addListener(TextClientContract::rendered);}
        Run run=new Run(client,directory,results);active=run;
        try{run.start();}catch(Throwable error){run.fail(error);}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){
        Run run=active;if(run==null)return;
        try{run.tick();}catch(Throwable error){run.fail(error);}
        if(run.finishing&&run.cleanup!=null&&run.cleanup.isDone()&&run.restoreReload!=null&&run.restoreReload.isDone()){
            try{run.cleanup.join();run.restoreReload.join();}catch(Throwable error){run.results.add("FAIL C21.text cleanup: "+error);}active=null;
        }
    }
    public static void close(){Run run=active;if(run!=null&&!run.finishing)run.fail(new AssertionError("Text contract closed before completion"));}
    private static void serverTick(ServerTickEvent.Post event){
        Run run=active;if(run==null||run.finishing||run.host==null||run.server!=event.getServer())return;
        try{Node node=run.host.machine().node();if(node instanceof li.cil.oc.api.network.Connector energy)energy.changeBuffer(Double.POSITIVE_INFINITY);}
        catch(Throwable error){run.serverError=error;}
    }
    private static void rendered(RenderFrameEvent.Post event){
        Run run=active;if(run==null||run.finishing)return;run.frame++;
        if(run.shot==null||run.frame<=run.shotAfter+1||run.client.getOverlay()!=null)return;
        try(NativeImage image=Screenshot.takeScreenshot(run.client.getMainRenderTarget())){
            Path path=run.directory.resolve(run.shot);image.writeToFile(path);
            run.results.add("SCREENSHOT C21 "+path+" "+image.getWidth()+"x"+image.getHeight()+" normal "+(run.shotGui?"screen GUI":"world screen")+" frame="+run.frame);
            if(run.shotGui)run.pixels(image);else run.worldPixels(image);
            run.shot=null;run.shotDone=true;
        }catch(Throwable error){run.fail(error);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object invoke(Object target,String method,Class<?>[] types,Object... args)throws Exception{return target.getClass().getMethod(method,types).invoke(target,args);}
    private static Object read(Object target,String name)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static final class Snapshot {
        final int w,h,vw,vh;final TextBuffer.ColorDepth depth;final int[][] text,fg,bg;final boolean[][] fp,bp;final int[] palette=new int[16];
        Snapshot(TextBuffer buffer){
            w=buffer.getWidth();h=buffer.getHeight();vw=buffer.getViewportWidth();vh=buffer.getViewportHeight();depth=buffer.getColorDepth();
            text=new int[h][w];fg=new int[h][w];bg=new int[h][w];fp=new boolean[h][w];bp=new boolean[h][w];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){text[y][x]=buffer.getCodePoint(x,y);fg[y][x]=buffer.getForegroundColor(x,y);bg[y][x]=buffer.getBackgroundColor(x,y);fp[y][x]=buffer.isForegroundFromPalette(x,y);bp[y][x]=buffer.isBackgroundFromPalette(x,y);}
            for(int i=0;i<16;i++)palette[i]=buffer.getPaletteColor(i);
        }
        boolean matches(TextBuffer buffer){
            if(buffer.getWidth()!=w||buffer.getHeight()!=h||buffer.getViewportWidth()!=vw||buffer.getViewportHeight()!=vh||buffer.getColorDepth()!=depth)return false;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(buffer.getCodePoint(x,y)!=text[y][x]||buffer.getForegroundColor(x,y)!=fg[y][x]||buffer.getBackgroundColor(x,y)!=bg[y][x]||buffer.isForegroundFromPalette(x,y)!=fp[y][x]||buffer.isBackgroundFromPalette(x,y)!=bp[y][x])return false;
            for(int i=0;i<16;i++)if(buffer.getPaletteColor(i)!=palette[i])return false;return true;
        }
    }
    private static final class Run {
        final Minecraft client;final MinecraftServer server;final ServerLevel level;final Path directory,settingsPath;final List<String> results;
        final BlockPos casePos,screenPos,keyboardPos,supportPos;final Map<BlockPos,BlockState> oldBlocks=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        final long started=System.nanoTime();final CameraType oldCamera;final boolean worldOnly;byte[] settings;Case host;BlockEntity serverScreen;TextBuffer serverBuffer,firstClientBuffer;Component gpu;
        volatile Throwable serverError;volatile Snapshot expected;CompletableFuture<Void> action,cleanup,restoreReload,reload;
        int phase,frame,shotAfter,page;long beforeRevision;String owner,shot;boolean shotDone,shotGui,finishing,textureFont;
        Vec3 oldPosition,worldEye;float oldYaw,oldPitch;boolean oldFlying,oldMayfly,oldNoGravity,oldOnGround;ItemStack oldMainHand;
        Run(Minecraft client,Path directory,List<String> results){
            this.client=client;this.server=client.getSingleplayerServer();this.level=server.getLevel(client.level.dimension());this.directory=directory;this.results=results;
            settingsPath=client.gameDirectory.toPath().resolve("config/opencomputers/settings.conf");
            oldCamera=client.options.getCameraType();
            worldOnly=System.getProperty("ocfixture.textFocus","all").equals("world");
            casePos=client.player.blockPosition().east(8).above(4);screenPos=casePos.above();keyboardPos=screenPos.east();supportPos=casePos.east();
        }
        ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing actual text hardware "+name);return descriptor.createItemStack(1);}
        void remember(BlockPos pos){if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos));level.getChunkAt(pos);require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Text fixture refuses occupied cell "+pos);oldBlocks.put(pos,level.getBlockState(pos));}
        void place(BlockPos pos,String name){remember(pos);var info=li.cil.oc.api.Items.get(name);require(info!=null&&info.block()!=null&&level.setBlock(pos,info.block().defaultBlockState(),3),"Could not place actual "+name);}
        void task(Task work){action=CompletableFuture.runAsync(()->{try{work.run();}catch(Exception error){throw new IllegalStateException(error);}},server);}
        boolean complete(){if(action==null||!action.isDone())return false;action.join();return true;}
        ServerPlayer player(){return java.util.Objects.requireNonNull(server.getPlayerList().getPlayer(client.player.getUUID()),"Actual text player is not connected");}
        void start()throws Exception{
            settings=Files.readAllBytes(settingsPath);font("hexfont",true,false);client.options.setCameraType(CameraType.FIRST_PERSON);
            task(()->{
                var player=player();oldPosition=player.position();oldYaw=player.getYRot();oldPitch=player.getXRot();oldFlying=player.getAbilities().flying;oldMayfly=player.getAbilities().mayfly;oldNoGravity=player.isNoGravity();oldOnGround=player.onGround();oldMainHand=player.getMainHandItem().copy();
                place(casePos,"case3");place(screenPos,"screen3");remember(supportPos);require(level.setBlock(supportPos,Blocks.STONE.defaultBlockState(),3),"Could not place real keyboard support");place(keyboardPos,"keyboard");
                require(level.getBlockEntity(casePos) instanceof Case,"Actual text host is not an SDK Case");host=(Case)level.getBlockEntity(casePos);serverScreen=level.getBlockEntity(screenPos);
                serverBuffer=(TextBuffer)invoke(serverScreen,"buffer",new Class<?>[0]);install(item("cpu3"));install(item("ram6"));install(item("graphicscard3"));
                player.setNoGravity(true);
            });
        }
        void install(ItemStack stack){for(int slot=0;slot<host.getContainerSize();slot++)if(host.getItem(slot).isEmpty()&&host.canPlaceItem(slot,stack)){host.setItem(slot,stack);return;}throw new AssertionError("Actual Case rejected "+stack);}
        Component gpu(){
            if(host.machine().node().network()==null)return null;
            for(Node node:host.machine().node().reachableNodes())if(node instanceof Component component&&component.name().equals("gpu"))return component;return null;
        }
        Object[] call(String name,Object... args)throws Exception{return gpu.invoke(name,host.machine(),args);}
        void successful(String name,Object... args)throws Exception{Object[] values=call(name,args);require(values!=null&&values.length>0&&Boolean.TRUE.equals(values[0]),"Actual GPU "+name+" failed: "+java.util.Arrays.toString(values));}
        void scene()throws Exception{
            owner=gpu.address();successful("bind",((Environment)serverScreen).node().address(),false);call("setDepth",8);successful("setResolution",32,12);successful("setViewport",24,10);
            call("setBackground",0);call("setForeground",0xFFFFFF);successful("fill",1,1,32,12," ");
            successful("set",1,1,"ASCII 0123 ABC xyz");successful("set",1,2,"Unicode é Ω ñ");successful("set",1,3,"中A文B");
            successful("set",22,2,"VERT",true);successful("set",1,4,"COPY");successful("copy",1,4,4,1,0,1);
            call("setForeground",0x00FF00);successful("fill",1,6,7,1,"█");call("setForeground",0xFFFFFF);
            serverBuffer.rawSetText(17,3,new int[][]{{'R','A','W'},{0x4E2D,' ','X'}});
            serverBuffer.rawSetForeground(17,3,new int[][]{{0x00FF00,0xFF0000,0x0000FF},{0xFFFFFF,0xFFFFFF,0xFFFFFF}});
            serverBuffer.rawSetBackground(17,3,new int[][]{{0,0,0},{0x000080,0x000080,0x000080}});
            int[] colors={0xFF0000,0x00FF00,0x0000FF};for(int i=0;i<colors.length;i++){call("setBackground",colors[i]);successful("fill",1+i*4,9,4,1," ");}
            call("setPaletteColor",3,0x55AAFF);call("setPaletteColor",4,0x224466);call("setBackground",4,true);successful("fill",13,9,4,1," ");
            call("setForeground",3,true);successful("set",1,10,"PALETTE");call("setBackground",0);call("setForeground",0xFFFFFF);successful("set",1,11,"HIDDEN VIEWPORT");
            expected=new Snapshot(serverBuffer);
        }
        void camera()throws Exception{
            var player=player();Direction face=((Rotatable)serverScreen).facing();Vec3 center=Vec3.atCenterOf(screenPos),eye=center.add(Vec3.atLowerCornerOf(face.getNormal()).scale(2.8));
            Vec3 delta=center.subtract(eye);float yaw=(float)Math.toDegrees(-Math.atan2(delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
            player.connection.teleport(eye.x,eye.y-player.getEyeHeight(),eye.z,yaw,pitch);player.setOnGround(false);player.setDeltaMovement(Vec3.ZERO);player.setNoGravity(true);player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);worldEye=eye;
        }
        boolean cameraReady(){
            return worldEye!=null&&client.gameRenderer.getMainCamera().getPosition().distanceToSqr(worldEye)<.0001&&client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(screenPos);
        }
        void open()throws Exception{
            camera();var player=player();Direction face=((Rotatable)serverScreen).facing();Vec3 center=Vec3.atCenterOf(screenPos);
            require(player.gameMode.useItemOn(player,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,new BlockHitResult(center,face,screenPos,false)).consumesAction(),"Actual screen interaction did not open its normal GUI");
        }
        TextBuffer clientBuffer()throws Exception{
            BlockEntity entity=client.level.getBlockEntity(screenPos);return entity==null?null:(TextBuffer)invoke(entity,"buffer",new Class<?>[0]);
        }
        long revision()throws Exception{return ((Number)invoke(client.level.getBlockEntity(screenPos),"revision",new Class<?>[0])).longValue();}
        boolean readyClient()throws Exception{TextBuffer buffer=clientBuffer();return buffer!=null&&expected!=null&&buffer.isRenderingEnabled()&&expected.matches(buffer);}
        java.util.Optional<?> cachedPage()throws Exception{return (java.util.Optional<?>)invoke(clientBuffer(),"getBuffer",new Class<?>[]{String.class,int.class},owner,page);}
        void font(String renderer,boolean aa,boolean linear)throws Exception{
            var config=ConfigFactory.parseString(new String(settings,StandardCharsets.UTF_8))
                .withValue("opencomputers.client.fontRenderer",ConfigValueFactory.fromAnyRef(renderer))
                .withValue("opencomputers.client.textAntiAlias",ConfigValueFactory.fromAnyRef(aa))
                .withValue("opencomputers.client.textLinearFiltering",ConfigValueFactory.fromAnyRef(linear));
            Files.writeString(settingsPath,config.root().render(ConfigRenderOptions.defaults().setJson(false)),StandardCharsets.UTF_8);
            Class.forName("li.cil.oc.Settings").getMethod("load",Path.class).invoke(null,settingsPath);textureFont=renderer.equals("texture");
        }
        int glyph(String name)throws Exception{return ((Number)Class.forName("li.cil.oc.client.renderer.ScreenTextRenderer").getMethod(name).invoke(null)).intValue();}
        void screenshot(String name,boolean gui){shot=name;shotGui=gui;shotDone=false;shotAfter=frame;}
        int rgb(NativeImage image,int x,int y){int rgba=image.getPixelRGBA(x,y);return (rgba&255)<<16|rgba&0xFF00|(rgba>>>16)&255;}
        // Vanilla's FULL_BRIGHT lightmap maps 255 to 252 in the native text shader.
        boolean colorMatches(int actual,int expected){return Math.abs((actual>>>16&255)-(expected>>>16&255))<=3&&Math.abs((actual>>>8&255)-(expected>>>8&255))<=3&&Math.abs((actual&255)-(expected&255))<=3;}
        int colored(NativeImage image,int column,int row,int count,int color)throws Exception{
            Screen screen=client.screen;double scale=((Number)read(screen,"scale")).doubleValue(),left=((Number)read(screen,"left")).doubleValue(),top=((Number)read(screen,"top")).doubleValue();
            double sx=(double)image.getWidth()/client.getWindow().getGuiScaledWidth(),sy=(double)image.getHeight()/client.getWindow().getGuiScaledHeight();
            int x0=(int)Math.ceil((left+(8+column*glyph("characterWidth"))*scale)*sx)+2,x1=(int)Math.floor((left+(8+(column+count)*glyph("characterWidth"))*scale)*sx)-2;
            int y0=(int)Math.ceil((top+(8+row*glyph("characterHeight"))*scale)*sy)+2,y1=(int)Math.floor((top+(8+(row+1)*glyph("characterHeight"))*scale)*sy)-2,found=0;
            for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++)if(colorMatches(rgb(image,x,y),color))found++;return found;
        }
        void pixels(NativeImage image)throws Exception{
            require(client.screen!=null&&client.screen.getClass().getName().equals("li.cil.oc.client.gui.ScreenGui"),"Text screenshot is not the actual screen GUI");
            require(glyph("characterWidth")==(textureFont?5:4)&&glyph("characterHeight")==(textureFont?9:8),"Actual original font cell dimensions changed");
            int[] colors={0xFF0000,0x00FF00,0x0000FF,0x224466};for(int i=0;i<colors.length;i++)require(colored(image,i*4,8,4,colors[i])>30,"Actual shader did not draw screen background color "+Integer.toHexString(colors[i]));
            int wideInk=colored(image,1,2,1,0xFFFFFF);
            require(textureFont?wideInk==0:wideInk>0,"Actual original "+(textureFont?"CP437 fallback":"Unicode double-width")+" glyph pixels do not match their two-cell behavior: "+wideInk);
            ResourceLocation font=ResourceLocation.fromNamespaceAndPath("opencomputers",textureFont?"textures/font/chars_aliased.png":"dynamic/screen_font_0");
            int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),min,mag;
            try{GlStateManager._bindTexture(client.getTextureManager().getTexture(font).getId());min=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER);mag=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER);}
            finally{GlStateManager._bindTexture(previous);}
            require(min==(textureFont?GL11.GL_LINEAR:GL11.GL_NEAREST)&&mag==GL11.GL_NEAREST,"Actual font MIN/MAG filters differ from configuration");
            results.add("PASS C21.pixels actual "+(textureFont?"CP437 aliased/linear-MIN":"Unifont/nearest")+" frame retains RGB/palette backgrounds, CJK two-cell behavior and native texture filters");
        }
        void worldPixels(NativeImage image)throws Exception{
            require(client.screen==null,"World-text screenshot still has a GUI");
            int width=image.getWidth(),height=image.getHeight(),best=0,bestWidth=0,bestHeight=0;
            boolean[] visited=new boolean[width*height];int[] queue=new int[width*height];
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                int origin=y*width+x;if(visited[origin]||!colorMatches(rgb(image,x,y),0xFF0000))continue;
                int head=0,tail=1,minX=x,maxX=x,minY=y,maxY=y;queue[0]=origin;visited[origin]=true;
                while(head<tail){
                    int value=queue[head++],px=value%width,py=value/width;minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
                    for(int step=0;step<4;step++){
                        int nx=px+(step==0?-1:step==1?1:0),ny=py+(step==2?-1:step==3?1:0);
                        if(nx<0||ny<0||nx>=width||ny>=height)continue;int index=ny*width+nx;
                        if(!visited[index]&&colorMatches(rgb(image,nx,ny),0xFF0000)){visited[index]=true;queue[tail++]=index;}
                    }
                }
                if(tail>best){best=tail;bestWidth=maxX-minX+1;bestHeight=maxY-minY+1;}
            }
            require(best>20,"Native world screen did not render its actual red background panel");
            double actual=(double)bestWidth/bestHeight,expected=4d*glyph("characterWidth")/glyph("characterHeight");
            require(Math.abs(actual-expected)<.4,"World text changed four-cell/one-row font aspect: "+bestWidth+"x"+bestHeight+" ratio "+actual+" expected "+expected);
            results.add("PASS C21.world-pixels actual "+(textureFont?"CP437":"Unicode")+" world-screen shader paints the four-cell RGB panel at original font aspect "+bestWidth+"x"+bestHeight);
        }
        void tick()throws Exception{
            require(System.nanoTime()-started<TimeUnit.MINUTES.toNanos(4),"Text contract timed out phase="+phase);if(serverError!=null)throw new IllegalStateException("Text server operation failed",serverError);if(finishing)return;
            switch(phase){
                case 0->{
                    if(!complete())return;
                    task(()->{gpu=gpu();if(gpu==null||serverBuffer.node()==null||serverBuffer.node().network()!=host.machine().node().network())return;scene();open();});phase=1;
                }
                case 1->{
                    if(!complete())return;
                    if(expected==null){phase=0;return;}
                    if(!readyClient()||client.screen==null||!client.screen.getClass().getName().equals("li.cil.oc.client.gui.ScreenGui"))return;
                    firstClientBuffer=clientBuffer();beforeRevision=revision();
                    if(worldOnly){
                        results.add("PASS C21.world-snapshot normal screen interaction delivers the actual live GPU raster before the focused world-font frames");
                        client.screen.onClose();font("texture",false,true);reload=client.reloadResourcePacks();phase=20;return;
                    }
                    require(firstClientBuffer.getCodePoint(0,2)==0x4E2D&&firstClientBuffer.getCodePoint(2,2)=='A',"Actual client lost CJK cell width before rendering");
                    results.add("PASS C21.full actual screen interaction/Open snapshot supplies ClientLevel Unicode/CJK, palette/RGB, vertical/copy/fill/raw planes and non-square resolution/viewport");
                    screenshot("text-hexfont-gui.png",true);phase=2;
                }
                case 2->{
                    if(!shotDone)return;
                    task(()->{
                        page=((Number)call("allocateBuffer",6,3)[0]).intValue();call("setActiveBuffer",page);call("setBackground",0x000080);call("setForeground",0xFFFF00);successful("fill",1,1,6,3," ");successful("set",1,1,"VRAM中");successful("set",1,2,"BLIT");
                        call("setActiveBuffer",0);successful("bitblt",0,12,5,6,3,page,1,1);
                        call("setForeground",0xFFFFFF);call("setBackground",0);successful("set",1,7,"DELTA");serverBuffer.rawSetText(0,7,new int[][]{{'R','A','W','2'}});
                        expected=new Snapshot(serverBuffer);
                    });phase=3;
                }
                case 3->{
                    if(!complete()||!readyClient()||revision()<=beforeRevision)return;
                    var cache=cachedPage();if(cache.isEmpty())return;require(cache.get() instanceof TextBuffer,"Real client VRAM cache is not a text buffer");var ram=(TextBuffer)cache.get();
                    require(ram.getWidth()==6&&ram.getHeight()==3&&ram.getCodePoint(0,0)=='V'&&ram.getCodePoint(4,0)==0x4E2D,"Actual client ramInit did not retain the real GPU page");
                    results.add("PASS C21.delta actual existing GUI receives normal GPU/raw incrementals, live ramInit/bitblt and newer screen revision without a replacement client buffer");
                    require(clientBuffer()==firstClientBuffer,"Incremental update replaced the actual screen buffer");
                    screenshot("text-vram-incremental.png",true);phase=4;
                }
                case 4->{if(!shotDone)return;task(()->{successful("freeBuffer",page);expected=new Snapshot(serverBuffer);});phase=5;}
                case 5->{
                    if(!complete()||!readyClient()||cachedPage().isPresent())return;
                    results.add("PASS C21.vram actual GPU freeBuffer destroys the normal client cache while the blitted screen raster stays intact");
                    font("texture",false,true);reload=client.reloadResourcePacks();phase=6;
                }
                case 6->{
                    if(!reload.isDone()||client.getOverlay()!=null)return;reload.join();require(clientBuffer()==firstClientBuffer&&readyClient(),"Actual resource reload replaced/lost live text data");
                    screenshot("text-cp437-reloaded.png",true);phase=7;
                }
                case 7->{
                    if(!shotDone)return;client.screen.onClose();phase=8;
                }
                case 8->{
                    if(client.screen!=null)return;
                    require(readyClient(),"Closing actual GUI lost the screen raster");task(this::camera);phase=12;
                }
                case 9->{if(!shotDone)return;font("hexfont",true,false);reload=client.reloadResourcePacks();phase=10;}
                case 10->{
                    if(!reload.isDone()||client.getOverlay()!=null)return;reload.join();require(readyClient(),"Actual font reload lost world-screen data");task(this::camera);phase=13;
                }
                case 11->{
                    if(!shotDone)return;results.add("PASS C21.font-world normal resource reload switches original Unicode/CP437 fonts, preserving the live screen; actual world frames retain viewport/font aspect and native shader appearance");finish();
                }
                case 12->{if(!complete()||!cameraReady())return;screenshot("text-world-cp437.png",false);phase=9;}
                case 13->{if(!complete()||!cameraReady())return;screenshot("text-world-hexfont.png",false);phase=11;}
                case 20->{if(!reload.isDone()||client.getOverlay()!=null||client.screen!=null)return;reload.join();require(readyClient(),"Focused actual font reload lost world-screen data");phase=8;}
                default->throw new AssertionError("Unknown text client phase "+phase);
            }
        }
        void fail(Throwable error){if(finishing)return;results.add("FAIL C21.text phase="+phase+": "+error);finish();}
        void finish(){
            if(finishing)return;finishing=true;if(client.screen!=null)client.screen.onClose();client.options.setCameraType(oldCamera);
            try{if(settings!=null){Files.write(settingsPath,settings);Class.forName("li.cil.oc.Settings").getMethod("load",Path.class).invoke(null,settingsPath);}restoreReload=client.reloadResourcePacks();}
            catch(Exception error){results.add("FAIL C21.text settings cleanup: "+error);restoreReload=CompletableFuture.completedFuture(null);}
            cleanup=CompletableFuture.runAsync(()->{
                if(host!=null)host.machine().stop();for(var entry:oldBlocks.entrySet()){if(level.getBlockEntity(entry.getKey()) instanceof Container inventory)inventory.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}
                for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);
                if(oldPosition!=null){var player=player();player.connection.teleport(oldPosition.x,oldPosition.y,oldPosition.z,oldYaw,oldPitch);player.setDeltaMovement(Vec3.ZERO);player.setNoGravity(oldNoGravity);player.setOnGround(oldOnGround);player.getAbilities().mayfly=oldMayfly;player.getAbilities().flying=oldFlying;player.onUpdateAbilities();player.setItemInHand(InteractionHand.MAIN_HAND,oldMainHand);}
            },server);
        }
    }
    @FunctionalInterface private interface Task{void run()throws Exception;}
}
