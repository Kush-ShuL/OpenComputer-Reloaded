package li.cil.oc.common.item.data;

import li.cil.oc.Constants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class HoverBootsData extends ItemData {
    public double charge;
    public HoverBootsData() { super(Constants.ItemName.HoverBoots); }
    public HoverBootsData(ItemStack stack) { this(); load(customData(stack)); }
    @Override public void load(CompoundTag tag) { charge = tag.getDouble("oc:charge"); }
    @Override public void save(CompoundTag tag) { tag.putDouble("oc:charge", charge); }
}
