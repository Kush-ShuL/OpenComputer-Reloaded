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
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.SidedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Actual printer-generated items, bounded work-queue checks and their normal world behavior. */
public final class PrintingContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-printing-contract");
    private static Run active;private static boolean observing;
    private PrintingContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){
        require(active==null,"A printing contract is already active");if(!observing){observing=true;NeoForge.EVENT_BUS.addListener(PrintingContract::joined);}
        Run run=new Run(level,position.immutable(),results);active=run;try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){Run run=active;if(run==null)return;try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}active=null;run.cleanup();}
    public static void close(){Run run=active;active=null;if(run!=null){run.results.add("FAIL printing: server stopped before the actual printer contract completed");run.cleanup();}}
    private static void joined(EntityJoinLevelEvent event){Run run=active;if(run!=null&&run.dropping&&event.getLevel()==run.level&&!event.loadedFromDisk()&&event.getEntity() instanceof ItemEntity item)run.drops.add(item);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void equal(double actual,double expected,String message){require(Math.abs(actual-expected)<=Math.max(1e-7,Math.abs(expected)*1e-10),message+": expected="+expected+", actual="+actual);}
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing printing item "+name);return descriptor.createItemStack(1);}
    private static CompoundTag tag(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static String text(Object value){return value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):String.valueOf(value);}
    @FunctionalInterface private interface Operation{void run()throws Exception;}
    private static void rejected(Operation operation,String message)throws Exception{try{operation.run();}catch(IllegalArgumentException error){require(error.getMessage()!=null&&error.getMessage().contains(message),"Unexpected printer error "+error);return;}throw new AssertionError("Printer accepted instead of "+message);}
    private static final class Run {
        final ServerLevel level;final BlockPos position;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();final List<ItemEntity> drops=new ArrayList<>();
        Case host;Container inventory;Component printer;ManagedEnvironment environment;Connector power;IItemHandler input;FakePlayer actor;
        BlockPos printerPos,printPos;BlockEntity print,beacon;ItemStack actual=ItemStack.EMPTY,crafted=ItemStack.EMPTY;
        int ticks,phase,waitTicks,materialCost,inkCost,materialPerItem,inkPerItem,blockedMaterial,blockedInk;
        double initialEnergy,pieceEnergy,blockedEnergy;long pressedAt;boolean dropping,cleaned;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;}
        void remember(BlockPos pos){
            if(restored.containsKey(pos))return;if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
            require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Printing fixture refuses occupied cell "+pos);restored.put(pos.immutable(),level.getBlockState(pos));
        }
        void place(BlockPos pos,BlockState state){remember(pos);require(level.setBlock(pos,state,3),"Could not place printing fixture block "+pos);}
        void start(){
            require(API.config!=null&&!API.config.getBoolean("power.ignorePower"),"Printer proof requires actual enabled power");
            place(position,li.cil.oc.api.Items.get("case1").block().defaultBlockState());printerPos=position.east();place(printerPos,li.cil.oc.api.Items.get("printer").block().defaultBlockState());
            require(level.getBlockEntity(position) instanceof Case&&level.getBlockEntity(printerPos) instanceof Container&&level.getBlockEntity(printerPos) instanceof Environment,"Actual Case/printer SDK interfaces are unavailable");
            host=(Case)level.getBlockEntity(position);inventory=(Container)level.getBlockEntity(printerPos);var node=((Environment)level.getBlockEntity(printerPos)).node();
            require(node instanceof Component&&node.host() instanceof ManagedEnvironment&&host.machine().node() instanceof Connector,"Actual printer exposes no component/environment/Case energy");printer=(Component)node;environment=(ManagedEnvironment)node.host();power=(Connector)host.machine().node();
            actor=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"OC_PrintProof"));actor.getAbilities().instabuild=true;actor.setPos(position.getX()+.5,position.getY()+1,position.getZ()-2);
        }
        Object[] call(String method,Object... args)throws Exception{return printer.invoke(method,host.machine(),args);}
        CompoundTag state(){CompoundTag state=new CompoundTag();environment.save(state,level.registryAccess());return state;}
        boolean busy()throws Exception{return "busy".equals(text(call("status")[0]));}
        void template()throws Exception{
            call("reset");Object[] invalid=call("commit");require(invalid[0]==null&&"model invalid".equals(text(invalid[1])),"Empty printer model committed");
            rejected(()->call("setCollidable",false,"bad"),"boolean");Object[] collision=call("isCollidable");require(Boolean.TRUE.equals(collision[0])&&Boolean.TRUE.equals(collision[1]),"Invalid collision call partially changed the template");
            rejected(()->call("addShape",0,0,0,0,1,1,"minecraft:block/stone"),"empty block");
            int maximum=((Number)call("getMaxShapeCount")[0]).intValue();require(maximum>0&&maximum<=64,"Printing proof requires a finite normal complexity setting");
            for(int i=0;i<=maximum;i++)require(Boolean.TRUE.equals(call("addShape",0,0,0,1,1,1,"minecraft:block/stone")[0]),"Original last allowed shape insertion changed");
            require(((Number)call("getShapeCount")[0]).intValue()==maximum+1,"Equal shapes were deduplicated instead of retaining original independent entries");Object[] tooComplex=call("addShape",0,0,0,1,1,1,"minecraft:block/stone");require(tooComplex[0]==null&&"model too complex".equals(text(tooComplex[1])),"Original complexity rejection changed");require(call("commit")[0]==null,"Over-complex model committed");call("reset");
            call("setLabel","OC printing proof");call("setTooltip","first line\nsecond line");call("setRedstoneEmitter",7);call("setLightLevel",3);call("setButtonMode",true);call("setCollidable",true,false);
            call("addShape",0,0,0,8,4,16,"minecraft:block/stone",0x336699);call("addShape",12,0,0,16,4,16,"minecraft:block/oak_planks");call("addShape",0,12,0,16,16,16,"minecraft:block/glass",true,0xAA7733);
            Object[] counts=call("getShapeCount");require(((Number)counts[0]).intValue()==2&&((Number)counts[1]).intValue()==1,"Off/on shape arguments changed");
            materialCost=(int)((1792/2+Math.max(0,API.config.getInt("printer.customRedstoneCost")))*Math.max(0,API.config.getDouble("printer.noclipMultiplier")));inkCost=1504/6;
            materialPerItem=API.config.getInt("printer.materialValue");inkPerItem=API.config.getInt("printer.inkValue");require(materialPerItem*8>2*materialCost&&inkPerItem>2*inkCost,"Configured materials cannot cover the bounded two-piece proof");
            input=level.getCapability(Capabilities.ItemHandler.BLOCK,printerPos,Direction.WEST);require(input!=null&&input.getSlots()==3,"Actual printer lacks its three-slot sided inventory capability");
            require(input.insertItem(0,item("chamelium").copyWithCount(8),false).isEmpty()&&input.insertItem(1,item("inkcartridge"),false).isEmpty(),"Actual printer rejected normal Chamelium/cartridge input");
            double cost=API.config.getDouble("power.cost.printerModel"),rate=API.config.getDouble("power.printerTickAmount");require(cost>0&&rate>=1&&Math.ceil(cost/rate)<500,"Configured printing time exceeds the bounded two-piece proof");
            double last=cost%rate;pieceEnergy=last>0&&last<1?cost+1-last:cost;power.changeBuffer(Double.POSITIVE_INFINITY);initialEnergy=power.globalBuffer();require(initialEnergy>pieceEnergy*2,"Actual printer network cannot hold two pieces' energy");
            require(Boolean.TRUE.equals(call("commit",1)[0]),"Valid actual printer template did not commit");
        }
        boolean tick()throws Exception{
            require(++ticks<1300,"Printing contract exceeded its bounded jobs/button/beacon intervals");if(ticks<3)return false;
            if(phase==0){
                require(!host.machine().isRunning()&&printer.network()==host.machine().node().network()&&printer.isNeighborOf(host.machine().node()),"Actual printer did not join its physical stopped Case");
                SidedEnvironment sided=(SidedEnvironment)level.getBlockEntity(printerPos);for(Direction side:Direction.values())require(sided.sidedNode(side)==(side==Direction.UP?null:printer),"Original printer top connection rule changed");template();phase=1;return false;
            }
            if(phase==1){
                if(inventory.getItem(2).isEmpty()||!inventory.getItem(0).isEmpty())return false;require(!busy()&&inventory.getItem(2).getCount()==1,"Actual first job did not deliver exactly one piece");actual=inventory.getItem(2).copy();
                require(actual.is(item("print").getItem())&&"OC printing proof".equals(actual.getHoverName().getString()),"Actual printer did not generate its labeled print item");CompoundTag saved=state();
                require(saved.getInt("oc:amountMaterial")==materialPerItem*8-materialCost&&saved.getInt("oc:amountInk")==inkPerItem-inkCost,"Actual template charged the wrong volume/surface/redstone/noclip material or ink cost");equal(initialEnergy-power.globalBuffer(),pieceEnergy,"Actual first print energy cost");
                require(inventory.getItem(1).is(item("inkcartridgeempty").getItem()),"Actual cartridge consumption did not leave its container");require(input.extractItem(1,1,false).is(item("inkcartridgeempty").getItem()),"Sided extraction did not release the non-ink cartridge remainder");
                // The blocking stack is a public inventory boundary setup copied from this
                // actual printer-generated item. No PrintData or expected result NBT is made.
                inventory.setItem(2,actual.copyWithCount(actual.getMaxStackSize()));require(Boolean.TRUE.equals(call("commit",1)[0]),"Actual second job would not commit behind its compatible full output stack");initialEnergy=power.globalBuffer();phase=2;return false;
            }
            if(phase==2){
                if(!busy())return false;CompoundTag saved=state();if(saved.getDouble("oc:remaining")>0)return false;
                require(inventory.getItem(2).getCount()==actual.getMaxStackSize(),"Second piece overwrote the actual full output boundary");equal(initialEnergy-power.globalBuffer(),pieceEnergy,"Actual second print energy cost");
                blockedMaterial=saved.getInt("oc:amountMaterial");blockedInk=saved.getInt("oc:amountInk");require(blockedMaterial==materialPerItem*8-2*materialCost&&blockedInk==inkPerItem-2*inkCost,"Blocked second piece charged more than once");
                require(input.insertItem(0,item("chamelium"),false).isEmpty()&&input.insertItem(1,new ItemStack(Items.BLACK_DYE),false).isEmpty(),"Could not install real waiting-input controls");blockedEnergy=power.globalBuffer();call("reset");
                CompoundTag pending=state();require(pending.contains("oc:output"),"Actual completed pending piece was not persisted");environment.load(pending,level.registryAccess());phase=3;return false;
            }
            if(phase==3){
                CompoundTag saved=state();require(busy()&&inventory.getItem(2).getCount()==actual.getMaxStackSize(),"Blocked real piece vanished after reset/save/load");equal(power.globalBuffer(),blockedEnergy,"Completed blocked piece consumed extra energy");
                require(saved.getInt("oc:amountMaterial")==blockedMaterial&&saved.getInt("oc:amountInk")==blockedInk&&inventory.getItem(0).getCount()==1&&inventory.getItem(1).is(Items.BLACK_DYE),"Completed full-output wait consumed materials/ink or converted more inputs");
                if(++waitTicks<3)return false;inventory.removeItem(2,actual.getMaxStackSize());phase=4;return false;
            }
            if(phase==4){
                if(inventory.getItem(2).isEmpty())return false;require(inventory.getItem(2).getCount()==1&&ItemStack.isSameItemSameComponents(inventory.getItem(2),actual),"Unblocking did not deliver exactly the captured real piece after model reset");equal(power.globalBuffer(),blockedEnergy,"Pending delivery charged another energy tick");CompoundTag saved=state();
                require(saved.getInt("oc:amountMaterial")==blockedMaterial+materialPerItem&&saved.getInt("oc:amountInk")==blockedInk+inkPerItem/10,"Actual Chamelium/dye input conversion did not resume after unblocking");
                int before=saved.getInt("oc:amountMaterial");ItemStack recycle=inventory.removeItem(2,1);require(input.insertItem(0,recycle,false).isEmpty(),"Printer rejected its actual generated recyclable piece");blockedMaterial=before;phase=5;return false;
            }
            if(phase==5){
                require(inventory.getItem(0).isEmpty()&&state().getInt("oc:amountMaterial")==blockedMaterial+(int)(materialCost*API.config.getDouble("printer.recycleRate")),"Actual generated print recycling changed its original configured refund");
                results.add("PASS printing.queue real Chamelium/cartridge/dye inputs, exact volume/surface/redstone/noclip costs, two physically powered jobs, cartridge remainder/extraction, compatible full-output wait with zero additional charges/conversion, captured pending registry save/load/reset/resume and actual-print recycling");
                worldPrint();phase=6;return false;
            }
            if(phase==6){
                if(level.getGameTime()-pressedAt<21)return false;require(!print.getUpdateTag(level.registryAccess()).getBoolean("oc:state"),"Actual button print did not return off after its 20-tick schedule");
                require(level.getBlockState(printPos).getSignal(level,printPos,Direction.UP)==0&&!level.getBlockState(printPos).getCollisionShape(level,printPos).isEmpty(),"Returning off did not restore real redstone/collision state");
                if(!(beacon instanceof BeaconBlockEntity))return false;int beaconLevel=beacon.saveWithoutMetadata(level.registryAccess()).getInt("Levels");if(beaconLevel<1)return false;
                require(beaconLevel==1,"Actual printed beacon foundation has the wrong level");
                CompoundTag saved=print.saveWithFullMetadata(level.registryAccess());level.getBlockState(printPos).useWithoutItem(level,actor,new BlockHitResult(Vec3.atCenterOf(printPos),Direction.UP,printPos,false));print.loadWithComponents(saved,level.registryAccess());
                require(!print.getUpdateTag(level.registryAccess()).getBoolean("oc:state")&&saved.getIntArray("oc:rs.input").length==6,"Actual print save/load lost off state/input history");
                actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);dropping=true;try{require(actor.gameMode.destroyBlock(printPos),"Normal survival destruction failed to break the actual print");}finally{dropping=false;}phase=7;return false;
            }
            for(ItemEntity drop:drops)if(drop.isAddedToLevel()&&!drop.isRemoved()&&drop.getItem().getCount()==1&&ItemStack.isSameItemSameComponents(drop.getItem(),crafted)){
                results.add("PASS printing.world actual recipe light/beacon upgrades preserve the generated template, normal item placement exposes rotated shape/support/light, on-state no-clip and redstone, 20-tick button return, public NBT state/input restore, real beacon activation and survival model-data drop");
                results.add("LIMIT printing: shared Windows client acceptance owns printed textures/tint/preview/menu and rebound sneak-key display; multiplayer/sounds are not observed here, and PIN pressurePlate has data only with no ladder behavior");LOGGER.info("PRINTING PASS actual printer jobs and generated printed-world behavior");return true;
            }
            throw new AssertionError("Normal print destruction produced no accepted matching actual item drop: "+drops.stream().map(ItemEntity::getItem).toList());
        }
        ItemStack craft(ItemStack source,ItemStack ingredient){
            CraftingInput input=CraftingInput.of(2,1,List.of(source.copyWithCount(1),ingredient));var recipe=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level);require(recipe.isPresent(),"Actual print upgrade crafting recipe is unavailable");ItemStack output=recipe.get().value().assemble(input,level.registryAccess());require(!output.isEmpty(),"Actual print recipe produced no upgraded item");return output;
        }
        void worldPrint(){
            crafted=craft(craft(actual,new ItemStack(Items.GLOWSTONE_DUST)),new ItemStack(Items.IRON_BLOCK));CompoundTag original=tag(actual),upgraded=tag(crafted);
            require(upgraded.getBoolean("isBeaconBase")&&upgraded.getByte("lightLevel")==original.getByte("lightLevel")+1&&upgraded.getList("stateOff",10).equals(original.getList("stateOff",10))&&upgraded.getList("stateOn",10).equals(original.getList("stateOn",10)),"Actual upgrading recipes lost the generated template or increments");
            printPos=position.east(5);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(dx!=0||dz!=0)place(printPos.offset(dx,0,dz),Blocks.IRON_BLOCK.defaultBlockState());
            remember(printPos);actor.getAbilities().instabuild=true;actor.setPos(printPos.getX()+.5,printPos.getY()+1,printPos.getZ()-2);actor.setYRot(0);actor.setItemInHand(InteractionHand.MAIN_HAND,crafted.copy());
            ItemStack stack=actor.getMainHandItem();require(stack.getItem() instanceof BlockItem,"Generated print is not placeable");require(((BlockItem)stack.getItem()).place(new BlockPlaceContext(actor,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(printPos),Direction.UP,printPos,false))).consumesAction(),"Actual generated print placement failed");
            print=level.getBlockEntity(printPos);require(print instanceof Rotatable,"Actual print lost its original public orientation marker");Rotatable rotated=(Rotatable)print;Direction old=rotated.facing();ItemStack wrench=item("wrench");actor.setItemInHand(InteractionHand.MAIN_HAND,wrench);
            require(wrench.getItem().onItemUseFirst(wrench,new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(printPos),Direction.UP,printPos,false))).consumesAction()&&rotated.facing()!=old,"Actual scrench did not rotate generated printed geometry");
            actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            BlockState state=level.getBlockState(printPos);equal(state.getCollisionShape(level,printPos).bounds().maxY,.25,"Actual off-state shape height");require(state.getSignal(level,printPos,Direction.UP)==0&&state.getLightEmission(level,printPos)==upgraded.getByte("lightLevel"),"Actual placed print redstone/light differs from its generated data");
            require(state.useWithoutItem(level,actor,new BlockHitResult(Vec3.atCenterOf(printPos),Direction.UP,printPos,false)).consumesAction(),"Actual print did not activate through its normal interaction entry");
            require(print.getUpdateTag(level.registryAccess()).getBoolean("oc:state")&&state.getCollisionShape(level,printPos).isEmpty()&&state.getSignal(level,printPos,Direction.UP)==7&&state.isFaceSturdy(level,printPos,Direction.UP),"Actual on-state no-clip/emission/support differs from its template");
            require(!state.useWithoutItem(level,actor,new BlockHitResult(Vec3.atCenterOf(printPos),Direction.UP,printPos,false)).consumesAction(),"Already-active button accepted another toggle");pressedAt=level.getGameTime();
            place(printPos.above(),Blocks.BEACON.defaultBlockState());beacon=level.getBlockEntity(printPos.above());
        }
        void fail(Throwable error){results.add("FAIL printing: "+error);LOGGER.error("PRINTING FAIL",error);}
        void cleanup(){
            if(cleaned)return;cleaned=true;List<BlockPos> positions=new ArrayList<>(restored.keySet());Collections.reverse(positions);
            for(BlockPos pos:positions)try{if(level.getBlockEntity(pos) instanceof Container container)container.clearContent();level.setBlock(pos,restored.get(pos),3);}catch(Throwable error){LOGGER.warn("Could not restore printing fixture cell {}",pos,error);}
            for(ItemEntity drop:drops)if(!drop.isRemoved())drop.discard();for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);
        }
    }
}
