package li.cil.oc.common.block.entity;

import java.util.ArrayList;
import java.util.Map;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.*;
import li.cil.oc.common.menu.AdapterMenu;
import li.cil.oc.api.util.Lifecycle;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** Six independent adaptations and the original single upgrade inventory. */
public final class AdapterBlockEntity extends BlockEntity implements li.cil.oc.api.internal.Adapter, EnvironmentHost, MenuProvider, Analyzable, DeviceInfo {
    private final ManagedEnvironment[] environments = new ManagedEnvironment[6];
    private final DriverBlock[] drivers = new DriverBlock[6];
    private final BlockEntity[] targets = new BlockEntity[6];
    private final BlockData[] savedBlocks = new BlockData[6];
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private ManagedEnvironment upgrade;
    private Node node;
    private CompoundTag pendingNode;
    private int openSides = 0x3f;
    private boolean initialized, refreshing, initializing;
    private record BlockData(String name, CompoundTag data) {}
    public AdapterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override public Level world() { return level; }
    @Override public double xPosition() { return worldPosition.getX() + .5; }
    @Override public double yPosition() { return worldPosition.getY() + .5; }
    @Override public double zPosition() { return worldPosition.getZ() + .5; }
    @Override public void markChanged() { setChanged(); }
    @Override public Node node() {
        if (node == null && level != null && !level.isClientSide) { node = li.cil.oc.api.Network.newNode(this, Visibility.Network).create(); if (pendingNode != null) { node.load(pendingNode); pendingNode = null; } }
        return node;
    }
    public boolean isSideOpen(Direction side) { return side != null && (openSides & 1 << side.get3DDataValue()) != 0; }
    public int sideMask() { return openSides; }
    public void setSideOpen(Direction side, boolean open) {
        if (side == null || isSideOpen(side) == open) return;
        if (open) openSides |= 1 << side.get3DDataValue(); else openSides &= ~(1 << side.get3DDataValue());
        setChanged();
        if (level != null && !level.isClientSide) {
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, .5f, .7f + level.random.nextFloat() * .25f);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            refreshNeighbor(side);
        }
    }
    private void initialize() {
        if (initialized || level == null || level.isClientSide || isRemoved()) return;
        initialized = true; initializing = true; try { node(); mountUpgrade(); li.cil.oc.api.Network.joinOrCreateNetwork(this); } finally { initializing = false; }
    }
    public void refreshNeighbors() {
        if (refreshing || level == null || level.isClientSide || isRemoved()) return;
        initialize();
        if (node() == null || node.network() == null) return;
        refreshing = true; try { for (Direction side : Direction.values()) refreshNeighbor(side); } finally { refreshing = false; }
    }
    public void refreshNeighbor(Direction side) {
        if (side == null || level == null || level.isClientSide || node() == null || node.network() == null || isRemoved()) return;
        int index = side.get3DDataValue(); BlockPos pos = worldPosition.relative(side);
        boolean available = isSideOpen(side) && level.hasChunkAt(pos);
        BlockEntity target = available ? level.getBlockEntity(pos) : null;
        // Original adapters deliberately do not adapt OC's own environments.
        if (target instanceof Environment && BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(target.getType()).getNamespace().equals("opencomputers")) available = false;
        DriverBlock next = available ? Driver.driverFor(level, pos, side) : null;
        if (next != null && next.equals(drivers[index]) && targets[index] == target && environments[index] != null) return;
        boolean driverChanged = drivers[index] != null && next != null && !next.equals(drivers[index]);
        release(index);
        if (driverChanged) savedBlocks[index] = null;
        if (next == null) return;
        ManagedEnvironment environment = next.createEnvironment(level, pos, side);
        if (environment == null) return;
        BlockData saved = savedBlocks[index];
        if (saved != null && saved.name.equals(environment.getClass().getName())) environment.load(saved.data, level.registryAccess());
        environments[index] = environment; drivers[index] = next; targets[index] = target;
        savedBlocks[index] = new BlockData(environment.getClass().getName(), new CompoundTag());
        if (environment.node() != null) node.connect(environment.node());
        setChanged();
    }
    private void save(int index) {
        ManagedEnvironment environment = environments[index];
        if (environment == null || level == null) return;
        CompoundTag data = new CompoundTag(); environment.save(data, level.registryAccess());
        savedBlocks[index] = new BlockData(environment.getClass().getName(), data);
    }
    private void release(int index) { ManagedEnvironment old = environments[index]; if (old != null && old.node() != null && node != null) node.disconnect(old.node()); save(index); environments[index] = null; drivers[index] = null; targets[index] = null; if (old != null && old.node() != null) old.node().remove(); }
    private void mountUpgrade() {
        if (upgrade != null || !initialized || level == null || level.isClientSide || items.getFirst().isEmpty()) return;
        var driver = Driver.driverFor(items.getFirst(), getClass());
        if (driver == null || !Slot.Upgrade.equals(driver.slot(items.getFirst()))) return;
        upgrade = driver.createEnvironment(items.getFirst(), this);
        if (upgrade != null) {
            lifecycle(upgrade, Lifecycle.LifecycleState.Constructing);
            try { upgrade.load(componentData(driver, items.getFirst()), level.registryAccess()); }
            catch (Throwable error) { li.cil.oc.OpenComputers.LOGGER.warn("An adapter item component failed while loading", error); }
            lifecycle(upgrade, Lifecycle.LifecycleState.Initializing);
            if (node() != null && node.network() != null && upgrade.node() != null) node.connect(upgrade.node());
            lifecycle(upgrade, Lifecycle.LifecycleState.Initialized); if (!initializing) flushUpgrade();
        }
    }
    public void flushUpgrade() {
        if (upgrade == null || level == null || level.isClientSide || items.getFirst().isEmpty()) return;
        var driver = Driver.driverFor(items.getFirst(), getClass());
        if (driver != null) { CompoundTag data = componentData(driver, items.getFirst()); upgrade.save(data, level.registryAccess()); driver.saveDataTag(items.getFirst(), data); }
    }
    private static CompoundTag componentData(li.cil.oc.api.driver.DriverItem driver, ItemStack stack) { CompoundTag data = driver.dataTag(stack); return data != null ? data : stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("oc:data"); }
    private static void lifecycle(ManagedEnvironment environment, Lifecycle.LifecycleState state) { if (environment instanceof Lifecycle listener) listener.onLifecycleStateChange(state); }
    private void releaseUpgrade() { if (upgrade == null) return; lifecycle(upgrade, Lifecycle.LifecycleState.Disposing); if (upgrade.node() != null) upgrade.node().remove(); flushUpgrade(); if (upgrade.node() != null) upgrade.node().remove(); lifecycle(upgrade, Lifecycle.LifecycleState.Disposed); upgrade = null; }
    public void prepareDrops() { releaseUpgrade(); }
    private void inventoryChanged() { mountUpgrade(); setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    @Override public void onConnect(Node connected) { if (connected == node) { if (upgrade != null && upgrade.node() != null) node.connect(upgrade.node()); refreshNeighbors(); } }
    @Override public void onDisconnect(Node disconnected) { if (disconnected == node) { for (int i = 0; i < 6; i++) release(i); if (upgrade != null && upgrade.node() != null) upgrade.node().remove(); } }
    @Override public void onMessage(Message message) {}
    @Override public Map<String, String> getDeviceInfo() { return Map.of("class", "bus", "description", "Adapter", "vendor", "MightyPirates GmbH & Co. KG", "product", "Multiplug Ext.1"); }
    @Override public Node[] onAnalyze(Player player, Direction side, float hitX, float hitY, float hitZ) { ArrayList<Node> result = new ArrayList<>(); for (ManagedEnvironment environment : environments) if (environment != null && environment.node() != null) result.add(environment.node()); if (upgrade != null && upgrade.node() != null) result.add(upgrade.node()); return result.toArray(Node[]::new); }
    public static void tick(AdapterBlockEntity adapter) { adapter.initialize(); if (adapter.node() != null && adapter.node.network() == null) li.cil.oc.api.Network.joinOrCreateNetwork(adapter); if (adapter.level.getGameTime() % Math.max(1, Settings.get().tickFrequency) == 0) adapter.refreshNeighbors(); for (ManagedEnvironment environment : adapter.environments) if (environment != null && environment.canUpdate()) environment.update(); if (adapter.upgrade != null && adapter.upgrade.canUpdate()) adapter.upgrade.update(); }
    @Override public void onLoad() { super.onLoad(); initialize(); }
    private void dispose() { releaseUpgrade(); for (int i = 0; i < 6; i++) release(i); if (node != null) node.remove(); initialized = false; }
    @Override public void setRemoved() { dispose(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { dispose(); super.onChunkUnloaded(); }
    @Override public int getContainerSize() { return 1; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return items.getFirst().isEmpty(); }
    @Override public ItemStack getItem(int slot) { if (slot != 0) return ItemStack.EMPTY; flushUpgrade(); return items.getFirst(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { if (slot != 0 || stack.isEmpty()) return false; var driver = Driver.driverFor(stack, getClass()); return driver != null && Slot.Upgrade.equals(driver.slot(stack)); }
    @Override public ItemStack removeItem(int slot, int amount) { if (slot != 0 || amount <= 0 || isEmpty()) return ItemStack.EMPTY; releaseUpgrade(); ItemStack result = ContainerHelper.removeItem(items, 0, amount); inventoryChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, 1); }
    @Override public void setItem(int slot, ItemStack stack) { if (slot != 0 || items.getFirst() == stack || items.getFirst().isEmpty() && stack.isEmpty()) return; ItemStack next = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1); releaseUpgrade(); items.set(0, next); inventoryChanged(); }
    @Override public void clearContent() { removeItem(0, 1); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(xPosition(), yPosition(), zPosition()) <= 64; }
    @Override public Component getDisplayName() { return Component.translatable("block.opencomputers.adapter"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return stillValid(player) ? new AdapterMenu(id, inventory, this) : null; }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); openSides = tag.contains("oc:openSides") ? tag.getByte("oc:openSides") & 63 : 63;
        if (tag.contains("oc:items", Tag.TAG_LIST)) { items.set(0, ItemStack.EMPTY); for (Tag raw : tag.getList("oc:items", Tag.TAG_COMPOUND)) { CompoundTag entry = (CompoundTag) raw; if (entry.getInt("slot") == 0) items.set(0, ItemStack.parseOptional(registries, entry.getCompound("item"))); } }
        pendingNode = tag.contains("oc:node", Tag.TAG_COMPOUND) ? tag.getCompound("oc:node").copy() : null; if (node != null && pendingNode != null) { node.load(pendingNode); pendingNode = null; }
        ListTag blocks = tag.getList("oc:adapter.blocks", Tag.TAG_COMPOUND); for (int i = 0; i < Math.min(6, blocks.size()); i++) { CompoundTag entry = blocks.getCompound(i); if (entry.contains("name") && entry.contains("data")) savedBlocks[i] = new BlockData(entry.getString("name"), entry.getCompound("data").copy()); }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); flushUpgrade(); tag.putByte("oc:openSides", (byte) openSides);
        ListTag inventory = new ListTag(); if (!isEmpty()) { CompoundTag entry = new CompoundTag(); entry.putInt("slot", 0); entry.put("item", items.getFirst().save(registries)); inventory.add(entry); } tag.put("oc:items", inventory);
        CompoundTag identity = new CompoundTag(); if (node() != null) node.save(identity); else if (pendingNode != null) identity = pendingNode.copy(); tag.put("oc:node", identity);
        ListTag blocks = new ListTag(); for (int i = 0; i < 6; i++) { save(i); CompoundTag entry = new CompoundTag(); if (savedBlocks[i] != null) { entry.putString("name", savedBlocks[i].name); entry.put("data", savedBlocks[i].data.copy()); } blocks.add(entry); } tag.put("oc:adapter.blocks", blocks);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); tag.putByte("oc:openSides", (byte) openSides); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
