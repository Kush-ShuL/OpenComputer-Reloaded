package li.cil.oc.common.item.data;

import li.cil.oc.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class DroneData extends MicrocontrollerData {
    public String name = "";
    public DroneData() { super(Constants.ItemName.Drone); }
    public DroneData(ItemStack stack, HolderLookup.Provider registries) { this(); load(stack, registries); }
    @Override public void save(ItemStack stack,HolderLookup.Provider registries){super.save(stack,registries);stack.set(net.minecraft.core.component.DataComponents.RARITY,tier<=0?net.minecraft.world.item.Rarity.COMMON:tier==1?net.minecraft.world.item.Rarity.UNCOMMON:tier==2?net.minecraft.world.item.Rarity.RARE:net.minecraft.world.item.Rarity.EPIC);}
    @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
        super.load(tag, registries);
        name = getDisplayName(tag).filter(value -> !value.isEmpty()).orElseGet(RobotData::randomName);
    }
    @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
        super.save(tag, registries);
        if (name != null && !name.isEmpty()) setDisplayName(tag, name);
    }
}
