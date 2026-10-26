package li.cil.oc.common.component;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.Constants;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.ComponentConnector;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.common.component.traits.TextBufferProxy;
import li.cil.oc.common.component.traits.VideoRamDevice;
import li.cil.oc.common.component.traits.VideoRamRasterizer;
import li.cil.oc.util.PackedColor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Source-derived screen state, energy accounting, input routing and batched updates.
 * Platform network/save/render services are supplied explicitly by the real host.
 */
public class TextBuffer extends AbstractManagedEnvironment implements TextBufferProxy, VideoRamRasterizer, DeviceInfo {
    public record Command(String type, Object[] payload) {
        public Command { payload = payload.clone(); }
        @Override public Object[] payload() { return payload.clone(); }
    }
    public interface HostServices {
        boolean client();
        void sendCommands(EnvironmentHost host, String address, List<Command> commands);
        void requestInitialization(String address);
        void register(EnvironmentHost host, String address, TextBuffer buffer);
        void unregister(EnvironmentHost host, TextBuffer buffer);
        void persistBuffer(EnvironmentHost host, CompoundTag metadata, String path, Consumer<CompoundTag> writer);
        CompoundTag loadBuffer(CompoundTag metadata, String path);
        Collection<Node> keyboardNodes(TextBuffer buffer);
        void sendInput(String address, String type, Object... payload);
        @OnlyIn(Dist.CLIENT) boolean render(TextBuffer buffer, PoseStack pose, MultiBufferSource buffers, int light);
        int characterWidth();
        int characterHeight();
    }

    private static final List<TextBuffer> CLIENT_BUFFERS = new CopyOnWriteArrayList<>();
    public final EnvironmentHost host;
    protected final HostServices services;
    private final ComponentConnector componentNode;
    private final li.cil.oc.util.TextBuffer data;
    private final Map<String, VideoRamDevice> videoDevices = new LinkedHashMap<>();
    private final List<Command> pendingCommands = new ArrayList<>();
    private int maxWidth, maxHeight, viewportWidth, viewportHeight;
    private ColorDepth maxDepth;
    private double aspectWidth = 1, aspectHeight = 1, powerConsumptionPerTick, relativeLitArea = -1;
    public double fullyLitCost;
    private boolean precisionMode, isRendering = true, isDisplaying = true, hasPower = true;
    public volatile boolean dirty;
    private String nodeAddress = "";
    private int syncCooldown = 100;

    public TextBuffer(EnvironmentHost host, HostServices services) {
        this.host = Objects.requireNonNull(host);
        this.services = Objects.requireNonNull(services);
        maxWidth = Settings.screenResolutionsByTier[0][0];
        maxHeight = Settings.screenResolutionsByTier[0][1];
        maxDepth = Settings.screenDepthsByTier[0];
        powerConsumptionPerTick = Settings.get().screenCost;
        data = new li.cil.oc.util.TextBuffer(maxWidth, maxHeight, PackedColor.Depth.format(maxDepth));
        viewportWidth = data.width; viewportHeight = data.height;
        componentNode = services.client() ? null : Network.newNode(this, Visibility.Network).withComponent("screen").withConnector().create();
        setNode(componentNode);
        fullyLitCost = computeFullyLitCost();
    }
    @Override public li.cil.oc.util.TextBuffer data() { return data; }
    @Override public ComponentConnector node() { return componentNode; }
    @Override public Map<String, VideoRamDevice> videoDevices() { return videoDevices; }
    public String nodeAddress() { return componentNode == null ? nodeAddress : componentNode.address(); }
    public double computeFullyLitCost() {
        int[] base = Settings.screenResolutionsByTier[0];
        return powerConsumptionPerTick * maxWidth * maxHeight / (base[0] * base[1]);
    }
    public void markInitialized() { syncCooldown = -1; relativeLitArea = -1; }
    @Override public Map<String, String> getDeviceInfo() {
        return Map.of(DeviceAttribute.Class, DeviceClass.Display, DeviceAttribute.Description, "Text buffer",
                DeviceAttribute.Vendor, Constants.DeviceInfo.DefaultVendor, DeviceAttribute.Product, "Text Screen V0",
                DeviceAttribute.Capacity, Integer.toString(maxWidth * maxHeight), DeviceAttribute.Width, Integer.toString(PackedColor.Depth.bits(maxDepth)));
    }
    @Override public boolean canUpdate() { return true; }
    @Override public void update() {
        int frequency = Math.max(1, (int) Settings.get().tickFrequency);
        if (isDisplaying && host.world() != null && host.world().getGameTime() % frequency == 0) {
            if (relativeLitArea < 0) {
                int lit = 0;
                for (int y = 0; y < viewportHeight; y++) for (int x = 0; x < viewportWidth; x++) {
                    int cp = data.buffer[y][x]; short c = data.color[y][x];
                    int bg = PackedColor.unpackBackground(c, data.getFormat()), fg = PackedColor.unpackForeground(c, data.getFormat());
                    if (cp == ' ' ? bg != 0 : cp == 0x2588 ? fg != 0 : fg != 0 || bg != 0) lit++;
                }
                relativeLitArea = lit / (double) (viewportWidth * viewportHeight);
            }
            if (componentNode != null) {
                boolean wasPowered = hasPower;
                hasPower = componentNode.tryChangeBuffer(-relativeLitArea * fullyLitCost * frequency);
                if (hasPower != wasPowered) command("power", isDisplaying && hasPower);
            }
        }
        flushCommands();
        if (services.client() && syncCooldown > 0 && --syncCooldown == 0) {
            syncCooldown = 100;
            services.requestInitialization(nodeAddress());
        }
    }
    public void flushCommands() {
        // Keep the queued mutation order intact: workers leave draining and packet publication to the host tick.
        if (!services.client() && host.world() instanceof net.minecraft.server.level.ServerLevel server && !server.getServer().isSameThread()) return;
        List<Command> commands;
        synchronized (pendingCommands) { commands = List.copyOf(pendingCommands); pendingCommands.clear(); }
        if (!commands.isEmpty()) services.sendCommands(host, nodeAddress(), commands);
    }
    public boolean hasPower() { return hasPower; }
    @Callback(direct = true, doc = "function():boolean -- Returns whether the screen is currently on.")
    public Object[] isOn(Context context, Arguments args) { return new Object[]{isDisplaying}; }
    @Callback(doc = "function():boolean,boolean -- Turns the screen on; returns whether it changed and its current state.")
    public Object[] turnOn(Context context, Arguments args) { boolean old = isDisplaying; setPowerState(true); return new Object[]{old != isDisplaying, isDisplaying}; }
    @Callback(doc = "function():boolean,boolean -- Turns the screen off; returns whether it changed and its current state.")
    public Object[] turnOff(Context context, Arguments args) { boolean old = isDisplaying; setPowerState(false); return new Object[]{old != isDisplaying, isDisplaying}; }
    @Callback(direct = true, doc = "function():number,number -- Physical aspect ratio of the display.")
    public synchronized Object[] getAspectRatio(Context context, Arguments args) { return new Object[]{aspectWidth, aspectHeight}; }
    @Callback(doc = "function():table -- Lists attached keyboards.")
    public Object[] getKeyboards(Context context, Arguments args) {
        context.pause(0.25);
        return new Object[]{services.keyboardNodes(this).stream().map(Node::address).toArray(String[]::new)};
    }
    @Callback(direct = true, doc = "function():boolean -- Returns whether sub-pixel mouse coordinates are enabled.")
    public Object[] isPrecise(Context context, Arguments args) { return new Object[]{precisionMode}; }
    @Callback(doc = "function(enabled:boolean):boolean -- Enables precise mouse coordinates on tier three screens.")
    public Object[] setPrecise(Context context, Arguments args) {
        if (maxDepth != Settings.screenDepthsByTier[2]) return new Object[]{null, "unsupported operation"};
        boolean old = precisionMode; precisionMode = args.checkBoolean(0); return new Object[]{old};
    }
    @Override public void setEnergyCostPerTick(double value) { powerConsumptionPerTick = value; fullyLitCost = computeFullyLitCost(); }
    @Override public double getEnergyCostPerTick() { return powerConsumptionPerTick; }
    @Override public void setPowerState(boolean value) {
        if (isDisplaying == value) return;
        isDisplaying = value;
        if (isDisplaying && componentNode != null) hasPower = componentNode.changeBuffer(-fullyLitCost * Settings.get().tickFrequency) == 0;
        command("power", isDisplaying && hasPower);
    }
    @Override public boolean getPowerState() { return isDisplaying; }
    public void applyPowerState(boolean powered) { hasPower = powered; isRendering = powered; dirty = true; }
    @Override public void setMaximumResolution(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("dimensions must be at least one");
        maxWidth = width; maxHeight = height; fullyLitCost = computeFullyLitCost(); command("maxResolution", width, height);
    }
    @Override public int getMaximumWidth() { return maxWidth; }
    @Override public int getMaximumHeight() { return maxHeight; }
    @Override public synchronized void setAspectRatio(double width, double height) { aspectWidth = width; aspectHeight = height; }
    @Override public synchronized double getAspectRatio() { return aspectWidth / aspectHeight; }
    @Override public boolean setResolution(int width, int height) {
        if (width < 1 || height < 1 || width > maxWidth || height > maxWidth || (long) width * height > (long) maxWidth * maxHeight)
            throw new IllegalArgumentException("unsupported resolution");
        command("resolution", width, height);
        boolean resized = data.setSize(width, height), viewportChanged = setViewport(width, height);
        if (resized && !viewportChanged && componentNode != null) componentNode.sendToReachable("computer.signal", "screen_resized", width, height);
        return resized || viewportChanged;
    }
    @Override public boolean setViewport(int width, int height) {
        if (width < 1 || height < 1 || width > data.width || height > data.height) throw new IllegalArgumentException("unsupported viewport resolution");
        command("viewport", width, height);
        if (viewportWidth == width && viewportHeight == height) return false;
        viewportWidth = width; viewportHeight = height;
        if (componentNode != null) componentNode.sendToReachable("computer.signal", "screen_resized", width, height);
        return true;
    }
    @Override public int getViewportWidth() { return viewportWidth; }
    @Override public int getViewportHeight() { return viewportHeight; }
    @Override public void setMaximumColorDepth(ColorDepth depth) { maxDepth = depth; }
    @Override public ColorDepth getMaximumColorDepth() { return maxDepth; }
    @Override public boolean setColorDepth(ColorDepth depth) { boolean changed = TextBufferProxy.super.setColorDepth(depth); command("depth", depth); return changed; }
    @Override public void onBufferPaletteChange(int index) { command("palette", index, getPaletteColor(index)); }
    @Override public void onBufferColorChange() { command("color", data.getForeground(), data.getBackground()); }
    @Override public void onBufferCopy(int col, int row, int w, int h, int tx, int ty) { command("copy", col, row, w, h, tx, ty); }
    @Override public void onBufferFill(int col, int row, int w, int h, int cp) { command("fill", col, row, w, h, cp); }
    @Override public void onBufferSet(int col, int row, String text, boolean vertical) { command("set", col, row, text, vertical); }
    @Override public void onBufferBitBlt(int col, int row, int w, int h, GpuTextBuffer ram, int fromCol, int fromRow) {
        command("bitblt", col, row, w, h, ram.owner, ram.id, fromCol, fromRow);
    }
    @Override public void onBufferRamInit(GpuTextBuffer ram) { CompoundTag tag = new CompoundTag(); ram.save(tag); command("ramInit", ram.owner, ram.id, tag); }
    @Override public void onBufferRamDestroy(GpuTextBuffer ram) { command("ramDestroy", ram.owner, ram.id); }
    @Override public void rawSetText(int col, int row, int[][] text) { TextBufferProxy.super.rawSetText(col, row, text); command("rawText", col, row, snapshot(text)); }
    @Override public void rawSetForeground(int col, int row, int[][] color) { TextBufferProxy.super.rawSetForeground(col, row, color); command("rawForeground", col, row, snapshot(color)); }
    @Override public void rawSetBackground(int col, int row, int[][] color) { TextBufferProxy.super.rawSetBackground(col, row, color); command("rawBackground", col, row, snapshot(color)); }
    private static int[][] snapshot(int[][] values) { return java.util.Arrays.stream(values).map(int[]::clone).toArray(int[][]::new); }
    private void command(String type, Object... payload) {
        relativeLitArea = -1;
        if (services.client()) dirty = true;
        else { host.markChanged(); synchronized (pendingCommands) { pendingCommands.add(new Command(type, payload)); } }
    }
    @OnlyIn(Dist.CLIENT) @Override public boolean renderText(PoseStack pose, MultiBufferSource buffers, int light) {
        return isRendering && relativeLitArea != 0 && services.render(this, pose, buffers, light);
    }
    @OnlyIn(Dist.CLIENT) @Override public int renderWidth() { return services.characterWidth() * viewportWidth; }
    @OnlyIn(Dist.CLIENT) @Override public int renderHeight() { return services.characterHeight() * viewportHeight; }
    @Override public void setRenderingEnabled(boolean enabled) { isRendering = enabled; }
    @Override public boolean isRenderingEnabled() { return isRendering; }
    @Override public void keyDown(char character, int code, Player player) { keyboardInput("keyboard.keyDown", player, character, code); }
    @Override public void keyUp(char character, int code, Player player) { keyboardInput("keyboard.keyUp", player, character, code); }
    @Override public void clipboard(String value, Player player) { if(value!=null&&value.length()<=Settings.get().maxClipboardTextLength)keyboardInput("keyboard.clipboard", player, value); }
    private void keyboardInput(String name, Player player, Object... values) {
        if (services.client()) { services.sendInput(nodeAddress(), name, values); return; }
        Object[] payload = new Object[values.length + 1]; payload[0] = player; System.arraycopy(values, 0, payload, 1, values.length);
        if (componentNode != null) for (Node keyboard : services.keyboardNodes(this)) componentNode.sendToAddress(keyboard.address(), name, payload);
    }
    @Override public void mouseDown(double x, double y, int button, Player player) { mouseInput(player, "touch", x, y, button); }
    @Override public void mouseDrag(double x, double y, int button, Player player) { mouseInput(player, "drag", x, y, button); }
    @Override public void mouseUp(double x, double y, int button, Player player) { mouseInput(player, "drop", x, y, button); }
    @Override public void mouseScroll(double x, double y, int delta, Player player) { mouseInput(player, "scroll", x, y, delta); }
    private void mouseInput(Player player, String name, double x, double y, int value) {
        if (services.client()) { services.sendInput(nodeAddress(), name, x, y, value); return; }
        ArrayList<Object> payload = new ArrayList<>(); payload.add(player); payload.add(name);
        if (precisionMode) { payload.add(x); payload.add(y); }
        else { payload.add((int) x + 1); payload.add((int) y + 1); }
        payload.add(value);
        if (Settings.get().inputUsername && player != null) payload.add(player.getGameProfile().getName());
        if (componentNode != null) componentNode.sendToReachable("computer.checked_signal", payload.toArray());
    }
    public void copyToAnalyzer(int line, Player player) {
        if (services.client()) { services.sendInput(nodeAddress(), "copyToAnalyzer", line); return; }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove("oc:clipboard");
        if (line >= 0 && line < viewportHeight) { String text = data.lineToString(line); if (!text.isEmpty()) tag.putString("oc:clipboard", text); }
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA); else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    @Override public void onConnect(Node node) { if (node == componentNode) services.register(host, node.address(), this); }
    @Override public void onDisconnect(Node node) { if (node == componentNode) services.unregister(host, this); }
    private String bufferPath() { return nodeAddress() + "_buffer"; }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (services.client()) {
            if (!nodeAddress.isEmpty()) return;
            nodeAddress = tag.getCompound("node").getString("address");
            services.register(host, nodeAddress, this); services.requestInitialization(nodeAddress); CLIENT_BUFFERS.add(this);
        } else if (tag.contains("buffer")) data.load(tag.getCompound("buffer"));
        else if (nodeAddress() != null && !nodeAddress().isEmpty()) data.load(services.loadBuffer(tag, bufferPath()));
        if (tag.contains("oc:isOn")) isDisplaying = tag.getBoolean("oc:isOn");
        if (tag.contains("oc:hasPower")) hasPower = tag.getBoolean("oc:hasPower");
        if (tag.contains("oc:maxWidth") && tag.contains("oc:maxHeight")) { maxWidth = tag.getInt("oc:maxWidth"); maxHeight = tag.getInt("oc:maxHeight"); }
        precisionMode = tag.getBoolean("oc:precise");
        viewportWidth = tag.contains("oc:viewportWidth") ? Math.clamp(tag.getInt("oc:viewportWidth"), 1, data.width) : data.width;
        viewportHeight = tag.contains("oc:viewportHeight") ? Math.clamp(tag.getInt("oc:viewportHeight"), 1, data.height) : data.height;
        fullyLitCost = computeFullyLitCost(); relativeLitArea = -1;
    }
    @Override public void save(CompoundTag tag) {
        if (componentNode == null) return;
        super.save(tag);
        if (componentNode.network() != null) for (Node node : componentNode.network().nodes())
            if (node.host() instanceof Machine machine && !machine.isPaused()) machine.pause(0.1);
        services.persistBuffer(host, tag, bufferPath(), data::save);
        tag.putBoolean("oc:isOn", isDisplaying); tag.putBoolean("oc:hasPower", hasPower);
        tag.putInt("oc:maxWidth", maxWidth); tag.putInt("oc:maxHeight", maxHeight);
        tag.putBoolean("oc:precise", precisionMode); tag.putInt("oc:viewportWidth", viewportWidth); tag.putInt("oc:viewportHeight", viewportHeight);
    }
    @SubscribeEvent public static void onChunkUnload(ChunkEvent.Unload event) {
        var pos = event.getChunk().getPos();
        CLIENT_BUFFERS.removeIf(buffer -> {
            boolean remove = buffer.host.world() == event.getLevel() && ((int) Math.floor(buffer.host.xPosition()) >> 4) == pos.x && ((int) Math.floor(buffer.host.zPosition()) >> 4) == pos.z;
            if (remove) buffer.services.unregister(buffer.host, buffer);
            return remove;
        });
    }
    @SubscribeEvent public static void onWorldUnload(LevelEvent.Unload event) {
        CLIENT_BUFFERS.removeIf(buffer -> { boolean remove = buffer.host.world() == event.getLevel(); if (remove) buffer.services.unregister(buffer.host, buffer); return remove; });
    }
}
