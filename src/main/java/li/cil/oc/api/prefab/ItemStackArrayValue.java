package li.cil.oc.api.prefab;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** A persistent, Lua-indexed array of item snapshots and a resumable iterator. */
public class ItemStackArrayValue extends AbstractValue {
    private static final String ARRAY_KEY = "Array";
    private static final String INDEX_KEY = "Index";
    private static final Map<Object, Object> EMPTY_MAP = Collections.emptyMap();

    private ItemStack[] array;
    private int iteratorIndex;
    private HolderLookup.Provider registries;

    public ItemStackArrayValue(ItemStack[] array) {
        if (array != null) {
            this.array = new ItemStack[array.length];
            for (int i = 0; i < array.length; i++) {
                this.array[i] = array[i] == null ? null : array[i].copy();
            }
        }
    }

    /** Bind registry access when callers use the single-argument load/save methods. */
    public ItemStackArrayValue(ItemStack[] array, HolderLookup.Provider registries) {
        this(array);
        this.registries = Objects.requireNonNull(registries);
    }

    public ItemStackArrayValue() {
        this(null);
    }

    @Override
    public Object[] call(Context context, Arguments arguments) {
        if (array == null || iteratorIndex >= array.length) return null;
        ItemStack stack = array[iteratorIndex++];
        return new Object[]{stack == null || stack.isEmpty() ? EMPTY_MAP : stack.copy()};
    }

    @Override
    public Object apply(Context context, Arguments arguments) {
        if (arguments.count() == 0 || array == null) return null;
        if (arguments.isInteger(0)) {
            int luaIndex = arguments.checkInteger(0);
            return luaIndex < 1 || luaIndex > array.length || array[luaIndex - 1] == null
                    ? null : array[luaIndex - 1].copy();
        }
        return arguments.isString(0) && arguments.checkString(0).equals("n") ? array.length : null;
    }

    @Override
    public void load(CompoundTag nbt, HolderLookup.Provider registries) {
        this.registries = Objects.requireNonNull(registries);
        if (nbt.contains(ARRAY_KEY, Tag.TAG_LIST)) {
            ListTag items = nbt.getList(ARRAY_KEY, Tag.TAG_COMPOUND);
            array = new ItemStack[items.size()];
            for (int i = 0; i < items.size(); i++) {
                array[i] = ItemStack.parseOptional(registries, items.getCompound(i));
            }
        } else {
            array = null;
        }
        iteratorIndex = Math.max(0, Math.min(nbt.getInt(INDEX_KEY), array == null ? 0 : array.length));
    }

    @Override
    public void save(CompoundTag nbt, HolderLookup.Provider registries) {
        this.registries = Objects.requireNonNull(registries);
        if (array != null) {
            ListTag items = new ListTag();
            for (ItemStack stack : array) items.add(stack == null ? new CompoundTag() : stack.saveOptional(registries));
            nbt.put(ARRAY_KEY, items);
        } else {
            nbt.remove(ARRAY_KEY);
        }
        nbt.putInt(INDEX_KEY, iteratorIndex);
    }

    @Override
    public void load(CompoundTag nbt) {
        load(nbt, requireRegistries());
    }

    @Override
    public void save(CompoundTag nbt) {
        save(nbt, requireRegistries());
    }

    private HolderLookup.Provider requireRegistries() {
        if (registries == null) {
            throw new IllegalStateException("ItemStackArrayValue requires the host level's registry access; use load/save(tag, registries)");
        }
        return registries;
    }

    @Callback(doc = "function():nil -- Reset the iterator index so that the next call will return the first element.")
    public Object[] reset(Context context, Arguments arguments) {
        iteratorIndex = 0;
        return null;
    }

    @Callback(doc = "function():number -- Returns the number of elements in the array.")
    public Object[] count(Context context, Arguments arguments) {
        return new Object[]{array == null ? 0 : array.length};
    }

    @Callback(doc = "function():table -- Returns ALL stacks in the array. Memory intensive.")
    public Object[] getAll(Context context, Arguments arguments) {
        TreeMap<Integer, Object> map = new TreeMap<>();
        if (array != null) {
            for (int i = 0; i < array.length; i++) {
                map.put(i + 1, array[i] == null ? EMPTY_MAP : array[i].copy());
            }
        }
        return new Object[]{map};
    }

    @Override
    public String toString() {
        return "{ItemStack Array}";
    }
}
