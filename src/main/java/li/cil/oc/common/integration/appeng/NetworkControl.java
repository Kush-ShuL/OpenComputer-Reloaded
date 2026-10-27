package li.cil.oc.common.integration.appeng;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;

/** Original ME network callbacks, implemented with AE2's unified key services. */
public abstract class NetworkControl extends AE2Drivers.Environment {
    final AEUtil.Target target;
    NetworkControl(AE2Drivers.Kind kind, AEUtil.Target target) { super(kind); this.target = target; }

    private Map<AEItemKey, Long> allItems() {
        IGrid grid = target.grid();
        Map<AEItemKey, Long> items = new LinkedHashMap<>();
        for (var entry : grid.getStorageService().getCachedInventory()) if (entry.getKey() instanceof AEItemKey item) items.put(item, entry.getLongValue());
        for (var key : grid.getCraftingService().getCraftables(AEItemKey.filter())) if (key instanceof AEItemKey item) items.putIfAbsent(item, 0L);
        return items;
    }
    static long recipeAmount(IGrid grid, AEItemKey key) {
        for (var pattern : grid.getCraftingService().getCraftingFor(key)) for (GenericStack output : pattern.getOutputs()) if (output.what().equals(key)) return output.amount();
        return 1;
    }
    private Map<Object, Object> convert(AEItemKey key, long count) {
        return AEUtil.item(key, count, target.grid().getCraftingService().isCraftable(key), target.level().registryAccess());
    }
    @Callback(doc="function():table -- Get a list of tables representing the available CPUs in the network.")
    public Object[] getCpus(Context context, Arguments args) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var cpu : target.grid().getCraftingService().getCpus()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", cpu.getName() == null ? "" : cpu.getName().getString());
            entry.put("storage", cpu.getAvailableStorage()); entry.put("coprocessors", cpu.getCoProcessors()); entry.put("busy", cpu.isBusy());
            result.add(entry);
        }
        return new Object[]{result.toArray()};
    }
    @Callback(doc="function([filter:table]):table -- Get a list of known item recipes. These can be used to issue crafting requests.")
    public Object[] getCraftables(Context context, Arguments args) {
        var filter = AEUtil.filter(args, 0); var grid = target.grid();
        List<Craftable> result = new ArrayList<>();
        for (var key : grid.getCraftingService().getCraftables(AEItemKey.filter())) if (key instanceof AEItemKey item) {
            long count = recipeAmount(grid, item);
            if (AEUtil.matches(AEUtil.item(item, count, true, target.level().registryAccess()), filter)) result.add(new Craftable(target, item, count));
        }
        return new Object[]{result.toArray()};
    }
    @Callback(doc="function([filter:table]):table -- Get a list of the stored items in the network.")
    public Object[] getItemsInNetwork(Context context, Arguments args) {
        var filter = AEUtil.filter(args, 0); List<Map<Object, Object>> result = new ArrayList<>();
        for (var entry : allItems().entrySet()) { var item = convert(entry.getKey(), entry.getValue()); if (AEUtil.matches(item, filter)) result.add(item); }
        return new Object[]{result.toArray()};
    }
    @Callback(doc="function([filter:table, dbAddress:string, startSlot:number, count:number]):boolean -- Store matching items in a database on the same OC network.")
    public Object[] store(Context context, Arguments args) {
        var filter = AEUtil.filter(args, 0);
        var database = args.isString(1) ? AEUtil.database(node(), args.checkString(1)) : AEUtil.firstDatabase(node());
        int slot = AEUtil.slot(args, 2, database.size(), 0);
        int count = Math.max(0, args.optInteger(3, Integer.MAX_VALUE));
        for (var entry : allItems().entrySet()) {
            if (count <= 0 || slot >= database.size()) break;
            if (!AEUtil.matches(convert(entry.getKey(), entry.getValue()), filter)) continue;
            while (slot < database.size()) { var stored = database.getStackInSlot(slot); if (stored == null || stored.isEmpty()) break; slot++; }
            if (slot >= database.size()) break;
            long amount = entry.getValue() > 0 ? entry.getValue() : recipeAmount(target.grid(), entry.getKey());
            database.setStackInSlot(slot++, entry.getKey().toStack((int)Math.min(Integer.MAX_VALUE, amount)));
            count--;
        }
        return new Object[]{true};
    }
    @Callback(doc="function():table -- Get a list of the stored fluids in the network.")
    public Object[] getFluidsInNetwork(Context context, Arguments args) {
        List<Object> result = new ArrayList<>();
        for (var entry : target.grid().getStorageService().getCachedInventory()) if (entry.getKey() instanceof AEFluidKey fluid && entry.getLongValue() > 0) {
            // The normal OC fluid converter handles the registry-backed fluid stack.
            var converted = li.cil.oc.server.driver.Registry.convert(new Object[]{fluid.toStack((int)Math.min(Integer.MAX_VALUE, entry.getLongValue()))}, target.level().registryAccess())[0];
            if (converted instanceof Map<?, ?> map) {
                Map<Object, Object> value = new LinkedHashMap<>(map); value.put("amount", entry.getLongValue()); result.add(value);
            } else result.add(converted);
        }
        return new Object[]{result.toArray()};
    }
    @Callback(doc="function():number -- Get the average power injection into the network.")
    public Object[] getAvgPowerInjection(Context context, Arguments args) { return new Object[]{target.grid().getEnergyService().getAvgPowerInjection()}; }
    @Callback(doc="function():number -- Get the average power usage of the network.")
    public Object[] getAvgPowerUsage(Context context, Arguments args) { return new Object[]{target.grid().getEnergyService().getAvgPowerUsage()}; }
    @Callback(doc="function():number -- Get the idle power usage of the network.")
    public Object[] getIdlePowerUsage(Context context, Arguments args) { return new Object[]{target.grid().getEnergyService().getIdlePowerUsage()}; }
    @Callback(doc="function():number -- Get the maximum stored power in the network.")
    public Object[] getMaxStoredPower(Context context, Arguments args) { return new Object[]{target.grid().getEnergyService().getMaxStoredPower()}; }
    @Callback(doc="function():number -- Get the stored power in the network.")
    public Object[] getStoredPower(Context context, Arguments args) { return new Object[]{target.grid().getEnergyService().getStoredPower()}; }
    @Callback(doc="function():boolean -- True if the AE network is considered online.")
    public Object[] isNetworkPowered(Context context, Arguments args) { return new Object[]{target.grid().getEnergyService().isNetworkPowered()}; }
    @Callback(doc="function():number -- Returns the energy demand on the AE network.")
    public Object[] getEnergyDemand(Context context, Arguments args) { context.consumeCallBudget(1.5); return new Object[]{target.grid().getEnergyService().getEnergyDemand(Double.MAX_VALUE)}; }
}
