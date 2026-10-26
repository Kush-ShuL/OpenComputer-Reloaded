package li.cil.oc.common.item.data;

import li.cil.oc.Constants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

public class NanomachineData extends ItemData {
    public String uuid = "";
    public Optional<CompoundTag> configuration = Optional.empty();
    public NanomachineData() { super(Constants.ItemName.Nanomachines); }
    public NanomachineData(ItemStack stack) { this(); load(customData(stack)); }
    public NanomachineData(String uuid, CompoundTag configuration) {
        this(); this.uuid = uuid; this.configuration = Optional.ofNullable(configuration).map(CompoundTag::copy);
    }
    @Override public void load(CompoundTag tag) {
        uuid = tag.getString("oc:uuid");
        configuration = tag.contains("oc:configuration") ? Optional.of(tag.getCompound("oc:configuration").copy()) : Optional.empty();
    }
    @Override public void save(CompoundTag tag) {
        tag.putString("oc:uuid", uuid);
        configuration.ifPresent(value -> tag.put("oc:configuration", value.copy()));
    }
}
