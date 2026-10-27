package li.cil.oc.common.block.entity;

import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.network.Visibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Six isolated OC networks share their fill ratio; no packets or components cross between sides. */
public final class PowerDistributorBlockEntity extends NetworkPowerBlockEntity implements SidedEnvironment, Analyzable {
    private final Connector[] connectors = new Connector[6];
    private ListTag savedConnectors;
    private double globalBuffer, globalBufferSize;
    private double lastSentRatio = -1;
    private int ticksUntilSync;

    public PowerDistributorBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) { super(type, position, state); }
    @Override protected double initialCapacity() { return 0; }
    @Override public Connector node() { return null; }
    private void ensureConnectors() {
        if (!server() || !isPowerAvailable()) return;
        for (int side = 0; side < connectors.length; side++) if (connectors[side] == null) {
            Connector node = Network.newNode(this, Visibility.None).withConnector(Settings.get().bufferDistributor).create();
            connectors[side] = node;
            if (node != null && savedConnectors != null && side < savedConnectors.size()) node.load(savedConnectors.getCompound(side));
        }
        savedConnectors = null;
    }
    @Override protected List<Connector> existingConnectors() {
        List<Connector> result = new ArrayList<>(6);
        for (Connector connector : connectors) if (connector != null) result.add(connector);
        return result;
    }
    @Override protected void ensureNetwork() {
        ensureConnectors();
        for (Connector connector : connectors) if (connector != null && connector.network() == null) { Network.joinOrCreateNetwork(this); break; }
    }
    @Override public Connector sidedNode(Direction side) { ensureConnectors(); return side == null ? null : connectors[side.get3DDataValue()]; }
    @OnlyIn(Dist.CLIENT) @Override public boolean canConnect(Direction side) { return true; }
    @Override public Node[] onAnalyze(Player player, Direction side, float x, float y, float z) { return null; }
    public double globalBuffer() { return globalBuffer; }
    public double globalBufferSize() { return globalBufferSize; }
    @Override public void serverTick() {
        super.serverTick();
        if (!server() || !isPowerAvailable() || level.getGameTime() % Settings.get().tickFrequency != 0) return;
        List<Connector> primary = new ArrayList<>(6);
        List<Object> locks = new ArrayList<>(6);
        Set<Object> networks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Connector connector : connectors) if (connector != null) {
            Object network = connector.network() == null ? this : connector.network();
            if (networks.add(network)) { primary.add(connector); locks.add(network); }
        }
        withLocks(locks, 0, () -> distribute(primary));
        synchronizePowerInformation();
    }
    private static void withLocks(List<Object> locks, int index, Runnable action) {
        if (index == locks.size()) { action.run(); return; }
        synchronized (locks.get(index)) { withLocks(locks, index + 1, action); }
    }
    private void distribute(List<Connector> primary) {
        double sumBuffer = 0, sumSize = 0;
        for (Connector node : primary) { sumBuffer += node.globalBuffer(); sumSize += node.globalBufferSize(); }
        if (sumSize > 0) {
            double ratio = sumBuffer / sumSize;
            // Original PowerBalancer holds all involved network locks while setting each target fill.
            for (Connector node : primary) node.changeBuffer(node.globalBufferSize() * ratio - node.globalBuffer());
        }
        globalBuffer = sumBuffer;
        globalBufferSize = sumSize;
        recordEnergyChanges();
    }
    private void synchronizePowerInformation() {
        double ratio = globalBufferSize > 0 ? globalBuffer / globalBufferSize : 0;
        boolean due = --ticksUntilSync <= 0;
        if (due) ticksUntilSync = Math.max(1, (int) (100 / Settings.get().tickFrequency));
        if (lastSentRatio < 0 || Math.abs(lastSentRatio - ratio) > 0.05 || due && lastSentRatio != ratio) {
            lastSentRatio = ratio;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("oc:connector", Tag.TAG_LIST)) {
            savedConnectors = tag.getList("oc:connector", Tag.TAG_COMPOUND).copy();
            for (int side = 0; side < connectors.length && side < savedConnectors.size(); side++) if (connectors[side] != null) connectors[side].load(savedConnectors.getCompound(side));
        }
        if (tag.contains("oc:globalBuffer")) globalBuffer = tag.getDouble("oc:globalBuffer");
        if (tag.contains("oc:globalBufferSize")) globalBufferSize = tag.getDouble("oc:globalBufferSize");
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag data = new ListTag();
        for (int side = 0; side < connectors.length; side++) {
            CompoundTag connector = new CompoundTag();
            if (connectors[side] != null) connectors[side].save(connector);
            else if (savedConnectors != null && side < savedConnectors.size()) connector = savedConnectors.getCompound(side).copy();
            data.add(connector);
        }
        tag.put("oc:connector", data);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("oc:globalBuffer", globalBuffer); tag.putDouble("oc:globalBufferSize", globalBufferSize);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
