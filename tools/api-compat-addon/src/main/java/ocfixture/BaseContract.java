package ocfixture;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.Converter;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.prefab.ItemStackArrayValue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Synchronous external API contracts; uses no implementation class or reflected OC internals. */
public final class BaseContract {
    private static final String MARKER = "ocfixture_component";
    private static final FixtureDriver DRIVER = new FixtureDriver();
    private static final List<String> RESULTS = new CopyOnWriteArrayList<>();
    private static Class<?> legacyFactory;
    private BaseContract() {}
    public static void register() {
        Driver.add(DRIVER);
        try {
            legacyFactory = Class.forName("ocfixture.legacy.LegacyConverterFactory");
            Driver.add((Converter) legacyFactory.getMethod("create").invoke(null));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Load the fixture's legacy source set compiled against the old SDK", error); }
    }
    public static void run(ServerLevel level) throws Exception {
        RESULTS.clear();
        check(API.config != null && API.driver != null && API.fileSystem != null && API.items != null && API.machine != null && API.network != null && API.nanomachines != null, "runtime facades initialized");
        network();
        driver(level);
        itemValue(level);
        commonClasses();
        FixtureBlocks.verifyPrefabs(level);
        pass("prefab constructors, registry-aware node save/load and chunk disconnect");
        if (Boolean.getBoolean("ocfixture.worldFixture")) FixtureBlocks.verifySimpleComponent(level);
    }
    private static void network() throws Exception {
        FixtureEnvironment near = new FixtureEnvironment("fixture_neighbor", Visibility.Neighbors, 64);
        FixtureEnvironment relay = new FixtureEnvironment("fixture_relay", Visibility.Network, 128);
        FixtureEnvironment far = new FixtureEnvironment("fixture_far", Visibility.Network, 0);
        try {
            Network.joinNewNetwork(near.node()); near.node().connect(relay.node()); relay.node().connect(far.node());
            check(near.node().network() == far.node().network(), "connect merges independently created addon nodes");
            check(((Component) near.node()).canBeSeenFrom(relay.node()) && !((Component) near.node()).canBeSeenFrom(far.node()), "Neighbors component visibility remains separate from reachability");
            far.node().sendToAddress(near.node().address(), "fixture.message", "hello", 17);
            check(near.lastMessage != null && near.lastMessage.source() == far.node() && near.lastMessage.name().equals("fixture.message") && near.lastMessage.data()[1].equals(17), "addressed messages retain source and payload");
            Connector connector = (Connector) near.node();
            check(connector.globalBufferSize() == 192, "connector capacities aggregate across the addon network");
            if (API.isPowerEnabled) {
                check(connector.changeBuffer(175.5) == 0 && connector.globalBuffer() == 175.5, "energy transfers use the global buffer");
                check(connector.tryChangeBuffer(-175.5) && connector.globalBuffer() == 0, "atomic energy consumption drains the network");
            }
            Object[] value = ((Component) relay.node()).invoke("legacy", new PlainContext(relay.node()));
            check(value.length == 1 && value[0] instanceof Map<?, ?> map && "old-sdk-binary".equals(map.get("legacyBinary")), "old-SDK two-argument Converter lambda links and runs through the new runtime");
            relay.node().disconnect(far.node());
            check(near.node().network() != far.node().network(), "disconnect splits addon networks");
        } finally { near.node().remove(); relay.node().remove(); far.node().remove(); }
    }
    private static void driver(ServerLevel level) {
        ItemStack stack = new ItemStack(Items.PAPER);
        CompoundTag root = new CompoundTag(); root.putBoolean(MARKER, true); root.putString("othermod", "preserved"); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        check(Driver.driverFor(stack) == DRIVER && Driver.itemDrivers().contains(DRIVER), "item driver registration is visible through public facades");
        CompoundTag detached = DRIVER.dataTag(stack); detached.putInt("counter", 77);
        check(!DRIVER.dataTag(stack).contains("counter"), "dataTag returns a detached snapshot");
        DRIVER.saveDataTag(stack, detached);
        check(DRIVER.dataTag(stack).getInt("counter") == 77 && stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("othermod").equals("preserved"), "saveDataTag commits while preserving foreign custom data");
        EnvironmentHost host = new EnvironmentHost() {
            public ServerLevel world() { return level; }
            public double xPosition() { return 0; } public double yPosition() { return 0; } public double zPosition() { return 0; }
            public void markChanged() {}
        };
        FixtureEnvironment environment = (FixtureEnvironment) DRIVER.createEnvironment(stack, host);
        FixtureEnvironment restored = (FixtureEnvironment) DRIVER.createEnvironment(stack, host);
        try {
            Network.joinNewNetwork(environment.node()); environment.counter = 91;
            CompoundTag saved = new CompoundTag(); environment.save(saved, level.registryAccess());
            restored.load(saved, level.registryAccess());
            check(restored.counter == 91 && restored.node().address().equals(environment.node().address()), "addon managed environments preserve address and own state through registry overloads");
        } finally { environment.node().remove(); restored.node().remove(); }
    }
    private static void itemValue(ServerLevel level) {
        ItemStack original = new ItemStack(Items.DIAMOND, 3);
        original.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("External fixture"));
        CompoundTag custom = new CompoundTag(); custom.putString("fixture", "components retained"); original.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        ItemStackArrayValue value = new ItemStackArrayValue(new ItemStack[]{original, ItemStack.EMPTY, null}, level.registryAccess());
        original.setCount(1);
        CompoundTag saved = new CompoundTag(); value.save(saved, level.registryAccess());
        ItemStackArrayValue restored = new ItemStackArrayValue(); restored.load(saved, level.registryAccess());
        Map<?, ?> values = (Map<?, ?>) restored.getAll(null, null)[0]; ItemStack first = (ItemStack) values.get(1);
        check(first.getCount() == 3 && first.getHoverName().getString().equals("External fixture") && first.get(DataComponents.CUSTOM_DATA).copyTag().getString("fixture").equals("components retained"), "ItemStackArrayValue registry round-trip retains count and data components");
        check(restored.count(null, null)[0].equals(3), "ItemStackArrayValue retains empty entries and length");
    }
    private static void commonClasses() throws Exception {
        for (String name : new String[]{"li.cil.oc.api.API", "li.cil.oc.api.Driver", "li.cil.oc.api.Network", "li.cil.oc.api.FileSystem", "li.cil.oc.api.Machine", "li.cil.oc.api.Manual", "li.cil.oc.api.prefab.TileEntityEnvironment", "li.cil.oc.api.prefab.TileEntitySidedEnvironment", "li.cil.oc.api.prefab.ResourceContentProvider"}) Class.forName(name).getDeclaredMethods();
        li.cil.oc.api.Manual.addProvider(new li.cil.oc.api.prefab.ResourceContentProvider("oc_api_compat"));
        pass("common facade/prefab classes link on the physical server; client resource provider registration remains safe");
    }
    private static void check(boolean condition, String description) { if (!condition) throw new AssertionError(description); pass(description); }
    static void pass(String message) { RESULTS.add("PASS " + message); }
    static void fail(String message) { RESULTS.add("FAIL " + message); }
    public static List<String> results() { return List.copyOf(RESULTS); }
    public static void tick() { FixtureBlocks.tick(); }
    public static boolean isRunning() { return FixtureBlocks.isRunning(); }
    public static void close() { FixtureBlocks.close(); }

    public static final class FixtureEnvironment extends AbstractManagedEnvironment {
        Message lastMessage; int counter;
        FixtureEnvironment(String name, Visibility visibility, double capacity) { setNode(Network.newNode(this, Visibility.Network).withComponent(name, visibility).withConnector(capacity).create()); }
        @Override public void onMessage(Message message) { lastMessage = message; }
        @Callback(direct = true) public Object[] ping(Context context, Arguments args) { return new Object[]{"pong"}; }
        @Callback(direct = true) public Object[] legacy(Context context, Arguments args) throws Exception { return new Object[]{legacyFactory.getMethod("payload", String.class).invoke(null, "old-sdk-binary")}; }
        @Override public void load(CompoundTag tag) { super.load(tag); counter = tag.getInt("counter"); }
        @Override public void save(CompoundTag tag) { super.save(tag); tag.putInt("counter", counter); }
    }
    private static final class FixtureDriver extends li.cil.oc.api.prefab.DriverItem {
        FixtureDriver() { super(new ItemStack(Items.PAPER)); }
        @Override public boolean worksWith(ItemStack stack) { return super.worksWith(stack) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(MARKER); }
        @Override public String slot(ItemStack stack) { return Slot.Card; }
        @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) { return new FixtureEnvironment("fixture_driver", Visibility.Network, 0); }
    }
    private record PlainContext(Node node) implements Context {
        public boolean canInteract(String player) { return true; } public boolean isRunning() { return false; } public boolean isPaused() { return false; }
        public boolean start() { return false; } public boolean stop() { return false; } public boolean pause(double seconds) { return false; }
        public void consumeCallBudget(double cost) {} public boolean signal(String name, Object... args) { return false; }
    }
}
