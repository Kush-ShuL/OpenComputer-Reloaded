package li.cil.oc.common.block.entity;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** The original power environments share node persistence and chunk/network lifetime. */
public abstract class NetworkPowerBlockEntity extends BlockEntity implements Environment, EnvironmentHost {
    private Connector connector;
    private CompoundTag savedNode;
    private boolean detached;
    private final Map<Connector, Double> previousEnergy = new IdentityHashMap<>();
    private final AtomicBoolean pendingSave = new AtomicBoolean();

    protected NetworkPowerBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) { super(type, position, state); }
    protected abstract double initialCapacity();
    protected Visibility reachability() { return Visibility.None; }
    protected final boolean server() { return level != null && !level.isClientSide; }
    protected final boolean onServerThread() { return level instanceof ServerLevel server && server.getServer().isSameThread(); }
    @Override public void setChanged() {
        if (server() && !onServerThread()) { pendingSave.set(true); return; }
        pendingSave.getAndSet(false);
        super.setChanged();
    }
    public final boolean isPowerAvailable() { return !detached && !isRemoved(); }
    @Override public Connector node() {
        if (connector == null && server() && isPowerAvailable()) {
            connector = Network.newNode(this, reachability()).withConnector(initialCapacity()).create();
            if (connector != null && savedNode != null) { connector.load(savedNode); savedNode = null; }
        }
        return connector;
    }
    protected List<Connector> existingConnectors() { return connector == null ? List.of() : List.of(connector); }
    protected void ensureNetwork() {
        Connector node = node();
        if (node != null && node.network() == null) Network.joinOrCreateNetwork(this);
    }
    public void serverTick() {
        if (!server() || !isPowerAvailable()) return;
        if (pendingSave.getAndSet(false)) super.setChanged();
        ensureNetwork();
        recordEnergyChanges();
    }
    protected final void recordEnergyChanges() {
        for (Connector node : existingConnectors()) {
            double energy = node.localBuffer();
            Double previous = previousEnergy.put(node, energy);
            if (previous == null || previous.doubleValue() != energy) setChanged();
        }
    }
    @Override public final Level world() { return level; }
    @Override public final double xPosition() { return worldPosition.getX() + 0.5; }
    @Override public final double yPosition() { return worldPosition.getY() + 0.5; }
    @Override public final double zPosition() { return worldPosition.getZ() + 0.5; }
    @Override public final void markChanged() { setChanged(); }
    @Override public void onConnect(Node node) {}
    @Override public void onDisconnect(Node node) {
        if (node == connector && !detached) {
            double capacity = connector.localBufferSize();
            connector.setLocalBufferSize(0);
            connector.setLocalBufferSize(capacity);
        }
        setChanged();
    }
    @Override public void onMessage(Message message) {}
    @Override public void onLoad() { super.onLoad(); detached = false; if (server()) ensureNetwork(); }
    public final void onBlockDestroyed() { detach(true); }
    private void detach(boolean redistribute) {
        if (detached) return;
        detached = true;
        for (Connector node : existingConnectors()) {
            if (redistribute && node.network() != null) {
                double capacity = node.localBufferSize();
                node.setLocalBufferSize(0);
                node.setLocalBufferSize(capacity);
            }
            node.remove();
        }
        afterDetach();
    }
    protected void afterDetach() {}
    @Override public void setRemoved() { detach(false); super.setRemoved(); }
    @Override public void onChunkUnloaded() { detach(false); super.onChunkUnloaded(); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("oc:node")) {
            savedNode = tag.getCompound("oc:node").copy();
            if (connector != null && server()) { connector.load(savedNode); savedNode = null; }
        }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (connector != null) { CompoundTag data = new CompoundTag(); connector.save(data); tag.put("oc:node", data); }
        else if (savedNode != null) tag.put("oc:node", savedNode.copy());
    }
}
