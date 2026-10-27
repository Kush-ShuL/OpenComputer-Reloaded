package li.cil.oc.common.block.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;
import li.cil.oc.Constants;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.driver.DriverItem;
import li.cil.oc.api.internal.Agent;
import li.cil.oc.api.internal.Rotatable;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.util.StateAware;
import li.cil.oc.common.block.ChargerBlock;
import li.cil.oc.common.capabilities.EnergyProvider;
import li.cil.oc.common.integration.ItemCharge;
import li.cil.oc.common.menu.ChargerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Original charger: redstone-scaled power transfer, adjacent agents and nearby player equipment. */
public final class ChargerBlockEntity extends NetworkPowerBlockEntity implements SidedEnvironment, EnergyProvider,
        WorldlyContainer, MenuProvider, Analyzable, StateAware, DeviceInfo, Rotatable {
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private final List<Target> targets = new ArrayList<>();
    private final Map<ItemStack, Player> equipment = new IdentityHashMap<>();
    private ManagedEnvironment component;
    private boolean componentLoaded, rescan = true;
    public boolean invertSignal, hasPower;
    public double chargeSpeed;
    private record Target(Object identity, Supplier<Vec3> position, DoubleUnaryOperator accept) {}

    public ChargerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override protected double initialCapacity() { return Settings.get().bufferConverter; }
    @Override public Direction facing() { return getBlockState().getValue(ChargerBlock.FACING); }
    @Override public Direction toGlobal(Direction side) {
        if (side == null || side.getAxis().isVertical()) return side;
        for (int turns = facing().get2DDataValue(); turns > 0; turns--) side = side.getClockWise();
        return side;
    }
    @Override public Direction toLocal(Direction side) {
        if (side == null || side.getAxis().isVertical()) return side;
        for (int turns = facing().get2DDataValue(); turns > 0; turns--) side = side.getCounterClockWise();
        return side;
    }
    @Override public boolean canConnect(Direction side) { return side != facing(); }
    @Override public Node sidedNode(Direction side) { return canConnect(side) ? node() : null; }
    @Override public void onConnect(Node node) {
        if (node == node()) {
            rescan = true;
            if (component != null && component.node() != null) node().connect(component.node());
        }
    }
    @Override public void onDisconnect(Node node) {
        super.onDisconnect(node);
        if (node == node()) releaseComponent();
    }
    public void neighborChanged() { rescan = true; }
    public void invert() { invertSignal = !invertSignal; chargeSpeed = 1 - chargeSpeed; setChanged(); sync(); }
    @Override public void serverTick() {
        super.serverTick();
        if (!server() || !isPowerAvailable()) return;
        if (!componentLoaded) rebuildComponent();
        if (component != null && component.canUpdate()) component.update();
        if (rescan) { rescan = false; updateSignal(); updateTargets(); }
        else if ((level.getGameTime() + Math.floorMod(worldPosition.hashCode(), 20)) % 20 == 0) updateTargets();
        if (level.getGameTime() % Settings.get().tickFrequency != 0) return;
        double external = Math.max(0, Settings.get().chargeRateExternal * chargeSpeed * Settings.get().tickFrequency);
        double internal = Math.max(0, Settings.get().chargeRateTablet * chargeSpeed * Settings.get().tickFrequency);
        boolean available = Settings.get().ignorePower || external > 0 && node().globalBuffer() >= external * .5;
        boolean changed = false;
        if (available) for (Target target : targets) changed |= transfer(external, target.accept) > 0;
        available |= internal > 0 && node().globalBuffer() >= internal * .5;
        if (available) {
            changed |= chargeStack(getItem(0), internal) > 0;
            for (var entry : equipment.entrySet()) if (chargeStack(entry.getKey(), internal) > 0) { entry.getValue().getInventory().setChanged(); changed = true; }
        }
        if (changed) { setChanged(); flushItem(); }
        if (hasPower != available) { hasPower = available; sync(); }
    }
    private double chargeStack(ItemStack stack, double amount) { return stack.isEmpty() ? 0 : transfer(amount, offered -> ItemCharge.charge(stack, offered, false)); }
    private double transfer(double requested, DoubleUnaryOperator accept) {
        if (!(requested > 0) || !Double.isFinite(requested)) return 0;
        double supplied = Math.clamp(requested + node().changeBuffer(-requested), 0, requested);
        if (!(supplied > 0)) return 0;
        double surplus;
        try { surplus = accept.applyAsDouble(supplied); }
        catch (RuntimeException | LinkageError error) { node().changeBuffer(supplied); OpenComputers.LOGGER.warn("Charger target rejected energy at {}", worldPosition, error); return 0; }
        surplus = Double.isFinite(surplus) ? Math.clamp(surplus, 0, supplied) : supplied;
        if (surplus > 0) node().changeBuffer(surplus);
        return supplied - surplus;
    }
    private void updateSignal() {
        int signal = 0;
        for (Direction side : Direction.values()) signal = Math.max(signal, li.cil.oc.common.redstone.BundledRedstone.computeInput(level, worldPosition, side));
        signal = Math.clamp(signal, 0, 15);
        double speed = (invertSignal ? 15 - signal : signal) / 15d;
        if (chargeSpeed != speed) { chargeSpeed = speed; setChanged(); sync(); }
    }
    private void updateTargets() {
        List<Object> previousTargets = targets.stream().map(Target::identity).toList();
        targets.clear(); equipment.clear();
        for (Direction side : Direction.values()) {
            BlockPos pos = worldPosition.relative(side);
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof li.cil.oc.api.internal.Robot robot)
                targets.add(agentTarget(robot));
        }
        AABB bounds = new AABB(worldPosition).inflate(1);
        for (Entity entity : level.getEntities((Entity) null, bounds, candidate -> candidate instanceof li.cil.oc.api.internal.Drone))
            targets.add(agentTarget((Agent) entity));
        for (Player player : level.getEntitiesOfClass(Player.class, bounds)) {
            if (li.cil.oc.api.Nanomachines.hasController(player)) targets.add(new Target(player, player::position, offered -> {
                var controller = li.cil.oc.api.Nanomachines.getController(player); return controller == null ? offered : controller.changeBuffer(offered);
            }));
            for (ItemStack stack : player.getInventory().items) if (canCharge(stack)) equipment.put(stack, player);
        }
        if (server() && !previousTargets.equals(targets.stream().map(Target::identity).toList())) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }
    private static Target agentTarget(Agent agent) {
        return new Target(agent, () -> new Vec3(agent.xPosition(), agent.yPosition(), agent.zPosition()), offered -> {
            var machine = agent.machine();
            return machine != null && machine.node() instanceof Connector connector && connector.network() != null ? connector.changeBuffer(offered) : offered;
        });
    }
    public void clientTick() {
        if (level == null || !level.isClientSide || chargeSpeed <= 0 || !hasPower || level.getGameTime() % 10 != 0) return;
        updateTargets();
        for (Target target : targets) {
            Vec3 position = target.position.get(); double theta = level.random.nextDouble() * Math.PI, phi = level.random.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.HAPPY_VILLAGER, position.x + .45 * Math.sin(theta) * Math.cos(phi), position.y + .45 * Math.cos(theta), position.z + .45 * Math.sin(theta) * Math.sin(phi), 0, 0, 0);
        }
    }
    public static boolean canCharge(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var driver = Driver.driverFor(stack, ChargerBlockEntity.class);
        return driver != null && li.cil.oc.api.driver.item.Slot.Tablet.equals(driver.slot(stack)) || ItemCharge.canCharge(stack);
    }
    private DriverItem componentDriver() {
        ItemStack stack = getItem(0); if (stack.isEmpty()) return null;
        var driver = Driver.driverFor(stack, getClass());
        return driver != null && li.cil.oc.api.driver.item.Slot.Tablet.equals(driver.slot(stack)) ? driver : null;
    }
    private CompoundTag componentData(DriverItem driver) {
        CompoundTag tag = driver.dataTag(getItem(0));
        return tag != null ? tag : getItem(0).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("oc:data");
    }
    private void rebuildComponent() {
        if (!server()) return; componentLoaded = true;
        DriverItem driver = componentDriver(); if (driver == null) return;
        component = driver.createEnvironment(getItem(0), this);
        if (component != null) { component.load(componentData(driver), level.registryAccess()); if (component.node() != null) node().connect(component.node()); }
    }
    public void flushItem() {
        if (!server() || component == null) return;
        DriverItem driver = componentDriver();
        if (driver != null) { CompoundTag tag = componentData(driver); component.save(tag, level.registryAccess()); driver.saveDataTag(getItem(0), tag); }
    }
    private void releaseComponent() { flushItem(); if (component != null && component.node() != null) component.node().remove(); component = null; componentLoaded = false; }
    @Override protected void afterDetach() { releaseComponent(); targets.clear(); equipment.clear(); }
    private void sync() { if (server()) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2); level.updateNeighborsAt(worldPosition, getBlockState().getBlock()); } }
    @Override public EnumSet<State> getCurrentState() { return targets.isEmpty() ? EnumSet.noneOf(State.class) : hasPower ? EnumSet.of(State.IsWorking) : EnumSet.of(State.CanWork); }
    @Override public Map<String, String> getDeviceInfo() { return Map.of("class", "generic", "description", "Charger", "vendor", Constants.DeviceInfo.DefaultVendor, "product", "PowerUpper"); }
    @Override public Node[] onAnalyze(Player player, Direction side, float x, float y, float z) { player.sendSystemMessage(Component.translatable("oc:gui.Analyzer.ChargerSpeed", chargeSpeed)); return null; }
    @Override public IEnergyStorage energy(Direction side) {
        if (!canConnect(side)) return null;
        return new IEnergyStorage() {
            public int receiveEnergy(int maximum, boolean simulate) {
                double ratio = Settings.get().ratioForgeEnergy;
                if (maximum <= 0 || !server() || !isPowerAvailable() || Settings.get().ignorePower || !(ratio > 0)) return 0;
                Connector connector = node();
                Object lock = connector.network() == null ? connector : connector.network();
                synchronized (lock) {
                    int used = (int) Math.max(0, Math.min(maximum, Math.floor(Math.min(Settings.get().chargerRate, connector.globalBufferSize() - connector.globalBuffer()) / ratio)));
                    if (!simulate && used > 0) { connector.changeBuffer(used * ratio); markChanged(); }
                    return used;
                }
            }
            public int extractEnergy(int maximum, boolean simulate) { return 0; }
            public int getEnergyStored() { return node() == null ? 0 : (int) Math.min(Integer.MAX_VALUE, node().globalBuffer() / Settings.get().ratioForgeEnergy); }
            public int getMaxEnergyStored() { return node() == null ? 0 : (int) Math.min(Integer.MAX_VALUE, node().globalBufferSize() / Settings.get().ratioForgeEnergy); }
            public boolean canExtract() { return false; }
            public boolean canReceive() { return server() && isPowerAvailable() && !Settings.get().ignorePower; }
        };
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        chargeSpeed = Math.clamp(tag.getDouble(tag.contains("chargeSpeed") ? "chargeSpeed" : "oc:chargeSpeed"), 0, 1);
        hasPower = tag.getBoolean(tag.contains("hasPower") ? "hasPower" : "oc:hasPower");
        invertSignal = tag.getBoolean(tag.contains("invertSignal") ? "invertSignal" : "oc:invertSignal");
        if (level == null || !level.isClientSide) { ContainerHelper.loadAllItems(tag, items, registries); componentLoaded = false; rescan = true; }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); flushItem(); ContainerHelper.saveAllItems(tag, items, registries); tag.putDouble("oc:chargeSpeed", chargeSpeed); tag.putBoolean("oc:hasPower", hasPower); tag.putBoolean("oc:invertSignal", invertSignal); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); tag.putDouble("oc:chargeSpeed", chargeSpeed); tag.putBoolean("oc:hasPower", hasPower); tag.putBoolean("oc:invertSignal", invertSignal); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public int getContainerSize() { return 1; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return getItem(0).isEmpty(); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { releaseComponent(); ItemStack stack = ContainerHelper.removeItem(items, slot, amount); setChanged(); return stack; }
    @Override public ItemStack removeItemNoUpdate(int slot) { releaseComponent(); ItemStack stack = ContainerHelper.takeItem(items, slot); setChanged(); return stack; }
    @Override public void setItem(int slot, ItemStack stack) { releaseComponent(); items.set(slot, stack.copyWithCount(Math.min(1, stack.getCount()))); setChanged(); }
    @Override public void clearContent() { releaseComponent(); items.clear(); setChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && canCharge(stack); }
    @Override public int[] getSlotsForFace(Direction side) { return new int[]{0}; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return true; }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(xPosition(), yPosition(), zPosition()) <= 64; }
    @Override public Component getDisplayName() { return Component.translatable("block.opencomputers.charger"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory player, Player owner) { return stillValid(owner) ? new ChargerMenu(id, player, this) : null; }
}
