package li.cil.oc.common.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import java.util.List;

public final class TexturePickerItem extends Item {
    public TexturePickerItem(Properties properties){super(properties);}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){if(context.getPlayer()==null)return InteractionResult.PASS;if(context.getLevel().isClientSide)li.cil.oc.client.DebugClient.pickTexture(context);return InteractionResult.sidedSuccess(context.getLevel().isClientSide);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);tooltip.add(Component.translatable("oc:tooltip.texturepicker"));}
}
