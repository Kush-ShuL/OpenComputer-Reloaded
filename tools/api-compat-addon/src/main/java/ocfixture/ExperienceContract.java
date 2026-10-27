package ocfixture;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.API;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.Network;
import li.cil.oc.api.event.RobotAnalyzeEvent;
import li.cil.oc.api.internal.Agent;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded D47 proof using installed real upgrades, native firmware and the real analyzer entrypoint. */
public final class ExperienceContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-experience-contract");
    private static Run active;
    private ExperienceContract() {}

    public static void start(ServerLevel level, BlockPos position, List<String> results) throws Exception {
        require(active == null, "An experience contract is already active");
        Run run = new Run(level, position, results); active = run;
        try { run.start(); }
        catch (Exception | Error error) { active = null; run.cleanup(); throw error; }
    }
    public static boolean isRunning() { return active != null; }
    public static void tick() {
        Run run = active;
        if (run == null) return;
        try { if (!run.tick()) return; }
        catch (Throwable error) { run.fail(error); return; }
        active = null; run.cleanup();
    }
    public static void close() { Run run = active; active = null; if (run != null) run.cleanup(); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static boolean near(double first, double second) { return Math.abs(first - second) <= 1e-7; }
    private static ItemStack item(String key) {
        var descriptor = li.cil.oc.api.Items.get(key);
        require(descriptor != null, "Missing actual experience fixture item " + key);
        ItemStack stack = descriptor.createItemStack(1); require(!stack.isEmpty(), "Empty experience fixture item " + key); return stack;
    }

    private static final class Run {
        final ServerLevel level;
        final BlockPos position;
        final List<String> results;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final Set<Integer> previousItems = new LinkedHashSet<>();
        final List<Unit> units = new ArrayList<>();
        final AnalyzeObserver observer = new AnalyzeObserver(this);
        CapturePlayer capture;
        boolean observing;
        int ticks, failedAt = -1, completedAt = -1;
        final double base = Math.max(0, API.config.getDouble("robot.xp.baseValue"));
        final double growth = Math.max(1, API.config.getDouble("robot.xp.constantGrowth"));
        final double exponent = Math.max(1, API.config.getDouble("robot.xp.exponentialGrowth"));
        final double bufferPerLevel = Math.max(0, API.config.getDouble("robot.xp.bufferPerLevel"));

        Run(ServerLevel level, BlockPos position, List<String> results) { this.level = level; this.position = position; this.results = results; }
        // These are the original PIN util/UpgradeExperience.scala formulas, not production helpers.
        double threshold(int level) { return level == 0 ? 0 : base + Math.pow(level * growth, exponent); }
        int integerLevel(double xp) { return Math.min(30, (int) (Math.pow(xp - base, 1 / exponent) / growth)); }
        double reportedLevel(double xp) { int level = integerLevel(xp); return level + Math.max(0, xp - threshold(level)) / (threshold(level + 1) - threshold(level)); }
        double seed(boolean maximum) {
            return maximum ? threshold(30) + Math.max(.0001, Math.ulp(threshold(30)) * 4)
                    : threshold(5) + (threshold(6) - threshold(5)) * .25;
        }
        void remember(BlockPos pos) {
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true)) forced.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            level.getChunkAt(pos);
            require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "Experience fixture will not overwrite occupied cell " + pos);
            restored.put(pos.immutable(), level.getBlockState(pos));
        }
        void start() {
            require(bufferPerLevel > 0 && !API.config.getBoolean("power.ignorePower"), "Use normal positive XP buffer capacity and enabled power accounting for the saved-energy regression");
            require(BIOS.getBytes(StandardCharsets.UTF_8).length < 4096, "Experience BIOS exceeds real EEPROM capacity");
            require(CreativeTab.instance != null, "Actual configured creative robots are unavailable");
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
            ItemStack preset = null;
            for (ItemStack stack : CreativeTab.instance.getDisplayItems()) {
                if (stack.is(item("robot").getItem()) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("oc:tier") == 3) { preset = stack.copy(); break; }
            }
            require(preset != null && preset.getItem() instanceof BlockItem, "Creative tab supplied no configured Robot item");
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, bounds())) previousItems.add(entity.getId());
            capture = new CapturePlayer(level);
            NeoForge.EVENT_BUS.register(observer); observing = true;
            Unit ordinary = new Unit(this, position, false), maximum = new Unit(this, position.east(8), true);
            units.add(ordinary); units.add(maximum);
            ordinary.place(configure(preset, false)); maximum.place(configure(preset, true));
            require(ordinary.robot.machine().node().network() != maximum.robot.machine().node().network(), "The two real XP robots unexpectedly share a component network");
        }
        ItemStack configure(ItemStack preset, boolean maximum) {
            ItemStack result = preset.copy(); CompoundTag data = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            ListTag original = data.getList("oc:components", Tag.TAG_COMPOUND), parts = new ListTag();
            var architecture = li.cil.oc.api.Machine.architectures().stream().filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type)))
                    .findFirst().orElseThrow(() -> new AssertionError("Native Lua 5.3 is unavailable"));
            int cpu = 0, flash = 0, upgrades = 0;
            for (Tag value : original) {
                ItemStack part = ItemStack.parseOptional(level.registryAccess(), (CompoundTag) value);
                var driver = Driver.driverFor(part);
                if (driver instanceof MutableProcessor processor && li.cil.oc.api.driver.item.Slot.CPU.equals(driver.slot(part))) { processor.setArchitecture(part, architecture); cpu++; }
                if (driver != null && "eeprom".equals(driver.slot(part))) { part = li.cil.oc.api.Items.registerEEPROM("Native experience fixture", BIOS.getBytes(StandardCharsets.UTF_8), new byte[0], false); flash++; }
                if (part.is(item("experienceupgrade").getItem())) {
                    require(driver != null, "Original experience item has no actual public driver");
                    // oc:xp is the original saved upgrade's input, inside the driver's normal item data.
                    CompoundTag saved = driver.dataTag(part); saved.putDouble("oc:xp", seed(maximum)); driver.saveDataTag(part, saved); upgrades++;
                }
                parts.add(part.saveOptional(level.registryAccess()));
            }
            require(cpu == 1 && flash == 1 && upgrades == 1 && parts.size() == original.size(), "Configured robot hardware changed beyond CPU architecture, EEPROM and the original XP item data");
            data.put("oc:components", parts); result.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); return result;
        }
        boolean tick() {
            ticks++;
            if (failedAt >= 0) return ticks - failedAt >= 3;
            require(ticks <= 800, "Native experience contract exceeded 800 ticks; " + status());
            for (Unit unit : units) {
                require(unit.robot.machine().lastError() == null, "Actual native XP BIOS failed: " + unit.robot.machine().lastError() + "; " + status());
                if (!unit.started) { require(unit.robot.machine().start(), "Actual XP robot did not start its native BIOS"); unit.started = true; }
            }
            if (units.stream().allMatch(unit -> unit.done)) {
                if (completedAt < 0) {
                    completedAt = ticks;
                    results.add("PASS D47.native actual Lua 5.3 robots executed experience level/consume for ordinary and max-level saved-item inputs; no Java stub Context or VM state injection");
                    results.add("LIMIT D47: C19 multiplayer and C23 on-screen UI acceptance remain unverified; analyzer message components and item tooltip were exercised headlessly through their actual server/item entrypoints");
                }
                return ticks - completedAt >= 3;
            }
            return false;
        }
        void analyze(Unit unit) throws Exception {
            capture.messages.clear(); observer.events = 0; observer.agent = null;
            Object result;
            try {
                result = Class.forName("li.cil.oc.common.item.AnalyzerItem").getMethod("analyze", Object.class, Player.class, Direction.class, float.class, float.class, float.class)
                        .invoke(null, unit.robot, capture, Direction.UP, 0f, 0f, 0f);
            } catch (InvocationTargetException error) { throw new IllegalStateException("Actual analyzer entrypoint failed", error.getCause()); }
            require(Boolean.TRUE.equals(result) && observer.events == 1 && observer.agent == unit.robot.machine().host(), "One actual analyzer call must emit one RobotAnalyzeEvent for the persistent machine host");
            List<TranslatableContents> messages = capture.messages.stream().map(Component::getContents).filter(TranslatableContents.class::isInstance)
                    .map(TranslatableContents.class::cast).filter(contents -> "oc:gui.Analyzer.RobotXp".equals(contents.getKey())).toList();
            require(messages.size() == 1, "Ordinary robot analysis did not send exactly one real XP message");
            Object[] args = messages.getFirst().getArgs(); double xp = unit.savedXp();
            require(args.length == 2 && String.format(Locale.ROOT, "%.2f", xp).equals(args[0]) && Integer.toString(integerLevel(xp)).equals(args[1]), "Actual analyzer message lost two-decimal experience or integer level formatting");
            results.add("PASS D47.analyzer actual AnalyzerItem.analyze emits exactly one persistent-host RobotAnalyzeEvent and one real translated XP message: " + args[0] + " / integer level " + args[1]);
        }
        String status() { return units.stream().map(unit -> (unit.maximum ? "maximum" : "ordinary") + ":step=" + unit.checked + ",error=" + unit.robot.machine().lastError()).toList().toString(); }
        void fail(Throwable error) {
            if (failedAt >= 0) return;
            failedAt = ticks; results.add("FAIL experience: " + error + "; " + status()); LOGGER.error("EXPERIENCE_CONTRACT FAIL {}; deferred cleanup", status(), error);
            for (Unit unit : units) if (unit.robot != null) unit.robot.machine().stop();
        }
        AABB bounds() { return new AABB(position).inflate(4).expandTowards(12, 4, 4); }
        void cleanup() {
            if (observing) { NeoForge.EVENT_BUS.unregister(observer); observing = false; }
            for (Unit unit : units) { if (unit.robot != null) unit.robot.machine().stop(); if (unit.probe != null) unit.probe.node().remove(); }
            var entries = new ArrayList<>(restored.entrySet()); Collections.reverse(entries);
            for (var entry : entries) {
                if (level.getBlockEntity(entry.getKey()) instanceof Container inventory) inventory.clearContent();
                level.setBlock(entry.getKey(), entry.getValue(), 3);
            }
            restored.clear();
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, bounds())) if (!previousItems.contains(entity.getId())) entity.discard();
            for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            forced.clear();
        }
    }

    private static final class Unit {
        final Run run;
        final BlockPos position;
        final boolean maximum;
        Robot robot;
        li.cil.oc.api.network.Component experience;
        ManagedEnvironment environment;
        Probe probe;
        boolean started, done;
        int checked;
        double beforeXp, toolCost;

        Unit(Run run, BlockPos position, boolean maximum) { this.run = run; this.position = position; this.maximum = maximum; }
        void place(ItemStack stack) {
            run.remember(position);
            FakePlayer owner = new FakePlayer(run.level, new GameProfile(UUID.randomUUID(), maximum ? "OC_XpMaximum" : "OC_XpOrdinary"));
            owner.getAbilities().instabuild = true; owner.setPos(position.getX() + .5, position.getY(), position.getZ() - 2); owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
            require(((BlockItem) stack.getItem()).place(new BlockPlaceContext(owner, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false))).consumesAction(), "Real XP robot failed BlockItem.place");
            require(run.level.getBlockEntity(position) instanceof Robot, "Configured XP item did not place a real SDK Robot");
            robot = (Robot) run.level.getBlockEntity(position);
            require("Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(robot.machine().architecture().getClass())), "Actual robot CPU did not select native Lua 5.3");
            for (Node node : robot.machine().node().network().nodes()) {
                if (node instanceof li.cil.oc.api.network.Component component && "experience".equals(component.name())) { require(experience == null, "Preset installed duplicate XP upgrades"); experience = component; }
            }
            require(experience != null && experience.host() instanceof ManagedEnvironment && experience instanceof Connector, "Actual installed experience upgrade is not a managed connector component");
            environment = (ManagedEnvironment) experience.host();
            require(near(savedXp(), run.seed(maximum)), "Actual installed XP upgrade did not load its original oc:xp saved-item input");
            probe = new Probe(this); Network.joinNewNetwork(probe.node()); probe.node().connect(robot.machine().node());
        }
        CompoundTag saved() { CompoundTag data = new CompoundTag(); environment.save(data, run.level.registryAccess()); return data; }
        double savedXp() { return saved().getDouble("oc:xp"); }
        void prepare(int step) {
            require(run.level.getServer().isSameThread(), "Experience fixture hardware preparation left the server thread");
            require(maximum ? step == 3 : step == checked + 1, "Unexpected native XP preparation stage");
            beforeXp = savedXp();
            if (step == 1 || step == 3) robot.mainInventory().setItem(0, new ItemStack(Items.EXPERIENCE_BOTTLE, 2));
            else if (step == 2) {
                var sharpness = run.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
                ItemStack sword = new ItemStack(Items.IRON_SWORD); sword.enchant(sharpness, 1);
                require(sword.isEnchanted(), "Real registry-holder enchantment did not produce an enchanted tool");
                toolCost = sharpness.value().getMinCost(1) * run.growth; require(toolCost > 0, "Actual enchantment min-cost gave no extractable XP");
                robot.mainInventory().setItem(0, sword);
            } else throw new AssertionError("Unexpected XP preparation step " + step);
        }
        void check(Context context, int step, double nativeLevel) throws Exception {
            require(maximum ? step == 3 && checked == 0 : step == checked + 1, "Native XP checks skipped or repeated");
            double xp = savedXp(); require(near(nativeLevel, run.reportedLevel(xp)), "Actual native level callback differs from the PIN fractional-level formula");
            if (step == 1) {
                ItemStack remaining = robot.mainInventory().getItem(0);
                require(remaining.is(Items.EXPERIENCE_BOTTLE) && remaining.getCount() == 1, "Native consume did not remove exactly one experience bottle");
                double bottles = (xp - beforeXp) / run.growth;
                require(bottles >= 3 - 1e-7 && bottles <= 11 + 1e-7 && near(bottles, Math.rint(bottles)) && nativeLevel > run.reportedLevel(beforeXp), "Real bottle did not award its 3..11 scaled XP or grow the native fractional level");
                require(noGlass(), "Experience consume returned a glass bottle absent from the pinned behavior");
                run.results.add("PASS D47.bottle actual native consume removes exactly one bottle and awards " + bottles + " times configured XP growth; real fractional level increased");
            } else if (step == 2) {
                require(robot.mainInventory().getItem(0).isEmpty() && near(xp - beforeXp, toolCost), "Native consume did not destroy the entire genuinely enchanted sword or award its real enchantment min-cost");
                require(noGlass(), "Consumed enchanted item unexpectedly returned a container");
                run.results.add("PASS D47.enchanted actual registry-enchanted iron sword is consumed whole with its real min-cost XP; no disenchant-in-place or container remainder");
                persistenceAndTooltip(context, nativeLevel);
                run.analyze(this);
            } else if (step == 3) {
                ItemStack untouched = robot.mainInventory().getItem(0);
                require(nativeLevel >= 30 && near(xp, beforeXp) && untouched.is(Items.EXPERIENCE_BOTTLE) && untouched.getCount() == 2, "Max-level native refusal changed XP or consumed the selected item");
                run.results.add("PASS D47.maximum actual native consume returns nil/max level and preserves the two selected bottles and original saved XP");
            } else throw new AssertionError("Unexpected XP proof step " + step);
            checked = step;
        }
        boolean noGlass() {
            for (int index = 0; index < robot.mainInventory().getContainerSize(); index++) if (robot.mainInventory().getItem(index).is(Items.GLASS_BOTTLE)) return false;
            return true;
        }
        void persistenceAndTooltip(Context context, double nativeLevel) throws Exception {
            Connector connector = (Connector) experience;
            double energy = Math.min(1234.5, connector.localBufferSize() / 2);
            require(energy > 0, "Actual experience upgrade has no positive level-derived energy capacity");
            connector.changeBuffer(energy - connector.localBuffer());
            require(near(connector.localBuffer(), energy), "Public Connector could not establish real stored upgrade energy");
            CompoundTag oldItemData = saved(); double xp = oldItemData.getDouble("oc:xp");
            ItemStack copy = item("experienceupgrade"); EnvironmentHost host = (EnvironmentHost) robot.machine().host();
            var driver = Driver.driverFor(copy, host.getClass()); require(driver != null, "Real experience item has no public host-aware driver");
            driver.saveDataTag(copy, oldItemData);
            ManagedEnvironment restored = driver.createEnvironment(copy, host);
            require(restored != null && restored.node() instanceof Connector && restored.node() instanceof li.cil.oc.api.network.Component, "Actual XP driver did not create a managed connector component");
            try {
                // The input is an actual saved item's node.buffer + oc:xp. Loading must not clip
                // that energy to an initial zero capacity before its saved XP restores the level.
                restored.load(driver.dataTag(copy), run.level.registryAccess());
                Connector loaded = (Connector) restored.node(); CompoundTag again = new CompoundTag(); restored.save(again, run.level.registryAccess());
                require(near(again.getDouble("oc:xp"), xp) && experience.address().equals(restored.node().address())
                        && near(loaded.localBuffer(), energy) && near(loaded.localBufferSize(), run.integerLevel(xp) * run.bufferPerLevel), "Fresh XP driver environment lost saved XP/node identity/local energy during registry-aware load");
                Object[] level = ((li.cil.oc.api.network.Component) restored.node()).invoke("level", context);
                require(level != null && level.length == 1 && level[0] instanceof Number && near(((Number) level[0]).doubleValue(), nativeLevel), "Freshly restored actual XP callback lost the fractional level");
                List<Component> tooltip = new ArrayList<>();
                copy.getItem().appendHoverText(copy, Item.TooltipContext.of(run.level), tooltip, TooltipFlag.NORMAL);
                List<TranslatableContents> levels = tooltip.stream().map(Component::getContents).filter(TranslatableContents.class::isInstance)
                        .map(TranslatableContents.class::cast).filter(contents -> "oc:tooltip.robot_level".equals(contents.getKey())).toList();
                require(levels.size() == 1 && levels.getFirst().getArgs().length == 1, "Real upgrade item did not append exactly one translated level tooltip");
                double reported = Double.parseDouble(levels.getFirst().getArgs()[0].toString());
                require(near(reported, nativeLevel) && !near(reported, Math.floor(reported)), "Real FunctionalUpgradeItem tooltip truncated its native fractional level to an integer");
                run.results.add("PASS D47.saved actual driver recreates the saved upgrade with XP/fractional level/address and " + energy + " local energy intact; actual headless item tooltip matches native level " + nativeLevel);
            } finally { restored.node().remove(); }
        }
    }

    /** Captures the actual common listener's real Component without a Minecraft connection/client. */
    public static final class CapturePlayer extends FakePlayer {
        final List<Component> messages = new ArrayList<>();
        CapturePlayer(ServerLevel level) { super(level, new GameProfile(UUID.randomUUID(), "OC_XpAnalyzer")); }
        @Override public void sendSystemMessage(Component message) { if (messages != null) messages.add(message.copy()); }
    }
    public static final class AnalyzeObserver {
        private final Run run;
        int events;
        Agent agent;
        AnalyzeObserver(Run run) { this.run = run; }
        @SubscribeEvent public void analyzed(RobotAnalyzeEvent event) { if (event.player == run.capture) { events++; agent = event.agent; } }
    }
    public static final class Probe extends AbstractManagedEnvironment {
        private final Unit unit;
        Probe(Unit unit) { this.unit = unit; setNode(Network.newNode(this, Visibility.Network).withComponent("experience_fixture", Visibility.Neighbors).create()); }
        private void caller(Context context) { require(context instanceof Machine machine && machine == unit.robot.machine() && machine.host() instanceof Robot, "Experience proof did not originate in its actual native robot VM"); }
        @Callback public Object[] target(Context context, Arguments args) { caller(context); return new Object[]{unit.experience.address(), unit.maximum}; }
        @Callback public Object[] prepare(Context context, Arguments args) { caller(context); unit.prepare(args.checkInteger(0)); return new Object[]{true}; }
        @Callback public Object[] check(Context context, Arguments args) throws Exception { caller(context); unit.check(context, args.checkInteger(0), args.checkDouble(1)); return new Object[]{true}; }
        @Callback public Object[] done(Context context, Arguments args) { caller(context); require(!unit.done && unit.checked == (unit.maximum ? 3 : 2), "Native XP proof finished before its bounded checks"); unit.done = true; return new Object[]{true}; }
    }

    private static final String BIOS = """
        assert(computer.getArchitecture()=="Lua 5.3")
        local p=component.proxy(assert(component.list("experience_fixture",true)()))
        local r=component.proxy(assert(component.list("robot",true)()))
        local address,maximum=p.target()
        local e=component.proxy(address)
        assert(r.select(1))
        if maximum then
          assert(p.prepare(3)) local before=e.level() assert(before>=30)
          local ok,message=e.consume() assert(ok==nil and message=="max level")
          assert(e.level()==before and p.check(3,e.level()))
        else
          assert(p.prepare(1)) local before=e.level()
          assert(e.consume()) assert(e.level()>before and p.check(1,e.level()))
          assert(p.prepare(2)) before=e.level()
          assert(e.consume()) assert(e.level()>before and p.check(2,e.level()))
        end
        assert(p.done()) computer.shutdown()
        """;
}
