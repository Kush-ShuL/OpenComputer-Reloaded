package li.cil.oc.common.block.entity;

import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.internal.Rotatable;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.ComponentConnector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.common.network.HologramNetworking;
import li.cil.oc.server.component.Hologram;
import li.cil.oc.util.RotationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** A real bottom-connected projector, with source-compatible pitch/yaw and projector NBT. */
public final class HologramBlockEntity extends BlockEntity implements Environment, SidedEnvironment, Hologram.Host, Analyzable, Rotatable, RotatableBlockEntity, DeviceInfo {
    /** Installed on the physical client, without a common-to-GUI class linkage. */
    public interface ClientAccess { void request(HologramBlockEntity entity); void forget(HologramBlockEntity entity); }
    private static volatile ClientAccess clientAccess;
    public static void setClientAccess(ClientAccess access) { clientAccess = java.util.Objects.requireNonNull(access); }
    private void requestSnapshot() { ClientAccess access = clientAccess; if (access != null) access.request(this); }
    private void forgetClient() { ClientAccess access = clientAccess; if (access != null) access.forget(this); }
    public final int tier;
    private Hologram projector;
    private CompoundTag pendingData;
    private volatile Direction pitch = Direction.NORTH, yaw = Direction.SOUTH;
    private final AtomicBoolean changeScheduled = new AtomicBoolean();
    private final ConcurrentLinkedQueue<CompoundTag> pendingUpdates = new ConcurrentLinkedQueue<>();
    private long revision;
    private boolean initialized;

    public HologramBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state, int tier) { super(type, position, state); this.tier = Math.clamp(tier, 0, 1); }
    public synchronized Hologram hologram() {
        if (projector == null) {
            projector = new Hologram(this, tier);
            if (pendingData != null) { projector.load(pendingData); pendingData = null; }
        }
        return projector;
    }
    @Override public ComponentConnector node() { return level == null ? null : hologram().node(); }
    @Override public void onConnect(Node node) { hologram().onConnect(node); }
    @Override public void onDisconnect(Node node) { hologram().onDisconnect(node); }
    @Override public void onMessage(Message message) { hologram().onMessage(message); }
    @Override public Level world() { return level; }
    @Override public double xPosition() { return worldPosition.getX() + 0.5; }
    @Override public double yPosition() { return worldPosition.getY() + 0.5; }
    @Override public double zPosition() { return worldPosition.getZ() + 0.5; }
    @Override public void markChanged() { changeScheduled.set(true); }
    @Override public void setChanged() { changeScheduled.set(true); }
    @Override public Map<String, String> getDeviceInfo() { return hologram().getDeviceInfo(); }
    public Direction pitch() { return pitch; }
    public Direction yaw() { return yaw; }
    @Override public Direction facing() { return pitch == Direction.NORTH ? yaw : pitch; }
    private Direction up() { return pitch == Direction.UP ? yaw.getOpposite() : pitch == Direction.DOWN ? yaw : Direction.UP; }
    private Direction right() {
        Direction up = up(), front = facing();
        return Direction.fromDelta(up.getStepY() * front.getStepZ() - up.getStepZ() * front.getStepY(), up.getStepZ() * front.getStepX() - up.getStepX() * front.getStepZ(), up.getStepX() * front.getStepY() - up.getStepY() * front.getStepX());
    }
    @Override public Direction toGlobal(Direction side) {
        if (side == null) return null;
        return switch (side) { case SOUTH -> facing(); case NORTH -> facing().getOpposite(); case UP -> up(); case DOWN -> up().getOpposite(); case EAST -> right(); case WEST -> right().getOpposite(); };
    }
    @Override public Direction toLocal(Direction side) {
        if (side == null) return null;
        for (Direction local : Direction.values()) if (toGlobal(local) == side) return local;
        throw new IllegalArgumentException("side");
    }
    @Override public Node sidedNode(Direction side) { return toLocal(side) == Direction.DOWN ? node() : null; }
    @OnlyIn(Dist.CLIENT) @Override public boolean canConnect(Direction side) { return toLocal(side) == Direction.DOWN; }
    @Override public Node[] onAnalyze(Player player, Direction side, float x, float y, float z) { Node node = node(); return node == null ? new Node[0] : new Node[]{node}; }
    public void setOrientation(Direction pitch, Direction yaw) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) return;
        Direction nextPitch = pitch.getAxis().isVertical() ? pitch : Direction.NORTH;
        Direction nextYaw = yaw.getAxis().isHorizontal() ? yaw : Direction.SOUTH;
        if (nextPitch == this.pitch && nextYaw == this.yaw) return;
        Direction oldConnection = toGlobal(Direction.DOWN);
        this.pitch = nextPitch; this.yaw = nextYaw;
        if (oldConnection != toGlobal(Direction.DOWN)) {
            var node = node();
            if (node != null) { var neighbors = new ArrayList<Node>(); node.neighbors().forEach(neighbors::add); for (Node neighbor : neighbors) node.disconnect(neighbor); }
            level.invalidateCapabilities(worldPosition);
            Network.joinOrCreateNetwork(this);
        }
        markChanged(); synchronizeHologram(hologram().fullUpdate());
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }
    @Override public boolean rotate(Direction axis) {
        // SimpleBlock in the original mod permits rotations around the Y axis.
        if (axis == null || !axis.getAxis().isVertical() || !(level instanceof ServerLevel)) return false;
        var rotated = RotationHelper.rotate(pitch, yaw, axis);
        if (rotated.pitch() == pitch && rotated.yaw() == yaw) return false;
        setOrientation(rotated.pitch(), rotated.yaw()); return true;
    }
    public long revision() { return revision; }
    private void orientation(CompoundTag data) { data.putInt("oc:pitch", pitch.get3DDataValue()); data.putInt("oc:yaw", yaw.get3DDataValue()); }
    private void readOrientation(CompoundTag data) {
        if (data.contains("oc:pitch")) { Direction direction = Direction.from3DDataValue(data.getInt("oc:pitch")); pitch = direction.getAxis().isVertical() ? direction : Direction.NORTH; }
        if (data.contains("oc:yaw")) { Direction direction = Direction.from3DDataValue(data.getInt("oc:yaw")); yaw = direction.getAxis().isHorizontal() ? direction : Direction.SOUTH; }
    }
    @Override public void synchronizeHologram(CompoundTag update) {
        if (!(level instanceof ServerLevel server)) return;
        if (!server.getServer().isSameThread()) { pendingUpdates.add(update.copy()); return; }
        orientation(update); revision++; HologramNetworking.broadcast(this, update);
    }
    public CompoundTag snapshot() { CompoundTag data = hologram().fullUpdate(); orientation(data); return data; }
    public void flushPendingUpdates() { hologram().flushUpdates(); }
    public void tick() {
        if (level == null) return;
        if (level.isClientSide) { if (!initialized) requestSnapshot(); return; }
        if (changeScheduled.getAndSet(false)) super.setChanged();
        if (node() != null && node().network() == null) Network.joinOrCreateNetwork(this);
        CompoundTag update; while ((update = pendingUpdates.poll()) != null) synchronizeHologram(update);
        hologram().update();
    }
    @OnlyIn(Dist.CLIENT) public void applySync(CompoundTag data, long sequence, boolean full) {
        if (initialized && sequence < revision || initialized && !full && sequence == revision) return;
        if (!full && (!initialized || sequence != revision + 1)) { requestSnapshot(); return; }
        readOrientation(data); hologram().applyUpdate(data); revision = sequence; initialized = true;
    }
    @Override public void onLoad() { super.onLoad(); if (level == null) return; if (level.isClientSide) requestSnapshot(); else Network.joinOrCreateNetwork(this); }
    @Override public void onChunkUnloaded() { if (projector != null && projector.node() != null) projector.node().remove(); if (level != null && level.isClientSide) forgetClient(); super.onChunkUnloaded(); }
    @Override public void setRemoved() { if (projector != null && projector.node() != null) projector.node().remove(); if (level != null && level.isClientSide) forgetClient(); super.setRemoved(); }
    @Override protected void loadAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.loadAdditional(data, registries); readOrientation(data);
        CompoundTag state = data.copy();
        if (data.contains("oc:node")) state.put("node", data.getCompound("oc:node").copy());
        if (projector == null) pendingData = state; else projector.load(state, registries);
        if (data.contains("oc:revision")) revision = data.getLong("oc:revision");
        initialized = data.contains("volume");
    }
    @Override protected void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        CompoundTag state = pendingData == null ? new CompoundTag() : pendingData.copy();
        if (projector != null) projector.save(state, registries);
        if (state.contains("node")) { state.put("oc:node", state.getCompound("node")); state.remove("node"); }
        data.merge(state); orientation(data); data.putLong("oc:revision", revision);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { flushPendingUpdates(); CompoundTag data = snapshot(); data.putLong("oc:revision", revision); return data; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    public AABB projectionBounds() {
        var hologram = hologram(); var offset = hologram.translation;
        double x = Math.abs(offset.x * 3) + 1.5, y = Math.max(Math.abs(offset.y * 2), Math.abs(offset.y * 2 + 2)), z = Math.abs(offset.z * 3) + 1.5;
        double radius = Math.sqrt(x * x + y * y + z * z) * hologram.scale * 1.02;
        return new AABB(xPosition() - radius, yPosition() - radius, zPosition() - radius, xPosition() + radius, yPosition() + radius, zPosition() + radius);
    }
    public double renderDistanceSquared(boolean fade) {
        double maximum = java.util.Arrays.stream(li.cil.oc.Settings.get().hologramMaxScaleByTier).max().orElse(1);
        double distance = fade ? li.cil.oc.Settings.get().hologramFadeStartDistance : li.cil.oc.Settings.get().hologramRenderDistance;
        return hologram().scale / Math.max(maximum, 0.333333) * distance * distance;
    }
}
