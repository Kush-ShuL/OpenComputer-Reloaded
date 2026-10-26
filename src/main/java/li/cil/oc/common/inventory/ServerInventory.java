package li.cil.oc.common.inventory;

import java.util.List;
import li.cil.oc.api.Driver;
import li.cil.oc.common.InventorySlots.InventorySlot;
import li.cil.oc.common.item.ServerItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Original blade inventory layout and root oc:items serialization, also usable on held blades. */
public class ServerInventory implements Container {
    protected final ItemStack container;
    protected final HolderLookup.Provider registries;
    protected final NonNullList<ItemStack> items;
    private final Runnable dirty;
    public ServerInventory(ItemStack container, HolderLookup.Provider registries) { this(container, registries, () -> {}); }
    public ServerInventory(ItemStack container, HolderLookup.Provider registries, Runnable dirty) {
        this.container = container; this.registries = registries; this.dirty = dirty; items = loadItems(container, registries);
    }
    public ItemStack container() { return container; }
    public int tier() { return container.getItem() instanceof ServerItem server ? server.tier() : 0; }
    private static InventorySlot s(String kind, int tier) { return new InventorySlot(kind, tier); }
    private static final InventorySlot[][] LAYOUT = {
        {s("card",1),s("card",1),s("cpu",1),s("component_bus",1),s("memory",1),s("memory",1),s("hdd",1),s("hdd",1),s("eeprom",Integer.MAX_VALUE)},
        {s("card",2),s("card",1),s("cpu",2),s("component_bus",2),s("component_bus",2),s("memory",2),s("memory",2),s("memory",2),s("hdd",2),s("hdd",2),s("hdd",2),s("card",1),s("eeprom",Integer.MAX_VALUE)},
        {s("card",2),s("card",2),s("cpu",2),s("component_bus",2),s("component_bus",2),s("component_bus",2),s("memory",2),s("memory",2),s("memory",2),s("memory",2),s("hdd",2),s("hdd",2),s("hdd",2),s("hdd",2),s("card",1),s("card",1),s("eeprom",Integer.MAX_VALUE)},
        {s("card",2),s("card",2),s("cpu",2),s("component_bus",2),s("component_bus",2),s("component_bus",2),s("memory",2),s("memory",2),s("memory",2),s("memory",2),s("hdd",2),s("hdd",2),s("hdd",2),s("hdd",2),s("card",2),s("card",2),s("eeprom",Integer.MAX_VALUE)}
    };
    public static InventorySlot[] layout(int tier) { return LAYOUT[Math.clamp(tier, 0, 3)].clone(); }
    public static NonNullList<ItemStack> loadItems(ItemStack server, HolderLookup.Provider registries) {
        int tier = server.getItem() instanceof ServerItem item ? item.tier() : 0;
        NonNullList<ItemStack> result = NonNullList.withSize(layout(tier).length, ItemStack.EMPTY);
        loadItems(server.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(), result, registries); return result;
    }
    public static void loadItems(CompoundTag tag, List<ItemStack> into, HolderLookup.Provider registries) {
        for (int i=0;i<into.size();i++) into.set(i, ItemStack.EMPTY);
        for (Tag entry : tag.getList("oc:items", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) entry; int slot = value.getByte("slot") & 255;
            if (slot < into.size()) into.set(slot, ItemStack.parse(registries, value.getCompound("item")).orElse(ItemStack.EMPTY));
        }
    }
    public static void saveItems(ItemStack server, List<ItemStack> items, HolderLookup.Provider registries) {
        CompoundTag tag = server.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); saveItems(tag, items, registries);
        server.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    public static void saveItems(CompoundTag tag, List<ItemStack> items, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int slot=0;slot<items.size();slot++) if (!items.get(slot).isEmpty()) {
            CompoundTag entry = new CompoundTag(); entry.putByte("slot", (byte) slot); entry.put("item", items.get(slot).save(registries)); list.add(entry);
        }
        tag.put("oc:items", list);
    }
    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    protected void beforeChange(int slot) {}
    protected void afterChange(int slot) { setChanged(); }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (amount<=0 || items.get(slot).isEmpty()) return ItemStack.EMPTY;
        beforeChange(slot); ItemStack result=items.get(slot).split(amount); if(items.get(slot).isEmpty())items.set(slot,ItemStack.EMPTY); afterChange(slot); return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { beforeChange(slot);ItemStack result=items.set(slot,ItemStack.EMPTY);afterChange(slot);return result; }
    @Override public void setItem(int slot, ItemStack stack) { beforeChange(slot);items.set(slot,stack.copyWithCount(Math.min(1,stack.getCount())));afterChange(slot); }
    @Override public void setChanged() { saveItems(container, items, registries);dirty.run(); }
    @Override public boolean stillValid(Player player) { return true; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public void clearContent() { for(int i=0;i<items.size();i++)beforeChange(i);items.replaceAll(ignored->ItemStack.EMPTY);setChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if(slot<0||slot>=items.size())return false;
        var driver=Driver.driverFor(stack,li.cil.oc.server.component.Server.class);var spec=layout(tier())[slot];
        return driver!=null&&driver.slot(stack).equals(spec.slot())&&driver.tier(stack)<=spec.tier();
    }
}
