package li.cil.oc.common.block.entity;

import java.util.EnumSet;
import java.util.stream.IntStream;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.network.*;
import li.cil.oc.api.util.StateAware;
import li.cil.oc.common.capabilities.EnergyProvider;
import li.cil.oc.common.menu.WorkstationMenu;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Shared server-thread inventory, connector and world lifetime for production devices. */
public abstract class WorkstationBlockEntity extends BlockEntity implements Environment, EnvironmentHost,
        SidedEnvironment, WorldlyContainer, MenuProvider, StateAware, DeviceInfo, EnergyProvider {
    protected final NonNullList<ItemStack> items;
    protected Connector connector;
    private CompoundTag pendingNode;
    private boolean detached;
    protected boolean active;
    protected WorkstationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
        super(type, pos, state); items = NonNullList.withSize(size, ItemStack.EMPTY);
    }
    protected abstract String deviceName();
    protected abstract double rate();
    protected abstract void work();
    public abstract double progress();
    public int timeRemaining() { return 0; }
    public boolean busy() { return active; }
    public boolean canStart() { return false; }
    public boolean active() { return active; }
    @Override public Level world() { return level; }
    @Override public double xPosition() { return worldPosition.getX() + .5; }
    @Override public double yPosition() { return worldPosition.getY() + .5; }
    @Override public double zPosition() { return worldPosition.getZ() + .5; }
    @Override public Connector node() {
        if (connector == null && level != null && !level.isClientSide && !detached && !isRemoved()) {
            var builder = Network.newNode(this, deviceName().equals("assembler") ? Visibility.Network : Visibility.None);
            connector = deviceName().equals("assembler")
                    ? builder.withComponent("assembler").withConnector(Settings.get().bufferConverter).create()
                    : builder.withConnector(Settings.get().bufferConverter).create();
            if (pendingNode != null) { connector.load(pendingNode); pendingNode = null; }
        }
        return connector;
    }
    @Override public Node sidedNode(Direction side) { return canConnect(side) ? node() : null; }
    @Override public boolean canConnect(Direction side) { return !deviceName().equals("assembler") || side != Direction.UP; }
    @Override public void onConnect(Node node) {}
    @Override public void onDisconnect(Node node) {}
    @Override public void onMessage(Message message) {}
    @Override public void markChanged() { setChanged(); }
    public final void tick() {
        if (level == null || level.isClientSide || detached || isRemoved()) return;
        if (node().network() == null) Network.joinOrCreateNetwork(this);
        if (level.getGameTime() % Settings.get().tickFrequency == 0) work();
    }
    public final void neighborsChanged() {
        if (level != null && !level.isClientSide && !detached && !isRemoved()) Network.joinOrCreateNetwork(this);
    }
    protected final void activity(boolean value) {
        if (active == value) return;
        active = value; setChanged();
        if (level != null) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2); level.updateNeighborsAt(worldPosition, getBlockState().getBlock()); }
    }
    @Override public void onLoad() { super.onLoad(); detached = false; neighborsChanged(); }
    private void detach() { if (detached) return; detached = true; if (connector != null) connector.remove(); }
    @Override public void setRemoved() { detach(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { detach(); super.onChunkUnloaded(); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (level != null && level.isClientSide) { active = tag.getBoolean("oc:isActive"); return; }
        ContainerHelper.loadAllItems(tag, items, registries);
        pendingNode = tag.getCompound("oc:node").copy();
        if (connector != null) { connector.load(pendingNode); pendingNode = null; }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); ContainerHelper.saveAllItems(tag, items, registries);
        CompoundTag data = pendingNode == null ? new CompoundTag() : pendingNode.copy();
        if (connector != null) connector.save(data);
        tag.put("oc:node", data);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); tag.putBoolean("oc:isActive", active); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public int getContainerSize() { return items.size(); }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) { ItemStack stack = ContainerHelper.removeItem(items, slot, count); if (!stack.isEmpty()) setChanged(); return stack; }
    @Override public ItemStack removeItemNoUpdate(int slot) { ItemStack stack = ContainerHelper.takeItem(items, slot); if (!stack.isEmpty()) setChanged(); return stack; }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(Math.min(1, stack.getCount()))); setChanged(); }
    @Override public void clearContent() { items.replaceAll(stack -> ItemStack.EMPTY); setChanged(); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(xPosition(), yPosition(), zPosition()) <= 64; }
    @Override public int[] getSlotsForFace(Direction side) { return IntStream.range(0, items.size()).toArray(); }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return !busy(); }
    @Override public Component getDisplayName() { return Component.translatable("block.opencomputers." + deviceName()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory player, Player owner) { return stillValid(owner) ? new WorkstationMenu(id, player, this) : null; }
    @Override public EnumSet<State> getCurrentState() { return busy() ? EnumSet.of(State.IsWorking) : canStart() ? EnumSet.of(State.CanWork) : EnumSet.noneOf(State.class); }
    @Override public java.util.Map<String, String> getDeviceInfo() { return java.util.Map.of("class", "generic", "description", deviceName().equals("assembler") ? "Assembler" : "Disassembler", "vendor", li.cil.oc.Constants.DeviceInfo.DefaultVendor, "product", deviceName().equals("assembler") ? "Factorizer R1D1" : "Break.3R-100"); }
    @Override public IEnergyStorage energy(Direction side) {
        if (side == Direction.UP) return null;
        return new IEnergyStorage() {
            public int receiveEnergy(int maximum, boolean simulate) {
                Connector node = node(); double ratio = Settings.get().ratioForgeEnergy;
                if (node == null || maximum <= 0 || ratio <= 0) return 0;
                int amount = (int) Math.max(0, Math.min(maximum, Math.min(rate(), node.localBufferSize() - node.localBuffer()) / ratio));
                if (!simulate && amount > 0) { node.changeBuffer(amount * ratio); markChanged(); }
                return amount;
            }
            public int extractEnergy(int amount, boolean simulate) { return 0; }
            public int getEnergyStored() { return node() == null ? 0 : (int) Math.min(Integer.MAX_VALUE, node().localBuffer() / Settings.get().ratioForgeEnergy); }
            public int getMaxEnergyStored() { return node() == null ? 0 : (int) Math.min(Integer.MAX_VALUE, node().localBufferSize() / Settings.get().ratioForgeEnergy); }
            public boolean canExtract() { return false; }
            public boolean canReceive() { return true; }
        };
    }
}
