package ocfixture;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Actual robot callbacks, real machine contexts, vanilla BlockItems and real inventories. */
public final class FlightUpgradeContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-flight-contract");
    private static Run active;
    private FlightUpgradeContract() {}

    public static void start(ServerLevel level, BlockPos position, List<String> results) throws Exception {
        require(active == null, "A flight upgrade contract is already running");
        Run run = new Run(level, position.immutable(), results);
        active = run;
        try { run.start(); }
        catch (Exception | Error error) {
            results.add("FAIL flight.interaction setup: " + error);
            LOGGER.error("FLIGHT_CONTRACT setup failed", error);
            active = null;
            run.cleanup();
        }
    }
    public static boolean isRunning() { return active != null; }
    public static void tick() {
        Run run = active;
        if (run == null) return;
        try {
            if (!run.tick()) return;
        } catch (Throwable error) {
            run.results.add("FAIL flight.interaction phase=" + run.phase + ": " + error);
            LOGGER.error("FLIGHT_CONTRACT phase {} failed", run.phase, error);
        }
        active = null;
        run.cleanup();
    }
    public static void close() {
        Run run = active;
        active = null;
        if (run != null) {
            run.results.add("FAIL flight.interaction: server stopped before the scenario completed");
            run.cleanup();
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static ItemStack item(String name) {
        var descriptor = li.cil.oc.api.Items.get(name);
        require(descriptor != null, "Missing flight fixture hardware " + name);
        return descriptor.createItemStack(1);
    }

    private static final class Run {
        final ServerLevel level;
        final BlockPos position;
        final List<String> results;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final Set<Integer> previousItems = new LinkedHashSet<>();
        final List<Unit> units = new ArrayList<>();
        Unit ordinary, angel;
        int ticks, phase;

        Run(ServerLevel level, BlockPos position, List<String> results) {
            this.level = level; this.position = position; this.results = results;
        }
        void rememberEmpty(BlockPos pos) {
            if (restored.containsKey(pos)) return;
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true))
                forced.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            level.getChunkAt(pos);
            require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "Flight fixture cell is occupied: " + pos);
            restored.put(pos.immutable(), level.getBlockState(pos));
        }
        void start() {
            require(CreativeTab.instance != null, "Configured creative robot is unavailable");
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
            ItemStack preset = null;
            Item robotItem = item("robot").getItem();
            for (ItemStack stack : CreativeTab.instance.getDisplayItems()) {
                if (stack.is(robotItem)
                        && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("oc:tier") == 3) {
                    preset = stack.copy(); break;
                }
            }
            require(preset != null && preset.getItem() instanceof BlockItem, "Creative tab supplied no configured robot");
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, bounds())) previousItems.add(entity.getId());
            ordinary = new Unit(this, position, false);
            angel = new Unit(this, position.east(8), true);
            units.add(ordinary); units.add(angel);
            ordinary.place(configure(preset, true));
            angel.place(configure(preset, false));
        }
        ItemStack configure(ItemStack preset, boolean removeAngel) {
            ItemStack configured = preset.copy();
            CompoundTag data = configured.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            ListTag original = data.getList("oc:components", Tag.TAG_COMPOUND), changed = new ListTag();
            int angels = 0;
            Item angelItem = item("angelupgrade").getItem();
            for (Tag value : original) {
                ItemStack part = ItemStack.parseOptional(level.registryAccess(), (CompoundTag) value);
                if (part.is(angelItem)) { angels++; if (removeAngel) continue; }
                changed.add(value.copy());
            }
            require(angels == 1 && changed.size() == original.size() - (removeAngel ? 1 : 0),
                    "The actual preset no longer has the bounded single removable Angel upgrade");
            data.put("oc:components", changed);
            configured.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            return configured;
        }
        boolean tick() throws Exception {
            if (++ticks < 3) return false;
            if (phase == 0) {
                ordinary.ready(); angel.ready();
                require(ordinary.robot.machine().node().network() != angel.robot.machine().node().network(), "Fixture robots share a machine network");
                ordinary.air(); angel.air();
                ordinary.result("place", false);
                ordinary.air(); ordinary.materials(7, 5, 6);
                pass("without Angel, actual place rejects pure air and preserves all material counts");
            } else if (phase == 1) {
                ordinary.result("use", false);
                ordinary.air(); ordinary.materials(7, 5, 6);
                pass("without Angel, actual use cannot place its equipped vanilla BlockItem in pure air");
            } else if (phase == 2) {
                angel.result("place", true);
                require(level.getBlockState(angel.target).is(Blocks.DIRT), "Angel place did not put selected-slot dirt in its actual target");
                angel.materials(7, 4, 6);
                pass("Angel place consumes one real selected-slot dirt and places it in the adjacent target");
            } else if (phase == 3) {
                require(level.setBlock(angel.target, Blocks.AIR.defaultBlockState(), 3), "Could not clear the previous fixture placement");
                angel.air();
                Object[] result = angel.result("use", true);
                require(result.length == 2 && "item_placed".equals(text(result[1])), "Angel use lost its original item_placed result");
                require(level.getBlockState(angel.target).is(Blocks.COBBLESTONE), "Angel use did not place real first-slot cobblestone");
                angel.materials(6, 4, 6);
                pass("Angel use consumes main slot one, preserves the actual selected slot and leaves selected/equipped items unchanged");
            } else if (phase == 4) {
                BlockPos anchor = ordinary.target.relative(ordinary.front);
                require(level.setBlock(anchor, Blocks.STONE.defaultBlockState(), 3), "Could not place the real solid ray reference");
                ordinary.result("place", true);
                require(level.getBlockState(ordinary.target).is(Blocks.DIRT) && level.getBlockState(anchor).is(Blocks.STONE),
                        "Without Angel, ordinary ray-hit placement did not use the solid block face");
                ordinary.materials(7, 4, 6);
                pass("without Angel, a real solid block ray hit still permits normal selected-slot placement");
                return true;
            }
            phase++;
            return false;
        }
        void pass(String text) { results.add("PASS flight.interaction " + text); }
        AABB bounds() { return new AABB(position).inflate(5).expandTowards(8, 0, 0); }
        void cleanup() {
            for (Unit unit : units) if (unit.robot != null) unit.robot.machine().stop();
            var entries = new ArrayList<>(restored.entrySet());
            Collections.reverse(entries);
            for (var entry : entries) {
                if (level.getBlockEntity(entry.getKey()) instanceof Container inventory) inventory.clearContent();
                level.setBlock(entry.getKey(), entry.getValue(), 3);
            }
            restored.clear();
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, bounds()))
                if (!previousItems.contains(entity.getId())) entity.discard();
            for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            forced.clear();
        }
    }

    private static final class Unit {
        final Run run;
        final BlockPos position;
        final boolean hasAngel;
        Robot robot;
        Component component;
        Direction front;
        BlockPos target;

        Unit(Run run, BlockPos position, boolean hasAngel) { this.run = run; this.position = position; this.hasAngel = hasAngel; }
        void place(ItemStack configured) {
            run.rememberEmpty(position);
            FakePlayer actor = new FakePlayer(run.level, new GameProfile(UUID.randomUUID(), hasAngel ? "OC_FlightAngel" : "OC_FlightPlain"));
            actor.getAbilities().instabuild = true;
            actor.setPos(position.getX() + .5, position.getY(), position.getZ() - 2);
            actor.setItemInHand(InteractionHand.MAIN_HAND, configured);
            require(((BlockItem) configured.getItem()).place(new BlockPlaceContext(actor, InteractionHand.MAIN_HAND, configured,
                    new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false))).consumesAction(), "Actual configured robot placement failed");
            require(run.level.getBlockEntity(position) instanceof Robot, "Placed flight unit is not an actual SDK Robot");
            robot = (Robot) run.level.getBlockEntity(position);
            front = robot.toGlobal(Direction.SOUTH);
            target = position.relative(front);
            for (int distance = 1; distance <= 4; distance++) run.rememberEmpty(position.relative(front, distance));
            for (Direction side : Direction.values()) run.rememberEmpty(target.relative(side));
        }
        void ready() {
            require(run.level.getServer().isSameThread(), "Flight callback scenario must run on the actual server thread");
            for (Node node : robot.machine().node().network().nodes()) {
                if (node instanceof Component value && "robot".equals(value.name()) && value.canBeSeenFrom(robot.machine().node())) component = value;
            }
            require(component != null, "The placed robot has no reachable actual robot callback component");
            int installedAngels = 0;
            Item angelItem = item("angelupgrade").getItem();
            for (int slot = 0; slot < robot.getContainerSize(); slot++) {
                ItemStack stack = robot.getItem(slot);
                if (!stack.is(angelItem)) continue;
                installedAngels++;
                require(Driver.driverFor(stack, robot.machine().host().getClass()) != null, "Actual robot host rejected its Angel driver");
                var environment = robot.getComponentInSlot(slot);
                require(environment != null && environment.node().canBeReachedFrom(robot.machine().node()), "Installed Angel is not in the actual robot network");
            }
            require(installedAngels == (hasAngel ? 1 : 0), "The two real robots do not have their intended independent Angel hardware");
            require(robot.mainInventory().getContainerSize() >= 3, "Configured robot has insufficient actual cargo slots");
            robot.mainInventory().clearContent();
            robot.mainInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 7));
            robot.mainInventory().setItem(2, new ItemStack(Items.DIRT, 5));
            robot.equipmentInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 6));
            robot.setSelectedSlot(2);
            require(run.level.getEntities(null, new AABB(target)).isEmpty(), "Air interaction target contains a real entity");
        }
        Object[] result(String method, boolean success) throws Exception {
            Object[] result = component.invoke(method, robot.machine(), 3, 3);
            require(result != null && result.length > 0 && Boolean.valueOf(success).equals(result[0]),
                    "Real robot " + method + " returned " + java.util.Arrays.deepToString(result) + ", Angel=" + hasAngel);
            return result;
        }
        void air() {
            for (int distance = 1; distance <= 4; distance++)
                require(run.level.isEmptyBlock(position.relative(front, distance)), "Air scenario contains a block on its actual ray");
        }
        void materials(int first, int selected, int equipped) {
            exact(robot.mainInventory().getItem(0), Items.COBBLESTONE, first, "first cargo slot");
            exact(robot.mainInventory().getItem(2), Items.DIRT, selected, "selected cargo slot");
            exact(robot.equipmentInventory().getItem(0), Items.OAK_PLANKS, equipped, "equipped BlockItem");
            require(robot.selectedSlot() == 2, "Interaction changed the real selected cargo slot");
        }
        void exact(ItemStack stack, Item item, int count, String slot) {
            require(stack.is(item) && stack.getCount() == count, "Actual " + slot + " item/count mismatch: " + stack);
        }
    }

    private static String text(Object value) {
        return value instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : String.valueOf(value);
    }
}
