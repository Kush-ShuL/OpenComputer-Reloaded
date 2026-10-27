package ocfixture;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A real registered item stores FE in its item components. A normal OC Charger
 * slot and world ticker perform the transfer; expected results never write the
 * cell's energy. Only the published OC SDK is linked. The new public production
 * item-charge helper is reflected for its otherwise inaccessible simulate path.
 */
public final class ForgeEnergyContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-forge-energy-contract");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "oc_api_compat");
    private static final Supplier<Item> CELL = ITEMS.register("forge_energy_cell", () -> new Item(new Item.Properties().stacksTo(1)));
    private static final String ENERGY = "ocfixture:fe", LIMIT = "ocfixture:fe_receive_limit", EXTRACT = "ocfixture:fe_extract_only";
    private static Run active;

    private ForgeEnergyContract() {}

    /** Always register this content, independently of the selected run branch. */
    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener((RegisterCapabilitiesEvent event) -> event.registerItem(Capabilities.EnergyStorage.ITEM,
            (stack, context) -> new CellStorage(stack), CELL.get()));
    }

    public static void start(ServerLevel level, BlockPos position, List<String> results) throws Exception {
        require(active == null, "A Forge Energy contract is already running");
        Run run = new Run(level, position.immutable(), results);
        active = run;
        try { run.start(); }
        catch (Exception | Error error) { run.fail(error); throw error; }
    }

    public static boolean isRunning() { return active != null; }

    public static void tick() {
        Run run = active;
        if (run == null) return;
        try {
            if (!run.tick()) return;
            active = null;
            run.cleanup();
        } catch (Throwable error) { run.fail(error); }
    }

    public static void close() {
        if (active != null) active.fail(new AssertionError("Server stopped before the FE contract completed"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** Legitimate initial item configuration, before any operation under test. */
    private static ItemStack cell(int energy, int limit, boolean extractOnly) {
        require(energy >= 0 && limit >= 0, "Invalid initial FE cell configuration");
        ItemStack stack = new ItemStack(CELL.get());
        CompoundTag tag = new CompoundTag();
        tag.putInt(ENERGY, energy); tag.putInt(LIMIT, limit); tag.putBoolean(EXTRACT, extractOnly);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static IEnergyStorage storage(ItemStack stack) {
        IEnergyStorage result = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        require(result != null, "The actual registered FE cell lost its capability");
        return result;
    }

    private static void closeTo(double actual, double expected, double scale, String message) {
        double tolerance = Math.max(1e-9, Math.ulp(Math.abs(scale)) * 8);
        require(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
            message + ": actual=" + actual + ", expected=" + expected);
    }

    private static boolean listed(String path, String... names) {
        if (!API.config.hasPath(path)) return false;
        for (String entry : API.config.getStringList(path)) for (String name : names)
            if (entry.trim().equalsIgnoreCase(name)) return true;
        return false;
    }

    private static final class Run {
        final ServerLevel level;
        final BlockPos position;
        final List<String> results;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final Access access;
        final double ratio;
        final boolean allowed;
        final int period;
        Container charger;
        Case contextCase;
        ManagedEnvironment driverEnvironment;
        Connector connector;
        ItemStack charging;
        int ticks, chargedAt = -1;
        double initialOC;

        Run(ServerLevel level, BlockPos position, List<String> results) throws ReflectiveOperationException {
            require(API.config != null && API.network != null, "OC configuration/network SDK is unavailable");
            this.level = level; this.position = position; this.results = results;
            String value = API.config.hasPath("power.value.ForgeEnergy") ? "power.value.ForgeEnergy" : "power.value.RedstoneFlux";
            ratio = API.config.getDouble(value) / 1000;
            require(ratio > 0 && Double.isFinite(ratio), "This actual-energy proof requires a finite positive configured OC/FE ratio");
            allowed = !listed("integration.modBlacklist", "forge", "neoforge")
                && !listed("power.modBlacklist", "forge", "neoforge", "forgeenergy", "neoforgeenergy");
            period = (int) Math.ceil(Math.max(1, API.config.getDouble("power.tickFrequency")));
            require(period <= 100, "The bounded FE fixture requires power.tickFrequency <= 100");
            access = new Access();
        }

        void start() {
            require(API.config.getDouble("power.chargerChargeRateTablet") * period >= ratio * 3,
                "Configured real charger rate is too small for the three-FE receive-limit proof");
            direct();
            var descriptor = li.cil.oc.api.Items.get("charger");
            require(descriptor != null && descriptor.block() != null, "The SDK has no actual Charger block");
            place(position, descriptor.block().defaultBlockState());
            place(position.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            var computer = li.cil.oc.api.Items.get("case1");
            require(computer != null && computer.block() != null, "The SDK has no real Case for driver callback context");
            place(position.north(2), computer.block().defaultBlockState());
            require(level.getBlockEntity(position.north(2)) instanceof Case, "Actual callback-context computer lacks the Case SDK");
            contextCase = (Case) level.getBlockEntity(position.north(2));
            require(level.getBlockEntity(position) instanceof Container && level.getBlockEntity(position) instanceof Environment,
                "Actual Charger lacks its public inventory/environment contracts");
            charger = (Container) level.getBlockEntity(position);
            charging = cell(0, 3, false);
            require(charger.canPlaceItem(0, charging) == allowed, "Real Charger slot did not honor generic FE registration/blacklist");
            require(access.canCharge(charging) == allowed, "Public FE helper detection disagrees with configuration");
            if (allowed) {
                charger.setItem(0, charging);
                charging = charger.getItem(0); // Container insertion may copy the actual stack.
                require(charging.is(CELL.get()) && storage(charging).getEnergyStored() == 0, "Actual Charger changed the inserted FE cell");
            }
            IEnergyStorage exposed = level.getCapability(Capabilities.EnergyStorage.BLOCK, position, Direction.UP);
            require((exposed != null) == allowed, "OC block FE capability did not honor the same integration/power gate");
            if (exposed != null) require(exposed.canReceive() && !exposed.canExtract(), "Real Charger exposes the wrong FE direction flags");
            LOGGER.info("FORGE_ENERGY_CONTRACT started allowed={} ratio={} period={} charger={}", allowed, ratio, period, position);
        }

        void place(BlockPos pos, BlockState state) {
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true))
                forced.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            level.getChunkAt(pos);
            require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "FE fixture will not replace occupied cell " + pos);
            restored.put(pos, level.getBlockState(pos));
            require(level.setBlock(pos, state, 3), "Could not place the real FE fixture device at " + pos);
        }

        void direct() {
            ItemStack sample = cell(0, 3, false);
            double offer = 10.5 * ratio;
            require(Double.isFinite(offer), "Configured ratio cannot represent the bounded FE offer");
            require(access.canCharge(sample) == allowed, "Real FE-cell detection does not follow the selected gate");
            ItemStack before = sample.copy();
            double remainder = access.charge(sample, offer, true);
            closeTo(remainder, offer - (allowed ? 3 * ratio : 0), offer, "Simulated FE remainder");
            require(ItemStack.isSameItemSameComponents(before, sample), "Simulation wrote actual item components");
            remainder = access.charge(sample, offer, false);
            require(storage(sample).getEnergyStored() == (allowed ? 3 : 0), "Actual FE helper ignored the real receive limit");
            closeTo(remainder, offer - (allowed ? 3 * ratio : 0), offer, "Actual partial FE remainder");

            ItemStack fractional = cell(0, Integer.MAX_VALUE, false);
            double subUnit = Math.nextDown(ratio);
            before = fractional.copy();
            closeTo(access.charge(fractional, subUnit, false), subUnit, subUnit, "Sub-FE energy must remain unapplied");
            require(ItemStack.isSameItemSameComponents(before, fractional), "A fractional FE offer changed the actual cell");
            // No rounding to a nearest integer: 2.5 FE can transfer exactly two.
            offer = 2.5 * ratio;
            remainder = access.charge(fractional, offer, false);
            require(storage(fractional).getEnergyStored() == (allowed ? 2 : 0), "Fractional FE was rounded up or discarded");
            closeTo(remainder, offer - (allowed ? 2 * ratio : 0), offer, "Integer FE truncation remainder");

            ItemStack capacity = cell(Integer.MAX_VALUE - 2, 17, false);
            offer = 31 * ratio;
            remainder = access.charge(capacity, offer, false);
            require(storage(capacity).getEnergyStored() == Integer.MAX_VALUE - (allowed ? 0 : 2), "Capacity-limited FE transfer changed the wrong amount");
            closeTo(remainder, offer - (allowed ? 2 * ratio : 0), offer, "Capacity-limited FE remainder");

            ItemStack large = cell(0, Integer.MAX_VALUE, false);
            offer = (Integer.MAX_VALUE + 7d) * ratio;
            require(Double.isFinite(offer), "Configured ratio cannot represent the integer-boundary FE offer");
            remainder = access.charge(large, offer, false);
            require(storage(large).getEnergyStored() == (allowed ? Integer.MAX_VALUE : 0), "Oversized FE offer wrapped or exceeded the actual int capacity");
            closeTo(remainder, allowed ? 7 * ratio : offer, offer, "Int.MAX_VALUE FE remainder");

            for (ItemStack rejected : List.of(new ItemStack(Items.STONE), cell(13, 17, true))) {
                require(!access.canCharge(rejected), "No-capability or extraction-only item was detected as chargeable");
                before = rejected.copy();
                offer = 9 * ratio;
                closeTo(access.charge(rejected, offer, false), offer, offer, "Rejected item consumed OC energy");
                require(ItemStack.isSameItemSameComponents(before, rejected), "Rejected item components were modified");
            }
            for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
                before = sample.copy();
                double returned = access.charge(sample, invalid, false);
                require(Double.isNaN(invalid) ? Double.isNaN(returned) : returned == invalid, "Invalid offer was not returned unchanged");
                require(ItemStack.isSameItemSameComponents(before, sample), "Invalid offer modified an actual FE cell");
            }
            pass("public production hook with real component-backed FE cell: simulate preserves components, actual receive/capacity limits, fractional/int-max remainders and absent/extract-only rejection; allowed=" + allowed + " ratio=" + ratio);
        }

        boolean tick() throws Exception {
            require(++ticks <= period * 5 + 100, "Actual Charger did not complete the bounded FE transfer");
            if (chargedAt < 0) {
                if (ticks < 3) return false;
                Node node = ((Environment) level.getBlockEntity(position)).node();
                if (!(node instanceof Connector value) || node.network() == null) return false;
                connector = value;
                for (Node other : connector.network().nodes()) require(other == connector, "FE fixture Charger unexpectedly shares another active device's energy network");
                require(storage(charging).getEnergyStored() == 0, "The FE cell changed before the real power arrangement");
                connector.changeBuffer(Double.MAX_VALUE);
                initialOC = connector.globalBuffer();
                require(initialOC >= API.config.getDouble("power.chargerChargeRateTablet") * period,
                    "Actual Charger buffer cannot fund one configured interval");
                chargedAt = ticks;
                return false;
            }
            int actualFE = storage(charging).getEnergyStored();
            double actualOC = connector.globalBuffer();
            if (!allowed) {
                require(actualFE == 0 && !charger.canPlaceItem(0, charging) && !access.canCharge(charging), "Blacklisted real FE charging became active");
                closeTo(actualOC, initialOC, initialOC, "Blacklisted empty Charger lost internal OC energy");
                if (ticks - chargedAt < period * 2 + 3) return false;
                driverReadback();
                pass("actual Charger slot refuses FE item, OC block FE capability is absent and two world-tick intervals preserve item/internal energy under configured blacklist");
                return true;
            }
            if (actualFE == 0) return false;
            require(actualFE == 3, "Actual Charger bypassed the FE cell's three-unit receive limit");
            require(actualOC < initialOC, "Actual FE charging created energy without debiting OC's real connector");
            closeTo(initialOC - actualOC, actualFE * ratio, initialOC, "Actual world-tick Charger OC/FE conservation");
            driverReadback();
            pass("actual Charger Container slot + normal world ticker: FE 0->" + actualFE + ", OC " + initialOC + "->" + actualOC + ", configured OC/FE=" + ratio);
            results.add("LIMIT ForgeEnergy: remote multiplayer and third-party item-specific behavior are not exercised by this minimal charging scenario");
            return true;
        }

        void driverReadback() throws Exception {
            var driver = Driver.driverFor(level, position, Direction.UP);
            driverEnvironment = driver == null ? null : driver.createEnvironment(level, position, Direction.UP);
            Node node = driverEnvironment == null ? null : driverEnvironment.node();
            List<String> names = List.of("getEnergyStored", "getMaxEnergyStored", "canReceive", "canExtract");
            if (!allowed) {
                require(!(node instanceof Component component) || names.stream().noneMatch(component.methods()::contains),
                    "Blacklisted capability still selected callable FE driver methods");
                pass("actual SDK block-driver selection exposes no FE methods when its real Charger capability is disabled");
                return;
            }
            require(node instanceof Component, "Actual registered FE block driver did not create a component");
            Component component = (Component) node;
            require(component.methods().containsAll(names), "Actual FE block driver lacks the original four callbacks");
            require(contextCase.machine() != null, "Actual CPU-less Case did not create its SDK machine context");
            node.connect(contextCase.machine().node());
            IEnergyStorage actual = level.getCapability(Capabilities.EnergyStorage.BLOCK, position, Direction.UP);
            require(actual != null, "Real FE capability disappeared during driver verification");
            require(((Number) component.invoke("getEnergyStored", contextCase.machine())[0]).intValue() == actual.getEnergyStored(),
                "Actual energy_device.getEnergyStored differs from the real FE storage");
            require(((Number) component.invoke("getMaxEnergyStored", contextCase.machine())[0]).intValue() == actual.getMaxEnergyStored(),
                "Actual energy_device.getMaxEnergyStored differs from the real FE capacity");
            require(Boolean.valueOf(actual.canReceive()).equals(component.invoke("canReceive", contextCase.machine())[0]),
                "Actual energy_device.canReceive differs from the real FE capability");
            require(Boolean.valueOf(actual.canExtract()).equals(component.invoke("canExtract", contextCase.machine())[0]),
                "Actual energy_device.canExtract differs from the real FE capability");
            pass("SDK-selected registered energy_device -> four actual callbacks with real Case.machine context equal the Charger's real sided FE storage");
        }

        void pass(String message) { results.add("PASS ForgeEnergy: " + message); LOGGER.info("FORGE_ENERGY_CONTRACT PASS {}", message); }

        void fail(Throwable error) {
            active = null;
            results.add("FAIL ForgeEnergy: " + error);
            LOGGER.error("FORGE_ENERGY_CONTRACT FAIL", error);
            try { cleanup(); } catch (Throwable cleanup) { LOGGER.error("FORGE_ENERGY_CONTRACT cleanup failed", cleanup); }
        }

        void cleanup() {
            if (driverEnvironment != null && driverEnvironment.node() != null) driverEnvironment.node().remove();
            if (charger != null) charger.clearContent();
            var entries = new ArrayList<>(restored.entrySet());
            Collections.reverse(entries);
            for (var entry : entries) level.setBlock(entry.getKey(), entry.getValue(), 3);
            restored.clear();
            for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            forced.clear();
        }
    }

    /** Standard capability: all transfer results derive from this actual ItemStack. */
    private static final class CellStorage implements IEnergyStorage {
        private final ItemStack stack;
        CellStorage(ItemStack stack) { this.stack = stack; }
        private int limit() { return Math.max(0, data(stack).getInt(LIMIT)); }
        private void store(int energy) { CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(ENERGY, energy)); }
        @Override public int receiveEnergy(int offered, boolean simulate) {
            if (offered <= 0 || !canReceive()) return 0;
            int stored = getEnergyStored();
            int accepted = Math.min(offered, Math.min(limit(), Integer.MAX_VALUE - stored));
            if (!simulate && accepted > 0) store(stored + accepted);
            return accepted;
        }
        @Override public int extractEnergy(int offered, boolean simulate) {
            if (offered <= 0 || !canExtract()) return 0;
            int stored = getEnergyStored();
            int extracted = Math.min(offered, Math.min(limit(), stored));
            if (!simulate && extracted > 0) store(stored - extracted);
            return extracted;
        }
        @Override public int getEnergyStored() { return Math.max(0, data(stack).getInt(ENERGY)); }
        @Override public int getMaxEnergyStored() { return Integer.MAX_VALUE; }
        @Override public boolean canExtract() { return data(stack).getBoolean(EXTRACT); }
        @Override public boolean canReceive() { return !canExtract(); }
    }

    /** Only the newly exposed public charge entry points are reflected. */
    private static final class Access {
        final Method canCharge, charge;
        Access() throws ReflectiveOperationException {
            Class<?> type = Class.forName("li.cil.oc.common.integration.forge.ForgeEnergyItemCharge", false, ForgeEnergyContract.class.getClassLoader());
            canCharge = type.getMethod("canCharge", ItemStack.class);
            charge = type.getMethod("charge", ItemStack.class, double.class, boolean.class);
        }
        boolean canCharge(ItemStack stack) { return Boolean.TRUE.equals(call(canCharge, stack)); }
        double charge(ItemStack stack, double amount, boolean simulate) { return ((Number) call(charge, stack, amount, simulate)).doubleValue(); }
        static Object call(Method method, Object... arguments) {
            try { return method.invoke(null, arguments); }
            catch (IllegalAccessException error) { throw new IllegalStateException("Public FE charge hook is inaccessible", error); }
            catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error fatal) throw fatal;
                throw new IllegalStateException("Public FE charge hook failed", cause);
            }
        }
    }
}
