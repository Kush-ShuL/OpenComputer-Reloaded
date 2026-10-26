package li.cil.oc.common.item.data;

import li.cil.oc.api.Items;
import li.cil.oc.api.Persistable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Objects;
import java.util.Optional;

/** Original item data layout stored in the modern immutable custom data component. */
public abstract class ItemData implements Persistable {
    public final String itemName;
    protected ItemData(String itemName) { this.itemName = itemName; }

    public void load(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag tag = customData(stack);
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null) setDisplayName(tag, name.getString());
        load(tag, registries);
    }
    public void save(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag tag = customData(stack);
        save(tag, registries);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        getDisplayName(tag).ifPresent(name -> stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)));
    }
    public ItemStack createItemStack(HolderLookup.Provider registries) {
        if (itemName == null) return ItemStack.EMPTY;
        ItemStack stack = Objects.requireNonNull(Items.get(itemName), "Unregistered OC item: " + itemName).createItemStack(1);
        save(stack, registries);
        return stack;
    }
    @Override public void load(CompoundTag tag) {
        throw new IllegalStateException("Registry access is required to load this item data");
    }
    @Override public void save(CompoundTag tag) {
        throw new IllegalStateException("Registry access is required to save this item data");
    }
    public static CompoundTag customData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
    public static Optional<String> getDisplayName(CompoundTag tag) {
        CompoundTag display = tag.getCompound("display");
        return display.contains("Name") ? Optional.of(display.getString("Name")) : Optional.empty();
    }
    public static void setDisplayName(CompoundTag tag, String name) {
        CompoundTag display = tag.getCompound("display");
        display.putString("Name", name);
        tag.put("display", display);
    }
}
