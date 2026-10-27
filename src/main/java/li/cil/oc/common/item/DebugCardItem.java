package li.cil.oc.common.item;

import li.cil.oc.common.item.data.DebugCardData;
import li.cil.oc.server.component.DebugAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import java.util.*;

public final class DebugCardItem extends Item {
    public DebugCardItem(Properties properties){super(properties);}
    @Override public int getMaxStackSize(ItemStack stack){return PlayerItemTooltips.stackSize(stack,super.getMaxStackSize(stack));}
    @Override public boolean isBookEnchantable(ItemStack stack,ItemStack book){return false;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){ItemStack stack=player.getItemInHand(hand);if(!level.isClientSide&&player.isShiftKeyDown()){var data=new DebugCardData(stack);String name=player.getGameProfile().getName();if(data.access.map(value->value.player().equals(name)).orElse(false))data.access=Optional.empty();else{var nonce=DebugAccess.whitelisted()?DebugAccess.nonce(name):Optional.<String>empty();if(DebugAccess.whitelisted()&&nonce.isEmpty()){player.sendSystemMessage(Component.literal("§cYou are not whitelisted to use debug card"));player.swing(hand,true);return InteractionResultHolder.fail(stack);}data.access=Optional.of(new DebugCardData.AccessContext(name,DebugAccess.whitelisted()?nonce.orElseThrow():""));}var root=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();data.save(root);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));player.swing(hand,true);}return InteractionResultHolder.success(stack);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);PlayerItemTooltips.append(stack,tooltip,"oc:tooltip.debugcard");new DebugCardData(stack).access.ifPresent(access->tooltip.add(Component.literal("§8"+access.player()+"§r")));}
}
