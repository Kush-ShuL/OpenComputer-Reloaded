package li.cil.oc.common.integration.thaumcraft;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import li.cil.oc.api.driver.Converter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Java migration of original OC commit 258943363ea8fc4ef14948e6bc6f67375061580c (MIT). */
final class ConverterThaumcraftItems implements Converter {
    private static final Set<String> ITEMS = Set.of(
        "thaumcraft:jar_normal", "thaumcraft:jar_void", "thaumcraft:phial", "thaumcraft:crystal_essence");

    @Override public void convert(Object value, Map<Object, Object> output) {
        if (!(value instanceof ItemStack stack) || !ITEMS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) return;
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.contains("Aspects", Tag.TAG_LIST)) {
            var aspects = new ArrayList<Map<String, Object>>();
            var tags = data.getList("Aspects", Tag.TAG_COMPOUND);
            for (int index = 0; index < tags.size(); index++) {
                CompoundTag aspect = tags.getCompound(index);
                aspects.add(Map.of("aspect", aspect.getString("key"), "amount", aspect.getInt("amount")));
            }
            output.put("aspects", aspects);
        }
        if (data.contains("AspectFilter", Tag.TAG_STRING)) output.put("aspectFilter", data.getString("AspectFilter"));
    }
}
