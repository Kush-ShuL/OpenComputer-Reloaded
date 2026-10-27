package li.cil.oc.common.block.entity;

import li.cil.oc.Constants;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Node;
import li.cil.oc.common.capabilities.EnergyProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import java.util.Map;

/** Accepts FE into the complete OC network, preserving Common.tryChangeBuffer throughput. */
public final class PowerConverterBlockEntity extends NetworkPowerBlockEntity implements EnergyProvider, DeviceInfo, Analyzable {
    public PowerConverterBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) { super(type, position, state); }
    @Override protected double initialCapacity() { return Settings.get().bufferConverter; }
    public double energyThroughput() { return Settings.get().powerConverterRate; }
    public double globalDemand() { var node = node(); return node == null ? 0 : Math.max(0, Math.min(energyThroughput(), node.globalBufferSize() - node.globalBuffer())); }
    private static double ratio() { double value = Settings.get().ratioForgeEnergy; return Double.isFinite(value) && value > 0 ? value : 0; }
    private static int inForgeEnergy(double value) { double ratio = ratio(); return ratio == 0 ? 0 : (int) Math.clamp(Math.floor(value / ratio), 0, Integer.MAX_VALUE); }
    private final IEnergyStorage energy = new IEnergyStorage() {
        @Override public int receiveEnergy(int maximum, boolean simulate) {
            double ratio = ratio();
            if (maximum <= 0 || ratio == 0 || !server() || Settings.get().ignorePower || !isPowerAvailable()) return 0;
            var node = node(); if (node == null) return 0;
            Object lock = node.network() == null ? node : node.network();
            synchronized (lock) {
                int accepted = Math.min(maximum, inForgeEnergy(globalDemand()));
                if (!simulate && accepted > 0) { node.changeBuffer(accepted * ratio); setChanged(); }
                return accepted;
            }
        }
        @Override public int extractEnergy(int maximum, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { var node = node(); return node == null ? 0 : inForgeEnergy(node.globalBuffer()); }
        @Override public int getMaxEnergyStored() { var node = node(); return node == null ? 0 : inForgeEnergy(node.globalBufferSize()); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return isPowerAvailable() && !Settings.get().ignorePower && ratio() > 0 && energyThroughput() > 0; }
    };
    @Override public IEnergyStorage energy(Direction side) { return energy; }
    @Override public Node[] onAnalyze(Player player, Direction side, float x, float y, float z) { return null; }
    @Override public Map<String, String> getDeviceInfo() {
        return Map.of(DeviceAttribute.Class, DeviceClass.Power, DeviceAttribute.Description, "Power converter",
                DeviceAttribute.Vendor, Constants.DeviceInfo.DefaultVendor, DeviceAttribute.Product, "Transgizer-PX5",
                DeviceAttribute.Capacity, Double.toString(energyThroughput()));
    }
}
