package ocfixture;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import li.cil.oc.api.API;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DriverItem;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** D48: actual robot native callbacks, plus public driver lifecycle energy checks. */
public final class GeneratorContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-generator-contract");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "oc_api_compat");
    private static final Supplier<Item> STARTER = ITEMS.register("generator_starter", () -> new Fuel(400, false));
    private static final Supplier<Item> CONTAINER_FUEL = ITEMS.register("generator_container_fuel", () -> new Fuel(8, true));
    private static Run active;
    private GeneratorContract() {}

    public static void register(IEventBus bus) { ITEMS.register(bus); }
    public static boolean isRunning() { return active != null; }
    public static void start(ServerLevel level, BlockPos position, List<String> results) throws Exception {
        require(active == null, "A generator contract is already active");
        Run run = new Run(level, position.immutable(), results); active = run;
        try { run.start(); } catch (Exception | Error error) { active = null; run.cleanup(); throw error; }
    }
    public static void tick() {
        Run run = active; if (run == null) return;
        try { if (!run.tick()) return; }
        catch (Throwable error) { run.fail(error); return; }
        active = null; run.cleanup();
    }
    public static void close() { Run run = active; active = null; if (run != null) run.cleanup(); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static ItemStack item(String name) {
        var descriptor = li.cil.oc.api.Items.get(name);
        require(descriptor != null, "Missing actual generator fixture hardware " + name);
        return descriptor.createItemStack(1);
    }
    private static final class Fuel extends Item {
        final int duration;
        Fuel(int duration, boolean container) { super(container ? new Item.Properties().craftRemainder(Items.BUCKET) : new Item.Properties()); this.duration = duration; }
        @Override public int getBurnTime(ItemStack stack, RecipeType<?> recipe) { return duration; }
    }
    private static final class Receiver extends AbstractManagedEnvironment {
        Receiver() { setNode(Network.newNode(this, Visibility.Network).withConnector(64).create()); }
        @Override public Connector node() { return (Connector) super.node(); }
    }

    private static final class Run {
        final ServerLevel level;
        final BlockPos position;
        final List<String> results;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final Set<Integer> previousDrops = new HashSet<>();
        final Set<ItemEntity> ownedDrops = new HashSet<>();
        li.cil.oc.api.internal.Robot robot;
        Probe probe;
        ManagedEnvironment generator, loaded;
        DriverItem driver;
        Receiver receiver;
        CompoundTag primeState;
        int bay, ticks, checked, prepared, failedAt = -1, doneAt = -1, energyStep, initialBurn, initialBurnTick;
        boolean started, worldBurnObserved, nativeDone;
        double efficiency;

        Run(ServerLevel level, BlockPos position, List<String> results) { this.level = level; this.position = position; this.results = results; }
        AABB bounds() { return new AABB(position).inflate(12); }
        void start() {
            require(API.config != null && CreativeTab.instance != null, "OC SDK configuration/creative tab is absent");
            efficiency = API.config.getDouble("power.generatorEfficiency");
            require(efficiency > 0 && efficiency < 16 && Double.isFinite(efficiency), "Bounded generator proof needs 0 < efficiency < 16");
            require(BIOS.getBytes(StandardCharsets.UTF_8).length < 4096, "Generator native BIOS exceeds its real EEPROM capacity");
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
            ItemStack preset = null;
            for (ItemStack stack : CreativeTab.instance.getDisplayItems())
                if (stack.is(item("robot").getItem()) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("oc:tier") == 3) { preset = stack.copy(); break; }
            require(preset != null && preset.getItem() instanceof BlockItem, "CreativeTab supplied no real configured Robot");
            CompoundTag data = preset.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            ListTag original = data.getList("oc:components", Tag.TAG_COMPOUND), parts = new ListTag();
            var architecture = li.cil.oc.api.Machine.architectures().stream().filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type)))
                .findFirst().orElseThrow(() -> new AssertionError("Real native Lua 5.3 is unavailable"));
            int hover = 0, cpu = 0, flash = 0;
            for (Tag raw : original) {
                ItemStack part = ItemStack.parseOptional(level.registryAccess(), (CompoundTag) raw);
                if (part.is(item("hoverupgrade2").getItem())) { hover++; continue; }
                var partDriver = Driver.driverFor(part);
                if (partDriver instanceof MutableProcessor processor && li.cil.oc.api.driver.item.Slot.CPU.equals(partDriver.slot(part))) { processor.setArchitecture(part, architecture); cpu++; }
                if (partDriver != null && "eeprom".equals(partDriver.slot(part))) { part = li.cil.oc.api.Items.registerEEPROM("Native generator contract", BIOS.getBytes(StandardCharsets.UTF_8), new byte[0], false); flash++; }
                parts.add(part.saveOptional(level.registryAccess()));
            }
            require(hover == 1 && cpu == 1 && flash == 1 && parts.size() + 1 == original.size(), "Generator setup must replace exactly one original upgrade without adding slots");
            data.put("oc:components", parts); preset.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            if (level.setChunkForced(position.getX() >> 4, position.getZ() >> 4, true)) forced.add(ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
            level.getChunkAt(position);
            require(level.isEmptyBlock(position) && level.getBlockEntity(position) == null, "Generator fixture will not overwrite an occupied cell");
            restored.put(position, level.getBlockState(position));
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, bounds())) previousDrops.add(entity.getId());
            FakePlayer actor = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "OC_Generator"));
            actor.getAbilities().instabuild = true; actor.setPos(position.getX() + .5, position.getY(), position.getZ() - 2); actor.setItemInHand(InteractionHand.MAIN_HAND, preset);
            require(((BlockItem) preset.getItem()).place(new BlockPlaceContext(actor, InteractionHand.MAIN_HAND, preset,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false))).consumesAction(), "Actual Robot BlockItem placement failed");
            require(level.getBlockEntity(position) instanceof li.cil.oc.api.internal.Robot, "Placed generator fixture lacks the real Robot SDK");
            robot = (li.cil.oc.api.internal.Robot) level.getBlockEntity(position);
            require(robot.mainInventory().getContainerSize() >= 6, "Actual robot has insufficient original cargo capacity");
            bay = -1;
            for (int slot = 1; slot < robot.equipmentInventory().getContainerSize(); slot++) if (robot.canPlaceItem(slot, item("generatorupgrade"))) { bay = slot; break; }
            require(bay > 0, "Original Robot has no existing upgrade bay for the replacement generator");
            robot.equipmentInventory().setItem(bay, item("generatorupgrade"));
            require(robot.componentCount() + 1 == original.size(), "Original built-in count changed beyond moving its one upgrade to an existing bay");
            require(robot.getComponentInSlot(bay) instanceof ManagedEnvironment, "Physical bay insertion did not create an actual generator environment");
            generator = (ManagedEnvironment) robot.getComponentInSlot(bay);
            require(generator.node() instanceof Component component && component.name().equals("generator"), "Real installed upgrade is not a generator component");
            driver = Driver.driverFor(robot.equipmentInventory().getItem(bay), robot.machine().host().getClass());
            require(driver != null, "Actual generator item has no public host-aware driver");
            probe = new Probe(this); Network.joinNewNetwork(probe.node()); probe.node().connect(robot.machine().node());
            LOGGER.info("GENERATOR_CONTRACT placed real native53 Robot, existing bay={} builtins={} original={}", bay, robot.componentCount(), original.size());
        }

        CompoundTag state(ManagedEnvironment environment) { CompoundTag tag = new CompoundTag(); environment.save(tag, level.registryAccess()); return tag; }
        ItemStack queue(CompoundTag state) { return ItemStack.parseOptional(level.registryAccess(), state.getCompound("inventory")); }
        int count(Item type) { int count = 0; for (int slot = 0; slot < robot.mainInventory().getContainerSize(); slot++) { ItemStack stack = robot.mainInventory().getItem(slot); if (stack.is(type)) count += stack.getCount(); } return count; }
        ItemStack selected() { return robot.mainInventory().getItem(robot.selectedSlot()); }
        void fill() { for (int slot = 0; slot < robot.mainInventory().getContainerSize(); slot++) robot.mainInventory().setItem(slot, new ItemStack(Items.STONE, 64)); }
        void set(int slot, Item item, int count) { robot.mainInventory().setItem(slot, new ItemStack(item, count)); }
        void prepare(int step) {
            require(step == checked + 1, "Native generator fixture skipped a preparation stage"); prepared = step;
            if (step > 4) require(state(generator).getInt("remainingTicks") > 0, "Inventory proof outlived the real active priming fuel");
            if (List.of(1, 2, 3, 4, 5, 9, 12, 13, 14, 15, 16).contains(step)) { robot.mainInventory().clearContent(); robot.setSelectedSlot(2); }
            switch (step) {
                case 2 -> set(2, Items.STONE, 8);
                case 3, 5 -> set(2, Items.STICK, 8);
                case 4 -> set(2, STARTER.get(), 1);
                case 6 -> { set(3, Items.COAL, 1); robot.setSelectedSlot(3); }
                case 7 -> robot.setSelectedSlot(2);
                case 9 -> set(2, CONTAINER_FUEL.get(), 4);
                case 12 -> { fill(); set(2, CONTAINER_FUEL.get(), 8); }
                case 13 -> { fill(); set(2, CONTAINER_FUEL.get(), 8); set(4, Items.BUCKET, 15); }
                case 14 -> { fill(); set(2, Items.BUCKET, 4); set(3, CONTAINER_FUEL.get(), 63); }
                case 15 -> { fill(); set(2, Items.BUCKET, 4); }
                case 16 -> set(2, Items.BUCKET, 3);
                default -> { }
            }
        }

        void check(int step) {
            require(step == prepared && step == checked + 1, "Native generator proof skipped/repeated a stage");
            CompoundTag saved = state(generator); int queued = queue(saved).getCount();
            switch (step) {
                case 1, 2 -> require(queued == 0, "Rejected non-fuel changed the queue");
                case 3 -> require(queued == 0 && count(Items.STICK) == 8, "Zero operations changed ordinary fuel");
                case 4 -> require(queued == 0 && worldBurnObserved && saved.getInt("remainingTicks") > 0, "Actual installed generator world ticker did not consume and burn its priming item");
                case 5, 6 -> require(queued == 5 && count(Items.STICK) == 3, "Normal insertion/foreign-fuel refusal broke fuel conservation");
                case 7 -> require(queued == 3 && selected().is(Items.STICK) && selected().getCount() == 5, "Partial removal ignored selected-slot-first order or changed totals");
                case 8 -> require(queued == 0 && count(Items.STICK) == 8, "Normal fuel queue round trip lost/duplicated fuel");
                case 9 -> require(queued == 4 && selected().is(Items.BUCKET) && selected().getCount() == 4 && robot.mainInventory().getItem(0).isEmpty(), "Container return did not prefer the actual selected slot");
                case 10 -> require(queued == 2 && count(CONTAINER_FUEL.get()) == 2 && count(Items.BUCKET) == 2, "Partial container-fuel removal did not consume exactly two empty containers");
                case 11 -> require(queued == 0 && count(CONTAINER_FUEL.get()) == 4 && count(Items.BUCKET) == 0, "Container-fuel round trip lost/duplicated filled or empty containers");
                case 12 -> require(queued == 0 && selected().is(CONTAINER_FUEL.get()) && selected().getCount() == 8 && count(Items.BUCKET) == 0, "Rejected container insertion failed to restore all selected fuel");
                case 13 -> {
                    require(queued == 4 && count(CONTAINER_FUEL.get()) == 4 && count(Items.BUCKET) == 16, "Partial container insertion did not preserve actual inventory/queue totals");
                    int dropped = 0;
                    for (ItemEntity entity : newDrops()) { require(entity.getItem().is(Items.BUCKET), "Unexpected generated item during partial container return"); dropped += entity.getItem().getCount(); entity.discard(); }
                    require(dropped == 3, "Partial container return must actually drop its three unaccepted buckets");
                }
                case 14 -> require(queued == 3 && count(CONTAINER_FUEL.get()) == 64 && selected().is(Items.BUCKET) && selected().getCount() == 3,
                    "Partial fuel acceptance consumed containers for fuel that remained queued");
                case 15 -> require(queued == 3 && selected().is(Items.BUCKET) && selected().getCount() == 4 && count(CONTAINER_FUEL.get()) == 0,
                    "Total fuel-removal rejection failed to restore selected containers and queue");
                case 16 -> { require(queued == 3 && saved.getInt("remainingTicks") > 0, "Final actual queue/timer proof was not live"); roundTrip(saved); }
                default -> throw new AssertionError("Unknown generator proof stage");
            }
            checked = step;
        }

        void roundTrip(CompoundTag saved) {
            ItemStack upgrade = robot.equipmentInventory().getItem(bay).copy();
            driver.saveDataTag(upgrade, saved);
            ManagedEnvironment copy = driver.createEnvironment(upgrade, (EnvironmentHost) robot.machine().host());
            require(copy != null, "Actual generator driver refused registry-aware reload");
            copy.load(driver.dataTag(upgrade), level.registryAccess());
            CompoundTag restored = state(copy);
            require(copy.node().address().equals(generator.node().address()) && restored.getInt("remainingTicks") == saved.getInt("remainingTicks")
                && ItemStack.matches(queue(saved), queue(restored)), "Actual generator driver round trip changed queue components/count, remaining time or node identity");
            // This copy never enters a network or ticks: it only reads an actual
            // serialized state. Do not activate copied fuel or turn it into drops.
            require(copy.node().network() == null, "Read-only generator reload unexpectedly joined a live network");
            results.add("PASS D48.persist actual driver saveDataTag/createEnvironment/load/save preserves the live queue, remaining ticks and node identity");
        }

        List<ItemEntity> newDrops() {
            List<ItemEntity> found = new ArrayList<>();
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, bounds()))
                if (!previousDrops.contains(entity.getId()) && !ownedDrops.contains(entity)) { found.add(entity); ownedDrops.add(entity); }
            return found;
        }

        boolean tick() {
            ticks++;
            if (failedAt >= 0) return ticks - failedAt >= 3;
            require(ticks <= 350, "Native generator contract exceeded its bounded stages; checked=" + checked);
            if (!started) {
                if (ticks < 3) return false;
                require(robot.machine().start(), "Actual generator Robot could not boot its native EEPROM: " + robot.machine().lastError()); started = true;
            }
            if (!nativeDone) {
                require(robot.machine().lastError() == null && robot.machine().isRunning(), "Actual native generator BIOS stopped: " + robot.machine().lastError());
                if (prepared == 4) {
                    CompoundTag saved = state(generator); int remaining = saved.getInt("remainingTicks");
                    if (remaining > 0 && queue(saved).isEmpty()) {
                        if (primeState == null) { primeState = saved.copy(); initialBurn = remaining; initialBurnTick = ticks; }
                        else if (ticks > initialBurnTick && remaining < initialBurn) worldBurnObserved = true;
                    }
                }
                return false;
            }
            if (doneAt < 0) {
                require(checked == 16 && primeState != null && queue(primeState).isEmpty(), "Native BIOS skipped inventory or actual world-burn proofs");
                robot.machine().stop();
                loaded = driver.createEnvironment(robot.equipmentInventory().getItem(bay).copy(), (EnvironmentHost) robot.machine().host());
                require(loaded != null, "Actual generator driver could not restore the observed burned state"); loaded.load(primeState, level.registryAccess());
                receiver = new Receiver(); Network.joinNewNetwork(receiver.node()); receiver.node().connect(loaded.node());
                require(receiver.node().network() != robot.machine().node().network(), "Lifecycle energy observation accidentally uses the creative host's auto-filled graph");
                receiver.node().changeBuffer(-Double.MAX_VALUE); doneAt = ticks;
                results.add("PASS D48.native real installed generator Lua callbacks completed 16 inventory stages; real world ticker consumed one priming fuel and decremented actual saved remaining time");
                return false;
            }
            if (energyStep < 5) {
                int before = state(loaded).getInt("remainingTicks"); double energy = receiver.node().globalBuffer();
                if (energyStep == 3) { receiver.node().changeBuffer(Double.MAX_VALUE); energy = receiver.node().globalBuffer(); }
                require(before > 1, "Observed priming state expired before bounded lifecycle checks");
                loaded.update();
                require(state(loaded).getInt("remainingTicks") == before - 1 && queue(state(loaded)).isEmpty(), "Real ManagedEnvironment.update changed the wrong burn timer or refuelled an empty queue");
                double after = receiver.node().globalBuffer(), expected = energyStep < 3 ? energy + efficiency : energy;
                require(Math.abs(after - expected) < 1e-8, "Actual generator connector energy differs from configured per-tick production");
                energyStep++; return false;
            }
            CompoundTag beforeRemoval = state(generator); int unburned = queue(beforeRemoval).getCount();
            require(unburned == 3 && beforeRemoval.getInt("remainingTicks") > 0, "Installed generator lost its unburned queue before real hot removal");
            newDrops(); // Mark any earlier fixture drop before isolating this actual removal.
            ItemStack removed = robot.equipmentInventory().removeItem(bay, 1);
            require(removed.is(item("generatorupgrade").getItem()) && robot.getComponentInSlot(bay) == null && generator.node().network() == null,
                "Physical generator bay removal did not run the real environment disconnect lifecycle");
            CompoundTag detached = state(generator), itemState = driver.dataTag(removed);
            require(queue(detached).isEmpty() && detached.getInt("remainingTicks") == 0 && queue(itemState).isEmpty() && itemState.getInt("remainingTicks") == 0,
                "Actual detached environment or removed item retained spent fuel/time");
            int dropped = 0;
            for (ItemEntity entity : newDrops()) { require(entity.getItem().is(CONTAINER_FUEL.get()), "Hot removal dropped burned fuel or extra containers"); dropped += entity.getItem().getCount(); }
            require(dropped == unburned && count(Items.BUCKET) == 3, "Hot removal failed to drop only unburned fuel or repeated the already-returned containers");
            loaded.node().remove(); loaded = null;
            results.add("PASS D48.lifecycle actual driver-restored environment/public update on an isolated real connector graph produces configured energy, decrements burn time and keeps burning at a full buffer; this is separate from the creative physical Robot graph");
            results.add("PASS D48.hot-remove actual equipment.removeItem drops exactly the three unburned queued fuels, clears timer/queue in real environment and removed item's saved data, and returns no duplicate empty containers");
            results.add("LIMIT D48: C19/C23 shared persistence/inventory infrastructure, cold process restart, other agent types and remote multiplayer are not declared verified by this bounded scenario");
            return true;
        }

        void fail(Throwable error) {
            if (failedAt >= 0) return;
            failedAt = ticks; results.add("FAIL generator: " + error + "; checked=" + checked); LOGGER.error("GENERATOR_CONTRACT FAIL", error);
            if (robot != null) robot.machine().stop();
        }
        void cleanup() {
            if (robot != null) { robot.machine().stop(); robot.mainInventory().clearContent(); }
            if (probe != null) probe.node().remove();
            if (loaded != null && loaded.node().network() != null) loaded.node().remove();
            if (receiver != null) receiver.node().remove();
            var entries = new ArrayList<>(restored.entrySet()); Collections.reverse(entries);
            for (var entry : entries) { if (level.getBlockEntity(entry.getKey()) instanceof Container inventory) inventory.clearContent(); level.setBlock(entry.getKey(), entry.getValue(), 3); }
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, bounds())) if (!previousDrops.contains(entity.getId())) entity.discard();
            for (ItemEntity entity : ownedDrops) entity.discard();
            for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            restored.clear(); forced.clear();
        }
    }

    public static final class Probe extends AbstractManagedEnvironment {
        final Run run;
        Probe(Run run) { this.run = run; setNode(Network.newNode(this, Visibility.Network).withComponent("generator_probe", Visibility.Neighbors).create()); }
        void caller(Context context) { require(context == run.robot.machine() && run.robot.machine().host() instanceof li.cil.oc.api.internal.Robot, "Generator proof did not originate in the actual installed Robot native VM"); }
        @Callback public Object[] prepare(Context context, Arguments args) { caller(context); run.prepare(args.checkInteger(0)); return new Object[]{true}; }
        @Callback public Object[] check(Context context, Arguments args) { caller(context); run.check(args.checkInteger(0)); return new Object[]{true}; }
        @Callback public Object[] primed(Context context, Arguments args) { caller(context); return new Object[]{run.worldBurnObserved}; }
        @Callback public Object[] done(Context context, Arguments args) { caller(context); require(run.checked == 16, "Native generator proof is incomplete"); run.nativeDone = true; return new Object[]{true}; }
    }

    private static final String BIOS = """
        assert(computer.getArchitecture()=="Lua 5.3")
        local p=component.proxy(assert(component.list("generator_probe",true)()))
        local g=component.proxy(assert(component.list("generator",true)()))
        local function prep(i) assert(p.prepare(i)) end
        local function check(i) assert(p.check(i)) end
        local function add(n) local ok,c=g.insert(n) assert(ok and c==n,"insert amount") end
        local function take(n,want) local ok,c=g.remove(n) assert(ok and c==want,"remove amount") end
        prep(1) local a,b=g.insert() assert(a==nil and b=="selected slot is empty") check(1)
        prep(2) a,b=g.insert() assert(a==nil and b=="selected slot does not contain fuel") check(2)
        prep(3) add(0) assert(g.count()==0 and g.remove(0))
          a,b=g.remove(1) assert(a==false and b=="queue is empty") check(3)
        prep(4) add(1) while not p.primed() do computer.pullSignal(0.05) end
          assert(g.count()==0) check(4)
        prep(5) add(5) local n,label=g.count() assert(n==5 and type(label)=="string" and #label>0) check(5)
        prep(6) a,b=g.insert(1) assert(a==nil and b=="different fuel type already queued") check(6)
        prep(7) assert(g.remove(0)) take(2,2) assert(g.count()==3) check(7)
        prep(8) take(99,3) assert(g.count()==0) check(8)
        prep(9) add(4) assert(g.count()==4) check(9)
        prep(10) take(2,2) assert(g.count()==2) check(10)
        prep(11) take(99,2) assert(g.count()==0) check(11)
        prep(12) a,b=g.insert(4) assert(a==false and b=="no space in inventory for fuel containers") check(12)
        prep(13) add(4) assert(g.count()==4) check(13)
        prep(14) take(3,1) assert(g.count()==3) check(14)
        prep(15) a,b=g.remove(2) assert(a==false and b=="no inventory space available for fuel") check(15)
        prep(16) assert(g.count()==3) check(16)
        assert(p.done()) while true do computer.pullSignal(1) end
        """;
}
