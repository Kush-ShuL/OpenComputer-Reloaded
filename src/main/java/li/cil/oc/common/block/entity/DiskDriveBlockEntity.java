package li.cil.oc.common.block.entity;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.common.block.DiskDriveBlock;
import li.cil.oc.common.menu.DiskDriveMenu;
import li.cil.oc.server.PacketSender;
import li.cil.oc.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Original disk_drive callbacks and inventory-managed, network-visible media. */
public final class DiskDriveBlockEntity extends BlockEntity implements Environment, EnvironmentHost, Container, MenuProvider, Analyzable, DeviceInfo, PacketSender.ActivityListener {
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private li.cil.oc.api.network.Component node;
    private ManagedEnvironment medium;
    private CompoundTag pendingNode;
    private boolean initialized;
    private final AtomicBoolean pendingSave = new AtomicBoolean();
    public volatile long lastAccess;

    public DiskDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override public Level world() { return level; }
    @Override public double xPosition() { return worldPosition.getX() + .5; }
    @Override public double yPosition() { return worldPosition.getY() + .5; }
    @Override public double zPosition() { return worldPosition.getZ() + .5; }
    @Override public void markChanged() { pendingSave.set(true); }
    @Override public void setChanged() {
        if (level instanceof ServerLevel server && !server.getServer().isSameThread()) { markChanged(); return; }
        super.setChanged();
    }
    public Direction facing() { return getBlockState().getValue(DiskDriveBlock.FACING); }
    @Override public li.cil.oc.api.network.Component node() {
        if (node == null && level != null && !level.isClientSide) {
            node = li.cil.oc.api.Network.newNode(this, Visibility.Network).withComponent("disk_drive").create();
            if (pendingNode != null) { node.load(pendingNode); pendingNode = null; }
        }
        return node;
    }
    public Node filesystemNode() { return medium == null ? null : medium.node(); }
    private void initialize() {
        if (initialized || level == null || level.isClientSide || isRemoved()) return;
        initialized = true;
        node();
        mountMedium();
        li.cil.oc.api.Network.joinOrCreateNetwork(this);
    }
    public void joinNetwork() { initialize(); if (node() != null) li.cil.oc.api.Network.joinOrCreateNetwork(this); }
    private void mountMedium() {
        if (medium != null || !initialized || level == null || level.isClientSide || items.getFirst().isEmpty()) return;
        ItemStack stack = items.getFirst();
        var driver = Driver.driverFor(stack, getClass());
        if (driver == null || !Slot.Floppy.equals(driver.slot(stack))) return;
        medium = driver.createEnvironment(stack, this);
        if (medium == null) throw new IllegalStateException("Floppy driver did not create its server environment");
        medium.load(driver.dataTag(stack), level.registryAccess());
        if (medium.node() instanceof li.cil.oc.api.network.Component component) component.setVisibility(Visibility.Network);
        if (node() != null && node.network() != null && medium.node() != null) node.connect(medium.node());
    }
    public void flushMedium() {
        ItemStack stack = items.getFirst();
        if (medium == null || level == null || level.isClientSide || stack.isEmpty()) return;
        var driver = Driver.driverFor(stack, getClass());
        if (driver != null) {
            CompoundTag data = driver.dataTag(stack);
            medium.save(data, level.registryAccess());
            driver.saveDataTag(stack, data);
        }
    }
    private void unmountMedium() { if (medium != null && medium.node() != null) medium.node().remove(); medium = null; }
    private void inventoryChanged(boolean removed, boolean inserted) {
        if (level != null && !level.isClientSide) {
            mountMedium();
            if (removed) play("floppy_eject");
            if (inserted) play("floppy_insert");
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        setChanged();
    }
    private void play(String sound) {
        if (level != null && Settings.get().soundVolume > 0)
            level.playSound(null, worldPosition, SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("opencomputers", sound)), SoundSource.BLOCKS, Settings.get().soundVolume, 1);
    }
    @Override public void onConnect(Node connected) { if (connected == node && medium != null && medium.node() != null) node.connect(medium.node()); }
    @Override public void onDisconnect(Node disconnected) {
        if (disconnected == node && medium != null) { flushMedium(); if (medium.node() != null) medium.node().remove(); }
    }
    @Override public void onMessage(Message message) {}
    @Override public void onComponentActivity(String address, boolean network) { if (!network) lastAccess = System.currentTimeMillis(); }
    @Override public Map<String, String> getDeviceInfo() { return Map.of("class", "disk", "description", "Floppy disk drive", "vendor", "MightyPirates GmbH & Co. KG", "product", "Spinner 520p1"); }
    @Override public Node[] onAnalyze(Player player, Direction side, float hitX, float hitY, float hitZ) { Node media = filesystemNode(); return media == null ? null : new Node[]{media}; }

    @Callback(doc = "function():boolean -- Checks whether some medium is currently in the drive.")
    public Object[] isEmpty(Context context, Arguments args) { return new Object[]{filesystemNode() == null}; }
    @Callback(doc = "function([velocity:number]):boolean -- Eject the currently present medium from the drive.")
    public Object[] eject(Context context, Arguments args) { return new Object[]{ejectMedium(args.optDouble(0, 0))}; }
    @Callback(doc = "function():string -- Return the internal floppy disk address.")
    public Object[] media(Context context, Arguments args) { Node media = filesystemNode(); return media == null ? new Object[]{null, "drive is empty"} : new Object[]{media.address()}; }
    public boolean ejectMedium(double velocity) {
        ItemStack stack = removeItem(0, 1);
        if (stack.isEmpty()) return false;
        ItemEntity entity = InventoryUtils.spawnStackInWorld(level, worldPosition, stack, facing());
        if (entity != null) { double speed = Double.isFinite(velocity) ? Math.clamp(velocity, 0, 1) : 0; Direction facing = facing(); entity.setDeltaMovement(entity.getDeltaMovement().add(facing.getStepX() * speed, facing.getStepY() * speed, facing.getStepZ() * speed)); }
        return true;
    }
    public static void tick(DiskDriveBlockEntity drive) {
        if (drive.level != null && !drive.level.isClientSide && drive.pendingSave.getAndSet(false)) drive.setChanged();
        drive.initialize();
        if (drive.node() != null && drive.node.network() == null) drive.joinNetwork();
        if (drive.medium != null && drive.medium.canUpdate()) drive.medium.update();
    }
    @Override public void onLoad() { super.onLoad(); initialize(); }
    private void dispose() { flushMedium(); unmountMedium(); if (node != null) node.remove(); initialized = false; }
    @Override public void setRemoved() { dispose(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { dispose(); super.onChunkUnloaded(); }

    @Override public int getContainerSize() { return 1; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return items.getFirst().isEmpty(); }
    @Override public ItemStack getItem(int slot) { if (slot != 0) return ItemStack.EMPTY; flushMedium(); return items.getFirst(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { if (slot != 0 || stack.isEmpty()) return false; var driver = Driver.driverFor(stack, getClass()); return driver != null && Slot.Floppy.equals(driver.slot(stack)); }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || amount <= 0 || isEmpty()) return ItemStack.EMPTY;
        flushMedium();
        ItemStack result = ContainerHelper.removeItem(items, 0, amount);
        unmountMedium();
        inventoryChanged(true, false);
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, 1); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        boolean occupied = medium != null || !isEmpty();
        flushMedium(); unmountMedium();
        items.set(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        inventoryChanged(occupied, !stack.isEmpty());
    }
    @Override public void clearContent() { removeItem(0, 1); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(xPosition(), yPosition(), zPosition()) <= 64; }
    @Override public Component getDisplayName() { return Component.translatable("block.opencomputers.diskdrive"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return stillValid(player) ? new DiskDriveMenu(id, inventory, this) : null; }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.set(0, ItemStack.EMPTY);
        if (tag.contains("oc:disk", Tag.TAG_COMPOUND)) items.set(0, ItemStack.parseOptional(registries, tag.getCompound("oc:disk")));
        else if (tag.contains("oc:items", Tag.TAG_LIST)) {
            for (Tag entry : tag.getList("oc:items", Tag.TAG_COMPOUND)) { CompoundTag slot = (CompoundTag) entry; if (slot.getInt("slot") == 0) items.set(0, ItemStack.parseOptional(registries, slot.getCompound("item"))); }
        } else if (tag.contains("Items", Tag.TAG_LIST)) ContainerHelper.loadAllItems(tag, items, registries);
        pendingNode = tag.contains("oc:node", Tag.TAG_COMPOUND) ? tag.getCompound("oc:node").copy() : null;
        if (node != null && pendingNode != null) { node.load(pendingNode); pendingNode = null; }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        flushMedium();
        ListTag inventory = new ListTag();
        if (!isEmpty()) { CompoundTag entry = new CompoundTag(); entry.putInt("slot", 0); entry.put("item", items.getFirst().save(registries)); inventory.add(entry); }
        tag.put("oc:items", inventory);
        CompoundTag data = new CompoundTag();
        if (node() != null) node.save(data); else if (pendingNode != null) data = pendingNode.copy();
        tag.put("oc:node", data);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        // NeoForge skips empty update tags. Send the empty slot explicitly so the client clears its disk model.
        tag.put("oc:disk", items.getFirst().saveOptional(registries));
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
