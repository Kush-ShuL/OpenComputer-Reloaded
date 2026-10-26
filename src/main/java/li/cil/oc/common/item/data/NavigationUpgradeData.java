package li.cil.oc.common.item.data;

import li.cil.oc.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class NavigationUpgradeData extends ItemData {
    public ItemStack map = new ItemStack(net.minecraft.world.item.Items.FILLED_MAP);
    public NavigationUpgradeData() { super(Constants.ItemName.NavigationUpgrade); }
    public NavigationUpgradeData(ItemStack stack, HolderLookup.Provider registries) { this(); load(stack, registries); }
    public MapItemSavedData mapData(Level level) {
        MapItemSavedData data = MapItem.getSavedData(map, level);
        if (data == null) throw new IllegalStateException("invalid map");
        return data;
    }
    public int getSize(Level level) { return 128 * (1 << mapData(level).scale); }
    @Override public void load(ItemStack stack, HolderLookup.Provider registries) { load(customData(stack).getCompound("oc:data"), registries); }
    @Override public void save(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag root = customData(stack), data = root.getCompound("oc:data");
        save(data, registries);
        root.put("oc:data", data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }
    @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("oc:map")) map = ItemStack.parseOptional(registries, tag.getCompound("oc:map"));
    }
    @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (map != null) tag.put("oc:map", map.saveOptional(registries));
    }
}
