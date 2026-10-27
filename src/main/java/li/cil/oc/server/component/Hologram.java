package li.cil.oc.server.component;

import li.cil.oc.Constants;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ComponentConnector;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.common.SaveHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;

/** MightyPirates' 48x32x48 projector, retaining the two original column bitplanes and Lua operations. */
public final class Hologram extends AbstractManagedEnvironment implements DeviceInfo {
    public interface Host extends EnvironmentHost { void synchronizeHologram(CompoundTag update); }
    public static final int WIDTH = 48, HEIGHT = 32, PLANE = WIDTH * WIDTH;
    public final int tier;
    public final Host host;
    public final int[] volume = new int[PLANE * 2];
    /** RGB in storage/API order; the renderer converts it when writing the vertex color. */
    public final int[] colors;
    public double scale = 1, litRatio = -1;
    public Vec3 translation = Vec3.ZERO;
    public boolean hasPower = true;
    public float rotationAngle, rotationX, rotationY, rotationZ;
    public float rotationSpeed, rotationSpeedX, rotationSpeedY, rotationSpeedZ;
    public long renderRevision;
    private final BitSet dirty = new BitSet(PLANE);
    private boolean settingsDirty;
    private boolean cleared;

    public Hologram(Host host, int tier) {
        this.host = host; this.tier = Math.clamp(tier, 0, 1);
        colors = this.tier == 0 ? new int[]{0x00FF00} : new int[]{0xFF0000, 0x00FF00, 0x0000FF};
        setNode(host.world() != null && host.world().isClientSide ? null : Network.newNode(this, Visibility.Network).withComponent("hologram").withConnector().create());
    }
    @Override public ComponentConnector node() { return (ComponentConnector) super.node(); }
    @Override public Map<String, String> getDeviceInfo() {
        return Map.of(DeviceAttribute.Class, DeviceClass.Display, DeviceAttribute.Description, "Holographic projector",
                DeviceAttribute.Vendor, Constants.DeviceInfo.DefaultVendor, DeviceAttribute.Product, "VirtualViewer H1-" + (tier + 1),
                DeviceAttribute.Capacity, Integer.toString(WIDTH * WIDTH * HEIGHT), DeviceAttribute.Width, Integer.toString(colors.length));
    }
    public int getColor(int x, int y, int z) {
        int column = x + z * WIDTH;
        return ((volume[column] >>> y) & 1) | (((volume[column + PLANE] >>> y) & 1) << 1);
    }
    public synchronized void setColor(int x, int y, int z, int value) {
        if (getColor(x, y, z) == (value & 3)) return;
        int index = x + z * WIDTH, mask = 1 << y;
        volume[index] = (volume[index] & ~mask) | ((value & 1) << y);
        volume[index + PLANE] = (volume[index + PLANE] & ~mask) | (((value >>> 1) & 1) << y);
        dirty(index);
    }
    private void dirty(int index) { dirty.set(index); litRatio = -1; renderRevision++; host.markChanged(); }
    private void settingsChanged() { settingsDirty = true; renderRevision++; host.markChanged(); }
    private static int coordinate(Arguments args, int index, int maximum, String axis) {
        int value = args.checkInteger(index) - 1;
        if (value < 0 || value >= maximum) throw new ArrayIndexOutOfBoundsException(axis);
        return value;
    }
    private int value(Arguments args, int index) {
        int value = args.isBoolean(index) ? args.checkBoolean(index) ? 1 : 0 : args.checkInteger(index);
        if (value < 0 || value > colors.length) throw new IllegalArgumentException("invalid value");
        return value;
    }
    @Callback(doc = "function() -- Clears the hologram.")
    public synchronized Object[] clear(Context context, Arguments args) {
        Arrays.fill(volume, 0); dirty.clear(); cleared = true; litRatio = 0; settingsChanged(); return null;
    }
    @Callback(direct = true, doc = "function(x:number, y:number, z:number):number -- Returns the value for the specified voxel.")
    public synchronized Object[] get(Context context, Arguments args) { return new Object[]{getColor(coordinate(args, 0, WIDTH, "x"), coordinate(args, 1, HEIGHT, "y"), coordinate(args, 2, WIDTH, "z"))}; }
    @Callback(direct = true, limit = 256, doc = "function(x:number, y:number, z:number, value:number or boolean) -- Set the value for the specified voxel.")
    public synchronized Object[] set(Context context, Arguments args) {
        setColor(coordinate(args, 0, WIDTH, "x"), coordinate(args, 1, HEIGHT, "y"), coordinate(args, 2, WIDTH, "z"), value(args, 3)); return null;
    }
    @Callback(direct = true, limit = 128, doc = "function(x:number, z:number[, minY:number], maxY:number, value:number or boolean) -- Fills an interval of a column with the specified value.")
    public synchronized Object[] fill(Context context, Arguments args) {
        int x = coordinate(args, 0, WIDTH, "x"), z = coordinate(args, 1, WIDTH, "z"), min, max, value;
        if (args.count() > 4) { min = Math.clamp(args.checkInteger(2), 1, HEIGHT); max = Math.clamp(args.checkInteger(3), 1, HEIGHT); value = value(args, 4); }
        else { min = 1; max = Math.clamp(args.checkInteger(2), 1, HEIGHT); value = value(args, 3); }
        if (min > max) throw new IllegalArgumentException("interval is empty");
        int mask = (-1 >>> (31 - (max - min))) << (min - 1), index = x + z * WIDTH;
        volume[index] = (value & 1) == 0 ? volume[index] & ~mask : volume[index] | mask;
        volume[index + PLANE] = (value & 2) == 0 ? volume[index + PLANE] & ~mask : volume[index + PLANE] | mask;
        dirty(index); return null;
    }
    @Callback(doc = "function(data:string) -- Set the raw buffer to the specified byte array, where each byte represents a voxel color. Nesting is x,z,y.")
    public synchronized Object[] setRaw(Context context, Arguments args) {
        byte[] bytes = args.checkByteArray(0);
        for (int x = 0; x < WIDTH; x++) for (int z = 0; z < WIDTH; z++) {
            int offset = (x * WIDTH + z) * HEIGHT;
            if (bytes.length < offset + HEIGHT) continue;
            int low = 0, high = 0;
            for (int y = 0; y < HEIGHT; y++) { low |= (bytes[offset + y] & 1) << y; high |= ((bytes[offset + y] >>> 1) & 1) << y; }
            int index = x + z * WIDTH;
            if (volume[index] != low || volume[index + PLANE] != high) { volume[index] = low; volume[index + PLANE] = high; dirty(index); }
        }
        context.pause(Settings.get().hologramSetRawDelay); return null;
    }
    @Callback(doc = "function(x:number, z:number, sx:number, sz:number, tx:number, tz:number) -- Copies an area of columns by the specified translation.")
    public synchronized Object[] copy(Context context, Arguments args) {
        int x = coordinate(args, 0, WIDTH, "x"), z = coordinate(args, 1, WIDTH, "z");
        int width = args.checkInteger(2), depth = args.checkInteger(3), tx = args.checkInteger(4), tz = args.checkInteger(5);
        if (width <= 0 || depth <= 0 || tx == 0 && tz == 0) return null;
        // The original uses inclusive, independently clamped endpoints. Keep its
        // edge behavior and copy away from the source so overlapping ranges work.
        int farX = Math.max(0, Math.min(WIDTH - 1, x + tx + width - 1)), nearX = Math.max(0, Math.min(WIDTH, x + tx));
        int farZ = Math.max(0, Math.min(WIDTH - 1, z + tz + depth - 1)), nearZ = Math.max(0, Math.min(WIDTH, z + tz));
        int fromX = tx > 0 ? farX : nearX, untilX = tx > 0 ? nearX : farX, stepX = tx > 0 ? -1 : 1;
        int fromZ = tz > 0 ? farZ : nearZ, untilZ = tz > 0 ? nearZ : farZ, stepZ = tz > 0 ? -1 : 1;
        for (int nz = fromZ; stepZ > 0 ? nz <= untilZ : nz >= untilZ; nz += stepZ) {
            int oz = nz - tz;
            if (oz < 0 || oz >= WIDTH) continue;
            for (int nx = fromX; stepX > 0 ? nx <= untilX : nx >= untilX; nx += stepX) {
                int ox = nx - tx;
                if (ox < 0 || ox >= WIDTH) continue;
                int to = nx + nz * WIDTH, from = ox + oz * WIDTH;
                volume[to] = volume[from]; volume[to + PLANE] = volume[from + PLANE]; dirty(to);
            }
        }
        int area = Math.abs(fromX - untilX) * Math.abs(fromZ - untilZ);
        context.pause(Math.max(0, area / (float) PLANE - 0.25)); return null;
    }
    @Callback(direct = true, doc = "function():number -- Returns the render scale of the hologram.")
    public synchronized Object[] getScale(Context context, Arguments args) { return new Object[]{scale}; }
    @Callback(doc = "function(value:number) -- Set the render scale. A larger scale consumes more energy.")
    public synchronized Object[] setScale(Context context, Arguments args) { scale = Math.max(0.333333, Math.min(Settings.get().hologramMaxScaleByTier[tier], args.checkDouble(0))); settingsChanged(); return null; }
    @Callback(direct = true, doc = "function():number, number, number -- Returns the relative render projection offsets of the hologram.")
    public synchronized Object[] getTranslation(Context context, Arguments args) { return new Object[]{translation.x, translation.y, translation.z}; }
    @Callback(doc = "function(tx:number, ty:number, tz:number) -- Sets the relative render projection offsets of the hologram.")
    public synchronized Object[] setTranslation(Context context, Arguments args) {
        double max = Settings.get().hologramMaxTranslationByTier[tier];
        translation = new Vec3(Math.max(-max, Math.min(max, args.checkDouble(0))), Math.max(0, Math.min(max * 2, args.checkDouble(1))), Math.max(-max, Math.min(max, args.checkDouble(2))));
        settingsChanged(); return null;
    }
    @Callback(direct = true, doc = "function():number -- The color depth supported by the hologram.")
    public Object[] maxDepth(Context context, Arguments args) { return new Object[]{tier + 1}; }
    private int paletteIndex(Arguments args) { int index = args.checkInteger(0) - 1; if (index < 0 || index >= colors.length) throw new ArrayIndexOutOfBoundsException(); return index; }
    private static int swapColor(int color) { return ((color & 0xFF) << 16) | (color & 0xFF00) | ((color >>> 16) & 0xFF); }
    @Callback(doc = "function(index:number):number -- Get the color defined for the specified value.")
    public synchronized Object[] getPaletteColor(Context context, Arguments args) { return new Object[]{colors[paletteIndex(args)]}; }
    @Callback(doc = "function(index:number, value:number):number -- Set the color defined for the specified value.")
    public synchronized Object[] setPaletteColor(Context context, Arguments args) {
        int index = paletteIndex(args), previous = colors[index]; colors[index] = args.checkInteger(1) & 0xFFFFFF; settingsChanged();
        // The original returns its old render-order (BGR) value, although get uses RGB.
        return new Object[]{swapColor(previous)};
    }
    @Callback(doc = "function(angle:number, x:number, y:number, z:number):boolean -- Set the base rotation of the displayed hologram.")
    public synchronized Object[] setRotation(Context context, Arguments args) {
        if (tier < 1) return new Object[]{null, "not supported"};
        float angle = (float) (args.checkDouble(0) % 360), x = (float) args.checkDouble(1), y = (float) args.checkDouble(2), z = (float) args.checkDouble(3);
        rotationAngle = angle; rotationX = x; rotationY = y; rotationZ = z;
        settingsChanged(); return new Object[]{true};
    }
    @Callback(doc = "function(speed:number, x:number, y:number, z:number):boolean -- Set the rotation speed of the displayed hologram.")
    public synchronized Object[] setRotationSpeed(Context context, Arguments args) {
        if (tier < 1) return new Object[]{null, "not supported"};
        float speed = (float) Math.min(1440, Math.max(-1440, args.checkDouble(0))), x = (float) args.checkDouble(1), y = (float) args.checkDouble(2), z = (float) args.checkDouble(3);
        rotationSpeed = speed; rotationSpeedX = x; rotationSpeedY = y; rotationSpeedZ = z;
        settingsChanged(); return new Object[]{true};
    }
    @Callback(direct = true, doc = "function():number, number, number -- Get the dimension of the x,y,z axes.")
    public Object[] getDimensions(Context context, Arguments args) { return new Object[]{WIDTH, HEIGHT, WIDTH}; }
    @Override public boolean canUpdate() { return true; }
    @Override public synchronized void update() {
        if (!(host.world() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        int frequency = Math.max(1, (int) Settings.get().tickFrequency);
        if (level.getGameTime() % frequency == 0) {
            if (litRatio < 0) litRatio = Arrays.stream(volume).filter(value -> value != 0).count() / (double) volume.length;
            boolean previous = hasPower;
            hasPower = Settings.get().ignorePower || node().tryChangeBuffer(-Settings.get().hologramCost * litRatio * scale * frequency);
            if (previous != hasPower) settingsDirty = true;
        }
        flushUpdates();
    }
    /** A snapshot request drains updates without consuming another tick's worth of energy. */
    public synchronized void flushUpdates() {
        if (!(host.world() instanceof ServerLevel level) || !level.getServer().isSameThread() || dirty.isEmpty() && !settingsDirty && !cleared) return;
        CompoundTag update = new CompoundTag(); writeSettings(update);
        if (cleared) update.putBoolean("clear", true);
        if (!dirty.isEmpty()) {
            int minX = WIDTH, maxX = -1, minZ = WIDTH, maxZ = -1;
            for (int index = dirty.nextSetBit(0); index >= 0; index = dirty.nextSetBit(index + 1)) { int x = index % WIDTH, z = index / WIDTH; minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z); }
            int width = maxX - minX + 1, depth = maxZ - minZ + 1;
            if (dirty.cardinality() > width * depth * 0.8) {
                update.putIntArray("area", new int[]{minX, minZ, width, depth});
                int[] values = new int[width * depth * 2]; int cursor = 0;
                for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) { int index = x + z * WIDTH; values[cursor++] = volume[index]; values[cursor++] = volume[index + PLANE]; }
                update.putIntArray("values", values);
            } else {
                int[] values = new int[dirty.cardinality() * 3]; int cursor = 0;
                for (int index = dirty.nextSetBit(0); index >= 0; index = dirty.nextSetBit(index + 1)) { values[cursor++] = index; values[cursor++] = volume[index]; values[cursor++] = volume[index + PLANE]; }
                update.putIntArray("columns", values);
            }
        }
        host.synchronizeHologram(update); dirty.clear(); settingsDirty = false; cleared = false;
    }
    private void writeSettings(CompoundTag tag) {
        tag.putIntArray("colors", colors.clone()); tag.putDouble("oc:scale", scale); tag.putBoolean("oc:hasPower", hasPower);
        tag.putDouble("oc:offsetX", translation.x); tag.putDouble("oc:offsetY", translation.y); tag.putDouble("oc:offsetZ", translation.z);
        tag.putFloat("oc:rotationAngle", rotationAngle); tag.putFloat("oc:rotationX", rotationX); tag.putFloat("oc:rotationY", rotationY); tag.putFloat("oc:rotationZ", rotationZ);
        tag.putFloat("oc:rotationSpeed", rotationSpeed); tag.putFloat("oc:rotationSpeedX", rotationSpeedX); tag.putFloat("oc:rotationSpeedY", rotationSpeedY); tag.putFloat("oc:rotationSpeedZ", rotationSpeedZ);
    }
    public synchronized CompoundTag fullUpdate() { CompoundTag tag = new CompoundTag(); tag.putIntArray("volume", volume.clone()); writeSettings(tag); return tag; }
    public synchronized void applyUpdate(CompoundTag tag) {
        if (tag.getBoolean("clear")) Arrays.fill(volume, 0);
        if (tag.contains("volume")) { int[] values = tag.getIntArray("volume"); Arrays.fill(volume, 0); System.arraycopy(values, 0, volume, 0, Math.min(values.length, volume.length)); }
        if (tag.contains("columns")) {
            int[] values = tag.getIntArray("columns");
            for (int cursor = 0; cursor + 2 < values.length; cursor += 3) { int index = values[cursor]; if (index >= 0 && index < PLANE) { volume[index] = values[cursor + 1]; volume[index + PLANE] = values[cursor + 2]; } }
        }
        if (tag.contains("area")) {
            int[] area = tag.getIntArray("area"), values = tag.getIntArray("values");
            if (area.length == 4 && area[0] >= 0 && area[1] >= 0 && area[2] > 0 && area[3] > 0 && area[0] + area[2] <= WIDTH && area[1] + area[3] <= WIDTH && values.length == area[2] * area[3] * 2) {
                int cursor = 0;
                for (int z = area[1]; z < area[1] + area[3]; z++) for (int x = area[0]; x < area[0] + area[2]; x++) { int index = x + z * WIDTH; volume[index] = values[cursor++]; volume[index + PLANE] = values[cursor++]; }
            }
        }
        if (tag.contains("colors")) { int[] palette = tag.getIntArray("colors"); for (int i = 0; i < Math.min(palette.length, colors.length); i++) colors[i] = palette[i] & 0xFFFFFF; }
        if (tag.contains("oc:scale")) scale = tag.getDouble("oc:scale");
        if (tag.contains("oc:hasPower")) hasPower = tag.getBoolean("oc:hasPower");
        translation = new Vec3(tag.getDouble("oc:offsetX"), tag.getDouble("oc:offsetY"), tag.getDouble("oc:offsetZ"));
        rotationAngle = tag.getFloat("oc:rotationAngle"); rotationX = tag.getFloat("oc:rotationX"); rotationY = tag.getFloat("oc:rotationY"); rotationZ = tag.getFloat("oc:rotationZ");
        rotationSpeed = tag.getFloat("oc:rotationSpeed"); rotationSpeedX = tag.getFloat("oc:rotationSpeedX"); rotationSpeedY = tag.getFloat("oc:rotationSpeedY"); rotationSpeedZ = tag.getFloat("oc:rotationSpeedZ");
        litRatio = -1; renderRevision++;
    }
    @Override public synchronized void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag state = tag;
        if (!tag.contains("volume") && tag.contains("dimension") && node() != null && node().address() != null) {
            state = tag.copy();state.merge(SaveHandler.loadNBT(tag, node().address() + "_data"));
        }
        applyUpdate(state); dirty.clear(); settingsDirty = false; cleared = false;
    }
    @Override public synchronized void save(CompoundTag tag) {
        super.save(tag); tag.putByte("oc:tier", (byte) tier); writeSettings(tag);
        if (host.world() instanceof ServerLevel && !SaveHandler.isSavingForClients()) {
            tag.remove("volume"); tag.remove("colors"); tag.remove("oc:hasPower");
            SaveHandler.scheduleSave(host, tag, node().address() + "_data", data -> { data.putIntArray("volume", volume.clone()); data.putIntArray("colors", colors.clone()); });
        } else tag.putIntArray("volume", volume.clone());
    }
}
