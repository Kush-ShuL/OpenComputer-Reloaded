package li.cil.oc.common.item;

import java.util.List;
import li.cil.oc.Settings;
import li.cil.oc.common.item.data.ItemData;
import li.cil.oc.common.item.data.RobotData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** Original assembled robot item, with data-dependent rarity and component information. */
public final class RobotItem extends BlockItem {
    public RobotItem(Block block,Properties properties){super(block,properties);}
    @Override public boolean isBookEnchantable(ItemStack stack,ItemStack book){return false;}
    @Override public void verifyComponentsAfterLoad(ItemStack stack){stack.set(DataComponents.RARITY,PlayerItemTooltips.rarity(ItemData.customData(stack).getInt("oc:tier")));}
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);var tag=ItemData.customData(stack);
        if(tag.contains("oc:xp")){var settings=Settings.get();int level=Math.min(30,(int)(Math.pow(tag.getDouble("oc:xp")-settings.baseXpToLevel,1/settings.exponentialXpGrowth)/settings.constantXpGrowth));if(level>0)tooltip.add(Component.translatable("oc:tooltip.robot_level",level));}
        int energy=tag.getInt("oc:storedEnergy");if(energy>0)tooltip.add(Component.translatable("oc:tooltip.robot_storedenergy",energy));
        PlayerItemTooltips.append(stack,tooltip,"oc:tooltip.robot");
        if(RobotDroneTooltips.extended()&&context.registries()!=null){RobotData info=new RobotData(stack,context.registries());if(info.containers.length+info.components.length>0){tooltip.add(Component.translatable("oc:tooltip.server.Components"));RobotDroneTooltips.components(info.containers,tooltip);RobotDroneTooltips.components(info.components,tooltip);}}
    }
}
