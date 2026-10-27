package ocfixture;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Adapter;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actual EnderStorage behavior through a real Adapter and native Lua 5.3 Case.
 * Only published OC SDK, Minecraft/NeoForge and verified public foreign APIs are
 * used. Owner changes below arrange test inputs; they are not OC owner callbacks
 * and do not claim that a player's diamond interaction has been tested.
 */
public final class EnderStorageContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-enderstorage-contract");
    private static final List<String> RESULTS = new CopyOnWriteArrayList<>();
    private static Run active;

    private EnderStorageContract() {}

    public static boolean enabled() { return Boolean.getBoolean("ocfixture.enderStorageOnly"); }
    public static boolean isRunning() { return active != null; }
    public static List<String> results() { return List.copyOf(RESULTS); }

    public static String run(ServerLevel level) throws Exception {
        require(active == null, "An EnderStorage contract is already running");
        require(ModList.get().isLoaded("enderstorage") && ModList.get().isLoaded("codechickenlib"),
            "Install the selected official full EnderStorage and CodeChickenLib runtimes");
        require(API.config != null && API.network != null && API.driver != null, "OC SDK facades are unavailable");
        RESULTS.clear();
        Run run = new Run(level);
        active = run;
        try { run.start(); }
        catch (Exception | Error error) { run.fail(error); throw error; }
        return "Started real EnderStorage Adapter/native53 contract, integration=" + run.integrationEnabled;
    }

    /** The real Case block owns machine updates; no substitute machine is ticked here. */
    public static void tick() {
        Run run = active;
        if (run == null) return;
        try { run.tick(); }
        catch (Throwable error) { run.fail(error); }
    }

    public static void close() {
        if (active != null) active.fail(new AssertionError("Server stopped before the EnderStorage contract completed"));
    }

    private static boolean integrationAllowed() {
        return !API.config.hasPath("integration.modBlacklist") || API.config.getStringList("integration.modBlacklist")
            .stream().noneMatch(id -> id.trim().equalsIgnoreCase("enderstorage"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static ItemStack item(String name) {
        ItemStack stack = Objects.requireNonNull(li.cil.oc.api.Items.get(name), "Missing SDK item " + name).createItemStack(1);
        require(!stack.isEmpty(), "SDK returned an empty " + name);
        return stack;
    }

    private record Owner(Optional<?> uuid, Optional<?> name) {}
    private record Sample(ItemStack marker, int water) {}

    private static final class Run {
        final ServerLevel level;
        final boolean integrationEnabled = integrationAllowed();
        final BlockPos origin = new BlockPos(Integer.getInteger("ocfixture.enderStorageX", 4096),
            Integer.getInteger("ocfixture.enderStorageY", 96), Integer.getInteger("ocfixture.enderStorageZ", 4096));
        final BlockPos adapterPosition = origin.east();
        final BlockPos chestPosition = origin.east(2);
        final BlockPos tankPosition = adapterPosition.south();
        final List<BlockPos> positions = List.of(origin, adapterPosition, chestPosition, tankPosition);
        final List<BlockPos> placed = new ArrayList<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final Map<String, Sample> samples = new LinkedHashMap<>();
        final List<String> disabledWrappers = new ArrayList<>();
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicInteger storageProofs = new AtomicInteger();
        final Access access;
        final String markerToken = UUID.randomUUID().toString();
        Case hardware;
        Adapter adapter;
        BlockEntity chest, tank;
        FakePlayer ownerA, ownerB;
        Owner expectedOwner = new Owner(Optional.empty(), Optional.empty());
        Probe probe;
        String chestAddress, tankAddress;
        int ticks, expectedDisabledWrappers = -1;
        boolean booted;

        Run(ServerLevel level) throws ReflectiveOperationException {
            this.level = level;
            access = new Access();
        }

        void start() {
            for (BlockPos position : positions) {
                long chunk = ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
                if (level.setChunkForced(position.getX() >> 4, position.getZ() >> 4, true)) forced.add(chunk);
                level.getChunkAt(position);
                require(level.isEmptyBlock(position), "EnderStorage fixture needs empty cells; nothing was replaced at " + position);
            }
            place(origin, Objects.requireNonNull(li.cil.oc.api.Items.get("case3"), "Missing real case3").block());
            place(adapterPosition, Objects.requireNonNull(li.cil.oc.api.Items.get("adapter"), "Missing real Adapter").block());
            place(chestPosition, block("enderstorage", "ender_chest"));
            place(tankPosition, block("enderstorage", "ender_tank"));
            require(level.getBlockEntity(origin) instanceof Case, "The placed computer is not a public Case host");
            hardware = (Case) level.getBlockEntity(origin);
            require(level.getBlockEntity(adapterPosition) instanceof Adapter, "The placed Adapter lacks its SDK interface");
            adapter = (Adapter) level.getBlockEntity(adapterPosition);
            chest = Objects.requireNonNull(level.getBlockEntity(chestPosition));
            tank = Objects.requireNonNull(level.getBlockEntity(tankPosition));
            require(access.tileType.isInstance(chest) && access.tankType.isInstance(tank), "Foreign test devices are not real EnderStorage tiles");
            require(hardware.machine() != null, "The real Case did not create a machine");
            verifyOwnerAndFrequency(0);

            ownerA = new FakePlayer(level, new GameProfile(UUID.fromString("115ca4ed-cdc4-4aa6-aa42-1cf51827506a"), "OC_Ender_A"));
            ownerB = new FakePlayer(level, new GameProfile(UUID.fromString("040674f6-885e-47e9-83ac-9131532ee0eb"), "OC_Ender_B"));
            ItemStack cpu = item("cpu3");
            var driver = Driver.driverFor(cpu, hardware.getClass());
            require(driver instanceof MutableProcessor, "The real CPU is not a MutableProcessor");
            Class<? extends Architecture> architecture = li.cil.oc.api.Machine.architectures().stream()
                .filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type))).findFirst()
                .orElseThrow(() -> new AssertionError("The EnderStorage fixture requires native Lua 5.3"));
            ((MutableProcessor) driver).setArchitecture(cpu, architecture);
            install(cpu); install(item("ram6")); install(item("hdd3"));
            ItemStack eeprom = li.cil.oc.api.Items.registerEEPROM("EnderStorage native contract", BIOS.getBytes(StandardCharsets.UTF_8), new byte[0], false);
            require(!eeprom.isEmpty(), "The public factory returned no real EEPROM");
            install(eeprom);
            probe = new Probe(this);
            LOGGER.info("ENDERSTORAGE_CONTRACT started integration={} case={} adapter={} chest={} tank={}",
                integrationEnabled, origin, adapterPosition, chestPosition, tankPosition);
        }

        void place(BlockPos position, Block block) {
            require(block != null && level.setBlock(position, block.defaultBlockState(), 3), "Could not place real fixture block at " + position);
            placed.add(position);
        }

        static Block block(String namespace, String path) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
            Block value = BuiltInRegistries.BLOCK.get(id);
            require(id.equals(BuiltInRegistries.BLOCK.getKey(value)), "Missing real block " + id);
            return value;
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

        boolean ready() {
            if (hardware.machine().node().network() == null || adapter.node() == null || adapter.node().network() == null) return false;
            if (hardware.machine().node().network() != adapter.node().network()) return false;
            boolean eeprom = false;
            chestAddress = null; tankAddress = null;
            for (Node node : adapter.node().network().nodes()) {
                if (!(node instanceof Component component) || !component.canBeSeenFrom(hardware.machine().node())) continue;
                if (integrationEnabled) {
                    if (component.name().equals("ender_chest")) chestAddress = node.address();
                    if (component.name().equals("ender_tank")) tankAddress = node.address();
                }
                if (component.name().equals("eeprom") && hardware.componentSlot(node.address()) >= 0) eeprom = true;
            }
            if (!integrationEnabled) {
                if (!eeprom) return false;
                require(adapter instanceof Analyzable, "The real Adapter does not expose the SDK analyzer contract");
                Node[] analyzed = ((Analyzable) adapter).onAnalyze(ownerA, Direction.UP, .5F, .5F, .5F);
                if (analyzed == null) return false;
                List<String> wrappers = new ArrayList<>();
                for (Node node : analyzed) {
                    if (node instanceof Component component && component.canBeSeenFrom(hardware.machine().node())
                        && node.address() != null) wrappers.add(node.address());
                }
                if (expectedDisabledWrappers < 0) {
                    // Use the Adapter's actual query directions and real SDK driver
                    // selection. Generic inventory/tank adapters default to disabled.
                    var chestDriver = Driver.driverFor(level, chestPosition, Direction.EAST);
                    var tankDriver = Driver.driverFor(level, tankPosition, Direction.SOUTH);
                    boolean inventoryEnabled = API.config.getBoolean("integration.vanilla.enableInventoryDriver");
                    boolean tankEnabled = API.config.getBoolean("integration.vanilla.enableTankDriver");
                    boolean itemCapability = level.getCapability(Capabilities.ItemHandler.BLOCK, chestPosition, Direction.EAST) != null;
                    boolean fluidCapability = level.getCapability(Capabilities.FluidHandler.BLOCK, tankPosition, Direction.SOUTH) != null;
                    require(itemCapability && fluidCapability, "Real EnderStorage capabilities disappeared while compatibility was disabled");
                    require(!inventoryEnabled || chestDriver != null, "Explicitly enabled generic inventory driver did not select the actual EnderChest");
                    require(!tankEnabled || tankDriver != null, "Explicitly enabled generic tank driver did not select the actual EnderTank");
                    expectedDisabledWrappers = (chestDriver == null ? 0 : 1) + (tankDriver == null ? 0 : 1);
                    List<String> evidence = new ArrayList<>();
                    for (Node node : analyzed) evidence.add(node instanceof Component component
                        ? component.name() + " address=" + node.address() + " visibility=" + component.visibility()
                            + " machineVisible=" + component.canBeSeenFrom(hardware.machine().node())
                        : "non-component address=" + node.address());
                    LOGGER.info("ENDERSTORAGE_CONTRACT disabled genericInventory={} genericTank={} itemCap={} fluidCap={} chestDriver={} tankDriver={} expectedWrappers={} analyzed={}",
                        inventoryEnabled, tankEnabled, itemCapability, fluidCapability, chestDriver == null ? "none" : chestDriver.getClass().getName(),
                        tankDriver == null ? "none" : tankDriver.getClass().getName(), expectedDisabledWrappers, evidence);
                }
                // Zero is valid only when actual SDK selection found no drivers;
                // do not invent wrappers to replace deliberately disabled adapters.
                if (wrappers.size() != expectedDisabledWrappers || new LinkedHashSet<>(wrappers).size() != wrappers.size()) return false;
                disabledWrappers.clear();
                disabledWrappers.addAll(wrappers);
                // Disabled metadata carries actual wrappers separately; these two
                // named fields are exclusively used by the unchanged enabled BIOS.
                chestAddress = "";
                tankAddress = "";
                return true;
            }
            return eeprom && chestAddress != null && tankAddress != null;
        }

        void tick() {
            require(++ticks <= 1200, "EnderStorage contract exceeded 1200 ticks");
            if (!booted) {
                require(ticks <= 150, "Real Case/Adapter components did not initialize within 150 ticks");
                if (ticks < 5 || !ready()) return;
                hardware.machine().node().connect(probe.node());
                power();
                require(hardware.machine().start(), "Real native Case did not start: " + hardware.machine().lastError());
                booted = true;
                return;
            }
            power();
            if (!hardware.machine().isRunning()) {
                require(completed.get(), "Native Case stopped before the EnderStorage proof: " + hardware.machine().lastError());
                require(storageProofs.get() >= (integrationEnabled ? 12 : 1), "The BIOS skipped physical storage proofs");
                pass(integrationEnabled
                    ? "native53 -> real Adapter -> five callbacks on chest/tank; packed/three-color/bounds/EnumColour mappings"
                    : "native53 verifies five blacklisted EnderStorage APIs unavailable; real channel and item/fluid contents remain unchanged");
                if (integrationEnabled) pass("real item/fluid capabilities: two public frequencies and two private owners remain isolated, recover their marked contents, and preserve UUID/ownerName after OC retuning");
                dispose(); active = null;
                LOGGER.info("ENDERSTORAGE_CONTRACT_COMPLETE status=PASS integration={}", integrationEnabled);
            }
        }

        void power() { if (probe.node() instanceof Connector connector) connector.changeBuffer(Double.MAX_VALUE); }

        IItemHandler items() {
            return Objects.requireNonNull(level.getCapability(Capabilities.ItemHandler.BLOCK, chestPosition, Direction.WEST),
                "Real EnderChest item capability is unavailable");
        }

        IFluidHandler fluids() {
            return Objects.requireNonNull(level.getCapability(Capabilities.FluidHandler.BLOCK, tankPosition, Direction.NORTH),
                "Real EnderTank fluid capability is unavailable");
        }

        void verifyOwnerAndFrequency(int packed) {
            for (BlockEntity tile : List.of(chest, tank)) {
                Object frequency = access.frequency(tile);
                require(access.packed(frequency) == packed, "Actual foreign frequency differs from the requested packed value " + packed);
                require(expectedOwner.equals(access.owner(frequency)), "OC retuning changed the actual owner UUID or ownerName");
            }
        }

        void empty(int packed) {
            verifyOwnerAndFrequency(packed);
            IItemHandler items = items();
            for (int slot = 0; slot < items.getSlots(); slot++) require(items.getStackInSlot(slot).isEmpty(), "A supposedly isolated EnderChest channel contains another channel's item");
            IFluidHandler fluids = fluids();
            for (int index = 0; index < fluids.getTanks(); index++) require(fluids.getFluidInTank(index).isEmpty(), "A supposedly isolated EnderTank channel contains another channel's fluid");
        }

        void fill(String tag, int packed, int count, int water) {
            require(!samples.containsKey(tag), "A storage sample was inserted twice");
            empty(packed);
            ItemStack marker = new ItemStack(Items.DIAMOND, count);
            marker.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("ocfixture:" + markerToken + ":" + tag));
            require(items().insertItem(0, marker.copy(), false).isEmpty(), "The real EnderChest rejected its marker item");
            require(fluids().fill(new FluidStack(Fluids.WATER, water), IFluidHandler.FluidAction.EXECUTE) == water,
                "The real EnderTank rejected its water marker");
            samples.put(tag, new Sample(marker, water));
            verifyStored(tag, packed);
        }

        void verifyStored(String tag, int packed) {
            verifyOwnerAndFrequency(packed);
            Sample sample = Objects.requireNonNull(samples.get(tag), "Unknown stored sample " + tag);
            // Re-query both capabilities every time: setFreq must invalidate their old bindings.
            IItemHandler items = items();
            ItemStack actual = items.getStackInSlot(0);
            require(actual.getCount() == sample.marker().getCount() && ItemStack.isSameItemSameComponents(actual, sample.marker()),
                "The actual EnderChest marker was lost or another frequency/owner leaked into " + tag);
            for (int slot = 1; slot < items.getSlots(); slot++) require(items.getStackInSlot(slot).isEmpty(), "Unexpected extra EnderChest contents in " + tag);
            IFluidHandler fluids = fluids();
            FluidStack fluid = fluids.getFluidInTank(0);
            require(fluid.getFluid() == Fluids.WATER && fluid.getAmount() == sample.water(),
                "The actual EnderTank water marker was lost or another frequency/owner leaked into " + tag);
            for (int index = 1; index < fluids.getTanks(); index++) require(fluids.getFluidInTank(index).isEmpty(), "Unexpected extra EnderTank contents in " + tag);
            storageProofs.incrementAndGet();
            pass("physical capabilities sample=" + tag + " frequency=" + packed + " owner=" + expectedOwner.uuid());
        }

        void arrangeOwner(String name) {
            require(integrationEnabled, "Owner setup is not part of the blacklisted scenario");
            FakePlayer player = switch (name) {
                case "a" -> ownerA;
                case "b" -> ownerB;
                case "global" -> null;
                default -> throw new IllegalArgumentException("Unknown fixture owner");
            };
            for (BlockEntity tile : List.of(chest, tank)) access.arrangeOwner(tile, player);
            expectedOwner = player == null ? new Owner(Optional.empty(), Optional.empty())
                : new Owner(Optional.of(player.getUUID()), Optional.of(player.getName()));
            for (BlockEntity tile : List.of(chest, tank)) require(expectedOwner.equals(access.owner(access.frequency(tile))), "Official owner test setup failed");
            LOGGER.info("ENDERSTORAGE_CONTRACT owner arrangement uses public Frequency.withOwner/withoutOwner and setFreq: {}", name);
        }

        void nativeCaller(Context context) { require(context == hardware.machine(), "Probe was invoked outside the real placed Case"); }

        void pass(String message) { RESULTS.add("PASS EnderStorage: " + message); LOGGER.info("ENDERSTORAGE_CONTRACT PASS {}", message); }

        void fail(Throwable error) {
            RESULTS.add("FAIL EnderStorage: " + error); LOGGER.error("ENDERSTORAGE_CONTRACT FAIL", error);
            try { dispose(); } catch (Throwable cleanup) { LOGGER.error("ENDERSTORAGE_CONTRACT cleanup failed", cleanup); }
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

    /** Probe operations are synchronized callbacks so all world/capability access is on the server thread. */
    public static final class Probe extends AbstractManagedEnvironment {
        private final Run run;
        Probe(Run run) {
            this.run = run;
            setNode(Network.newNode(this, Visibility.Network).withComponent("enderstorage_probe", Visibility.Network)
                .withConnector(10_000_000).create());
        }

        @Callback
        public Object[] metadata(Context context, Arguments args) {
            run.nativeCaller(context);
            return new Object[]{Map.of("enabled", run.integrationEnabled, "chest", run.chestAddress, "tank", run.tankAddress,
                "colours", run.access.colourNames, "ownerA", run.ownerA.getName().getString(), "ownerB", run.ownerB.getName().getString(),
                "wrappers", run.disabledWrappers.toArray(String[]::new), "expectedWrappers", run.expectedDisabledWrappers)};
        }

        @Callback
        public Object[] fill(Context context, Arguments args) {
            run.nativeCaller(context);
            run.fill(args.checkString(0), args.checkInteger(1), args.checkInteger(2), args.checkInteger(3));
            return new Object[]{true};
        }

        @Callback
        public Object[] expect(Context context, Arguments args) {
            run.nativeCaller(context);
            String sample = args.checkString(0);
            if (sample.equals("empty")) { run.empty(args.checkInteger(1)); run.storageProofs.incrementAndGet(); }
            else run.verifyStored(sample, args.checkInteger(1));
            return new Object[]{true};
        }

        @Callback
        public Object[] ownerSetup(Context context, Arguments args) {
            run.nativeCaller(context);
            run.arrangeOwner(args.checkString(0));
            return new Object[]{true};
        }

        @Callback
        public Object[] report(Context context, Arguments args) {
            run.nativeCaller(context);
            require("native53-complete".equals(args.checkString(0)), "Unexpected EnderStorage native proof marker");
            run.completed.set(true);
            return new Object[]{true};
        }
    }

    /** Every reflected member is public and comes from the official 1.21.1 source. */
    private static final class Access {
        final Class<?> tileType, tankType;
        final Method getFrequency, setFrequency, colours, owner, ownerName, withOwner, withoutOwner;
        final String[] colourNames;

        Access() throws ReflectiveOperationException {
            ClassLoader loader = EnderStorageContract.class.getClassLoader();
            tileType = Class.forName("codechicken.enderstorage.tile.TileFrequencyOwner", false, loader);
            tankType = Class.forName("codechicken.enderstorage.tile.TileEnderTank", false, loader);
            Class<?> frequency = Class.forName("codechicken.enderstorage.api.Frequency", false, loader);
            Class<?> colour = Class.forName("codechicken.lib.colour.EnumColour", false, loader);
            getFrequency = tileType.getMethod("getFrequency");
            setFrequency = tileType.getMethod("setFreq", frequency);
            colours = frequency.getMethod("toArray");
            owner = frequency.getMethod("owner");
            ownerName = frequency.getMethod("ownerName");
            withOwner = frequency.getMethod("withOwner", Player.class);
            withoutOwner = frequency.getMethod("withoutOwner");
            Object[] constants = Objects.requireNonNull(colour.getEnumConstants(), "Official colour type is not an enum");
            require(constants.length == 16, "Official EnumColour does not have the expected sixteen wool colors");
            colourNames = new String[constants.length];
            for (int index = 0; index < constants.length; index++) colourNames[index] = constants[index].toString();
        }

        Object frequency(BlockEntity tile) { return call(getFrequency, tile); }

        int packed(Object frequency) {
            Object[] values = (Object[]) call(colours, frequency);
            return ((Enum<?>) values[0]).ordinal() << 8 | ((Enum<?>) values[1]).ordinal() << 4 | ((Enum<?>) values[2]).ordinal();
        }

        Owner owner(Object frequency) { return new Owner((Optional<?>) call(owner, frequency), (Optional<?>) call(ownerName, frequency)); }

        /** Test setup only: production OC exposes no owner setter. */
        void arrangeOwner(BlockEntity tile, Player player) {
            Object current = frequency(tile);
            Object changed = player == null ? call(withoutOwner, current) : call(withOwner, current, player);
            call(setFrequency, tile, changed);
        }

        static Object call(Method method, Object target, Object... arguments) {
            try { return method.invoke(target, arguments); }
            catch (IllegalAccessException exception) { throw new IllegalStateException("Official public EnderStorage API is inaccessible", exception); }
            catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("Official EnderStorage API call failed", cause);
            }
        }
    }

    private static final String BIOS = """
        assert(computer.getArchitecture() == "Lua 5.3", "EnderStorage must be tested in the real native VM")
        local p = component.proxy(assert(component.list("enderstorage_probe", true)()))
        local info = p.metadata()
        local addresses = {info.chest, info.tank}
        local function frequency(device, left, middle, right)
          local f = device.getFrequency()
          assert(type(f) == "table" and f[1] == left and f[2] == middle and f[3] == right, "wrong frequency triplet")
          local names = device.getFrequencyColors()
          assert(names[1] == info.colours[left+1] and names[2] == info.colours[middle+1]
            and names[3] == info.colours[right+1], "wrong actual EnumColour names")
        end
        if not info.enabled then
          assert(p.fill("blacklisted", 0, 11, 1100))
          local forbidden = {"getFrequency", "setFrequency", "getOwner", "getFrequencyColors", "getColors"}
          assert(#info.wrappers == info.expectedWrappers, "actual Adapter wrapper selection changed")
          for _, address in ipairs(info.wrappers) do
            for _, method in ipairs(forbidden) do
              local ok = pcall(component.invoke, address, method, 4095)
              assert(not ok, "blacklisted EnderStorage method was still callable: " .. method)
            end
          end
          for address in component.list() do
            local methods = component.methods(address)
            for _, method in ipairs(forbidden) do
              assert(methods[method] == nil, "blacklisted EnderStorage API leaked to the real network: " .. method)
            end
          end
          if info.expectedWrappers == 0 then
            assert(not component.list("ender_chest", true)() and not component.list("ender_tank", true)(),
              "driverless EnderStorage device was still exposed to the native computer")
          end
          assert(p.expect("blacklisted", 0))
        else
          for _, address in ipairs(addresses) do
            local d = component.proxy(address)
            assert(d.getOwner() == "global", "new test storage must have a public owner")
            local colours = d.getColors()
            for index=0,15 do assert(colours[index] == info.colours[index+1], "zero-based colour mapping changed") end
            d.setFrequency(0) frequency(d, 0, 0, 0)
            d.setFrequency(4095) frequency(d, 15, 15, 15)
            d.setFrequency(2, 3, 4) frequency(d, 2, 3, 4)
            for _, args in ipairs({{-1}, {4096}, {-1,0,0}, {0,16,0}, {0,0,16}}) do
              local ok, reason = pcall(d.setFrequency, table.unpack(args))
              assert(not ok and tostring(reason):find("invalid frequency", 1, true), "invalid frequency was accepted")
              frequency(d, 2, 3, 4)
            end
          end
          local chest, tank = component.proxy(info.chest), component.proxy(info.tank)
          local function retune(packed) chest.setFrequency(packed) tank.setFrequency(packed) end
          local function owner(name)
            assert(chest.getOwner() == name and tank.getOwner() == name, "stored owner name was changed")
          end
          retune(0) assert(p.fill("public-zero", 0, 13, 1250))
          retune(0x123) assert(p.expect("empty", 0x123)) assert(p.fill("public-other", 0x123, 7, 750))
          retune(0) assert(p.expect("public-zero", 0))
          retune(0x123) assert(p.expect("public-other", 0x123))
          assert(p.ownerSetup("a")) owner(info.ownerA) assert(p.expect("empty", 0x123))
          assert(p.fill("owner-a", 0x123, 17, 1750))
          retune(0x456) owner(info.ownerA) assert(p.expect("empty", 0x456))
          retune(0x123) owner(info.ownerA) assert(p.expect("owner-a", 0x123))
          assert(p.ownerSetup("b")) owner(info.ownerB) assert(p.expect("empty", 0x123))
          assert(p.fill("owner-b", 0x123, 19, 2750))
          retune(0x456) owner(info.ownerB) assert(p.expect("empty", 0x456))
          retune(0x123) owner(info.ownerB) assert(p.expect("owner-b", 0x123))
          assert(p.ownerSetup("a")) owner(info.ownerA) assert(p.expect("owner-a", 0x123))
          assert(p.ownerSetup("global")) owner("global") assert(p.expect("public-other", 0x123))
        end
        assert(p.report("native53-complete"))
        computer.shutdown()
        """;
}
