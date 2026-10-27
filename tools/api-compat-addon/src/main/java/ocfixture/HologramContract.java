package ocfixture;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.API;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Rotatable;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Finite projector operations on real world hardware and its own physical Case networks. */
public final class HologramContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-hologram-contract");
    private static Run active;
    private HologramContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){
        require(active==null,"A hologram contract is already active");Run run=new Run(level,position.immutable(),results);active=run;
        try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}
    }
    public static void tick(){Run run=active;if(run==null)return;try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}active=null;run.cleanup();}
    public static boolean isRunning(){return active!=null;}
    public static void close(){Run run=active;active=null;if(run!=null){run.results.add("FAIL hologram: server stopped before the actual projector contract completed");run.cleanup();}}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void equal(double actual,double expected,String message){require(Math.abs(actual-expected)<=Math.max(1e-8,Math.abs(expected)*1e-10),message+": expected="+expected+", actual="+actual);}
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing actual hologram item "+name);return descriptor.createItemStack(1);}
    private static String text(Object value){return value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):String.valueOf(value);}
    @FunctionalInterface private interface Operation{void run()throws Exception;}
    private static void rejected(Operation operation,Class<? extends Exception> kind,String message)throws Exception{
        try{operation.run();}catch(Exception error){require(kind.isInstance(error),message+": unrelated error "+error);return;}throw new AssertionError(message+": accepted");
    }
    private static void rejected(Operation operation,String message)throws Exception{
        try{operation.run();}catch(IllegalArgumentException error){require(error.getMessage()!=null&&error.getMessage().contains(message),"Unrelated callback error "+error);return;}throw new AssertionError("Callback accepted instead of "+message);
    }
    private record Unit(int tier,BlockPos position,Case host,BlockEntity block,SidedEnvironment sided,Rotatable rotation,Component component,ManagedEnvironment environment){}
    private static final class Run {
        final ServerLevel level;final BlockPos position;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();final List<Unit> units=new ArrayList<>();
        final double[] before=new double[2],expectedCost=new double[2];
        FakePlayer actor;int ticks,phase,frequency;long setupTime;boolean cleaned;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;}
        void remember(BlockPos pos){
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
            require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Hologram fixture refuses an occupied cell: "+pos);restored.put(pos.immutable(),level.getBlockState(pos));
        }
        void start(){
            require(API.config!=null&&!API.config.getBoolean("power.ignorePower"),"Hologram energy proof requires actual enabled power");
            frequency=Math.max(1,(int)API.config.getDouble("power.tickFrequency"));require(frequency<=200,"Configured projector power interval exceeds this finite contract");
            actor=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"OC_HoloProof"));actor.getAbilities().instabuild=true;
            for(int tier=0;tier<2;tier++){
                BlockPos casePos=tier==0?position.west():position.east(),hologramPos=casePos.above();remember(casePos);
                var descriptor=li.cil.oc.api.Items.get("case1");require(descriptor!=null&&level.setBlock(casePos,descriptor.block().defaultBlockState(),3),"Could not place actual projector Case");
                require(level.getBlockEntity(casePos) instanceof Case,"Projector Context host is not a real Case");Case host=(Case)level.getBlockEntity(casePos);
                remember(hologramPos);ItemStack stack=item("hologram"+(tier+1));require(stack.getItem() instanceof BlockItem,"Actual hologram item is not placeable");
                actor.setPos(casePos.getX()+.5,casePos.getY()+1,casePos.getZ()-2);actor.setYRot(tier==0?0:180);actor.setXRot(tier==0?80:-80);actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var hit=new BlockHitResult(Vec3.atCenterOf(casePos).add(0,.5,0),Direction.UP,casePos,false);
                require(((BlockItem)stack.getItem()).place(new BlockPlaceContext(actor,InteractionHand.MAIN_HAND,stack,hit)).consumesAction(),"Normal hologram block-item placement failed");
                BlockEntity block=level.getBlockEntity(hologramPos);require(block instanceof SidedEnvironment&&block instanceof Rotatable,"Actual projector exposes no SDK sided/orientation interfaces");
                SidedEnvironment sided=(SidedEnvironment)block;Node node=sided.sidedNode(Direction.DOWN);require(node instanceof Component&&node.host() instanceof ManagedEnvironment,"Actual placed hologram exposes no component/environment");
                units.add(new Unit(tier,hologramPos,host,block,sided,(Rotatable)block,(Component)node,(ManagedEnvironment)node.host()));
                require(((Rotatable)block).facing()==(tier==0?Direction.NORTH:Direction.SOUTH),"Original horizontal/inverted player-yaw placement was not applied");
                require(level.getBlockState(hologramPos).isFaceSturdy(level,hologramPos,Direction.DOWN)&&!level.getBlockState(hologramPos).isFaceSturdy(level,hologramPos,Direction.UP),"Actual half-height projector lost its bottom-only supporting face");
            }
        }
        Object[] call(Unit unit,String name,Object... arguments)throws Exception{return unit.component.invoke(name,unit.host.machine(),arguments);}
        int integer(Unit unit,String name,Object... arguments)throws Exception{Object[] result=call(unit,name,arguments);require(result!=null&&result.length==1&&result[0] instanceof Number,"Actual "+name+" returned no single number");return((Number)result[0]).intValue();}
        double number(Unit unit,String name,Object... arguments)throws Exception{Object[] result=call(unit,name,arguments);require(result!=null&&result.length==1&&result[0] instanceof Number,"Actual "+name+" returned no single number");return((Number)result[0]).doubleValue();}
        void voxel(Unit unit,int x,int y,int z,int expected)throws Exception{require(integer(unit,"get",x,y,z)==expected,"Tier "+(unit.tier+1)+" voxel mismatch at "+x+","+y+","+z);}
        CompoundTag view(Unit unit){return unit.block.getUpdateTag(level.registryAccess());}
        Connector power(Unit unit){require(unit.host.machine().node() instanceof Connector,"Real Case has no connector");return(Connector)unit.host.machine().node();}
        boolean tick()throws Exception{
            require(++ticks<=frequency*4+20,"Hologram contract exceeded its finite power intervals");if(ticks<3)return false;
            if(phase==0){
                for(Unit unit:units){
                    require(!unit.host.machine().isRunning()&&unit.component.network()!=null&&unit.component.network()==unit.host.machine().node().network()&&unit.component.isNeighborOf(unit.host.machine().node()),"Placed projector did not join its physical Case's normal network");
                    for(Direction side:Direction.values())require(unit.sided.sidedNode(side)==(side==Direction.DOWN?unit.component:null),"Projector connects through a non-bottom face");
                    require(unit.block instanceof Analyzable&&((Analyzable)unit.block).onAnalyze(actor,Direction.UP,.5f,.5f,.5f)[0]==unit.component,"Actual projector analyzer path lost its component node");
                    callbacks(unit);persistence(unit);preparePower(unit);
                }
                require(units.get(0).component.network()!=units.get(1).component.network(),"Independent projector fixtures unexpectedly share an energy network");setupTime=level.getGameTime();phase=1;return false;
            }
            if(level.getGameTime()<=setupTime||level.getGameTime()%frequency!=0)return false;
            if(phase==1){
                for(Unit unit:units){equal(power(unit).globalBuffer(),before[unit.tier]-expectedCost[unit.tier],"Actual hologram periodic column-bitplane energy cost");require(view(unit).getBoolean("oc:hasPower"),"Charged actual projector did not remain powered");power(unit).changeBuffer(Double.NEGATIVE_INFINITY);}
                setupTime=level.getGameTime();phase=2;return false;
            }
            if(phase==2){
                for(Unit unit:units){equal(power(unit).globalBuffer(),0,"Unpowered projector changed the empty network");require(!view(unit).getBoolean("oc:hasPower"),"Nonempty actual projector remained visible on an empty network");call(unit,"clear");}
                setupTime=level.getGameTime();phase=3;return false;
            }
            for(Unit unit:units){require(view(unit).getBoolean("oc:hasPower"),"Actual cleared projector did not recover power at zero cost");equal(power(unit).globalBuffer(),0,"Empty hologram consumed energy");}
            results.add("PASS hologram.power both real projector tiers charge the original nonzero-bitplane-column ratio times scale/cost/tick interval, switch off with no energy, and recover on clear without consuming power");
            results.add("LIMIT hologram: client depth/additive pass, fading/flicker, camera-inside view and snapshot/cache reload require the finite Windows visual scene; actual pause durations on stopped Case contexts and multiplayer are not measured");
            LOGGER.info("HOLOGRAM PASS both actual tiers, SDK callbacks, auxiliary persistence and three real power intervals");return true;
        }
        void callbacks(Unit unit)throws Exception{
            int colors=unit.tier==0?1:3;Object[] dimensions=call(unit,"getDimensions");require(dimensions.length==3&&((Number)dimensions[0]).intValue()==48&&((Number)dimensions[1]).intValue()==32&&((Number)dimensions[2]).intValue()==48,"Original voxel dimensions changed");require(integer(unit,"maxDepth")==unit.tier+1,"Original tier color depth changed");
            call(unit,"clear");call(unit,"set",1,1,1,true);call(unit,"set",48,32,48,colors);voxel(unit,1,1,1,1);voxel(unit,48,32,48,colors);
            call(unit,"set",1,1,1,false);voxel(unit,1,1,1,0);
            rejected(()->call(unit,"get",0,1,1),ArrayIndexOutOfBoundsException.class,"Zero x coordinate");rejected(()->call(unit,"get",1,33,1),ArrayIndexOutOfBoundsException.class,"Out-of-range y coordinate");rejected(()->call(unit,"get",1,1,49),ArrayIndexOutOfBoundsException.class,"Out-of-range z coordinate");
            rejected(()->call(unit,"set",1,1,1,colors+1),"invalid value");rejected(()->call(unit,"getPaletteColor",0),ArrayIndexOutOfBoundsException.class,"Zero palette index");rejected(()->call(unit,"getPaletteColor",colors+1),ArrayIndexOutOfBoundsException.class,"Out-of-range tier palette");
            call(unit,"fill",2,2,-10,80,colors);voxel(unit,2,1,2,colors);voxel(unit,2,32,2,colors);call(unit,"fill",2,2,1,false);voxel(unit,2,1,2,0);voxel(unit,2,32,2,colors);
            rejected(()->call(unit,"fill",2,2,20,10,true),"interval is empty");
            call(unit,"clear");call(unit,"set",10,1,10,true);call(unit,"set",11,2,10,true);call(unit,"copy",10,10,2,1,1,0);voxel(unit,11,1,10,1);voxel(unit,12,2,10,1);
            call(unit,"set",2,3,1,true);call(unit,"copy",1,1,1,1,-1,0);voxel(unit,1,3,1,1);
            call(unit,"clear");call(unit,"set",2,1,2,true);byte[] raw=new byte[48*32+33];raw[0]=1;raw[48*32+31]=(byte)colors;raw[raw.length-1]=2;
            call(unit,"setRaw",(Object)raw);voxel(unit,1,1,1,1);voxel(unit,2,32,1,colors);voxel(unit,2,1,2,1);voxel(unit,1,32,2,0);
            int old=integer(unit,"setPaletteColor",1,0x112233);require(old==(unit.tier==0?0x00FF00:0x0000FF),"Original setPaletteColor previous BGR value changed");require(integer(unit,"getPaletteColor",1)==0x112233,"Palette RGB read changed");require(integer(unit,"setPaletteColor",1,0xABCDEF)==0x332211,"Previous custom palette value was returned as RGB instead of original BGR");
            double maximum=API.config.getDoubleList("hologram.maxScale").get(unit.tier),translation=API.config.getDoubleList("hologram.maxTranslation").get(unit.tier);
            call(unit,"setScale",Double.POSITIVE_INFINITY);equal(number(unit,"getScale"),Math.max(1,maximum),"Original scale upper clamp");call(unit,"setScale",Double.NEGATIVE_INFINITY);equal(number(unit,"getScale"),.333333,"Original scale lower clamp");
            call(unit,"setTranslation",Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY);Object[] offset=call(unit,"getTranslation");equal(((Number)offset[0]).doubleValue(),Math.max(0,translation),"Original x offset clamp");equal(((Number)offset[1]).doubleValue(),0,"Original y offset clamp");equal(((Number)offset[2]).doubleValue(),-Math.max(0,translation),"Original z offset clamp");
            if(unit.tier==0){for(String method:List.of("setRotation","setRotationSpeed")){Object[] result=call(unit,method);require(result.length==2&&result[0]==null&&"not supported".equals(text(result[1])),"Tier-one rotation should reject before reading parameters");}}
            else{
                require(Boolean.TRUE.equals(call(unit,"setRotation",390,0,1,0)[0]),"Actual tier-two base rotation failed");require(Boolean.TRUE.equals(call(unit,"setRotationSpeed",2000,0,1,0)[0]),"Actual tier-two rotation speed failed");
                rejected(()->call(unit,"setRotation",45,1,2,"bad"),IllegalArgumentException.class,"Invalid fourth base-rotation argument");rejected(()->call(unit,"setRotationSpeed",10,1,2,"bad"),IllegalArgumentException.class,"Invalid fourth speed argument");
                CompoundTag data=view(unit);equal(data.getFloat("oc:rotationAngle"),30,"Failed rotation call partially changed the angle");equal(data.getFloat("oc:rotationX"),0,"Failed rotation call partially changed its axis");equal(data.getFloat("oc:rotationSpeed"),1440,"Original speed clamp or atomic update changed");equal(data.getFloat("oc:rotationSpeedX"),0,"Failed speed call partially changed its axis");
            }
            Direction previous=unit.rotation.facing();ItemStack wrench=item("wrench");actor.setItemInHand(InteractionHand.MAIN_HAND,wrench);
            require(wrench.getItem().onItemUseFirst(wrench,new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(unit.position).add(0,.5,0),Direction.UP,unit.position,false))).consumesAction(),"Actual scrench did not rotate the projector");
            require(unit.rotation.facing()!=previous&&unit.rotation.toGlobal(Direction.DOWN)==Direction.DOWN&&unit.component.network()==unit.host.machine().node().network(),"Vertical-axis scrench rotation lost its orientation or physical bottom network");
            results.add("PASS hologram.callbacks tier="+(unit.tier+1)+" actual placement/physical connection/analyzer/scrench and real Case-context voxel edges, errors, fill/raw ordering, overlap and original clipped-copy edge, BGR previous palette, floating clamps and tier rotation behavior");
        }
        void persistence(Unit unit)throws Exception{
            int color=unit.tier==0?1:3;call(unit,"clear");call(unit,"set",48,32,48,color);call(unit,"setScale",1);call(unit,"setTranslation",.1,.2,-.1);
            Object[] expectedOffset=call(unit,"getTranslation");String address=unit.component.address();CompoundTag saved=new CompoundTag();unit.environment.save(saved,level.registryAccess());
            require(!saved.contains("volume")&&!saved.contains("colors")&&saved.contains("dimension")&&address.equals(saved.getCompound("node").getString("address")),"Actual registry-aware projector save did not use its node-address auxiliary representation");
            call(unit,"clear");call(unit,"setPaletteColor",1,0);call(unit,"setScale",.333333);call(unit,"setTranslation",0,0,0);
            if(unit.tier>0){call(unit,"setRotation",0,0,0,0);call(unit,"setRotationSpeed",0,0,0,0);}
            unit.environment.load(saved,level.registryAccess());voxel(unit,48,32,48,color);require(integer(unit,"getPaletteColor",1)==0xABCDEF,"Actual auxiliary palette did not restore");equal(number(unit,"getScale"),1,"Actual projector scale did not restore");Object[] offsets=call(unit,"getTranslation");for(int axis=0;axis<3;axis++)equal(((Number)offsets[axis]).doubleValue(),((Number)expectedOffset[axis]).doubleValue(),"Actual projector offset did not restore");
            require(address.equals(unit.component.address())&&unit.component.network()==unit.host.machine().node().network(),"Actual registry-aware projector load changed node/network identity");
            if(unit.tier>0){CompoundTag data=view(unit);equal(data.getFloat("oc:rotationAngle"),30,"Actual base rotation did not restore");equal(data.getFloat("oc:rotationSpeed"),1440,"Actual rotation speed did not restore");}
            results.add("PASS hologram.persistence tier="+(unit.tier+1)+" actual environment registry-aware save/mutate/load restores auxiliary volume/palette, scale/offsets, tier-two rotation and real node identity on the same placed device");
        }
        void preparePower(Unit unit)throws Exception{
            double scale=Math.min(2,Math.max(1,API.config.getDoubleList("hologram.maxScale").get(unit.tier)));call(unit,"clear");call(unit,"setScale",scale);call(unit,"fill",1,1,32,unit.tier==0?1:3);
            Connector power=power(unit);power.changeBuffer(Double.NEGATIVE_INFINITY);equal(power.changeBuffer(100),0,"Could not charge the actual Case network");before[unit.tier]=power.globalBuffer();
            expectedCost[unit.tier]=API.config.getDouble("power.cost.hologram")*(unit.tier==0?1:2)/(48d*48*2)*scale*frequency;
            require(expectedCost[unit.tier]>0&&expectedCost[unit.tier]<before[unit.tier],"Hologram contract requires configured positive affordable projector energy cost");
            double stored=power.globalBuffer();view(unit);view(unit);equal(power.globalBuffer(),stored,"Public hologram snapshot requests consumed an extra power tick");
        }
        void fail(Throwable error){results.add("FAIL hologram: "+error);LOGGER.error("HOLOGRAM FAIL",error);}
        void cleanup(){
            if(cleaned)return;cleaned=true;List<BlockPos> positions=new ArrayList<>(restored.keySet());Collections.reverse(positions);
            for(BlockPos pos:positions)try{level.setBlock(pos,restored.get(pos),3);}catch(Throwable error){LOGGER.warn("Could not restore hologram fixture cell {}",pos,error);}
            for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);
        }
    }
}
