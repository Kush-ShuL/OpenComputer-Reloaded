package li.cil.oc.common.item;

import java.util.List;
import li.cil.oc.api.internal.Tiered;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

public final class RedstoneCardItem extends Item implements Tiered {
    private final int tier;
    public RedstoneCardItem(Properties properties, int tier) { super(properties.rarity(tier == 0 ? net.minecraft.world.item.Rarity.COMMON : net.minecraft.world.item.Rarity.UNCOMMON)); this.tier = tier; }
    @Override public int tier() { return tier; }
    @Override public boolean isBookEnchantable(ItemStack stack,ItemStack book) { return false; }
    @Override public int getMaxStackSize(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("oc:data");
        return data.getCompound("node").contains("address") ? 1 : super.getMaxStackSize(stack);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        PlayerItemTooltips.append(stack,tooltip,"oc:tooltip.redstonecard");
        if(flag.isAdvanced())tooltip.add(Component.translatable("oc:tooltip.tier",tier+1));
    }
}
