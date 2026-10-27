package ocfixture;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.API;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.IFluidTank;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** D56/D57 callbacks and signals executed by two real, independently networked native robots. */
public final class AgentFluidContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-agent-fluid");
    private static Run active;
    private AgentFluidContract() {}

    public static void start(ServerLevel level, BlockPos position, List<String> results) throws Exception {
        require(active == null, "An agent fluid contract is already active");
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
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static ItemStack item(String name) {
        var descriptor = li.cil.oc.api.Items.get(name);
        require(descriptor != null, "Missing real fluid fixture hardware " + name);
        ItemStack result = descriptor.createItemStack(1);
        require(!result.isEmpty(), "Empty fluid fixture hardware " + name); return result;
    }

    private static final class Run {
        final ServerLevel level;
        final BlockPos position;
        final List<String> results;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final Set<Integer> previousItems = new LinkedHashSet<>();
        final List<Unit> units = new ArrayList<>();
        int ticks, failedAt = -1, completedAt = -1;

        Run(ServerLevel level, BlockPos position, List<String> results) { this.level = level; this.position = position; this.results = results; }
        void force(BlockPos pos) {
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true)) forced.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            level.getChunkAt(pos);
        }
        void rememberEmpty(BlockPos pos) {
            if (restored.containsKey(pos)) return;
            force(pos);
            require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "Fluid fixture will not overwrite occupied cell " + pos);
            restored.put(pos.immutable(), level.getBlockState(pos));
        }
        void place(BlockPos pos, BlockState state) { rememberEmpty(pos); require(level.setBlock(pos, state, 3), "Could not place fluid fixture block at " + pos); }
        void start() throws Exception {
            require(API.config != null && API.config.getBoolean("misc.allowItemStackInspection"), "Use the normal enabled item-stack-inspection setting for the tank-controller contract");
            require(BIOS.getBytes(StandardCharsets.UTF_8).length < 4096, "Fluid BIOS exceeds the real EEPROM code capacity");
            require(CreativeTab.instance != null, "Real configured creative robot is unavailable");
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
            ItemStack preset = null;
            for (ItemStack stack : CreativeTab.instance.getDisplayItems()) {
                if (stack.is(item("robot").getItem()) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("oc:tier") == 3) { preset = stack.copy(); break; }
            }
            require(preset != null && preset.getItem() instanceof BlockItem, "Creative tab supplied no actual configured robot");
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, bounds())) previousItems.add(entity.getId());
            Unit withTanks = new Unit(this, position, false);
            Unit withoutTanks = new Unit(this, position.east(8), true);
            units.add(withTanks); units.add(withoutTanks);
            withTanks.place(configure(preset, false)); withoutTanks.place(configure(preset, true));
            withTanks.prepareWorldCell();
            require(withTanks.robot.machine().node().network() != withoutTanks.robot.machine().node().network(), "The two real robots unexpectedly share a component network");
        }
        ItemStack configure(ItemStack preset, boolean noTank) {
            ItemStack configured = preset.copy();
            CompoundTag data = configured.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            ListTag original = data.getList("oc:components", Tag.TAG_COMPOUND), changed = new ListTag();
            var architecture = li.cil.oc.api.Machine.architectures().stream().filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type)))
                    .findFirst().orElseThrow(() -> new AssertionError("Actual native Lua 5.3 architecture is unavailable"));
            int tanks = 0, hover = 0, cpus = 0, eeproms = 0;
            for (Tag tag : original) {
                ItemStack part = ItemStack.parseOptional(level.registryAccess(), (CompoundTag) tag);
                if (part.is(item("tankupgrade").getItem())) { tanks++; if (noTank) continue; }
                if (part.is(item("hoverupgrade2").getItem())) { hover++; if (!noTank) part = item("tankupgrade"); }
                var driver = Driver.driverFor(part);
                if (driver instanceof MutableProcessor processor && li.cil.oc.api.driver.item.Slot.CPU.equals(driver.slot(part))) { processor.setArchitecture(part, architecture); cpus++; }
                if (driver != null && "eeprom".equals(driver.slot(part))) { part = li.cil.oc.api.Items.registerEEPROM("Native agent fluid fixture", BIOS.getBytes(StandardCharsets.UTF_8), new byte[0], false); eeproms++; }
                changed.add(part.saveOptional(level.registryAccess()));
            }
            require(tanks == 1 && hover == 1 && cpus == 1 && eeproms == 1, "The creative preset no longer matches the bounded one-replacement fluid setup");
            require(changed.size() == original.size() - (noTank ? 1 : 0), "Fluid fixture added hardware slots instead of replacing/removing a preset component");
            data.put("oc:components", changed); configured.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); return configured;
        }
        boolean tick() {
            ticks++;
            if (failedAt >= 0) return ticks - failedAt >= 3;
            require(ticks <= 800, "Native robot fluid contract exceeded 800 ticks; " + status());
            for (Unit unit : units) {
                require(unit.robot.machine().lastError() == null, "Native robot fluid BIOS failed: " + unit.robot.machine().lastError() + "; " + status());
                if (!unit.started) {
                    require(unit.robot.machine().node().network() != null, "Real robot has no initialized component network");
                    require(unit.probe.node().network() == unit.robot.machine().node().network(), "Real robot lost its dedicated SDK probe network");
                    require(unit.robot.machine().start(), "Real fluid robot could not boot its actual native EEPROM"); unit.started = true;
                }
            }
            if (units.stream().allMatch(unit -> unit.done)) {
                if (completedAt < 0) {
                    completedAt = ticks;
                    results.add("PASS D56/D57.native real Lua 5.3 robots completed tank/controller callbacks and exact tank_changed index/delta checks without Java callback stand-ins");
                    results.add("LIMIT D56/D57: multiplayer, other hosts, mod-specific containers and multi-tank external-mod traversal remain unverified; D15/D36 are not declared complete");
                }
                return ticks - completedAt >= 3;
            }
            return false;
        }
        String status() { return units.stream().map(unit -> (unit.noTank ? "no-tank" : "two-tank") + " step=" + unit.checked + ",running=" + unit.robot.machine().isRunning() + ",error=" + unit.robot.machine().lastError()).toList().toString(); }
        void fail(Throwable error) {
            if (failedAt >= 0) return;
            failedAt = ticks; results.add("FAIL agent.fluid: " + error + "; " + status()); LOGGER.error("AGENT_FLUID FAIL {}; defer cleanup to the main tick", status(), error);
            for (Unit unit : units) if (unit.robot != null) unit.robot.machine().stop();
        }
        AABB bounds() { return new AABB(position).inflate(4).expandTowards(12, 4, 4); }
        void cleanup() {
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
        final boolean noTank;
        Robot robot;
        Probe probe;
        BlockPos source;
        boolean started, done;
        int checked;
        Unit(Run run, BlockPos position, boolean noTank) { this.run = run; this.position = position; this.noTank = noTank; }
        void place(ItemStack configured) {
            run.rememberEmpty(position);
            FakePlayer actor = new FakePlayer(run.level, new GameProfile(UUID.randomUUID(), noTank ? "OC_FluidNone" : "OC_FluidTwo"));
            actor.getAbilities().instabuild = true; actor.setPos(position.getX() + .5, position.getY(), position.getZ() - 2); actor.setItemInHand(InteractionHand.MAIN_HAND, configured);
            require(((BlockItem) configured.getItem()).place(new BlockPlaceContext(actor, InteractionHand.MAIN_HAND, configured, new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false))).consumesAction(), "Actual configured robot failed BlockItem.place");
            require(run.level.getBlockEntity(position) instanceof Robot, "Placed fluid fixture is not a real SDK Robot");
            robot = (Robot) run.level.getBlockEntity(position);
            require(robot.machine().host() instanceof Robot && robot.tank().tankCount() == (noTank ? 0 : 2), "Real robot did not create exactly its selected tank hardware");
            require("Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(robot.machine().architecture().getClass())), "Real robot did not select native Lua 5.3");
            probe = new Probe(this); Network.joinNewNetwork(probe.node()); probe.node().connect(robot.machine().node());
            require(component("robot") != null && component("tank_controller") != null, "Actual robot/controller components were not installed by the preset");
        }
        Component component(String name) {
            for (Node node : robot.machine().node().network().nodes()) if (node instanceof Component component && name.equals(component.name())) return component;
            return null;
        }
        void prepareWorldCell() {
            source = position.relative(robot.toGlobal(Direction.SOUTH));
            run.rememberEmpty(source);
            run.place(source.below(), Blocks.BEDROCK.defaultBlockState());
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos wall = source.relative(side);
                if (!wall.equals(position)) run.place(wall, Blocks.BEDROCK.defaultBlockState());
            }
        }
        IFluidTank tank(int index) { return robot.tank().getFluidTank(index); }
        void exact(int index, net.minecraft.world.level.material.Fluid fluid, int amount) {
            require(tank(index).getFluidAmount() == amount && (amount == 0 ? tank(index).getFluid().isEmpty() : tank(index).getFluid().is(fluid)), "Actual tank " + (index + 1) + " contents/resource mismatch");
        }
        void prepare(int step) {
            require(!noTank && run.level.getServer().isSameThread(), "Fluid preparation must use the actual two-tank robot on the server thread");
            if (step == 1) {
                require(robot.tank().tankCount() == 2, "Actual preset did not expose two independent tanks");
                for (int index = 0; index < 2; index++) {
                    IFluidTank tank = tank(index);
                    require(tank.getCapacity() == 16000 && tank.getFluidAmount() == 0 && tank instanceof ManagedEnvironment && tank instanceof DeviceInfo, "Actual tank upgrade lost capacity, empty initial state or SDK environment/device info");
                    var info = ((DeviceInfo) tank).getDeviceInfo();
                    require("16000".equals(info.get("capacity")) && "Superblubb V10".equals(info.get("product")), "Tank device metadata differs from the original");
                }
                FluidStack water = new FluidStack(Fluids.WATER, 1234); CompoundTag payload = new CompoundTag(); payload.putString("agent_fluid_fixture", "registered-data-components"); water.set(DataComponents.CUSTOM_DATA, CustomData.of(payload));
                require(tank(0).fill(water, FluidAction.SIMULATE) == 1234 && tank(0).getFluidAmount() == 0, "SIMULATE fill mutated the real tank");
                require(tank(0).fill(water, FluidAction.EXECUTE) == 1234, "Real tank could not execute its fluid fill");
                require(tank(0).drain(234, FluidAction.SIMULATE).getAmount() == 234 && tank(0).getFluidAmount() == 1234, "SIMULATE drain mutated the real tank");
                require(tank(0).drain(234, FluidAction.EXECUTE).getAmount() == 234, "Real tank failed its executed drain");
                persistence();
            } else if (step == 2) {
                tank(0).drain(16000, FluidAction.EXECUTE); tank(1).drain(16000, FluidAction.EXECUTE);
                require(tank(0).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000 && tank(1).fill(new FluidStack(Fluids.LAVA, 2000), FluidAction.EXECUTE) == 2000, "Could not seed real incompatible tank contents");
            } else if (step == 3) {
                exact(0, Fluids.LAVA, 2000); exact(1, Fluids.WATER, 1000);
                tank(0).drain(16000, FluidAction.EXECUTE);
                require(robot.mainInventory().getContainerSize() > 0, "Real robot has no cargo slot for its bucket");
                robot.setSelectedSlot(0); robot.mainInventory().setItem(0, new ItemStack(Items.BUCKET));
            } else if (step == 4) {
                exact(0, Fluids.EMPTY, 0); exact(1, Fluids.WATER, 1000);
                require(run.level.getBlockState(source).isAir(), "Bounded world source cell is no longer empty");
                require(run.level.setBlock(source, Blocks.WATER.defaultBlockState(), 3), "Could not place the actual source-water block");
            } else throw new AssertionError("Unexpected fluid preparation step " + step);
        }
        void persistence() {
            ManagedEnvironment original = (ManagedEnvironment) tank(0);
            CompoundTag state = new CompoundTag(); original.save(state, run.level.registryAccess());
            String address = original.node().address(); require(address != null && !address.isEmpty(), "Real tank node has no saved address");
            ItemStack stack = item("tankupgrade"); EnvironmentHost host = (EnvironmentHost) robot.machine().host();
            var driver = Driver.driverFor(stack, host.getClass()); require(driver != null, "Real tank item has no public driver");
            ManagedEnvironment restored = driver.createEnvironment(stack, host);
            require(restored instanceof IFluidTank, "Actual tank driver did not recreate a fluid environment");
            try {
                restored.load(state, run.level.registryAccess());
                IFluidTank loaded = (IFluidTank) restored;
                require(address.equals(restored.node().address()) && loaded.getCapacity() == 16000 && FluidStack.matches(tank(0).getFluid(), loaded.getFluid()), "Tank driver/registry round trip lost its node, amount, fluid or components");
                require("registered-data-components".equals(loaded.getFluid().get(DataComponents.CUSTOM_DATA).copyTag().getString("agent_fluid_fixture")), "Registry-aware fluid data-component payload was lost");
            } finally { restored.node().remove(); }
        }
        void check(int step) {
            require(step == checked + 1, "Native robot fluid proof steps skipped or repeated");
            checked = step;
            if (step == 1) {
                exact(0, Fluids.WATER, 1000); exact(1, Fluids.EMPTY, 0);
                run.results.add("PASS D56.storage two actual 16000mB tank environments preserve fluid components/node through public-driver registry save/load; native signal checks prove SIM emits none and EXEC emits one-based positive/negative deltas");
            } else if (step == 2) {
                exact(0, Fluids.LAVA, 2000); exact(1, Fluids.WATER, 1000);
                run.results.add("PASS D56.transfer native robot callbacks exchange the full incompatible contents and accept same-target/zero no-ops without fluid loss or extra tank_changed");
            } else if (step == 3) {
                exact(0, Fluids.EMPTY, 0); exact(1, Fluids.WATER, 1000);
                require(robot.mainInventory().getItem(0).is(Items.BUCKET) && robot.mainInventory().getItem(0).getCount() == 1, "Native controller bucket round trip lost its actual empty container");
                run.results.add("PASS D57.bucket native tank-controller fill/drain transfers exactly one bucket both ways; real inventory/fluid state conserve 1000mB and explicit item-slot nil is rejected");
            } else if (step == 4) {
                exact(0, Fluids.WATER, 1000); exact(1, Fluids.WATER, 1000);
                require(run.level.getFluidState(source).isEmpty() && waterTotal() == 2000, "Native robot world drain lost source water or duplicated resources");
            } else if (step == 5 || step == 6) {
                exact(0, Fluids.EMPTY, 0); exact(1, Fluids.WATER, 1000);
                require(run.level.getFluidState(source).isSource() && run.level.getFluidState(source).is(Fluids.WATER) && waterTotal() == 2000, "Native robot world placement/refusal changed the two-bucket resource total");
                if (step == 6) run.results.add("PASS D56/D57.world native robot drains a real source and places it back, refuses filling an already-full source without consumption, accepts omitted external tank and rejects explicit nil through robot/controller callbacks");
            } else throw new AssertionError("Unexpected fluid proof step " + step);
        }
        int waterTotal() {
            int amount = 0;
            for (int index = 0; index < robot.tank().tankCount(); index++) if (tank(index).getFluid().is(Fluids.WATER)) amount += tank(index).getFluidAmount();
            amount += FluidUtil.getFluidContained(robot.mainInventory().getItem(0)).filter(fluid -> fluid.is(Fluids.WATER)).map(FluidStack::getAmount).orElse(0);
            if (run.level.getFluidState(source).isSource() && run.level.getFluidState(source).is(Fluids.WATER)) amount += 1000;
            return amount;
        }
    }

    public static final class Probe extends AbstractManagedEnvironment {
        private final Unit unit;
        Probe(Unit unit) { this.unit = unit; setNode(Network.newNode(this, Visibility.Network).withComponent("agent_fluid_probe", Visibility.Neighbors).create()); }
        private void caller(Context context) { require(context instanceof Machine machine && machine == unit.robot.machine() && machine.host() instanceof Robot, "Fluid proof did not originate in the actual placed robot VM"); }
        @Callback public Object[] mode(Context context, Arguments args) { caller(context); return new Object[]{unit.noTank ? 0 : 1}; }
        @Callback public Object[] address(Context context, Arguments args) { caller(context); return new Object[]{((ManagedEnvironment) unit.tank(args.checkInteger(0) - 1)).node().address()}; }
        @Callback public Object[] prepare(Context context, Arguments args) { caller(context); unit.prepare(args.checkInteger(0)); return new Object[]{true}; }
        @Callback public Object[] check(Context context, Arguments args) { caller(context); unit.check(args.checkInteger(0)); return new Object[]{true}; }
        @Callback public Object[] buckets(Context context, Arguments args) {
            caller(context);
            require(!unit.noTank && unit.checked == 3, "Stacked bucket check ran outside its actual controller stage");
            if (args.checkBoolean(0)) unit.robot.mainInventory().setItem(0, new ItemStack(Items.BUCKET, 2));
            else {
                require(unit.robot.mainInventory().getItem(0).is(Items.BUCKET) && unit.robot.mainInventory().getItem(0).getCount() == 2,
                    "Rejected stacked-bucket operation changed its actual containers");
                unit.exact(1, Fluids.WATER, 1000);
                unit.run.results.add("PASS D57.stack real two-bucket stack is refused by the standard item capability without losing buckets/fluid; all ten public controller callback families ran in native Lua");
                unit.robot.mainInventory().setItem(0, new ItemStack(Items.BUCKET));
            }
            return new Object[]{true};
        }
        @Callback public Object[] done(Context context, Arguments args) {
            caller(context); require(!unit.done && (unit.noTank || unit.checked == 6), "Native fluid proof completed before its actual callback checks");
            if (unit.noTank) unit.run.results.add("PASS D56/D57.no-tank actual native robot without tank hardware returns zero/default selection/nil contents and graceful transfer errors without NullPointerException");
            unit.done = true; return new Object[]{true};
        }
    }

    private static final String BIOS = """
        assert(computer.getArchitecture()=="Lua 5.3")
        local p=component.proxy(assert(component.list("agent_fluid_probe",true)()))
        local r=component.proxy(assert(component.list("robot",true)()))
        local t=component.proxy(assert(component.list("tank_controller",true)()))
        local function ev(want)
          local got={}
          while true do local n,a,i,d=computer.pullSignal(0) if not n then break end
            if n=="tank_changed" then assert(a==p.address(i),"tank_changed source") got[#got+1]={i,d} end
          end
          if want then assert(#got==#want,"tank_changed count")
            for i,v in ipairs(want) do assert(got[i][1]==v[1] and got[i][2]==v[2],"tank_changed index/delta") end
          end
        end
        if p.mode()==0 then
          assert(r.tankCount()==0 and r.tankLevel()==0 and r.tankLevel(nil)==0)
          assert(r.tankSpace()==0 and r.tankSpace(nil)==0)
          assert(r.selectTank()==1 and r.selectTank(nil)==1)
          assert(t.getFluidInInternalTank()==nil and t.getFluidInInternalTank(nil)==nil)
          assert(r.drain(3)==nil and r.fill(3)==nil and t.drain()==nil and t.fill()==nil)
          assert(p.done()) computer.shutdown() return
        end
        assert(r.tankCount()==2) ev()
        assert(p.prepare(1)) ev({{1,1234},{1,-234}}) assert(p.check(1))
        assert(p.prepare(2)) ev()
        assert(r.selectTank(nil)==1 and r.tankLevel(nil)==1000 and r.tankSpace(nil)==15000)
        assert(r.transferFluidTo(1) and r.transferFluidTo(2,0)) ev({})
        assert(r.transferFluidTo(2,500)==nil) ev({})
        assert(r.transferFluidTo(2,1000)) ev({{2,-2000},{1,-1000},{2,1000},{1,2000}}) assert(p.check(2))
        assert(p.prepare(3)) ev() assert(r.selectTank(2)==2) assert(r.select(1))
        assert(t.getFluidInInternalTank(1)==nil and t.getFluidInInternalTank(nil)~=nil)
        assert(not pcall(t.getTankLevelInSlot,nil) and not pcall(t.getTankCapacityInSlot,nil) and not pcall(t.getFluidInTankInSlot,nil))
        local ok,n=t.fill(1000) assert(ok and n==1000 and t.getTankLevelInSlot()==1000 and t.getTankCapacityInSlot()==1000)
        assert(t.getFluidInTankInSlot().amount==1000) ev({{2,-1000}})
        ok,n=t.drain(1000) assert(ok and n==1000 and t.getTankLevelInSlot()==0 and t.getFluidInTankInSlot()==nil) ev({{2,1000}}) assert(p.check(3))
        assert(p.buckets(true) and t.fill(1000)==nil) ev({}) assert(p.buckets(false))
        assert(p.prepare(4)) ev() assert(r.selectTank(1)==1)
        ok,n=r.drain(3,0) assert(ok==false and n==0) ev({})
        ok,n=r.drain(3,1000) assert(ok and n==1000) ev({{1,1000}}) assert(p.check(4))
        ok,n=r.fill(3,0) assert(ok and n==0) ev({})
        ok,n=r.fill(3,1000) assert(ok and n==1000) ev({{1,-1000}}) assert(p.check(5))
        assert(r.selectTank(2)==2 and r.compareFluid(3) and r.compareFluid(3,1))
        assert(not pcall(r.compareFluid,3,nil))
        assert(t.getTankCount(3)==1 and t.getTankLevel(3)==1000 and t.getTankCapacity(3)==1000)
        assert(t.getFluidInTank(3,1).amount==1000 and t.getFluidInTank(3)[1].capacity==1000)
        assert(not pcall(t.getTankLevel,3,nil) and not pcall(t.getTankCapacity,3,nil) and not pcall(t.getFluidInTank,3,nil))
        ok=r.fill(3,1000) assert(not ok) ev({}) assert(p.check(6))
        assert(p.done()) computer.shutdown()
        """;
}
