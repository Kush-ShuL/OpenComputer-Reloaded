package li.cil.oc.common.item;

import li.cil.oc.api.internal.Tiered;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import java.util.List;

/** The original inventory upgrade and real hot-swap slot containers. */
public final class AgentUpgradeItem extends Item implements Tiered {
    public final String kind;
    private final int tier;
    public AgentUpgradeItem(Properties properties,String kind,int tier){super(properties);this.kind=kind;this.tier=tier;}
    @Override public int tier(){return tier;}
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String key = switch (kind) {
            case li.cil.oc.api.driver.item.Slot.Card -> "oc:tooltip.upgradecontainercard";
            case li.cil.oc.api.driver.item.Slot.Upgrade -> "oc:tooltip.upgradecontainerupgrade";
            default -> "";
        };
        if (!key.isEmpty()) for (String line : Component.translatable(key, tier + 1).getString().split("\\[nl\\]")) tooltip.add(Component.literal(line));
    }
}
