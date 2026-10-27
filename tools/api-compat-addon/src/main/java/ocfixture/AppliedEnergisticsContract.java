package ocfixture;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import appeng.api.config.Actionable;
import appeng.api.config.RedstoneMode;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.parts.automation.ExportBusPart;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Database;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Value;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real placed AE hardware and native OC callbacks; uses OC's published SDK only. */
public final class AppliedEnergisticsContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-ae2-contract");
    private static final List<String> RESULTS = new CopyOnWriteArrayList<>();
    private static Run active;
    private AppliedEnergisticsContract() {}
    public static boolean enabled() { return Boolean.getBoolean("ocfixture.ae2Only"); }
    public static boolean isRunning() { return active != null; }
    public static List<String> results() { return List.copyOf(RESULTS); }
    public static String run(ServerLevel level) throws Exception {
        require(active == null, "An AE2 contract is already running");
        require(ModList.get().isLoaded("ae2") && ModList.get().isLoaded("guideme"), "The official AE2 and GuideME runtimes must be installed");
        require(API.driver != null && API.network != null && API.config != null, "The OC SDK facades are unavailable");
        require(!API.config.getStringList("integration.modBlacklist").stream().anyMatch(id -> id.equalsIgnoreCase("ae2") || id.equalsIgnoreCase("appliedenergistics2")), "This behavior scenario requires the AE integration to be enabled");
        RESULTS.clear(); Run run = new Run(level); active = run;
        try { run.start(); } catch (Exception | Error error) { run.fail(error); throw error; }
        return "Started real AE2 Controller/Interface/Bus/ME cell/CPU/PatternProvider/Assembler and native Lua 5.3 contract";
    }
    public static void tick() { Run run = active; if (run != null) try { run.tick(); } catch (Throwable error) { run.fail(error); } }
    public static void close() { if (active != null) active.fail(new AssertionError("Server stopped before the real AE2 contract completed")); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static ItemStack ocItem(String name) { return Objects.requireNonNull(li.cil.oc.api.Items.get(name), "Missing OC SDK item " + name).createItemStack(1); }

    private static final class Run {
        final ServerLevel level;
        final BlockPos origin = new BlockPos(Integer.getInteger("ocfixture.ae2X", 6144), Integer.getInteger("ocfixture.ae2Y", 96), Integer.getInteger("ocfixture.ae2Z", 6144));
        final BlockPos casePos = origin, adapterPos = origin.east(), controllerPos = origin.east(2), meChestPos = origin.east(3);
        final BlockPos interfacePos = controllerPos.south(), interfaceAdapterPos = interfacePos.south();
        final BlockPos busPos = meChestPos.south(), busAdapterPos = busPos.south(), outputPos = busPos.east(), inputPos = busPos.above();
        final BlockPos cpuPos = controllerPos.north(), providerPos = meChestPos.north(), assemblerPos = providerPos.east();
        final Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
        final Set<Long> forcedChunks = new LinkedHashSet<>();
        final AtomicBoolean completed = new AtomicBoolean();
        Case hardware; Probe probe; ControllerBlockEntity controller; MEChestBlockEntity chest; ExportBusPart export;
        int ticks, phase, roundTrips; boolean seeded, booted;

        Run(ServerLevel level) { this.level = level; }
        private void place(BlockPos pos, BlockState state) {
            long chunk = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true)) forcedChunks.add(chunk);
            level.getChunkAt(pos);
            require(level.isEmptyBlock(pos), "AE2 fixture will not overwrite a nonempty cell " + pos);
            placed.put(pos.immutable(), level.getBlockState(pos));
            require(level.setBlock(pos, state, 3), "Could not place actual fixture block at " + pos);
        }
        private void ocBlock(BlockPos pos, String name) {
            Block block = Objects.requireNonNull(li.cil.oc.api.Items.get(name), "Missing real OC block " + name).block();
            require(block != null, "SDK descriptor has no block: " + name); place(pos, block.defaultBlockState());
        }
        void start() {
            ocBlock(casePos, "case3"); ocBlock(adapterPos, "adapter");
            place(controllerPos, AEBlocks.CONTROLLER.block().defaultBlockState());
            place(meChestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
            place(controllerPos.above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            place(interfacePos, AEBlocks.INTERFACE.block().defaultBlockState()); ocBlock(interfaceAdapterPos, "adapter");
            // The center cable and actual parts use AE's public multipart placement API.
            ResourceLocation cableId = ResourceLocation.fromNamespaceAndPath("ae2", "cable_bus");
            Block cableBlock = BuiltInRegistries.BLOCK.get(cableId);
            require(BuiltInRegistries.BLOCK.getKey(cableBlock).equals(cableId), "Official AE cable bus block is unavailable");
            place(busPos, cableBlock.defaultBlockState()); ocBlock(busAdapterPos, "adapter");
            place(outputPos, Blocks.CHEST.defaultBlockState()); place(inputPos, Blocks.CHEST.defaultBlockState());
            place(cpuPos, AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
            place(providerPos, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
            place(assemblerPos, AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
            controller = (ControllerBlockEntity) level.getBlockEntity(controllerPos);
            chest = (MEChestBlockEntity) level.getBlockEntity(meChestPos);
            chest.setCell(AEItems.ITEM_CELL_1K.stack());
            ((CraftingBlockEntity)level.getBlockEntity(cpuPos)).setName("OC fixture CPU");
            IPartHost host = (IPartHost)level.getBlockEntity(busPos);
            require(host.addPart(AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT), null, null) != null, "Actual glass cable could not be added");
            export = host.addPart(AEParts.EXPORT_BUS.get(), Direction.EAST, null);
            require(export != null, "Actual export bus could not be added");
            require(host.addPart(AEParts.IMPORT_BUS.get(), Direction.UP, null) != null, "Actual import bus could not be added");
            require(host.addPart(AEParts.INTERFACE.get(), Direction.SOUTH, null) != null, "Actual part interface could not be added");
            export.getUpgrades().setItemDirect(0, AEItems.REDSTONE_CARD.stack());
            export.getUpgrades().setItemDirect(1, AEItems.SPEED_CARD.stack());
            // Stop automatic export so the exact subsequent OC operation is measurable.
            export.getConfigManager().putSetting(appeng.api.config.Settings.REDSTONE_CONTROLLED, RedstoneMode.HIGH_SIGNAL);
            installPattern();
            hardware = (Case)level.getBlockEntity(casePos);
            require(hardware != null && hardware.machine() != null, "Actual placed Case has no SDK machine");
            ItemStack cpu = ocItem("cpu3"); var processor = Driver.driverFor(cpu, hardware.getClass());
            require(processor instanceof MutableProcessor, "Real OC CPU does not expose MutableProcessor");
            Class<? extends Architecture> native53 = li.cil.oc.api.Machine.architectures().stream()
                    .filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type))).findFirst()
                    .orElseThrow(() -> new AssertionError("Real native Lua 5.3 is required"));
            ((MutableProcessor)processor).setArchitecture(cpu, native53);
            install(cpu); install(ocItem("ram6")); install(ocItem("hdd3"));
            install(li.cil.oc.api.Items.registerEEPROM("AE2 native hardware contract", BIOS.getBytes(StandardCharsets.UTF_8), new byte[0], false));
            Container adapter = (Container)level.getBlockEntity(adapterPos);
            ItemStack database = ocItem("databaseupgrade3"); require(adapter.canPlaceItem(0, database), "Real Adapter rejects the actual database upgrade"); adapter.setItem(0, database);
            probe = new Probe(this);
            LOGGER.info("AE2_CONTRACT placed actual hardware at {} with cell, three parts, powered CPU and encoded oak-plank pattern", origin);
        }
        private void installPattern() {
            ItemStack[] input = new ItemStack[9]; Arrays.fill(input, ItemStack.EMPTY); input[0] = new ItemStack(Items.OAK_LOG);
            CraftingInput grid = CraftingInput.of(3, 3, Arrays.asList(input));
            RecipeHolder<CraftingRecipe> recipe = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .filter(holder -> holder.value().getResultItem(level.registryAccess()).is(Items.OAK_PLANKS) && holder.value().matches(grid, level))
                    .findFirst().orElseThrow(() -> new AssertionError("Real vanilla oak-log to planks crafting recipe is unavailable"));
            ItemStack output = recipe.value().assemble(grid, level.registryAccess());
            require(output.is(Items.OAK_PLANKS) && output.getCount() == 4, "Vanilla plank recipe has unexpected actual result");
            ItemStack encoded = PatternDetailsHelper.encodeCraftingPattern(recipe, input, output, false, false);
            PatternProviderBlockEntity provider = (PatternProviderBlockEntity)level.getBlockEntity(providerPos);
            provider.getLogic().getPatternInv().setItemDirect(0, encoded); provider.getLogic().updatePatterns();
        }
        private void install(ItemStack stack) {
            for (int slot = 0; slot < hardware.getContainerSize(); slot++) if (hardware.getItem(slot).isEmpty() && hardware.canPlaceItem(slot, stack)) { hardware.setItem(slot, stack); return; }
            throw new AssertionError("No real Case slot accepts " + stack.getHoverName().getString());
        }
        private IGrid grid() { return Objects.requireNonNull(controller.getActionableNode(), "Real Controller has no live node").getGrid(); }
        private Database database() {
            Node node = hardware.machine().node();
            if (node.network() != null) for (Node other : node.reachableNodes()) if (other.host() instanceof Database database) return database;
            return null;
        }
        private void connectAdapters() {
            Node machine = hardware.machine().node();
            for (BlockPos pos : List.of(adapterPos, interfaceAdapterPos, busAdapterPos)) {
                BlockEntity tile = level.getBlockEntity(pos);
                require(tile instanceof Environment, "Real Adapter does not expose the SDK Environment");
                Network.joinOrCreateNetwork(tile); machine.connect(((Environment)tile).node());
            }
            machine.connect(probe.node());
        }
        private boolean ready() {
            var machine = hardware.machine();
            if (machine.node().network() == null || machine.node().address() == null || controller.getActionableNode() == null || !controller.getActionableNode().isActive()) return false;
            if (grid().getCraftingService().getCpus().isEmpty() || !grid().getCraftingService().isCraftable(AEItemKey.of(new ItemStack(Items.OAK_PLANKS)))) return false;
            connectAdapters();
            Set<String> names = new LinkedHashSet<>();
            boolean multipart = false;
            for (Node node : machine.node().reachableNodes()) {
                if (!(node instanceof Component component) || !component.canBeSeenFrom(machine.node())) continue;
                names.add(component.name());
                // One cable host is one compound component. The original export
                // priority names it; import and interface callbacks are merged into it.
                if (component.name().equals("me_exportbus")) multipart = component.methods().containsAll(Set.of(
                        "getExportConfiguration", "setExportConfiguration", "exportIntoSlot",
                        "getImportConfiguration", "setImportConfiguration",
                        "getInterfaceConfiguration", "setInterfaceConfiguration"));
            }
            return multipart && names.containsAll(Set.of("me_controller", "me_interface", "me_exportbus", "eeprom", "database")) && database() != null;
        }
        void tick() {
            require(++ticks <= 1400, "Real AE2 native hardware contract exceeded 1400 ticks; phase=" + phase);
            if (!booted) {
                require(ticks <= 300, "Real AE network/CPU/pattern/Adapter/Case did not become ready within 300 ticks");
                if (ticks < 10 || !ready()) return;
                if (!seeded) {
                    var inventory = grid().getStorageService().getInventory(); var source = IActionSource.ofMachine(controller);
                    require(inventory.insert(AEItemKey.of(new ItemStack(Items.IRON_INGOT)), 32, Actionable.MODULATE, source) == 32, "Real ME cell rejected iron seed");
                    require(inventory.insert(AEItemKey.of(new ItemStack(Items.OAK_LOG)), 8, Actionable.MODULATE, source) == 8, "Real ME cell rejected log seed");
                    require(inventory.insert(AEItemKey.of(new ItemStack(Items.REDSTONE)), 17, Actionable.MODULATE, source) == 17, "Real ME cell rejected redstone seed");
                    Database database = database();
                    database.setStackInSlot(0, new ItemStack(Items.IRON_INGOT)); database.setStackInSlot(1, new ItemStack(Items.DIAMOND));
                    database.setStackInSlot(2, new ItemStack(Items.GOLD_INGOT)); database.setStackInSlot(3, new ItemStack(Items.COPPER_INGOT));
                    seeded = true; return;
                }
                ((Connector)probe.node()).changeBuffer(Double.MAX_VALUE);
                require(hardware.machine().start(), "Real Case could not boot native Lua 5.3: " + hardware.machine().lastError()); booted = true;
                return;
            }
            ((Connector)probe.node()).changeBuffer(Double.MAX_VALUE);
            if (!hardware.machine().isRunning()) {
                require(completed.get(), "Real native Case stopped before proof completion: " + hardware.machine().lastError());
                require(phase == 4 && roundTrips == 2, "Native script skipped actual AE behavior phases or Value persistence");
                pass("native Lua 5.3 reached every real Adapter driver phase, two SDK Value round-trips and actual crafting completion");
                cleanup(); active = null; LOGGER.info("AE2_CONTRACT_COMPLETE status=PASS");
            }
        }
        private long stored(net.minecraft.world.item.Item item) { return grid().getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(new ItemStack(item))); }
        private int chestCount(BlockPos pos, net.minecraft.world.item.Item item) {
            Container inventory = (Container)level.getBlockEntity(pos); int count = 0;
            for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(item)) count += inventory.getItem(i).getCount();
            return count;
        }
        void nativeCaller(Context context) { require(context == hardware.machine(), "AE proof callback was invoked outside the actual native Case VM"); }
        void pass(String message) { RESULTS.add("PASS AE2: " + message); LOGGER.info("AE2_CONTRACT PASS {}", message); }
        void report(String stage, int moved) {
            switch (stage) {
                case "network" -> {
                    require(phase == 0 && stored(Items.IRON_INGOT) == 32 && stored(Items.OAK_LOG) == 8 && stored(Items.REDSTONE) == 17,
                            "Native network query did not correspond to the actual ME cell contents");
                    pass("native Controller queries, filtered stock, live CPU/energy and real cell metrics match actual ME storage"); phase = 1;
                }
                case "export" -> {
                    require(phase == 1 && moved == 8 && chestCount(outputPos, Items.IRON_INGOT) == 8 && stored(Items.IRON_INGOT) == 24,
                            "Real explicit export did not conserve 32 iron across 24 ME + 8 destination items");
                    pass("native exportIntoSlot moved exactly eight real iron with installed speed card and preserved source/destination totals"); phase = 2;
                }
                case "import" -> {
                    require(phase == 2 && chestCount(inputPos, Items.COPPER_INGOT) == 0 && stored(Items.COPPER_INGOT) == 11,
                            "Native import configuration did not move the actual eleven copper ingots into ME");
                    pass("native import configuration drives the actual bus from real sided chest to ME, with eleven-item conservation"); phase = 3;
                }
                case "crafting" -> {
                    require(phase == 3 && stored(Items.OAK_LOG) == 7 && stored(Items.OAK_PLANKS) == 4,
                            "Completed native request did not turn one real log into four real planks through AE hardware");
                    require(roundTrips == 2, "Craftable/status did not both traverse SDK persistence");
                    pass("real CPU + encoded pattern + Molecular Assembler completed the native Craftable request, persisted status and completed-job cancel rejection");
                    phase = 4; completed.set(true);
                }
                default -> throw new AssertionError("Unknown native AE proof stage " + stage);
            }
        }
        void fail(Throwable error) {
            RESULTS.add("FAIL AE2: " + error); LOGGER.error("AE2_CONTRACT FAIL", error);
            try { cleanup(); } catch (Throwable cleanup) { LOGGER.error("AE2 fixture cleanup failed", cleanup); }
            active = null;
        }
        void cleanup() {
            if (hardware != null) { hardware.machine().stop(); hardware.clearContent(); }
            if (probe != null) probe.node().remove();
            var positions = new ArrayList<>(placed.entrySet()); Collections.reverse(positions);
            for (var entry : positions) { if (level.getBlockEntity(entry.getKey()) instanceof Container inventory) inventory.clearContent(); level.setBlock(entry.getKey(), entry.getValue(), 3); }
            placed.clear();
            for (long chunk : forcedChunks) level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(chunk), net.minecraft.world.level.ChunkPos.getZ(chunk), false);
            forcedChunks.clear();
        }
    }

    public static final class Probe extends AbstractManagedEnvironment {
        private final Run run;
        Probe(Run run) { this.run = run; setNode(Network.newNode(this, Visibility.Network).withComponent("ae2_probe", Visibility.Network).withConnector(10_000_000).create()); }
        @Callback(direct = true)
        public Object[] metadata(Context context, Arguments args) {
            run.nativeCaller(context);
            Node database = null; for (Node node : run.hardware.machine().node().reachableNodes()) if (node.host() instanceof Database) { database = node; break; }
            require(database != null, "Actual database component is not connected to the Case");
            return new Object[]{Map.of("database", database.address(), "east", Direction.EAST.get3DDataValue(), "up", Direction.UP.get3DDataValue(), "south", Direction.SOUTH.get3DDataValue())};
        }
        @Callback
        public Object[] cell(Context context, Arguments args) { run.nativeCaller(context); return new Object[]{run.chest.getCell()}; }
        @Callback
        public Object[] supplyImport(Context context, Arguments args) {
            run.nativeCaller(context); require(run.phase == 2, "Copper supply occurred before explicit export was verified");
            Container inventory = (Container)run.level.getBlockEntity(run.inputPos); require(inventory.isEmpty(), "Real import input chest is unexpectedly occupied");
            inventory.setItem(0, new ItemStack(Items.COPPER_INGOT, 11)); return new Object[]{true};
        }
        @Callback
        public Object[] roundTrip(Context context, Arguments args) throws ReflectiveOperationException {
            run.nativeCaller(context); require(args.checkAny(0) instanceof Value, "Native script did not provide a real SDK Value");
            Value original = (Value)args.checkAny(0); CompoundTag saved = new CompoundTag(); original.save(saved, run.level.registryAccess());
            Object restored = original.getClass().getConstructor().newInstance(); require(restored instanceof Value, "Public Value constructor did not restore a Value");
            ((Value)restored).load(saved, run.level.registryAccess()); run.roundTrips++;
            return new Object[]{restored};
        }
        @Callback
        public Object[] report(Context context, Arguments args) { run.nativeCaller(context); run.report(args.checkString(0), args.optInteger(1, 0)); return new Object[]{true}; }
    }

    // Keep the BIOS within the actual 4096-byte EEPROM limit.
    private static final String BIOS = """
        assert(computer.getArchitecture()=="Lua 5.3","requires actual native Lua 5.3")
        local function proxy(name)return component.proxy(assert(component.list(name,true)(),"missing real " .. name))end
        local p,me,ex=proxy("ae2_probe"),proxy("me_controller"),proxy("me_exportbus")
        local im=ex
        local meta=p.metadata()
        assert(me.isNetworkPowered(),"actual AE network is offline")
        assert(me.getMaxStoredPower()>0 and me.getStoredPower()>0)
        assert(type(me.getAvgPowerInjection())=="number" and type(me.getAvgPowerUsage())=="number")
        assert(type(me.getIdlePowerUsage())=="number" and type(me.getEnergyDemand())=="number")
        assert(type(me.getFluidsInNetwork())=="table")
        local iron=me.getItemsInNetwork({name="minecraft:iron_ingot"})
        assert(#iron==1 and iron[1].size==32 and not iron[1].isCraftable,"actual filtered iron stock is wrong")
        assert(#me.getItemsInNetwork({name="minecraft:diamond"})==0,"item filter returned unmatched stock")
        local cpus=me.getCpus()
        assert(#cpus>0 and cpus[1].storage>=1024 and not cpus[1].busy,"actual CPU is unavailable")
        local cell=p.cell()
        assert(cell.totalBytes==1024 and cell.storedItemCount==57 and cell.storedItemTypes==3,
        "actual storage cell did not cross the cell converter chain")
        assert(me.store({name="minecraft:iron_ingot"},meta.database,5,1))
        assert(p.report("network"))
        assert(ex.setExportConfiguration(meta.east,1,meta.database,1))
        assert(ex.getExportConfiguration(meta.east,1).name=="minecraft:iron_ingot")
        local moved,why=ex.exportIntoSlot(meta.east,1)
        assert(moved==8,"actual explicit export moved wrong count: " .. tostring(moved).. ": " .. tostring(why))
        assert(p.report("export",moved))
        local bi,pi=proxy("me_interface"),ex
        assert(bi and pi,"both actual block and part interface adapters are required")
        assert(im.setImportConfiguration and pi.setInterfaceConfiguration,"actual multipart callbacks are missing")
        assert(bi.setInterfaceConfiguration(1,meta.database,2,3))
        local cfg=bi.getInterfaceConfiguration(1)
        assert(cfg.name=="minecraft:diamond" and cfg.size==3)
        assert(pi.setInterfaceConfiguration(meta.south,1,meta.database,3,2))
        cfg=pi.getInterfaceConfiguration(meta.south,1)
        assert(cfg.name=="minecraft:gold_ingot" and cfg.size==2)
        assert(im.setImportConfiguration(meta.up,1,meta.database,4))
        assert(im.getImportConfiguration(meta.up,1).name=="minecraft:copper_ingot")
        assert(p.supplyImport())
        local imported=false
        for i=1,400 do
        local stock=me.getItemsInNetwork({name="minecraft:copper_ingot"})
        if#stock==1 and stock[1].size==11 then imported=true;break end
        computer.pullSignal(.05)
        end
        assert(imported,"actual import bus did not move copper into ME")
        assert(p.report("import"))
        local recipes=me.getCraftables({name="minecraft:oak_planks"})
        assert(#recipes==1 and recipes[1].getItemStack().name=="minecraft:oak_planks","actual plank pattern was not exposed")
        local craft=p.roundTrip(recipes[1])
        local ready=false
        for i=1,200 do
        local amount,why=craft.requesting()
        if type(amount)=="number" then ready=true;break end
        assert(why=="waiting for ae network to load",tostring(why));computer.pullSignal(.05)
        end
        assert(ready,"persisted Craftable did not reattach to actual AE grid")
        local status,why=craft.request(4,true,cpus[1].name)
        assert(status,tostring(why))
        local linked=false
        for i=1,200 do
        local canceled,reason=status.isCanceled()
        if reason==nil then assert(not canceled);linked=true;break end
        assert(reason=="computing",tostring(reason));computer.pullSignal(.05)
        end
        assert(linked,"real async AE request never acquired a link")
        status=p.roundTrip(status)
        local done=false
        for i=1,600 do
        local complete,reason=status.isDone()
        assert(reason==nil or reason=="computing",tostring(reason))
        if complete then done=true;break end
        computer.pullSignal(.05)
        end
        assert(done and not status.isCanceled(),"actual AE crafting did not complete")
        local canceled,reason=status.cancel()
        assert(canceled==false and reason=="job already completed","completed-job cancellation contract changed")
        assert(p.report("crafting"))
        computer.shutdown()
        """;
}
