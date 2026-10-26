package li.cil.oc.common.item.data;

import li.cil.oc.Constants;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Printed shapes, active state, collision/light/redstone settings and material costs. */
public class PrintData extends ItemData {
    public Optional<String> label = Optional.empty(), tooltip = Optional.empty();
    public boolean isButtonMode, pressurePlate, isBeaconBase, noclipOff, noclipOn;
    public int redstoneLevel, lightLevel;
    public final Set<Shape> stateOff = new LinkedHashSet<>(), stateOn = new LinkedHashSet<>();
    private boolean opacityDirty = true;
    private float cachedOpacity;
    private static final Set<Method> INK_PROVIDERS = new LinkedHashSet<>();

    public PrintData() { super(Constants.BlockName.Print); }
    public PrintData(ItemStack stack) { this(); load(customData(stack)); }
    public int complexity() { return Math.max(stateOn.size(), stateOff.size()); }
    public boolean hasActiveState() { return !stateOn.isEmpty(); }
    public boolean emitLight() { return lightLevel > 0; }
    public boolean emitRedstone() { return redstoneLevel > 0; }
    public boolean emitRedstone(boolean state) { return state ? emitRedstoneWhenOn() : emitRedstoneWhenOff(); }
    public boolean emitRedstoneWhenOff() { return emitRedstone() && !hasActiveState(); }
    public boolean emitRedstoneWhenOn() { return emitRedstone() && hasActiveState(); }
    public void markShapesChanged() { opacityDirty = true; }
    public float opacity() {
        if (opacityDirty) {
            opacityDirty = false;
            cachedOpacity = Math.min(computeApproximateOpacity(stateOn), computeApproximateOpacity(stateOff));
        }
        return cachedOpacity;
    }
    @Override public void load(CompoundTag tag) {
        label = tag.contains("label") ? Optional.of(tag.getString("label")) : Optional.empty();
        tooltip = tag.contains("tooltip") ? Optional.of(tag.getString("tooltip")) : Optional.empty();
        isButtonMode = tag.getBoolean("isButtonMode");
        redstoneLevel = tag.getBoolean("emitRedstone") ? 15 : Math.clamp(tag.getInt("redstoneLevel"), 0, 15);
        pressurePlate = tag.getBoolean("pressurePlate");
        stateOff.clear(); stateOn.clear();
        for (Tag value : tag.getList("stateOff", Tag.TAG_COMPOUND)) stateOff.add(nbtToShape((CompoundTag) value));
        for (Tag value : tag.getList("stateOn", Tag.TAG_COMPOUND)) stateOn.add(nbtToShape((CompoundTag) value));
        isBeaconBase = tag.getBoolean("isBeaconBase");
        lightLevel = Math.clamp(tag.getByte("lightLevel") & 255, 0, 15);
        noclipOff = tag.getBoolean("noclipOff");
        noclipOn = tag.getBoolean("noclipOn");
        opacityDirty = true;
    }
    @Override public void save(CompoundTag tag) {
        label.ifPresent(value -> tag.putString("label", value));
        tooltip.ifPresent(value -> tag.putString("tooltip", value));
        tag.putBoolean("isButtonMode", isButtonMode);
        tag.putInt("redstoneLevel", redstoneLevel);
        tag.putBoolean("pressurePlate", pressurePlate);
        tag.put("stateOff", saveShapes(stateOff));
        tag.put("stateOn", saveShapes(stateOn));
        tag.putBoolean("isBeaconBase", isBeaconBase);
        tag.putByte("lightLevel", (byte) lightLevel);
        tag.putBoolean("noclipOff", noclipOff);
        tag.putBoolean("noclipOn", noclipOn);
    }
    private static ListTag saveShapes(Set<Shape> shapes) {
        ArrayList<Shape> sorted = new ArrayList<>(shapes);
        sorted.sort(PrintData::compareShapes);
        ListTag list = new ListTag();
        for (Shape shape : sorted) list.add(shapeToNBT(shape));
        return list;
    }
    private static int compareShapes(Shape a, Shape b) {
        double[] aa = {a.bounds.minX, a.bounds.minY, a.bounds.minZ, a.bounds.maxX, a.bounds.maxY, a.bounds.maxZ};
        double[] bb = {b.bounds.minX, b.bounds.minY, b.bounds.minZ, b.bounds.maxX, b.bounds.maxY, b.bounds.maxZ};
        for (int i = 0; i < aa.length; i++) { int result = Double.compare(bb[i], aa[i]); if (result != 0) return result; }
        int tint = Comparator.<Integer>nullsFirst(Integer::compareTo).compare(b.tint.orElse(null), a.tint.orElse(null));
        return tint == 0 ? b.texture.compareTo(a.texture) : tint;
    }
    public static float computeApproximateOpacity(Iterable<Shape> shapes) {
        ArrayList<Shape> list = new ArrayList<>(); shapes.forEach(list::add);
        float volume = 1;
        if (!list.isEmpty()) for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) {
            AABB part = new AABB(x / 4.0, y / 4.0, z / 4.0, (x + 1) / 4.0, (y + 1) / 4.0, (z + 1) / 4.0);
            if (list.stream().noneMatch(shape -> shape.bounds.intersects(part))) volume -= 1F / 64;
        }
        return volume;
    }
    public record Costs(int material, int ink) {}
    public static Optional<Costs> computeCosts(PrintData data) {
        int totalVolume = 0, totalSurface = 0;
        for (Set<Shape> shapes : java.util.List.of(data.stateOn, data.stateOff)) for (Shape shape : shapes) {
            int sx = (int) Math.round((shape.bounds.maxX - shape.bounds.minX) * 16);
            int sy = (int) Math.round((shape.bounds.maxY - shape.bounds.minY) * 16);
            int sz = (int) Math.round((shape.bounds.maxZ - shape.bounds.minZ) * 16);
            totalVolume += sx * sy * sz;
            totalSurface += 2 * (sx * sy + sx * sz + sy * sz);
        }
        if (totalVolume <= 0) return Optional.empty();
        int material = Math.max(totalVolume / 2, 1);
        if (data.redstoneLevel > 0 && data.redstoneLevel < 15) material += Settings.get().printCustomRedstone;
        if (data.noclipOff || data.noclipOn) material = (int) (material * Settings.get().noclipMultiplier);
        return Optional.of(new Costs(material, Math.max(totalSurface / 6, 1)));
    }
    public static int materialValue(ItemStack stack) {
        if (Items.get(stack) == Items.get(Constants.ItemName.Chamelium)) return Settings.get().printMaterialValue;
        if (Items.get(stack) == Items.get(Constants.BlockName.Print))
            return computeCosts(new PrintData(stack)).map(cost -> (int) (cost.material * Settings.get().printRecycleRate)).orElse(0);
        return 0;
    }
    public static void addInkProvider(Method provider) { synchronized (INK_PROVIDERS) { INK_PROVIDERS.add(provider); } }
    public static int inkValue(ItemStack stack) {
        Method[] providers;
        synchronized (INK_PROVIDERS) { providers = INK_PROVIDERS.toArray(Method[]::new); }
        for (Method provider : providers) {
            try {
                Object result = provider.invoke(null, stack);
                if (result instanceof Number value && value.intValue() > 0) return value.intValue();
            } catch (ReflectiveOperationException error) { OpenComputers.LOGGER.warn("Ink provider failed: {}", provider, error); }
        }
        return 0;
    }
    public static Shape nbtToShape(CompoundTag tag) {
        AABB bounds;
        if (tag.contains("minX")) bounds = new AABB(tag.getByte("minX") / 16F, tag.getByte("minY") / 16F, tag.getByte("minZ") / 16F,
                tag.getByte("maxX") / 16F, tag.getByte("maxY") / 16F, tag.getByte("maxZ") / 16F);
        else {
            byte[] values = java.util.Arrays.copyOf(tag.getByteArray("bounds"), 6);
            bounds = new AABB(values[0] / 16F, values[1] / 16F, values[2] / 16F, values[3] / 16F, values[4] / 16F, values[5] / 16F);
        }
        return new Shape(bounds, tag.getString("texture"), tag.contains("tint") ? Optional.of(tag.getInt("tint")) : Optional.empty());
    }
    public static CompoundTag shapeToNBT(Shape shape) {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("bounds", new byte[]{(byte) Math.round(shape.bounds.minX * 16), (byte) Math.round(shape.bounds.minY * 16),
                (byte) Math.round(shape.bounds.minZ * 16), (byte) Math.round(shape.bounds.maxX * 16),
                (byte) Math.round(shape.bounds.maxY * 16), (byte) Math.round(shape.bounds.maxZ * 16)});
        tag.putString("texture", shape.texture);
        shape.tint.ifPresent(value -> tag.putInt("tint", value));
        return tag;
    }
    public static final class Shape {
        public final AABB bounds;
        public final String texture;
        public final Optional<Integer> tint;
        public Shape(AABB bounds, String texture, Optional<Integer> tint) { this.bounds = bounds; this.texture = texture; this.tint = tint; }
    }
}
