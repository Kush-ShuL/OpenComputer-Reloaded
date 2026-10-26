package li.cil.oc.common.block.entity;

import li.cil.oc.Constants;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Map;

/** A network buffer with the original six immediate and six half-strength axial bonuses. */
public class CapacitorBlockEntity extends NetworkPowerBlockEntity implements DeviceInfo {
    private int previousComparator = -1;
    public CapacitorBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) { super(type, position, state); }
    @Override protected Visibility reachability() { return Visibility.Network; }
    @Override protected double initialCapacity() { return Settings.get().bufferCapacitor + Settings.get().bufferCapacitorAdjacencyBonus * 9; }
    protected String product() { return "CapBank3x"; }
    @Override public Map<String, String> getDeviceInfo() {
        return Map.of(DeviceAttribute.Class, DeviceClass.Power, DeviceAttribute.Description, "Battery",
                DeviceAttribute.Vendor, Constants.DeviceInfo.DefaultVendor, DeviceAttribute.Product, product(),
                DeviceAttribute.Capacity, Double.toString(initialCapacity()));
    }
    @Override public void onConnect(Node node) { if (node == node()) recomputeCapacity(true); }
    private CapacitorBlockEntity capacitor(BlockPos pos) {
        if (level == null || !level.hasChunkAt(pos)) return null;
        return level.getBlockEntity(pos) instanceof CapacitorBlockEntity capacitor && capacitor.isPowerAvailable() ? capacitor : null;
    }
    public void recomputeCapacity() { recomputeCapacity(false); }
    public void recomputeCapacity(boolean updateNeighbors) {
        if (!server() || !isPowerAvailable()) return;
        var node = node(); if (node == null) return;
        double bonus = Settings.get().bufferCapacitorAdjacencyBonus, capacity = Settings.get().bufferCapacitor;
        for (Direction side : Direction.values()) {
            if (capacitor(worldPosition.relative(side)) != null) capacity += bonus;
            if (capacitor(worldPosition.relative(side, 2)) != null) capacity += bonus / 2;
        }
        if (node.localBufferSize() != capacity) { node.setLocalBufferSize(capacity); setChanged(); }
        if (updateNeighbors) updateNeighborCapacitors();
        updateComparator();
    }
    private void updateNeighborCapacitors() {
        if (!server()) return;
        for (Direction side : Direction.values()) for (int distance = 1; distance <= 2; distance++) {
            CapacitorBlockEntity neighbor = capacitor(worldPosition.relative(side, distance));
            if (neighbor != null) neighbor.recomputeCapacity();
        }
    }
    public int comparatorValue() {
        var node = node(); if (node == null || node.localBufferSize() <= 0) return 0;
        return (int) Math.clamp(Math.round(15 * node.localBuffer() / node.localBufferSize()), 0, 15);
    }
    private void updateComparator() {
        int comparator = comparatorValue();
        if (comparator != previousComparator) { previousComparator = comparator; level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock()); }
    }
    @Override public void serverTick() { super.serverTick(); if (server() && isPowerAvailable()) updateComparator(); }
    @Override protected void afterDetach() { updateNeighborCapacitors(); }
}
