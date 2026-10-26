package ocfixture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import li.cil.oc.api.API;
import li.cil.oc.api.IMC;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One external IMC template traverses the real assembler and disassembler inventories. */
public final class ProductionContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-api-production");
    private static final String MARKER = "oc_fixture_production";
    private static final List<String> RESULTS = new ArrayList<>();
    private static boolean registered, enqueued;
    private static int validationCalls, assemblyCalls, disassemblyCalls;
    private static double assemblyEnergy;
    private static Run current;

    private ProductionContract() {}

    /** Enqueued common setup establishes the fixture's power-scaled job duration. */
    public static void register() {
        require(API.config != null, "The public config facade is unavailable");
        assemblyEnergy = Math.max(1, API.config.getDouble("power.assemblerTickAmount"))
                * Math.max(1, API.config.getDouble("power.tickFrequency")) * 3;
        require(Double.isFinite(assemblyEnergy), "Invalid configured assembly power");
        registered = true;
    }

    /** Call from InterModEnqueueEvent; this intentionally uses no implementation registry. */
    public static void enqueue() {
        if (enqueued) return;
        String owner = ProductionContract.class.getName();
        IMC.registerAssemblerTemplate("ocfixture production assembly", owner + ".selectStick", owner + ".validate",
                owner + ".assemble", null, null, null, List.of());
        IMC.registerDisassemblerTemplate("ocfixture production disassembly", owner + ".selectBook", owner + ".disassemble");
        enqueued = true;
    }

    public static boolean selectStick(ItemStack stack) { return stack.is(Items.STICK) && marked(stack); }
    public static boolean selectBook(ItemStack stack) { return stack.is(Items.BOOK) && marked(stack); }
    public static Object[] validate(Container inventory) {
        validationCalls++;
        boolean valid = inventory.getContainerSize() == 22 && selectStick(inventory.getItem(0)) && inventory.getItem(0).getCount() == 1;
        for (int slot = 1; slot < inventory.getContainerSize(); slot++) valid &= inventory.getItem(slot).isEmpty();
        return new Object[]{valid, net.minecraft.network.chat.Component.literal("External production fixture")};
    }
    public static Object[] assemble(Container inventory) {
        require(selectStick(inventory.getItem(0)) && inventory.getItem(0).getCount() == 1,
                "Assembler did not pass its actual input container to the addon");
        assemblyCalls++;
        return new Object[]{mark(new ItemStack(Items.BOOK)), assemblyEnergy};
    }
    public static Object[] disassemble(ItemStack stack, ItemStack[] ingredients) {
        require(selectBook(stack) && stack.getCount() == 1 && ingredients != null,
                "Disassembler did not pass the produced book and inferred ingredients to the addon");
        disassemblyCalls++;
        // No probabilistic output is asserted. The second array must bypass the configured break chance.
        return new Object[]{new ItemStack[0], new ItemStack[]{mark(new ItemStack(Items.FEATHER, 2))}};
    }

    public static String run(ServerLevel level) throws Exception {
        require(registered && enqueued, "Production IMC setup was not invoked during mod lifecycle");
        require(current == null, "A production fixture is already running");
        RESULTS.clear();
        if (!Boolean.getBoolean("ocfixture.worldFixture")) {
            RESULTS.add("SKIP production: temporary world fixtures are disabled");
            return "Skipped production contract outside the isolated fixture run";
        }
        require(API.isPowerEnabled, "The isolated production contract requires power to be enabled");
        validationCalls = assemblyCalls = disassemblyCalls = 0;
        Run run = new Run(level); current = run;
        try { run.start(); }
        catch (Exception | Error failure) { current = null; run.cleanup(); throw failure; }
        return "Started external IMC production contract";
    }

    public static void tick() {
        Run run = current;
        if (run == null) return;
        try {
            if (!run.tick()) return;
            RESULTS.add("PASS production: real assembler consumes the external IMC template, input and powered progress");
            RESULTS.add("PASS production: real disassembler consumes the external template and inserts its guaranteed drops into a chest");
            LOGGER.info("API_PRODUCTION PASS external IMC assembly, powered progress and guaranteed disassembly drops");
        } catch (Throwable error) {
            RESULTS.add("FAIL production: " + error);
            LOGGER.error("API_PRODUCTION FAIL", error);
        }
        current = null; run.cleanup();
    }
    public static boolean isRunning() { return current != null; }
    public static List<String> results() { return List.copyOf(RESULTS); }
    public static void close() { Run run = current; current = null; if (run != null) run.cleanup(); }

    private static final class Run {
        final ServerLevel level;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final List<Node> nodes = new ArrayList<>();
        BlockPos assemblerPos, disassemblerPos, chestPos;
        Container assembler, disassembler, chest;
        Component component;
        Supply supply;
        double startingPower;
        int ticks, phase;
        boolean sawIntermediate;

        Run(ServerLevel level) { this.level = level; }
        void start() throws Exception {
            Block assemblerBlock = block("assembler"), disassemblerBlock = block("disassembler");
            BlockPos spawn = level.getSharedSpawnPos();
            int y = Math.min(level.getMaxBuildHeight() - 4, Math.max(level.getMinBuildHeight() + 12, spawn.getY() + 24));
            for (int offset = 0; offset < 8; offset++) {
                BlockPos candidate = new BlockPos(spawn.getX() + 6, y + offset, spawn.getZ() + 6);
                if (candidate.getY() >= level.getMaxBuildHeight() - 1) break;
                if (empty(candidate) && empty(candidate.east(3)) && empty(candidate.east(3).south())) {
                    assemblerPos = candidate; disassemblerPos = candidate.east(3); chestPos = disassemblerPos.south(); break;
                }
            }
            require(assemblerPos != null, "No empty cells above the isolated world's spawn for production devices");
            place(assemblerPos, assemblerBlock.defaultBlockState());
            place(disassemblerPos, disassemblerBlock.defaultBlockState());
            place(chestPos, Blocks.CHEST.defaultBlockState());
            BlockEntity assemblerEntity = level.getBlockEntity(assemblerPos), disassemblerEntity = level.getBlockEntity(disassemblerPos);
            require(assemblerEntity instanceof Environment && assemblerEntity instanceof Container,
                    "Assembler is not a real public Environment and inventory");
            require(disassemblerEntity instanceof Environment && disassemblerEntity instanceof Container,
                    "Disassembler is not a real public Environment and inventory");
            require(level.getBlockEntity(chestPos) instanceof Container, "Adjacent vanilla chest did not create an inventory");
            assembler = (Container) assemblerEntity; disassembler = (Container) disassemblerEntity;
            chest = (Container) level.getBlockEntity(chestPos);
            Network.joinOrCreateNetwork(assemblerEntity); Network.joinOrCreateNetwork(disassemblerEntity);
            Node assemblyNode = ((Environment) assemblerEntity).node(), disassemblyNode = ((Environment) disassemblerEntity).node();
            require(assemblyNode instanceof Component && assemblyNode instanceof Connector && disassemblyNode instanceof Connector,
                    "Production devices did not expose their actual component/energy nodes");
            nodes.add(assemblyNode); nodes.add(disassemblyNode); component = (Component) assemblyNode;
            supply = new Supply(assemblyEnergy + 10000); Network.joinNewNetwork(supply.node());
            supply.node().connect(assemblyNode); supply.node().connect(disassemblyNode);
            require(supply.node().changeBuffer(assemblyEnergy + 1000) == 0, "Could not charge the production network");
            startingPower = supply.node().globalBuffer();
            ItemStack input = mark(new ItemStack(Items.STICK));
            require(assembler.canPlaceItem(0, input), "IMC assembly selector was not consumed by the device");
            assembler.setItem(0, input);
            Object[] start = component.invoke("start", new PlainContext(supply.node()));
            require(start != null && start.length == 1 && Boolean.TRUE.equals(start[0]), "External assembly template did not start");
            require(assemblyCalls == 1 && validationCalls > 0 && assembler.getItem(0).isEmpty(),
                    "The device did not validate, consume its input and enqueue the template output exactly once");
            require(progress() == 0 && supply.node().globalBuffer() == startingPower,
                    "Assembly completed or consumed work energy synchronously in start()");
        }
        boolean tick() throws Exception {
            require(++ticks <= 400, "Production devices did not finish within 400 ticks");
            if (phase == 0) {
                ItemStack result = assembler.getItem(0);
                if (result.isEmpty()) {
                    double progress = progress();
                    if (progress > 0 && progress < 100) {
                        sawIntermediate = true;
                        require(supply.node().globalBuffer() < startingPower, "Assembly progress advanced without consuming network energy");
                    }
                    return false;
                }
                require(selectBook(result) && result.getCount() == 1 && sawIntermediate,
                        "The assembly result did not survive the real powered queue");
                require(Math.abs(startingPower - supply.node().globalBuffer() - assemblyEnergy) < .000001,
                        "Assembly did not consume the template's stated energy cost");
                ItemStack book = assembler.removeItem(0, 1);
                require(disassembler.canPlaceItem(0, book), "IMC disassembly selector was not consumed by the device");
                disassembler.setItem(0, book); phase = 1;
                return false;
            }
            int count = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (!stack.isEmpty()) {
                    require(stack.is(Items.FEATHER) && marked(stack), "Disassembler emitted a recipe fallback instead of the addon output");
                    count += stack.getCount();
                }
            }
            if (count == 0) return false;
            require(count == 2 && disassemblyCalls == 1 && disassembler.isEmpty(),
                    "Guaranteed template output was lost, duplicated, or its input was not consumed");
            return true;
        }
        private double progress() throws Exception {
            Object[] status = component.invoke("status", new PlainContext(supply.node()));
            require(status != null && status.length == 2 && "busy".equals(status[0]) && status[1] instanceof Number,
                    "Assembler did not expose the real busy/progress callback");
            return ((Number) status[1]).doubleValue();
        }
        private Block block(String path) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("opencomputers", path);
            require(BuiltInRegistries.BLOCK.containsKey(id), "Missing registered production device " + id);
            return BuiltInRegistries.BLOCK.get(id);
        }
        private boolean empty(BlockPos pos) { return level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty() && level.getBlockEntity(pos) == null; }
        private void place(BlockPos pos, BlockState state) {
            restored.put(pos, level.getBlockState(pos));
            require(level.setBlock(pos, state, 3), "Could not place isolated fixture block at " + pos);
        }
        void cleanup() {
            if (supply != null) supply.node().remove();
            for (Node node : nodes) node.remove(); nodes.clear();
            for (var entry : restored.entrySet()) {
                if (level.getBlockEntity(entry.getKey()) instanceof Container inventory) inventory.clearContent();
                level.setBlock(entry.getKey(), entry.getValue(), 3);
            }
            restored.clear();
            if (assemblerPos != null) {
                AABB region = new AABB(assemblerPos.getX() - 32, level.getMinBuildHeight(), assemblerPos.getZ() - 32,
                        assemblerPos.getX() + 32, level.getMaxBuildHeight(), assemblerPos.getZ() + 32);
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, region, entity -> marked(entity.getItem()))) item.discard();
            }
        }
    }
    private static final class Supply extends AbstractManagedEnvironment {
        Supply(double capacity) { setNode(Network.newNode(this, Visibility.Network).withConnector(capacity).create()); }
        @Override public Connector node() { return (Connector) super.node(); }
    }
    private record PlainContext(Node node) implements Context {
        @Override public boolean canInteract(String player) { return true; }
        @Override public boolean isRunning() { return false; }
        @Override public boolean isPaused() { return false; }
        @Override public boolean start() { return false; }
        @Override public boolean stop() { return false; }
        @Override public boolean pause(double seconds) { return false; }
        @Override public void consumeCallBudget(double cost) {}
        @Override public boolean signal(String name, Object... args) { return false; }
    }
    private static ItemStack mark(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(MARKER, true)); return stack;
    }
    private static boolean marked(ItemStack stack) { return !stack.isEmpty() && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(MARKER); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
