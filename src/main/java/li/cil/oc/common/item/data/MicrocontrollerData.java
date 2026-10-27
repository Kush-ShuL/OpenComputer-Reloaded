package li.cil.oc.common.item.data;

import li.cil.oc.Constants;
import li.cil.oc.api.Items;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import java.util.ArrayList;
import java.util.Arrays;

public class MicrocontrollerData extends ItemData {
    public int tier;
    public ItemStack[] components = {ItemStack.EMPTY};
    public int storedEnergy;
    public MicrocontrollerData() { this(Constants.BlockName.Microcontroller); }
    protected MicrocontrollerData(String name) { super(name); }
    public MicrocontrollerData(ItemStack stack, HolderLookup.Provider registries) { this(); load(stack, registries); }
    @Override public void save(ItemStack stack,HolderLookup.Provider registries){super.save(stack,registries);stack.set(DataComponents.RARITY,tier==0?Rarity.COMMON:tier==1?Rarity.UNCOMMON:tier==2?Rarity.RARE:Rarity.EPIC);}
    @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
        tier = tag.getByte("oc:tier");
        storedEnergy = tag.getInt("oc:storedEnergy");
        ArrayList<ItemStack> loaded = new ArrayList<>();
        for (Tag value : tag.getList("oc:components", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.parseOptional(registries, (CompoundTag) value);
            if (!stack.isEmpty()) loaded.add(stack);
        }
        if (loaded.stream().noneMatch(stack -> Items.get(stack) == Items.get(Constants.ItemName.EEPROM))) loaded.add(ItemStack.EMPTY);
        components = loaded.toArray(ItemStack[]::new);
    }
    @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putByte("oc:tier", (byte) tier);
        ListTag values = new ListTag();
        for (ItemStack stack : components) if (!stack.isEmpty()) values.add(stack.saveOptional(registries));
        tag.put("oc:components", values);
        tag.putInt("oc:storedEnergy", storedEnergy);
    }
    public ItemStack copyItemStack(HolderLookup.Provider registries) {
        ItemStack stack = createItemStack(registries);
        new MicrocontrollerData(stack, registries).save(stack, registries);
        return stack;
    }
}
