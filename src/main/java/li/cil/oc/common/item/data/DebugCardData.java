package li.cil.oc.common.item.data;

import li.cil.oc.Constants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

public class DebugCardData extends ItemData {
    public Optional<AccessContext> access = Optional.empty();
    public DebugCardData() { super(Constants.ItemName.DebugCard); }
    public DebugCardData(ItemStack stack) { this(); load(customData(stack)); }
    @Override public void load(CompoundTag tag) { access = AccessContext.load(tag.getCompound("oc:data")); }
    @Override public void save(CompoundTag tag) {
        CompoundTag data = tag.getCompound("oc:data");
        AccessContext.remove(data);
        access.ifPresent(value -> value.save(data));
        tag.put("oc:data", data);
    }
    public record AccessContext(String player, String nonce) {
        public static Optional<AccessContext> load(CompoundTag tag) {
            return tag.contains("oc:player") ? Optional.of(new AccessContext(tag.getString("oc:player"), tag.getString("oc:accessNonce"))) : Optional.empty();
        }
        public void save(CompoundTag tag) { tag.putString("oc:player", player); tag.putString("oc:accessNonce", nonce); }
        public static void remove(CompoundTag tag) { tag.remove("oc:player"); tag.remove("oc:accessNonce"); }
    }
}
