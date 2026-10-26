package li.cil.oc.common.item;

import java.util.List;
import java.util.TreeMap;
import li.cil.oc.api.internal.Tiered;
import li.cil.oc.common.inventory.ServerInventory;
import li.cil.oc.common.menu.ServerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class ServerItem extends Item implements Tiered {
    private final int tier;
    public ServerItem(Properties properties,int tier){super(properties.stacksTo(1).rarity(tier==0?Rarity.COMMON:tier==1?Rarity.UNCOMMON:tier==2?Rarity.RARE:Rarity.EPIC));this.tier=tier;}
    @Override public int tier(){return tier;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(!player.isShiftKeyDown()&&player instanceof ServerPlayer server)ServerMenu.openHeld(server,hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);tooltip.add(Component.translatable("oc:tooltip.server"));
        if(flag.isAdvanced()&&context.registries()!=null){TreeMap<String,Integer> counts=new TreeMap<>();for(ItemStack component:ServerInventory.loadItems(stack,context.registries()))if(!component.isEmpty())counts.merge(component.getHoverName().getString(),component.getCount(),Integer::sum);counts.forEach((name,count)->tooltip.add(Component.literal("- "+count+"x "+name)));}
    }
}
