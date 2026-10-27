package ocfixture;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.parts.automation.ExportBusPart;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Adapter;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Value;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Native Craftable/status userdata and a real AE-to-furnace job across two server JVMs. */
public final class AppliedEnergisticsPersistenceContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-ae2-persistence");
    private static final String PROCESS=UUID.randomUUID().toString(),MARKER="ocfixture:ae2_persistence_probe";
    private static Run active;
    private AppliedEnergisticsPersistenceContract(){}
    public static boolean enabled(){return Boolean.getBoolean("ocfixture.ae2Persistence");}
    public static void register(){Driver.add(new ProbeDriver());}
    public static void start(MinecraftServer server){
        try{active=new Run(server);active.start();}
        catch(Throwable error){if(active!=null)active.fail(error);else throw new IllegalStateException("AE persistence startup failed",error);}
    }
    public static void tick(){Run run=active;if(run==null||run.finished)return;try{run.tick();}catch(Throwable error){run.fail(error);}}
    public static void stopped(){
        Run run=active;if(run==null)return;
        if(run.pendingSuccess){run.report("PASS");LOGGER.info("AE2_PERSISTENCE_COMPLETE stage={} status=PASS",run.stage);}
        else if(!run.finished)run.fail(new AssertionError("Server stopped before AE persistence proof completed"));
        active=null;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing real OC hardware "+name);return descriptor.createItemStack(1);}
    private static final class Run {
        final MinecraftServer server;final ServerLevel level;
        final String stage=System.getProperty("ocfixture.ae2Stage"),runId=System.getProperty("ocfixture.ae2RunId");
        final Path directory=Path.of(System.getProperty("ocfixture.ae2Directory")).toAbsolutePath().normalize();
        final Properties manifest=new Properties();final List<String> evidence=new ArrayList<>();
        final BlockPos origin=new BlockPos(7168,96,7168),casePos=origin,adapterPos=origin.east(),controllerPos=origin.east(2),cellPos=origin.east(3);
        final BlockPos cpuPos=controllerPos.north(),providerPos=cellPos.north(),furnacePos=providerPos.below();
        final BlockPos lowerCablePos=providerPos.east(),middleCablePos=lowerCablePos.below(),bottomCablePos=middleCablePos.below(),importCablePos=furnacePos.below();
        final BlockPos exportCablePos=cellPos.south(),exportAdapterPos=exportCablePos.south(),outputPos=exportCablePos.east();
        final List<BlockPos> positions=List.of(casePos,adapterPos,controllerPos,cellPos,controllerPos.above(),cpuPos,providerPos,
            furnacePos,lowerCablePos,middleCablePos,bottomCablePos,importCablePos,exportCablePos,exportAdapterPos,outputPos);
        Case machine;ControllerBlockEntity controller;MEChestBlockEntity cell;FurnaceBlockEntity furnace;Probe probe;ExportBusPart export;
        int ticks;boolean ready,started,finished,pendingSuccess;
        Run(MinecraftServer server){this.server=server;level=server.overworld();}
        void force(){for(BlockPos pos:positions){level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);level.getChunkAt(pos);}}
        void start()throws Exception{
            require(stage!=null&&(stage.equals("prepare")||stage.equals("resume")),"Select AE prepare/resume stage");
            require(runId!=null&&directory.equals(Path.of("").toAbsolutePath().normalize()),"AE stage must run in its explicit owned directory");
            require(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(directory.resolve("ocfixture-appeng-world")),"AE stage refuses another world");
            force();
            if(stage.equals("prepare"))prepare();
            else{
                require(Files.isRegularFile(directory.resolve("prepare-results.txt"))&&Files.readString(directory.resolve("prepare-results.txt")).startsWith("status=PASS"),"AE prepare did not finish normal server shutdown");
                try(InputStream input=Files.newInputStream(directory.resolve("checkpoint.properties"))){manifest.load(input);}
                require(runId.equals(manifest.getProperty("run_id"))&&"prepared".equals(manifest.getProperty("phase")),"AE checkpoint selection differs or was already consumed");
                require(!PROCESS.equals(manifest.getProperty("process")),"AE restore must execute in a different Java process");
                require(directory.resolve("ocfixture-appeng-world").toString().equals(manifest.getProperty("world")),"AE checkpoint belongs to a different world");
                machine=(Case)level.getBlockEntity(casePos);controller=(ControllerBlockEntity)level.getBlockEntity(controllerPos);
                cell=(MEChestBlockEntity)level.getBlockEntity(cellPos);furnace=(FurnaceBlockEntity)level.getBlockEntity(furnacePos);
                export=(ExportBusPart)((IPartHost)level.getBlockEntity(exportCablePos)).getPart(Direction.EAST);
            }
            evidence.add("run_id="+runId);evidence.add("process="+PROCESS);evidence.add("world="+server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
        }
        void place(BlockPos pos,BlockState state){require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"AE fixture refuses occupied cell "+pos);require(level.setBlock(pos,state,3),"Could not place actual AE fixture block "+pos);}
        void ocBlock(BlockPos pos,String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null&&descriptor.block()!=null,"Missing real OC block "+name);place(pos,descriptor.block().defaultBlockState());}
        IPartHost cable(BlockPos pos){
            var id=ResourceLocation.fromNamespaceAndPath("ae2","cable_bus");var block=BuiltInRegistries.BLOCK.get(id);require(BuiltInRegistries.BLOCK.getKey(block).equals(id),"Official AE cable block is unavailable");
            place(pos,block.defaultBlockState());IPartHost host=(IPartHost)level.getBlockEntity(pos);
            require(host.addPart(AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT),null,null)!=null,"Actual AE center cable failed");return host;
        }
        void prepare(){
            require(!Files.exists(directory.resolve("checkpoint.properties")),"AE prepare refuses an existing checkpoint");
            require(BIOS.getBytes(StandardCharsets.UTF_8).length<4096,"AE persistence BIOS exceeds the real EEPROM capacity");
            ocBlock(casePos,"case3");ocBlock(adapterPos,"adapter");place(controllerPos,AEBlocks.CONTROLLER.block().defaultBlockState());
            place(cellPos,AEBlocks.ME_CHEST.block().defaultBlockState());place(controllerPos.above(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            place(cpuPos,AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());place(providerPos,AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
            place(furnacePos,Blocks.FURNACE.defaultBlockState());place(outputPos,Blocks.CHEST.defaultBlockState());ocBlock(exportAdapterPos,"adapter");
            // Vanilla furnaces accept smelting input only from above, and output extraction below.
            cable(lowerCablePos);cable(middleCablePos);cable(bottomCablePos);
            var importer=cable(importCablePos).addPart(AEParts.IMPORT_BUS.get(),Direction.UP,null);require(importer!=null,"Actual furnace import bus failed");
            importer.getConfig().setStack(0,new GenericStack(AEItemKey.of(new ItemStack(Items.IRON_INGOT)),1));
            export=cable(exportCablePos).addPart(AEParts.EXPORT_BUS.get(),Direction.EAST,null);require(export!=null,"Actual fuzzy export bus failed");
            export.getUpgrades().setItemDirect(0,AEItems.REDSTONE_CARD.stack());
            export.getConfigManager().putSetting(appeng.api.config.Settings.REDSTONE_CONTROLLED,RedstoneMode.HIGH_SIGNAL);
            export.getConfig().setStack(0,new GenericStack(AEItemKey.of(new ItemStack(Items.IRON_PICKAXE)),1));
            controller=(ControllerBlockEntity)level.getBlockEntity(controllerPos);cell=(MEChestBlockEntity)level.getBlockEntity(cellPos);
            furnace=(FurnaceBlockEntity)level.getBlockEntity(furnacePos);cell.setCell(AEItems.ITEM_CELL_1K.stack());furnace.setItem(1,new ItemStack(Items.COAL,8));
            ((CraftingBlockEntity)level.getBlockEntity(cpuPos)).setName("OC cold furnace CPU");
            ItemStack pattern=PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(new ItemStack(Items.IRON_ORE)),1)),List.of(new GenericStack(AEItemKey.of(new ItemStack(Items.IRON_INGOT)),1)));
            var provider=(PatternProviderBlockEntity)level.getBlockEntity(providerPos);provider.getLogic().getPatternInv().setItemDirect(0,pattern);provider.getLogic().updatePatterns();
            machine=(Case)level.getBlockEntity(casePos);ItemStack cpu=item("cpu3");var driver=Driver.driverFor(cpu,machine.getClass());
            require(driver instanceof MutableProcessor,"Actual CPU has no public architecture selection");
            var architecture=li.cil.oc.api.Machine.architectures().stream().filter(type->"Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type))).findFirst().orElseThrow(()->new AssertionError("Native Lua 5.3 unavailable"));
            ((MutableProcessor)driver).setArchitecture(cpu,architecture);install(cpu);install(item("ram6"));install(item("hdd3"));
            install(li.cil.oc.api.Items.registerEEPROM("AE2 actual furnace cold contract",BIOS.getBytes(StandardCharsets.UTF_8),new byte[0],false));
            ItemStack observation=new ItemStack(Items.PAPER);CustomData.update(DataComponents.CUSTOM_DATA,observation,tag->tag.putBoolean(MARKER,true));
            var adapter=(Container)level.getBlockEntity(adapterPos);require(adapter.canPlaceItem(0,observation),"Actual Adapter rejects the public observation driver");adapter.setItem(0,observation);
        }
        void install(ItemStack stack){for(int slot=0;slot<machine.getContainerSize();slot++)if(machine.getItem(slot).isEmpty()&&machine.canPlaceItem(slot,stack)){machine.setItem(slot,stack);return;}throw new AssertionError("No actual Case slot accepts "+stack);}
        IGrid grid(){return Objects.requireNonNull(controller.getActionableNode(),"Actual AE Controller has no node").getGrid();}
        long stored(net.minecraft.world.item.Item item){return grid().getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(new ItemStack(item)));}
        boolean connected(){
            if(machine==null||machine.machine()==null||machine.machine().node().network()==null||controller.getActionableNode()==null||!controller.getActionableNode().isActive())return false;
            Node node=machine.machine().node();
            for(BlockPos pos:List.of(adapterPos,exportAdapterPos)){BlockEntity tile=level.getBlockEntity(pos);require(tile instanceof Environment,"Actual Adapter exposes no SDK Environment");Network.joinOrCreateNetwork(tile);node.connect(((Environment)tile).node());}
            boolean control=false,bus=false;
            for(Node other:node.reachableNodes()){
                if(other.host() instanceof Probe found)probe=found;
                if(other instanceof Component component&&component.canBeSeenFrom(node)){if(component.name().equals("me_controller"))control=true;if(component.name().equals("me_exportbus"))bus=true;}
            }
            return probe!=null&&control&&bus&&!grid().getCraftingService().getCpus().isEmpty()&&grid().getCraftingService().isCraftable(AEItemKey.of(new ItemStack(Items.IRON_INGOT)));
        }
        void tick()throws Exception{
            require(++ticks<=1600,"Actual AE persistence stage timed out: phase="+(probe==null?"not loaded":probe.phase)+", error="+(machine==null?"no machine":machine.machine().lastError()));
            if(!ready){
                if(!connected()){require(ticks<200,"Actual AE world/CPU/pattern/Adapter/observation node did not initialize");return;}
                if(stage.equals("prepare")){
                    var storage=grid().getStorageService().getInventory();var source=IActionSource.ofMachine(controller);
                    require(storage.insert(AEItemKey.of(new ItemStack(Items.IRON_ORE)),3,Actionable.MODULATE,source)==3,"Actual ME cell rejected ore");
                    ItemStack damaged=new ItemStack(Items.IRON_PICKAXE);damaged.setDamageValue(100);
                    require(storage.insert(AEItemKey.of(damaged),1,Actionable.MODULATE,source)==1,"Actual ME cell rejected damaged fuzzy item");
                    ((Connector)machine.machine().node()).changeBuffer(Double.POSITIVE_INFINITY);
                    require(machine.machine().start(),"Actual native Case failed to start");started=true;
                }else{
                    require(probe.boots==1&&probe.phase==3,"Actual saved observation driver lost the native boot/checkpoint");
                    require(manifest.getProperty("machine").equals(machine.machine().node().address())&&manifest.getProperty("probe").equals(probe.node().address()),"Actual world changed Case/observation node identities");
                    require(machine.machine().isRunning(),"World-loaded native VM did not retain running state: "+machine.machine().lastError());
                    if(machine.machine().isPaused())require(machine.machine().start(),"Could not release actual paused native VM");
                }
                ready=true;return;
            }
            ((Connector)machine.machine().node()).changeBuffer(Double.POSITIVE_INFINITY);
            require(machine.machine().lastError()==null,"Actual native AE BIOS failed: "+machine.machine().lastError());
            if(stage.equals("prepare")&&probe.phase==3&&machine.machine().isPaused()){
                require(furnace.getItem(0).is(Items.IRON_ORE),"Cold save did not retain actual ore in the real furnace");
                require(grid().getCraftingService().getCpus().stream().anyMatch(cpu->cpu.isBusy()),"Cold save had no actual active CPU job");
                require(server.saveEverything(false,true,true),"Minecraft failed to save actual AE/native/furnace world");
                manifest.setProperty("run_id",runId);manifest.setProperty("process",PROCESS);manifest.setProperty("phase","prepared");manifest.setProperty("world",directory.resolve("ocfixture-appeng-world").toString());
                manifest.setProperty("machine",machine.machine().node().address());manifest.setProperty("probe",probe.node().address());manifest.setProperty("link",probe.linkId);manifest.setProperty("iron_before",Long.toString(probe.ironBefore));
                try(OutputStream output=Files.newOutputStream(directory.resolve("checkpoint.properties"))){manifest.store(output,"Actual pending AE crafting link held by native Lua userdata");}
                evidence.add("PASS fuzzy: exact filter rejected damaged iron pickaxe; real fuzzy card IGNORE_ALL exported exactly that item with source/destination conservation");
                evidence.add("PASS cancel: actual busy CPU request acquired a link, cancel returned true and actual link reported canceled");
                evidence.add("PASS checkpoint: native Lua retains actual Craftable/status while CPU and vanilla furnace hold a pending ore-to-ingot job");
                evidence.add("link="+probe.linkId);evidence.add("boots="+probe.boots);success();
            }else if(stage.equals("resume")&&probe.phase==4){
                require(probe.boots==1,"Native AE BIOS rebooted instead of resuming its userdata locals");
                require(stored(Items.IRON_INGOT)==Long.parseLong(manifest.getProperty("iron_before"))+1,"Cold job did not produce exactly one additional real smelted iron ingot");
                require(furnace.getItem(0).isEmpty()&&furnace.getItem(2).isEmpty(),"Restored actual furnace/import bus did not drain the completed job");
                require(grid().getCraftingService().getCpus().stream().noneMatch(cpu->cpu.isBusy()),"Restored actual crafting CPU remained busy after completion");
                evidence.add("PASS cold: different JVM restored native Craftable/status locals, original request UUID and requester/CPU pairing; actual saved furnace smelted and import bus completed that pending job");
                evidence.add("PASS cold: restored Craftable requesting/getItemStack and completed status/cancel callbacks retain original public forms");
                evidence.add("boots="+probe.boots);evidence.add("link="+probe.linkId);manifest.setProperty("phase","complete");
                try(OutputStream output=Files.newOutputStream(directory.resolve("checkpoint.properties"))){manifest.store(output,"Completed actual AE cold job");}
                require(server.saveEverything(false,true,true),"Could not save completed actual AE world");success();
            }
        }
        void nativeCaller(Context context){require(context==machine.machine(),"AE observation did not originate in its actual native Case VM");}
        void success(){finished=true;pendingSuccess=true;report("PENDING_STOP");server.halt(false);}
        void fail(Throwable error){if(finished&&!pendingSuccess)return;finished=true;pendingSuccess=false;evidence.add("FAIL "+error);LOGGER.error("AE2_PERSISTENCE stage={} ticks={}",stage,ticks,error);report("FAIL");server.halt(false);}
        void report(String status){
            List<String> lines=new ArrayList<>();lines.add("status="+status);lines.add("stage="+stage);lines.add("ticks="+ticks);lines.addAll(evidence);
            try{Files.write(directory.resolve(stage+"-results.txt"),lines,StandardCharsets.UTF_8);}catch(Exception error){throw new IllegalStateException("Cannot write actual AE stage report",error);}
        }
    }
    private static final class ProbeDriver extends li.cil.oc.api.prefab.DriverItem implements HostAware {
        @Override public boolean worksWith(ItemStack stack){return stack.is(Items.PAPER)&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBoolean(MARKER);}
        @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){return worksWith(stack)&&Adapter.class.isAssignableFrom(host);}
        @Override public String slot(ItemStack stack){return li.cil.oc.api.driver.item.Slot.Upgrade;}
        @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){return host instanceof Adapter?new Probe():null;}
    }
    public static final class Probe extends AbstractManagedEnvironment {
        int boots,phase;long ironBefore;String linkId="";
        Probe(){setNode(Network.newNode(this,Visibility.Network).withComponent("ae2_cold_probe",Visibility.Network).create());}
        @Callback public Object[] boot(Context context,Arguments args){active.nativeCaller(context);require(active.stage.equals("prepare"),"Saved native AE BIOS unexpectedly rebooted in resume JVM");return new Object[]{++boots};}
        @Callback public Object[] fuzzyCard(Context context,Arguments args){
            active.nativeCaller(context);require(phase==0,"Fuzzy setup ran more than once");
            active.export.getUpgrades().setItemDirect(1,AEItems.FUZZY_CARD.stack());
            active.export.getConfigManager().putSetting(appeng.api.config.Settings.FUZZY_MODE,FuzzyMode.IGNORE_ALL);return new Object[]{true};
        }
        @Callback public Object[] fuzzyDone(Context context,Arguments args){
            active.nativeCaller(context);require(phase==0,"Fuzzy result ran out of order");
            Container output=(Container)active.level.getBlockEntity(active.outputPos);ItemStack exported=output.getItem(0);
            require(exported.is(Items.IRON_PICKAXE)&&exported.getCount()==1&&exported.getDamageValue()==100,"Fuzzy export did not move the actual damaged stack to requested slot");
            ItemStack key=new ItemStack(Items.IRON_PICKAXE);key.setDamageValue(100);
            require(active.grid().getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(key))==0,"Fuzzy export did not conserve real ME/tool contents");phase=1;return new Object[]{true};
        }
        @Callback public Object[] cancelReady(Context context,Arguments args){
            active.nativeCaller(context);require(phase==1,"Cancellation ran before actual fuzzy proof");
            return new Object[]{active.grid().getCraftingService().getCpus().stream().anyMatch(cpu->cpu.isBusy())};
        }
        @Callback public Object[] canceled(Context context,Arguments args){active.nativeCaller(context);require(phase==1,"Repeated cancel proof");phase=2;return new Object[]{true};}
        @Callback public Object[] coldReady(Context context,Arguments args){
            active.nativeCaller(context);require(phase==2,"Pending job started before canceled job proof");
            boolean ready=active.grid().getCraftingService().getCpus().stream().noneMatch(cpu->cpu.isBusy())&&active.furnace.getItem(0).isEmpty()&&active.furnace.getItem(2).isEmpty();
            if(ready)ironBefore=active.stored(Items.IRON_INGOT);return new Object[]{ready};
        }
        @Callback public Object[] inFurnace(Context context,Arguments args){active.nativeCaller(context);return new Object[]{active.furnace.getItem(0).is(Items.IRON_ORE)};}
        @Callback public Object[] checkpoint(Context context,Arguments args){
            active.nativeCaller(context);require(phase==2&&boots==1&&active.stage.equals("prepare"),"Native checkpoint ran in wrong stage");
            require(args.checkAny(0) instanceof Value&&args.checkAny(0) instanceof ICraftingRequester&&args.checkAny(1) instanceof Value,"Actual native Lua did not retain real AE userdata");
            Value craft=(Value)args.checkAny(0),status=(Value)args.checkAny(1);CompoundTag saved=new CompoundTag();status.save(saved,active.level.registryAccess());
            require(saved.hasUUID("link")&&!saved.getBoolean("computing")&&!saved.getBoolean("failed"),"Actual status has no submitted pending link");linkId=saved.getUUID("link").toString();
            require(((ICraftingRequester)craft).getRequestedJobs().stream().anyMatch(link->link.getCraftingID().toString().equals(linkId)&&!link.isCanceled()&&!link.isDone()),"Actual Craftable does not own the pending status link");
            require(context.pause(600),"Could not pause actual native Lua at serializable pending-job yield");phase=3;return new Object[]{true};
        }
        @Callback public Object[] restored(Context context,Arguments args){
            active.nativeCaller(context);require(active.stage.equals("resume")&&phase==3&&boots==1,"Restored userdata proof did not originate in the second JVM's resumed native program");
            require(args.checkAny(0) instanceof Value&&args.checkAny(0) instanceof ICraftingRequester&&args.checkAny(1) instanceof Value,"Resumed native userdata types changed");
            CompoundTag status=new CompoundTag();((Value)args.checkAny(1)).save(status,active.level.registryAccess());
            require(status.hasUUID("link")&&status.getUUID("link").toString().equals(active.manifest.getProperty("link")),"Cold status lost the actual submitted request UUID");
            require(!status.getBoolean("computing")&&!status.getBoolean("failed"),"Cold status became computing/failed instead of completing");phase=4;return new Object[]{true};
        }
        @Override public void load(CompoundTag tag){super.load(tag);boots=tag.getInt("boots");phase=tag.getInt("phase");ironBefore=tag.getLong("ironBefore");linkId=tag.getString("linkId");}
        @Override public void save(CompoundTag tag){super.save(tag);tag.putInt("boots",boots);tag.putInt("phase",phase);tag.putLong("ironBefore",ironBefore);tag.putString("linkId",linkId);}
    }
    private static final String BIOS="""
        assert(computer.getArchitecture()=="Lua 5.3")
        local function proxy(n)return component.proxy(assert(component.list(n,true)()))end
        local p,me,ex=proxy("ae2_cold_probe"),proxy("me_controller"),proxy("me_exportbus")
        assert(p.boot()==1)
        local n,why=ex.exportIntoSlot(5,1)
        assert(n==nil and why=="no items moved","exact filter exported damaged item")
        assert(p.fuzzyCard())
        assert(ex.exportIntoSlot(5,1)==1,"fuzzy card did not export real damaged item")
        assert(p.fuzzyDone())
        local craft=assert(me.getCraftables({name="minecraft:iron_ingot"})[1])
        assert(craft.getItemStack().name=="minecraft:iron_ingot")
        local cpu=assert(me.getCpus()[1].name)
        local canceled=assert(craft.request(1,true,cpu))
        local linked=false
        for i=1,200 do
          local c,r=canceled.isCanceled()
          if r==nil and p.cancelReady() then assert(c==false);linked=true;break end
          assert(r==nil or r=="computing",tostring(r));computer.pullSignal(.05)
        end
        assert(linked and not canceled.isDone(),"real active request did not acquire pending link")
        assert(canceled.cancel()==true and canceled.isCanceled()==true)
        assert(p.canceled())
        local idle=false
        for i=1,500 do if p.coldReady() then idle=true;break end computer.pullSignal(.05) end
        assert(idle,"canceled furnace job did not settle naturally")
        local status=assert(craft.request(1,false,cpu))
        local pending=false
        for i=1,200 do
          local c,r=status.isCanceled()
          if r==nil and p.inFurnace() then assert(not c and not status.isDone());pending=true;break end
          assert(r==nil or r=="computing",tostring(r));computer.pullSignal(.05)
        end
        assert(pending,"real pending job never reached vanilla furnace")
        assert(p.checkpoint(craft,status))
        local done=false
        for i=1,800 do
          local count=craft.requesting()
          local complete,reason=status.isDone()
          if type(count)=="number" and reason==nil then
            assert(not status.isCanceled(),"cold job canceled")
            if complete then done=true;break end
          else assert(reason==nil or reason=="no link" or reason=="computing",tostring(reason)) end
          computer.pullSignal(.05)
        end
        assert(done,"actual cold furnace request never completed")
        assert(craft.getItemStack().name=="minecraft:iron_ingot")
        local cancelled,reason=status.cancel()
        assert(cancelled==false and reason=="job already completed")
        assert(p.restored(craft,status))
        computer.shutdown()
        """;
}
