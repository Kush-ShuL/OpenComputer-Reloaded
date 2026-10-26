package ocfixture;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Memory;
import li.cil.oc.api.driver.item.Processor;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.machine.MachineHost;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A real machine checkpoint and replacement host, using only the published SDK. */
public final class CheckpointContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-api-checkpoint");
    private static final String CPU_TAG = "oc_fixture_checkpoint_cpu";
    private static final List<Run> RUNS = new CopyOnWriteArrayList<>();
    private static final List<String> RESULTS = new CopyOnWriteArrayList<>();
    private static HolderLookup.Provider expectedRegistries;
    private static boolean registered;

    private CheckpointContract() {}

    public static synchronized void register() {
        if (registered) return;
        Driver.add(new CheckpointProcessor());
        registered = true;
    }

    public static String run(ServerLevel level) {
        require(registered, "Checkpoint driver was not registered during common setup");
        require(RUNS.isEmpty(), "A checkpoint exercise is already active");
        RESULTS.clear();
        expectedRegistries = level.registryAccess();
        List<Class<? extends Architecture>> architectures = new ArrayList<>(li.cil.oc.api.Machine.architectures());
        require(architectures.stream().anyMatch(type -> !"LuaJ".equals(li.cil.oc.api.Machine.getArchitectureName(type))),
                "Native Lua is required to verify coroutine checkpoint restoration");
        for (Class<? extends Architecture> architecture : architectures) {
            Run run = new Run(level, architecture);
            RUNS.add(run);
            try { run.host.start(); }
            catch (RuntimeException | Error error) { RUNS.remove(run); run.dispose(); throw error; }
        }
        return "Started machine checkpoint contracts: " + architectures.stream().map(li.cil.oc.api.Machine::getArchitectureName).toList();
    }

    public static void tick() {
        for (Run run : RUNS) {
            try {
                run.host.machine.update();
                if (++run.ticks > 400) throw new AssertionError("Checkpoint contract did not finish within 400 ticks");
                if (run.probe.checkpointsRequested.get() > run.checkpointsSaved) run.replaceHost();
                if (!run.host.machine.isRunning()) {
                    require(run.probe.completed.get(), "Machine stopped before checkpoint verification: " + run.host.machine.lastError());
                    String detail = run.luaJ
                            ? "LuaJ restores by rebooting, as in the original implementation"
                            : "new hosts resume the native coroutine and synchronized return, Value, tmpfs handle and queued signal";
                    RESULTS.add("PASS " + run.name + " checkpoint: " + detail);
                    LOGGER.info("API_CHECKPOINT PASS {}: {}", run.name, detail);
                    RUNS.remove(run);
                    run.dispose();
                }
            } catch (Throwable error) {
                RESULTS.add("FAIL " + run.name + " checkpoint: " + error);
                LOGGER.error("API_CHECKPOINT FAIL {}", run.name, error);
                RUNS.remove(run);
                run.dispose();
            }
        }
    }

    public static List<String> results() { return List.copyOf(RESULTS); }
    public static boolean isRunning() { return !RUNS.isEmpty(); }
    public static void close() { for (Run run : RUNS) run.dispose(); RUNS.clear(); expectedRegistries = null; }

    private static final class Probe {
        final AtomicInteger checkpointsRequested = new AtomicInteger();
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicInteger boots = new AtomicInteger();
        final AtomicInteger savedValues = new AtomicInteger();
        final AtomicInteger loadedValues = new AtomicInteger();
    }

    private static final class Run {
        final ServerLevel level;
        final Class<? extends Architecture> architecture;
        final String name;
        final boolean luaJ;
        final Probe probe = new Probe();
        Host host;
        int checkpointsSaved;
        int ticks;

        Run(ServerLevel level, Class<? extends Architecture> architecture) {
            this.level = level;
            this.architecture = architecture;
            name = li.cil.oc.api.Machine.getArchitectureName(architecture);
            luaJ = "LuaJ".equals(name);
            host = new Host(level, architecture, probe);
        }

        void replaceHost() {
            // A callback paused the machine while its worker was still running.
            // Saving takes the public machine's execution monitor, so the Lua
            // worker has yielded before the checkpoint is written.
            require(host.machine.isPaused(), "Checkpoint was not held at a yield boundary");
            if (checkpointsSaved == 0)
                require(host.machine.signal("checkpoint_resume", 2718, "saved-signal"), "Could not queue the checkpoint wakeup");
            CompoundTag machineTag = new CompoundTag(), componentTag = new CompoundTag(), eepromTag = new CompoundTag();
            host.machine.save(machineTag, level.registryAccess());
            require(machineTag.getIntArray("state").length > 0, "Native persistence rejected the checkpoint");
            host.component.save(componentTag, level.registryAccess());
            host.eeprom.save(eepromTag, level.registryAccess());
            String address = host.machine.node().address(), tmpAddress = host.machine.tmpAddress();
            if (!luaJ) require(probe.savedValues.get() > 0, "The native checkpoint did not serialize the live addon Value");
            host.dispose();

            Host restored = new Host(level, architecture, probe);
            host = restored;
            restored.component.load(componentTag, level.registryAccess());
            restored.eeprom.load(eepromTag, level.registryAccess());
            // Match real block hosts: restore while disconnected, then attach
            // the restored component nodes to the replacement machine network.
            restored.machine.load(machineTag, level.registryAccess());
            require(restored.machine.isRunning(), "Saved machine failed to restore: " + restored.machine.lastError());
            require(address.equals(restored.machine.node().address()), "Machine address changed across its checkpoint");
            require(tmpAddress.equals(restored.machine.tmpAddress()), "tmpfs address changed across its checkpoint");
            restored.connect();
            restored.machine.onHostChanged();
            if (!luaJ) {
                require(probe.loadedValues.get() > 0, "The native checkpoint did not reconstruct the addon Value");
                require(restored.machine.start(), "Could not release the restored machine's startup pause");
            }
            checkpointsSaved++;
        }

        void dispose() { host.dispose(); }
    }

    private static final class Host implements MachineHost {
        final ServerLevel level;
        final AtomicBoolean dirty = new AtomicBoolean();
        final ItemStack processor = new ItemStack(Items.BLAZE_ROD);
        final Machine machine;
        final CheckpointComponent component;
        final CheckpointEeprom eeprom = new CheckpointEeprom();

        Host(ServerLevel level, Class<? extends Architecture> architecture, Probe probe) {
            this.level = level;
            CompoundTag marker = new CompoundTag();
            marker.putString(CPU_TAG, architecture.getName());
            processor.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
            machine = li.cil.oc.api.Machine.create(this);
            require(machine != null, "Machine.create returned null");
            component = new CheckpointComponent(probe);
        }

        void connect() {
            Network.joinNewNetwork(machine.node());
            machine.node().connect(component.node());
            machine.node().connect(eeprom.node());
            machine.setCostPerTick(0);
            ((Connector) machine.node()).changeBuffer(Double.MAX_VALUE);
        }

        void start() { connect(); require(machine.start(), "Checkpoint machine did not boot: " + machine.lastError()); }
        void dispose() { machine.stop(); machine.node().remove(); component.node().remove(); eeprom.node().remove(); }
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

    public static final class CheckpointComponent extends AbstractManagedEnvironment {
        final Probe probe;
        CheckpointComponent(Probe probe) {
            this.probe = probe;
            setNode(Network.newNode(this, Visibility.Network).withComponent("checkpoint_fixture", Visibility.Network).create());
        }
        @Callback(direct = true, limit = 64) public Object[] booted(Context context, Arguments arguments) { return new Object[]{probe.boots.incrementAndGet()}; }
        @Callback(direct = true, limit = 64) public Object[] value(Context context, Arguments arguments) {
            CheckpointValue value = new CheckpointValue();
            value.probe = probe;
            return new Object[]{value};
        }
        @Callback(direct = true, limit = 64) public Object[] ready(Context context, Arguments arguments) {
            require(probe.boots.get() == 1, "Checkpoint BIOS restarted before its checkpoint");
            require(!context.start(), "Starting an already running machine changed its state");
            require(context.pause(60), "Could not hold the checkpoint machine at its next yield");
            probe.checkpointsRequested.incrementAndGet();
            return new Object[]{true};
        }
        @Callback public Object[] synchronizedCheckpoint(Context context, Arguments arguments) {
            require(probe.checkpointsRequested.get() == 1, "Synchronized checkpoint was requested out of order");
            require(context.pause(60), "Could not hold the synchronized return checkpoint");
            CheckpointValue value = new CheckpointValue();
            value.probe = probe;
            probe.checkpointsRequested.incrementAndGet();
            return new Object[]{"saved synchronized result", value};
        }
        @Callback(direct = true, limit = 64) public Object[] complete(Context context, Arguments arguments) {
            require(probe.boots.get() == 1, "Native checkpoint resumed by re-running the BIOS");
            require(probe.checkpointsRequested.get() == 2, "Native synchronized return checkpoint was not exercised");
            require(arguments.checkInteger(0) == 314159, "Lua local state was lost");
            probe.completed.set(true);
            return new Object[]{true};
        }
        @Callback(direct = true, limit = 64) public Object[] rebooted(Context context, Arguments arguments) {
            require(probe.boots.get() == 2, "LuaJ did not restart exactly once on restore");
            probe.completed.set(true);
            return new Object[]{true};
        }
    }

    /** Public default constructor is required by the original userdata protocol. */
    public static final class CheckpointValue extends AbstractValue {
        private Probe probe;
        private int counter = 42;
        private ItemStack stack;
        public CheckpointValue() {
            stack = new ItemStack(Items.EMERALD, 7);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("checkpoint value"));
        }
        @Override public void save(CompoundTag tag) { throw new AssertionError("Value.save lost registry context"); }
        @Override public void load(CompoundTag tag) { throw new AssertionError("Value.load lost registry context"); }
        @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
            require(registries == expectedRegistries, "Value.save received a different registry provider");
            tag.putInt("counter", counter);
            tag.put("stack", stack.save(registries));
            // The persistent payload carries identity only for the isolated
            // fixture's Java observation; its actual Lua state is serialized.
            int index = -1;
            for (int i = 0; i < RUNS.size(); i++) if (RUNS.get(i).probe == probe) { index = i; break; }
            require(index >= 0, "Checkpoint Value has no owning fixture");
            tag.putString("architecture", RUNS.get(index).name);
            probe.savedValues.incrementAndGet();
        }
        @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
            require(registries == expectedRegistries, "Value.load received a different registry provider");
            counter = tag.getInt("counter");
            stack = ItemStack.parseOptional(registries, tag.getCompound("stack"));
            for (Run run : RUNS) if (run.name.equals(tag.getString("architecture"))) { probe = run.probe; break; }
            require(probe != null, "Restored Value has no owning fixture");
            probe.loadedValues.incrementAndGet();
        }
        @Callback(direct = true, limit = 64) public Object[] snapshot(Context context, Arguments arguments) {
            return new Object[]{counter, stack.getCount(), stack.getHoverName().getString()};
        }
    }

    public static final class CheckpointEeprom extends AbstractManagedEnvironment {
        CheckpointEeprom() { setNode(Network.newNode(this, Visibility.Network).withComponent("eeprom", Visibility.Network).create()); }
        @Callback(direct = true, limit = 64) public Object[] get(Context context, Arguments arguments) { return new Object[]{BIOS.getBytes(StandardCharsets.UTF_8)}; }
    }

    private static final class CheckpointProcessor implements Processor, Memory, HostAware {
        @Override public boolean worksWith(ItemStack stack) {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            return stack.is(Items.BLAZE_ROD) && data != null && data.copyTag().contains(CPU_TAG);
        }
        @Override public boolean worksWith(ItemStack stack, Class<? extends EnvironmentHost> host) { return host == Host.class && worksWith(stack); }
        @Override public int supportedComponents(ItemStack stack) { return 32; }
        @Override public Class<? extends Architecture> architecture(ItemStack stack) {
            String type = stack.get(DataComponents.CUSTOM_DATA).copyTag().getString(CPU_TAG);
            return li.cil.oc.api.Machine.architectures().stream().filter(value -> value.getName().equals(type)).findFirst().orElseThrow();
        }
        @Override public double amount(ItemStack stack) { return 4096; }
        @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) { return null; }
        @Override public String slot(ItemStack stack) { return "cpu"; }
        @Override public int tier(ItemStack stack) { return 0; }
        @Override public CompoundTag dataTag(ItemStack stack) { return null; }
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final String BIOS = """
        local p = component.proxy(assert(component.list("checkpoint_fixture", true)()))
        local boots = p.booted()
        if boots > 1 then
          assert(computer.getArchitecture() == "LuaJ", "native checkpoint restarted its BIOS")
          assert(p.rebooted())
          computer.shutdown()
        end
        local retained = 314159
        local value = p.value()
        local fs = component.proxy(assert(computer.tmpAddress()))
        local writer = assert(fs.open("checkpoint.txt", "w"))
        assert(fs.write(writer, "abcdefghi"))
        fs.close(writer)
        local reader = assert(fs.open("checkpoint.txt", "r"))
        assert(fs.read(reader, 3) == "abc")
        assert(p.ready())
        local event, number, text
        repeat event, number, text = computer.pullSignal() until event == "checkpoint_resume"
        assert(number == 2718 and text == "saved-signal", "saved signal arguments were lost")
        assert(retained == 314159, "Lua local state was lost")
        local count, size, label = value.snapshot()
        assert(count == 42 and size == 7 and label == "checkpoint value", "persisted Value lost its data components")
        assert(fs.read(reader, 6) == "defghi", "open tmpfs handle did not retain its position")
        assert(fs.seek(reader, "set", 3) == 3)
        local returned, returnedValue = p.synchronizedCheckpoint()
        assert(returned == "saved synchronized result", "synchronized return tuple was lost")
        count, size, label = returnedValue.snapshot()
        assert(count == 42 and size == 7 and label == "checkpoint value", "synchronized return Value was lost")
        assert(fs.read(reader, 6) == "defghi", "tmpfs handle was lost across synchronized return persistence")
        fs.close(reader)
        assert(p.complete(retained))
        computer.shutdown()
        """;
}
