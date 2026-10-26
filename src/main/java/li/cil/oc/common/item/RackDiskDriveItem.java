package li.cil.oc.common.item;

import java.util.List;
import li.cil.oc.common.menu.RackDiskMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class RackDiskDriveItem extends Item {
    public RackDiskDriveItem(Properties properties){super(properties.stacksTo(1));}
    @Override public InteractionResultHolder<ItemStack> use(Level world,Player player,InteractionHand hand){
        if(player instanceof ServerPlayer server&&!player.isSpectator())RackDiskMenu.openHeld(server,hand);
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),world.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        super.appendHoverText(stack,context,lines,flag);
        for(String line:Component.translatable("oc:tooltip.diskdrivemountable").getString().split("\\[nl\\]"))lines.add(Component.literal(line));
    }
}
