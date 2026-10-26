package li.cil.oc.server.network;

import li.cil.oc.Settings;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Visibility;
import net.minecraft.nbt.CompoundTag;

public class Connector extends Node implements li.cil.oc.api.network.Connector {
    double buffer, capacity;
    Connector(Environment host, Visibility reachability, double capacity) { super(host, reachability); this.capacity = Math.max(0, capacity); }
    public double localBuffer() { synchronized (energyLock()) { return buffer; } }
    public double localBufferSize() { synchronized (energyLock()) { return capacity; } }
    public double globalBuffer() { synchronized (energyLock()) { return graph == null ? buffer : graph.buffer; } }
    public double globalBufferSize() { synchronized (energyLock()) { return graph == null ? capacity : graph.capacity; } }
    Object energyLock() { return graph == null ? this : graph; }
    public double changeBuffer(double delta) {
        if (Settings.get().ignorePower) return delta < 0 ? 0 : delta;
        synchronized (energyLock()) {
            double before = buffer;
            if (capacity <= 0) return graph == null ? delta : graph.changeBuffer(delta);
            buffer += delta;
            double remaining;
            if (buffer < 0) { remaining = buffer; buffer = 0; }
            else if (buffer > capacity) { remaining = buffer - capacity; buffer = capacity; }
            else remaining = 0;
            if (graph != null) { graph.buffer = Math.max(0, Math.min(graph.capacity, graph.buffer - before + buffer)); return graph.changeBuffer(remaining); }
            return remaining;
        }
    }
    public boolean tryChangeBuffer(double delta) {
        if (delta == 0) return true;
        if (Settings.get().ignorePower) return delta < 0;
        synchronized (energyLock()) {
            if (graph != null) {
                if (buffer > capacity) {
                    double surplus = buffer - capacity;
                    buffer = capacity;
                    graph.refreshEnergy();
                    graph.changeBuffer(surplus);
                }
                double next = graph.buffer + delta;
                return (delta > 0 || next >= 0) && (delta < 0 || next <= graph.capacity) && graph.changeBuffer(delta) == 0;
            }
            double next = buffer + delta;
            if ((delta < 0 && next < 0) || (delta > 0 && next > capacity)) return false;
            buffer = next;
            return true;
        }
    }
    public void setLocalBufferSize(double size) {
        synchronized (energyLock()) {
            capacity = Math.max(0, size);
            double surplus = Math.max(0, buffer - capacity);
            buffer -= surplus;
            if (graph != null) { graph.refreshEnergy(); graph.changeBuffer(surplus); }
        }
    }
    public void load(CompoundTag nbt) { super.load(nbt); synchronized (energyLock()) { buffer = Math.max(0, Math.min(capacity, nbt.getDouble("buffer"))); if (graph != null) graph.refreshEnergy(); } }
    public void save(CompoundTag nbt) { super.save(nbt); nbt.putDouble("buffer", localBuffer()); }
}
