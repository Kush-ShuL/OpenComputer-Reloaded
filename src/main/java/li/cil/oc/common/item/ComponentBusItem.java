package li.cil.oc.common.item;

import java.util.List;
import li.cil.oc.Settings;
import li.cil.oc.api.internal.Tiered;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

/** Component buses add capacity without providing a second CPU architecture or node. */
public final class ComponentBusItem extends Item implements Tiered {
    public final int capacityTier;
    public ComponentBusItem(Properties properties,int tier){super(properties.rarity(tier==0?Rarity.COMMON:tier==1?Rarity.UNCOMMON:tier==2?Rarity.RARE:Rarity.EPIC));capacityTier=tier;}
    @Override public int tier(){return Math.min(2,capacityTier);}
    @Override public int getMaxStackSize(ItemStack stack){return PlayerItemTooltips.stackSize(stack,super.getMaxStackSize(stack));}
    @Override public boolean isBookEnchantable(ItemStack stack,ItemStack book){return false;}
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        for(String line:Component.translatable("oc:tooltip.componentbus",Settings.get().cpuComponentSupport[capacityTier]).getString().split("\\[nl\\]"))tooltip.add(Component.literal(line));
        var node=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("oc:data").getCompound("node");if(node.contains("address")){String address=node.getString("address");tooltip.add(Component.literal("§8"+address.substring(0,Math.min(13,address.length()))+"...§7"));}
        if(flag.isAdvanced())tooltip.add(Component.translatable("oc:tooltip.tier",tier()+1));
    }
}
