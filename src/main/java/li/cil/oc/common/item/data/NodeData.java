package li.cil.oc.common.item.data;

import li.cil.oc.api.network.Visibility;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

public class NodeData extends ItemData {
    public static final String NodeTag = "node", AddressTag = "address", BufferTag = "buffer", VisibilityTag = "visibility";
    public Optional<String> address = Optional.empty();
    public Optional<Double> buffer = Optional.empty();
    public Optional<Visibility> visibility = Optional.empty();
    public NodeData() { super(null); }
    public NodeData(ItemStack stack) { this(); load(customData(stack)); }
    @Override public void load(CompoundTag tag) {
        CompoundTag node = tag.getCompound("oc:data").getCompound(NodeTag);
        address = node.contains(AddressTag) ? Optional.of(node.getString(AddressTag)) : Optional.empty();
        buffer = node.contains(BufferTag) ? Optional.of(node.getDouble(BufferTag)) : Optional.empty();
        int ordinal = node.getInt(VisibilityTag);
        visibility = node.contains(VisibilityTag) && ordinal >= 0 && ordinal < Visibility.values().length
                ? Optional.of(Visibility.values()[ordinal]) : Optional.empty();
    }
    @Override public void save(CompoundTag tag) {
        CompoundTag data = tag.getCompound("oc:data"), node = data.getCompound(NodeTag);
        address.ifPresent(value -> node.putString(AddressTag, value));
        buffer.ifPresent(value -> node.putDouble(BufferTag, value));
        visibility.ifPresent(value -> node.putInt(VisibilityTag, value.ordinal()));
        data.put(NodeTag, node);
        tag.put("oc:data", data);
    }
}
