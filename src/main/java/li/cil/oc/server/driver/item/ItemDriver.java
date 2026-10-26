package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.DriverItem;
import li.cil.oc.common.item.ComponentItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

abstract class ItemDriver implements DriverItem {
    protected abstract boolean supports(ComponentItem item);
    @Override public boolean worksWith(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.getItem() instanceof ComponentItem item && supports(item); }
    protected ComponentItem item(ItemStack stack) {
        if (!worksWith(stack)) throw new IllegalArgumentException("Unsupported hardware item");
        return (ComponentItem) stack.getItem();
    }
    @Override public int tier(ItemStack stack) { return item(stack).tier(); }
    @Override public CompoundTag dataTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("oc:data");
    }
    @Override public void saveDataTag(ItemStack stack, CompoundTag data) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put("oc:data", data.copy()));
    }
}
