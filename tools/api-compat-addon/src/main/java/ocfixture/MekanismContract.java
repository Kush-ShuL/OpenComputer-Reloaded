package ocfixture;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actual optional-mod behavior, exercised through the published OC SDK and
 * Minecraft APIs. There are no OC implementation imports or private reflection.
 * The harness must install the official full Mekanism 1.21.1 runtime JAR.
 */
public final class MekanismContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-mekanism-contract");
    private static final List<String> RESULTS = new CopyOnWriteArrayList<>();
    private static final ItemAbility DISMANTLE = ItemAbility.get("wrench_dismantle");
    private static Run active;

    private MekanismContract() {}

    public static boolean enabled() { return Boolean.getBoolean("ocfixture.mekanismOnly"); }
    public static boolean isRunning() { return active != null; }
    public static List<String> results() { return List.copyOf(RESULTS); }

    public static String run(ServerLevel level) throws Exception { return start(level); }

    /** Start on the server thread, after normal mod setup and registry loading. */
    public static String start(ServerLevel level) throws Exception {
        require(active == null, "A Mekanism behavior contract is already running");
        require(ModList.get().isLoaded("mekanism"), "Install the official full Mekanism runtime JAR for this scenario");
        require(API.config != null && API.driver != null && API.network != null, "OC SDK facades are unavailable");
        RESULTS.clear();
        Run run = new Run(level);
        active = run;
        try {
            run.start();
        } catch (Exception | Error error) {
            run.fail(error);
            throw error;
        }
        return "Started real Mekanism contract: wrench=" + run.wrenchEnabled
            + ", chemical=" + run.chemicalEnabled + ", debugIds=" + run.debugIds + ", Lua 5.3";
    }

    /** The real placed Case owns machine updates; this method only observes it. */
    public static void tick() {
        Run run = active;
        if (run == null) return;
        try { run.tick(); }
        catch (Throwable error) { run.fail(error); }
    }

    public static void close() {
        Run run = active;
        if (run == null) return;
        run.fail(new AssertionError("Server stopped before the real Mekanism contract completed"));
    }

    private static boolean allowed(String id) {
        return !API.config.hasPath("integration.modBlacklist") || API.config.getStringList("integration.modBlacklist")
            .stream().noneMatch(entry -> entry.trim().equalsIgnoreCase(id));
    }

    private static ItemStack item(String name) {
        ItemStack stack = Objects.requireNonNull(li.cil.oc.api.Items.get(name), "Missing SDK item: " + name).createItemStack(1);
        require(!stack.isEmpty(), "The SDK returned an empty " + name);
        return stack;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Run {
        final ServerLevel level;
        final BlockPos casePosition = new BlockPos(Integer.getInteger("ocfixture.mekanismX", 4096),
            Integer.getInteger("ocfixture.mekanismY", 96), Integer.getInteger("ocfixture.mekanismZ", 4096));
        final BlockPos splitterPosition = casePosition.east(3);
        final boolean wrenchEnabled = allowed("mekanism");
        final boolean chemicalEnabled = allowed("MekanismAPI|gas") && allowed("mekanismgas");
        final boolean debugIds = API.config.getBoolean("debug.insertIdsInConverters");
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicInteger chemicalCalls = new AtomicInteger();
        final AtomicInteger ordinaryCalls = new AtomicInteger();
        final MekAccess mekanism;
        Case hardware;
        Probe probe;
        FakePlayer actor;
        ItemStack configurator;
        DataComponentType<?> modeType;
        boolean forcedCaseChunk, forcedSplitterChunk, placedCase, placedSplitter, booted;
        int ticks;

        Run(ServerLevel level) throws ReflectiveOperationException {
            this.level = level;
            mekanism = new MekAccess();
        }

        void start() {
            forcedCaseChunk = level.setChunkForced(casePosition.getX() >> 4, casePosition.getZ() >> 4, true);
            forcedSplitterChunk = level.setChunkForced(splitterPosition.getX() >> 4, splitterPosition.getZ() >> 4, true);
            level.getChunkAt(casePosition);
            level.getChunkAt(splitterPosition);
            require(level.isEmptyBlock(casePosition) && level.isEmptyBlock(splitterPosition),
                "Mekanism fixture requires two empty cells; nothing was replaced");

            Block caseBlock = Objects.requireNonNull(li.cil.oc.api.Items.get("case3"), "Real case3 is unavailable").block();
            require(caseBlock != null, "The SDK case descriptor has no block");
            Block splitter = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(API.ID_OWNER, "netsplitter"));
            require(BuiltInRegistries.BLOCK.getKey(splitter).equals(ResourceLocation.fromNamespaceAndPath(API.ID_OWNER, "netsplitter")),
                "The real NetSplitter block is unavailable");
            require(level.setBlock(casePosition, caseBlock.defaultBlockState(), 3), "Could not place the real Case");
            placedCase = true;
            require(level.setBlock(splitterPosition, splitter.defaultBlockState(), 3), "Could not place the real NetSplitter");
            placedSplitter = true;
            BlockEntity entity = level.getBlockEntity(casePosition);
            require(entity instanceof Case, "Placed hardware does not expose the public Case SDK interface");
            hardware = (Case) entity;
            require(hardware.machine() != null, "The real Case has no machine");

            ItemStack cpu = item("cpu3");
            var driver = Driver.driverFor(cpu, hardware.getClass());
            require(driver instanceof MutableProcessor, "The real CPU does not expose MutableProcessor");
            Class<? extends Architecture> native53 = li.cil.oc.api.Machine.architectures().stream()
                .filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type)))
                .findFirst().orElseThrow(() -> new AssertionError("Native Lua 5.3 is required; LuaJ is not a substitute"));
            ((MutableProcessor) driver).setArchitecture(cpu, native53);
            install(cpu);
            install(item("ram6"));
            install(item("hdd3"));
            ItemStack eeprom = li.cil.oc.api.Items.registerEEPROM("Mekanism native contract", BIOS.getBytes(StandardCharsets.UTF_8), new byte[0], false);
            require(!eeprom.isEmpty(), "The public EEPROM factory returned no real EEPROM");
            install(eeprom);
            probe = new Probe(this);

            ResourceLocation configuratorId = ResourceLocation.fromNamespaceAndPath("mekanism", "configurator");
            Item actualItem = BuiltInRegistries.ITEM.get(configuratorId);
            require(BuiltInRegistries.ITEM.getKey(actualItem).equals(configuratorId), "The official Mekanism Configurator is unavailable");
            configurator = new ItemStack(actualItem);
            modeType = Objects.requireNonNull(BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                ResourceLocation.fromNamespaceAndPath("mekanism", "configurator_mode")), "Mekanism's public mode component is unavailable");
            actor = new FakePlayer(level, new GameProfile(UUID.fromString("d7bc3544-66c8-4b8c-9e79-147333073564"), "OC_MekFixture"));
            actor.getAbilities().instabuild = true;
            actor.setPos(splitterPosition.getX() + .5, splitterPosition.getY(), splitterPosition.getZ() - 2);
            actor.setItemInHand(InteractionHand.MAIN_HAND, configurator);
            LOGGER.info("MEKANISM_CONTRACT started: wrench={} chemical={} debugIds={} case={} splitter={}",
                wrenchEnabled, chemicalEnabled, debugIds, casePosition, splitterPosition);
        }

        void install(ItemStack stack) {
            for (int slot = 0; slot < hardware.getContainerSize(); slot++) {
                if (hardware.getItem(slot).isEmpty() && hardware.canPlaceItem(slot, stack)) {
                    hardware.setItem(slot, stack);
                    return;
                }
            }
            throw new AssertionError("No real Case slot accepted " + stack.getHoverName().getString());
        }

        boolean hardwareReady() {
            Machine machine = hardware.machine();
            if (machine.node().network() == null || machine.node().address() == null) return false;
            for (Node node : machine.node().network().nodes()) {
                if (node instanceof Component component && component.name().equals("eeprom")
                    && hardware.componentSlot(node.address()) >= 0) return true;
            }
            return false;
        }

        void tick() {
            require(++ticks <= 800, "Real Mekanism native contract exceeded 800 ticks");
            if (!booted) {
                require(ticks <= 100, "The real Case did not initialize its installed components within 100 ticks");
                if (!hardwareReady() || ticks < 5) return;
                for (String mode : List.of("wrench", "rotate", "empty", "configurate_items", "configurate_fluids",
                    "configurate_chemicals", "configurate_energy", "configurate_heat")) {
                    interact(mode, false);
                }
                interact("wrench", true);
                hardware.machine().node().connect(probe.node());
                power();
                require(hardware.machine().start(), "Real Case failed to boot native Lua 5.3: " + hardware.machine().lastError());
                booted = true;
                return;
            }
            power();
            if (!hardware.machine().isRunning()) {
                require(completed.get(), "The real native Case stopped before its proof callback: " + hardware.machine().lastError());
                require(chemicalCalls.get() == 4 && ordinaryCalls.get() == 1, "The BIOS skipped required real conversion calls");
                pass("native53 real Case callbacks: " + (chemicalEnabled
                    ? "ChemicalStack amount/name/label, empty stack, id=" + debugIds
                        + ", exact 2147483665/9007199254740993/9223372036854775807 integers"
                    : "blacklisted ChemicalStack remains an ordinary unsupported value"));
                pass("ordinary object and ordinary amount/name/label table retain their normal conversion behavior");
                dispose();
                active = null;
                LOGGER.info("MEKANISM_CONTRACT_COMPLETE status=PASS wrench={} chemical={} debugIds={}", wrenchEnabled, chemicalEnabled, debugIds);
            }
        }

        private void power() {
            if (probe.node() instanceof Connector connector) connector.changeBuffer(Double.MAX_VALUE);
        }

        private void interact(String mode, boolean sneaking) {
            setMode(configurator, modeType, mode);
            require(configurator.canPerformAction(DISMANTLE) == mode.equals("wrench"),
                "The official Configurator did not accept the requested real mode: " + mode);
            actor.setShiftKeyDown(sneaking);
            BlockState before = level.getBlockState(splitterPosition);
            Map<Direction, Boolean> previous = faces(before);
            Direction clicked = Direction.NORTH, changed = sneaking ? clicked.getOpposite() : clicked;
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(splitterPosition), clicked, splitterPosition, false);
            ItemInteractionResult result = before.useItemOn(configurator, level, actor, InteractionHand.MAIN_HAND, hit);
            boolean shouldToggle = wrenchEnabled && mode.equals("wrench");
            require(result == (shouldToggle ? ItemInteractionResult.sidedSuccess(false) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION),
                "Unexpected real NetSplitter interaction result in " + mode + ": " + result);
            BlockState after = level.getBlockState(splitterPosition);
            require(after.is(before.getBlock()), "Configurator interaction replaced the NetSplitter");
            Map<Direction, Boolean> current = faces(after);
            for (Direction side : Direction.values()) {
                boolean expected = previous.get(side) ^ (shouldToggle && side == changed);
                require(current.get(side) == expected, "NetSplitter " + side + " state was wrong after " + mode + ", sneak=" + sneaking);
            }
            pass("real Configurator " + mode + " sneak=" + sneaking + " -> BlockState.useItemOn: "
                + (shouldToggle ? changed.getName() + " toggled" : "all six face states unchanged"));
        }

        private static Map<Direction, Boolean> faces(BlockState state) {
            Map<Direction, Boolean> result = new EnumMap<>(Direction.class);
            for (Direction side : Direction.values()) {
                var property = state.getBlock().getStateDefinition().getProperty(side.getName());
                require(property instanceof BooleanProperty, "NetSplitter is missing the public " + side.getName() + " boolean state");
                result.put(side, state.getValue((BooleanProperty) property));
            }
            return result;
        }

        /** Use the real registered data component's codec; no internal Mekanism enum linkage. */
        private static <T> void setMode(ItemStack stack, DataComponentType<T> type, String mode) {
            Codec<T> codec = Objects.requireNonNull(type.codec(), "Configurator mode has no public persistent codec");
            stack.set(type, codec.parse(NbtOps.INSTANCE, StringTag.valueOf(mode)).getOrThrow());
        }

        void requireNativeCaller(Context context) {
            require(context == hardware.machine(), "The callback was invoked outside the real placed Case's VM");
        }

        void pass(String message) {
            RESULTS.add("PASS Mekanism: " + message);
            LOGGER.info("MEKANISM_CONTRACT PASS {}", message);
        }

        void fail(Throwable error) {
            RESULTS.add("FAIL Mekanism: " + error);
            LOGGER.error("MEKANISM_CONTRACT FAIL", error);
            try { dispose(); }
            catch (Throwable cleanup) { LOGGER.error("MEKANISM_CONTRACT cleanup failed", cleanup); }
            active = null;
        }

        void dispose() {
            if (hardware != null) {
                hardware.machine().stop();
                hardware.clearContent();
            }
            if (probe != null) probe.node().remove();
            if (actor != null) actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (placedCase) { level.removeBlock(casePosition, false); placedCase = false; }
            if (placedSplitter) { level.removeBlock(splitterPosition, false); placedSplitter = false; }
            if (forcedSplitterChunk) { level.setChunkForced(splitterPosition.getX() >> 4, splitterPosition.getZ() >> 4, false); forcedSplitterChunk = false; }
            if (forcedCaseChunk) { level.setChunkForced(casePosition.getX() >> 4, casePosition.getZ() >> 4, false); forcedCaseChunk = false; }
        }
    }

    /** The addon supplies actual foreign objects, never their expected converted tables. */
    public static final class Probe extends AbstractManagedEnvironment {
        private final Run run;

        Probe(Run run) {
            this.run = run;
            setNode(Network.newNode(this, Visibility.Network).withComponent("mekanism_probe", Visibility.Network)
                .withConnector(10_000_000).create());
        }

        @Callback(direct = true, limit = 64)
        public Object[] metadata(Context context, Arguments args) {
            run.requireNativeCaller(context);
            return new Object[]{Map.of("enabled", run.chemicalEnabled, "debugIds", run.debugIds,
                "name", run.mekanism.hydrogenName, "label", run.mekanism.hydrogenLabel,
                "id", run.mekanism.hydrogenId, "emptyName", run.mekanism.emptyName,
                "emptyLabel", run.mekanism.emptyLabel, "emptyId", run.mekanism.emptyId)};
        }

        @Callback(direct = true, limit = 64)
        public Object[] chemical(Context context, Arguments args) throws ReflectiveOperationException {
            run.requireNativeCaller(context);
            Object stack = switch (args.checkString(0)) {
                case "large" -> run.mekanism.stack(2_147_483_665L);
                case "boundary" -> run.mekanism.stack(9_007_199_254_740_993L);
                case "maximum" -> run.mekanism.stack(Long.MAX_VALUE);
                case "empty" -> run.mekanism.empty;
                default -> throw new IllegalArgumentException("Unknown real chemical sample");
            };
            run.chemicalCalls.incrementAndGet();
            return new Object[]{stack, stack.toString()};
        }

        @Callback(direct = true, limit = 64)
        public Object[] ordinary(Context context, Arguments args) {
            run.requireNativeCaller(context);
            run.ordinaryCalls.incrementAndGet();
            return new Object[]{new OrdinaryPayload(), "ordinary-kept", Map.of("ordinary", true,
                "amount", 41L, "name", "ordinary-name", "label", "ordinary-label")};
        }

        @Callback(direct = true, limit = 64)
        public Object[] report(Context context, Arguments args) {
            run.requireNativeCaller(context);
            require("native53-complete".equals(args.checkString(0)), "Unexpected native Mekanism proof marker");
            require(run.chemicalCalls.get() == 4 && run.ordinaryCalls.get() == 1, "Native BIOS did not exercise every requested payload");
            run.completed.set(true);
            return new Object[]{true};
        }
    }

    private static final class OrdinaryPayload {
        @Override public String toString() { return "ordinary-payload"; }
    }

    /** Bind only the verified public official ChemicalStack API, without an API JAR dependency. */
    private static final class MekAccess {
        final Registry<Object> registry;
        final Constructor<?> stackConstructor;
        final Holder<Object> hydrogen;
        final Object empty;
        final String hydrogenName, hydrogenLabel, emptyName, emptyLabel;
        final int hydrogenId, emptyId;

        @SuppressWarnings("unchecked")
        MekAccess() throws ReflectiveOperationException {
            ClassLoader loader = MekanismContract.class.getClassLoader();
            Class<?> stackType = Class.forName("mekanism.api.chemical.ChemicalStack", true, loader);
            Object chemicalRegistry = Class.forName("mekanism.api.MekanismAPI", true, loader)
                .getField("CHEMICAL_REGISTRY").get(null);
            require(chemicalRegistry instanceof Registry<?>, "Mekanism's real chemical registry is unavailable");
            registry = (Registry<Object>) chemicalRegistry;
            ResourceLocation expectedHydrogen = ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen");
            Object chemical = registry.get(expectedHydrogen);
            require(expectedHydrogen.equals(registry.getKey(chemical)), "Official hydrogen is missing from the real Mekanism registry");
            hydrogen = registry.wrapAsHolder(chemical);
            stackConstructor = stackType.getConstructor(Holder.class, long.class);
            empty = stackType.getField("EMPTY").get(null);
            Method getChemical = stackType.getMethod("getChemical");
            Method getText = stackType.getMethod("getTextComponent");
            Object sample = stack(1);
            Object emptyChemical = getChemical.invoke(empty);
            hydrogenName = Objects.requireNonNull(registry.getKey(chemical)).toString();
            hydrogenId = registry.getId(chemical);
            hydrogenLabel = ((net.minecraft.network.chat.Component) getText.invoke(sample)).getString();
            emptyName = Objects.requireNonNull(registry.getKey(emptyChemical)).toString();
            emptyId = registry.getId(emptyChemical);
            emptyLabel = ((net.minecraft.network.chat.Component) getText.invoke(empty)).getString();
        }

        Object stack(long amount) throws ReflectiveOperationException {
            return stackConstructor.newInstance(hydrogen, amount);
        }
    }

    private static final String BIOS = """
        assert(computer.getArchitecture() == "Lua 5.3", "the chemical contract requires the real native Lua 5.3 VM")
        local address = assert(component.list("mekanism_probe", true)(), "real addon probe is not connected")
        local p = component.proxy(address)
        local info = p.metadata()
        local function check(sample, amount, decimal, empty)
          local result, raw = p.chemical(sample)
          if not info.enabled then
            assert(type(result) == "string" and result == raw, sample .. ": blacklisted chemical was still converted")
            return
          end
          assert(type(result) == "table", sample .. ": real ChemicalStack did not cross the callback converter chain")
          assert(result.amount == amount and tostring(result.amount) == decimal, sample .. ": chemical long amount lost precision")
          assert(math.type(result.amount) == "integer", sample .. ": chemical amount is not a native Lua 5.3 integer")
          assert(result.name == (empty and info.emptyName or info.name), sample .. ": wrong registry name")
          assert(result.label == (empty and info.emptyLabel or info.label), sample .. ": wrong display label")
          if info.debugIds then
            assert(result.id == (empty and info.emptyId or info.id), sample .. ": missing/wrong debug registry id")
          else
            assert(result.id == nil, sample .. ": debug registry id leaked while disabled")
          end
        end
        check("large", 2147483665, "2147483665", false)
        check("boundary", 9007199254740993, "9007199254740993", false)
        check("maximum", 9223372036854775807, "9223372036854775807", false)
        check("empty", 0, "0", true)
        local object, marker, ordinary = p.ordinary()
        assert(object == "ordinary-payload" and marker == "ordinary-kept", "ordinary object conversion was changed")
        assert(type(ordinary) == "table" and ordinary.ordinary and ordinary.amount == 41
          and ordinary.name == "ordinary-name" and ordinary.label == "ordinary-label" and ordinary.id == nil,
          "ordinary table was changed by the chemical integration")
        assert(p.report("native53-complete"))
        computer.shutdown()
        """;
}
