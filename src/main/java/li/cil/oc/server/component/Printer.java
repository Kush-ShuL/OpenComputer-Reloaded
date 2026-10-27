package li.cil.oc.server.component;

import java.util.Map;
import java.util.Optional;
import li.cil.oc.Constants;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ComponentConnector;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.common.block.entity.PrinterBlockEntity;
import li.cil.oc.common.item.data.PrintData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Original printer3d Lua model builder and material/ink/energy work queue. */
public final class Printer extends AbstractManagedEnvironment implements DeviceInfo {
    public static final int MAX_MATERIAL = 256000, MAX_INK = 100000;
    private final PrinterBlockEntity host;
    public PrintData data = new PrintData();
    public int material, ink, limit;
    public boolean active, working;
    public ItemStack output = ItemStack.EMPTY;
    public double totalEnergy, remainingEnergy;

    public Printer(PrinterBlockEntity host) {
        this.host = host;
        setNode(Network.newNode(this, Visibility.Network).withComponent("printer3d")
                .withConnector(Settings.get().bufferConverter).create());
    }
    @Override public ComponentConnector node() { return (ComponentConnector) super.node(); }
    @Override public Map<String, String> getDeviceInfo() {
        return Map.of("class", "printer", "description", "3D Printer", "vendor", Constants.DeviceInfo.DefaultVendor,
                "product", "Omni-Materializer T6.1");
    }
    public boolean canPrint() { return !data.stateOff.isEmpty() && data.stateOff.size() <= Settings.get().maxPrintComplexity && data.stateOn.size() <= Settings.get().maxPrintComplexity; }
    public boolean isPrinting() { return !output.isEmpty(); }
    public double progress() { return totalEnergy <= 0 ? (isPrinting() ? 100 : 0) : Math.clamp((1 - remainingEnergy / totalEnergy) * 100, 0, 100); }
    public int timeRemaining() { return (int) (remainingEnergy / Settings.get().assemblerTickAmount / 20); }
    private void changed(boolean uncommit) { if (uncommit) active = false; data.markShapesChanged(); host.syncPreview(); host.markChanged(); }
    @Callback(doc = "function() -- Resets the configuration of the printer and stop printing (current job will finish).")
    public Object[] reset(Context context, Arguments args) { data = new PrintData(); changed(true); return null; }
    @Callback(doc = "function(value:string) -- Set a label for the block being printed.")
    public Object[] setLabel(Context context, Arguments args) { data.label = text(args.optString(0, null), 24); changed(true); return null; }
    @Callback(doc = "function():string -- Get the current label for the block being printed.")
    public Object[] getLabel(Context context, Arguments args) { return new Object[]{data.label.orElse(null)}; }
    @Callback(doc = "function(value:string) -- Set a tooltip for the block being printed.")
    public Object[] setTooltip(Context context, Arguments args) { data.tooltip = text(args.optString(0, null), 128); changed(true); return null; }
    @Callback(doc = "function():string -- Get the current tooltip for the block being printed.")
    public Object[] getTooltip(Context context, Arguments args) { return new Object[]{data.tooltip.orElse(null)}; }
    private static Optional<String> text(String value, int length) { return value == null || value.isEmpty() ? Optional.empty() : Optional.of(value.substring(0, Math.min(length, value.length()))); }
    @Callback(doc = "function(value:number) -- Set what light level the printed block should have.")
    public Object[] setLightLevel(Context context, Arguments args) { data.lightLevel = Math.clamp(args.checkInteger(0), 0, Settings.get().maxPrintLightLevel); changed(true); return null; }
    @Callback(doc = "function():number -- Get which light level the printed block should have.")
    public Object[] getLightLevel(Context context, Arguments args) { return new Object[]{data.lightLevel}; }
    @Callback(doc = "function(value:boolean or number) -- Set whether the printed block should emit redstone when in its active state.")
    public Object[] setRedstoneEmitter(Context context, Arguments args) { data.redstoneLevel = args.isBoolean(0) ? args.checkBoolean(0) ? 15 : 0 : Math.clamp(args.checkInteger(0), 0, 15); changed(true); return null; }
    @Callback(doc = "function():boolean, number -- Get whether the printed block should emit redstone when in its active state.")
    public Object[] isRedstoneEmitter(Context context, Arguments args) { return new Object[]{data.emitRedstone(), data.redstoneLevel}; }
    @Callback(doc = "function(value:boolean) -- Set whether the printed block should automatically return to its off state.")
    public Object[] setButtonMode(Context context, Arguments args) { data.isButtonMode = args.checkBoolean(0); changed(true); return null; }
    @Callback(doc = "function():boolean -- Get whether the printed block should automatically return to its off state.")
    public Object[] isButtonMode(Context context, Arguments args) { return new Object[]{data.isButtonMode}; }
    @Callback(doc = "function(collideOff:boolean, collideOn:boolean) -- Set whether the printed block should be collidable or not.")
    public Object[] setCollidable(Context context, Arguments args) { boolean off = args.checkBoolean(0), on = args.checkBoolean(1); data.noclipOff = !off; data.noclipOn = !on; changed(false); return null; }
    @Callback(doc = "function():boolean, boolean -- Get whether the printed block should be collidable or not.")
    public Object[] isCollidable(Context context, Arguments args) { return new Object[]{!data.noclipOff, !data.noclipOn}; }
    @Callback(doc = "function(minX:number, minY:number, minZ:number, maxX:number, maxY:number, maxZ:number, texture:string[, state:boolean=false][,tint:number]) -- Adds a shape to the printers configuration, optionally specifying whether it is for the off or on state.")
    public Object[] addShape(Context context, Arguments args) {
        if (data.stateOff.size() > Settings.get().maxPrintComplexity || data.stateOn.size() > Settings.get().maxPrintComplexity) return new Object[]{null, "model too complex"};
        double x0 = unit(args.checkInteger(0)), y0 = unit(args.checkInteger(1)), z0 = 1 - unit(args.checkInteger(2));
        double x1 = unit(args.checkInteger(3)), y1 = unit(args.checkInteger(4)), z1 = 1 - unit(args.checkInteger(5));
        String texture = args.checkString(6); texture = texture.substring(0, Math.min(64, texture.length()));
        boolean state = args.count() > 7 && args.isBoolean(7) && args.checkBoolean(7);
        Optional<Integer> tint = args.count() > 7 && args.isInteger(7) ? Optional.of(args.checkInteger(7)) : args.count() > 8 && args.isInteger(8) ? Optional.of(args.checkInteger(8)) : Optional.empty();
        if (x0 == x1 || y0 == y1 || z0 == z1) throw new IllegalArgumentException("empty block");
        (state ? data.stateOn : data.stateOff).add(new PrintData.Shape(new AABB(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1)), texture, tint));
        changed(true); return new Object[]{true};
    }
    private static double unit(int value) { return Math.clamp(value, 0, 16) / 16d; }
    @Callback(doc = "function():number -- Get the number of shapes in the current configuration.")
    public Object[] getShapeCount(Context context, Arguments args) { return new Object[]{data.stateOff.size(), data.stateOn.size()}; }
    @Callback(doc = "function():number -- Get the maximum allowed number of shapes.")
    public Object[] getMaxShapeCount(Context context, Arguments args) { return new Object[]{Settings.get().maxPrintComplexity}; }
    @Callback(doc = "function([count:number]):boolean -- Commit and begin printing the current configuration.")
    public Object[] commit(Context context, Arguments args) {
        if (!canPrint()) return new Object[]{null, "model invalid"};
        limit = (int) Math.clamp(args.optDouble(0, 1), 0, Integer.MAX_VALUE); active = limit > 0; host.markChanged(); return new Object[]{true};
    }
    @Callback(doc = "function(): string, number or boolean -- The current state of the printer, `busy' or `idle', followed by the progress or model validity, respectively.")
    public Object[] status(Context context, Arguments args) { return isPrinting() ? new Object[]{"busy", progress()} : new Object[]{"idle", canPrint()}; }
    @Override public boolean canUpdate() { return true; }
    @Override public void update() {
        boolean dirty = false, wasWorking = working, started = false; working = false;
        if (active && output.isEmpty()) {
            ItemStack candidate = data.createItemStack(host.world().registryAccess());
            if (outputTypeMatches(candidate)) {
                Optional<PrintData.Costs> costs = PrintData.computeCosts(data);
                if (costs.isEmpty()) { active = false; data = new PrintData(); host.syncPreview(); dirty = true; }
                else {
                    totalEnergy = remainingEnergy = Settings.get().printCost;
                    if (material >= costs.get().material() && ink >= costs.get().ink()) {
                        material -= costs.get().material(); ink -= costs.get().ink();
                        output = candidate; if (--limit < 1) active = false; dirty = true; started = true;
                    }
                }
            }
        }
        if (!output.isEmpty()) {
            double want = started || remainingEnergy > 0 ? Math.max(1, Math.min(remainingEnergy, Settings.get().printerTickAmount)) : 0;
            double have = want > 0 ? want + (Settings.get().ignorePower ? 0 : node().changeBuffer(-want)) : 0;
            remainingEnergy = Math.max(0, remainingEnergy - have); working = have > .5;
            dirty |= have > 0;
            if (remainingEnergy <= 0) {
                if (!canMerge(output)) {
                    // Keep the completed piece and queue busy until there is room.
                    // Waiting consumes neither more power nor additional input items.
                    working = false;
                    if (dirty) host.markChanged();
                    if (wasWorking != working) host.syncPreview();
                    return;
                }
                ItemStack present = host.getItem(2);
                if (present.isEmpty()) host.setItem(2, output.copy()); else { present.grow(output.getCount()); host.setChanged(); }
                output = ItemStack.EMPTY; working = false; dirty = true;
            }
        }
        ItemStack materialStack = host.getItem(0);
        int value = PrintData.materialValue(materialStack);
        if (value > 0 && MAX_MATERIAL - material >= value) { host.removeItem(0, 1); material += value; dirty = true; }
        ItemStack inkStack = host.getItem(1); value = PrintData.inkValue(inkStack);
        if (value > 0 && MAX_INK - ink >= value) {
            ItemStack consumed = host.removeItem(1, 1);
            ink += value; dirty = true;
            if (consumed.hasCraftingRemainingItem()) {
                ItemStack remainder = consumed.getCraftingRemainingItem();
                if (host.getItem(1).isEmpty()) host.setItem(1, remainder);
                else if (!remainder.isEmpty()) net.minecraft.world.Containers.dropItemStack(host.world(), host.xPosition(), host.yPosition(), host.zPosition(), remainder);
            }
        }
        if (dirty) host.markChanged();
        if (wasWorking != working) host.syncPreview();
    }
    private boolean outputTypeMatches(ItemStack candidate) { ItemStack present = host.getItem(2); return present.isEmpty() || ItemStack.isSameItemSameComponents(present, candidate); }
    private boolean canMerge(ItemStack candidate) { ItemStack present = host.getItem(2); return present.isEmpty() || ItemStack.isSameItemSameComponents(present, candidate) && present.getCount() + candidate.getCount() <= present.getMaxStackSize(); }
    @Override public void load(CompoundTag tag) { load(tag, host.world().registryAccess()); }
    @Override public void save(CompoundTag tag) { save(tag, host.world().registryAccess()); }
    @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
        super.load(tag); material = tag.getInt("oc:amountMaterial"); ink = tag.getInt("oc:amountInk");
        data.load(tag.getCompound("oc:data")); active = tag.getBoolean("oc:active"); limit = tag.getInt("oc:limit");
        output = tag.contains("oc:output") ? ItemStack.parseOptional(registries, tag.getCompound("oc:output")) : ItemStack.EMPTY;
        totalEnergy = tag.getDouble("oc:total"); remainingEnergy = tag.getDouble("oc:remaining");
    }
    @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
        super.save(tag); tag.putInt("oc:amountMaterial", material); tag.putInt("oc:amountInk", ink);
        CompoundTag model = new CompoundTag(); data.save(model); tag.put("oc:data", model);
        tag.putBoolean("oc:active", active); tag.putInt("oc:limit", limit);
        if (!output.isEmpty()) tag.put("oc:output", output.save(registries));
        tag.putDouble("oc:total", totalEnergy); tag.putDouble("oc:remaining", remainingEnergy);
    }
}
