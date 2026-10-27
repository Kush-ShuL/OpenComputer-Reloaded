package li.cil.oc.common.item;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;

/** Original Delegate/SimpleItem tooltip and address defaults for player tools and assembly materials. */
final class PlayerItemTooltips {
    private PlayerItemTooltips(){}
    static void append(ItemStack stack,List<Component> tooltip,String key){
        if(Language.getInstance().has(key))Component.translatable(key).getString().replace("[nl]","\n").lines().forEach(line->tooltip.add(Component.literal(line.strip())));
        var node=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data").getCompound("node");
        if(node.contains("address")){
            String address=node.getString("address");tooltip.add(Component.literal("§8"+address.substring(0,Math.min(13,address.length()))+"...§7"));
        }
    }
    static int stackSize(ItemStack stack,int ordinary){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data").getCompound("node").contains("address")?1:ordinary;}
    static Rarity rarity(int tier){return tier<=0?Rarity.COMMON:tier==1?Rarity.UNCOMMON:tier==2?Rarity.RARE:Rarity.EPIC;}
}
