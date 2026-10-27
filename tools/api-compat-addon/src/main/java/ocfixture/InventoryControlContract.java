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
import li.cil.oc.api.Driver;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Database;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Original inventory-controller and transposer operations on real world hardware. */
public final class InventoryControlContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-inventory-control");
    private static Run active;
    private InventoryControlContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){
        Run run=new Run(level,position,results);active=run;
        try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){
        Run run=active;if(run==null)return;
        try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}
        active=null;run.cleanup();
    }
    public static void close(){Run run=active;active=null;if(run!=null)run.cleanup();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing inventory hardware "+name);return descriptor.createItemStack(1);}
    private static void number(Object value,int expected,String message){require(value instanceof Number n&&n.intValue()==expected,message+": "+value);}
    private static final class Run {
        final ServerLevel level;final BlockPos position;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();
        final Set<Long> forced=new LinkedHashSet<>();final Set<Integer> oldItems=new LinkedHashSet<>();
        final List<Node> owned=new ArrayList<>();
        Robot robot;Case machineHost;BlockPos robotChest,transposerPos;int ticks;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;}
        void remember(BlockPos pos){
            if(restored.containsKey(pos))return;
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));
            level.getChunkAt(pos);require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Inventory fixture cell is occupied: "+pos);
            restored.put(pos.immutable(),level.getBlockState(pos));
        }
        void place(BlockPos pos,BlockState state){remember(pos);require(level.setBlock(pos,state,3),"Cannot place inventory fixture block "+pos);}
        void start(){
            for(ItemEntity entity:level.getEntitiesOfClass(ItemEntity.class,bounds()))oldItems.add(entity.getId());
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(),true,level.registryAccess()));
            ItemStack preset=null;
            for(ItemStack stack:CreativeTab.instance.getDisplayItems())if(stack.is(item("robot").getItem())&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("oc:tier")==3){preset=stack.copy();break;}
            require(preset!=null&&preset.getItem() instanceof BlockItem,"No original configured creative Robot");
            remember(position);FakePlayer actor=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"OC_InventoryProof"));
            actor.getAbilities().instabuild=true;actor.setPos(position.getX()+.5,position.getY(),position.getZ()-2);actor.setItemInHand(InteractionHand.MAIN_HAND,preset);
            require(((BlockItem)preset.getItem()).place(new BlockPlaceContext(actor,InteractionHand.MAIN_HAND,preset,new BlockHitResult(Vec3.atCenterOf(position),Direction.UP,position,false))).consumesAction(),"Real configured Robot placement failed");
            require(level.getBlockEntity(position) instanceof Robot,"Placed inventory host is not the real Robot");robot=(Robot)level.getBlockEntity(position);
            robotChest=position.relative(robot.toGlobal(Direction.SOUTH));place(robotChest,Blocks.CHEST.defaultBlockState());
            BlockPos casePos=position.east(12);place(casePos,li.cil.oc.api.Items.get("case3").block().defaultBlockState());
            require(level.getBlockEntity(casePos) instanceof Case,"Placed transfer Context is not the real Case");machineHost=(Case)level.getBlockEntity(casePos);
            transposerPos=position.east(16);place(transposerPos,li.cil.oc.api.Items.get("transposer").block().defaultBlockState());
            place(transposerPos.west(),Blocks.CHEST.defaultBlockState());place(transposerPos.east(),Blocks.CHEST.defaultBlockState());
            remember(transposerPos.above());remember(transposerPos.below());
        }
        Component component(String name){
            for(Node node:robot.machine().node().network().nodes())if(node instanceof Component component&&name.equals(component.name()))return component;
            throw new AssertionError("Real Robot did not install "+name);
        }
        boolean tick()throws Exception{
            if(++ticks<3)return false;
            require(!robot.machine().isRunning()&&!machineHost.machine().isRunning(),"Inventory proof must not boot fixture firmware");
            controller();transposer();return true;
        }
        void controller()throws Exception{
            Component controller=component("inventory_controller");Container cargo=robot.mainInventory();cargo.clearContent();robot.setSelectedSlot(2);
            ItemStack sample=new ItemStack(Items.DIAMOND,37);sample.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Inventory sample"));cargo.setItem(2,sample);
            ItemStack disk=item("databaseupgrade1");var driver=Driver.driverFor(disk,robot.getClass());require(driver!=null,"Real Robot rejects database driver");
            ManagedEnvironment environment=driver.createEnvironment(disk,robot);require(environment instanceof Database,"Database driver did not create the actual environment");
            owned.add(environment.node());robot.machine().node().connect(environment.node());Database database=(Database)environment;
            Object[] stored=controller.invoke("storeInternal",robot.machine(),3,environment.node().address(),1);
            require(stored.length==1&&Boolean.FALSE.equals(stored[0]),"Initial database store should report an empty destination");
            require(cargo.getItem(2).getCount()==37&&database.getStackInSlot(0).getCount()==1&&ItemStack.isSameItemSameComponents(cargo.getItem(2),database.getStackInSlot(0)),"Database store consumed cargo or lost its data components");
            ItemStack transferUpgrade=item("transposer");var transferDriver=Driver.driverFor(transferUpgrade,robot.getClass());
            require(transferDriver!=null&&transferDriver.tier(transferUpgrade)==0&&"upgrade".equals(transferDriver.slot(transferUpgrade)),"Transposer item lost its original tier-one upgrade driver");
            ManagedEnvironment transferEnvironment=transferDriver.createEnvironment(transferUpgrade,robot);require(transferEnvironment!=null&&transferEnvironment.node() instanceof Component,"Transposer upgrade created no actual component");
            owned.add(transferEnvironment.node());robot.machine().node().connect(transferEnvironment.node());
            number(((Component)transferEnvironment.node()).invoke("getInventorySize",robot.machine(),robot.toGlobal(Direction.SOUTH).get3DDataValue())[0],27,"Mounted transposer upgrade world inventory size");
            Container chest=(Container)level.getBlockEntity(robotChest);Object[] dropped=controller.invoke("dropIntoSlot",robot.machine(),3,1,7);
            require(Boolean.TRUE.equals(dropped[0])&&cargo.getItem(2).getCount()==30&&chest.getItem(0).getCount()==7,"Real sided drop did not move exactly seven items");
            cargo.setItem(2,new ItemStack(Items.STONE,64));Object[] sucked=controller.invoke("suckFromSlot",robot.machine(),3,1,2);
            number(sucked[0],2,"Suck result");require(cargo.getItem(0).isEmpty()&&cargo.getItem(3).is(Items.DIAMOND)&&cargo.getItem(3).getCount()==2&&chest.getItem(0).getCount()==5,"Suck did not continue after selected slot before wrapping to slot zero");
            cargo.setItem(2,ItemStack.EMPTY);Object[] empty=controller.invoke("dropIntoSlot",robot.machine(),0,1);
            require(empty.length==1&&Boolean.FALSE.equals(empty[0]),"Empty selected slot did not return the original single false value");
            try{controller.invoke("dropIntoSlot",robot.machine(),3,1,1,null);throw new AssertionError("Explicit nil access side was accepted");}catch(IllegalArgumentException expected){require(expected.getMessage().contains("integer"),"Explicit nil failed with an unrelated error");}
            results.add("PASS inventory.controller actual Robot callbacks copy a 37-item component-bearing stack into a count-one database without consumption, execute the mounted transposer upgrade, drop seven, suck two in selected-to-end order, and retain empty/explicit-nil results");
        }
        void transposer()throws Exception{
            require(level.getBlockEntity(transposerPos) instanceof Environment,"Real transposer exposes no public Environment");
            Node node=((Environment)level.getBlockEntity(transposerPos)).node();require(node instanceof Component&&node instanceof Connector,"Real transposer lacks its component/connector");
            machineHost.machine().node().connect(node);Component transposer=(Component)node;Connector power=(Connector)node;
            power.changeBuffer(Double.NEGATIVE_INFINITY);power.changeBuffer(Double.POSITIVE_INFINITY);
            require(power.globalBuffer()>4*API.config.getDouble("power.cost.transposer"),"Actual transposer network has insufficient charged capacity");
            Container source=(Container)level.getBlockEntity(transposerPos.west()),sink=(Container)level.getBlockEntity(transposerPos.east());source.setItem(0,new ItemStack(Items.DIRT,12));
            double before=power.globalBuffer();Object[] moved=transposer.invoke("transferItem",machineHost.machine(),4,5,5,1,3);
            number(moved[0],5,"Transposer item result");require(source.getItem(0).getCount()==7&&sink.getItem(2).getCount()==5,"Real transposer moved the wrong source/destination slots");
            double cost=API.config.getDouble("power.cost.transposer");require(Math.abs(before-power.globalBuffer()-cost)<1e-8,"Transfer did not charge its configured energy once");
            before=power.globalBuffer();Object[] zero=transposer.invoke("transferItem",machineHost.machine(),4,5,0,1,3);number(zero[0],0,"Zero-count transfer result");
            require(Math.abs(before-power.globalBuffer()-cost)<1e-8,"Zero-count operation did not retain the original energy cost");
            placeWater();Object[] fluid=transposer.invoke("transferFluid",machineHost.machine(),1,0,1000);
            require(Boolean.TRUE.equals(fluid[0]),"Actual source-water transposer transfer failed");number(fluid[1],1000,"Transferred source-water amount");
            require(level.getFluidState(transposerPos.above()).isEmpty()&&level.getFluidState(transposerPos.below()).isSource(),"Source-water transfer did not change both actual world cells");
            power.changeBuffer(Double.NEGATIVE_INFINITY);Object[] empty=transposer.invoke("transferItem",machineHost.machine(),4,5,1);
            require(empty.length==2&&empty[0]==null&&"not enough energy".equals(text(empty[1]))&&source.getItem(0).getCount()==7&&sink.getItem(2).getCount()==5,"Unpowered transfer changed inventory or its original error");
            results.add("PASS transposer.real actual block/Case Context moves five chest items to explicit slot, charges once including zero-count operation, transfers one world-source bucket, and refuses an unpowered transfer without changing inventory");
        }
        void placeWater(){require(level.setBlock(transposerPos.above(),Blocks.WATER.defaultBlockState(),3),"Cannot place actual source water");}
        String text(Object value){return value instanceof byte[] bytes?new String(bytes,java.nio.charset.StandardCharsets.UTF_8):String.valueOf(value);}
        AABB bounds(){return new AABB(position).inflate(4).expandTowards(20,0,0);}
        void fail(Throwable error){results.add("FAIL inventory.control "+error);LOGGER.error("INVENTORY_CONTROL failed",error);}
        void cleanup(){
            for(Node node:owned)node.remove();owned.clear();
            if(robot!=null){robot.machine().stop();robot.mainInventory().clearContent();robot.equipmentInventory().clearContent();}
            if(machineHost!=null)machineHost.machine().stop();
            var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);
            for(var entry:entries){if(level.getBlockEntity(entry.getKey()) instanceof Container inventory)inventory.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}restored.clear();
            for(ItemEntity entity:level.getEntitiesOfClass(ItemEntity.class,bounds()))if(!oldItems.contains(entity.getId()))entity.discard();
            for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);forced.clear();
        }
    }
}
