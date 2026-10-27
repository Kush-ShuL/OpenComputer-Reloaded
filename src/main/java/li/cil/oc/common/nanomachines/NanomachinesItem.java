package li.cil.oc.common.nanomachines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;
public final class NanomachinesItem extends Item {
    public NanomachinesItem(Properties properties){super(properties.rarity(Rarity.UNCOMMON));}
    @Override public boolean isBookEnchantable(ItemStack stack,ItemStack book){return false;}
    @Override public int getMaxStackSize(ItemStack stack){return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("oc:data").getCompound("node").contains("address")?1:super.getMaxStackSize(stack);}
    public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){player.startUsingItem(hand);return InteractionResultHolder.success(player.getItemInHand(hand));}
    public UseAnim getUseAnimation(ItemStack stack){return UseAnim.EAT;}
    public int getUseDuration(ItemStack stack,LivingEntity entity){return 32;}
    public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){if(!(entity instanceof Player player))return stack;if(!level.isClientSide){NanomachineData data=new NanomachineData(stack);Nanomachines.INSTANCE.uninstallController(player);ControllerImpl controller=(ControllerImpl)Nanomachines.INSTANCE.installController(player);if(data.configuration!=null){if(!data.uuid.isEmpty())controller.uuid=data.uuid;controller.configuration.load(data.configuration);}else controller.reconfigure();NanomachineNetwork.sendConfiguration(player);}stack.shrink(1);return stack.isEmpty()?ItemStack.EMPTY:stack;}
    public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);Component.translatable("oc:tooltip.nanomachines").getString().replace("[nl]","\n").lines().forEach(line->tooltip.add(Component.literal(line.strip())));String uuid=new NanomachineData(stack).uuid;if(!uuid.isEmpty())tooltip.add(Component.literal(uuid.substring(0,Math.min(13,uuid.length()))+"...").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));}
}
