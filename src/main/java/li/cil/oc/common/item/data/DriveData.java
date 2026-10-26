package li.cil.oc.common.item.data;

import li.cil.oc.api.Driver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class DriveData extends ItemData {
    public boolean isUnmanaged;
    public String lockInfo = "";
    public DriveData() { super(null); }
    public DriveData(ItemStack stack) { this(); load(customData(stack)); }
    public boolean isLocked() { return lockInfo != null && !lockInfo.isEmpty(); }
    @Override public void load(CompoundTag tag) {
        isUnmanaged = tag.getBoolean("oc:unmanaged");
        lockInfo = tag.getString("oc:lock");
    }
    @Override public void save(CompoundTag tag) {
        tag.putBoolean("oc:unmanaged", isUnmanaged);
        tag.putString("oc:lock", lockInfo == null ? "" : lockInfo);
    }
    public static void lock(ItemStack stack, Player player) {
        DriveData data = new DriveData(stack);
        if (!data.isLocked()) {
            String name = player.getGameProfile().getName();
            data.lockInfo = name == null || name.isEmpty() ? "notch" : name;
            data.save(stack, player.level().registryAccess());
        }
    }
    public static void setUnmanaged(ItemStack stack, boolean unmanaged) {
        DriveData data = new DriveData(stack);
        CompoundTag tag = customData(stack);
        if (data.isUnmanaged != unmanaged) {
            CompoundTag component = tag.getCompound("oc:data");
            CompoundTag node = component.getCompound("node");
            node.remove("address");
            component.put("node", node);
            tag.put("oc:data", component);
            data.lockInfo = "";
        }
        data.isUnmanaged = unmanaged;
        data.save(tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
