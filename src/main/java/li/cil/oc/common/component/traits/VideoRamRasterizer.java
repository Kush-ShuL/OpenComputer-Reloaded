package li.cil.oc.common.component.traits;

import li.cil.oc.common.component.GpuTextBuffer;
import li.cil.oc.util.PackedColor;
import net.minecraft.nbt.CompoundTag;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public interface VideoRamRasterizer {
    Map<String, VideoRamDevice> videoDevices();
    void onBufferRamInit(GpuTextBuffer ram);
    void onBufferBitBlt(int col, int row, int w, int h, GpuTextBuffer ram, int fromCol, int fromRow);
    void onBufferRamDestroy(GpuTextBuffer ram);
    final class VirtualRamDevice implements VideoRamDevice {
        public final String owner;
        private final Map<Integer, GpuTextBuffer> buffers = new LinkedHashMap<>();
        public VirtualRamDevice(String owner) { this.owner = owner; }
        @Override public Map<Integer, GpuTextBuffer> videoBuffers() { return buffers; }
    }
    default boolean addBuffer(GpuTextBuffer ram) {
        VideoRamDevice device = videoDevices().computeIfAbsent(ram.owner, VirtualRamDevice::new);
        boolean existed = device.addBuffer(ram);
        if (!existed || ram.dirty) onBufferRamInit(ram);
        return existed;
    }
    default boolean removeBuffer(String owner, int id) {
        VideoRamDevice device = videoDevices().get(owner);
        if (device == null) return false;
        Optional<GpuTextBuffer> ram = device.getBuffer(id);
        if (ram.isEmpty()) return false;
        onBufferRamDestroy(ram.get());
        boolean removed = device.removeBuffers(new int[]{id}) == 1;
        if (device.isEmpty()) videoDevices().remove(owner);
        return removed;
    }
    default int removeAllBuffers(String owner) {
        VideoRamDevice device = videoDevices().get(owner);
        if (device == null) return 0;
        int removed = 0;
        for (int id : device.bufferIndexes()) if (removeBuffer(owner, id)) removed++;
        return removed;
    }
    default int removeAllBuffers() {
        int removed = 0;
        for (String owner : videoDevices().keySet().toArray(String[]::new)) removed += removeAllBuffers(owner);
        return removed;
    }
    default boolean loadBuffer(String owner, int id, CompoundTag nbt) {
        var data = new li.cil.oc.util.TextBuffer(1, 1, PackedColor.Depth.format(li.cil.oc.api.internal.TextBuffer.ColorDepth.OneBit));
        data.load(nbt);
        return addBuffer(new GpuTextBuffer(owner, id, data));
    }
    default Optional<GpuTextBuffer> getBuffer(String owner, int id) {
        VideoRamDevice device = videoDevices().get(owner);
        return device == null ? Optional.empty() : device.getBuffer(id);
    }
}
