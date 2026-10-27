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
import li.cil.oc.api.Driver;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real production inventories and a three-disk cabinet, using real Case machine contexts. */
public final class ProductionDevicesContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-production-devices");
    private static Run active;
    private ProductionDevicesContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){
        Run run=new Run(level,position.immutable(),results);active=run;
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
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing production hardware "+name);return descriptor.createItemStack(1);}
    private static Object first(Object[] result,String operation){require(result!=null&&result.length>0,operation+" returned no value");return result[0];}
    private static String text(Object value){return value instanceof byte[] bytes?new String(bytes,java.nio.charset.StandardCharsets.UTF_8):String.valueOf(value);}

    private static final class Run {
        final ServerLevel level;final BlockPos position,assemblerPos,disassemblerPos,chestPos,raidPos,movedPos;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();
        final Set<Long> forced=new LinkedHashSet<>();final Set<Integer> oldItems=new LinkedHashSet<>();final List<Case> hosts=new ArrayList<>();
        Case assemblyHost,disassemblyHost,raidHost,movedHost;
        Container assembler,disassembler,chest;BlockEntity cabinet;Component filesystem;
        FakePlayer player;AbstractContainerMenu inputMenu;ItemStack originalMap;
        final byte[] payload={0,1,127,(byte)128,(byte)255};
        String raidAddress;int ticks,phase;
        boolean navigationDone;
        Run(ServerLevel level,BlockPos position,List<String> results){
            this.level=level;this.position=position;this.results=results;
            assemblerPos=position;disassemblerPos=position.south(4);chestPos=disassemblerPos.below();raidPos=position.east(5);movedPos=raidPos.south(4);
        }
        void remember(BlockPos pos){
            if(restored.containsKey(pos))return;
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));
            level.getChunkAt(pos);require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Production fixture cell is occupied: "+pos);
            restored.put(pos.immutable(),level.getBlockState(pos));
        }
        BlockEntity place(BlockPos pos,BlockState state){remember(pos);require(level.setBlock(pos,state,3),"Cannot place production fixture block "+pos);return level.getBlockEntity(pos);}
        BlockEntity place(String name,BlockPos pos){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null&&descriptor.block()!=null,"Missing production block "+name);return place(pos,descriptor.block().defaultBlockState());}
        Case host(BlockPos pos){BlockEntity entity=place("case3",pos);require(entity instanceof Case,"Production Context host is not an actual Case");Case host=(Case)entity;hosts.add(host);return host;}
        Container inventory(BlockEntity entity){require(entity instanceof Container,"Actual production device has no inventory");return (Container)entity;}
        void start(){
            for(ItemEntity entity:level.getEntitiesOfClass(ItemEntity.class,bounds()))oldItems.add(entity.getId());
            assemblyHost=host(assemblerPos.west());assembler=inventory(place("assembler",assemblerPos));
            disassemblyHost=host(disassemblerPos.above());disassembler=inventory(place("disassembler",disassemblerPos));
            chest=inventory(place(chestPos,Blocks.CHEST.defaultBlockState()));
            raidHost=host(raidPos.west());cabinet=place("raid",raidPos);
            movedHost=host(movedPos.west());remember(movedPos);
            player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"OC_ProductionProof"));
        }
        void put(Container inventory,int slot,ItemStack stack){require(inventory.canPlaceItem(slot,stack),"Actual device rejected fixture material at slot "+slot);inventory.setItem(slot,stack);}
        int comparator(){return level.getBlockState(assemblerPos).getAnalogOutputSignal(level,assemblerPos);}
        void assembler()throws Exception{
            require(comparator()==0,"Empty actual assembler comparator is not zero");
            put(assembler,0,item("case1"));put(assembler,16,item("cpu1"));put(assembler,17,item("ram1"));
            require(comparator()==10,"Valid idle actual assembler comparator is not ten");
            Node node=((Environment)level.getBlockEntity(assemblerPos)).node();
            require(node instanceof Component&&node.network()==assemblyHost.machine().node().network(),"Actual assembler is not connected to its neighboring Case");
            Object[] started=((Component)node).invoke("start",assemblyHost.machine());
            require(Boolean.TRUE.equals(first(started,"assembler.start"))&&assembler.isEmpty()&&comparator()==15,"Actual assembly did not consume its inputs and report working comparator fifteen");
            Object[] status=((Component)node).invoke("status",assemblyHost.machine());
            require(status!=null&&status.length==2&&"busy".equals(text(status[0]))&&status[1] instanceof Number,"Actual assembler lost its real busy/progress callback");
            results.add("PASS production.comparator real Case-context assembly transitions empty zero -> valid ten -> working fifteen and consumes the actual case/CPU/RAM once");
        }
        ItemStack craftNavigation(){
            originalMap=MapItem.create(level,position.getX(),position.getZ(),(byte)0,true,false);
            originalMap.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Production navigation map"));
            CompoundTag marker=new CompoundTag();marker.putString("production_map","keep-components");originalMap.set(DataComponents.CUSTOM_DATA,CustomData.of(marker));
            require(originalMap.get(DataComponents.MAP_ID)!=null,"Vanilla did not create an actual registered map ID");
            var navigation=item("navigationupgrade").getItem();
            for(var holder:level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)){
                var recipe=holder.value();if(!recipe.getResultItem(level.registryAccess()).is(navigation))continue;
                List<ItemStack> ingredients=new ArrayList<>();boolean usesMap=false,available=true;
                for(var ingredient:recipe.getIngredients()){
                    if(ingredient.isEmpty())ingredients.add(ItemStack.EMPTY);
                    else if(ingredient.test(originalMap)){ingredients.add(originalMap.copy());usesMap=true;}
                    else{ItemStack[] choices=ingredient.getItems();if(choices.length==0){available=false;break;}ingredients.add(choices[0].copy());}
                }
                if(!available||!usesMap)continue;
                for(int width=1;width<=3;width++)for(int height=1;height<=3;height++){
                    if(width*height<ingredients.size())continue;
                    List<ItemStack> layout=new ArrayList<>(ingredients);while(layout.size()<width*height)layout.add(ItemStack.EMPTY);
                    CraftingInput input=CraftingInput.of(width,height,layout);
                    var selected=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level);
                    if(selected.isEmpty())continue;
                    ItemStack result=selected.get().value().assemble(input,level.registryAccess());
                    if(result.is(navigation))return result;
                }
            }
            throw new AssertionError("No real navigation crafting recipe accepted the vanilla map input");
        }
        void startNavigation(){
            require(((Environment)level.getBlockEntity(disassemblerPos)).node().network()==disassemblyHost.machine().node().network(),"Actual disassembler did not accept its original top-side OC connection");
            ItemStack navigation=craftNavigation();require(disassembler.canPlaceItem(0,navigation),"Actual disassembler rejected the crafted navigation upgrade");
            var driver=Driver.driverFor(navigation);require(driver!=null,"Crafted navigation upgrade has no actual item driver");
            CompoundTag data=driver.dataTag(navigation);require(data!=null&&ItemStack.matches(originalMap,ItemStack.parseOptional(level.registryAccess(),data.getCompound("oc:map"))),"Actual navigation crafting recipe did not store the registered map and its components");
            player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            require(player.getAbilities().instabuild,"Actual menu player did not enter creative mode");
            player.setPos(disassemblerPos.getX()+.5,disassemblerPos.getY(),disassemblerPos.getZ()-2);
            require(level.getBlockEntity(disassemblerPos) instanceof MenuProvider,"Actual disassembler has no menu input path");
            inputMenu=((MenuProvider)level.getBlockEntity(disassemblerPos)).createMenu(71,player.getInventory(),player);
            require(inputMenu!=null,"Actual disassembler could not create its real input menu");
            inputMenu.getSlot(0).setByPlayer(navigation,inputMenu.getSlot(0).getItem());
            require(ItemStack.matches(navigation,disassembler.getItem(0)),"Actual player menu insertion did not retain the crafted navigation upgrade");
        }
        void navigation(){
            if(navigationDone)return;
            ItemStack recovered=ItemStack.EMPTY;int maps=0;
            for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(Items.FILLED_MAP)){
                recovered=chest.getItem(slot);maps+=recovered.getCount();
            }
            if(maps==0){
                CompoundTag state=level.getBlockEntity(disassemblerPos).saveWithoutMetadata(level.registryAccess());
                var queue=state.getList("oc:queue",net.minecraft.nbt.Tag.TAG_COMPOUND);
                require(queue.isEmpty()||state.getBoolean("oc:creativeJob"),"Actual creative menu input became an unpowered ordinary disassembly job: "+state);
                require(!disassembler.isEmpty()||!queue.isEmpty(),"Actual navigation disassembly finished without returning its stored map; disassembler="+state+", chest="+chestItems());
                return;
            }
            require(maps==1&&ItemStack.matches(originalMap,recovered),"Real navigation disassembly changed its registered MAP_ID, name, count or custom data");
            require(disassembler.isEmpty(),"Navigation upgrade input remained after actual disassembly");navigationDone=true;
            results.add("PASS production.navigation an actual crafting recipe stores a vanilla registered map, and real creative-menu disassembly returns the same MAP_ID and all item components through the adjacent chest");
        }
        List<String> chestItems(){List<String> items=new ArrayList<>();for(int slot=0;slot<chest.getContainerSize();slot++)if(!chest.getItem(slot).isEmpty())items.add(chest.getItem(slot).toString());return items;}
        Component filesystem(BlockEntity entity,Case context){
            require(entity instanceof Analyzable,"Actual RAID lacks its public analyzer entry");
            Node[] nodes=((Analyzable)entity).onAnalyze(player,Direction.NORTH,.5f,.5f,.5f);
            if(nodes==null||nodes.length==0||nodes[0]==null)return null;
            require(nodes[0] instanceof Component component&&"filesystem".equals(component.name()),"Actual RAID analyzer returned a different component");
            Component component=(Component)nodes[0];
            require(component.canBeSeenFrom(context.machine().node()),"Actual RAID filesystem is not visible in the neighboring real Case graph");
            return component;
        }
        void charge(Case context){Connector connector=(Connector)context.machine().node();connector.changeBuffer(Double.POSITIVE_INFINITY);require(connector.globalBuffer()>0,"Real Case graph has no chargeable filesystem capacity");}
        void write(Component filesystem,Case context)throws Exception{
            charge(context);Object handle=first(filesystem.invoke("open",context.machine(),"cabinet.bin","w"),"filesystem.open(write)");
            require(handle!=null,"Actual RAID returned a null writable descriptor");
            try{require(Boolean.TRUE.equals(first(filesystem.invoke("write",context.machine(),handle,payload),"filesystem.write")),"Actual RAID write failed");}
            finally{filesystem.invoke("close",context.machine(),handle);}
        }
        void read(Component filesystem,Case context)throws Exception{
            charge(context);Object handle=first(filesystem.invoke("open",context.machine(),"cabinet.bin","r"),"filesystem.open(read)");
            require(handle!=null,"Actual RAID returned a null readable descriptor");
            try{
                Object data=first(filesystem.invoke("read",context.machine(),handle,payload.length),"filesystem.read");
                require(data instanceof byte[] bytes&&Arrays.equals(bytes,payload),"Actual RAID binary data did not survive its lifecycle");
            }finally{filesystem.invoke("close",context.machine(),handle);}
        }
        void label(Component filesystem,Case context)throws Exception{
            require("fixture-array".equals(text(first(filesystem.invoke("getLabel",context.machine()),"filesystem.getLabel"))),"Actual RAID label changed");
        }
        void populateRaid(){Container inventory=inventory(cabinet);for(int slot=0;slot<3;slot++)put(inventory,slot,item("hdd1"));}
        void moveRaid(){
            BlockEntity previous=cabinet;Component previousFilesystem=filesystem;
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            player.setPos(raidPos.getX()+.5,raidPos.getY(),raidPos.getZ()-2);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
            require(player.gameMode.destroyBlock(raidPos)&&level.isEmptyBlock(raidPos),"Normal survival destruction did not remove the actual RAID cabinet");
            var dropped=level.getEntitiesOfClass(ItemEntity.class,new AABB(raidPos).inflate(2),entity->!oldItems.contains(entity.getId())&&entity.getItem().is(item("raid").getItem()));
            require(dropped.size()==1&&dropped.getFirst().getItem().getCount()==1,"Actual RAID did not produce exactly one normal cabinet drop");
            ItemStack stack=dropped.getFirst().getItem().copy();dropped.getFirst().discard();
            require(previousFilesystem.network()==null,"Destroyed cabinet retained its old filesystem network");
            player.setPos(movedPos.getX()+.5,movedPos.getY(),movedPos.getZ()-2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            require(stack.getItem() instanceof BlockItem&&((BlockItem)stack.getItem()).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,
                    new BlockHitResult(Vec3.atCenterOf(movedPos),Direction.UP,movedPos,false))).consumesAction(),"Actual dropped RAID BlockItem could not be placed at its new position");
            cabinet=level.getBlockEntity(movedPos);require(cabinet!=null&&cabinet!=previous,"Cabinet transport did not create a real replacement block entity");
        }
        boolean tick()throws Exception{
            if(++ticks>400)throw new AssertionError("Production device contract exceeded 400 actual world ticks; phase="+phase+", navigation="+level.getBlockEntity(disassemblerPos).saveWithoutMetadata(level.registryAccess())+", chest="+chestItems());
            for(Case host:hosts)require(!host.machine().isRunning(),"Production proof must not boot fixture firmware");
            if(phase==0){
                if(ticks<3)return false;assembler();startNavigation();populateRaid();phase=1;return false;
            }
            navigation();
            if(phase==1){
                filesystem=filesystem(cabinet,raidHost);if(filesystem==null)return false;
                require("fixture-array".equals(text(first(filesystem.invoke("setLabel",raidHost.machine(),"fixture-array"),"filesystem.setLabel"))),"Actual RAID refused its label");
                raidAddress=filesystem.address();write(filesystem,raidHost);read(filesystem,raidHost);
                results.add("PASS raid.io three real HDD items create the actual visible RAID filesystem, whose real Case-context callbacks label and write/read binary data");
                moveRaid();phase=2;return false;
            }
            if(phase==2){
                filesystem=filesystem(cabinet,movedHost);if(filesystem==null)return false;
                require(raidAddress.equals(filesystem.address()),"Normal cabinet drop/replacement changed the RAID address");label(filesystem,movedHost);read(filesystem,movedHost);
                Container inventory=inventory(cabinet);for(int slot=0;slot<3;slot++)require(!inventory.getItem(slot).isEmpty(),"Normal cabinet transport lost a member disk");
                results.add("PASS raid.transport normal survival drop and real BlockItem replacement at a new position retain all three disks, filesystem address, label and binary file");
                Component previous=filesystem;BlockEntity same=cabinet;cabinet.onChunkUnloaded();require(previous.network()==null,"RAID public unload retained its old filesystem node");cabinet.clearRemoved();cabinet.onLoad();
                require(level.getBlockEntity(movedPos)==same,"Public RAID lifecycle replaced the fixture instance");phase=3;return false;
            }
            if(phase==3){
                filesystem=filesystem(cabinet,movedHost);if(filesystem==null)return false;
                require(raidAddress.equals(filesystem.address()),"Same-instance public unload/onLoad changed the RAID address");label(filesystem,movedHost);read(filesystem,movedHost);
                results.add("PASS raid.lifecycle public onChunkUnloaded/onLoad on the same actual cabinet reattaches its saved volume with unchanged address, label and file");
                Component previous=filesystem;Container inventory=inventory(cabinet);ItemStack member=inventory.removeItem(0,1);
                require(!member.isEmpty()&&inventory.getItem(0).isEmpty()&&!inventory.getItem(1).isEmpty()&&!inventory.getItem(2).isEmpty(),"Actual member removal changed the wrong disks");
                require(previous.network()==null&&filesystem(cabinet,movedHost)==null,"Removing one actual member did not dismantle the RAID filesystem");
                put(inventory,0,member);phase=4;return false;
            }
            if(phase==4){
                filesystem=filesystem(cabinet,movedHost);if(filesystem==null)return false;
                require(!raidAddress.equals(filesystem.address())&&Boolean.FALSE.equals(first(filesystem.invoke("exists",movedHost.machine(),"cabinet.bin"),"filesystem.exists")),"Replacing a removed member restored the destroyed array instead of creating a fresh empty one");
                results.add("PASS raid.member removing a real member detaches the old filesystem; reinserting that disk creates a new address with no previous binary file");phase=5;
            }
            return phase==5&&navigationDone;
        }
        AABB bounds(){return new AABB(position).inflate(4).expandTowards(9,2,8);}
        void fail(Throwable error){results.add("FAIL production.devices phase="+phase+": "+error);LOGGER.error("PRODUCTION_DEVICES failed at phase {}",phase,error);}
        void cleanup(){
            if(inputMenu!=null&&player!=null)inputMenu.removed(player);
            for(Case host:hosts)host.machine().stop();
            if(player!=null)player.getInventory().clearContent();
            var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);
            for(var entry:entries){if(level.getBlockEntity(entry.getKey()) instanceof Container inventory)inventory.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}restored.clear();
            for(ItemEntity entity:level.getEntitiesOfClass(ItemEntity.class,bounds()))if(!oldItems.contains(entity.getId()))entity.discard();
            for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);forced.clear();
        }
    }
}
