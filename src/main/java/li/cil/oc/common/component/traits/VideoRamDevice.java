package li.cil.oc.common.component.traits;

import li.cil.oc.common.component.GpuTextBuffer;
import li.cil.oc.util.PackedColor;
import net.minecraft.nbt.CompoundTag;
import java.util.Map;
import java.util.Optional;

public interface VideoRamDevice {
    int RESERVED_SCREEN_INDEX = 0;
    Map<Integer, GpuTextBuffer> videoBuffers();
    default boolean isEmpty() { return videoBuffers().isEmpty(); }
    default void onBufferRamDestroy(int id) {}
    default int[] bufferIndexes() { return videoBuffers().keySet().stream().mapToInt(Integer::intValue).toArray(); }
    default boolean addBuffer(GpuTextBuffer ram) { return videoBuffers().put(ram.id, ram) != null; }
    default int removeBuffers(int[] ids) {
        int count = 0;
        for (int id : ids) if (videoBuffers().remove(id) != null) { onBufferRamDestroy(id); count++; }
        return count;
    }
    default int removeAllBuffers() { return removeBuffers(bufferIndexes()); }
    default void loadBuffer(String address, int id, CompoundTag tag) {
        var data = new li.cil.oc.util.TextBuffer(1, 1, PackedColor.Depth.format(li.cil.oc.api.internal.TextBuffer.ColorDepth.OneBit));
        data.load(tag);
        addBuffer(new GpuTextBuffer(address, id, data));
    }
    default Optional<GpuTextBuffer> getBuffer(int id) { return Optional.ofNullable(videoBuffers().get(id)); }
    default int nextAvailableBufferIndex() {
        int index = RESERVED_SCREEN_INDEX + 1;
        while (videoBuffers().containsKey(index)) index++;
        return index;
    }
    default int calculateUsedMemory() {
        return videoBuffers().values().stream().mapToInt(buffer -> buffer.data().width * buffer.data().height).sum();
    }
}
