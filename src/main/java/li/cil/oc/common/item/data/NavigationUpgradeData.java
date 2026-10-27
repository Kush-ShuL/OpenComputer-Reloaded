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
import net.minecraft.world.level.saveddata.maps.MapId;

public class NavigationUpgradeData extends ItemData {
    public ItemStack map = new ItemStack(net.minecraft.world.item.Items.FILLED_MAP);
    public NavigationUpgradeData() { super(Constants.ItemName.NavigationUpgrade); }
    public NavigationUpgradeData(ItemStack stack, HolderLookup.Provider registries) { this(); load(stack, registries); }
    public MapItemSavedData mapData(Level level) {
        try {
            if (!(map.getItem() instanceof MapItem)) throw new IllegalStateException("invalid map");
            // A legacy filled map without extra data has item damage (map ID) zero.
            if (!map.has(DataComponents.MAP_ID)) map.set(DataComponents.MAP_ID, new MapId(0));
            MapItemSavedData data = MapItem.getSavedData(map, level);
            if (data == null && !level.isClientSide) {
                // ItemMap.getMapData used to allocate missing maps at spawn, at scale 3.
                var spawn = level.getLevelData().getSpawnPos();
                ItemStack created = MapItem.create(level, spawn.getX(), spawn.getZ(), (byte) 3, false, false);
                map.set(DataComponents.MAP_ID, created.get(DataComponents.MAP_ID));
                data = MapItem.getSavedData(map, level);
            }
            if (data == null) throw new IllegalStateException("invalid map");
            return data;
        } catch (Exception error) {
            throw new IllegalStateException("invalid map", error);
        }
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
