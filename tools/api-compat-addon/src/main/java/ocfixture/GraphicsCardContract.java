package ocfixture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.driver.DriverItem;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.TextBuffer;
import li.cil.oc.api.machine.LimitReachedException;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Three real GPU drivers, the placed Case's own Context and naturally connected world screens. */
public final class GraphicsCardContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-graphics-card-contract");
    private static final int[][] RESOLUTIONS = {{50,16},{80,25},{160,50}};
    private static final int[] DEPTHS = {1,4,8};
    private static Run active;
    private GraphicsCardContract() {}
    public static void start(ServerLevel level, BlockPos position, List<String> results) {
        require(active == null, "A GPU contract is already active");
        Run run = new Run(level, position.immutable(), results); active = run;
        try { run.start(); } catch (Throwable error) { run.fail(error); active = null; run.cleanup(); }
    }
    public static boolean isRunning() { return active != null; }
    public static void tick() {
        Run run = active; if (run == null) return;
        try { if (!run.tick()) return; } catch (Throwable error) { run.fail(error); }
        active = null; run.cleanup();
    }
    public static void close() {
        Run run = active; active = null;
        if (run != null) { run.results.add("FAIL gpu: server stopped before the contract completed"); run.cleanup(); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Object first(Object[] values, String method) {
        require(values != null && values.length > 0, method + " returned no result"); return values[0];
    }
    private static double number(Object value) { require(value instanceof Number, "Expected numeric GPU result: " + value); return ((Number)value).doubleValue(); }
    private static void near(double actual, double expected, String message) { require(Math.abs(actual - expected) < 1e-7, message + ": " + actual + " != " + expected); }
    private record Card(ItemStack stack, DriverItem driver, ManagedEnvironment environment, Component component, int tier) {}

    private static final class Run {
        final ServerLevel level; final BlockPos position, screenPos, alternatePos; final List<String> results;
        final Map<BlockPos,BlockState> restored = new LinkedHashMap<>(); final Set<Long> forced = new LinkedHashSet<>();
        final List<Card> cards = new ArrayList<>();
        Case host; TextBuffer screen, alternate; Connector energy; Card gpu; int ticks, phase; boolean cleaned;
        Run(ServerLevel level, BlockPos position, List<String> results) {
            this.level=level; this.position=position; screenPos=position.east(); alternatePos=position.west(); this.results=results;
        }
        void place(String name, BlockPos pos) {
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true)) forced.add(ChunkPos.asLong(pos.getX() >> 4,pos.getZ() >> 4));
            level.getChunkAt(pos);
            require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "GPU fixture refuses occupied cell " + pos);
            restored.put(pos, level.getBlockState(pos));
            var info=li.cil.oc.api.Items.get(name);
            require(info != null && info.block() != null && level.setBlock(pos,info.block().defaultBlockState(),3), "Could not place actual " + name);
        }
        void start() {
            require(API.config != null && !API.config.getBoolean("power.ignorePower"), "GPU contract requires normal power accounting");
            require(API.config.getDoubleList("gpu.vramSizes").equals(List.of(1d,2d,3d))
                && API.config.getDouble("gpu.bitbltCost") == .5, "GPU contract requires the normal isolated-fixture VRAM/budget configuration");
            place("case3",position); place("screen3",screenPos); place("screen1",alternatePos);
            require(level.getBlockEntity(position) instanceof Case, "Placed GPU host exposes no SDK Case");
            host=(Case)level.getBlockEntity(position);
            require(host.machine()!=null && host.machine().host()==host, "GPU callbacks require the actual placed Case's machine Context");
        }
        TextBuffer buffer(BlockPos pos) {
            require(level.getBlockEntity(pos) instanceof Environment, "World screen exposes no SDK Environment");
            var node=((Environment)level.getBlockEntity(pos)).node();
            require(node!=null && node.host() instanceof TextBuffer, "Real screen node exposes no SDK TextBuffer");
            return (TextBuffer)node.host();
        }
        Object[] call(Card card,String method,Object... args) throws Exception { return card.component.invoke(method,host.machine(),args); }
        Object value(Card card,String method,Object... args) throws Exception { return first(call(card,method,args),method); }
        void ok(Card card,String method,Object... args) throws Exception { require(Boolean.TRUE.equals(value(card,method,args)),method+" did not succeed"); }
        void nil(Card card,String method,String message,Object... args) throws Exception {
            Object[] values=call(card,method,args);
            require(values!=null && values.length==2 && values[0]==null && message.equals(values[1]),method+" did not return nil, "+message);
        }
        void invalid(Card card,String method,Object... args) throws Exception {
            try { call(card,method,args); } catch (IllegalArgumentException expected) { return; }
            throw new AssertionError(method+" accepted invalid arguments");
        }
        void dimensions(Card card,String method,int width,int height,Object... args) throws Exception {
            Object[] values=call(card,method,args); require(values!=null&&values.length==2,method+" omitted dimensions");
            near(number(values[0]),width,method+" width"); near(number(values[1]),height,method+" height");
        }
        Card create(ItemStack stack) {
            var driver=Driver.driverFor(stack,host.getClass()); require(driver!=null,"Actual Case rejected its GPU item driver");
            ManagedEnvironment environment=driver.createEnvironment(stack,host);
            require(environment!=null&&environment.node() instanceof Component&&environment.node() instanceof Connector,"Actual GPU has no component/energy node");
            environment.load(driver.dataTag(stack),level.registryAccess());
            Card card=new Card(stack,driver,environment,(Component)environment.node(),driver.tier(stack)); cards.add(card); return card;
        }
        void connect(Card card) {
            host.machine().node().connect(card.component);
            require(card.component.network()==host.machine().node().network() && card.component.canBeSeenFrom(host.machine().node()),"Actual GPU did not join its real host network");
        }
        void tiers() throws Exception {
            energy=(Connector)host.machine().node(); energy.changeBuffer(energy.globalBufferSize()); require(energy.globalBuffer()>0,"Real Case energy network could not be charged");
            for (int tier=0;tier<3;tier++) {
                Card card=create(li.cil.oc.api.Items.get("graphicscard"+(tier+1)).createItemStack(1));
                require(card.tier==tier,"Real graphics driver assigned the wrong tier");
                // A real driver environment may allocate VRAM before its node receives a graph address.
                require(number(value(card,"allocateBuffer",8,4))==1,"Unjoined GPU could not allocate its first VRAM page");
                near(number(value(card,"freeAllBuffers")),1,"Unjoined GPU free count");
                connect(card);
                invalid(card,"setForeground","wrong type"); invalid(card,"getPaletteColor","wrong type");
                invalid(card,"setPaletteColor",0,"wrong type"); nil(card,"setDepth","no screen",9);
                nil(card,"getScreen","no screen"); nil(card,"setActiveBuffer","invalid buffer index",99);
                ok(card,"bind",screen.node().address());
                dimensions(card,"maxResolution",RESOLUTIONS[tier][0],RESOLUTIONS[tier][1]);
                near(number(value(card,"maxDepth")),DEPTHS[tier],"Tier screen depth");
                near(number(value(card,"totalMemory")),RESOLUTIONS[tier][0]*RESOLUTIONS[tier][1]*(tier+1),"Tier VRAM capacity");
                int page=(int)number(value(card,"allocateBuffer"));
                dimensions(card,"getBufferSize",RESOLUTIONS[tier][0],RESOLUTIONS[tier][1],page);
                near(number(value(card,"freeMemory")),RESOLUTIONS[tier][0]*RESOLUTIONS[tier][1]*tier,"Tier remaining VRAM");
                require(number(value(card,"setActiveBuffer",page))==0,"Selecting VRAM did not return the old screen index");
                dimensions(card,"getViewport",RESOLUTIONS[tier][1],RESOLUTIONS[tier][0]);
                require(List.of("OneBit","FourBit","EightBit").get(tier).equals(value(card,"setDepth",DEPTHS[tier])),"setDepth lost its original enum-to-string return");
                invalid(card,"setDepth",16); invalid(card,"setResolution",RESOLUTIONS[tier][0]+1,1);
                require(card.environment instanceof DeviceInfo,"Actual GPU has no SDK device info");
                var info=((DeviceInfo)card.environment).getDeviceInfo();
                require(Integer.toString(DEPTHS[tier]).equals(info.get("width"))&&Integer.toString(RESOLUTIONS[tier][0]*RESOLUTIONS[tier][1]).equals(info.get("capacity")),"GPU device info disagrees with its actual limits");
                near(number(value(card,"freeAllBuffers")),1,"Tier free count"); near(number(value(card,"getActiveBuffer")),0,"Freed active page fallback");
                if(tier<2)invalid(card,"setDepth",DEPTHS[tier+1]);
                gpu=card;
            }
            results.add("PASS gpu.tiers three real Case-context drivers: unjoined allocation, original type-error precedence, depth/resolution/VRAM limits, non-square viewport order, setDepth enum conversion, device info and freed-active fallback");
        }
        void binding() throws Exception {
            ok(gpu,"bind",screen.node().address()); ok(gpu,"setResolution",20,6); ok(gpu,"setViewport",12,4);
            alternate.setResolution(12,3); alternate.setForegroundColor(0x00FF00);
            int page=(int)number(value(gpu,"allocateBuffer",8,4)); value(gpu,"setActiveBuffer",page);
            value(gpu,"setForeground",0x123456); value(gpu,"setBackground",0x654321);
            ok(gpu,"bind",alternate.node().address());
            require(alternate.getWidth()==12&&alternate.getHeight()==3&&alternate.getForegroundColor()==0x00FF00,
                "Binding with VRAM active reset the actual screen instead of the selected page");
            require(alternate.node().address().equals(value(gpu,"getScreen")),"Binding failed to update the real screen address");
            near(number(value(gpu,"getForeground")),0xFFFFFF,"Active VRAM bind foreground"); near(number(value(gpu,"getBackground")),0,"Active VRAM bind background");
            value(gpu,"setActiveBuffer",0); dimensions(gpu,"getResolution",12,3);
            ok(gpu,"bind",screen.node().address(),false); dimensions(gpu,"getResolution",20,6); dimensions(gpu,"getViewport",12,4);
            value(gpu,"freeAllBuffers"); ok(gpu,"bind",screen.node().address()); ok(gpu,"setResolution",8,4);
            results.add("PASS gpu.bind real alternate screen address changes while reset affects active VRAM; bind(false) retains the previous real screen resolution and viewport");
        }
        void character(Card card,int x,int y,String expected) throws Exception {
            require(expected.equals(value(card,"get",x,y)),"GPU character mismatch at "+x+","+y+": expected "+expected);
        }
        boolean cached(String owner,int id) throws Exception {
            // Existing public rasterizer method; no private state or implementation classes are referenced.
            Object value=screen.getClass().getMethod("getBuffer",String.class,int.class).invoke(screen,owner,id);
            require(value instanceof Optional<?>,"Actual screen rasterizer returned no Optional page"); return ((Optional<?>)value).isPresent();
        }
        void rasterAndPersistence() throws Exception {
            int page=(int)number(value(gpu,"allocateBuffer",8,4)); value(gpu,"setActiveBuffer",page);
            nil(gpu,"allocateBuffer","invalid page dimensions: must be greater than zero",0,1);
            nil(gpu,"allocateBuffer","not enough video memory",24001,1);
            value(gpu,"setPaletteColor",1,0x12AB34); value(gpu,"setForeground",1,true); value(gpu,"setBackground",0);
            double before=energy.globalBuffer();
            ok(gpu,"set",1,1,"A😀"); ok(gpu,"set",1,2,"界"); ok(gpu,"set",2,2,"x");
            ok(gpu,"set",7,2,"UVWX"); ok(gpu,"set",1,3,"abcdef"); ok(gpu,"copy",1,3,6,1,1,0);
            near(energy.globalBuffer(),before,"VRAM text/copy energy");
            character(gpu,2,1,"😀"); character(gpu,1,2," "); character(gpu,2,2,"x"); character(gpu,8,2,"V"); character(gpu,3,3,"b"); character(gpu,7,3,"f");
            Object[] cell=call(gpu,"get",2,1);
            require(number(cell[1])==0x12AB34&&number(cell[3])==1,"VRAM palette color/index were not retained");
            invalid(gpu,"fill",1,1,1,1,"too long"); invalid(gpu,"getPaletteColor",16);
            int second=(int)number(value(gpu,"allocateBuffer",4,2));
            ok(gpu,"bitblt",second,0,1,5,1,page,1,3); value(gpu,"setActiveBuffer",second);
            character(gpu,1,1,"a"); character(gpu,4,1,"d"); near(energy.globalBuffer(),before,"VRAM bitblt energy");
            value(gpu,"freeBuffer",second); value(gpu,"setActiveBuffer",0); value(gpu,"setPaletteColor",1,0x12AB34);
            before=energy.globalBuffer(); ok(gpu,"bitblt",0,1,1,8,4,page,1,1);
            near(before-energy.globalBuffer(),32*cost("Copy")/15,"VRAM-to-screen bitblt energy");
            character(gpu,2,1,"😀"); cell=call(gpu,"get",2,1);
            require(number(cell[1])==0x12AB34&&number(cell[3])==1,"Actual screen lost the blitted palette/color");
            require(cached(gpu.component.address(),page),"Actual screen did not cache the rasterized real VRAM page");
            value(gpu,"setActiveBuffer",page);
            CompoundTag saved=new CompoundTag(); gpu.environment.save(saved,level.registryAccess());
            gpu.environment.load(saved,level.registryAccess());
            require(!cached(gpu.component.address(),page),"Reloading the real GPU left its old screen RAM cache behind");
            character(gpu,2,1,"😀"); dimensions(gpu,"getBufferSize",8,4); near(number(value(gpu,"getPaletteColor",1)),0x12AB34,"Reloaded palette");
            gpu.driver.saveDataTag(gpu.stack,saved);
            ItemStack serialized=ItemStack.parseOptional(level.registryAccess(),(CompoundTag)gpu.stack.saveOptional(level.registryAccess()));
            String address=gpu.component.address(); gpu.component.remove();
            Card restoredCard=create(serialized); connect(restoredCard); gpu=restoredCard;
            require(address.equals(gpu.component.address())&&screen.node().address().equals(value(gpu,"getScreen")),"Saved GPU item did not restore its real node/screen binding");
            near(number(value(gpu,"getActiveBuffer")),page,"Restored active page"); character(gpu,2,1,"😀");
            cell=call(gpu,"get",2,1); require(number(cell[1])==0x12AB34&&number(cell[3])==1,"Restored GPU item lost Unicode colors/palette");
            results.add("PASS gpu.raster real VRAM Unicode/wide-character repair, clipped set, overlap copy, clipped RAM bitblt, palette colors and screen rasterization; live load removes cached RAM; actual serialized GPU item remount restores address/binding/active page/content");
        }
        double cost(String operation) { return API.config.getDouble("power.cost.gpu"+operation)/(50*16); }
        void energyAndBudget() throws Exception {
            value(gpu,"setActiveBuffer",0); double before=energy.globalBuffer();
            ok(gpu,"set",1,1,"XY"); near(before-energy.globalBuffer(),2*cost("Set"),"Screen set energy");
            before=energy.globalBuffer(); ok(gpu,"fill",1,1,4,2," "); near(before-energy.globalBuffer(),8*cost("Clear"),"Screen clear energy");
            before=energy.globalBuffer(); ok(gpu,"fill",1,1,4,2,"Q"); near(before-energy.globalBuffer(),8*cost("Fill"),"Screen fill energy");
            before=energy.globalBuffer(); ok(gpu,"copy",1,1,2,2,1,0); near(before-energy.globalBuffer(),4*cost("Copy"),"Screen copy energy");
            energy.changeBuffer(-energy.globalBuffer()); nil(gpu,"set","not enough energy",1,1,"!"); character(gpu,1,1,"Q");
            value(gpu,"setActiveBuffer",1); ok(gpu,"set",1,4,"R"); near(energy.globalBuffer(),0,"VRAM writes with depleted power");
            near(number(value(gpu,"freeAllBuffers")),1,"Free restored page count"); energy.changeBuffer(energy.globalBufferSize());
            int page=(int)number(value(gpu,"allocateBuffer",160,150)); value(gpu,"setActiveBuffer",page); ok(gpu,"set",1,1,"Z"); value(gpu,"setActiveBuffer",0);
            before=energy.globalBuffer(); boolean limited=false;
            try { call(gpu,"bitblt",0,1,1,1,1,page,1,1); } catch(LimitReachedException expected) { limited=true; }
            require(limited,"Dirty full-VRAM transfer did not yield on its original budget limit");
            near(energy.globalBuffer(),before,"Yielding bitblt energy"); character(gpu,1,1,"Q");
            ok(gpu,"bitblt",0,1,1,1,1,page,1,1); character(gpu,1,1,"Z");
            near(before-energy.globalBuffer(),cost("Copy")/15,"Retried bitblt energy");
            near(number(value(gpu,"freeAllBuffers")),1,"Free budget page count");
            results.add("PASS gpu.power actual Case Connector: exact screen set/fill/clear/copy/bitblt fees, atomic no-energy rejection, free off-screen editing, dirty whole-page bitblt budget yield followed by a successful retry");
        }
        boolean tick() throws Exception {
            require(++ticks<=100,"GPU contract exceeded 100 actual world ticks");
            if(phase==0) {
                if(host.machine().node().network()==null)return false;
                screen=buffer(screenPos); alternate=buffer(alternatePos);
                if(screen.node().network()!=host.machine().node().network()||alternate.node().network()!=host.machine().node().network())return false;
                tiers(); binding(); rasterAndPersistence(); energyAndBudget();
                require(!host.machine().isRunning(),"GPU contract unexpectedly started a VM");
                require(level.setBlock(screenPos,Blocks.AIR.defaultBlockState(),3),"Could not remove the actual bound screen"); phase=1; return false;
            }
            nil(gpu,"getScreen","no screen"); nil(gpu,"getResolution","no screen");
            near(number(value(gpu,"freeMemory")),24000,"Final GPU free memory");
            results.add("PASS gpu.lifecycle removing the real bound world screen clears the GPU binding; all allocated pages are freed; callbacks used only the actual stopped Case Context");
            results.add("LIMIT gpu: initialized-VM call-budget scheduling and client packet/render appearance belong to the shared runtime/client acceptance, not this stopped-host contract");
            return true;
        }
        void fail(Throwable error) { results.add("FAIL gpu: "+error); LOGGER.error("GRAPHICS_CARD_CONTRACT FAIL",error); }
        void cleanup() {
            if(cleaned)return; cleaned=true;
            for(Card card:cards)card.environment.node().remove(); if(host!=null)host.machine().stop();
            var entries=new ArrayList<>(restored.entrySet()); Collections.reverse(entries);
            for(var entry:entries)level.setBlock(entry.getKey(),entry.getValue(),3);
            for(long packed:forced){ChunkPos chunk=new ChunkPos(packed);level.setChunkForced(chunk.x,chunk.z,false);} forced.clear();
        }
    }
}
