package ocfixture;

import java.nio.charset.StandardCharsets;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.Converter;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Memory;
import li.cil.oc.api.driver.item.Processor;
import li.cil.oc.api.fs.Handle;
import li.cil.oc.api.fs.Label;
import li.cil.oc.api.fs.Mode;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.machine.MachineHost;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.prefab.AbstractValue;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** An external-addon contract exercise: imports only published OC API and game APIs. */
public final class RuntimeContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-api-fixture-runtime");
    private static final String CPU_TAG = "oc_fixture_runtime_cpu";
    private static final List<Run> RUNS = new CopyOnWriteArrayList<>();
    private static final List<String> RESULTS = new CopyOnWriteArrayList<>();
    private static boolean registered;

    private RuntimeContract() {}

    /** Call once in common setup, before OpenComputers locks driver registration. */
    public static synchronized void register() {
        if (registered) return;
        Driver.add(new FixtureProcessor());
        Driver.add((Converter) (value, output) -> {
            if (value instanceof LegacyPayload payload) output.put("legacy", payload.text());
        });
        Driver.add(new Converter() {
            @Override public void convert(Object value, Map<Object, Object> output) {
                convert(value, output, null);
            }
            @Override public void convert(Object value, Map<Object, Object> output, HolderLookup.Provider registries) {
                if (value instanceof Payload payload) {
                    int count = payload.conversions.incrementAndGet();
                    if (count != 1) throw new AssertionError("A result crossed more than one conversion boundary");
                    output.put("kind", "fixture");
                    output.put("path", payload.path);
                    output.put("lookup", registries == payload.expectedLookup);
                    output.put("hasLookup", registries != null);
                    output.put("nested", List.of(new LegacyPayload("legacy-sam")));
                }
            }
        });
        registered = true;
    }

    /** Starts in-memory, disconnected fixture hosts; no block, disk or player data is touched. */
    public static String run(ServerLevel level) throws Exception {
        if (!registered) throw new IllegalStateException("RuntimeContract.register must run in common setup");
        if (!RUNS.isEmpty()) throw new IllegalStateException("The runtime contract is already running");
        RESULTS.clear();
        verifyPersistence(level.registryAccess());
        verifyPrivateCallbackCarrier();
        Class<? extends Architecture> selected = li.cil.oc.api.Machine.LuaArchitecture;
        if (selected == null) throw new AssertionError("The built-in Lua architecture was not published");
        List<Class<? extends Architecture>> architectures = new ArrayList<>();
        architectures.add(selected);
        for (Class<? extends Architecture> architecture : li.cil.oc.api.Machine.architectures()) {
            if ("LuaJ".equals(li.cil.oc.api.Machine.getArchitectureName(architecture)) && !architectures.contains(architecture))
                architectures.add(architecture);
        }
        for (Class<? extends Architecture> architecture : architectures) {
            Run run = new Run(level, architecture);
            RUNS.add(run);
            try { run.start(); }
            catch (Throwable error) {
                RUNS.remove(run);
                run.dispose();
                throw error;
            }
        }
        return "Started API runtime contracts: " + architectures.stream().map(li.cil.oc.api.Machine::getArchitectureName).toList();
    }

    /** Call on each server tick. The fixture host's dirty callback only sets an atomic flag. */
    public static void tick() {
        for (Run run : RUNS) {
            try {
                run.machine.update();
                if (++run.ticks > 400) throw new AssertionError("Lua bridge did not finish within 400 server ticks");
                if (!run.machine.isRunning()) {
                    if (!run.completed.get()) throw new AssertionError("Lua contract stopped: " + run.machine.lastError());
                    run.pass("Lua kernel component, Value.apply/call/invoke, registry context and IMC program mapping");
                    RUNS.remove(run);
                    run.dispose();
                }
            } catch (Throwable error) {
                RESULTS.add("FAIL " + run.name + ": " + error);
                LOGGER.error("API_RUNTIME_CONTRACT FAIL {}", run.name, error);
                RUNS.remove(run);
                run.dispose();
            }
        }
    }

    public static List<String> results() { return List.copyOf(RESULTS); }
    public static boolean isRunning() { return !RUNS.isEmpty(); }
    public static void close() { for (Run run : RUNS) run.dispose(); RUNS.clear(); }

    private static void verifyPersistence(HolderLookup.Provider registries) {
        RegistryFileSystem backing = new RegistryFileSystem(registries);
        RegistryLabel label = new RegistryLabel(registries);
        ManagedEnvironment environment = li.cil.oc.api.FileSystem.asManagedEnvironment(
            li.cil.oc.api.FileSystem.asReadOnly(backing), label);
        require(environment != null, "The managed filesystem facade was not initialized");
        Network.joinNewNetwork(environment.node());
        try {
            CompoundTag saved = new CompoundTag();
            environment.save(saved, registries);
            require(backing.saved && label.saved, "The managed filesystem did not save its nested addon objects");
            environment.load(saved, registries);
            require(backing.loaded && label.loaded, "The managed filesystem did not restore its nested addon objects");
            RESULTS.add("PASS persistence: managed filesystem -> read-only wrapper -> addon filesystem and label retain identical Provider");
            LOGGER.info("API_RUNTIME_CONTRACT PASS registry-aware filesystem and label persistence");
        } finally { environment.node().remove(); }
    }

    private static void verifyPrivateCallbackCarrier() {
        HiddenComponent environment = new HiddenComponent();
        Network.joinNewNetwork(environment.node());
        try {
            Object[] result = ((Component) environment.node()).invoke("ping", new PlainContext(environment.node()));
            require(result.length == 1 && "pong".equals(result[0]), "A private addon class's public callback returned the wrong value");
            RESULTS.add("PASS callbacks: a private addon environment exposes a public @Callback");
            LOGGER.info("API_RUNTIME_CONTRACT PASS private addon callback carrier");
        } catch (Throwable error) {
            RESULTS.add("FAIL callbacks: private addon environment public @Callback: " + error);
            LOGGER.error("API_RUNTIME_CONTRACT FAIL private addon callback carrier", error);
        } finally { environment.node().remove(); }
    }

    private static final class Run implements MachineHost {
        final ServerLevel level;
        final Class<? extends Architecture> architecture;
        final String name;
        final AtomicBoolean dirty = new AtomicBoolean();
        final AtomicBoolean completed = new AtomicBoolean();
        final ItemStack processor = new ItemStack(Items.STICK);
        final Machine machine;
        final FixtureComponent component;
        final FixtureEeprom eeprom;
        int ticks;

        Run(ServerLevel level, Class<? extends Architecture> architecture) {
            this.level = level;
            this.architecture = architecture;
            name = li.cil.oc.api.Machine.getArchitectureName(architecture);
            CompoundTag marker = new CompoundTag();
            marker.putString(CPU_TAG, architecture.getName());
            processor.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
            machine = li.cil.oc.api.Machine.create(this);
            if (machine == null) throw new AssertionError("Machine.create returned null after common setup");
            component = new FixtureComponent(this);
            eeprom = new FixtureEeprom();
        }

        void start() throws Exception {
            Network.joinNewNetwork(machine.node());
            machine.node().connect(component.node());
            machine.node().connect(eeprom.node());
            machine.setCostPerTick(0);
            ((Connector) machine.node()).changeBuffer(Double.MAX_VALUE);

            Map<?, ?> converted = (Map<?, ?>) ((Component) component.node()).invoke("payload", machine, "java-component")[0];
            require(Boolean.TRUE.equals(converted.get("lookup")), "Component.invoke lost the invoking level lookup");
            require(Boolean.TRUE.equals(converted.get("hasLookup")), "Component.invoke supplied no registry context");
            require("java-component".equals(converted.get("path")), "Component.invoke did not apply the addon converter");
            pass("Component.invoke custom converter + registry lookup");

            Context context = new PlainContext(machine.node());
            converted = (Map<?, ?>) ((Component) component.node()).invoke("payload", context, "plain-context")[0];
            require(Boolean.TRUE.equals(converted.get("lookup")), "A non-machine context used a global registry lookup");
            require(Boolean.FALSE.equals(converted.get("hasLookup")), "A non-machine context inherited registry context");
            pass("Non-machine Context retains explicit null registry context");

            require(machine.start(), "The API fixture machine did not start: " + machine.lastError());
        }

        void pass(String message) {
            RESULTS.add("PASS " + name + ": " + message);
            LOGGER.info("API_RUNTIME_CONTRACT PASS {}: {}", name, message);
        }

        void dispose() {
            machine.stop();
            machine.node().remove();
            component.node().remove();
            eeprom.node().remove();
        }

        @Override public Machine machine() { return machine; }
        @Override public Iterable<ItemStack> internalComponents() { return List.of(processor); }
        @Override public int componentSlot(String address) { return -1; }
        @Override public void onMachineConnect(Node node) {}
        @Override public void onMachineDisconnect(Node node) {}
        @Override public Level world() { return level; }
        @Override public double xPosition() { return .5; }
        @Override public double yPosition() { return level.getMinBuildHeight() + .5; }
        @Override public double zPosition() { return .5; }
        @Override public void markChanged() { dirty.set(true); }
    }

    /** Public callback carrier, as real addons commonly implement it. */
    public static final class FixtureComponent extends AbstractManagedEnvironment {
        private final Run run;
        FixtureComponent(Run run) {
            this.run = run;
            setNode(Network.newNode(this, Visibility.Network).withComponent("api_fixture", Visibility.Network).create());
        }
        @Callback(direct = true, limit = 64)
        public Object[] payload(Context context, Arguments arguments) {
            HolderLookup.Provider lookup = context instanceof Machine machine ? machine.host().world().registryAccess() : null;
            return new Object[] {new Payload(arguments.checkString(0), lookup)};
        }
        @Callback(direct = true, limit = 64)
        public Object[] value(Context context, Arguments arguments) {
            return new Object[] {new ContractValue()};
        }
        @Callback(direct = true, limit = 64)
        public Object[] report(Context context, Arguments arguments) {
            require("done".equals(arguments.checkString(0)), "Unexpected Lua report");
            run.completed.set(true);
            return new Object[] {true};
        }
    }

    /** Default constructor and API callbacks exercise the addon Value contract. */
    public static final class ContractValue extends AbstractValue {
        public ContractValue() {}
        private Payload result(Context context, String path) {
            return new Payload(path, ((Machine) context).host().world().registryAccess());
        }
        @Override public Object apply(Context context, Arguments arguments) {
            require(arguments.checkInteger(0) == 41, "Value.apply arguments were changed");
            return result(context, "apply");
        }
        @Override public Object[] call(Context context, Arguments arguments) {
            require("argument".equals(arguments.checkString(0)), "Value.call arguments were changed");
            return new Object[] {result(context, "call"), 42};
        }
        @Callback(direct = true, limit = 64)
        public Object[] fetch(Context context, Arguments arguments) {
            return new Object[] {result(context, "invoke")};
        }
    }

    public static final class FixtureEeprom extends AbstractManagedEnvironment {
        FixtureEeprom() { setNode(Network.newNode(this, Visibility.Network).withComponent("eeprom", Visibility.Network).create()); }
        @Callback(direct = true, limit = 64)
        public Object[] get(Context context, Arguments arguments) { return new Object[] {BIOS.getBytes(StandardCharsets.UTF_8)}; }
    }

    private static final class HiddenComponent extends AbstractManagedEnvironment {
        HiddenComponent() { setNode(Network.newNode(this, Visibility.Network).withComponent("api_hidden", Visibility.Network).create()); }
        @Callback(direct = true)
        public Object[] ping(Context context, Arguments arguments) { return new Object[] {"pong"}; }
    }

    private record LegacyPayload(String text) {}
    private static final class Payload {
        final String path;
        final HolderLookup.Provider expectedLookup;
        final AtomicInteger conversions = new AtomicInteger();
        Payload(String path, HolderLookup.Provider expectedLookup) { this.path = path; this.expectedLookup = expectedLookup; }
    }

    private static final class FixtureProcessor implements Processor, Memory, HostAware {
        @Override public boolean worksWith(ItemStack stack) {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            return stack.is(Items.STICK) && data != null && data.copyTag().contains(CPU_TAG);
        }
        @Override public boolean worksWith(ItemStack stack, Class<? extends EnvironmentHost> host) { return host == Run.class && worksWith(stack); }
        @Override public int supportedComponents(ItemStack stack) { return 32; }
        @Override public Class<? extends Architecture> architecture(ItemStack stack) {
            String name = stack.get(DataComponents.CUSTOM_DATA).copyTag().getString(CPU_TAG);
            for (Class<? extends Architecture> architecture : li.cil.oc.api.Machine.architectures())
                if (architecture.getName().equals(name)) return architecture;
            throw new IllegalArgumentException("Fixture architecture is no longer registered: " + name);
        }
        @Override public double amount(ItemStack stack) { return 4096; }
        @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) { return null; }
        @Override public String slot(ItemStack stack) { return "cpu"; }
        @Override public int tier(ItemStack stack) { return 0; }
        @Override public CompoundTag dataTag(ItemStack stack) { return null; }
    }

    private static final class RegistryLabel implements Label {
        final HolderLookup.Provider expected;
        boolean saved, loaded;
        RegistryLabel(HolderLookup.Provider expected) { this.expected = expected; }
        @Override public String getLabel() { return "registry-aware fixture"; }
        @Override public void setLabel(String value) {}
        @Override public void load(CompoundTag nbt) { throw new AssertionError("Label.load dropped registry context"); }
        @Override public void save(CompoundTag nbt) { throw new AssertionError("Label.save dropped registry context"); }
        @Override public void load(CompoundTag nbt, HolderLookup.Provider registries) {
            require(registries == expected, "Label.load received a different Provider");
            require("label".equals(nbt.getString("fixtureLabel")), "Label persistence data was lost");
            loaded = true;
        }
        @Override public void save(CompoundTag nbt, HolderLookup.Provider registries) {
            require(registries == expected, "Label.save received a different Provider");
            nbt.putString("fixtureLabel", "label");
            saved = true;
        }
    }

    private static final class RegistryFileSystem implements li.cil.oc.api.fs.FileSystem {
        final HolderLookup.Provider expected;
        final li.cil.oc.api.fs.FileSystem inner = li.cil.oc.api.FileSystem.fromMemory(4096);
        boolean saved, loaded;
        RegistryFileSystem(HolderLookup.Provider expected) { this.expected = expected; }
        @Override public void load(CompoundTag nbt) { throw new AssertionError("FileSystem.load dropped registry context"); }
        @Override public void save(CompoundTag nbt) { throw new AssertionError("FileSystem.save dropped registry context"); }
        @Override public void load(CompoundTag nbt, HolderLookup.Provider registries) {
            require(registries == expected, "FileSystem.load received a different Provider");
            require("filesystem".equals(nbt.getString("fixtureFileSystem")), "FileSystem persistence data was lost");
            inner.load(nbt, registries);
            loaded = true;
        }
        @Override public void save(CompoundTag nbt, HolderLookup.Provider registries) {
            require(registries == expected, "FileSystem.save received a different Provider");
            inner.save(nbt, registries);
            nbt.putString("fixtureFileSystem", "filesystem");
            saved = true;
        }
        @Override public boolean isReadOnly() { return false; }
        @Override public long spaceTotal() { return inner.spaceTotal(); }
        @Override public long spaceUsed() { return inner.spaceUsed(); }
        @Override public boolean exists(String path) { return inner.exists(path); }
        @Override public long size(String path) { return inner.size(path); }
        @Override public boolean isDirectory(String path) { return inner.isDirectory(path); }
        @Override public long lastModified(String path) { return inner.lastModified(path); }
        @Override public String[] list(String path) { return inner.list(path); }
        @Override public boolean delete(String path) { return inner.delete(path); }
        @Override public boolean makeDirectory(String path) { return inner.makeDirectory(path); }
        @Override public boolean rename(String from, String to) throws FileNotFoundException { return inner.rename(from, to); }
        @Override public boolean setLastModified(String path, long time) { return inner.setLastModified(path, time); }
        @Override public int open(String path, Mode mode) throws FileNotFoundException { return inner.open(path, mode); }
        @Override public Handle getHandle(int handle) { return inner.getHandle(handle); }
        @Override public void close() { inner.close(); }
    }

    private record PlainContext(Node node) implements Context {
        @Override public boolean canInteract(String player) { return true; }
        @Override public boolean isRunning() { return false; }
        @Override public boolean isPaused() { return false; }
        @Override public boolean start() { return false; }
        @Override public boolean pause(double seconds) { return false; }
        @Override public boolean stop() { return false; }
        @Override public void consumeCallBudget(double callCost) {}
        @Override public boolean signal(String name, Object... args) { return false; }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final String BIOS = """
        assert(computer.getProgramLocations().oc_fixture_program == "fixture-disk",
          "IMC program mapping was not received before server startup")
        local address = assert(component.list("api_fixture", true)())
        local function check(result, path)
          assert(type(result) == "table", path .. ": addon result was not converted")
          assert(result.kind == "fixture" and result.path == path, path .. ": wrong addon conversion")
          assert(result.lookup and result.hasLookup, path .. ": registry context was not preserved")
          assert(result.nested[1].legacy == "legacy-sam", path .. ": legacy Converter SAM was not retained")
        end
        check(component.invoke(address, "payload", "lua-component"), "lua-component")
        local value = assert(component.invoke(address, "value"))
        check(value[41], "apply")
        local called, extra = value("argument")
        check(called, "call")
        assert(extra == 42, "Value.call result tuple was changed")
        check(value.fetch(), "invoke")
        assert(component.invoke(address, "report", "done"))
        computer.shutdown()
        """;
}
