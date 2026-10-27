package ocfixture;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.API;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.event.RobotBreakBlockEvent;
import li.cil.oc.api.event.RobotMoveEvent;
import li.cil.oc.api.event.RobotUsedToolEvent;
import li.cil.oc.api.internal.Agent;
import li.cil.oc.api.internal.Drone;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Actual assembled agents, public component calls, cancellable world actions and dismantling. */
public final class RobotDroneContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-robot-drone-contract");
    private static Run active;
    private static boolean registered;
    private RobotDroneContract(){}
    public static void start(ServerLevel level,BlockPos origin,List<String> results){
        require(active==null,"An agent contract is already active");
        Run run=new Run(level,origin.immutable(),results);active=run;
        if(!registered){
            registered=true;
            NeoForge.EVENT_BUS.addListener(RobotDroneContract::beforeTick);
            NeoForge.EVENT_BUS.addListener(RobotDroneContract::joined);
            NeoForge.EVENT_BUS.addListener(RobotDroneContract::moving);
            NeoForge.EVENT_BUS.addListener(RobotDroneContract::moved);
            NeoForge.EVENT_BUS.addListener(RobotDroneContract::breaking);
            NeoForge.EVENT_BUS.addListener(RobotDroneContract::broken);
            NeoForge.EVENT_BUS.addListener(RobotDroneContract::tossed);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,RobotDroneContract::damageRate);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,RobotDroneContract::applyingDamage);
        }
        try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){Run run=active;if(run==null)return;try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}active=null;run.cleanup();}
    public static void close(){Run run=active;active=null;if(run!=null){run.results.add("FAIL robot.drone: server stopped before completion");run.cleanup();}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double actual,double expected,String message){require(Math.abs(actual-expected)<1e-5,message+": "+actual+" != "+expected);}
    private static void beforeTick(ServerTickEvent.Pre event){
        Run run=active;
        if(run!=null)run.tickerOpportunity=run.level.getChunkAt(run.robotPos).getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)
            &&run.level.areEntitiesLoaded(ChunkPos.asLong(run.robotPos));
    }
    private static void joined(EntityJoinLevelEvent event){
        Run run=active;if(run==null||event.getLevel()!=run.level||event.loadedFromDisk()||!run.bounds().contains(event.getEntity().position()))return;
        if(event.getEntity() instanceof Drone drone){run.spawnedAgents.add(event.getEntity());if(run.cancelDrone){run.cancelledDrone=drone;run.cancelledNode=drone.machine().node();event.setCanceled(true);}else{run.drone=drone;run.droneEntity=event.getEntity();}}
        if(event.getEntity() instanceof ItemEntity item)run.drops.add(new Drop(item,item.getItem().copy()));
    }
    private static boolean ours(Agent agent){return active!=null&&active.robot!=null&&agent.machine()==active.robot.machine();}
    private static void moving(RobotMoveEvent.Pre event){if(ours(event.agent)){active.movePre++;if(active.cancelMove)event.setCanceled(true);}}
    private static void moved(RobotMoveEvent.Post event){if(ours(event.agent))active.movePost++;}
    private static void breaking(RobotBreakBlockEvent.Pre event){if(ours(event.agent)){active.breakPre++;if(active.cancelBreak)event.setCanceled(true);}}
    private static void broken(RobotBreakBlockEvent.Post event){if(ours(event.agent))active.breakPost++;}
    private static void tossed(ItemTossEvent event){if(active!=null&&active.cancelToss&&active.robot!=null&&event.getPlayer()==active.robot.player()){active.tosses++;event.setCanceled(true);}}
    private static void damageRate(RobotUsedToolEvent.ComputeDamageRate event){if(ours(event.agent)&&active.zeroDamage)event.setDamageRate(0);}
    private static void applyingDamage(RobotUsedToolEvent.ApplyDamageRate event){if(ours(event.agent)&&active.zeroDamage){active.applyEvents++;active.damageBeforeRepair=event.toolAfterUse.getDamageValue()-event.toolBeforeUse.getDamageValue();}}
    private record Drop(ItemEntity entity,ItemStack stack){}
    private static final class Run {
        final ServerLevel level;final BlockPos origin,dronePad;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        final List<Entity> spawnedAgents=new ArrayList<>();final List<Drop> drops=new ArrayList<>();
        BlockPos robotPos;Robot robot;Component robotControl,droneControl;Drone drone,cancelledDrone;Entity droneEntity;Node cancelledNode;
        FakePlayer actor;int phase,ticks,movePre,movePost,breakPre,breakPost,tosses,applyEvents,damageBeforeRepair;
        boolean tickerOpportunity,cancelMove,cancelBreak,cancelToss,cancelDrone,zeroDamage;
        Run(ServerLevel level,BlockPos origin,List<String> results){this.level=level;this.origin=origin;this.robotPos=origin;this.dronePad=origin.east(4).below();this.results=results;}
        AABB bounds(){return new AABB(origin).inflate(6);}
        void remember(BlockPos pos){
            if(restored.containsKey(pos))return;
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
            require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Agent fixture refuses occupied cell "+pos);restored.put(pos.immutable(),level.getBlockState(pos));
        }
        ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing actual hardware "+name);return descriptor.createItemStack(1);}
        ItemStack preset(String name){
            for(ItemStack stack:CreativeTab.instance.getDisplayItems())if(stack.is(item(name).getItem())&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("oc:tier")==3)return stack.copy();
            throw new AssertionError("Missing original configured creative "+name);
        }
        Component component(Agent host,String name){
            if(host.machine().node().network()==null)return null;
            for(Node node:host.machine().node().network().nodes())if(node instanceof Component component&&component.name().equals(name))return component;
            return null;
        }
        Object[] call(Component component,Agent host,String method,Object... args)throws Exception{return component.invoke(method,host.machine(),args);}
        void success(Component component,Agent host,String method,Object... args)throws Exception{Object[] values=call(component,host,method,args);require(values!=null&&values.length>0&&Boolean.TRUE.equals(values[0]),method+" failed: "+java.util.Arrays.toString(values));}
        int count(net.minecraft.world.item.Item item){int count=0;for(int i=0;i<robot.mainInventory().getContainerSize();i++){ItemStack stack=robot.mainInventory().getItem(i);if(stack.is(item))count+=stack.getCount();}return count;}
        void start(){
            require(API.config!=null&&!API.config.getBoolean("power.ignorePower"),"Agent contract requires ordinary energy accounting");
            require(CreativeTab.instance!=null,"Configured agent creative tab unavailable");
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(),true,level.registryAccess()));
            for(BlockPos pos:List.of(origin,origin.west(),origin.west(2),dronePad,dronePad.above(),dronePad.above(2)))remember(pos);
            require(level.setBlock(dronePad,Blocks.STONE.defaultBlockState(),3),"Could not place drone's real support");
            actor=new FakePlayer(level,new GameProfile(UUID.fromString("4e6bdd52-610e-41c9-992c-3e2f17a996a1"),"OCAgentFamily"));
            actor.getAbilities().instabuild=true;actor.setYRot(0);actor.setPos(origin.getX()+.5,origin.getY(),origin.getZ()-2);
            ItemStack stack=preset("robot");actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
            require(stack.getItem() instanceof BlockItem&&((BlockItem)stack.getItem()).place(new BlockPlaceContext(actor,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(origin),Direction.UP,origin,false))).consumesAction(),"Actual robot item placement failed");
            require(level.getBlockEntity(origin) instanceof Robot,"Actual placement produced no SDK Robot");robot=(Robot)level.getBlockEntity(origin);
            require(robot.facing()==Direction.SOUTH,"Robot placement did not preserve the placer's facing");
            require(robot.mainInventory().getContainerSize()>=2&&robot.equipmentInventory().getContainerSize()>0,"Original assembled robot lacks its actual inventory/tool slots");
        }
        boolean tick()throws Exception{
            require(++ticks<=200,"Agent world contract timed out in phase "+phase);
            if(phase==0){
                if(!tickerOpportunity||(robotControl=component(robot,"robot"))==null)return false;
                Connector energy=(Connector)robotControl;energy.changeBuffer(Double.POSITIVE_INFINITY);
                double before=energy.globalBuffer();boolean rejected=false;
                try{call(robotControl,robot,"turn",1);}catch(IllegalArgumentException expected){rejected=true;}
                require(rejected,"turn accepted a non-boolean");near(energy.globalBuffer(),before,"Invalid turn spent energy");
                success(robotControl,robot,"turn",true);require(robot.facing()==Direction.WEST,"Real robot did not turn clockwise");
                near(before-energy.globalBuffer(),API.config.getDouble("power.cost.robotTurn"),"Actual turn energy cost");
                robot.mainInventory().setItem(0,new ItemStack(Items.DIAMOND,3));robot.mainInventory().setItem(1,new ItemStack(Items.REDSTONE,4));robot.setSelectedSlot(0);
                Object[] partial=call(robotControl,robot,"transferTo",2,2);require(Boolean.FALSE.equals(partial[0]),"Partial incompatible transfer swapped whole stacks");
                success(robotControl,robot,"transferTo",2,64);require(robot.mainInventory().getItem(0).is(Items.REDSTONE)&&robot.mainInventory().getItem(1).is(Items.DIAMOND),"Full transfer failed the real inventory swap");
                cancelToss=true;success(robotControl,robot,"drop",3,2);cancelToss=false;
                require(tosses==1&&count(Items.REDSTONE)==4,"Cancelled real ItemTossEvent lost the selected inventory");
                cancelMove=true;before=energy.globalBuffer();Object[] cancelled=call(robotControl,robot,"move",3);cancelMove=false;
                require(cancelled[0]==null&&movePre==1&&movePost==0&&level.getBlockEntity(origin)==robot,"Cancelled move changed the real proxy/world");
                near(energy.globalBuffer(),before,"Cancelled move failed to refund energy");
                var machine=robot.machine();success(robotControl,robot,"move",3);robotPos=origin.west();
                require(level.getBlockEntity(robotPos) instanceof Robot,"Successful move did not place the actual next proxy");robot=(Robot)level.getBlockEntity(robotPos);
                require(robot.machine()==machine&&movePre==2&&movePost==1&&robot.selectedSlot()==0&&count(Items.REDSTONE)==4,"Real movement lost the stable machine, inventory or events");
                near(before-energy.globalBuffer(),API.config.getDouble("power.cost.robotMove"),"Actual move energy cost");
                results.add("PASS D36.motion actual placed facing, boolean validation before charge, turn/move costs, cancellable move/refund and stable moved machine/inventory");
                phase=1;return false;
            }
            if(phase==1){
                if(level.getBlockEntity(robotPos).getUpdateTag(level.registryAccess()).getInt("oc:animationTicksLeft")>0)return false;
                robot.equipmentInventory().setItem(0,new ItemStack(Items.DIAMOND_PICKAXE));
                require(level.setBlock(origin.west(2),Blocks.STONE.defaultBlockState(),3),"Could not place actual harvest target");
                cancelBreak=true;Object[] cancelled=call(robotControl,robot,"swing",3);cancelBreak=false;
                String cancelledState="result="+java.util.Arrays.toString(cancelled)+", breakPre="+breakPre+", breakPost="+breakPost+", target="+level.getBlockState(origin.west(2));
                require(Boolean.FALSE.equals(cancelled[0]),"Cancelled swing returned success: "+cancelledState);
                require(breakPre>0,"Cancelled swing emitted no public Pre: "+cancelledState);
                require(breakPost==0,"Cancelled swing emitted a public Post: "+cancelledState);
                require(level.getBlockState(origin.west(2)).is(Blocks.STONE),"Cancelled break changed the actual target: "+cancelledState);
                zeroDamage=true;Object[] harvested=call(robotControl,robot,"swing",3);zeroDamage=false;
                require(harvested!=null&&harvested.length>0&&Boolean.TRUE.equals(harvested[0]),"Actual swing failed: "+java.util.Arrays.toString(harvested));
                require(level.isEmptyBlock(origin.west(2)),"Actual swing left its target "+origin.west(2)+" as "+level.getBlockState(origin.west(2))+"; result="+java.util.Arrays.toString(harvested)+", breakPre="+breakPre+", breakPost="+breakPost+", player="+robot.player().position()+", facing="+robot.facing());
                require(breakPost==1,"Actual harvest emitted "+breakPost+" Post events; result="+java.util.Arrays.toString(harvested)+", breakPre="+breakPre);
                require(count(Items.COBBLESTONE)==1,"Actual harvested block drop did not reach real agent inventory: inventory="+java.util.stream.IntStream.range(0,robot.mainInventory().getContainerSize()).mapToObj(slot->robot.mainInventory().getItem(slot).toString()).filter(stack->!stack.equals("0 air")).toList()+", joined="+drops.stream().map(drop->drop.stack+" added="+drop.entity.isAddedToLevel()+" removed="+drop.entity.isRemoved()+" remaining="+drop.entity.getItem()).toList()+", tool="+robot.equipmentInventory().getItem(0)+", damageEvents="+applyEvents+", damageBeforeRepair="+damageBeforeRepair+", dropsRule="+level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS));
                require(drops.stream().anyMatch(drop->drop.stack.is(Items.COBBLESTONE)&&drop.entity.isRemoved()),"Harvest did not collect the actual joined drop entity");
                require(applyEvents==1&&damageBeforeRepair>0&&robot.equipmentInventory().getItem(0).getDamageValue()==0,"Default ApplyDamageRate handler did not repair inside the public event");
                results.add("PASS D36.actions real full-stack swap, cancelled toss writeback, cancelled/actual block harvesting, joined drop pickup and public tool-damage repair order");
                actor.getAbilities().instabuild=false;ItemStack stack=preset("drone");actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
                cancelDrone=true;var cancelledPlacement=stack.getItem().useOn(droneUse());cancelDrone=false;
                require(!cancelledPlacement.consumesAction()&&stack.getCount()==1&&cancelledDrone!=null&&((Entity)cancelledDrone).isRemoved()&&cancelledNode.network()==null,"Cancelled drone spawn consumed the item or leaked its initialized runtime");
                require(stack.getItem().useOn(droneUse()).consumesAction()&&stack.isEmpty()&&droneEntity!=null&&droneEntity.isAddedToLevel(),"Actual drone item did not spawn/consume normally");
                Vec3 hit=Vec3.atCenterOf(dronePad).add(0,.5,0),expected=Vec3.atLowerCornerOf(dronePad).add(hit.subtract(Vec3.atLowerCornerOf(dronePad)).scale(1.1));
                near(droneEntity.position().distanceTo(expected),0,"Drone clicked-face spawn location");
                require(drone.ownerUUID().equals(actor.getUUID())&&drone.ownerName().equals(actor.getGameProfile().getName()),"Drone did not retain its real placer identity");
                phase=2;return false;
            }
            if(phase==2){
                if(droneEntity.tickCount==0||(droneControl=component(drone,"drone"))==null)return false;
                require(droneEntity instanceof Context,"Actual drone entity does not expose its original machine Context");
                Vec3 target=drone.getTarget();call(droneControl,drone,"move",1.13,.38,-.62);
                Vec3 expected=new Vec3(Math.round((float)(target.x+1.13f)*4)/4f,Math.round((float)(target.y+.38f)*4)/4f,Math.round((float)(target.z-.62f)*4)/4f);
                near(drone.getTarget().distanceTo(expected),0,"Real drone.move quarter-block target");
                near(((Number)call(droneControl,drone,"setAcceleration",9)[0]).doubleValue(),2,"Drone acceleration clamp");
                require(((Number)call(droneControl,drone,"setLightColor",-7)[0]).intValue()==-7,"Drone light color lost original raw integer");
                require("abcdefghij\n0123456789".equals(call(droneControl,drone,"setStatusText","abcdefghijk\n01234567890\nignored")[0]),"Drone status did not retain original two ten-character lines");
                drone.mainInventory().setItem(0,new ItemStack(Items.EMERALD,3));drone.setSelectedSlot(0);
                CompoundTag saved=droneEntity.saveWithoutId(new CompoundTag());
                near(saved.getFloat("targetX"),drone.getTarget().x,"Saved drone target X");near(saved.getFloat("targetY"),drone.getTarget().y,"Saved drone target Y");near(saved.getFloat("targetZ"),drone.getTarget().z,"Saved drone target Z");
                require(saved.getUUID("ownerUuid").equals(actor.getUUID())&&saved.getString("statusText").equals("abcdefghij\n0123456789"),"Real drone NBT lost owner/status");
                Connector energy=(Connector)droneControl;energy.changeBuffer(-energy.globalBuffer());energy.changeBuffer(321);int local=(int)energy.localBuffer();
                require(local>0,"Actual drone connector was not charged");int firstDrop=drops.size();actor.setShiftKeyDown(true);actor.setItemInHand(InteractionHand.MAIN_HAND,item("wrench"));
                require(droneEntity.interact(actor,InteractionHand.MAIN_HAND).consumesAction()&&droneEntity.isRemoved(),"Actual sneaking wrench interaction did not dismantle drone");
                List<Drop> actual=drops.subList(firstDrop,drops.size());
                Drop assembled=actual.stream().filter(drop->drop.stack.is(item("drone").getItem())).findFirst().orElseThrow(()->new AssertionError("Dismantling produced no real drone item"));
                require(assembled.entity.isAddedToLevel()&&!assembled.entity.isRemoved()&&assembled.stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("oc:storedEnergy")==local,"Dismantled item did not preserve actual local control energy before runtime removal");
                require(actual.stream().anyMatch(drop->drop.entity.isAddedToLevel()&&!drop.entity.isRemoved()&&drop.stack.is(Items.EMERALD)&&drop.stack.getCount()==3),"Dismantling lost actual inventory drops");
                results.add("PASS D15.actions actual clicked-face placement/owner, cancelled spawn cleanup, public entity Context, quarter-block move target, acceleration/status/light callbacks, real NBT and wrench dismantling local energy/inventory");
                results.add("REUSE D15/D36 native flight/movement, fluid/controller/upgrades and cold-process state contracts remain the existing evidence; this contract adds no firmware or stand-in hosts");
                return true;
            }
            throw new AssertionError("Unknown agent contract phase "+phase);
        }
        UseOnContext droneUse(){return new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(dronePad).add(0,.5,0),Direction.UP,dronePad,false));}
        void fail(Throwable error){results.add("FAIL robot.drone phase="+phase+" ticks="+ticks+": "+error);LOGGER.error("ROBOT_DRONE phase={} ticks={}",phase,ticks,error);}
        void cleanup(){
            if(robot!=null)robot.machine().stop();for(Entity entity:spawnedAgents)if(!entity.isRemoved())entity.discard();
            for(Drop drop:drops)if(!drop.entity.isRemoved())drop.entity.discard();
            var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);
            for(var entry:entries){if(level.getBlockEntity(entry.getKey()) instanceof net.minecraft.world.Container inventory)inventory.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}
            for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);
        }
    }
}
