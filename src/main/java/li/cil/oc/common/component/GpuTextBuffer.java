package li.cil.oc.common.component;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.api.internal.TextBuffer;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.common.component.traits.TextBufferProxy;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Off-screen VRAM; it deliberately has no world node, input, power or renderer. */
public class GpuTextBuffer implements TextBufferProxy {
    public final String owner;
    public final int id;
    private final li.cil.oc.util.TextBuffer data;
    public boolean dirty = true;
    public GpuTextBuffer(String owner, int id, li.cil.oc.util.TextBuffer data) { this.owner = owner; this.id = id; this.data = data; }
    @Override public li.cil.oc.util.TextBuffer data() { return data; }
    @Override public Node node() { throw new IllegalStateException("GpuTextBuffers do not have nodes"); }
    @Override public int getMaximumWidth() { return data.width; }
    @Override public int getMaximumHeight() { return data.height; }
    @Override public int getViewportWidth() { return data.width; }
    @Override public int getViewportHeight() { return data.height; }
    @Override public void onBufferSet(int col, int row, String text, boolean vertical) { dirty = true; }
    @Override public void onBufferColorChange() { dirty = true; }
    @Override public void onBufferCopy(int col, int row, int w, int h, int tx, int ty) { dirty = true; }
    @Override public void onBufferFill(int col, int row, int w, int h, int cp) { dirty = true; }
    @Override public void onBufferPaletteChange(int index) { dirty = true; }
    @Override public void load(CompoundTag tag) { data.load(tag); dirty = true; }
    @Override public void save(CompoundTag tag) { data.save(tag); dirty = false; }
    @Override public void setEnergyCostPerTick(double value) {}
    @Override public double getEnergyCostPerTick() { return 0; }
    @Override public void setPowerState(boolean value) {}
    @Override public boolean getPowerState() { return false; }
    @Override public void setMaximumResolution(int width, int height) {}
    @Override public void setAspectRatio(double width, double height) {}
    @Override public double getAspectRatio() { return 1; }
    @Override public boolean setResolution(int width, int height) { return false; }
    @Override public boolean setViewport(int width, int height) { return false; }
    @Override public void setMaximumColorDepth(ColorDepth depth) {}
    @Override public ColorDepth getMaximumColorDepth() { return data.getFormat().depth(); }
    @Override public boolean renderText(PoseStack pose, MultiBufferSource buffers, int light) { return false; }
    @Override public int renderWidth() { return 0; }
    @Override public int renderHeight() { return 0; }
    @Override public void setRenderingEnabled(boolean enabled) {}
    @Override public boolean isRenderingEnabled() { return false; }
    @Override public void keyDown(char character, int code, Player player) {}
    @Override public void keyUp(char character, int code, Player player) {}
    @Override public void clipboard(String value, Player player) {}
    @Override public void mouseDown(double x, double y, int button, Player player) {}
    @Override public void mouseDrag(double x, double y, int button, Player player) {}
    @Override public void mouseUp(double x, double y, int button, Player player) {}
    @Override public void mouseScroll(double x, double y, int delta, Player player) {}
    @Override public boolean canUpdate() { return false; }
    @Override public void update() {}
    @Override public void onConnect(Node node) {}
    @Override public void onDisconnect(Node node) {}
    @Override public void onMessage(Message message) {}

    public static void bitblt(TextBuffer destination, int col, int row, int w, int h, TextBuffer source, int fromCol, int fromRow) {
        int x = col - 1, y = row - 1, fx = fromCol - 1, fy = fromRow - 1;
        if (x < 0) { w += x; fx -= x; x = 0; }
        if (y < 0) { h += y; fy -= y; y = 0; }
        if (fx < 0) { w += fx; x -= fx; fx = 0; }
        if (fy < 0) { h += fy; y -= fy; fy = 0; }
        w -= Math.max(0, x + w - destination.getWidth());
        w -= Math.max(0, fx + w - source.getWidth());
        h -= Math.max(0, y + h - destination.getHeight());
        h -= Math.max(0, fy + h - source.getHeight());
        if (w <= 0 || h <= 0) return;
        if (destination instanceof li.cil.oc.common.component.TextBuffer screen && source instanceof GpuTextBuffer gpu) {
            if (screen.data().rawcopy(x + 1, y + 1, w, h, gpu.data, fx + 1, fy + 1)) {
                screen.addBuffer(gpu);
                screen.onBufferBitBlt(x + 1, y + 1, w, h, gpu, fx + 1, fy + 1);
            }
        } else if (destination instanceof GpuTextBuffer gpu && source instanceof TextBufferProxy proxy) {
            if (gpu.data.rawcopy(x + 1, y + 1, w, h, proxy.data(), fx + 1, fy + 1)) gpu.dirty = true;
        } else throw new UnsupportedOperationException("Buffer does not support this bitblt operation");
    }
}
