package ocfixture;

import forestry.api.IForestryApi;
import forestry.api.apiculture.ForestryActivityTypes;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBee;
import forestry.api.arboriculture.genetics.ITree;
import forestry.api.core.circuits.CircuitHolder;
import forestry.api.core.circuits.ICircuit;
import forestry.api.core.genetics.ForestrySpeciesTypes;
import forestry.api.core.genetics.IIndividual;
import forestry.api.core.genetics.IMutation;
import forestry.api.core.genetics.ISpeciesType;
import forestry.api.core.genetics.alleles.Allele;
import forestry.api.core.genetics.alleles.AllelePair;
import forestry.api.core.genetics.alleles.BeeChromosomes;
import forestry.api.core.genetics.alleles.ButterflyChromosomes;
import forestry.api.core.genetics.alleles.ForestryAlleles;
import forestry.api.core.genetics.alleles.TreeChromosomes;
import forestry.api.lepidopterology.genetics.IButterfly;
import forestry.core.engine.circuits.EnumCircuitBoardType;
import forestry.core.engine.circuits.ItemCircuitBoard;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DriverItem;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Adapter;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real Forestry blocks, official public factory objects and the actual Adapter/native Lua conversion path. */
public final class ForestryContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-forestry-contract");
    private static final String MARKER="ocfixture:forestry";
    private static Run active;
    private ForestryContract(){}
    public static void register(){Driver.add(new SampleDriver());}
    public static void start(ServerLevel level,BlockPos position,List<String> results){Run run=new Run(level,position.immutable(),results);active=run;try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}}
    public static boolean isRunning(){return active!=null;}
    public static void tick(){Run run=active;if(run==null)return;try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}active=null;run.cleanup();}
    public static void close(){Run run=active;active=null;if(run!=null)run.cleanup();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing real Forestry host item "+name);return descriptor.createItemStack(1);}
    private static ResourceLocation forestry(String name){return ResourceLocation.fromNamespaceAndPath("forestry",name);}

    /** Actual SDK addon driver: its card returns foreign objects for OC's registered converters. */
    private static final class SampleDriver implements DriverItem {
        public boolean worksWith(ItemStack stack){return stack.is(Items.PAPER)&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBoolean(MARKER);}
        public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){require(host instanceof Case,"Forestry sample card requires the real Case host");return new Samples(active,(Case)host);}
        public String slot(ItemStack stack){return "card";}
        public int tier(ItemStack stack){return 0;}
        public CompoundTag dataTag(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data");}
    }

    public static final class Samples extends AbstractManagedEnvironment {
        private final Run run;private final Case host;
        Samples(Run run,Case host){this.run=run;this.host=host;setNode(Network.newNode(this,Visibility.Network).withComponent("forestry_contract",Visibility.Network).create());}
        private void caller(Context context){require(run!=null&&context==host.machine(),"Foreign-object callback did not come from the actual Case Context");}
        @Callback(direct=true,limit=32)
        public Object[] metadata(Context context,Arguments args){caller(context);return new Object[]{run.metadata};}
        @Callback(direct=true,limit=32)
        public Object[] sample(Context context,Arguments args){caller(context);String name=args.checkString(0);require(run.samples.containsKey(name),"Unknown actual Forestry sample "+name);run.called.add(name);return new Object[]{run.samples.get(name)};}
        @Callback(direct=true)
        public Object[] report(Context context,Arguments args){caller(context);require("forestry-complete".equals(args.checkString(0)),"Unexpected Forestry completion marker");Set<String> expected=run.enabled?Set.of("plain","bee","tree","butterfly","bee_item","circuit","mutation"):Set.of("bee");require(run.called.equals(expected),"Native Lua skipped real Forestry samples: "+run.called);run.completed.set(true);return new Object[]{true};}
    }

    private static final class Run {
        final ServerLevel level;final BlockPos position,adapterPosition,analyzerPosition,housingPosition;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        final Map<String,Object> samples=new LinkedHashMap<>(),metadata=new LinkedHashMap<>();final Set<String> called=ConcurrentHashMap.newKeySet();final AtomicBoolean completed=new AtomicBoolean();
        final boolean enabled=API.config.getStringList("integration.modBlacklist").stream().noneMatch(name->name.trim().equalsIgnoreCase("forestry"));
        Case computer;Adapter adapter;Container analyzer;IBeeHousing housing;int ticks;boolean booted;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;adapterPosition=position.east();analyzerPosition=position.east(2);housingPosition=adapterPosition.south();}
        void remember(BlockPos pos){if(restored.containsKey(pos))return;if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Forestry fixture cell is occupied: "+pos);restored.put(pos.immutable(),level.getBlockState(pos));}
        BlockEntity place(BlockPos pos,Block block){remember(pos);require(level.setBlock(pos,block.defaultBlockState(),3),"Cannot place real Forestry fixture block "+pos);return level.getBlockEntity(pos);}
        Block block(String name){var id=forestry(name);require(BuiltInRegistries.BLOCK.containsKey(id),"Official Forestry block is unavailable: "+id);return BuiltInRegistries.BLOCK.get(id);}
        void install(ItemStack stack){for(int slot=0;slot<computer.getContainerSize();slot++)if(computer.getItem(slot).isEmpty()&&computer.canPlaceItem(slot,stack)){computer.setItem(slot,stack);return;}throw new AssertionError("Real Case rejected Forestry fixture hardware "+stack);}
        void start(){
            require(ModList.get().isLoaded("forestry")&&ModList.get().isLoaded("forestrybutterflies"),"Install the selected official core/butterfly runtimes for the full original conversion branch");require(BIOS.getBytes(StandardCharsets.UTF_8).length<=4096,"Forestry BIOS exceeds real EEPROM capacity");
            require(place(position,li.cil.oc.api.Items.get("case3").block()) instanceof Case,"Forestry Context is not a real Case");computer=(Case)level.getBlockEntity(position);require(place(adapterPosition,li.cil.oc.api.Items.get("adapter").block()) instanceof Adapter,"Forestry host is not a real Adapter");adapter=(Adapter)level.getBlockEntity(adapterPosition);
            BlockEntity analyzerTile=place(analyzerPosition,block("analyzer")),housingTile=place(housingPosition,block("apiary"));require(analyzerTile instanceof Container&&housingTile instanceof IBeeHousing,"Official analyzer/apiary do not expose their real inventory/housing contracts");analyzer=(Container)analyzerTile;housing=(IBeeHousing)housingTile;
            createSamples();ItemStack specimen=((IBee)samples.get("plain")).createStack(BeeLifeStage.DRONE);require(analyzer.canPlaceItem(2,specimen),"Actual analyzer rejects its ordinary input slot");analyzer.setItem(2,specimen);housing.getBeeInventory().setQueen(((IBee)samples.get("bee")).createStack(BeeLifeStage.QUEEN));housing.getBeeInventory().setDrone(((IBee)samples.get("plain")).createStack(BeeLifeStage.DRONE));
            ItemStack cpu=item("cpu3");var cpuDriver=Driver.driverFor(cpu,computer.getClass());require(cpuDriver instanceof MutableProcessor,"Actual CPU cannot select its architecture");var architecture=li.cil.oc.api.Machine.architectures().stream().filter(type->"Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type))).findFirst().orElseThrow(()->new AssertionError("Forestry conversion proof requires actual native Lua 5.3"));((MutableProcessor)cpuDriver).setArchitecture(cpu,architecture);install(cpu);install(item("ram6"));install(li.cil.oc.api.Items.registerEEPROM("Forestry contract",BIOS.getBytes(StandardCharsets.UTF_8),new byte[0],false));
            ItemStack card=new ItemStack(Items.PAPER);CustomData.update(DataComponents.CUSTOM_DATA,card,tag->tag.putBoolean(MARKER,true));require(Driver.driverFor(card,computer.getClass())!=null,"ForestryContract.register was not called");install(card);
        }
        void createSamples(){
            var genetics=IForestryApi.INSTANCE.getGeneticManager();ISpeciesType<?,?> beeType=genetics.getSpeciesType(ForestrySpeciesTypes.BEE),treeType=genetics.getSpeciesType(ForestrySpeciesTypes.TREE),butterflyType=genetics.getSpeciesType(ForestrySpeciesTypes.BUTTERFLY);require(beeType!=null&&treeType!=null&&butterflyType!=null,"Real Forestry species registration is incomplete");
            IBee plain=genetics.createDefaultIndividual(ForestrySpeciesTypes.BEE);require(!plain.isAnalyzed(),"Official default individual should be unanalysed");samples.put("plain",plain);
            IBee bee=plain.copyWithGenome(plain.getGenome().copyWithPairs(Map.of(BeeChromosomes.SPEED,new AllelePair<>(ForestryAlleles.SPEED_FAST,ForestryAlleles.SPEED_SLOW),BeeChromosomes.ACTIVITY,AllelePair.both(Allele.reference(ForestryActivityTypes.CATHEMERAL)))));bee.setMate(plain.getGenome());bee.setPristine(false);bee.analyze();samples.put("bee",bee);samples.put("bee_item",bee.createStack(BeeLifeStage.QUEEN));
            ITree tree=genetics.createDefaultIndividual(ForestrySpeciesTypes.TREE);tree=(ITree)tree.copyWithGenome(tree.getGenome().copyWithPairs(Map.of(TreeChromosomes.HEIGHT,new AllelePair<>(ForestryAlleles.HEIGHT_LARGE,ForestryAlleles.HEIGHT_SMALL))));tree.analyze();samples.put("tree",tree);
            IButterfly butterfly=genetics.createDefaultIndividual(ForestrySpeciesTypes.BUTTERFLY);butterfly=(IButterfly)butterfly.copyWithGenome(butterfly.getGenome().copyWithPairs(Map.of(ButterflyChromosomes.SIZE,new AllelePair<>(ForestryAlleles.SIZE_LARGE,ForestryAlleles.SIZE_SMALL))));butterfly.analyze();samples.put("butterfly",butterfly);
            var mutations=beeType.getMutations().getAllMutations();require(!mutations.isEmpty(),"Actual Forestry datapack has no bee mutations");IMutation<?> mutation=mutations.stream().<IMutation<?>>map(value->value).filter(value->!value.getSpecialConditions().isEmpty()).findFirst().orElse(mutations.getFirst());samples.put("mutation",mutation);
            var circuits=IForestryApi.INSTANCE.getCircuitManager();CircuitHolder holder=circuits.getCircuitHolders().stream().findFirst().orElseThrow(()->new AssertionError("Real Forestry has no registered circuit"));var layout=circuits.getLayout(holder.layoutId());require(layout!=null,"Real circuit holder names an absent layout");ItemStack circuit=ItemCircuitBoard.createCircuitboard(EnumCircuitBoardType.ENHANCED,layout,new ICircuit[]{holder.circuit(),null});require(circuits.getCircuitBoard(circuit)!=null,"Official circuitboard factory returned no board");samples.put("circuit",circuit);
            metadata.put("enabled",enabled);metadata.put("bee",bee.getSpecies().id().toString());metadata.put("tree",tree.getSpecies().id().toString());metadata.put("butterfly",butterfly.getSpecies().id().toString());metadata.put("rawBee",bee.toString());metadata.put("speedA",bee.getGenome().getActiveValue(BeeChromosomes.SPEED));metadata.put("speedB",bee.getGenome().getInactiveValue(BeeChromosomes.SPEED));metadata.put("heightA",tree.getGenome().getActiveValue(TreeChromosomes.HEIGHT));metadata.put("heightB",tree.getGenome().getInactiveValue(TreeChromosomes.HEIGHT));metadata.put("sizeA",butterfly.getGenome().getActiveValue(ButterflyChromosomes.SIZE));metadata.put("sizeB",butterfly.getGenome().getInactiveValue(ButterflyChromosomes.SIZE));metadata.put("circuit",holder.circuit().getId());metadata.put("child",mutation.getResult().id().toString());metadata.put("childName",mutation.getResult().getDisplayName().getString());metadata.put("parent1",mutation.getFirstParent().id().toString());metadata.put("parent2",mutation.getSecondParent().id().toString());metadata.put("parentName1",mutation.getFirstParent().getDisplayName().getString());metadata.put("parentName2",mutation.getSecondParent().getDisplayName().getString());metadata.put("chance",mutation.getChance());
        }
        boolean component(String name){if(computer.node().network()==null)return false;for(Node node:computer.node().network().nodes())if(node instanceof Component value&&name.equals(value.name()))return true;return false;}
        boolean tick(){
            require(++ticks<=240,"Actual Forestry contract exceeded 240 ticks; booted="+booted+", complete="+completed.get());if(computer.node() instanceof Connector power)power.changeBuffer(Double.POSITIVE_INFINITY);
            if(!booted){if(ticks<3||!component("forestry_contract"))return false;if(enabled&&(!component("forestry_analyzer")||!component("bee_housing")))return false;if(!enabled)require(!component("forestry_analyzer")&&!component("bee_housing"),"Blacklisted actual Adapter still mounted Forestry drivers");require(computer.machine().componentCount()<=computer.machine().maxComponents(),"Actual Forestry host exceeds its CPU capacity");require(computer.machine().start(),"Cannot boot real Forestry native Case: "+computer.machine().lastError());booted=true;return false;}
            require(computer.machine().lastError()==null,"Actual Forestry native VM failed: "+computer.machine().lastError());if(!completed.get()){require(computer.machine().isRunning(),"Actual Forestry VM stopped before its real callbacks completed");return false;}if(computer.machine().isRunning())return false;
            results.add(enabled?"PASS forestry.native actual native Lua53 Case and Adapter call all nine analyzer/apiary methods; real unanalysed/analyzed bee, tree and optional butterfly objects preserve hidden/active/inactive genes, scalar/area shapes, mated/pristine/glint fields, mutation/species tables, real item individuals and official-factory circuit IDs through the registered converters":"PASS forestry.blacklist actual blacklist prevents both Adapter environments and native conversion of a real Forestry individual");
            results.add("LIMIT forestry: this finite proof does not wait for a full breeding/analysis production cycle or recheck shared inventory/persistence/client coverage");return true;
        }
        void fail(Throwable error){results.add("FAIL forestry.integration ticks="+ticks+": "+error);LOGGER.error("FORESTRY_CONTRACT failed",error);}
        void cleanup(){if(computer!=null){computer.machine().stop();computer.clearContent();}var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries){if(level.getBlockEntity(entry.getKey()) instanceof Container container)container.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);}
    }

    private static final String BIOS="""
        local p
        while not p do for a in component.list('forestry_contract') do p=a break end if not p then computer.pullSignal(.05) end end
        local function s(k)return component.invoke(p,'sample',k)end
        local m=component.invoke(p,'metadata')
        local function find(k)for a,n in component.list(k)do if n==k then return a end end end
        if not m.enabled then
          assert(not find('forestry_analyzer') and not find('bee_housing'))
          assert(s('bee')==m.rawBee)
        else
          local a,h=assert(find('forestry_analyzer')),assert(find('bee_housing'))
          local u,b,t,f=s('plain'),s('bee'),s('tree'),s('butterfly')
          assert(type(u)=='table' and not u.isAnalyzed and u.active==nil and u.inactive==nil)
          local function genes(v,kind,id,fields)
            assert(v.type==kind and v.ident==id and v.isAnalyzed and type(v.displayName)=='string' and type(v.isSecret)=='boolean')
            for _,g in ipairs{v.active,v.inactive}do assert(type(g)=='table') for k in fields:gmatch('%S+')do assert(g[k]~=nil,kind..':'..k)end end
          end
          genes(b,'bee',m.bee,'species speed lifespan fertility temperatureTolerance neverSleeps humidityTolerance toleratesRain caveDwelling flowerProvider flowering effect territory')
          genes(t,'tree',m.tree,'species fireproof height fertility fruits yield sappiness effect maturation girth')
          genes(f,'butterfly',m.butterfly,'species size speed lifespan metabolism fertility temperatureTolerance neverSleeps humidityTolerance tolerantFlyer fireResist flowerProvider effect cocoon')
          assert(b.canSpawn and not b.isNatural and type(b.hasEffect)=='boolean' and type(b.isAlive)=='boolean' and type(b.generation)=='number')
          assert(type(b.health)=='number' and type(b.maxHealth)=='number' and b.active.neverSleeps and b.inactive.neverSleeps)
          assert(math.abs(b.active.speed-m.speedA)<1e-6 and math.abs(b.inactive.speed-m.speedB)<1e-6 and m.speedA~=m.speedB)
          assert(math.abs(t.active.height-m.heightA)<1e-6 and math.abs(t.inactive.height-m.heightB)<1e-6 and m.heightA~=m.heightB)
          assert(math.abs(f.active.size-m.sizeA)<1e-6 and math.abs(f.inactive.size-m.sizeB)<1e-6 and m.sizeA~=m.sizeB)
          assert(type(b.active.territory)=='table' and #b.active.territory==3)
          assert(s('bee_item').individual.ident==m.bee)
          local c=s('circuit') assert(type(c.circuits)=='table' and #c.circuits==1 and c.circuits[1]==m.circuit)
          local x=s('mutation') assert(x.allele1.uid==m.parent1 and x.allele2.uid==m.parent2 and math.abs(x.chance-m.chance)<1e-6 and type(x.specialConditions)=='table')
          assert(not component.invoke(a,'isWorking') and component.invoke(a,'getProgress')==1)
          assert(component.invoke(a,'getIndividualOnDisplay').ident==m.bee)
          assert(type(component.invoke(h,'canBreed'))=='boolean')
          assert(component.invoke(h,'getDrone').ident==m.bee and component.invoke(h,'getQueen').ident==m.bee)
          local rows=component.invoke(h,'getBeeBreedingData') local found=false
          for _,v in pairs(rows)do if v.result==m.childName and v.allele1==m.parentName1 and v.allele2==m.parentName2 then found=true end end assert(found)
          found=false for _,v in pairs(component.invoke(h,'listAllSpecies'))do if v.uid==m.child then found=true end end assert(found)
          found=false for _,v in pairs(component.invoke(h,'getBeeParents',m.child))do if v.allele1.uid==m.parent1 and v.allele2.uid==m.parent2 then found=true end end assert(found)
          assert(not pcall(component.invoke,h,'getBeeParents'))
        end
        assert(component.invoke(p,'report','forestry-complete'))
        computer.shutdown()
        """;
}
