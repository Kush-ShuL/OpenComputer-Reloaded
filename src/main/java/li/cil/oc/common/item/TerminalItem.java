package li.cil.oc.common.item;

import java.util.List;
import li.cil.oc.common.network.RackNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class TerminalItem extends Item {
    public TerminalItem(Properties properties){super(properties.stacksTo(1));}
    @Override public boolean isBookEnchantable(ItemStack stack,ItemStack book){return false;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){ItemStack stack=player.getItemInHand(hand);var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();if(!player.isShiftKeyDown()&&!tag.getString("oc:key").isEmpty()&&!tag.getString("oc:server").isEmpty()){if(player instanceof ServerPlayer server)RackNetworking.openTerminal(server,hand);player.swing(InteractionHand.MAIN_HAND);}return InteractionResultHolder.pass(stack);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);PlayerItemTooltips.append(stack,tooltip,"oc:tooltip.terminal");String address=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("oc:server");if(!address.isEmpty())tooltip.add(Component.literal(address.substring(0,Math.min(13,address.length()))+"...").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));}
}
