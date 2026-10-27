package ocfixture;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real native Case/card2 <-> one real framed bundled cable <-> real OC I/O.
 * This addon imports only the published OC SDK and Minecraft/NeoForge. The
 * reflected members are exclusively ProjectRed's public api source set at
 * https://github.com/MrTJP/ProjectRed/tree/v1.21.1-4.23.0/api/src/main/java/mrtjp/projectred/api .
 * Cable placement uses its real registered ItemStack's normal useOn path; no
 * synthetic emitter, provider, interaction registration or signal writes exist.
 * A single straight framed cable is covered, not branched/face/multipart networks.
 */
public final class ProjectRedContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-projectred-contract");
    private static final List<String> RESULTS = new CopyOnWriteArrayList<>();
    private static final int[] LEVELS = {0, 1, 127, 128, 254, 255};
    private static Run active;

    private ProjectRedContract() {}

    public static boolean enabled() { return Boolean.getBoolean("ocfixture.projectRedOnly"); }
    public static boolean isRunning() { return active != null; }
    public static List<String> results() { return List.copyOf(RESULTS); }

    public static String run(ServerLevel level) throws Exception {
        require(active == null, "A ProjectRed contract is already running");
        require(ModList.get().isLoaded("projectred_core") && ModList.get().isLoaded("projectred_transmission"),
            "Install the official ProjectRed Core/Transmission and their real runtime dependencies");
        require(API.config != null && API.network != null && API.driver != null, "OC SDK facades are unavailable");
        RESULTS.clear();
        Run run = new Run(level);
        active = run;
        try { run.start(); }
        catch (Exception | Error error) { run.fail(error); throw error; }
        return "Started real ProjectRed framed-cable/native53 contract, integration=" + run.integrationEnabled;
    }

    /** Real world block tickers own machines, input sampling and wire propagation. */
    public static void tick() {
        Run run = active;
        if (run == null) return;
        try { run.tick(); }
        catch (Throwable error) { run.fail(error); }
    }

    public static void close() {
        if (active != null) active.fail(new AssertionError("Server stopped before the ProjectRed contract completed"));
    }

    private static boolean integrationAllowed() {
        return !API.config.hasPath("integration.modBlacklist") || API.config.getStringList("integration.modBlacklist").stream()
            .noneMatch(id -> switch (id.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "projectred", "projectred-transmission", "projectred_transmission", "projectred-core", "projectred_core" -> true;
                default -> false;
            });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static ItemStack item(String name) {
        ItemStack result = Objects.requireNonNull(li.cil.oc.api.Items.get(name), "Missing SDK item " + name).createItemStack(1);
        require(!result.isEmpty(), "SDK returned an empty " + name);
        return result;
    }

    private static ItemStack foreignItem(String namespace, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        var item = BuiltInRegistries.ITEM.get(id);
        require(id.equals(BuiltInRegistries.ITEM.getKey(item)), "Missing actual registered item " + id);
        return new ItemStack(item);
    }

    private static int[] pattern(int phase) {
        int[] values = new int[16];
        for (int color = 0; color < 16; color++) values[color] = LEVELS[(color + phase) % LEVELS.length];
        return values;
    }

    private static final class Run {
        final ServerLevel level;
        final boolean integrationEnabled = integrationAllowed();
        final BlockPos origin = new BlockPos(Integer.getInteger("ocfixture.projectRedX", 4128),
            Integer.getInteger("ocfixture.projectRedY", 96), Integer.getInteger("ocfixture.projectRedZ", 4096));
        final BlockPos wirePosition = origin.east(), ioPosition = origin.east(2);
        final BlockPos waypointPosition = origin.west(2), splitterPosition = origin.west(4);
        // The southern OC cables carry the computer network. They never carry PR signals.
        final List<BlockPos> cables = List.of(origin.south(), origin.east().south(), origin.east(2).south());
        final List<BlockPos> placed = new ArrayList<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final Set<String> proofs = new LinkedHashSet<>();
        final AtomicBoolean completed = new AtomicBoolean();
        final Access access;
        Case hardware;
        Component card, io;
        Probe probe;
        FakePlayer actor;
        int ticks, stopTick = -1, orientation, forwardProofs, reverseProofs, zeroProofs;
        boolean booted;
        String lastObservation = "no native request yet";

        Run(ServerLevel level) throws ReflectiveOperationException {
            this.level = level;
            access = new Access();
        }

        void start() {
            List<BlockPos> positions = new ArrayList<>(List.of(origin, wirePosition, ioPosition, waypointPosition, splitterPosition));
            positions.addAll(cables);
            for (BlockPos position : positions) {
                if (level.setChunkForced(position.getX() >> 4, position.getZ() >> 4, true))
                    forced.add(ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
                level.getChunkAt(position);
                require(level.isEmptyBlock(position), "ProjectRed fixture needs empty cells; nothing was replaced at " + position);
            }
            BlockState state = sdkBlock("case3").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
            place(origin, state);
            place(ioPosition, sdkBlock("redstone").defaultBlockState());
            for (BlockPos position : cables) place(position, sdkBlock("cable").defaultBlockState());
            place(waypointPosition, sdkBlock("waypoint").defaultBlockState());
            place(splitterPosition, sdkBlock("netsplitter").defaultBlockState());
            require(level.getBlockEntity(origin) instanceof Case, "The placed computer lacks the public Case interface");
            hardware = (Case) level.getBlockEntity(origin);
            require(level.getBlockEntity(ioPosition) instanceof Environment, "The actual I/O block is not an SDK Environment");
            actor = new FakePlayer(level, new GameProfile(UUID.fromString("da8aa0b1-4fc5-48ed-b94a-55b614210d40"), "OC_ProjectRed"));
            actor.getAbilities().instabuild = false;
            actor.setPos(origin.getX() + .5, origin.getY() + 2, origin.getZ() + .5);

            ItemStack wire = foreignItem("projectred_transmission", "neutral_framed_bundled_wire");
            actor.setItemInHand(InteractionHand.MAIN_HAND, wire);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(origin).add(.5, 0, 0), Direction.EAST, origin, false);
            // Record the reserved empty cell first so a partial placement is cleaned up too.
            placed.add(wirePosition);
            require(wire.useOn(new UseOnContext(actor, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                "The real ProjectRed framed bundled item refused normal right-click placement");
            require(access.containsWire(level, wirePosition), "The normal item interaction did not place an actual framed bundled cable");
            require(wire.isEmpty(), "A survival placement did not consume its real framed-cable item");
            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            ItemStack cpu = item("cpu3");
            var driver = Driver.driverFor(cpu, hardware.getClass());
            require(driver instanceof MutableProcessor, "The actual CPU is not a MutableProcessor");
            Class<? extends Architecture> architecture = li.cil.oc.api.Machine.architectures().stream()
                .filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type))).findFirst()
                .orElseThrow(() -> new AssertionError("This contract requires the real native Lua 5.3 architecture"));
            ((MutableProcessor) driver).setArchitecture(cpu, architecture);
            install(cpu); install(item("ram6")); install(item("redstonecard2"));
            byte[] bios = BIOS.getBytes(StandardCharsets.UTF_8);
            require(bios.length < 4096, "The native ProjectRed BIOS must fit the real EEPROM");
            install(li.cil.oc.api.Items.registerEEPROM("ProjectRed native contract", bios, new byte[0], false));
            probe = new Probe(this);
            LOGGER.info("PROJECTRED_CONTRACT started integration={} case={} wire={} io={}", integrationEnabled, origin, wirePosition, ioPosition);
        }

        static Block sdkBlock(String name) {
            Block block = Objects.requireNonNull(li.cil.oc.api.Items.get(name), "Missing SDK block " + name).block();
            require(block != null, "SDK descriptor is not a real block: " + name);
            return block;
        }

        void place(BlockPos position, BlockState state) {
            require(level.setBlock(position, state, 3), "Could not place real fixture block at " + position);
            placed.add(position);
        }

        void install(ItemStack stack) {
            require(!stack.isEmpty(), "The actual EEPROM/hardware factory returned an empty item");
            for (int slot = 0; slot < hardware.getContainerSize(); slot++) {
                if (hardware.getItem(slot).isEmpty() && hardware.canPlaceItem(slot, stack)) {
                    hardware.setItem(slot, stack);
                    return;
                }
            }
            throw new AssertionError("No actual Case slot accepted " + stack.getHoverName().getString());
        }

        boolean ready() {
            Node machine = hardware.machine().node();
            Node external = ((Environment) level.getBlockEntity(ioPosition)).node();
            if (machine.network() == null || !(external instanceof Component component) || external.network() != machine.network()) return false;
            io = component;
            card = null;
            boolean eeprom = false;
            for (Node node : machine.network().nodes()) {
                if (!(node instanceof Component candidate) || !candidate.canBeSeenFrom(machine)) continue;
                if (candidate.name().equals("redstone") && hardware.componentSlot(node.address()) >= 0) card = candidate;
                if (candidate.name().equals("eeprom") && hardware.componentSlot(node.address()) >= 0) eeprom = true;
            }
            return card != null && eeprom && io.name().equals("redstone") && io.canBeSeenFrom(machine)
                && card.methods().contains("setBundledOutput") && io.methods().contains("getBundledInput");
        }

        void tick() throws Exception {
            require(++ticks <= 1600, "ProjectRed contract timed out: " + lastObservation);
            if (!booted) {
                require(ticks <= 150, "Actual Case/card2/I/O did not initialize through their world tickers");
                if (ticks < 5 || !ready()) return;
                hardware.machine().node().connect(probe.node());
                power();
                require(hardware.machine().start(), "Native Case refused to start: " + hardware.machine().lastError());
                booted = true;
                return;
            }
            power();
            if (stopTick < 0) {
                require(hardware.machine().isRunning(), "The native Case stopped before Java completion: " + hardware.machine().lastError());
                if (!completed.get()) return;
                require(orientation == 1 && forwardProofs == 13 && reverseProofs == 12 && zeroProofs == 25,
                    "The real native BIOS skipped required directional/channel stages");
                screwdriver();
                hardware.machine().stop();
                stopTick = ticks;
                return;
            }
            // Let the real Case ticker disable/clear output and the foreign cable propagate.
            if (ticks - stopTick < 5) return;
            require(ticks - stopTick <= 100, "Stopped Case output failed to clear: " + lastObservation);
            int expectedMask = integrationEnabled ? 1 << Direction.EAST.get3DDataValue() : 0;
            int mask = access.mask(level, wirePosition);
            boolean cleared = !hardware.machine().isRunning() && mask == expectedMask
                && zero(access.input(level, ioPosition, Direction.WEST)) && zero(read(io, Direction.WEST, false))
                && zero(read(card, Direction.EAST, true));
            lastObservation = "stopped Case mask=" + mask + " expected=" + expectedMask;
            if (!cleared) return;
            pass(integrationEnabled
                ? "native53 real Case/card2 and external I/O -> one actual item-placed framed cable: both directions, all 16 colors, 0/1/127/128/254/255 and two Case orientations; official API equals actual OC provider/native reads"
                : "blacklisted integration: native OC outputs remain programmable but actual framed cable has no OC connections, official API/input callbacks stay zero and screwdriver interactions are refused");
            pass("real Case stop removes its cable connection and clears all 16 output/input channels after ordinary world ticks");
            RESULTS.add("LIMIT ProjectRed: one straight neutral framed cable only; branched/face wires, multipart covers, chunk reload, simultaneous multi-source networks and multiplayer remain unverified");
            dispose(); active = null;
            LOGGER.info("PROJECTRED_CONTRACT_COMPLETE status=PASS integration={}", integrationEnabled);
        }

        void power() { if (probe.node() instanceof Connector connector) connector.changeBuffer(Double.MAX_VALUE); }

        int side(Component component, Direction global) {
            return (component == card ? hardware.toLocal(global) : global).get3DDataValue();
        }

        int[] read(Component component, Direction global, boolean output) throws Exception {
            Object result = component.invoke(output ? "getBundledOutput" : "getBundledInput", hardware.machine(), side(component, global))[0];
            require(result instanceof Map<?, ?>, "Actual SDK redstone callback returned no color map");
            Map<?, ?> map = (Map<?, ?>) result;
            int[] values = new int[16];
            for (int color = 0; color < 16; color++) {
                Object value = map.containsKey(color) ? map.get(color) : map.get((double) color);
                require(value instanceof Number, "Actual SDK callback omitted color " + color);
                values[color] = ((Number) value).intValue();
            }
            return values;
        }

        boolean prove(String key, String source, int phase) throws Exception {
            require(phase >= 0 && phase < LEVELS.length, "Unexpected native pattern phase");
            require(source.equals("case") || source.equals("io") || source.equals("zero"), "Unexpected native signal source");
            int[] expected = integrationEnabled && !source.equals("zero") ? pattern(phase) : new int[16];
            int mask = access.mask(level, wirePosition);
            int both = (1 << Direction.WEST.get3DDataValue()) | (1 << Direction.EAST.get3DDataValue());
            byte[] atCase = access.input(level, origin, Direction.EAST), atIo = access.input(level, ioPosition, Direction.WEST);
            int[] fromCase = read(card, Direction.EAST, false), fromIo = read(io, Direction.WEST, false);
            lastObservation = key + " mask=" + mask + " APIcase=" + Arrays.toString(unsigned(atCase))
                + " APIio=" + Arrays.toString(unsigned(atIo)) + " OCcase=" + Arrays.toString(fromCase) + " OCio=" + Arrays.toString(fromIo);
            // Read the actual receiver's cache. A source's own input notification
            // is not needed to prove either direction; the reverse stage tests it.
            boolean matches = mask == (integrationEnabled ? both : 0)
                && signal(atCase, expected, integrationEnabled) && signal(atIo, expected, integrationEnabled)
                && (source.equals("case") ? Arrays.equals(fromIo, expected)
                    : source.equals("io") ? Arrays.equals(fromCase, expected)
                    : Arrays.equals(fromCase, expected) && Arrays.equals(fromIo, expected))
                && zero(access.input(level, origin, Direction.WEST)) && zero(access.input(level, ioPosition, Direction.EAST))
                && zero(read(card, Direction.WEST, false)) && zero(read(io, Direction.EAST, false));
            if (!matches) return false;
            if (proofs.add(key)) {
                if (source.equals("case")) forwardProofs++;
                else if (source.equals("io")) reverseProofs++;
                else zeroProofs++;
                LOGGER.info("PROJECTRED_CONTRACT settled {} mask={} integration={}", key, mask, integrationEnabled);
            }
            return true;
        }

        int rotate() {
            require(orientation == 0, "Native BIOS rotated the actual Case more than once");
            BlockState before = level.getBlockState(origin);
            require(level.setBlock(origin, before.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 3), "Actual Case rotation failed");
            require(hardware.facing() == Direction.EAST, "Public Case orientation did not follow its actual block state");
            orientation = 1;
            return hardware.toLocal(Direction.EAST).get3DDataValue();
        }

        void screwdriver() {
            ItemStack screwdriver = foreignItem("projectred_core", "screwdriver");
            require(access.screwdriver.isInstance(screwdriver.getItem()) && screwdriver.isDamageableItem(), "The registered tool is not the real damageable IScrewdriver");
            actor.setItemInHand(InteractionHand.MAIN_HAND, screwdriver);
            actor.setShiftKeyDown(false);
            BlockState before = level.getBlockState(waypointPosition);
            int damage = screwdriver.getDamageValue();
            ItemInteractionResult result = before.useItemOn(screwdriver, level, actor, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(waypointPosition), Direction.NORTH, waypointPosition, false));
            require(result == (integrationEnabled ? ItemInteractionResult.sidedSuccess(false) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION),
                "Actual Waypoint did not recognize/refuse the screwdriver as expected: " + result);
            require(level.getBlockState(waypointPosition).equals(before) && screwdriver.getDamageValue() == damage,
                "Non-consuming Waypoint wrench detection changed state or damaged the actual screwdriver");
            before = level.getBlockState(splitterPosition);
            Map<Direction, Boolean> faces = faces(before);
            result = before.useItemOn(screwdriver, level, actor, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(splitterPosition), Direction.EAST, splitterPosition, false));
            require(result == (integrationEnabled ? ItemInteractionResult.sidedSuccess(false) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION),
                "Actual NetSplitter did not recognize/refuse the screwdriver as expected: " + result);
            Map<Direction, Boolean> after = faces(level.getBlockState(splitterPosition));
            for (Direction side : Direction.values()) require(after.get(side) == (faces.get(side) ^ (integrationEnabled && side == Direction.EAST)),
                "Actual NetSplitter changed the wrong face: " + side);
            require(screwdriver.getDamageValue() == damage + (integrationEnabled ? 1 : 0),
                "Actual screwdriver damage must occur exactly once on real use; use the default ProjectRed unbreakableScrewdriver=false for this proof");
            pass("actual registered screwdriver -> Waypoint non-consuming detection -> NetSplitter east-face use; damage=" + damage + "->" + screwdriver.getDamageValue());
            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }

        static Map<Direction, Boolean> faces(BlockState state) {
            Map<Direction, Boolean> result = new EnumMap<>(Direction.class);
            for (Direction side : Direction.values()) {
                var property = state.getBlock().getStateDefinition().getProperty(side.getName());
                require(property instanceof BooleanProperty, "Real NetSplitter has no " + side.getName() + " boolean state");
                result.put(side, state.getValue((BooleanProperty) property));
            }
            return result;
        }

        void nativeCaller(Context context) { require(context == hardware.machine(), "Probe was invoked outside the actual placed native Case"); }
        void pass(String message) { RESULTS.add("PASS ProjectRed: " + message); LOGGER.info("PROJECTRED_CONTRACT PASS {}", message); }
        void fail(Throwable error) {
            RESULTS.add("FAIL ProjectRed: " + error); LOGGER.error("PROJECTRED_CONTRACT FAIL", error);
            try { dispose(); } catch (Throwable cleanup) { LOGGER.error("PROJECTRED_CONTRACT cleanup failed", cleanup); }
            active = null;
        }

        void dispose() {
            if (hardware != null) { hardware.machine().stop(); hardware.clearContent(); }
            if (probe != null) probe.node().remove();
            for (int index = placed.size() - 1; index >= 0; index--) level.removeBlock(placed.get(index), false);
            placed.clear();
            for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            forced.clear();
        }
    }

    private static int[] unsigned(byte[] signal) {
        if (signal == null) return null;
        int[] values = new int[signal.length];
        for (int color = 0; color < signal.length; color++) values[color] = signal[color] & 0xFF;
        return values;
    }

    private static boolean signal(byte[] signal, int[] expected, boolean connected) {
        if (signal == null) return !connected && zero(expected);
        if (signal.length != 16) return false;
        for (int color = 0; color < 16; color++) if ((signal[color] & 0xFF) != expected[color] || signal[color] != (byte) expected[color]) return false;
        return true;
    }

    private static boolean zero(byte[] signal) { return signal == null || zero(unsigned(signal)); }
    private static boolean zero(int[] signal) { return signal != null && Arrays.stream(signal).allMatch(value -> value == 0); }

    /** All world assertions run in synchronized callbacks on the real server thread. */
    public static final class Probe extends AbstractManagedEnvironment {
        private final Run run;
        Probe(Run run) {
            this.run = run;
            setNode(Network.newNode(this, Visibility.Network).withComponent("projectred_probe", Visibility.Network)
                .withConnector(10_000_000).create());
        }

        @Callback public Object[] metadata(Context context, Arguments args) {
            run.nativeCaller(context);
            return new Object[]{Map.of("enabled", run.integrationEnabled, "card", run.card.address(), "io", run.io.address(),
                "caseSide", run.side(run.card, Direction.EAST), "ioSide", Direction.WEST.get3DDataValue())};
        }

        @Callback public Object[] prove(Context context, Arguments args) throws Exception {
            run.nativeCaller(context);
            return new Object[]{run.prove(args.checkString(0), args.checkString(1), args.checkInteger(2))};
        }

        @Callback public Object[] rotate(Context context, Arguments args) {
            run.nativeCaller(context);
            return new Object[]{run.rotate()};
        }

        @Callback public Object[] report(Context context, Arguments args) {
            run.nativeCaller(context);
            require("native53-complete".equals(args.checkString(0)), "Unexpected native ProjectRed proof marker");
            run.completed.set(true);
            return new Object[]{true};
        }
    }

    private static final class Access {
        final Object transmission;
        final Class<?> screwdriver;
        final Method input, contains, mask;

        Access() throws ReflectiveOperationException {
            ClassLoader loader = ProjectRedContract.class.getClassLoader();
            Class<?> api = Class.forName("mrtjp.projectred.api.ProjectRedAPI", false, loader);
            Class<?> type = Class.forName("mrtjp.projectred.api.ITransmissionAPI", false, loader);
            screwdriver = Class.forName("mrtjp.projectred.api.IScrewdriver", false, loader);
            transmission = api.getField("transmissionAPI").get(null);
            require(type.isInstance(transmission), "Official ProjectRed transmissionAPI is unavailable");
            input = type.getMethod("getBundledInput", Level.class, BlockPos.class, Direction.class);
            contains = type.getMethod("containsFramedWire", Level.class, BlockPos.class);
            mask = type.getMethod("getFramedWireConnectionMask", Level.class, BlockPos.class);
        }

        byte[] input(Level level, BlockPos position, Direction side) { return (byte[]) call(input, transmission, level, position, side); }
        boolean containsWire(Level level, BlockPos position) { return Boolean.TRUE.equals(call(contains, transmission, level, position)); }
        int mask(Level level, BlockPos position) { return ((Number) call(mask, transmission, level, position)).intValue(); }

        static Object call(Method method, Object target, Object... arguments) {
            try { return method.invoke(target, arguments); }
            catch (IllegalAccessException error) { throw new IllegalStateException("Official public ProjectRed API is inaccessible", error); }
            catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error fatal) throw fatal;
                throw new IllegalStateException("Official public ProjectRed API call failed", cause);
            }
        }
    }

    private static final String BIOS = """
        assert(computer.getArchitecture() == "Lua 5.3", "real native53 required")
        local p = component.proxy(assert(component.list("projectred_probe", true)()))
        local m = p.metadata()
        local c, r = component.proxy(m.card), component.proxy(m.io)
        local cs, rs = m.caseSide, m.ioSide
        local levels = {0, 1, 127, 128, 254, 255}
        local z = {} for color=0,15 do z[color]=0 end
        local function values(phase)
          local v = {} for color=0,15 do v[color]=levels[(color+phase)%6+1] end return v
        end
        local function settled(key, source, phase)
          while not p.prove(key, source, phase) do computer.pullSignal(0.05) end
          for color=0,15 do
            local expected = m.enabled and source ~= "zero" and levels[(color+phase)%6+1] or 0
            if source=="case" then assert(r.getBundledInput(rs,color)==expected, "native I/O input mismatch")
            elseif source=="io" then assert(c.getBundledInput(cs,color)==expected, "native card input mismatch")
            else assert(c.getBundledInput(cs,color)==0 and r.getBundledInput(rs,color)==0, "native zero mismatch") end
          end
        end
        c.setBundledOutput(cs,z) r.setBundledOutput(rs,z)
        settled("initial-zero", "zero", 0)
        for orientation=0,1 do
          for phase=0,5 do
            local v, key = values(phase), tostring(orientation).."/"..tostring(phase)
            c.setBundledOutput(cs,v)
            for color=0,15 do assert(c.getBundledOutput(cs,color)==v[color]) end
            settled(key.."/case", "case", phase)
            c.setBundledOutput(cs,z) settled(key.."/zero-case", "zero", phase)
            r.setBundledOutput(rs,v)
            for color=0,15 do assert(r.getBundledOutput(rs,color)==v[color]) end
            settled(key.."/io", "io", phase)
            r.setBundledOutput(rs,z) settled(key.."/zero-io", "zero", phase)
          end
          if orientation==0 then
            local old=cs cs=p.rotate() assert(cs~=old, "Case rotation did not remap local sides")
          end
        end
        c.setBundledOutput(cs,values(5)) settled("final-live-output", "case", 5)
        assert(p.report("native53-complete"))
        -- Java now stops the Case and checks its real world output-disable path.
        -- Keep the VM alive until then so tmp-node shutdown cannot fake a failure.
        while true do computer.pullSignal(1) end
        """;
}
