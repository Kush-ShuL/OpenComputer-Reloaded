package li.cil.oc.common.component;

import li.cil.oc.api.internal.TextBuffer;
import li.cil.oc.common.component.traits.VideoRamRasterizer;
import net.minecraft.nbt.CompoundTag;

/** Client packet operations on the same VRAM and bitblt algorithms as the server. */
public final class ClientGpuTextBufferHandler {
    private ClientGpuTextBufferHandler() {}
    public static void bitblt(TextBuffer destination, int col, int row, int w, int h, String owner, int sourceId, int fromCol, int fromRow) {
        if (destination instanceof VideoRamRasterizer rasterizer) rasterizer.getBuffer(owner, sourceId)
                .ifPresent(buffer -> GpuTextBuffer.bitblt(destination, col, row, w, h, buffer, fromCol, fromRow));
    }
    public static boolean removeBuffer(TextBuffer buffer, String owner, int id) {
        return buffer instanceof VideoRamRasterizer rasterizer && rasterizer.removeBuffer(owner, id);
    }
    public static boolean loadBuffer(TextBuffer buffer, String owner, int id, CompoundTag data) {
        return buffer instanceof VideoRamRasterizer rasterizer && rasterizer.loadBuffer(owner, id, data);
    }
}
