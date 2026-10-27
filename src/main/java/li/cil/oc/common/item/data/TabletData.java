package li.cil.oc.common.item.data;

import li.cil.oc.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import java.util.Arrays;

public class TabletData extends ItemData {
    public ItemStack[] items = new ItemStack[32];
    public boolean isRunning;
    public double energy, maxEnergy;
    public int tier;
    public ItemStack container = ItemStack.EMPTY;
    public TabletData() { super(Constants.ItemName.Tablet); Arrays.fill(items, ItemStack.EMPTY); }
    public TabletData(ItemStack stack, HolderLookup.Provider registries) { this(); load(stack, registries); }
    public void applyItemProperties(ItemStack stack){stack.set(DataComponents.RARITY,tier==0?Rarity.COMMON:tier==1?Rarity.UNCOMMON:tier==2?Rarity.RARE:Rarity.EPIC);}
    @Override public void save(ItemStack stack,HolderLookup.Provider registries){super.save(stack,registries);applyItemProperties(stack);}
    @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
        Arrays.fill(items, ItemStack.EMPTY);
        for (Tag value : tag.getList("oc:items", Tag.TAG_COMPOUND)) {
            CompoundTag slot = (CompoundTag) value;
            int index = slot.getByte("slot");
            if (index >= 0 && index < items.length) items[index] = ItemStack.parseOptional(registries, slot.getCompound("item"));
        }
        isRunning = tag.getBoolean("oc:isRunning");
        energy = tag.getDouble("oc:energy");
        maxEnergy = tag.getDouble("oc:maxEnergy");
        tier = tag.getInt("oc:tier");
        container = ItemStack.parseOptional(registries, tag.getCompound("oc:container"));
    }
    @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < items.length; i++) if (!items[i].isEmpty()) {
            CompoundTag slot = new CompoundTag();
            slot.putByte("slot", (byte) i);
            slot.put("item", items[i].saveOptional(registries));
            list.add(slot);
        }
        tag.put("oc:items", list);
        tag.putBoolean("oc:isRunning", isRunning);
        tag.putDouble("oc:energy", energy);
        tag.putDouble("oc:maxEnergy", maxEnergy);
        tag.putInt("oc:tier", tier);
        if (container.isEmpty()) tag.remove("oc:container");
        else tag.put("oc:container", container.saveOptional(registries));
    }
}
