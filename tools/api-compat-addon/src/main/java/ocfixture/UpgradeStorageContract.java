package ocfixture;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.DriverItem;
import li.cil.oc.api.driver.item.Chargeable;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Database;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real battery item capabilities and database driver round trips, linked only against the published SDK. */
public final class UpgradeStorageContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-upgrade-storage-contract");
    private static Run active;
    private UpgradeStorageContract(){}

    public static void start(ServerLevel level,BlockPos position,List<String> results)throws Exception{
        require(active==null,"An upgrade storage contract is already active");
        Run run=new Run(level,position.immutable(),results);active=run;
        try{run.start();}catch(Exception|Error error){active=null;run.cleanup();throw error;}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){
        Run run=active;if(run==null)return;
        try{if(!run.tick())return;}
        catch(Throwable error){run.results.add("FAIL upgrade.storage: "+error);LOGGER.error("UPGRADE_STORAGE FAIL",error);}
        active=null;run.cleanup();
    }
    public static void close(){
        Run run=active;active=null;
        if(run!=null){run.results.add("FAIL upgrade.storage: server stopped before the real Case/database contract completed");run.cleanup();}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void equal(double actual,double expected,String message){
        require(Math.abs(actual-expected)<=Math.max(1e-8,Math.abs(expected)*1e-10),message+": expected="+expected+", actual="+actual);
    }
    private static ItemStack item(String name){
        var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing actual upgrade storage fixture item "+name);
        return descriptor.createItemStack(1);
    }
    private static CompoundTag tag(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static void same(ItemStack actual,ItemStack expected,String message){
        require(actual.getCount()==expected.getCount()&&ItemStack.isSameItemSameComponents(actual,expected),message);
    }
    private static Object first(Object[] result,String message){require(result!=null&&result.length==1,message);return result[0];}
    private static int integer(Object[] result,String message){Object value=first(result,message);require(value instanceof Number,message);return ((Number)value).intValue();}
    private static String hash(ItemStack stack,ServerLevel level)throws Exception{
        // Independent Minecraft serialization and JDK digest; never call the production hash/ItemUtils helper.
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        NbtIo.writeCompressed((CompoundTag)stack.saveOptional(level.registryAccess()),bytes);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
    }

    private record DatabaseUnit(ItemStack stack,DriverItem driver,ManagedEnvironment environment,Database database,Component component){}
    private static final class Run {
        final ServerLevel level;final BlockPos position;final List<String> results;
        final List<Node> ownedNodes=new ArrayList<>();
        final List<DatabaseUnit> databases=new ArrayList<>();
        BlockState restored;Case host;boolean forced,cleaned;int ticks;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;}

        void start()throws Exception{
            require(API.config!=null&&!API.config.getBoolean("power.ignorePower"),"Battery energy proof requires the real enabled OC power network");
            forced=level.setChunkForced(position.getX()>>4,position.getZ()>>4,true);level.getChunkAt(position);
            require(level.isEmptyBlock(position)&&level.getBlockEntity(position)==null,"Upgrade storage fixture refuses an occupied Case cell");
            var descriptor=li.cil.oc.api.Items.get("casecreative");require(descriptor!=null&&descriptor.block()!=null,"Actual creative computer Case block is unavailable");
            restored=level.getBlockState(position);require(level.setBlock(position,descriptor.block().defaultBlockState(),3),"Could not place real upgrade storage Case");
            require(level.getBlockEntity(position) instanceof Case,"Placed block does not expose the real published Case interface");host=(Case)level.getBlockEntity(position);
            require(host.machine()!=null&&host.machine().host()==host,"Storage callbacks require the actual placed Case's own Machine");
            batteries();
        }
        DriverItem driver(ItemStack stack){
            DriverItem driver=Driver.driverFor(stack,host.getClass());require(driver!=null,"Actual Case-context driver rejected "+stack);return driver;
        }
        ManagedEnvironment create(DriverItem driver,ItemStack stack){
            ManagedEnvironment environment=driver.createEnvironment(stack,host);
            require(environment!=null&&environment.node()!=null,"Real upgrade driver created no environment/node");ownedNodes.add(environment.node());return environment;
        }

        void batteries(){
            List<Double> capacities=API.config.getDoubleList("power.buffer.batteryUpgrades");
            double ratio=API.config.getDouble("power.value.ForgeEnergy")/1000d;
            require(capacities.size()==3&&Double.isFinite(ratio)&&ratio>0,"Battery proof requires three configured capacities and a positive FE conversion");
            for(int tier=0;tier<3;tier++){
                String prefix="battery tier "+(tier+1);double capacity=capacities.get(tier);
                ItemStack stack=item("batteryupgrade"+(tier+1));DriverItem driver=driver(stack);
                require(stack.getItem() instanceof Chargeable,prefix+" is not an actual OC-chargeable item");Chargeable chargeable=(Chargeable)stack.getItem();
                IEnergyStorage fe=stack.getCapability(Capabilities.EnergyStorage.ITEM);
                require(fe!=null&&fe.canReceive()&&fe.canExtract(),prefix+" has no real bidirectional FE item capability");
                int maximum=(int)Math.clamp(capacity*ratio,0,Integer.MAX_VALUE);
                require(maximum>=4&&fe.getMaxEnergyStored()==maximum&&fe.getEnergyStored()==0,prefix+" FE capacity/initial charge is wrong");
                int receive=Math.min(97,maximum/4),extract=Math.max(1,receive/2);
                ItemStack before=stack.copy();require(fe.receiveEnergy(receive,true)==receive,prefix+" simulated FE reception returned the wrong amount");same(stack,before,prefix+" receive SIM changed item components");
                require(fe.receiveEnergy(receive,false)==receive&&fe.getEnergyStored()==receive,prefix+" real FE reception did not store its reported energy");
                equal(driver.dataTag(stack).getCompound("node").getDouble("buffer"),receive/ratio,prefix+" FE reception did not persist OC charge");
                before=stack.copy();require(fe.extractEnergy(extract,true)==extract,prefix+" simulated extraction returned the wrong amount");same(stack,before,prefix+" extract SIM changed item components");
                require(fe.extractEnergy(extract,false)==extract&&fe.getEnergyStored()==receive-extract,prefix+" real FE extraction did not consume its reported energy");
                before=stack.copy();require(chargeable.charge(stack,Double.POSITIVE_INFINITY,true)==Double.POSITIVE_INFINITY,prefix+" infinite receive SIM changed unused-energy semantics");same(stack,before,prefix+" infinite receive SIM wrote data");
                require(chargeable.charge(stack,Double.POSITIVE_INFINITY,false)==Double.POSITIVE_INFINITY,prefix+" infinite OC fill must report unused infinity");
                equal(driver.dataTag(stack).getCompound("node").getDouble("buffer"),capacity,prefix+" +Infinity did not fill to capacity");
                require(fe.getEnergyStored()==maximum,prefix+" OC fill was not visible through its existing FE capability");
                ManagedEnvironment environment=create(driver,stack);environment.load(driver.dataTag(stack),level.registryAccess());
                require(environment.node() instanceof Connector&&!(environment.node() instanceof Component),prefix+" must provide a real power connector without a Lua component");
                Connector battery=(Connector)environment.node();equal(battery.localBufferSize(),capacity,prefix+" mounted capacity changed");equal(battery.localBuffer(),capacity,prefix+" mount lost stored charge");
                double consumed=Math.min(7,capacity/8);equal(battery.changeBuffer(-consumed),0,prefix+" mounted connector could not consume charge");
                CompoundTag saved=new CompoundTag();environment.save(saved,level.registryAccess());driver.saveDataTag(stack,saved);environment.node().remove();
                ManagedEnvironment remounted=create(driver,stack);remounted.load(driver.dataTag(stack),level.registryAccess());
                equal(((Connector)remounted.node()).localBuffer(),capacity-consumed,prefix+" node save/item commit/remount lost remaining energy");
                remounted.node().remove();before=stack.copy();
                require(chargeable.charge(stack,Double.NEGATIVE_INFINITY,true)==Double.NEGATIVE_INFINITY,prefix+" infinite extraction SIM changed unused semantics");same(stack,before,prefix+" infinite extraction SIM wrote data");
                require(chargeable.charge(stack,Double.NEGATIVE_INFINITY,false)==Double.NEGATIVE_INFINITY,prefix+" infinite OC drain must report unused negative infinity");
                equal(driver.dataTag(stack).getCompound("node").getDouble("buffer"),0,prefix+" -Infinity did not empty the stored charge");require(fe.getEnergyStored()==0,prefix+" OC drain was not visible to FE");
                results.add("PASS battery.storage tier="+(tier+1)+" capacity="+capacity+" actual FE receive/extract/SIM, OC +/-Infinity and real connector item-save/remount preserve charge");
            }
        }

        DatabaseUnit database(int tier)throws Exception{
            int size=new int[]{9,25,81}[tier];String prefix="database tier "+(tier+1);
            ItemStack stack=item("databaseupgrade"+(tier+1));DriverItem driver=driver(stack);ManagedEnvironment environment=create(driver,stack);
            require(environment instanceof Database&&environment.node() instanceof Component,prefix+" driver did not expose the real Database/component interfaces");
            Database database=(Database)environment;require(database.size()==size,prefix+" has wrong capacity");
            ItemStack sample=new ItemStack(Items.DIAMOND,37);CompoundTag sampleTag=new CompoundTag();sampleTag.putString("contract","upgrade-storage-"+tier);
            sample.set(DataComponents.CUSTOM_DATA,CustomData.of(sampleTag));sample.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Storage sample "+tier));sample.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
            ItemStack expected=sample.copyWithCount(1);database.setStackInSlot(0,sample);database.setStackInSlot(size-1,new ItemStack(Items.EMERALD,12));
            same(database.getStackInSlot(0),expected,prefix+" set did not limit count to one and preserve components");require(sample.getCount()==1,prefix+" set did not normalize the owned input sample as the original inventory does");
            ItemStack returned=database.getStackInSlot(0);returned.setCount(3);returned.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("caller mutated copy"));
            same(database.getStackInSlot(0),expected,prefix+" get leaked its mutable backing ItemStack");
            // No environment.save call precedes this read: set must immediately update the source item itself.
            CompoundTag data=driver.dataTag(stack);var entries=data.getList("oc:items",Tag.TAG_COMPOUND);
            require(entries.size()==size,prefix+" did not immediately write its source item oc:items");same(ItemStack.parseOptional(level.registryAccess(),entries.getCompound(0)),expected,prefix+" immediate item data lost the sample's components");
            CompoundTag legacy=new CompoundTag();environment.save(legacy);driver.saveDataTag(stack,legacy);
            same(ItemStack.parseOptional(level.registryAccess(),driver.dataTag(stack).getList("oc:items",Tag.TAG_COMPOUND).getCompound(0)),expected,prefix+" legacy save/driver save discarded the live sample");
            environment.load(legacy);same(database.getStackInSlot(0),expected,prefix+" legacy load discarded the live sample");
            environment.node().remove();DriverItem restoredDriver=driver(stack);ManagedEnvironment restored=create(restoredDriver,stack);
            require(restored instanceof Database&&restored.node() instanceof Component,prefix+" driver recreation lost its published database interface");Database restoredDatabase=(Database)restored;
            same(restoredDatabase.getStackInSlot(0),expected,prefix+" recreate from original source item lost the unsaved sample");same(restoredDatabase.getStackInSlot(size-1),new ItemStack(Items.EMERALD),prefix+" final slot did not survive immediate persistence");
            Component component=(Component)restored.node();
            // Node.connect is invoked on an existing network member, just as the production Case does.
            // A freshly reconstructed database is detached and cannot initiate this connection itself.
            host.machine().node().connect(component);
            require(component.network()!=null&&component.network()==host.machine().node().network()
                    &&component.canBeSeenFrom(host.machine().node()),prefix+" is not on its real Case network");
            results.add("PASS database.storage tier="+(tier+1)+" slots="+size+" count-one components/copy isolation, immediate source-item persistence, legacy load/save and driver recreation preserve samples");
            return new DatabaseUnit(stack,restoredDriver,restored,restoredDatabase,component);
        }

        boolean tick()throws Exception{
            require(++ticks<=100,"Upgrade storage contract exceeded 100 actual world ticks");Machine machine=host.machine();
            if(ticks<2)return false;
            // The real Case supplies registry/network/Context behavior; this storage regression does not boot another VM.
            require(level.getBlockEntity(position)==host&&machine.host()==host&&machine.node().network()!=null,
                    "Actual placed Case did not initialize its own network through normal world ticks");
            for(int tier=0;tier<3;tier++)databases.add(database(tier));
            callbacks();
            LOGGER.info("UPGRADE_STORAGE PASS three real FE batteries and three persistent SDK databases/callbacks");return true;
        }

        void callbacks()throws Exception{
            Machine context=host.machine();String emptyHash=hash(ItemStack.EMPTY,level);
            for(DatabaseUnit unit:databases){
                String expected=hash(unit.database.getStackInSlot(0),level);
                require(expected.equals(first(unit.component.invoke("computeHash",context,1),"computeHash returned no SHA")),"Database SHA differs from independent compressed Minecraft NBT/JDK SHA-256");
                require(integer(unit.component.invoke("indexOf",context,expected),"indexOf returned no index")==1&&unit.database.findStackWithHash(expected)==0,"Database item hash lookup changed its Lua/SDK index bases");
                require(emptyHash.equals(first(unit.component.invoke("computeHash",context,2),"Empty computeHash returned no SHA")),"Empty database slot did not hash ItemStack.EMPTY compressed NBT");
                require(integer(unit.component.invoke("indexOf",context,emptyHash),"Empty indexOf returned no index")==2&&unit.database.findStackWithHash(emptyHash)==1,"Empty-slot hash was not searchable");
            }
            results.add("PASS database.hash three actual Component.invoke computeHash/indexOf pairs match independent SHA256 for component-bearing samples and ItemStack.EMPTY, including Lua/SDK index bases");
            DatabaseUnit first=databases.get(0),second=databases.get(1),third=databases.get(2);
            require(Boolean.FALSE.equals(first(first.component.invoke("copy",context,1,3),"Local database copy returned no overwrite flag")),"Copy to an empty local slot reported overwrite");same(first.database.getStackInSlot(2),first.database.getStackInSlot(0),"Actual local copy lost item components");
            boolean rejected=false;
            try{first.component.invoke("copy",context,1,4,(Object)null);}catch(IllegalArgumentException expected){rejected=true;}
            require(rejected&&first.database.getStackInSlot(3).isEmpty(),"Explicit nil third copy argument was accepted or changed its target");
            require(Boolean.FALSE.equals(first(first.component.invoke("copy",context,1,5,second.component.address()),"Cross-database copy returned no flag")),"Copy to an empty remote slot reported overwrite");same(second.database.getStackInSlot(4),first.database.getStackInSlot(0),"Same-network copy did not transfer the actual entry");
            require(Boolean.TRUE.equals(first(first.component.invoke("copy",context,1,5,second.component.address()),"Overwrite copy returned no flag")),"Remote copy did not report an existing entry as overwritten");
            require(integer(second.component.invoke("clone",context,third.component.address()),"Clone returned no copied slot count")==25,"Clone did not limit to the source's 25 slots");
            for(int slot=0;slot<25;slot++)same(third.database.getStackInSlot(slot),second.database.getStackInSlot(slot),"Clone failed to copy or clear target slot "+slot);
            same(third.database.getStackInSlot(80),new ItemStack(Items.EMERALD),"Clone changed trailing slots beyond the source capacity");
            require(integer(third.component.invoke("clone",context,first.component.address()),"Small-target clone returned no count")==9,"Clone did not limit to the target's nine slots");
            for(int slot=0;slot<9;slot++)same(first.database.getStackInSlot(slot),third.database.getStackInSlot(slot),"Small-target clone failed at slot "+slot);
            var persisted=first.driver.dataTag(first.stack).getList("oc:items",Tag.TAG_COMPOUND);
            same(ItemStack.parseOptional(level.registryAccess(),persisted.getCompound(0)),third.database.getStackInSlot(0),"Callback clone did not immediately persist the target item's components");
            results.add("PASS database.callbacks real Case-context copy/clone preserve components, reject explicit nil address, report overwrite, honor both capacity limits and persist the same-network target item immediately");
        }

        void cleanup(){
            if(cleaned)return;cleaned=true;
            if(host!=null)host.machine().stop();for(Node node:ownedNodes)node.remove();ownedNodes.clear();databases.clear();
            if(restored!=null){if(level.getBlockEntity(position) instanceof net.minecraft.world.Container inventory)inventory.clearContent();level.setBlock(position,restored,3);}
            if(forced)level.setChunkForced(position.getX()>>4,position.getZ()>>4,false);
        }
    }
}
