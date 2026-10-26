package li.cil.oc.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;

/** Original display.color storage, also exposed through the modern dye component. */
public final class ItemColorizer {
    private ItemColorizer() {}
    public static boolean hasColor(ItemStack stack) {
        return stack.has(DataComponents.DYED_COLOR) || ItemUtils.customData(stack).getCompound("display").contains("color", Tag.TAG_ANY_NUMERIC);
    }
    public static int getColor(ItemStack stack) {
        var color = stack.get(DataComponents.DYED_COLOR);
        if (color != null) return color.rgb();
        var display = ItemUtils.customData(stack).getCompound("display");
        return display.contains("color", Tag.TAG_ANY_NUMERIC) ? display.getInt("color") : -1;
    }
    public static void setColor(ItemStack stack, int color) {
        var root = ItemUtils.customData(stack);
        var display = root.getCompound("display");
        display.putInt("color", color & 0xffffff);
        root.put("display", display);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color & 0xffffff, false));
    }
    public static void removeColor(ItemStack stack) {
        var root = ItemUtils.customData(stack);
        var display = root.getCompound("display");
        display.remove("color");
        if (display.isEmpty()) root.remove("display"); else root.put("display", display);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        stack.remove(DataComponents.DYED_COLOR);
    }
}
