package li.cil.oc.server.driver.converter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.Converter;
import li.cil.oc.util.ItemUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.fml.ModList;

/** Original item inspection schema, backed by 1.21 data components. */
public final class ItemStack implements Converter {
    @Override public void convert(Object value, Map<Object, Object> output) { convert(value, output, null); }
    @Override public void convert(Object value, Map<Object, Object> output, HolderLookup.Provider registries) {
        if (!(value instanceof net.minecraft.world.item.ItemStack stack)) return;
        if (Settings.get().insertIdsInConverters) {
            output.put("id", BuiltInRegistries.ITEM.getId(stack.getItem()));
            // Modern item tags replace the former ore dictionary identifiers.
            output.put("oreNames", stack.getTags().map(tag -> tag.location().toString()).sorted().toArray(String[]::new));
        }
        output.put("damage", stack.getDamageValue()); output.put("maxDamage", stack.getMaxDamage());
        output.put("size", stack.getCount()); output.put("maxSize", stack.getMaxStackSize());
        output.put("hasTag", !stack.getComponentsPatch().isEmpty());
        output.put("name", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        output.put("label", stack.getHoverName().getString());
        var lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
        if (!lore.isEmpty()) output.put("lore", lore.stream().map(net.minecraft.network.chat.Component::getString).collect(Collectors.joining("\n")));
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.contains("advDmg", Tag.TAG_INT)) output.put("customDamage", data.getInt("advDmg"));
        if (data.contains("Energy", Tag.TAG_INT)) output.put("Energy", data.getInt("Energy"));
        if (ModList.get().isLoaded("draconicevolution")) {
            if (data.contains("DEUpgrades", Tag.TAG_COMPOUND)) output.put("DEUpgrades", data.getCompound("DEUpgrades").copy());
            for (int i = 0; i < 15; i++) { String profile = "Profile_" + i; if (data.contains(profile, Tag.TAG_COMPOUND)) output.put(profile, data.getCompound(profile).copy()); }
        }
        if (Settings.get().allowItemStackNBTTags && !stack.getComponentsPatch().isEmpty()) output.put("tag", componentTag(stack.getComponentsPatch(), registries));
        var enchantments = new ArrayList<Map<String, Object>>();
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            Map<String, Object> enchantment = new LinkedHashMap<>();
            var holder = entry.getKey(); int level = entry.getIntValue();
            enchantment.put("name", holder.unwrapKey().map(key -> key.location().toString()).orElseGet(() -> holder.value().description().getString()));
            enchantment.put("label", Enchantment.getFullname(holder, level).getString()); enchantment.put("level", level);
            enchantments.add(enchantment);
        }
        if (!enchantments.isEmpty()) output.put("enchantments", enchantments);
    }
    private static byte[] componentTag(DataComponentPatch patch, HolderLookup.Provider registries) {
        com.mojang.serialization.DataResult<Tag> encoded;
        try {
            encoded = DataComponentPatch.CODEC.encodeStart(registries == null ? NbtOps.INSTANCE : registries.createSerializationContext(NbtOps.INSTANCE), patch);
        } catch (RuntimeException error) {
            if (registries == null) throw new MissingRegistryContextException("Encoding item data components requires the invoking level's registry lookup", error);
            throw error;
        }
        Tag tag = encoded.result().orElseThrow(() -> {
            String reason = encoded.error().map(error -> error.message()).orElse("unknown component codec error");
            return registries == null ? new MissingRegistryContextException("Encoding item data components without a registry lookup failed: " + reason) : new IllegalStateException("Could not encode item data components: " + reason);
        });
        if (!(tag instanceof CompoundTag compound)) throw new IllegalStateException("DataComponentPatch codec did not produce a compound tag");
        try { return ItemUtils.saveTag(compound); }
        catch (IOException error) { throw new IllegalStateException("Could not compress item data components", error); }
    }
}
