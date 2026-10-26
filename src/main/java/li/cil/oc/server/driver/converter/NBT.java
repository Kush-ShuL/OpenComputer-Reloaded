package li.cil.oc.server.driver.converter;

import java.util.LinkedHashMap;
import java.util.Map;
import li.cil.oc.api.driver.Converter;
import net.minecraft.nbt.*;

/** Original oc:flatten NBT conversion, including modern long arrays. */
public final class NBT implements Converter {
    @Override public void convert(Object value, Map<Object, Object> output) { if (value instanceof Tag tag) output.put("oc:flatten", convertTag(tag)); }
    private static Object convertTag(Tag tag) {
        if (tag instanceof NumericTag number) return number.getAsNumber();
        if (tag instanceof StringTag text) return text.getAsString();
        if (tag instanceof ByteArrayTag bytes) return bytes.getAsByteArray().clone();
        if (tag instanceof IntArrayTag integers) return integers.getAsIntArray().clone();
        if (tag instanceof LongArrayTag longs) return longs.getAsLongArray().clone();
        if (tag instanceof ListTag list) { Object[] values = new Object[list.size()]; for (int i = 0; i < values.length; i++) values[i] = convertTag(list.get(i)); return values; }
        if (tag instanceof CompoundTag compound) { Map<String, Object> values = new LinkedHashMap<>(); for (String key : compound.getAllKeys()) values.put(key, convertTag(compound.get(key))); return values; }
        return null;
    }
}
