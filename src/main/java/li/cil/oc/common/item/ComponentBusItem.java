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
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);tooltip.add(Component.translatable("oc:tooltip.cpu.Components",Settings.get().cpuComponentSupport[capacityTier]));}
}
