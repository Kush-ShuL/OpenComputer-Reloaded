package li.cil.oc.common.item;

import li.cil.oc.api.internal.Tiered;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Empty assembly housing, distinct from the assembled drone and its internal upgrades. */
public final class AgentCaseItem extends Item implements Tiered {
    private final int tier;
    public AgentCaseItem(Properties properties,int tier){super(properties.rarity(PlayerItemTooltips.rarity(tier)));this.tier=tier;}
    @Override public int tier(){return tier;}
    @Override public boolean isBookEnchantable(ItemStack stack,ItemStack book){return false;}
    @Override public int getMaxStackSize(ItemStack stack){return PlayerItemTooltips.stackSize(stack,super.getMaxStackSize(stack));}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);PlayerItemTooltips.append(stack,tooltip,"oc:tooltip.dronecase");if(flag.isAdvanced())tooltip.add(Component.translatable("oc:tooltip.tier",tier+1));}
}
