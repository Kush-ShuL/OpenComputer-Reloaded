package ocfixture;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.API;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Network;
import li.cil.oc.api.event.GeolyzerEvent;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.internal.Rotatable;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real mounted terrain analysis, live motion messages and actual waypoint block interactions. */
public final class WorldSensorsContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-world-sensors");
    private static Run active;
    private WorldSensorsContract() {}

    public static void start(ServerLevel level, BlockPos position, List<String> results) {
        if (active != null) { results.add("FAIL world.sensors: contract already active"); return; }
        Run run = new Run(level, position.immutable(), results); active = run;
        try { run.start(); }
        catch (Throwable error) { run.fail(error); active = null; clean(run); }
    }
    public static boolean isRunning() { return active != null; }
    public static void tick() {
        Run run = active; if (run == null) return;
        try { if (!run.tick()) return; }
        catch (Throwable error) { run.fail(error); }
        active = null; clean(run);
    }
    public static void close() {
        Run run = active; active = null;
        if (run != null) { run.fail(new IllegalStateException("server stopped before sensor contract completed")); clean(run); }
    }
    private static void clean(Run run) {
        try { run.cleanup(); }
        catch (Throwable error) { run.results.add("FAIL world.sensors cleanup: " + error); LOGGER.error("WORLD_SENSORS cleanup failed", error); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void near(double actual, double expected, String message) {
        require(Math.abs(actual - expected) <= Math.max(1e-7, Math.abs(expected) * 1e-9), message + ": expected=" + expected + ", actual=" + actual);
    }
    private static ItemStack item(String name) {
        var descriptor = li.cil.oc.api.Items.get(name); require(descriptor != null, "Missing real sensor hardware " + name);
        return descriptor.createItemStack(1);
    }

    private static final class Run {
        final ServerLevel level;
        final BlockPos position, motionPosition, waypointPosition;
        final List<String> results;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final Set<Integer> previousItems = new HashSet<>();
        Robot robot;
        FakePlayer actor;
        ArmorStand target;
        SignalProbe probe;
        Component motion;
        CancelAnalysis cancel;
        Vec3 initialTarget;
        int ticks, phase, phaseAt;
        boolean failed, cleaned;

        Run(ServerLevel level, BlockPos position, List<String> results) {
            this.level = level; this.position = position; this.results = results;
            motionPosition = position.east(8); waypointPosition = position.north(4);
        }
        AABB bounds() { return new AABB(Vec3.atLowerCornerOf(position.offset(-2, -2, -6)), Vec3.atLowerCornerOf(position.offset(15, 5, 5))); }
        void force(BlockPos pos) {
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true)) forced.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            level.getChunkAt(pos);
        }
        void remember(BlockPos pos) {
            if (restored.containsKey(pos)) return;
            force(pos); require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "Sensor fixture refuses occupied cell " + pos);
            restored.put(pos.immutable(), level.getBlockState(pos));
        }
        void place(BlockPos pos, BlockState state) { remember(pos); require(level.setBlock(pos, state, 3), "Could not place real sensor cell " + pos); }
        void placeItem(BlockPos pos, ItemStack stack) {
            remember(pos); require(stack.getItem() instanceof BlockItem, "Fixture item is not an actual placeable block");
            actor.setItemInHand(InteractionHand.MAIN_HAND, stack);
            require(((BlockItem) stack.getItem()).place(new BlockPlaceContext(actor, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))).consumesAction(), "Actual sensor block item placement failed at " + pos);
        }
        void start() {
            require(API.config != null && !API.config.getBoolean("power.ignorePower") && API.config.getBoolean("misc.allowItemStackInspection"),
                "Sensor contract requires normal enabled power and item inspection");
            force(position); force(motionPosition); force(waypointPosition);
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, bounds())) previousItems.add(entity.getId());
            actor = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "OC_WorldSensors"));
            actor.getAbilities().instabuild = true; actor.setPos(position.getX() + .5, position.getY(), position.getZ() - 2);
            require(CreativeTab.instance != null, "Actual creative robot tab is unavailable");
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
            ItemStack preset = null;
            for (ItemStack stack : CreativeTab.instance.getDisplayItems()) {
                if (stack.is(item("robot").getItem()) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("oc:tier") == 3) { preset = stack.copy(); break; }
            }
            require(preset != null, "Creative tab supplied no real configured robot");
            placeItem(position, preset);
            require(level.getBlockEntity(position) instanceof Robot, "Actual placed robot exposes no SDK Robot");
            robot = (Robot) level.getBlockEntity(position);
            require(robot.machine() != null && robot.machine().host() instanceof Robot && !robot.machine().isRunning(), "Sensor Context must be the actual stopped robot Machine");
            place(position.above(), Blocks.STONE.defaultBlockState());
            place(motionPosition, li.cil.oc.api.Items.get("motionsensor").block().defaultBlockState());
            actor.setYRot(45); actor.setXRot(40);
            placeItem(waypointPosition, item("waypoint"));
        }
        Component robotComponent(String name) {
            require(robot.machine().node().network() != null, "Placed real robot has no component network");
            for (Node node : robot.machine().node().network().nodes()) if (node instanceof Component component && name.equals(component.name())) return component;
            throw new AssertionError("Actual robot did not mount its real " + name + " hardware");
        }
        Component blockComponent(BlockPos pos, String name) {
            require(level.getBlockEntity(pos) instanceof Environment, "Actual sensor block exposes no SDK Environment at " + pos);
            Node node = ((Environment) level.getBlockEntity(pos)).node();
            require(node instanceof Component && name.equals(((Component) node).name()), "Actual block has no " + name + " component at " + pos);
            return (Component) node;
        }
        boolean tick() throws Exception {
            require(++ticks <= 180, "Sensor contract exceeded its finite tracking/sample deadline");
            require(!robot.machine().isRunning(), "Sensor contract unexpectedly started fixture firmware");
            if (phase == 0) {
                if (ticks < 3) return false;
                geolyzer(); waypoint(); prepareMotion(); phase = 1; phaseAt = ticks; return false;
            }
            require(target != null && target.isAlive() && !target.isRemoved(), "Actual motion target died or was removed");
            if (level.getEntity(target.getUUID()) != target || !level.getEntitiesOfClass(ArmorStand.class, bounds()).contains(target)) {
                require(ticks - phaseAt < 80, "Real motion target never became publicly tracked"); return false;
            }
            Vec3 reference = new Vec3(motionPosition.getX() + 1, motionPosition.getY() + 1, motionPosition.getZ() + 1);
            Vec3 expected = (phase == 1 ? initialTarget : initialTarget.add(1, 0, 0)).subtract(reference);
            if (!probe.has(expected, API.config.getBoolean("misc.inputUsername"), target.getName().getString())) {
                require(ticks - phaseAt < 60, "Actual ten-tick motion sensor emitted no matching " + (phase == 1 ? "new-entity" : "moved-entity") + " signal; captured=" + probe.signals.size());
                return false;
            }
            if (phase == 1) {
                probe.signals.clear(); target.setPos(initialTarget.x + 1, initialTarget.y, initialTarget.z); phase = 2; phaseAt = ticks; return false;
            }
            results.add("PASS motion.real: actual sensor emits original new-entity and moved-entity computer.signal messages for a publicly tracked living ArmorStand, with configured username and original relative coordinates at the ten-tick sample");
            results.add("LIMIT world.sensors: real mounted Robot terrain callbacks, one live motion target and representative waypoint orientations; Microcontroller/Drone/Tablet host branches, all occlusion/weather states, chunk cold recovery, multiplayer and client UI not exercised");
            return true;
        }
        void geolyzer() throws Exception {
            Component geolyzer = robotComponent("geolyzer");
            require(geolyzer instanceof Connector, "Real mounted geolyzer exposes no energy connector");
            Connector power = (Connector) geolyzer;
            double cost = API.config.getDouble("power.cost.geolyzerScan"), before = power.globalBuffer();
            require(cost > 0 && before >= cost * 3, "Real geolyzer must have enough configured positive energy for the bounded callbacks");
            Object[] analyzed = geolyzer.invoke("analyze", robot.machine(), Direction.UP.get3DDataValue());
            require(analyzed != null && analyzed.length == 1 && analyzed[0] instanceof Map, "Mounted real geolyzer analyze returned no block table");
            Map<?, ?> data = (Map<?, ?>) analyzed[0];
            require("minecraft:stone".equals(data.get("name")) && data.get("hardness") instanceof Number, "Mounted real geolyzer did not analyze the actual adjacent stone");
            double hardness = level.getBlockState(position.above()).getDestroySpeed(level, position.above());
            near(((Number) data.get("hardness")).doubleValue(), hardness, "Real analyzed hardness");
            Object[] scanned = geolyzer.invoke("scan", robot.machine(), 0, 0, 1, 1, 1, 1);
            require(scanned != null && scanned.length == 1 && scanned[0] instanceof float[] && ((float[]) scanned[0]).length == 64, "Actual geolyzer scan did not preserve its 64-value result");
            float[] values = (float[]) scanned[0];
            require(Math.abs(values[0] - hardness) <= API.config.getDouble("misc.geolyzerNoise") / 33 + 1e-5, "Real scanned stone density is outside the original noise range");
            for (int i = 1; i < values.length; i++) require(Math.abs(values[i]) <= 1d / 33 + 1e-7, "Short scan tail lost the original signed-byte noise range");
            near(before - power.globalBuffer(), cost * 2, "Actual analyze+scan energy charge");
            double unchanged = power.globalBuffer();
            try { geolyzer.invoke("analyze", robot.machine(), Direction.UP.get3DDataValue(), "invalid-options"); throw new AssertionError("Malformed analyze options were accepted"); }
            catch (IllegalArgumentException expected) { near(power.globalBuffer(), unchanged, "Malformed options must fail before charging energy"); }
            cancel = new CancelAnalysis(robot.machine().host()); NeoForge.EVENT_BUS.register(cancel);
            try {
                Object[] canceled = geolyzer.invoke("analyze", robot.machine(), Direction.UP.get3DDataValue());
                require(canceled != null && canceled.length == 2 && canceled[0] == null && "scan was canceled".equals(canceled[1]) && cancel.calls == 1,
                    "Actual mounted analyze did not honor its real cancellable event");
                near(unchanged - power.globalBuffer(), cost, "Canceled original scan still consumes its charge");
            } finally { NeoForge.EVENT_BUS.unregister(cancel); cancel = null; }
            results.add("PASS geolyzer.real: creative Robot actually mounts its item driver; real Component.invoke analyzes/scans adjacent stone, preserves 64-value noise bounds and exact configured cost, validates options before payment and honors the real cancel event");
        }
        void waypoint() throws Exception {
            BlockEntity block = level.getBlockEntity(waypointPosition);
            require(block instanceof Rotatable, "Real waypoint exposes no Rotatable API");
            Rotatable rotation = (Rotatable) block;
            require(rotation.facing() == Direction.EAST, "Waypoint placement did not retain original horizontal orientation at 40-degree pitch/45-degree yaw");
            Component waypoint = blockComponent(waypointPosition, "waypoint");
            String address = waypoint.address();
            String label = "World sensor fixture label longer than thirty-two characters";
            waypoint.invoke("setLabel", robot.machine(), label);
            require(label.substring(0, 32).equals(waypoint.invoke("getLabel", robot.machine())[0]), "Actual waypoint label callback did not retain 32 characters");
            actor.setPos(waypointPosition.getX() + .5, waypointPosition.getY(), waypointPosition.getZ() - 2);
            ItemStack wrench = item("wrench"); actor.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            turn(Direction.UP, false); require(rotation.facing() == Direction.SOUTH, "Actual positive-axis waypoint wrench rotation is wrong");
            turn(Direction.UP, true); require(rotation.facing() == Direction.EAST, "Sneaking waypoint wrench did not reverse its axis");
            turn(Direction.NORTH, false); require(rotation.facing() == Direction.UP && rotation.toGlobal(Direction.UP) == Direction.WEST,
                "Actual vertical waypoint orientation lost its pitch/yaw basis");
            for (Direction local : Direction.values()) require(rotation.toLocal(rotation.toGlobal(local)) == local, "Waypoint global/local conversion failed at " + local);
            require(address != null && address.equals(blockComponent(waypointPosition, "waypoint").address()) &&
                label.substring(0, 32).equals(waypoint.invoke("getLabel", robot.machine())[0]), "Actual wrench interaction replaced the waypoint node or label");
            actor.setShiftKeyDown(false);
            results.add("PASS waypoint.real: actual BlockItem placement retains original pitch rounding; real label callbacks truncate to 32, real wrench block interactions reverse signed axes and retain vertical local/global conversion, node identity and label");
        }
        void turn(Direction side, boolean sneaking) {
            actor.setShiftKeyDown(sneaking);
            level.getBlockState(waypointPosition).useItemOn(actor.getMainHandItem(), level, actor, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(waypointPosition), side, waypointPosition, false));
        }
        void prepareMotion() throws Exception {
            motion = blockComponent(motionPosition, "motion_sensor");
            Object[] previous = motion.invoke("setSensitivity", robot.machine(), .5);
            require(previous != null && previous.length == 1 && previous[0] instanceof Number, "Real sensor did not return its old sensitivity");
            near(((Number) previous[0]).doubleValue(), .4, "Real new sensor initial sensitivity");
            near(((Number) motion.invoke("getSensitivity", robot.machine())[0]).doubleValue(), .5, "Real sensor sensitivity update");
            probe = new SignalProbe(motion); Network.joinNewNetwork(probe.node()); probe.node().connect(motion);
            initialTarget = new Vec3(motionPosition.getX() + 3, motionPosition.getY() + 1, motionPosition.getZ() + 1);
            force(BlockPos.containing(initialTarget)); target = EntityType.ARMOR_STAND.create(level);
            require(target != null, "Vanilla ArmorStand factory failed"); target.setNoGravity(true); target.setInvulnerable(true);
            target.setPos(initialTarget.x, initialTarget.y, initialTarget.z);
            require(level.addFreshEntity(target), "Actual motion target could not join the real level");
        }
        void fail(Throwable error) {
            if (failed) return; failed = true; results.add("FAIL world.sensors: " + error); LOGGER.error("WORLD_SENSORS FAIL", error);
        }
        void cleanup() {
            if (cleaned) return; cleaned = true;
            if (cancel != null) NeoForge.EVENT_BUS.unregister(cancel);
            if (probe != null) probe.node().remove();
            if (target != null) target.discard();
            if (robot != null) robot.machine().stop();
            List<Map.Entry<BlockPos, BlockState>> cells = new ArrayList<>(restored.entrySet()); Collections.reverse(cells);
            for (var cell : cells) {
                if (level.getBlockEntity(cell.getKey()) instanceof Container inventory) inventory.clearContent();
                level.setBlock(cell.getKey(), cell.getValue(), 3);
            }
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, bounds())) if (!previousItems.contains(entity.getId())) entity.discard();
            for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            forced.clear(); restored.clear();
        }
    }

    public static final class CancelAnalysis {
        private final EnvironmentHost host;
        int calls;
        CancelAnalysis(EnvironmentHost host) { this.host = host; }
        @SubscribeEvent(priority = EventPriority.HIGHEST) public void analyze(GeolyzerEvent.Analyze event) {
            if (event.host == host) { calls++; event.setCanceled(true); }
        }
    }
    private static final class SignalProbe extends AbstractManagedEnvironment {
        final Node sensor;
        final List<Object[]> signals = new ArrayList<>();
        SignalProbe(Node sensor) { this.sensor = sensor; setNode(Network.newNode(this, Visibility.Network).create()); }
        @Override public void onMessage(Message message) {
            if (message.source() == sensor && message.name().equals("computer.signal") && message.data().length >= 4 && "motion".equals(message.data()[0])) signals.add(message.data().clone());
        }
        boolean has(Vec3 delta, boolean username, String name) {
            for (Object[] data : signals) {
                if (data.length != (username ? 5 : 4) || !(data[1] instanceof Number x) || !(data[2] instanceof Number y) || !(data[3] instanceof Number z)) continue;
                if (Math.abs(x.doubleValue() - delta.x) < 1e-7 && Math.abs(y.doubleValue() - delta.y) < 1e-7 && Math.abs(z.doubleValue() - delta.z) < 1e-7 && (!username || name.equals(data[4]))) return true;
            }
            return false;
        }
    }
}
