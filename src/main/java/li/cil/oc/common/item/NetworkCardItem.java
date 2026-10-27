package li.cil.oc.common.item;

import java.util.List;
import li.cil.oc.api.internal.Tiered;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

/** Data, Internet and linked cards retain their original item and slot tiers. */
public final class NetworkCardItem extends Item implements Tiered {
    public enum Kind { DATA, INTERNET, LINKED }
    private final Kind kind;
    private final int tier;
    public NetworkCardItem(Properties properties,Kind kind,int tier) {
        super(properties.rarity(tier==0?Rarity.COMMON:tier==1?Rarity.UNCOMMON:Rarity.RARE)); this.kind=kind; this.tier=tier;
    }
    public Kind kind() { return kind; }
    @Override public int tier() { return tier; }
    @Override public int getMaxStackSize(ItemStack stack) {
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data");
        return data.getCompound("node").contains("address")?1:super.getMaxStackSize(stack);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag) {
        super.appendHoverText(stack,context,tooltip,flag);
        if(kind==Kind.DATA||kind==Kind.LINKED){
            String key=kind==Kind.DATA?"oc:tooltip.datacard"+tier:"oc:tooltip.linkedcard";
            for(String line:Component.translatable(key).getString().split("\\[nl\\]"))tooltip.add(Component.literal(line));
        }
        tooltip.add(Component.translatable("tooltip.opencomputers.hardware_tier",tier+1).withStyle(ChatFormatting.GRAY));
        if(kind==Kind.LINKED) {
            var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data");
            if(data.contains("oc:tunnel")) {
                String channel=data.getString("oc:tunnel");
                tooltip.add(Component.translatable("oc:tooltip.linkedcard_channel",channel.length()>13?channel.substring(0,13)+"...":channel).withStyle(ChatFormatting.GRAY));
            }
        }
        var node=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data").getCompound("node");
        if(node.contains("address")){
            String address=node.getString("address");
            tooltip.add(Component.literal("§8"+address.substring(0,Math.min(13,address.length()))+"...§7"));
        }
        if(flag.isAdvanced())tooltip.add(Component.translatable("oc:tooltip.tier",tier+1));
    }
}
