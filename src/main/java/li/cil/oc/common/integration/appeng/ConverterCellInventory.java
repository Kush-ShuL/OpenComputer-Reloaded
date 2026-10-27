package li.cil.oc.common.integration.appeng;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.me.cells.BasicCellInventory;
import li.cil.oc.api.driver.Converter;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/** Original cell metric names, populated only when the cell exposes those facts. */
public final class ConverterCellInventory implements Converter {
    @Override public void convert(Object value, Map<Object, Object> output) { convert(value, output, null); }
    @Override public void convert(Object value, Map<Object, Object> output, HolderLookup.Provider registries) {
        StorageCell cell = value instanceof StorageCell storage ? storage : value instanceof ItemStack stack ? StorageCells.getCellInventory(stack.copy(), null) : null;
        if (cell == null) return;
        var available = cell.getAvailableStacks();
        var items = new ArrayList<Object>(); long count = 0;
        for (var entry : available) {
            long amount = entry.getLongValue(); count += amount;
            Object stack = entry.getKey() instanceof AEItemKey item ? item.toStack((int)Math.min(Integer.MAX_VALUE, Math.max(1, amount)))
                    : entry.getKey() instanceof AEFluidKey fluid ? fluid.toStack((int)Math.min(Integer.MAX_VALUE, amount)) : null;
            if (stack == null) continue;
            Object converted = li.cil.oc.server.driver.Registry.convert(new Object[]{stack}, registries)[0];
            if (converted instanceof Map<?, ?> map) {
                Map<Object, Object> values = new LinkedHashMap<>(map);
                values.put(entry.getKey() instanceof AEItemKey ? "size" : "amount", amount); items.add(values);
            } else items.add(converted);
        }
        output.put("storedItemTypes", available.size()); output.put("storedItemCount", count);
        output.put("getAvailableItems", items.toArray()); output.put("name", cell.getDescription().getString());
        if (cell instanceof BasicCellInventory basic) {
            output.put("storedItemTypes", basic.getStoredItemTypes()); output.put("storedItemCount", basic.getStoredItemCount());
            output.put("remainingItemCount", basic.getRemainingItemCount()); output.put("remainingItemTypes", basic.getRemainingItemTypes());
            output.put("getTotalItemTypes", basic.getTotalItemTypes());
            output.put("totalBytes", basic.getTotalBytes()); output.put("freeBytes", basic.getFreeBytes()); output.put("usedBytes", basic.getUsedBytes());
            output.put("unusedItemCount", basic.getUnusedItemCount()); output.put("canHoldNewItem", basic.canHoldNewItem());
            output.put("fuzzyMode", basic.getFuzzyMode().toString());
        }
    }
}
