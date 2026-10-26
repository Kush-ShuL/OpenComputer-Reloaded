package li.cil.oc.common.item.data;

import li.cil.oc.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

public class RaidData extends ItemData {
    public ItemStack[] disks = {};
    public CompoundTag filesystem = new CompoundTag();
    public Optional<String> label = Optional.empty();
    public RaidData() { super(Constants.BlockName.Raid); }
    public RaidData(ItemStack stack, HolderLookup.Provider registries) { this(); load(stack, registries); }
    @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
        disks = RobotData.readStacks(tag.getList("oc:disks", Tag.TAG_COMPOUND), registries);
        filesystem = tag.getCompound("oc:filesystem").copy();
        label = tag.contains("oc:label") ? Optional.of(tag.getString("oc:label")) : Optional.empty();
    }
    @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("oc:disks", RobotData.writeStacks(disks, registries));
        tag.put("oc:filesystem", filesystem.copy());
        label.ifPresent(value -> tag.putString("oc:label", value));
    }
}
